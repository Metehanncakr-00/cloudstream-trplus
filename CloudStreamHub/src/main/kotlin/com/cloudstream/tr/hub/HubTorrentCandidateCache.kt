package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.LinkedHashMap

/** Bounded in-memory cache: no expiring CDN URLs and no twelve-hour peer assumptions. */
internal object HubTorrentCandidateCache {
    const val MAX_KEYS = 64
    const val TTL_MS = 30 * 60 * 1000L
    private data class Entry(val time: Long, val links: List<ExtractorLink>)
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    @Synchronized fun put(key: String, links: List<ExtractorLink>, now: Long = System.currentTimeMillis()) {
        val safe = cacheableHubLinks(links).take(32)
        if (safe.isEmpty()) return
        entries[key] = Entry(now, safe)
        val iterator = entries.entries.iterator()
        while (entries.size > MAX_KEYS && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    @Synchronized fun get(key: String, now: Long = System.currentTimeMillis()): List<ExtractorLink>? {
        val entry = entries[key] ?: return null
        if (now < entry.time || now - entry.time > TTL_MS) {
            entries.remove(key)
            return null
        }
        return entry.links.toList()
    }

    @Synchronized fun size(): Int = entries.size
    @Synchronized fun clear() = entries.clear()
}
