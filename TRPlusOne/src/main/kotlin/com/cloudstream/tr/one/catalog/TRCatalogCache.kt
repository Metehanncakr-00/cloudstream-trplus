package com.cloudstream.tr.one.catalog

import com.cloudstream.tr.one.models.CanonicalMedia
import java.util.concurrent.ConcurrentHashMap

object TRCatalogCache {

    private const val MAX_ITEMS_PER_CATEGORY = 100
    private const val TTL_MS = 10 * 60 * 1000L // 10 dakika önbellek

    data class CacheEntry(
        val items: List<CanonicalMedia>,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private fun makeKey(category: String, page: Int): String = "$category:$page"

    fun get(category: String, page: Int): List<CanonicalMedia>? {
        val entry = cache[makeKey(category, page)] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > TTL_MS) {
            // Süresi dolmuş
            return null
        }
        return entry.items
    }

    fun put(category: String, page: Int, items: List<CanonicalMedia>) {
        if (items.isEmpty()) return // Boş yanıt başarılı önbelleği ezemez!
        val bounded = items.take(MAX_ITEMS_PER_CATEGORY)
        cache[makeKey(category, page)] = CacheEntry(bounded)
    }

    fun clear() {
        cache.clear()
    }
}
