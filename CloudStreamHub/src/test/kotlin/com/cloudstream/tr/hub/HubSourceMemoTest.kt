package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class HubSourceMemoTest {
    private fun link(i: Int, type: ExtractorLinkType = ExtractorLinkType.MAGNET) = ExtractorLink(
        source = "Torrentio", name = "2160p",
        url = if (type == ExtractorLinkType.MAGNET)
            "magnet:?xt=urn:btih:" + i.toString(16).padStart(40, '0') + "&index=0"
        else "https://cdn.example/file?token=rotating", referer = "", quality = 2160, type = type
    )

    @Test fun parallelSameKeyUsesOneRequest() = runTest {
        val memo = HubSourceMemo()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val first = async { memo.resolve("movie") {
            calls++
            release.await()
            HubLookupOutcome(listOf(link(1)))
        } }
        runCurrent()
        val second = async { memo.resolve("movie") {
            calls++
            HubLookupOutcome(listOf(link(2)))
        } }
        runCurrent()
        assertEquals(1, calls)
        release.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, calls)
    }

    @Test fun onlyAuthoritativeEmptyIsNegativelyCached() = runTest {
        val now = AtomicLong(1_000)
        val memo = HubSourceMemo(clock = { now.get() })
        var calls = 0
        suspend fun lookup(definitive: Boolean) = HubLookupOutcome(
            emptyList(), conclusiveEmpty = definitive
        )
        memo.resolve("network_failure") { calls++; lookup(false) }
        memo.resolve("network_failure") { calls++; lookup(false) }
        assertEquals(2, calls)
        memo.resolve("real_empty") { calls++; lookup(true) }
        memo.resolve("real_empty") { calls++; lookup(false) }
        assertEquals(3, calls)
        now.addAndGet(10_001)
        memo.resolve("real_empty") { calls++; lookup(false) }
        assertEquals(4, calls)
    }

    @Test fun directUrlsAreNeverReusedAndPositiveMagnetExpires() = runTest {
        val now = AtomicLong(1_000)
        val memo = HubSourceMemo(clock = { now.get() }, maximum = 1)
        var calls = 0
        memo.resolve("direct") { calls++; HubLookupOutcome(listOf(link(1, ExtractorLinkType.VIDEO))) }
        memo.resolve("direct") { calls++; HubLookupOutcome(listOf(link(1, ExtractorLinkType.VIDEO))) }
        assertEquals(2, calls)
        memo.resolve("magnet") { calls++; HubLookupOutcome(listOf(link(1))) }
        memo.resolve("magnet") { calls++; HubLookupOutcome(listOf(link(2))) }
        assertEquals(3, calls)
        assertEquals(1, memo.size())
        now.addAndGet(30_001)
        memo.resolve("magnet") { calls++; HubLookupOutcome(listOf(link(2))) }
        assertEquals(4, calls)
    }
}
