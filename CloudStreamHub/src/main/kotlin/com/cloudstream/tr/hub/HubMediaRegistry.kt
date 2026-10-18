package com.cloudstream.tr.hub

import android.content.Context
import com.cloudstream.tr.core.model.ProviderModels
import com.lagradost.cloudstream3.TvType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, persistent registry storing canonical media identities
 * and their corresponding multi-provider content references.
 * Guarantees:
 * - Incremental reference merging under the same canonical identity (never overwrites prior providers)
 * - Dedicated persistent storage under filesDir (protected from OS/app cacheDir eviction)
 * - Batched/dirty-checked atomic disk writes to avoid redundant I/O on large 300-1000+ item pages
 * - Full cold-boot recovery for Favorites, Watch History, and direct URL loads
 */
object HubMediaRegistry {

    private const val MAX_REGISTRY_ENTRIES = 5000
    private const val PERSISTENT_DIR_NAME = "hub_persistent_registry_v4"
    private const val PERSISTENT_REGISTRY_FILE = "canonical_media_registry_v4.json"

    var customPersistentDir: File? = null

    private val byId = ConcurrentHashMap<String, CanonicalMedia>()
    private val byIdAlias = ConcurrentHashMap<String, String>()
    private val byUrl = ConcurrentHashMap<String, CanonicalMedia>()
    private val byTitleYear = ConcurrentHashMap<String, CanonicalMedia>()
    private val byTmdbId = ConcurrentHashMap<String, CanonicalMedia>()

    @Volatile
    private var diskLoadedForPath: String? = null

    @Volatile
    private var dirty: Boolean = false

    private fun rawTitleKey(title: String, year: Int?, isMovie: Boolean): String {
        val norm = ProviderModels.normalizeTitle(title)
        val y = year ?: 0
        return "$norm:$y:$isMovie"
    }

    private fun cleanTitleKey(title: String, year: Int?, isMovie: Boolean): String {
        val norm = ProviderModels.normalizeTitle(title)
        val clean = HubSingleCardMerger.cleanTitle(norm, year).ifBlank { norm }
        val y = year ?: 0
        return "$clean:$y:$isMovie"
    }

    private fun tmdbKey(tmdbId: Int, isMovie: Boolean): String = "$tmdbId:$isMovie"

    private fun isDiskPersistenceEnabled(context: Context?): Boolean {
        return context != null || customPersistentDir != null || HubCatalogCache.customDiskCacheDir != null
    }

    fun getStorageDir(context: Context?): File {
        val baseDir = customPersistentDir
            ?: HubCatalogCache.customDiskCacheDir
            ?: (context?.filesDir ?: context?.cacheDir ?: File(System.getProperty("java.io.tmpdir"), PERSISTENT_DIR_NAME))
        val dir = File(baseDir, PERSISTENT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getStorageFile(context: Context?): File {
        return File(getStorageDir(context), PERSISTENT_REGISTRY_FILE)
    }

    @Synchronized
    private fun ensureLoaded(context: Context?) {
        if (!isDiskPersistenceEnabled(context)) return
        val file = getStorageFile(context)
        val path = file.absolutePath
        if (diskLoadedForPath == path && byId.isNotEmpty()) return
        if (file.exists()) {
            loadFromDiskInternal(file)
            diskLoadedForPath = path
        }
    }

    private fun canMergeRegistryEntries(a: CanonicalMedia, b: CanonicalMedia): Boolean {
        if (a.isMovie != b.isMovie) return false
        val aAnimeMovie = a.tvType == TvType.AnimeMovie
        val aAnimeSeries = a.tvType == TvType.Anime
        val bAnimeMovie = b.tvType == TvType.AnimeMovie
        val bAnimeSeries = b.tvType == TvType.Anime
        if ((aAnimeMovie && bAnimeSeries) || (aAnimeSeries && bAnimeMovie)) return false
        if (a.year != null && b.year != null && a.year != b.year) return false

        val cleanA = HubSingleCardMerger.cleanTitle(ProviderModels.normalizeTitle(a.title), a.year)
        val cleanB = HubSingleCardMerger.cleanTitle(ProviderModels.normalizeTitle(b.title), b.year)
        val seqA = HubSingleCardMerger.extractSequelTag(cleanA)
        val seqB = HubSingleCardMerger.extractSequelTag(cleanB)
        if (seqA != seqB) return false
        return true
    }

    private fun deduplicateReferences(refs: List<ProviderContentReference>): List<ProviderContentReference> {
        val map = LinkedHashMap<String, ProviderContentReference>()
        for (ref in refs) {
            if (ref.providerName.isBlank() || ref.url.isBlank()) continue
            val key = "${ref.providerName.trim().lowercase()}|${ref.url.trim()}"
            val prev = map[key]
            if (prev == null || ref.confidenceScore > prev.confidenceScore) {
                map[key] = ref
            }
        }
        return map.values.toList()
    }

    private fun mergeCanonicalMedia(existing: CanonicalMedia, incoming: CanonicalMedia): CanonicalMedia {
        val combinedRefs = deduplicateReferences(existing.providerReferences + incoming.providerReferences)
        val preferIncomingId = incoming.id.startsWith("tmdb:") && !existing.id.startsWith("tmdb:")
        val finalId = if (preferIncomingId) incoming.id else existing.id
        val finalTitle = when {
            existing.id.startsWith("tmdb:") -> existing.title
            incoming.id.startsWith("tmdb:") -> incoming.title
            else -> existing.title.ifBlank { incoming.title }
        }
        return CanonicalMedia(
            id = finalId,
            title = finalTitle,
            originalTitle = existing.originalTitle ?: incoming.originalTitle,
            year = existing.year ?: incoming.year,
            isMovie = existing.isMovie,
            tvType = existing.tvType,
            posterUrl = existing.posterUrl?.takeIf { it.isNotBlank() } ?: incoming.posterUrl,
            backdropUrl = existing.backdropUrl?.takeIf { it.isNotBlank() } ?: incoming.backdropUrl,
            plot = existing.plot?.takeIf { it.isNotBlank() } ?: incoming.plot,
            score = existing.score?.takeIf { it.isNotBlank() } ?: incoming.score,
            tmdbId = existing.tmdbId ?: incoming.tmdbId,
            imdbId = existing.imdbId ?: incoming.imdbId,
            providerReferences = combinedRefs
        )
    }

    private fun indexInMemory(merged: CanonicalMedia, existing: CanonicalMedia?, incoming: CanonicalMedia) {
        if (existing != null && existing.id != merged.id) {
            byId.remove(existing.id)
            byIdAlias[existing.id] = merged.id
        }
        if (incoming.id != merged.id) {
            byId.remove(incoming.id)
            byIdAlias[incoming.id] = merged.id
        }
        byId[merged.id] = merged

        for (ref in merged.providerReferences) {
            byUrl[ref.url] = merged
            val refYear = ref.year ?: merged.year
            if (refYear != null) {
                byTitleYear[cleanTitleKey(ref.title, refYear, merged.isMovie)] = merged
                byTitleYear[rawTitleKey(ref.title, refYear, merged.isMovie)] = merged
            }
        }

        byTitleYear[cleanTitleKey(merged.title, merged.year, merged.isMovie)] = merged
        byTitleYear[rawTitleKey(merged.title, merged.year, merged.isMovie)] = merged

        merged.originalTitle?.let {
            byTitleYear[cleanTitleKey(it, merged.year, merged.isMovie)] = merged
            byTitleYear[rawTitleKey(it, merged.year, merged.isMovie)] = merged
        }
        existing?.let {
            byTitleYear[cleanTitleKey(it.title, it.year ?: merged.year, merged.isMovie)] = merged
            byTitleYear[rawTitleKey(it.title, it.year ?: merged.year, merged.isMovie)] = merged
        }
        byTitleYear[cleanTitleKey(incoming.title, incoming.year ?: merged.year, merged.isMovie)] = merged
        byTitleYear[rawTitleKey(incoming.title, incoming.year ?: merged.year, merged.isMovie)] = merged

        merged.tmdbId?.let {
            byTmdbId[tmdbKey(it, merged.isMovie)] = merged
        }
    }

    @Synchronized
    fun register(
        media: CanonicalMedia,
        context: Context? = null,
        flushImmediate: Boolean = true
    ): CanonicalMedia {
        ensureLoaded(context)

        val resolvedId = byIdAlias[media.id] ?: media.id
        val cKey = cleanTitleKey(media.title, media.year, media.isMovie)
        val rKey = rawTitleKey(media.title, media.year, media.isMovie)
        val origCKey = media.originalTitle?.let { cleanTitleKey(it, media.year, media.isMovie) }
        val origRKey = media.originalTitle?.let { rawTitleKey(it, media.year, media.isMovie) }

        val existing = byId[resolvedId]
            ?: media.tmdbId?.let { byTmdbId[tmdbKey(it, media.isMovie)] }
            ?: media.providerReferences.firstNotNullOfOrNull { byUrl[it.url] }?.takeIf { canMergeRegistryEntries(it, media) }
            ?: (if (media.year != null) byTitleYear[cKey]?.takeIf { canMergeRegistryEntries(it, media) } else null)
            ?: (if (media.year != null) byTitleYear[rKey]?.takeIf { canMergeRegistryEntries(it, media) } else null)
            ?: (if (media.year != null && origCKey != null) byTitleYear[origCKey]?.takeIf { canMergeRegistryEntries(it, media) } else null)
            ?: (if (media.year != null && origRKey != null) byTitleYear[origRKey]?.takeIf { canMergeRegistryEntries(it, media) } else null)

        if (existing == null && byId.size >= MAX_REGISTRY_ENTRIES) {
            val keysToEvict = byId.keys().toList().take(MAX_REGISTRY_ENTRIES / 5)
            for (k in keysToEvict) {
                val removed = byId.remove(k) ?: continue
                for (ref in removed.providerReferences) {
                    byUrl.remove(ref.url)
                }
                byTitleYear.remove(cleanTitleKey(removed.title, removed.year, removed.isMovie))
                byTitleYear.remove(rawTitleKey(removed.title, removed.year, removed.isMovie))
                removed.originalTitle?.let {
                    byTitleYear.remove(cleanTitleKey(it, removed.year, removed.isMovie))
                    byTitleYear.remove(rawTitleKey(it, removed.year, removed.isMovie))
                }
                removed.tmdbId?.let { byTmdbId.remove(tmdbKey(it, removed.isMovie)) }
            }
        }

        val mergedMedia = if (existing != null) {
            mergeCanonicalMedia(existing, media)
        } else {
            media.copy(providerReferences = deduplicateReferences(media.providerReferences))
        }

        indexInMemory(mergedMedia, existing, media)

        if (existing == null || existing != mergedMedia) {
            dirty = true
        }

        if (flushImmediate && dirty) {
            flushToDisk(context)
        }

        return mergedMedia
    }

    @Synchronized
    fun flushToDisk(context: Context? = null) {
        if (!isDiskPersistenceEnabled(context)) return
        if (!dirty && getStorageFile(context).exists()) return
        saveToDisk(context)
        dirty = false
    }

    @Synchronized
    private fun saveToDisk(context: Context?) {
        try {
            val file = getStorageFile(context)
            val tempFile = File(file.parentFile, "$PERSISTENT_REGISTRY_FILE.tmp")
            val root = JSONObject()
            val array = JSONArray()

            for ((_, media) in byId) {
                val obj = JSONObject().apply {
                    put("id", media.id)
                    put("title", media.title)
                    media.originalTitle?.let { put("originalTitle", it) }
                    media.year?.let { put("year", it) }
                    put("isMovie", media.isMovie)
                    put("tvType", media.tvType.name)
                    media.posterUrl?.let { put("posterUrl", it) }
                    media.score?.let { put("score", it) }
                    media.tmdbId?.let { put("tmdbId", it) }
                    media.imdbId?.let { put("imdbId", it) }

                    val refArray = JSONArray()
                    for (ref in media.providerReferences) {
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
                array.put(obj)
            }

            root.put("items", array)
            tempFile.writeText(root.toString(), Charsets.UTF_8)
            if (file.exists()) {
                file.delete()
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            diskLoadedForPath = file.absolutePath
        } catch (_: Throwable) {}
    }

    @Synchronized
    private fun loadFromDisk(context: Context?) {
        if (!isDiskPersistenceEnabled(context)) return
        val file = getStorageFile(context)
        if (!file.exists()) return
        loadFromDiskInternal(file)
        diskLoadedForPath = file.absolutePath
    }

    @Synchronized
    private fun loadFromDiskInternal(file: File) {
        try {
            if (!file.exists()) return
            val text = file.readText(Charsets.UTF_8)
            val root = JSONObject(text)
            val array = root.optJSONArray("items") ?: return

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "")
                val title = obj.optString("title", "")
                if (id.isBlank() || title.isBlank()) continue

                val originalTitle = obj.optString("originalTitle", "").takeIf { it.isNotBlank() }
                val year = obj.optInt("year", -1).takeIf { it > 0 }
                val isMovie = obj.optBoolean("isMovie", true)
                val typeStr = obj.optString("tvType", if (isMovie) "Movie" else "TvSeries")
                val tvType = TvType.values().firstOrNull { it.name == typeStr } ?: if (isMovie) TvType.Movie else TvType.TvSeries
                val posterUrl = obj.optString("posterUrl", "").takeIf { it.isNotBlank() }
                val score = obj.optString("score", "").takeIf { it.isNotBlank() }
                val tmdbId = obj.optInt("tmdbId", -1).takeIf { it > 0 }
                val imdbId = obj.optString("imdbId", "").takeIf { it.isNotBlank() }

                val refArray = obj.optJSONArray("providerReferences")
                val references = mutableListOf<ProviderContentReference>()
                if (refArray != null) {
                    for (j in 0 until refArray.length()) {
                        val refObj = refArray.getJSONObject(j)
                        val pName = refObj.optString("providerName", "")
                        val pUrl = refObj.optString("url", "")
                        if (pName.isNotBlank() && pUrl.isNotBlank()) {
                            val rId = refObj.optString("realId", "").takeIf { it.isNotBlank() }
                            val rTitle = refObj.optString("title", title)
                            val rYear = refObj.optInt("year", -1).takeIf { it > 0 }
                            val rTypeStr = refObj.optString("tvType", "")
                            val rType = TvType.values().firstOrNull { it.name == rTypeStr }
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

                val diskMedia = CanonicalMedia(
                    id = id,
                    title = title,
                    originalTitle = originalTitle,
                    year = year,
                    isMovie = isMovie,
                    tvType = tvType,
                    posterUrl = posterUrl,
                    score = score,
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    providerReferences = deduplicateReferences(references)
                )

                val existing = byId[id]
                val merged = if (existing != null) mergeCanonicalMedia(existing, diskMedia) else diskMedia
                indexInMemory(merged, existing, diskMedia)
            }
        } catch (_: Throwable) {}
    }

    fun getById(id: String, context: Context? = null): CanonicalMedia? {
        val resolved = byIdAlias[id] ?: id
        val memory = byId[resolved]
        if (memory != null) return memory
        loadFromDisk(context)
        val resolvedAfterLoad = byIdAlias[id] ?: id
        return byId[resolvedAfterLoad]
    }

    fun getByUrl(url: String, context: Context? = null): CanonicalMedia? {
        val memory = byUrl[url]
        if (memory != null) return memory
        loadFromDisk(context)
        return byUrl[url]
    }

    fun find(title: String, year: Int?, isMovie: Boolean, context: Context? = null): CanonicalMedia? {
        val cKey = cleanTitleKey(title, year, isMovie)
        val rKey = rawTitleKey(title, year, isMovie)
        val memory = byTitleYear[cKey] ?: byTitleYear[rKey]
        if (memory != null) return memory
        loadFromDisk(context)
        return byTitleYear[cKey] ?: byTitleYear[rKey]
    }

    fun findByTmdb(tmdbId: Int, isMovie: Boolean, context: Context? = null): CanonicalMedia? {
        val key = tmdbKey(tmdbId, isMovie)
        val memory = byTmdbId[key]
        if (memory != null) return memory
        loadFromDisk(context)
        return byTmdbId[key]
    }

    fun getReferences(payload: AggregatorLinkPayload, context: Context? = null): List<ProviderContentReference> {
        val direct = payload.canonicalId?.let { getById(it, context) }
        if (direct != null && direct.providerReferences.isNotEmpty()) {
            return direct.providerReferences
        }
        val byTmdb = payload.tmdbId?.let { findByTmdb(it, payload.isMovie, context) }
        if (byTmdb != null && byTmdb.providerReferences.isNotEmpty()) {
            return byTmdb.providerReferences
        }
        val byTitle = find(payload.title, payload.year, payload.isMovie, context)
        if (byTitle != null && byTitle.providerReferences.isNotEmpty()) {
            return byTitle.providerReferences
        }
        val byOrig = payload.originalTitle?.let { find(it, payload.year, payload.isMovie, context) }
        if (byOrig != null && byOrig.providerReferences.isNotEmpty()) {
            return byOrig.providerReferences
        }
        return emptyList()
    }

    @Synchronized
    fun clear() {
        byId.clear()
        byIdAlias.clear()
        byUrl.clear()
        byTitleYear.clear()
        byTmdbId.clear()
        diskLoadedForPath = null
        dirty = false
    }

    @Synchronized
    fun clearDisk(context: Context? = null) {
        try {
            val file = getStorageFile(context)
            if (file.exists()) file.delete()
            val tmp = File(file.parentFile, "$PERSISTENT_REGISTRY_FILE.tmp")
            if (tmp.exists()) tmp.delete()
        } catch (_: Throwable) {}
    }

    @Synchronized
    fun clearAll(context: Context? = null) {
        clear()
        clearDisk(context)
    }
}
