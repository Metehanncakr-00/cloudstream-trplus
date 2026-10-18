package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.junit.Assert.*
import org.junit.Test

/** Regression: cache admission must not expose expired direct CDN URLs. */
class HubCachePolicyTest {
    private fun link(url: String, type: ExtractorLinkType) = ExtractorLink(
        source = "Test", name = "1080p", url = url, referer = "",
        quality = 1080, type = type
    )

    @Test fun cacheExpiresAndEvictsOldKeysBeforeConsumingMemory() {
        HubTorrentCandidateCache.clear()
        val magnet = link("magnet:?xt=urn:btih:0000000000000000000000000000000000000001&index=0", ExtractorLinkType.MAGNET)
        HubTorrentCandidateCache.put("old", listOf(magnet), now = 10L)
        assertNotNull(HubTorrentCandidateCache.get("old", now = 10L))
        assertNull(HubTorrentCandidateCache.get("old", now = HubTorrentCandidateCache.TTL_MS + 11L))
        repeat(HubTorrentCandidateCache.MAX_KEYS + 5) {
            HubTorrentCandidateCache.put("cache_" + it, listOf(magnet), now = 1000L)
        }
        assertTrue(HubTorrentCandidateCache.size() <= HubTorrentCandidateCache.MAX_KEYS)
        assertNull(HubTorrentCandidateCache.get("cache_0", now = 1000L))
        HubTorrentCandidateCache.clear()
    }

    @Test fun hubCachesOnlyTorrentCandidates() {
        val key = "cache_policy_test"
        try {
            val direct = link("https://cdn.example/video.m3u8?token=expired", ExtractorLinkType.M3U8)
            val torrent = link("magnet:?xt=urn:btih:0000000000000000000000000000000000000001&index=0", ExtractorLinkType.MAGNET)
            HubTorrentCandidateCache.put(key, listOf(direct, torrent).let(::cacheableHubLinks))
            assertEquals(listOf(torrent), HubTorrentCandidateCache.get(key))
            assertFalse(HubTorrentCandidateCache.get(key).orEmpty().any { it.url.contains("expired") })
        } finally {
            HubTorrentCandidateCache.clear()
        }
    }
}
