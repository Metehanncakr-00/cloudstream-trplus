package com.cloudstream.tr.core.streaming

import com.cloudstream.tr.core.network.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BoundedLinkDeliveryTest {
    private fun link(id: String) = ExtractorLink("source", id, "https://test.invalid/$id", "https://referer.invalid/", 720,
        type = ExtractorLinkType.M3U8, headers = mapOf("referer" to "https://explicit.invalid/", "Cookie" to "test=value"), extractorData = "opaque", audioTracks = emptyList())
    private fun master(language: String) = """#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",LANGUAGE="$language"
#EXT-X-STREAM-INF:AUDIO="a",BANDWIDTH=1
video.m3u8
"""

    @Test fun oneResponsePerCandidatePreservesIdentityHeadersTracksAndFallbacks() = runBlocking {
        val requests = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
        val closed = AtomicInteger()
        val receivedHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
        StreamValidator.transport = StreamHttpTransport { url, headers ->
            requests.computeIfAbsent(url) { AtomicInteger() }.incrementAndGet()
            receivedHeaders[url] = headers
            val id = url.substringAfterLast('/')
            val code = if (id == "404") 404 else if (id == "transient") 503 else 200
            val text = when (id) { "tr" -> master("tr"); "fa" -> master("per"); "html" -> "<html>error</html>"; else -> "#EXTM3U\n#EXTINF:1\nx.ts\n" }
            StreamHttpResponse(code, if (id == "html") "text/html" else "application/vnd.apple.mpegurl") {
                object : java.io.ByteArrayInputStream(text.toByteArray()) {
                    override fun close() { closed.incrementAndGet(); super.close() }
                }
            }
        }
        try {
            val candidates = listOf("unknown_tr", "fa", "404", "html", "transient", "tr").map(::link)
            val track = com.lagradost.cloudstream3.newAudioFile("https://audio.invalid/track")
            candidates.last().audioTracks = listOf(track)
            val delivered = mutableListOf<ExtractorLink>()
            assertEquals(4, BoundedLinkDelivery.collectAndDeliver(
                discover = { emit -> candidates.forEach(emit); emit(candidates.first()) }, callback = delivered::add))
            assertSame(candidates.last(), delivered.first())
            assertSame(candidates[0], delivered[1])
            assertTrue(delivered.contains(candidates[0]))
            assertTrue(delivered.contains(candidates[4]))
            assertEquals(listOf(track), delivered.first().audioTracks)
            assertEquals("opaque", delivered.first().extractorData)
            assertEquals(candidates.last().headers, delivered.first().headers)
            assertEquals("https://referer.invalid/", delivered.first().referer)
            assertEquals(6, requests.size)
            assertTrue(requests.values.all { it.get() == 1 })
            assertEquals(4, closed.get())
            assertTrue(receivedHeaders.values.all { headers -> headers.keys.count { it.equals("Referer", true) } == 1 && headers["referer"] == "https://explicit.invalid/" })
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun budgetsPreservePartialAndUnprobedCandidatesAndBoundAdmissionConcurrency() = runBlocking {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val calls = AtomicInteger()
        val delivered = mutableListOf<ExtractorLink>()
        val magnet = ExtractorLink("source", "magnet", "magnet:?xt=test", "", 720, type = ExtractorLinkType.MAGNET, audioTracks = emptyList())
        BoundedLinkDelivery.collectAndDeliver(
            discover = { emit -> emit(magnet); (1..20).map { link("$it") }.forEach(emit); delay(1000) },
            callback = delivered::add, maxCandidates = 6, maxConcurrency = 2,
            discoveryBudgetMs = 30, preflightBudgetMs = 100, probeTimeoutMs = 1000,
            probe = { _, _ ->
                calls.incrementAndGet()
                val count = active.incrementAndGet(); maximum.updateAndGet { maxOf(it, count) }
                try { delay(1000); PreflightResult(ValidationStatus.VALID, ExtractorLinkType.M3U8) }
                finally { active.decrementAndGet() }
            })
        assertEquals(6, delivered.size)
        assertTrue(delivered.contains(magnet))
        assertEquals(2, calls.get())
        assertEquals(2, maximum.get())
        assertEquals(0, active.get())
    }

    @Test fun externalCancellationDuringDiscoveryOrPreflightDoesNotDeliver() = runBlocking {
        for (duringDiscovery in listOf(true, false)) {
            val entered = CompletableDeferred<Unit>()
            val delivered = mutableListOf<ExtractorLink>()
            val job = launch {
                BoundedLinkDelivery.collectAndDeliver(
                    discover = { emit -> emit(link("one")); if (duringDiscovery) { entered.complete(Unit); awaitCancellation() } },
                    callback = delivered::add,
                    probe = { _, _ -> entered.complete(Unit); awaitCancellation() })
            }
            entered.await(); job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertTrue(delivered.isEmpty())
        }
    }

    @Test fun headerVariantsAreNotDedupedAndCallbackCancellationStopsDelivery() = runBlocking {
        val a = link("same")
        val b = link("same").apply { headers = mapOf("Authorization" to "test") }
        val delivered = mutableListOf<ExtractorLink>()
        val probed = AtomicInteger()
        val job = launch(start = CoroutineStart.LAZY) {
            val context = currentCoroutineContext()
            BoundedLinkDelivery.collectAndDeliver(
                discover = { emit -> emit(a); emit(a); emit(b) },
                callback = { delivered.add(it); context.cancel() },
                probe = { _, _ -> probed.incrementAndGet(); PreflightResult(ValidationStatus.VALID, ExtractorLinkType.M3U8) })
        }
        job.start(); job.join()
        assertEquals(2, probed.get())
        assertEquals(1, delivered.size)
        assertTrue(job.isCancelled)
    }

    @Test fun overallProbeTimeoutKeepsCompletedEvidenceAndIndeterminateButNotDefiniteInvalids() = runBlocking {
        val candidates = listOf("unknown", "invalid", "transient", "tr", "slow").map(::link)
        val delivered = mutableListOf<ExtractorLink>()
        BoundedLinkDelivery.collectAndDeliver(
            discover = { emit -> candidates.forEach(emit) }, callback = delivered::add,
            maxConcurrency = 1, preflightBudgetMs = 100, probeTimeoutMs = 1000,
            probe = { link, _ -> when (link.name) {
                "invalid" -> PreflightResult(ValidationStatus.INVALID, link.type)
                "transient" -> PreflightResult(ValidationStatus.INDETERMINATE, link.type)
                "tr" -> PreflightResult(ValidationStatus.VALID, link.type,
                    languageEvidence = com.cloudstream.tr.core.model.ManifestLanguageEvidence(setOf("tr")))
                "slow" -> { delay(1000); PreflightResult(ValidationStatus.VALID, link.type) }
                else -> PreflightResult(ValidationStatus.VALID, link.type)
            } })
        assertEquals(4, delivered.size)
        assertSame(candidates[3], delivered.first())
        assertTrue(delivered.contains(candidates[2]))
        assertTrue(delivered.contains(candidates[4]))
        assertFalse(delivered.contains(candidates[1]))
    }
}
