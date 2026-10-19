package com.cloudstream.tr.one

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/** Local index; never replaces a successful page with an empty one. */
object OneIndex {
    private val media = LinkedHashMap<String, CanonicalMedia>()
    private val pages = LinkedHashMap<String, LinkedHashSet<String>>()
    private val aliases = LinkedHashMap<String, String>()
    private var loaded = false
    private var appContext: Context? = null
    private const val MAX_MEDIA = 2500
    private const val MAX_PAGE_ITEMS = 120

    @Synchronized fun init(context: Context) {
        appContext = context.applicationContext
        if (loaded) return
        loaded = true
        val file = File(context.filesDir, "trplusone-index-v1.json")
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val items = root.optJSONArray("media") ?: JSONArray()
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val lists = obj.optJSONArray("listings") ?: continue
                val listings = (0 until lists.length()).mapNotNull { j ->
                    val s = lists.optJSONObject(j) ?: return@mapNotNull null
                    val kind = runCatching { OneKind.valueOf(s.optString("kind")) }.getOrNull() ?: return@mapNotNull null
                    ProviderListing(
                        providerId = s.optString("provider"), url = s.optString("url"),
                        name = s.optString("name"), year = s.optInt("year").takeIf { s.has("year") },
                        kind = kind, posterUrl = s.optString("poster").takeIf(String::isNotBlank),
                        imdbId = s.optString("imdb").takeIf(String::isNotBlank),
                        tmdbId = s.optInt("tmdb").takeIf { s.has("tmdb") },
                        lastSeenMs = s.optLong("seen")
                    )
                }
                if (listings.isEmpty()) continue
                val kind = listings.first().kind
                val id = obj.optString("id")
                if (id.isNotBlank()) media[id] = CanonicalMedia(
                    id, obj.optString("name"), obj.optInt("year").takeIf { obj.has("year") },
                    kind, obj.optString("poster").takeIf(String::isNotBlank), listings
                )
            }
            val cat = root.optJSONObject("pages") ?: JSONObject()
            for (key in cat.keys()) {
                val array = cat.optJSONArray(key) ?: continue
                pages[key] = LinkedHashSet((0 until array.length()).map { array.optString(it) }.filter { it in media })
            }
            val aliasObj = root.optJSONObject("aliases")
            if (aliasObj != null) {
                for (key in aliasObj.keys()) {
                    val target = aliasObj.optString(key)
                    if (target.isNotBlank()) aliases[key] = target
                }
            }
        } catch (e: Exception) {
            // Keep an invalid database for diagnosis rather than overwrite it.
            OneDiagnostics.record("index-read", "ERROR:${e.javaClass.simpleName}")
        }
    }

    private fun pageKey(category: String, page: Int) = "$category#$page"

    @Synchronized fun page(category: String, page: Int): List<CanonicalMedia> =
        pages[pageKey(category, page)]?.mapNotNull { media[aliases[it] ?: it] }?.distinctBy { it.id }.orEmpty()

    @Synchronized fun media(id: String): CanonicalMedia? {
        val targetId = aliases[id] ?: id
        return media[targetId]
    }

    @Synchronized fun registerAlias(aliasId: String, canonicalId: String) {
        if (aliasId.isNotBlank() && canonicalId.isNotBlank() && aliasId != canonicalId) {
            aliases[aliasId] = canonicalId
        }
    }

    @Synchronized fun updateMedia(updated: CanonicalMedia) {
        media[updated.id] = updated
        persist()
    }

    @Synchronized fun stats(): String {
        val breakdown = media.values.flatMap { it.listings }
            .groupingBy { it.providerId }.eachCount().entries.joinToString(", ") { "${it.key}=${it.value}" }
        return "Indexed=${media.size}, listings=${media.values.sumOf { it.listings.size }}, pages=${pages.size}, aliases=${aliases.size}; sources: $breakdown"
    }

    @Synchronized fun searchLocal(query: String): List<CanonicalMedia> {
        val needle = OneIdentity.normalizedTitle(query, null)
        return if (needle.isBlank()) emptyList() else media.values.filter {
            OneIdentity.normalizedTitle(it.name, it.year).contains(needle)
        }.distinctBy { it.id }.take(60)
    }

    @Synchronized fun ingest(category: String, page: Int, listings: List<ProviderListing>): List<CanonicalMedia> {
        if (listings.isEmpty()) return this.page(category, page)
        val updated = CanonicalEngine.merge(media.values, listings) { alias, canonical ->
            registerAlias(alias, canonical)
        }
        media.clear()
        updated.takeLast(MAX_MEDIA).forEach { media[it.id] = it }
        val categoryKey = pageKey(category, page)
        val ids = listings.map { aliases[OneIdentity.key(it)] ?: OneIdentity.key(it) }.filter { it in media }
        val saved = pages.getOrPut(categoryKey) { LinkedHashSet() }
        saved.addAll(ids)
        while (saved.size > MAX_PAGE_ITEMS) saved.remove(saved.first())
        // Writes are invoked only on Dispatchers.IO by the caller.
        persist()
        return this.page(category, page)
    }

    @Synchronized fun saveSearch(listings: List<ProviderListing>) {
        if (listings.isEmpty()) return
        val updated = CanonicalEngine.merge(media.values, listings) { alias, canonical ->
            registerAlias(alias, canonical)
        }
        media.clear()
        updated.takeLast(MAX_MEDIA).forEach { media[it.id] = it }
        persist()
    }

    @Synchronized fun clearForTests() {
        media.clear()
        pages.clear()
        aliases.clear()
    }

    private fun persist() {
        val context = appContext ?: return
        try {
            val root = JSONObject()
            val all = JSONArray()
            media.values.forEach { entry ->
                val lists = JSONArray()
                entry.listings.forEach { l ->
                    val obj = JSONObject().put("provider", l.providerId).put("url", l.url)
                        .put("name", l.name).put("kind", l.kind.name).put("seen", l.lastSeenMs)
                    l.year?.let { obj.put("year", it) }
                    l.posterUrl?.let { obj.put("poster", it) }
                    l.imdbId?.let { obj.put("imdb", it) }
                    l.tmdbId?.let { obj.put("tmdb", it) }
                    lists.put(obj)
                }
                val obj = JSONObject().put("id", entry.id).put("name", entry.name)
                    .put("listings", lists)
                entry.year?.let { obj.put("year", it) }
                entry.posterUrl?.let { obj.put("poster", it) }
                all.put(obj)
            }
            root.put("media", all)
            val pageJson = JSONObject()
            pages.forEach { (key, ids) -> pageJson.put(key, JSONArray(ids.toList())) }
            root.put("pages", pageJson)
            val aliasJson = JSONObject()
            aliases.forEach { (k, v) -> aliasJson.put(k, v) }
            root.put("aliases", aliasJson)
            val atomic = AtomicFile(File(context.filesDir, "trplusone-index-v1.json"))
            val out = atomic.startWrite()
            try {
                out.write(root.toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(out)
            } catch (e: Exception) {
                atomic.failWrite(out)
                throw e
            }
        } catch (e: Exception) {
            OneDiagnostics.record("index-write", "ERROR:${e.javaClass.simpleName}")
        }
    }
}
