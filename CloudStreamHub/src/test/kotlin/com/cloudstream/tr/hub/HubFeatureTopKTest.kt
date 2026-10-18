package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.junit.Assert.*
import org.junit.Test

class HubFeatureTopKTest {
    private fun candidate(i: Int, quality: Int, title: String = "feature") = ExtractorLink(
        source = "Provider", name = title,
        url = "https://cdn.example/video$i.mkv", referer = "", quality = quality,
        type = ExtractorLinkType.VIDEO
    )

    @Test fun lateArrivingRemuxReplacesWeakestPendingCandidate() {
        val top = HubFeatureTopK(3)
        (1..9).forEach { top.offer(candidate(it, 2160)) }
        top.offer(candidate(10, 2160, "2160p BluRay REMUX (20 seeds)"))
        assertEquals(3, top.size())
        assertTrue(top.snapshot().any { it.url.contains("video10.mkv") })
        top.offer(candidate(10, 2160, "2160p BluRay REMUX (20 seeds)"))
        assertEquals(3, top.size())
    }

    @Test fun strongerResolutionWinsAndMemoryRemainsBounded() {
        val top = HubFeatureTopK(2)
        top.offer(candidate(1, 1080))
        top.offer(candidate(2, 2160))
        top.offer(candidate(3, 2160))
        assertEquals(2, top.size())
        assertFalse(top.snapshot().any { it.url.contains("video1.mkv") })
        assertEquals(2160, top.snapshot().first().quality)
        val empty = HubFeatureTopK(0)
        empty.offer(candidate(1, 2160))
        assertTrue(empty.snapshot().isEmpty())
    }
}
