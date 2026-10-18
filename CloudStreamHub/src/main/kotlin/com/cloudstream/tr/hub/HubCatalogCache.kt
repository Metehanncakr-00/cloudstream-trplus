package com.cloudstream.tr.hub

import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-RAM safe, TTL-based catalog cache and crawler resumption state.
 * Supports:
 * - Isolated namespaces: Unified Catalog vs Individual Provider Raw Crawl
 * - Versioned disk persistence with migration support (v4)
 * - Category- and provider-isolated crawler cursors, retries, and completion state
 * - Automatic cache eviction and size bounding (up to 300 items per page)
 */
object HubCatalogCache {

    private const val CACHE_VERSION = 4
    private const val PREFS_CRAWLER = "CloudStreamHub_Crawler_Cursor_v4"
    private const val DISK_CACHE_DIR = "hub_catalog_cache_v4"
    private const val DEFAULT_TTL_MS = 60 * 60 * 1000L // 1 hour
    private const val MAX_ITEMS_PER_PAGE = 300
    private const val MAX_DISK_FILES = 500

    var customDiskCacheDir: File? = null // For deterministic unit testing

    private object CacheSearchResponseBuilder : MainAPI() {
        override var name = "CloudStreamHub"
        override var mainUrl = "https://api.themoviedb.org/3"
    }

    private data class CacheEntry(
        val timestamp: Long,
        val items: List<SearchResponse>,
        val hasNext: Boolean
    )

    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    private var ttlMs: Long = DEFAULT_TTL_MS

    fun setTtlMinutes(minutes: Int) {
        ttlMs = (minutes.coerceAtLeast(5) * 60 * 1000L)
    }

    fun getDiskCacheDir(context: Context?): File {
        val baseDir = customDiskCacheDir ?: (context?.cacheDir ?: File(System.getProperty("java.io.tmpdir"), "hub_catalog_cache_v4"))
        val dir = File(baseDir, DISK_CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // ---------------------------------------------------------
    // 1. UNIFIED CATALOG CACHE (Only full multi-provider merges)
    // ---------------------------------------------------------

    private fun unifiedCacheKey(category: String, page: Int): String = "unified:$category:$page"

    private fun unifiedFileName(category: String, page: Int): String {
        val sanitized = category.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "cache_v${CACHE_VERSION}_unified_${sanitized}_p${page}.json"
    }

    fun getUnified(category: String, page: Int, context: Context? = null): Pair<List<SearchResponse>, Boolean>? {
        val key = unifiedCacheKey(category, page)
        val now = System.currentTimeMillis()

        // 1. Memory cache check
        val memEntry = memoryCache[key]
        if (memEntry != null) {
            if (now - memEntry.timestamp <= ttlMs && memEntry.items.isNotEmpty()) {
                return memEntry.items to memEntry.hasNext
            }
        }

        // 2. Disk cache check
        if (context != null || customDiskCacheDir != null) {
            val diskResult = loadFromFile(context, unifiedFileName(category, page), ignoreTtl = false)
            if (diskResult != null && diskResult.first.isNotEmpty()) {
                memoryCache[key] = CacheEntry(
                    timestamp = now,
                    items = diskResult.first,
                    hasNext = diskResult.second
                )
                return diskResult
            }
        }

        return null
    }

    /**
     * Retrieves the last known non-empty unified catalog page even if TTL has expired.
     * Used as a resilient fallback when live network / provider requests time out or fail,
     * ensuring Movie and Series catalogs never disappear on Android TV.
     */
    fun getUnifiedStale(category: String, page: Int, context: Context? = null): Pair<List<SearchResponse>, Boolean>? {
        val key = unifiedCacheKey(category, page)
        memoryCache[key]?.takeIf { it.items.isNotEmpty() }?.let {
            return it.items to it.hasNext
        }
        if (context != null || customDiskCacheDir != null) {
            val diskResult = loadFromFile(context, unifiedFileName(category, page), ignoreTtl = true)
            if (diskResult != null && diskResult.first.isNotEmpty()) {
                return diskResult
            }
        }
        return null
    }

    /**
     * Returns recently cached catalog items for Continue Watching / quick fallback rows.
     */
    fun getRecentCatalogItems(limit: Int = 40, context: Context? = null): List<SearchResponse> {
        val result = mutableListOf<SearchResponse>()
        val seenUrls = HashSet<String>()
        for ((key, entry) in memoryCache) {
            if (key.startsWith("unified:")) {
                for (item in entry.items) {
                    if (item.url.isNotBlank() && seenUrls.add(item.url)) {
                        result.add(item)
                        if (result.size >= limit) return result
                    }
                }
            }
        }
        val fallbackCategories = listOf(
            UnifiedCategories.ALL_CONTENT,
            UnifiedCategories.MOVIES,
            UnifiedCategories.SERIES,
            UnifiedCategories.TURKISH_SERIES
        )
        for (cat in fallbackCategories) {
            val stale = getUnifiedStale(cat, 1, context)?.first.orEmpty()
            for (item in stale) {
                if (item.url.isNotBlank() && seenUrls.add(item.url)) {
                    result.add(item)
                    if (result.size >= limit) return result
                }
            }
        }
        return result
    }

    /**
     * Returns all URLs stored in unified pages strictly preceding [beforePage] (i.e. 1 until beforePage),
     * preventing duplicate items across continuation pages.
     */
    fun getUnifiedUrlsBeforePage(category: String, beforePage: Int, context: Context? = null): Set<String> {
        if (beforePage <= 1) return emptySet()
        val urls = LinkedHashSet<String>()
        for (p in 1 until beforePage) {
            val pageData = getUnified(category, p, context) ?: break
            pageData.first.forEach { urls.add(it.url) }
        }
        return urls
    }

    /**
     * Collects all contiguous cached unified items for [category] across overflow/continuation pages.
     */
    fun getAllUnifiedItems(category: String, context: Context? = null): Pair<List<SearchResponse>, Boolean> {
        val allItems = mutableListOf<SearchResponse>()
        var lastHasNext = false
        var p = 1
        while (p <= 100) {
            val pageData = getUnified(category, p, context) ?: break
            allItems.addAll(pageData.first)
            lastHasNext = pageData.second
            p++
        }
        return allItems to lastHasNext
    }

    /**
     * Stores unified catalog items starting at [page].
     * - Never poisons or overwrites an existing cache with an empty list: if [items] is empty,
     *   returns any existing stale cache entry without writing an empty file.
     * - If [items] exceeds [MAX_ITEMS_PER_PAGE] (300), excess items are NOT truncated or lost:
     *   they are deterministically sliced into sequential overflow pages (`page`, `page + 1`, `page + 2`, ...)
     *   in both memory and disk cache, with `hasNext = true` on all intermediate slices.
     *
     * @return The slice `(items, hasNext)` corresponding to the requested [page].
     */
    fun putUnified(
        category: String,
        page: Int,
        items: List<SearchResponse>,
        hasNext: Boolean,
        context: Context? = null
    ): Pair<List<SearchResponse>, Boolean> {
        val priorUrls = getUnifiedUrlsBeforePage(category, page, context).toMutableSet()
        val deduped = items.filter { item ->
            item.url.isNotBlank() && priorUrls.add(item.url)
        }

        if (deduped.isEmpty()) {
            // Never overwrite a valid cached catalog with empty results on transient failure
            val stale = getUnifiedStale(category, page, context)
            if (stale != null && stale.first.isNotEmpty()) {
                return stale
            }
            return emptyList<SearchResponse>() to false
        }

        val chunks: List<List<SearchResponse>> = deduped.chunked(MAX_ITEMS_PER_PAGE)

        val now = System.currentTimeMillis()
        var firstSlice: Pair<List<SearchResponse>, Boolean>? = null

        for ((idx, chunk) in chunks.withIndex()) {
            val targetPage = page + idx
            val chunkHasNext = if (idx < chunks.lastIndex) true else hasNext

            memoryCache[unifiedCacheKey(category, targetPage)] = CacheEntry(
                timestamp = now,
                items = chunk,
                hasNext = chunkHasNext
            )

            if (context != null || customDiskCacheDir != null) {
                saveToFile(context, unifiedFileName(category, targetPage), category, targetPage, chunk, chunkHasNext, now)
            }

            if (idx == 0) {
                firstSlice = chunk to chunkHasNext
            }
        }

        // Remove any stale overflow pages beyond the last written chunk if a re-merge produced fewer pages
        val afterLastPage = page + chunks.size
        memoryCache.remove(unifiedCacheKey(category, afterLastPage))

        return firstSlice ?: (emptyList<SearchResponse>() to hasNext)
    }

    // Backward compatibility delegates
    fun get(category: String, page: Int, context: Context? = null): Pair<List<SearchResponse>, Boolean>? =
        getUnified(category, page, context)

    fun put(category: String, page: Int, items: List<SearchResponse>, hasNext: Boolean, context: Context? = null) =
        putUnified(category, page, items, hasNext, context)

    // ---------------------------------------------------------
    // 2. PROVIDER RAW CATALOG CACHE (Crawler & Single Providers)
    // ---------------------------------------------------------

    private fun providerCacheKey(providerName: String, categoryKey: String, page: Int): String =
        "prov:$providerName:$categoryKey:$page"

    private fun providerFileName(providerName: String, categoryKey: String, page: Int): String {
        val safeProv = providerName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val safeCat = categoryKey.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "cache_v${CACHE_VERSION}_prov_${safeProv}_${safeCat}_p${page}.json"
    }

    fun getProviderCatalog(
        providerName: String,
        categoryKey: String,
        page: Int,
        context: Context? = null
    ): Pair<List<SearchResponse>, Boolean>? {
        val key = providerCacheKey(providerName, categoryKey, page)
        val now = System.currentTimeMillis()

        val memEntry = memoryCache[key]
        if (memEntry != null) {
            if (now - memEntry.timestamp > ttlMs) {
                memoryCache.remove(key)
            } else {
                return memEntry.items to memEntry.hasNext
            }
        }

        if (context != null || customDiskCacheDir != null) {
            val diskResult = loadFromFile(context, providerFileName(providerName, categoryKey, page))
            if (diskResult != null) {
                memoryCache[key] = CacheEntry(
                    timestamp = now,
                    items = diskResult.first,
                    hasNext = diskResult.second
                )
                return diskResult
            }
        }

        return null
    }

    /**
     * Collects all contiguous cached raw provider pages (1..N) for `(providerName, categoryKey)`
     * using [getProviderCatalog].
     */
    fun getAllCachedProviderPages(
        providerName: String,
        categoryKey: String,
        context: Context? = null
    ): Pair<List<SearchResponse>, Boolean> {
        val items = mutableListOf<SearchResponse>()
        var lastHasNext = false
        var p = 1
        while (p <= 100) {
            val cached = getProviderCatalog(providerName, categoryKey, p, context) ?: break
            items.addAll(cached.first)
            lastHasNext = cached.second
            p++
        }
        return items to lastHasNext
    }

    fun putProviderCatalog(
        providerName: String,
        categoryKey: String,
        page: Int,
        items: List<SearchResponse>,
        hasNext: Boolean,
        context: Context? = null
    ) {
        val deduped = items.distinctBy { it.url }
        val now = System.currentTimeMillis()

        memoryCache[providerCacheKey(providerName, categoryKey, page)] = CacheEntry(
            timestamp = now,
            items = deduped,
            hasNext = hasNext
        )

        if (context != null || customDiskCacheDir != null) {
            saveToFile(context, providerFileName(providerName, categoryKey, page), categoryKey, page, deduped, hasNext, now)
        }
    }

    // ---------------------------------------------------------
    // 3. COMMON DISK I/O & EVICTION
    // ---------------------------------------------------------

    fun clear() {
        memoryCache.clear()
    }

    fun clearAll(context: Context? = null) {
        clear()
        try {
            val dir = getDiskCacheDir(context)
            dir.listFiles { f -> f.name.startsWith("cache_v") && f.name.endsWith(".json") }?.forEach { it.delete() }
        } catch (_: Throwable) {}
    }

    private fun saveToFile(
        context: Context?,
        fileName: String,
        category: String,
        page: Int,
        items: List<SearchResponse>,
        hasNext: Boolean,
        timestamp: Long
    ) {
        try {
            val dir = getDiskCacheDir(context)

            // Eviction check if file count exceeds threshold (only evict cache_v*.json files)
            val files = dir.listFiles { f -> f.name.startsWith("cache_v") && f.name.endsWith(".json") } ?: emptyArray()
            if (files.size >= MAX_DISK_FILES) {
                val sorted = files.sortedBy { it.lastModified() }
                sorted.take(files.size - MAX_DISK_FILES + 25).forEach { it.delete() }
            }

            val root = JSONObject().apply {
                put("version", CACHE_VERSION)
                put("timestamp", timestamp)
                put("category", category)
                put("page", page)
                put("hasNext", hasNext)

                val array = JSONArray()
                for (item in items) {
                    val itemObj = JSONObject().apply {
                        put("name", item.name)
                        put("url", item.url)
                        put("type", item.type?.name ?: "Movie")
                        put("posterUrl", item.posterUrl ?: "")
                        val y = when (item) {
                            is MovieSearchResponse -> item.year
                            is TvSeriesSearchResponse -> item.year
                            else -> null
                        }
                        put("year", y ?: -1)
                        put("score", item.score?.toString() ?: "")

                        // Retain canonical media & multi-provider origins
                        val canonical = HubSingleCardMerger.getCanonicalMedia(item, context)
                        if (canonical != null) {
                            put("canonicalId", canonical.id)
                            canonical.tmdbId?.let { put("tmdbId", it) }
                            if (canonical.providerReferences.isNotEmpty()) {
                                val refArray = JSONArray()
                                for (ref in canonical.providerReferences) {
                                    val refObj = JSONObject().apply {
                                        put("providerName", ref.providerName)
                                        put("url", ref.url)
                                        ref.realId?.let { put("realId", it) }
                                        put("title", ref.title)
                                        ref.year?.let { put("year", it) }
                                        ref.tvType?.let { put("tvType", it.name) }
                                        put("confidenceScore", ref.confidenceScore)
                                    }
                                    refArray.put(refObj)
                                }
                                put("providerReferences", refArray)
                            }
                        }
                    }
                    array.put(itemObj)
                }
                put("items", array)
            }

            val file = File(dir, fileName)
            file.writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Throwable) {
            // Low storage or I/O failure must not crash the app
        }
    }

    private fun loadFromFile(
        context: Context?,
        fileName: String,
        ignoreTtl: Boolean = false
    ): Pair<List<SearchResponse>, Boolean>? {
        return try {
            val dir = getDiskCacheDir(context)
            val file = File(dir, fileName)
            if (!file.exists()) return null

            val text = file.readText(Charsets.UTF_8)
            val root = JSONObject(text)

            // Migration / Version check
            val version = root.optInt("version", 1)
            if (version < CACHE_VERSION) {
                file.delete()
                return null
            }

            // TTL check (do not delete expired file so getUnifiedStale can serve it on transient network failure)
            val timestamp = root.optLong("timestamp", 0L)
            val now = System.currentTimeMillis()
            if (!ignoreTtl && now - timestamp > ttlMs) {
                return null
            }

            val hasNext = root.optBoolean("hasNext", false)
            val array = root.optJSONArray("items") ?: return null

            val items = mutableListOf<SearchResponse>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "")
                val url = obj.optString("url", "")
                if (name.isBlank() || url.isBlank()) continue

                val typeStr = obj.optString("type", "Movie")
                val posterUrl = obj.optString("posterUrl", "").takeIf { it.isNotBlank() }
                val year = obj.optInt("year", -1).takeIf { it > 0 }
                val score = obj.optString("score", "").takeIf { it.isNotBlank() }
                val tvType = TvType.values().firstOrNull { it.name == typeStr } ?: TvType.Movie

                val resp = if (tvType == TvType.TvSeries || tvType == TvType.Anime || tvType == TvType.AsianDrama) {
                    CacheSearchResponseBuilder.newTvSeriesSearchResponse(name, url, tvType) {
                        this.posterUrl = posterUrl
                        this.year = year
                    }
                } else {
                    CacheSearchResponseBuilder.newMovieSearchResponse(name, url, tvType) {
                        this.posterUrl = posterUrl
                        this.year = year
                    }
                }
                items.add(resp)

                // Restore CanonicalMedia into HubMediaRegistry for cold-restart single-card multi-source integrity
                val canonicalId = obj.optString("canonicalId", "")
                val tmdbId = obj.optInt("tmdbId", -1).takeIf { it > 0 }
                val refArray = obj.optJSONArray("providerReferences")
                val references = mutableListOf<ProviderContentReference>()
                if (refArray != null) {
                    for (j in 0 until refArray.length()) {
                        val refObj = refArray.getJSONObject(j)
                        val pName = refObj.optString("providerName", "")
                        val pUrl = refObj.optString("url", "")
                        if (pName.isNotBlank() && pUrl.isNotBlank()) {
                            val rId = refObj.optString("realId", "").takeIf { it.isNotBlank() }
                            val rTitle = refObj.optString("title", name)
                            val rYear = refObj.optInt("year", -1).takeIf { it > 0 }
                            val rTypeStr = refObj.optString("tvType", "").takeIf { it.isNotBlank() }
                            val rType = rTypeStr?.let { s -> TvType.values().firstOrNull { it.name == s } }
                            val rConf = refObj.optDouble("confidenceScore", 1.0)
                            references.add(
                                ProviderContentReference(
                                    providerName = pName,
                                    url = pUrl,
                                    realId = rId,
                                    title = rTitle,
                                    year = rYear,
                                    tvType = rType,
                                    confidenceScore = rConf
                                )
                            )
                        }
                    }
                }

                if (canonicalId.isNotBlank() || references.isNotEmpty()) {
                    val canonicalMedia = CanonicalMedia(
                        id = if (canonicalId.isNotBlank()) canonicalId else "disk:$url",
                        title = name,
                        originalTitle = null,
                        year = year,
                        isMovie = tvType != TvType.TvSeries && tvType != TvType.AsianDrama,
                        tvType = tvType,
                        posterUrl = posterUrl,
                        score = score,
                        tmdbId = tmdbId,
                        providerReferences = references
                    )
                    HubMediaRegistry.register(canonicalMedia, context, flushImmediate = false)
                }
            }
            if (!HubMediaRegistry.getStorageFile(context).exists()) {
                HubMediaRegistry.flushToDisk(context)
            }
            items to hasNext
        } catch (_: Throwable) {
            null
        }
    }

    // ---------------------------------------------------------
    // 4. CRAWLER RESUMPTION & RETRY STATE (SharedPreferences / Test Store)
    // ---------------------------------------------------------

    private val testCrawlerPrefs = ConcurrentHashMap<String, Any>()

    fun getLastUpstreamPage(context: Context?, category: String): Int {
        val key = "upstream_page_${category.hashCode()}"
        if (context == null) {
            return (testCrawlerPrefs[key] as? Int) ?: 0
        }
        val prefs = context.getSharedPreferences(PREFS_CRAWLER, Context.MODE_PRIVATE)
        return prefs.getInt(key, 0)
    }

    fun saveLastUpstreamPage(context: Context?, category: String, upstreamPage: Int) {
        val key = "upstream_page_${category.hashCode()}"
        testCrawlerPrefs[key] = upstreamPage
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_CRAWLER, Context.MODE_PRIVATE)
            prefs.edit().putInt(key, upstreamPage).apply()
        }
    }

    fun getCrawlerCursor(context: Context?, providerName: String, categoryKey: String = ""): Int {
        val key = if (categoryKey.isBlank()) "cursor_$providerName" else "cursor_${providerName}_${categoryKey.hashCode()}"
        if (context == null) {
            return (testCrawlerPrefs[key] as? Int) ?: 1
        }
        val prefs = context.getSharedPreferences(PREFS_CRAWLER, Context.MODE_PRIVATE)
        return prefs.getInt(key, 1)
    }

    fun saveCrawlerCursor(context: Context?, providerName: String, categoryKey: String = "", page: Int) {
        val key = if (categoryKey.isBlank()) "cursor_$providerName" else "cursor_${providerName}_${categoryKey.hashCode()}"
        testCrawlerPrefs[key] = page
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_CRAWLER, Context.MODE_PRIVATE)
            prefs.edit().putInt(key, page).apply()
        }
    }

    fun isCrawlerFinished(context: Context?, providerName: String, categoryKey: String = ""): Boolean {
        val key = if (categoryKey.isBlank()) "finished_$providerName" else "finished_${providerName}_${categoryKey.hashCode()}"
        if (context == null) {
            return (testCrawlerPrefs[key] as? Boolean) ?: false
        }
        val prefs = context.getSharedPreferences(PREFS_CRAWLER, Context.MODE_PRIVATE)
        return prefs.getBoolean(key, false)
    }

    fun setCrawlerFinished(context: Context?, providerName: String, categoryKey: String = "", finished: Boolean) {
        val key = if (categoryKey.isBlank()) "finished_$providerName" else "finished_${providerName}_${categoryKey.hashCode()}"
        testCrawlerPrefs[key] = finished
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_CRAWLER, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(key, finished).apply()
        }
    }

    fun getCrawlerRetry(context: Context?, providerName: String, categoryKey: String = ""): Int {
        val key = if (categoryKey.isBlank()) "retry_$providerName" else "retry_${providerName}_${categoryKey.hashCode()}"
        if (context == null) {
            return (testCrawlerPrefs[key] as? Int) ?: 0
        }
        val prefs = context.getSharedPreferences(PREFS_CRAWLER, Context.MODE_PRIVATE)
        return prefs.getInt(key, 0)
    }

    fun recordCrawlerRetry(context: Context?, providerName: String, categoryKey: String = "", retryCount: Int) {
        val key = if (categoryKey.isBlank()) "retry_$providerName" else "retry_${providerName}_${categoryKey.hashCode()}"
        testCrawlerPrefs[key] = retryCount
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_CRAWLER, Context.MODE_PRIVATE)
            prefs.edit().putInt(key, retryCount).apply()
        }
    }

    fun resetCrawlerCursors(context: Context?) {
        testCrawlerPrefs.clear()
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_CRAWLER, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
    }
}
