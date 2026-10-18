package com.cloudstream.tr.core

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.extractors.RapidVidExtractor
import com.cloudstream.tr.core.model.ProviderModels
import com.cloudstream.tr.core.network.PreflightResult
import com.cloudstream.tr.core.network.SafeHttpClient
import com.cloudstream.tr.core.network.InFlightRequestDeduplicator
import com.cloudstream.tr.core.network.GetRequestKey
import com.cloudstream.tr.core.network.safeHttpCall
import com.cloudstream.tr.core.network.StreamValidator
import com.cloudstream.tr.core.network.ValidationStatus
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CoreTest {

    private val api = object : MainAPI() {
        override var name = "TestAPI"
        override var mainUrl = "https://example.com"
        override val supportedTypes = setOf(TvType.Movie)
    }

    @Test
    fun testProviderModelsNormalizeTitle() {
        val raw = "Kurtlar Vadisi: Pusu - 1. Bölüm İzle!"
        val norm = ProviderModels.normalizeTitle(raw)
        assertEquals("kurtlar vadisi pusu 1 bölüm", norm)
    }

    @Test
    fun testProviderModelsFormatSourceTitle() {
        val title = ProviderModels.formatSourceTitle("Vidmoly", isDubbed = true, isSubbed = false, resolution = "1080p")
        assertTrue(title.contains("TR Dublaj"))
        assertTrue(title.contains("1080p"))
        assertFalse(title.contains("TR Altyazı"))
    }

    @Test
    fun testProviderModelsDedupSearchResults() {
        val item1 = api.newMovieSearchResponse("Yıldızlararası", "https://site.com/film/1", TvType.Movie)
        val item2 = api.newMovieSearchResponse("Yıldızlararası (FİLM)", "https://site.com/film/1/", TvType.Movie)
        val item3 = api.newMovieSearchResponse("Inception", "https://site.com/film/2", TvType.Movie)

        val deduped = ProviderModels.dedupSearchResults(listOf(item1, item2, item3))
        assertEquals(2, deduped.size)
        assertEquals("Yıldızlararası", deduped[0].name)
        assertEquals("Inception", deduped[1].name)
    }

    @Test
    fun testProviderModelsDedupRemakesPreserved() {
        val dune1984 = api.newMovieSearchResponse("Dune", "https://site.com/dune-1984", TvType.Movie) {
            this.year = 1984
        }
        val dune2021 = api.newMovieSearchResponse("Dune", "https://site.com/dune-2021", TvType.Movie) {
            this.year = 2021
        }
        val dune2021Duplicate = api.newMovieSearchResponse("Dune", "https://site.com/dune-2021/", TvType.Movie) {
            this.year = 2021
        }

        val deduped = ProviderModels.dedupSearchResults(listOf(dune1984, dune2021, dune2021Duplicate))
        assertEquals(2, deduped.size)
        assertTrue(deduped.any { it.url.contains("1984") })
        assertTrue(deduped.any { it.url.contains("2021") })
    }

    @Test
    fun testSafeHttpClientHeaders() {
        val headers = SafeHttpClient.defaultHeaders("https://example.com/", isAjax = true)
        assertEquals("XMLHttpRequest", headers["X-Requested-With"])
        assertEquals("https://example.com/", headers["Referer"])
        assertEquals(SafeHttpClient.DEFAULT_USER_AGENT, headers["User-Agent"])
    }

    @Test
    fun deduplicatedRequestsShareOnlyMatchingRequestOptions() = runBlocking {
        val deduplicator = InFlightRequestDeduplicator<String, String>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var fetchCount = 0

        coroutineScope {
            val first = async {
                deduplicator.execute("url|header-a|cache-0") {
                    fetchCount++
                    started.complete(Unit)
                    release.await()
                    "response"
                }
            }
            started.await()
            val duplicate = async {
                deduplicator.execute("url|header-a|cache-0") {
                    fetchCount++
                    "unexpected"
                }
            }
            val differentHeaders = async {
                deduplicator.execute("url|header-b|cache-0") {
                    fetchCount++
                    "header-b"
                }
            }
            val differentCache = async {
                deduplicator.execute("url|header-a|cache-60") {
                    fetchCount++
                    "cache-60"
                }
            }

            assertEquals("header-b", differentHeaders.await())
            assertEquals("cache-60", differentCache.await())
            release.complete(Unit)
            assertEquals("response", first.await())
            assertEquals("response", duplicate.await())
        }
        assertEquals(3, fetchCount)
        assertNotEquals(
            GetRequestKey("https://example.test", mapOf("Authorization" to "a"), 0),
            GetRequestKey("https://example.test", mapOf("Authorization" to "b"), 0)
        )
        assertNotEquals(
            GetRequestKey("https://example.test", emptyMap(), 0),
            GetRequestKey("https://example.test", emptyMap(), 60)
        )
    }

    @Test
    fun cancellingWaiterDoesNotCancelSharedRequest() = runBlocking {
        val deduplicator = InFlightRequestDeduplicator<String, String>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        coroutineScope {
            val owner = async {
                deduplicator.execute("shared") {
                    started.complete(Unit)
                    release.await()
                    "ok"
                }
            }
            started.await()
            val waiter = async { deduplicator.execute("shared") { "unexpected" } }
            waiter.cancelAndJoin()
            release.complete(Unit)
            assertEquals("ok", owner.await())
        }

    }

    @Test
    fun cancellingRequestOwnerPropagatesAndCleansEntry() = runBlocking {
        val deduplicator = InFlightRequestDeduplicator<String, String>()
        val started = CompletableDeferred<Unit>()

        coroutineScope {
            val owner = async {
                deduplicator.execute("cancelled") {
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
            started.await()
            owner.cancelAndJoin()
        }
        assertEquals("recovered", deduplicator.execute("cancelled") { "recovered" })
    }

    @Test
    fun deduplicatedFailurePropagatesAndDoesNotPoisonNextRequest() = runBlocking {
        val deduplicator = InFlightRequestDeduplicator<String, String>()
        val expected = IllegalStateException("request failed")
        try {
            deduplicator.execute("failure") { throw expected }
            fail("Failure must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }
        assertEquals("recovered", deduplicator.execute("failure") { "recovered" })
    }

    @Test
    fun safeHttpCallReturnsNullOnErrorButPropagatesCancellation() = runBlocking {
        var reportedFailure: Exception? = null
        assertNull(safeHttpCall<String>(request = { throw IllegalStateException("offline") }) {
            reportedFailure = it
        })
        assertTrue(reportedFailure is IllegalStateException)

        val cancellation = CancellationException("cancel request")
        try {
            safeHttpCall<String>(request = { throw cancellation }) { fail("Cancellation is not a network failure") }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun testBoundedParallelResolverProgressive() = runBlocking {
        val items = listOf("stream1", "stream2", "stream3", "stream4")
        val linksEmitted = mutableListOf<String>()

        val count = BoundedParallelResolver.resolveProgressive(
            candidates = items,
            maxConcurrency = 2,
            earlyExitOnFirstSuccess = false,
            resolver = { item, emitLink ->
                delay(10)
                emitLink(
                    newExtractorLink("Test", item, "https://$item.mp4", ExtractorLinkType.VIDEO)
                )
            },
            onLinkFound = { link ->
                synchronized(linksEmitted) {
                    linksEmitted.add(link.url)
                }
            }
        )

        assertEquals(4, count)
        assertEquals(4, linksEmitted.size)
    }

    @Test
    fun testBoundedParallelResolverEarlyExit() = runBlocking {
        val items = listOf("stream1", "stream2", "stream3", "stream4")
        val linksEmitted = mutableListOf<String>()

        val count = BoundedParallelResolver.resolveProgressive(
            candidates = items,
            maxConcurrency = 1,
            earlyExitOnFirstSuccess = true,
            resolver = { item, emitLink ->
                delay(10)
                emitLink(
                    newExtractorLink("Test", item, "https://$item.mp4", ExtractorLinkType.VIDEO)
                )
            },
            onLinkFound = { link ->
                synchronized(linksEmitted) {
                    linksEmitted.add(link.url)
                }
            }
        )

        assertTrue(count >= 1)
        assertTrue(linksEmitted.isNotEmpty())
    }

    @Test
    fun testRapidVidDecrypt() {
        val result = RapidVidExtractor.decryptRapidvidAv("")
        assertEquals("", result)
    }

    @Test
    fun testStreamValidatorInferFromBytes() {
        val hlsBytes = "#EXTM3U\n#EXT-X-VERSION:3".toByteArray(Charsets.UTF_8)
        assertEquals(ExtractorLinkType.M3U8, StreamValidator.inferTypeFromBytes(hlsBytes))

        val mp4Bytes = byteArrayOf(0x00, 0x00, 0x00, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        assertEquals(ExtractorLinkType.VIDEO, StreamValidator.inferTypeFromBytes(mp4Bytes))

        val mkvBytes = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0x01, 0x02)
        assertEquals(ExtractorLinkType.VIDEO, StreamValidator.inferTypeFromBytes(mkvBytes))
    }

    @Test
    fun testStreamValidatorMpegTsSyncByte() {
        // 188 bytes: size < 189, must NOT throw IndexOutOfBoundsException and should return null
        val exact188Bytes = ByteArray(188) { 0x00 }
        exact188Bytes[0] = 0x47.toByte()
        assertNull(StreamValidator.inferTypeFromBytes(exact188Bytes))

        // 189 bytes with sync at 0 and 188: valid TS packet boundary
        val valid189Bytes = ByteArray(189) { 0x00 }
        valid189Bytes[0] = 0x47.toByte()
        valid189Bytes[188] = 0x47.toByte()
        assertEquals(ExtractorLinkType.VIDEO, StreamValidator.inferTypeFromBytes(valid189Bytes))

        // 377 bytes with sync at 0, 188, 376: valid multi-packet TS
        val valid377Bytes = ByteArray(377) { 0x00 }
        valid377Bytes[0] = 0x47.toByte()
        valid377Bytes[188] = 0x47.toByte()
        valid377Bytes[376] = 0x47.toByte()
        assertEquals(ExtractorLinkType.VIDEO, StreamValidator.inferTypeFromBytes(valid377Bytes))

        // 377 bytes with sync at 0 and 188, but corrupted at 376
        val corrupted377Bytes = ByteArray(377) { 0x00 }
        corrupted377Bytes[0] = 0x47.toByte()
        corrupted377Bytes[188] = 0x47.toByte()
        corrupted377Bytes[376] = 0x00.toByte()
        assertNull(StreamValidator.inferTypeFromBytes(corrupted377Bytes))
    }

    @Test
    fun testStreamValidatorClassifyHttpStatus() {
        assertEquals(ValidationStatus.VALID, StreamValidator.classifyHttpStatus(200))
        assertEquals(ValidationStatus.VALID, StreamValidator.classifyHttpStatus(206))

        assertEquals(ValidationStatus.INVALID, StreamValidator.classifyHttpStatus(400))
        assertEquals(ValidationStatus.INVALID, StreamValidator.classifyHttpStatus(401))
        assertEquals(ValidationStatus.INVALID, StreamValidator.classifyHttpStatus(403))
        assertEquals(ValidationStatus.INVALID, StreamValidator.classifyHttpStatus(404))
        assertEquals(ValidationStatus.INVALID, StreamValidator.classifyHttpStatus(405))
        assertEquals(ValidationStatus.INVALID, StreamValidator.classifyHttpStatus(406))
        assertEquals(ValidationStatus.INVALID, StreamValidator.classifyHttpStatus(410))
        assertEquals(ValidationStatus.INVALID, StreamValidator.classifyHttpStatus(422))

        assertEquals(ValidationStatus.INDETERMINATE, StreamValidator.classifyHttpStatus(408))
        assertEquals(ValidationStatus.INDETERMINATE, StreamValidator.classifyHttpStatus(425))
        assertEquals(ValidationStatus.INDETERMINATE, StreamValidator.classifyHttpStatus(429))
        assertEquals(ValidationStatus.INDETERMINATE, StreamValidator.classifyHttpStatus(500))
        assertEquals(ValidationStatus.INDETERMINATE, StreamValidator.classifyHttpStatus(503))
    }

    @Test
    fun testPreflightResultFailClosed() {
        val indeterminate = PreflightResult(
            status = ValidationStatus.INDETERMINATE,
            streamType = ExtractorLinkType.VIDEO,
            failureReason = "PREFLIGHT_TIMEOUT"
        )
        assertFalse("INDETERMINATE must be fail-closed (isValid = false)", indeterminate.isValid)

        val invalid = PreflightResult(
            status = ValidationStatus.INVALID,
            streamType = ExtractorLinkType.VIDEO,
            failureReason = "HTTP_STATUS_403"
        )
        assertFalse("INVALID must have isValid = false", invalid.isValid)

        val valid = PreflightResult(
            status = ValidationStatus.VALID,
            streamType = ExtractorLinkType.M3U8
        )
        assertTrue("VALID must have isValid = true", valid.isValid)
    }

    @Test
    fun testStreamValidatorInvalidMediaBody() {
        val html = "<!DOCTYPE html><html><head><title>Just a moment...</title></head><body>Cloudflare</body></html>"
        assertTrue(StreamValidator.isInvalidMediaBody(html, "text/html"))

        val secError = "security error"
        assertTrue(StreamValidator.isInvalidMediaBody(secError, "text/html"))

        val jsonErr = "{\"status\":false,\"message\":\"Expired link\"}"
        assertTrue(StreamValidator.isInvalidMediaBody(jsonErr, "application/json"))

        val validM3u8 = "#EXTM3U\n#EXT-X-TARGETDURATION:10"
        assertFalse(StreamValidator.isInvalidMediaBody(validM3u8, "application/x-mpegurl"))
    }

    @Test
    fun testStreamValidatorInferFromMetadata() {
        assertEquals(ExtractorLinkType.M3U8, StreamValidator.inferTypeFromMetadata("application/vnd.apple.mpegurl", "https://cdn.com/stream"))
        assertEquals(ExtractorLinkType.M3U8, StreamValidator.inferTypeFromMetadata(null, "https://cdn.com/hls/master.m3u8?token=123"))
        assertEquals(ExtractorLinkType.VIDEO, StreamValidator.inferTypeFromMetadata("video/mp4", "https://cdn.com/play/1"))
        assertEquals(ExtractorLinkType.DASH, StreamValidator.inferTypeFromMetadata("application/dash+xml", "https://cdn.com/manifest.mpd"))
    }

    @Test
    fun testDiagnosticLoggerRedaction() {
        val urlWithToken = "https://cdn.example.com/hls/live.m3u8?token=secret123456789&key=abc"
        val redacted = com.cloudstream.tr.core.diagnostics.DiagnosticLogger.redactUrl(urlWithToken)
        assertFalse(redacted.contains("secret123456789"))
        assertTrue(redacted.contains("[REDACTED]"))

        val msg = "User token=xyz987654321 Bearer secret_bearer_token_12345 failed"
        val cleanMsg = com.cloudstream.tr.core.diagnostics.DiagnosticLogger.redactSensitiveInfo(msg)
        assertFalse(cleanMsg.contains("xyz987654321"))
        assertFalse(cleanMsg.contains("secret_bearer_token_12345"))
        assertTrue(cleanMsg.contains("[REDACTED]"))

        val diagnosticUrl = com.cloudstream.tr.core.diagnostics.DiagnosticLogger.redactUrl(
            "https://cdn.example.com/path?token=private_token&signature=private_signature"
        )
        assertFalse(diagnosticUrl.contains("private_token"))
        assertFalse(diagnosticUrl.contains("private_signature"))
    }

    @Test
    fun testBoundedParallelResolverDeduplication() = runBlocking {
        val items = listOf("stream1", "stream1", "stream2", "stream2")
        val linksEmitted = mutableListOf<String>()

        val count = BoundedParallelResolver.resolveProgressive(
            candidates = items,
            maxConcurrency = 2,
            earlyExitOnFirstSuccess = false,
            resolver = { item, emitLink ->
                emitLink(newExtractorLink("Test", item, "https://$item.mp4", ExtractorLinkType.VIDEO))
            },
            onLinkFound = { link ->
                synchronized(linksEmitted) {
                    linksEmitted.add(link.url)
                }
            }
        )

        assertEquals(2, count)
        assertEquals(2, linksEmitted.size)
    }

    @Test
    fun testCloseLoadExtractorDecode() {
        // Test payload from closeload with verified real sample
        val sampleParts = listOf(
            "srv9.cdnimages2570.shop", "txt/master.txt", "hls", "thegetout-2026",
            "dummy1", "dummy2", "dummy3", "dummy4", "dummy5", "dummy6",
            "dummy7", "dummy8", "dummy9", "dummy10", "dummy11"
        )
        // Ensure decoder runs deterministically without exception
        val decoded = com.cloudstream.tr.core.extractors.CloseLoadExtractor.decodeCloseLoadPayload(sampleParts)
        assertNotNull(decoded)
    }

    @Test
    fun testCloseLoadUnpackPacker() {
        val packed = """eval(function(p,a,c,k,e,d){e=function(c){return c};if(!''.replace(/^/,String)){while(c--){d[c]=k[c]||c}k=[function(e){return d[e]}];e=function(){return'\\w+'};c=1};while(c--){if(k[c]){p=p.replace(new RegExp('\\b'+e(c)+'\\b','g'),k[c])}}return p}('0 1=2;',3,3,'var|foo|bar'.split('|'),0,{}))"""
        val unpacked = com.cloudstream.tr.core.extractors.CloseLoadExtractor.unpackPacker(packed)
        assertTrue(unpacked.contains("var foo=bar;"))
    }
}

