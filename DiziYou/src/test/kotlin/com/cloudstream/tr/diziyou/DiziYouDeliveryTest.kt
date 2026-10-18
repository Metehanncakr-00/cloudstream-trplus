package com.cloudstream.tr.diziyou

import com.cloudstream.tr.core.network.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DiziYouDeliveryTest {
    private val provider = DiziYou()
    private val doc = Jsoup.parse("""<iframe id="diziyouPlayer" src="/player/123.html"></iframe><iframe src="https://embed.invalid/late"></iframe>""")
    private fun fallback(id: String) = ExtractorLink("fallback", id, "https://media.invalid/$id", "https://embed.invalid/late", 720,
        type = ExtractorLinkType.M3U8, headers = mapOf("X-Test" to "preserved"), extractorData = "opaque", audioTracks = emptyList())
    private fun master(language: String) = """#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",LANGUAGE="$language"
#EXT-X-STREAM-INF:AUDIO="a",BANDWIDTH=1
video.m3u8
"""

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun defaultBudgetsRetainAllUnknownCandidatesBeforeHubDeadline() = runTest {
        val delivered = mutableListOf<ExtractorLink>()
        val deliveryTimes = mutableListOf<Long>()
        val fallbackLinks = (1..6).map { fallback("stalled-$it") }
        var probes = 0
        StreamValidator.transport = StreamHttpTransport { _, _ ->
            probes++
            awaitCancellation()
        }
        try {
            val result = withTimeoutOrNull(30_000L) {
                provider.deliverPageLinks(doc, "https://www.diziyou.one/episode", {}, {
                    delivered.add(it)
                    deliveryTimes.add(currentTime)
                }, resolveFallback = { _, emit ->
                    fallbackLinks.forEach(emit)
                    awaitCancellation()
                })
            }
            assertNotNull("Default delivery must finish before the Hub deadline", result)
            assertEquals(8, result)
            assertEquals(8, probes)
            assertEquals(8, delivered.size)
            assertEquals(8, delivered.map { it.url }.distinct().size)
            assertTrue(fallbackLinks.all { link -> delivered.any { it === link } })
            assertEquals(2, delivered.count { it.url.contains("/episodes/123") })
            assertTrue(deliveryTimes.all { it < 30_000L })
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun standaloneWaitsForLateFallbackThenUsesOneProbeAndDropsInvalidNative() = runBlocking {
        val requests = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
        val delivered = mutableListOf<ExtractorLink>()
        val turkish = fallback("tr").apply { audioTracks = listOf(com.lagradost.cloudstream3.newAudioFile("https://audio.invalid/tr")) }
        StreamValidator.transport = StreamHttpTransport { url, headers ->
            requests.computeIfAbsent(url) { AtomicInteger() }.incrementAndGet()
            assertEquals(1, headers.keys.count { it.equals("Referer", true) })
            val body = when { url.endsWith("/tr") -> master("tr"); url.endsWith("/fa") -> master("fas"); else -> "#EXTM3U\n#EXTINF:1\npart.ts\n" }
            StreamHttpResponse(if (url.contains("123_tr")) 404 else 200, "application/vnd.apple.mpegurl") { body.byteInputStream() }
        }
        try {
            val count = provider.deliverPageLinks(doc, "https://www.diziyou.one/episode", {}, delivered::add,
                resolveFallback = { _, emit ->
                    assertTrue(delivered.isEmpty())
                    delay(20)
                    emit(fallback("fa")); emit(turkish)
                })
            assertEquals(3, count)
            assertSame(turkish, delivered.first())
            assertEquals("DiziYou HLS", delivered[1].name)
            assertEquals("fa", delivered.last().name)
            assertEquals(turkish.audioTracks, delivered.first().audioTracks)
            assertEquals("opaque", delivered.first().extractorData)
            assertEquals(turkish.headers, delivered.first().headers)
            assertEquals(4, requests.size)
            assertTrue(requests.values.all { it.get() == 1 })
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun guessedNativeTurkishStaysUnknownAndInternalDiscoveryTimeoutRetainsPartialLinks() = runBlocking {
        StreamValidator.transport = StreamHttpTransport { url, _ ->
            val body = if (url.endsWith("/fa")) master("dut") else "#EXTM3U\n#EXTINF:1\npart.ts\n"
            StreamHttpResponse(200, "application/vnd.apple.mpegurl") { body.byteInputStream() }
        }
        try {
            val delivered = mutableListOf<ExtractorLink>()
            assertEquals(3, provider.deliverPageLinks(doc, "https://www.diziyou.one/episode", {}, delivered::add,
                discoveryBudgetMs = 50, resolveFallback = { _, emit -> emit(fallback("fa")); delay(1000) }))
            assertEquals("DiziYou HLS", delivered.first().name)
            assertEquals("fa", delivered.last().name)
            assertTrue(delivered.any { it.url.contains("123_tr") })
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun cancellationWhileFallbackResolvesNeverEmitsNativeCandidates() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val delivered = mutableListOf<ExtractorLink>()
        var probes = 0
        StreamValidator.transport = StreamHttpTransport { _, _ -> probes++; error("No probe expected") }
        try {
            val job = launch {
                provider.deliverPageLinks(doc, "https://www.diziyou.one/episode", {}, delivered::add,
                    resolveFallback = { _, _ -> entered.complete(Unit); awaitCancellation() })
            }
            entered.await(); job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertTrue(delivered.isEmpty())
            assertEquals(0, probes)
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun standalonePreflightBudgetKeepsPartialMetadataAndTimedOutUnknown() = runBlocking {
        val cancelled = AtomicInteger()
        StreamValidator.transport = StreamHttpTransport { url, _ ->
            if (url.contains("123_tr")) {
                try { awaitCancellation() } finally { cancelled.incrementAndGet() }
            }
            val body = if (url.endsWith("/tr")) master("tr") else "#EXTM3U\n#EXTINF:1\npart.ts\n"
            StreamHttpResponse(200, "application/vnd.apple.mpegurl") { body.byteInputStream() }
        }
        try {
            val delivered = mutableListOf<ExtractorLink>()
            assertEquals(3, provider.deliverPageLinks(doc, "https://www.diziyou.one/episode", {}, delivered::add,
                preflightBudgetMs = 100, resolveFallback = { _, emit -> emit(fallback("tr")) }))
            assertEquals("tr", delivered.first().name)
            assertTrue(delivered.any { it.url.contains("123_tr") })
            assertEquals(1, cancelled.get())
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun externalCancellationDuringActualInjectedTransportPreflightDeliversNothing() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = AtomicInteger()
        val delivered = mutableListOf<ExtractorLink>()
        StreamValidator.transport = StreamHttpTransport { _, _ ->
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.incrementAndGet() }
        }
        try {
            val job = launch {
                provider.deliverPageLinks(doc, "https://www.diziyou.one/episode", {}, delivered::add,
                    resolveFallback = { _, _ -> })
            }
            entered.await(); job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertTrue(delivered.isEmpty())
            assertTrue(cancelled.get() > 0)
        } finally { StreamValidator.resetTransport() }
    }
}
