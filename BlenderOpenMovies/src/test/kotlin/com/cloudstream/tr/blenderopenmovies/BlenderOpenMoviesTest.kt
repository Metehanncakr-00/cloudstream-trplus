package com.cloudstream.tr.blenderopenmovies

import com.cloudstream.tr.core.network.StreamHttpResponse
import com.cloudstream.tr.core.network.StreamHttpTransport
import com.cloudstream.tr.core.network.StreamValidator
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BlenderOpenMoviesTest {
    private val mapper = jacksonObjectMapper()
    private val uuid = "0eb052d0-fd51-43e6-aa33-ecdbf77a5d40"
    private val url = BlenderCatalog.watchUrl(uuid)
    private fun fixture() = javaClass.getResource("/sintel-detail.json")!!.readText()
    private fun node() = mapper.readTree(fixture()) as ObjectNode
    private fun catalogJson(vararg rows: ObjectNode, total: Int = rows.size) = mapper.createObjectNode().apply {
        put("total", total); set<com.fasterxml.jackson.databind.JsonNode>("data", mapper.valueToTree(rows.toList()))
    }.toString()

    @Test fun offlineCatalogSearchLoadAndStablePayloadUseOnlyOfficialChannel() = runBlocking {
        val requests = mutableListOf<String>()
        val unknown = node().apply { put("uuid", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa") }
        val provider = BlenderOpenMovies().apply {
            catalogOverride = BlenderCatalog { request ->
                requests += request
                if (request.contains("/video-channels/")) catalogJson(node(), unknown) else fixture()
            }
        }
        val search = provider.search("sintel", 1).items.single()
        assertEquals("Sintel", search.name)
        assertEquals(url, search.url)
        assertTrue(provider.search("unlicensed", 1).items.isEmpty())
        assertTrue(provider.search("sintel", 2).items.isEmpty())
        val loaded = provider.load(url) as MovieLoadResponse
        assertEquals("Sintel", loaded.name)
        assertEquals(2010, loaded.year)
        assertEquals(url, loaded.dataUrl)
        assertTrue(loaded.plot!!.contains("credited artists"))
        assertTrue(requests.all { it.startsWith("${BlenderCatalog.BASE}/api/v1/") })
        assertTrue(requests.none { it.contains("/search") })
        assertEquals(listOf("Sintel"), provider.quickSearch("Sintel").map { it.name })
        val home = provider.getMainPage(1, com.lagradost.cloudstream3.MainPageRequest("Blender Open Movies", "open-movies", false)).items.single()
        assertEquals(listOf("Sintel"), home.list.map { it.name })
    }

    @Test fun eligibilityFailsClosedForPrivacyChannelRightsAndUnknownYear() {
        assertNotNull(BlenderCatalog.parseFilm(node()))
        for (privacy in listOf(2, 3, 4)) {
            assertNull(BlenderCatalog.parseFilm(node().apply { (path("privacy") as ObjectNode).put("id", privacy) }))
        }
        assertNull(BlenderCatalog.parseFilm(node().apply { (path("channel") as ObjectNode).put("name", "other") }))
        assertNull(BlenderCatalog.parseFilm(node().apply { (path("channel") as ObjectNode).put("host", "other.invalid") }))
        assertNull(BlenderCatalog.parseFilm(node().apply { put("isLive", true) }))
        val unknown = node().apply { put("uuid", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"); put("name", "A new film") }
        assertNull(BlenderCatalog.parseFilm(unknown))
        for (license in listOf(1, 2)) {
            (unknown.path("licence") as ObjectNode).put("id", license)
            val film = BlenderCatalog.parseFilm(unknown)!!
            assertEquals("A new film", film.title)
            assertNull(film.year) // Never infer release year from publishedAt.
        }
        (unknown.path("licence") as ObjectNode).put("id", 3)
        assertNull(BlenderCatalog.parseFilm(unknown))
    }

    @Test fun rejectsHostSchemePayloadAndFileConfusionBeforeAnyFetch() = runBlocking {
        val catalog = BlenderCatalog { error("Must not fetch invalid payload") }
        for (bad in listOf(url.replace("https:", "http:"), "$url?token=secret", "$url#x", url.replace("video.blender.org", "evil.invalid"),
            url.replace("video.blender.org", "user@video.blender.org"), "$url/../other", "ipfs://$uuid", uuid)) {
            assertNull(catalog.detail(bad))
        }
        val good = "${BlenderCatalog.BASE}/object-storage/web_videos/$uuid-240.mp4"
        assertTrue(BlenderCatalog.isPublicFile(good, uuid))
        for (bad in listOf(good.replace(uuid, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "$good?token=x", "$good#x",
            good.replace("https:", "http:"), good.replace("video.blender.org", "evil.invalid"),
            good.replace("video.blender.org", "user@video.blender.org"), good.replace(".mp4", ".zip"),
            good.replace("/object-storage/", "/paid/"), good.replace("-240", "-../../240"), good.replace("-240", "%2d240"))) {
            assertFalse(bad, BlenderCatalog.isPublicFile(bad, uuid))
        }
        assertNull(BlenderCatalog { fixture().replace(uuid, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa") }.detail(url))
    }

    @Test fun paginationIsBoundedAndSearchNeverUsesGlobalSearch() = runBlocking {
        val requests = mutableListOf<String>()
        val catalog = BlenderCatalog { request ->
            requests += request
            if (request.contains("start=0&")) catalogJson(*Array(20) { node() }, total = 21)
            else catalogJson(node(), total = 21)
        }
        assertEquals(1, catalog.search("Sintel").size)
        assertEquals(2, requests.size)
        assertTrue(requests[0].contains("count=20&start=0&"))
        assertTrue(requests[1].contains("count=20&start=20&"))
        assertFalse(catalog.page(0).hasNext)
        assertFalse(catalog.page(11).hasNext)
        assertEquals(2, requests.size)
    }

    @Test fun linksRefreshDetailProbeOnceAndPreserveRefererWithoutMetadataAudioClaims() = runBlocking {
        var detailCalls = 0
        val provider = BlenderOpenMovies().apply { catalogOverride = BlenderCatalog { detailCalls++; fixture() } }
        val probes = ConcurrentHashMap<String, AtomicInteger>()
        StreamValidator.transport = StreamHttpTransport { media, headers ->
            probes.computeIfAbsent(media) { AtomicInteger() }.incrementAndGet()
            assertEquals(url, headers["Referer"])
            assertTrue(headers["Range"]!!.startsWith("bytes=0-"))
            StreamHttpResponse(206, "video/mp4") { byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112).inputStream() }
        }
        try {
            repeat(2) {
                val links = mutableListOf<ExtractorLink>()
                assertTrue(provider.loadLinks(url, false, {}, links::add))
                assertEquals(2, links.size)
                assertEquals(720, links.first().quality)
                assertTrue(links.all { it.referer == url && it.audioTracks.isNullOrEmpty() && !it.name.contains("English") })
            }
            assertEquals(2, detailCalls)
            assertTrue(probes.values.all { it.get() == 2 })
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun invalidMediaNeverEmitsAndCancellationPropagates() = runBlocking {
        val provider = BlenderOpenMovies().apply { catalogOverride = BlenderCatalog { fixture() } }
        val links = mutableListOf<ExtractorLink>()
        StreamValidator.transport = StreamHttpTransport { _, _ -> StreamHttpResponse(403, "text/html") { "Forbidden".byteInputStream() } }
        try {
            assertFalse(provider.loadLinks(url, false, {}, links::add))
            assertTrue(links.isEmpty())
            val entered = CompletableDeferred<Unit>()
            StreamValidator.transport = StreamHttpTransport { _, _ -> entered.complete(Unit); awaitCancellation() }
            val job = launch { provider.loadLinks(url, false, {}, links::add) }
            entered.await(); job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertTrue(links.isEmpty())
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun preflightsAreBoundedAndFinishBeforeSortedCallbacks() = runBlocking {
        val root = node()
        val files = mapper.createArrayNode()
        for (quality in listOf(240, 360, 480, 720, 1080)) {
            files.add(mapper.createObjectNode().apply {
                putObject("resolution").put("id", quality)
                put("fileUrl", "${BlenderCatalog.BASE}/object-storage/web_videos/$uuid-$quality.mp4")
            })
        }
        files.add(mapper.createObjectNode().apply {
            putObject("resolution").put("id", 2160)
            put("fileUrl", "https://evil.invalid/object-storage/web_videos/$uuid-2160.mp4")
        })
        root.set<com.fasterxml.jackson.databind.JsonNode>("files", files)
        val provider = BlenderOpenMovies().apply { catalogOverride = BlenderCatalog { root.toString() } }
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val completed = AtomicInteger()
        StreamValidator.transport = StreamHttpTransport { _, _ ->
            val count = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, count) }
            try {
                delay(20)
                StreamHttpResponse(206, "video/mp4") { byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112).inputStream() }
            } finally { active.decrementAndGet(); completed.incrementAndGet() }
        }
        try {
            val links = mutableListOf<ExtractorLink>()
            assertTrue(provider.loadLinks(url, false, {}, {
                assertEquals(5, completed.get())
                links += it
            }))
            assertTrue(peak.get() in 2..4)
            assertEquals(5, links.size)
            // The shared policy has quality buckets and deterministic tie breakers, not a bespoke quality sort.
            assertEquals(links.sortedWith(com.cloudstream.tr.core.model.StreamPrioritySorter.comparator()), links)
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun officialLiveChannelDetailAndNativeMp4ByteProbe() = runBlocking {
        org.junit.Assume.assumeTrue("Explicit network smoke only", System.getenv("BLENDER_LIVE_TEST") == "1")
        fun connection(target: String) = (java.net.URL(target).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000; instanceFollowRedirects = false
        }
        val catalog = BlenderCatalog { target ->
            val conn = connection(target)
            try {
                assertEquals(target, 200, conn.responseCode)
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally { conn.disconnect() }
        }
        val provider = BlenderOpenMovies().apply { catalogOverride = catalog }
        assertEquals("Sintel", provider.search("Sintel", 1).items.single().name)
        assertEquals(2010, (provider.load(url) as MovieLoadResponse).year)
        val detail = catalog.detail(url)!!
        assertTrue(detail.second.isNotEmpty())
        val probed = ConcurrentHashMap<String, Int>()
        StreamValidator.transport = StreamHttpTransport { target, headers ->
            assertTrue(BlenderCatalog.isPublicFile(target, uuid))
            val conn = connection(target)
            headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
            try {
                val code = conn.responseCode
                val type = conn.contentType
                val bytes = if (code in 200..299) conn.inputStream.use { StreamValidator.readBoundedBytes(it) } else byteArrayOf()
                probed[target] = code
                println("BLENDER_LIVE media=$target status=$code mime=$type bytes=${bytes.size} ftyp=${bytes.size >= 8 && String(bytes.sliceArray(4..7)) == "ftyp"}")
                StreamHttpResponse(code, type) { bytes.inputStream() }
            } finally { conn.disconnect() }
        }
        try {
            val links = mutableListOf<ExtractorLink>()
            assertTrue(provider.loadLinks(url, false, {}, links::add))
            assertTrue(links.any { it.quality == 240 })
            assertEquals(detail.second.size, probed.size)
            assertTrue(links.all { probed[it.url] == 206 && it.audioTracks.isNullOrEmpty() })
            println("BLENDER_LIVE search/load/refresh/loadLinks passed; callbacks=${links.size}; audio=UNKNOWN; device playback=NOT_TESTED")
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun cancellationFromFirstCallbackStopsFurtherDelivery() = runBlocking {
        val provider = BlenderOpenMovies().apply { catalogOverride = BlenderCatalog { fixture() } }
        val links = mutableListOf<ExtractorLink>()
        StreamValidator.transport = StreamHttpTransport { _, _ ->
            StreamHttpResponse(206, "video/mp4") { byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112).inputStream() }
        }
        try {
            val job = launch {
                val playerContext = currentCoroutineContext()
                provider.loadLinks(url, false, {}, {
                    links += it
                    playerContext.cancel()
                })
            }
            job.join()
            assertTrue(job.isCancelled)
            assertEquals("No delivery after the player cancels", 1, links.size)
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun unknownPreflightRemainsFallbackWithoutClaimingPlayback() = runBlocking {
        val provider = BlenderOpenMovies().apply { catalogOverride = BlenderCatalog { fixture() } }
        val links = mutableListOf<ExtractorLink>()
        val probes = AtomicInteger()
        StreamValidator.transport = StreamHttpTransport { _, _ ->
            probes.incrementAndGet()
            throw java.io.IOException("Offline indeterminate probe")
        }
        try {
            assertTrue(provider.loadLinks(url, false, {}, links::add))
            assertEquals(2, links.size)
            assertEquals(2, probes.get())
            assertTrue(links.all { it.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO &&
                it.referer == url && it.audioTracks.isNullOrEmpty() && !it.name.contains("working", true) })
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun knownAliasesCannotProduceEqualTitleYearCandidatesWithDistinctUrls() = runBlocking {
        val aliases = mapper.readTree(javaClass.getResource("/big-buck-bunny-aliases.json")!!.readText())
            .path("data").map { it as ObjectNode }
        val primary = "bf1f3fb5-b119-4f9f-9930-8e20e892b898"
        for (rows in listOf(aliases, aliases.reversed())) {
            val catalog = BlenderCatalog { catalogJson(*rows.toTypedArray()) }
            assertEquals(listOf(primary), catalog.page(1).films.map { it.uuid })
            // Matching must receive one confident (Big Buck Bunny, 2008) identity, not two URLs.
            assertEquals(listOf(primary), catalog.search("Big Buck Bunny").map { it.uuid })
        }
        val requests = mutableListOf<String>()
        val crossPage = BlenderCatalog { request ->
            requests += request
            if (request.contains("start=0&")) catalogJson(*Array(20) { aliases[0] }, total = 21)
            else catalogJson(aliases[1], total = 21)
        }
        assertEquals(listOf(primary), crossPage.search("Big Buck Bunny").map { it.uuid })
        assertEquals(2, requests.size)
        val unknown = node().apply {
            put("uuid", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"); put("name", "Big Buck Bunny")
            (path("licence") as ObjectNode).put("id", 1)
        }
        val other = unknown.deepCopy().apply { put("uuid", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb") }
        val distinct = BlenderCatalog { catalogJson(*aliases.toTypedArray(), unknown, other) }.search("Big Buck Bunny")
        assertEquals(3, distinct.size)
        assertEquals(2, distinct.count { it.year == null })
    }

    @Test fun reportedQualityLabelOverridesNonstandardIdWithoutInventingMarketingQuality() = runBlocking {
        suspend fun quality(id: Int, label: String?): Int? {
            val root = node()
            val file = (root.path("files")[0] as ObjectNode).deepCopy()
            val resolution = file.path("resolution") as ObjectNode
            resolution.put("id", id)
            if (label != null) resolution.put("label", label)
            root.set<com.fasterxml.jackson.databind.JsonNode>("files", mapper.createArrayNode().add(file))
            val detail = BlenderCatalog { root.toString() }.detail(url)!!
            assertEquals(uuid, detail.first.uuid)
            assertEquals("Sintel", detail.first.title)
            assertEquals(2010, detail.first.year)
            return detail.second.singleOrNull()?.quality
        }
        assertEquals(1080, quality(818, "1080p"))
        assertEquals(720, quality(818, "720p"))
        assertEquals(818, quality(818, null))
        assertEquals(818, quality(818, "remux4k"))
        assertEquals(720, quality(720, "99999p"))
        assertNull(quality(-1, "-1080p"))
        assertNull(quality(0, "0p"))
        assertNull(quality(8641, "8641p"))

        val root = node()
        for ((index, resolution) in listOf(818 to "1080p", 720 to "720p").withIndex()) {
            val file = root.path("files")[index] as ObjectNode
            (file.path("resolution") as ObjectNode).put("id", resolution.first).put("label", resolution.second)
            file.put("fileUrl", "${BlenderCatalog.BASE}/object-storage/web_videos/$uuid-${resolution.first}.mp4")
        }
        val provider = BlenderOpenMovies().apply { catalogOverride = BlenderCatalog { root.toString() } }
        StreamValidator.transport = StreamHttpTransport { _, headers ->
            assertEquals(url, headers["Referer"])
            StreamHttpResponse(206, "video/mp4") { byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112).inputStream() }
        }
        try {
            val links = mutableListOf<ExtractorLink>()
            assertTrue(provider.loadLinks(url, false, {}, links::add))
            assertEquals(listOf(1080, 720), links.map { it.quality })
            assertTrue(links.first().url.endsWith("-818.mp4"))
            assertTrue(links.all { it.referer == url && it.audioTracks.isNullOrEmpty() })
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun sharedAdmissionLimitBoundsPreflightsAndDelivery() = runBlocking {
        val root = node()
        val files = mapper.createArrayNode()
        repeat(140) { index ->
            files.add(mapper.createObjectNode().apply {
                putObject("resolution").put("id", 720)
                put("fileUrl", "${BlenderCatalog.BASE}/object-storage/web_videos/$uuid-${index + 1}.mp4")
            })
        }
        root.set<com.fasterxml.jackson.databind.JsonNode>("files", files)
        val provider = BlenderOpenMovies().apply { catalogOverride = BlenderCatalog { root.toString() } }
        val probes = ConcurrentHashMap<String, AtomicInteger>()
        StreamValidator.transport = StreamHttpTransport { media, headers ->
            probes.computeIfAbsent(media) { AtomicInteger() }.incrementAndGet()
            assertEquals(url, headers["Referer"])
            StreamHttpResponse(503, "text/plain") { "Temporarily unavailable".byteInputStream() }
        }
        try {
            val links = mutableListOf<ExtractorLink>()
            assertTrue(provider.loadLinks(url, false, {}, links::add))
            assertEquals(128, links.size)
            assertEquals(128, probes.size)
            assertTrue(probes.values.all { it.get() == 1 })
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun nativeFileContractRejectsDetectedManifestWithoutChangingCandidateType() = runBlocking {
        val provider = BlenderOpenMovies().apply { catalogOverride = BlenderCatalog { fixture() } }
        StreamValidator.transport = StreamHttpTransport { _, _ ->
            StreamHttpResponse(200, "application/vnd.apple.mpegurl") {
                "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nsegment.ts\n#EXT-X-ENDLIST".byteInputStream()
            }
        }
        try {
            val links = mutableListOf<ExtractorLink>()
            assertFalse(provider.loadLinks(url, false, {}, links::add))
            assertTrue(links.isEmpty())
        } finally { StreamValidator.resetTransport() }
    }
}
