package com.cloudstream.tr.one

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, persistent episode registry that preserves original provider episode URLs.
 * Eliminates redundant series page re-fetching and regex parsing when playing episodes on Android TV.
 */
object OneEpisodeRegistry {
    // Map: mediaId -> "$season:$episode" -> providerId -> episodeDataUrl
    private val episodeMap = ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, String>>>()
    private var appContext: Context? = null
    private var loaded = false
    private const val MAX_TRACKED_SHOWS = 1200

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        if (loaded) return
        loaded = true
        val file = File(context.filesDir, "trplusone-episodes-v1.json")
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val keys = root.keys()
            while (keys.hasNext()) {
                val mediaId = keys.next()
                val showObj = root.optJSONObject(mediaId) ?: continue
                val seasonEpMap = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
                val epKeys = showObj.keys()
                while (epKeys.hasNext()) {
                    val epKey = epKeys.next()
                    val provObj = showObj.optJSONObject(epKey) ?: continue
                    val provMap = ConcurrentHashMap<String, String>()
                    val provKeys = provObj.keys()
                    while (provKeys.hasNext()) {
                        val provId = provKeys.next()
                        val url = provObj.optString(provId)
                        if (url.isNotBlank()) {
                            provMap[provId] = url
                        }
                    }
                    if (provMap.isNotEmpty()) {
                        seasonEpMap[epKey] = provMap
                    }
                }
                if (seasonEpMap.isNotEmpty()) {
                    episodeMap[mediaId] = seasonEpMap
                }
            }
        } catch (e: Exception) {
            OneDiagnostics.record("ep-registry", "INIT_ERROR:${e.javaClass.simpleName}")
        }
    }

    fun epKey(season: Int, episode: Int): String = "$season:$episode"

    fun register(mediaId: String, season: Int, episode: Int, providerId: String, episodeUrl: String) {
        if (mediaId.isBlank() || episodeUrl.isBlank()) return
        val showMap = episodeMap.getOrPut(mediaId) { ConcurrentHashMap() }
        val provMap = showMap.getOrPut(epKey(season, episode)) { ConcurrentHashMap() }
        provMap[providerId] = episodeUrl
        OneDiagnostics.log("EPISODE_MATCH", "id=$mediaId s=$season e=$episode provider=$providerId url=$episodeUrl")
    }

    fun get(mediaId: String, season: Int, episode: Int): Map<String, String> {
        val showMap = episodeMap[mediaId] ?: return emptyMap()
        return showMap[epKey(season, episode)]?.toMap() ?: emptyMap()
    }

    fun hasEpisode(mediaId: String, season: Int, episode: Int): Boolean {
        val showMap = episodeMap[mediaId] ?: return false
        return showMap[epKey(season, episode)]?.isNotEmpty() == true
    }

    fun persist() {
        val context = appContext ?: return
        try {
            val root = JSONObject()
            episodeMap.entries.toList().takeLast(MAX_TRACKED_SHOWS).forEach { entry ->
                val mediaId = entry.key
                val eps = entry.value
                val showObj = JSONObject()
                eps.forEach { (epKey, provs) ->
                    val provObj = JSONObject()
                    provs.forEach { (provId, url) -> provObj.put(provId, url) }
                    showObj.put(epKey, provObj)
                }
                root.put(mediaId, showObj)
            }
            val atomic = AtomicFile(File(context.filesDir, "trplusone-episodes-v1.json"))
            val out = atomic.startWrite()
            try {
                out.write(root.toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(out)
            } catch (e: Exception) {
                atomic.failWrite(out)
                throw e
            }
        } catch (e: Exception) {
            OneDiagnostics.record("ep-registry", "PERSIST_ERROR:${e.javaClass.simpleName}")
        }
    }

    @Synchronized
    fun clear() {
        episodeMap.clear()
        loaded = false
    }
}
