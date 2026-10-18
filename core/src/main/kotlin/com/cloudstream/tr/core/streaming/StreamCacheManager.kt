package com.cloudstream.tr.core.streaming

import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.concurrent.ConcurrentHashMap

data class CachedStreamEntry(
    val timestamp: Long,
    val links: List<ExtractorLink>
)

object StreamCacheManager {
    // 12 hours Time-To-Live for resolved streams
    private const val TTL_MS = 12 * 60 * 60 * 1000L

    private val cache = ConcurrentHashMap<String, CachedStreamEntry>()

    fun buildKey(
        title: String,
        year: Int? = null,
        isMovie: Boolean,
        season: Int? = null,
        episode: Int? = null
    ): String {
        val clean = title.lowercase().trim().replace(Regex("""\s+"""), "_")
        return if (isMovie) {
            "movie_${clean}_${year ?: 0}"
        } else {
            "series_${clean}_s${season ?: 1}e${episode ?: 1}"
        }
    }

    fun get(key: String): List<ExtractorLink>? {
        return getAt(key, System.currentTimeMillis())
    }

    internal fun getAt(key: String, nowMs: Long): List<ExtractorLink>? {
        val entry = cache[key] ?: return null
        if (nowMs - entry.timestamp >= TTL_MS) {
            cache.remove(key)
            return null
        }
        return entry.links
    }

    fun put(key: String, links: List<ExtractorLink>) {
        putAt(key, links, System.currentTimeMillis())
    }

    internal fun putAt(key: String, links: List<ExtractorLink>, timestampMs: Long) {
        if (links.isEmpty()) return
        cache[key] = CachedStreamEntry(
            timestamp = timestampMs,
            links = links
        )
    }

    fun clear() {
        cache.clear()
    }
}
