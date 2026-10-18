package com.cloudstream.tr.hub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class HubTorrentVlcActionTest {
    @Test fun mxResumePositionClampsWithoutIntegerOverflow() {
        assertEquals(0, mxPlayerPosition(null))
        assertEquals(0, mxPlayerPosition(-1L))
        assertEquals(123456, mxPlayerPosition(123456L))
        assertEquals(Int.MAX_VALUE, mxPlayerPosition(Long.MAX_VALUE))
    }

    private fun link(url: String, type: ExtractorLinkType) = ExtractorLink(
        source = "Torrentio", name = "2160p", url = url, referer = "", quality = 2160,
        type = type
    )

    @Test fun convertsOnlySelectedTorrentWithoutChangingFileIndex() = runBlocking {
        val selected = link("magnet:?xt=urn:btih:example&index=2", ExtractorLinkType.MAGNET)
        val output = link("http://127.0.0.1:44075/stream/hash/2", ExtractorLinkType.VIDEO)
        val result = prepareTorrentStream(listOf(link("other", ExtractorLinkType.MAGNET), selected), 1,
            consent = { true }, transform = { assertSame(selected, it); output })
        assertSame(output, result)
    }

    @Test fun readinessRunsAfterLocalValidationAndBeforeReturningLink() = runBlocking {
        val order = mutableListOf<String>()
        val output = link("http://127.0.0.1:44075/stream/a?link=hash&index=2&play", ExtractorLinkType.VIDEO)
        val result = prepareTorrentStream(listOf(link("magnet:?xt=example", ExtractorLinkType.MAGNET)), 0,
            consent = { order.add("consent"); true },
            readiness = { assertSame(output, it); order.add("ready") },
            transform = { order.add("transform"); output })
        order.add("handoff")
        assertSame(output, result)
        assertEquals(listOf("consent", "transform", "ready", "handoff"), order)
    }

    @Test fun remoteTransformAndPreparationFailurePreventHandoff() = runBlocking {
        for (remote in listOf(true, false)) {
            var prepared = false
            try {
                prepareTorrentStream(listOf(link("magnet:?xt=example", ExtractorLinkType.MAGNET)), 0,
                    consent = { true }, readiness = { prepared = true; throw ErrorLoadingException("Not ready") },
                    transform = { link(if (remote) "https://example.com/video" else
                        "http://127.0.0.1:44075/stream/a", ExtractorLinkType.VIDEO) })
                fail("Must not hand off")
            } catch (_: ErrorLoadingException) { assertEquals(!remote, prepared) }
        }
    }

    @Test fun rejectsUnselectedNonTorrentAndDeniedConsentBeforeStartingServer() = runBlocking {
        val torrent = link("magnet:?xt=example", ExtractorLinkType.MAGNET)
        for ((links, index, allowed) in listOf(
            Triple(listOf(torrent), null, true),
            Triple(listOf(torrent), 3, true),
            Triple(listOf(link("https://example.com/a", ExtractorLinkType.VIDEO)), 0, true),
            Triple(listOf(torrent), 0, false)
        )) {
            try {
                prepareTorrentStream(links, index, { allowed }) { fail("Server must not start"); it }
                fail("Expected rejection")
            } catch (_: ErrorLoadingException) { }
        }
    }

    @Test fun cancellationFromTorrentBootstrapPropagates() = runBlocking {
        val cancellation = CancellationException("cancelled")
        try {
            prepareTorrentStream(listOf(link("magnet:?xt=example", ExtractorLinkType.MAGNET)), 0,
                { true }) { throw cancellation }
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
    }

    @Test
    fun acceptsOnlyLocalServerStreams() {
        assertTrue(isLocalTorrentStream("http://127.0.0.1:44075/stream/hash/0"))
        listOf(
            "magnet:?xt=urn:btih:example",
            "https://example.com/video.mp4",
            "http://127.0.0.1.evil.example:44075/stream",
            "http://user@127.0.0.1:44075/stream",
            "http://127.0.0.1/stream",
            "http://127.0.0.1:65536/stream",
            "http://127.0.0.1:44075",
            "not a uri"
        ).forEach { assertFalse(it, isLocalTorrentStream(it)) }
    }
}
