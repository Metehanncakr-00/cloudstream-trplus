package com.cloudstream.tr.core

import com.cloudstream.tr.core.streaming.StreamCacheManager
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class StreamCacheManagerTest {
    private val link = runBlocking {
        newExtractorLink("Test", "Test source", "https://cdn.example/stream.m3u8", ExtractorLinkType.M3U8)
    }

    @After
    fun clearCache() {
        StreamCacheManager.clear()
    }

    @Test
    fun entryIsAvailableBeforeTtlAndExpiresAtTtlBoundary() {
        val timestamp = 1_000L
        StreamCacheManager.putAt("ttl", listOf(link), timestamp)

        assertEquals(listOf(link), StreamCacheManager.getAt("ttl", timestamp + 12 * 60 * 60 * 1000L - 1))
        assertNull(StreamCacheManager.getAt("ttl", timestamp + 12 * 60 * 60 * 1000L))
        assertNull(StreamCacheManager.getAt("ttl", timestamp + 12 * 60 * 60 * 1000L + 1))
    }

    @Test
    fun emptyEntriesAreIgnoredAndClearRemovesCachedEntries() {
        StreamCacheManager.put("empty", emptyList())
        assertNull(StreamCacheManager.get("empty"))

        StreamCacheManager.put("cached", listOf(link))
        assertEquals(listOf(link), StreamCacheManager.get("cached"))
        StreamCacheManager.clear()
        assertNull(StreamCacheManager.get("cached"))
    }
}
