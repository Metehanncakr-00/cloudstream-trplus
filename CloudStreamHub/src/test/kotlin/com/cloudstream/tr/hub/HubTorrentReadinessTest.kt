package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.ErrorLoadingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class HubTorrentReadinessTest {
    private val url = "http://127.0.0.1:8090/stream/selected.mkv?link=abc&index=2&play&preload&stat"
    private fun metadata(size: Long = 200_000L) =
        """{"file_stats":[{"id":0,"path":"sample.txt","length":100},{"id":2,"path":"Feature.Remux.2160p.mkv","length":$size}]}""".toByteArray()

    private fun range(start: Long, end: Long, size: Long) =
        TorrentProbeResponse(206, "bytes $start-$end/$size", ByteArray((end - start + 1).toInt()))

    @Test fun featureProfileUsesBoundedWarmupRange() {
        assertEquals(65_536, HubReadAheadPolicy.warmupProbeBytes(HubPerformanceSettings.AUTO))
        assertEquals(262_144, HubReadAheadPolicy.warmupProbeBytes(HubPerformanceSettings.FEATURE))
    }

    @Test fun featureProfileHasLongerBoundedStartupBudget() {
        assertEquals(90_000L, torrentReadinessBudget(HubPerformanceSettings.AUTO))
        assertEquals(120_000L, torrentReadinessBudget(HubPerformanceSettings.FEATURE))
        assertEquals(90_000L, torrentReadinessBudget(HubPerformanceSettings.COMPATIBLE))
    }

    @Test fun torrServerSnapshotExposesActualDownloadRateWithoutClaimingBufferDepth() {
        val payload = """{"download_speed":2500000,"active_peers":7,"connected_seeders":3,
            "preloaded_bytes":524288,"preload_size":1048576,"duration_seconds":7200}""".toByteArray()
        val health = torrentTransferHealth(TorrentProbeResponse(200, null, payload), 54L * 1024 * 1024 * 1024)!!
        assertEquals(20.0, health.downloadMbps!!, 0.001)
        assertEquals(7, health.activePeers)
        assertEquals(3, health.connectedSeeders)
        assertEquals(50, health.preloadPercent)
        assertTrue(health.requiredAverageMbps!! > 60)
        assertTrue(health.safeSummary().contains("down_20.0Mbps"))
    }

    @Test fun incompleteOrMalformedTorrentStatsDoNotMarkPlaybackAsReady() {
        assertNull(torrentTransferHealth(TorrentProbeResponse(200, null, "{invalid".toByteArray()), 100))
        assertNull(torrentTransferHealth(TorrentProbeResponse(503, null,
            """{"download_speed":50000}""".toByteArray()), 100))
        assertNull(torrentTransferHealth(TorrentProbeResponse(200, null,
            """{"file_stats":[]}""".toByteArray()), 100))
    }

    @Test fun normalizesFlagsWithoutChangingSelectedFile() {
        val prepared = torrentPreparationUrl(url, true).toHttpUrl()
        assertEquals("/stream/selected.mkv", prepared.encodedPath)
        assertEquals("2", prepared.queryParameter("index"))
        assertEquals("abc", prepared.queryParameter("link"))
        assertEquals(8090, prepared.port)
        assertFalse(prepared.queryParameterNames.contains("play"))
        assertEquals(setOf("link", "index", "preload", "stat"), prepared.queryParameterNames)
        val play = torrentPreparationUrl(url, false).toHttpUrl()
        assertEquals(setOf("link", "index", "play"), play.queryParameterNames)
        val status = torrentStatusUrl(url).toHttpUrl()
        assertEquals(setOf("link", "index", "stat"), status.queryParameterNames)
        assertEquals("2", status.queryParameter("index"))
    }

    @Test fun rejectsRemoteMissingOrAmbiguousIndexBeforeNetwork() {
        listOf(url.replace("127.0.0.1", "example.com"), url.replace("index=2", "index=-1"),
            url.replace("index=2", "index=2&index=3"), url.replace("index=2&", ""))
            .forEach { bad ->
                try { torrentPreparationUrl(bad, true); fail(bad) } catch (_: ErrorLoadingException) { }
            }
    }

    @Test fun preloadThenHeadThenTailAreRequiredForReadiness() = runBlocking {
        val calls = mutableListOf<String?>()
        val transport = TorrentReadinessTransport { request, requestedRange, _, _ ->
            calls.add(requestedRange)
            assertEquals("2", request.toHttpUrl().queryParameter("index"))
            when (requestedRange) {
                null -> TorrentProbeResponse(200, null, metadata())
                "bytes=0-65535" -> range(0, 65535, 200_000)
                "bytes=134464-199999" -> range(134464, 199999, 200_000)
                else -> throw AssertionError(requestedRange)
            }
        }
        awaitTorrentReadiness(url, transport, diagnostic = {})
        assertEquals(listOf(null, "bytes=0-65535", "bytes=134464-199999"), calls)
    }

    @Test fun featureProfileProbesAtMost256KiBBeforeHandoff() = runBlocking {
        val old = HubPerformanceSettings.profile
        HubPerformanceSettings.profile = HubPerformanceSettings.FEATURE
        try {
            val requested = mutableListOf<String?>()
            awaitTorrentReadiness(url, TorrentReadinessTransport { _, wanted, _, _ ->
                requested.add(wanted)
                when (wanted) {
                    null -> TorrentProbeResponse(200, null, metadata(524_288))
                    "bytes=0-262143" -> range(0, 262143, 524_288)
                    "bytes=458752-524287" -> range(458752, 524287, 524_288)
                    else -> throw AssertionError(wanted)
                }
            }, budgetMs = 4_000, diagnostic = {})
            assertEquals(listOf(null, "bytes=0-262143", "bytes=458752-524287"), requested)
        } finally {
            HubPerformanceSettings.profile = old
        }
    }

    @Test fun smallFilesAndWarmRetryUseActualBytes() = runBlocking {
        var heads = 0
        awaitTorrentReadiness(url, TorrentReadinessTransport { request, requestedRange, _, _ ->
            when {
                requestedRange == null && request.toHttpUrl().queryParameterNames.contains("preload") ->
                    throw IOException("Preload response timeout")
                requestedRange == null -> TorrentProbeResponse(200, null,
                    """{"file_stats":[{"id":2,"path":"Feature.mkv","length":10}]}""".toByteArray())
                heads++ == 0 -> throw IOException("Not ready")
                else -> range(0, 9, 10)
            }
        }, budgetMs = 3000, diagnostic = {})
        assertEquals(3, heads)
    }

    @Test fun rejectsStatusRedirectWrongRangeChangedSizeAndShortBody() {
        val valid = range(0, 9, 10)
        listOf(valid.copy(code = 200), valid.copy(code = 302), valid.copy(range = "bytes 1-9/10"),
            valid.copy(range = "bytes 0-8/10"), valid.copy(bytes = ByteArray(9)),
            valid.copy(range = "bytes 0-9/*")).forEach {
            try { validateTorrentRange(it, 0, 65535); fail("Expected rejection") }
            catch (_: ErrorLoadingException) { }
        }
        try { validateTorrentRange(valid, 0, 9, 11); fail("Size changed") }
        catch (_: ErrorLoadingException) { }
    }

    @Test fun temporarilyIncompleteRangesAreRetriedUntilAvailable() = runBlocking {
        var headReads = 0
        awaitTorrentReadiness(url, TorrentReadinessTransport { _, requestedRange, _, _ ->
            when (requestedRange) {
                null -> TorrentProbeResponse(200, null, metadata())
                "bytes=0-65535" -> {
                    headReads++
                    if (headReads == 1) TorrentProbeResponse(503, null, ByteArray(5))
                    else range(0, 65535, 200_000)
                }
                "bytes=134464-199999" -> range(134464, 199999, 200_000)
                else -> throw AssertionError(requestedRange)
            }
        }, budgetMs = 4_000, diagnostic = {})
        assertEquals(2, headReads)
    }

    @Test fun torrentStatusSelectsCorrectLargeBluRayFileAndIndexZeroIsAllowed() {
        val result = TorrentProbeResponse(200, null, metadata(8L * 1024 * 1024 * 1024))
        val selected = selectedTorrentVideo(result, 2)
        assertEquals(2, selected.index)
        assertEquals(8L * 1024 * 1024 * 1024, selected.byteLength)
        assertEquals("mkv", selected.extension)
    }

    @Test fun rejectsMissingAndNonVideoSelectionsWithoutHandoff() {
        for ((body, index) in listOf(
            metadata() to 19,
            metadata() to 0,
            """{"file_stats":[]}""".toByteArray() to 2,
            """{"file_stats":[{"id":2,"path":"Feature.iso","length":100}]}""".toByteArray() to 2,
            """{"file_stats":[{"id":2,"path":"Feature.m2ts","length":0}]}""".toByteArray() to 2
        )) {
            try {
                selectedTorrentVideo(TorrentProbeResponse(200, null, body), index)
                fail("Expected bad file rejection")
            } catch (_: ErrorLoadingException) { }
        }
    }

    @Test fun supportsBluRayTransportStreamButRejectsMismatchedRangeLength() {
        val video = selectedTorrentVideo(TorrentProbeResponse(200, null,
            """{"file_stats":[{"id":2,"path":"BDMV/STREAM/00001.m2ts","length":5242880}]}""".toByteArray()), 2)
        assertEquals("m2ts", video.extension)
        try {
            validateTorrentRange(range(0, 65535, 200_000), 0, 65535, video.byteLength)
            fail("Must reject wrong selected file")
        } catch (_: ErrorLoadingException) { }
    }

    @Test fun missingSelectedFileFailsBeforeRangeRequests() = runBlocking {
        var calls = 0
        try {
            awaitTorrentReadiness(url, TorrentReadinessTransport { _, range, _, _ ->
                calls++
                assertNull("Should reject at preload metadata stage", range)
                TorrentProbeResponse(200, null, """{"file_stats":[{"id":1,"path":"wrong.mkv","length":90000}]}""".toByteArray())
            }, budgetMs = 3000, diagnostic = {})
            fail("Selected index must exist")
        } catch (_: ErrorLoadingException) { assertEquals(1, calls) }
    }

    @Test fun permanentRangeProtocolErrorsFailFast() = runBlocking {
        var probes = 0
        try {
            awaitTorrentReadiness(url, TorrentReadinessTransport { _, range, _, _ ->
                if (range == null) TorrentProbeResponse(200, null, metadata())
                else {
                    probes++
                    TorrentProbeResponse(200, null, ByteArray(10))
                }
            }, budgetMs = 3000, diagnostic = {})
            fail("Range ignored by server")
        } catch (_: ErrorLoadingException) { assertEquals(1, probes) }
    }

    @Test fun preloadTimeoutRequiresStatMetadataBeforeByteProbes() = runBlocking {
        val requests = mutableListOf<Pair<String, String?>>()
        awaitTorrentReadiness(url, TorrentReadinessTransport { request, requestedRange, _, _ ->
            val flags = request.toHttpUrl().queryParameterNames
            requests.add((if ("preload" in flags) "preload" else if ("stat" in flags) "stat" else "play") to requestedRange)
            when {
                "preload" in flags -> throw IOException("Client timed out while server preloaded")
                "stat" in flags -> TorrentProbeResponse(200, null, metadata())
                requestedRange == "bytes=0-65535" -> range(0, 65_535, 200_000)
                requestedRange == "bytes=134464-199999" -> range(134_464, 199_999, 200_000)
                else -> throw AssertionError(request)
            }
        }, budgetMs = 4_000, diagnostic = {})
        assertEquals(listOf(
            "preload" to null,
            "stat" to null,
            "play" to "bytes=0-65535",
            "play" to "bytes=134464-199999"
        ), requests)
    }

    @Test fun preloadTimeoutWithMissingFileMustNotReadAnyMediaBytes() = runBlocking {
        var ranges = 0
        try {
            awaitTorrentReadiness(url, TorrentReadinessTransport { request, requestedRange, _, _ ->
                if (requestedRange != null) ranges++
                if (request.toHttpUrl().queryParameterNames.contains("preload")) throw IOException("Preload timed out")
                TorrentProbeResponse(200, null, """{"file_stats":[{"id":1,"path":"wrong.mkv","length":200000}]}""".toByteArray())
            }, budgetMs = 3_000, diagnostic = {})
            fail("Wrong file must never be handed to player")
        } catch (_: ErrorLoadingException) { assertEquals(0, ranges) }
    }

    @Test fun malformedStatAfterPreloadTimeoutMustFailClosed() = runBlocking {
        var rangeReads = 0
        try {
            awaitTorrentReadiness(url, TorrentReadinessTransport { request, requestedRange, _, _ ->
                if (requestedRange != null) rangeReads++
                if (request.toHttpUrl().queryParameterNames.contains("preload")) throw IOException("Preload timed out")
                TorrentProbeResponse(200, null, "{invalid".toByteArray())
            }, budgetMs = 3_000, diagnostic = {})
            fail("Invalid metadata cannot establish media readiness")
        } catch (_: ErrorLoadingException) { assertEquals(0, rangeReads) }
    }

    @Test fun transientStatCanRecoverWithoutAnotherPreload() = runBlocking {
        var preloads = 0
        var stats = 0
        awaitTorrentReadiness(url, TorrentReadinessTransport { request, requestedRange, _, _ ->
            val flags = request.toHttpUrl().queryParameterNames
            when {
                "preload" in flags -> {
                    preloads++
                    TorrentProbeResponse(503, null, ByteArray(0))
                }
                "stat" in flags -> {
                    stats++
                    if (stats == 1) TorrentProbeResponse(503, null, ByteArray(0))
                    else TorrentProbeResponse(200, null, metadata())
                }
                requestedRange == "bytes=0-65535" -> range(0, 65_535, 200_000)
                requestedRange == "bytes=134464-199999" -> range(134_464, 199_999, 200_000)
                else -> throw AssertionError(request)
            }
        }, budgetMs = 4_000, diagnostic = {})
        assertEquals(1, preloads)
        assertEquals(2, stats)
    }

    @Test fun statusIdZeroOmissionIsOnlyAllowedOnFirstFile() {
        val valid = TorrentProbeResponse(200, null,
            """{"file_stats":[{"path":"Feature.mkv","length":200000},{"id":1,"path":"sample.txt","length":100}]}""".toByteArray())
        assertEquals(0, selectedTorrentVideo(valid, 0).index)
        val malformed = listOf(
            """{"file_stats":[{"id":1,"path":"sample.txt","length":100},{"path":"Feature.mkv","length":200000}]}""",
            """{"file_stats":[{"id":"0","path":"Feature.mkv","length":200000}]}""",
            """{"file_stats":[{"id":2,"path":"A.mkv","length":200000},{"id":2,"path":"B.mkv","length":200000}]}"""
        )
        malformed.forEachIndexed { index, json ->
            try {
                selectedTorrentVideo(TorrentProbeResponse(200, null, json.toByteArray()), if (index < 2) 0 else 2)
                fail("Ambiguous or malformed index must fail")
            } catch (_: ErrorLoadingException) { }
        }
    }

    @Test fun preparationTimeoutIsFriendlyAndDoesNotBecomeReady() = runBlocking {
        try {
            awaitTorrentReadiness(url, TorrentReadinessTransport { _, _, _, _ -> awaitCancellation() },
                budgetMs = 30, diagnostic = {})
            fail("Expected timeout")
        } catch (e: ErrorLoadingException) { assertTrue(e.message.orEmpty().contains("hazırlanamadı")) }
    }

    @Test fun callerCancellationPropagates() = runBlocking {
        try {
            withTimeout(30) {
                awaitTorrentReadiness(url, TorrentReadinessTransport { _, _, _, _ -> awaitCancellation() },
                    diagnostic = {})
            }
            fail("Expected caller cancellation")
        } catch (_: CancellationException) { }
    }

    @Test fun httpFailureDoesNotLaunchReadinessProbes() = runBlocking {
        var calls = 0
        try {
            awaitTorrentReadiness(url, TorrentReadinessTransport { _, _, _, _ ->
                calls++; TorrentProbeResponse(403, null, "{}".toByteArray())
            }, diagnostic = {})
            fail("Expected rejection")
        } catch (_: ErrorLoadingException) { assertEquals(1, calls) }
    }
}
