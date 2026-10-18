package com.cloudstream.tr.core.resolvers

import android.content.Context

object DebridConfig {
    private const val PREFS_NAME = "CloudStreamHub_Settings"
    private const val KEY_REAL_DEBRID = "real_debrid_token"
    private const val KEY_TORBOX = "torbox_token"
    private const val KEY_ALL_DEBRID = "all_debrid_token"
    private const val KEY_ENABLE_TORRENT = "enable_torrent_sources"
    private const val KEY_PRIORITIZE_CDN = "prioritize_direct_cdn"
    private const val KEY_AUDIO_PREF = "audio_preference"

    var realDebridToken: String? = null
    var torboxToken: String? = null
    var allDebridToken: String? = null
    var enableTorrentSources: Boolean = true
    var prioritizeDirectCdn: Boolean = true
    var audioPreference: String = "ALL" // "ALL", "TR_DUB", "ORIGINAL"

    fun initFromPreferences(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            realDebridToken = prefs.getString(KEY_REAL_DEBRID, null)
            torboxToken = prefs.getString(KEY_TORBOX, null)
            allDebridToken = prefs.getString(KEY_ALL_DEBRID, null)
            enableTorrentSources = prefs.getBoolean(KEY_ENABLE_TORRENT, true)
            prioritizeDirectCdn = prefs.getBoolean(KEY_PRIORITIZE_CDN, true)
            audioPreference = prefs.getString(KEY_AUDIO_PREF, "ALL") ?: "ALL"
        } catch (_: Exception) {}
    }

    fun saveToPreferences(
        context: Context,
        rdToken: String?,
        torbox: String?,
        enableTorrents: Boolean,
        prioritizeCdn: Boolean,
        audioPref: String
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_REAL_DEBRID, rdToken?.trim()?.ifBlank { null })
                .putString(KEY_TORBOX, torbox?.trim()?.ifBlank { null })
                .putBoolean(KEY_ENABLE_TORRENT, enableTorrents)
                .putBoolean(KEY_PRIORITIZE_CDN, prioritizeCdn)
                .putString(KEY_AUDIO_PREF, audioPref)
                .apply()

            realDebridToken = rdToken?.trim()?.ifBlank { null }
            torboxToken = torbox?.trim()?.ifBlank { null }
            enableTorrentSources = enableTorrents
            prioritizeDirectCdn = prioritizeCdn
            audioPreference = audioPref
        } catch (_: Exception) {}
    }

    fun getActiveDebridPrefix(): String? {
        realDebridToken?.takeIf { it.isNotBlank() }?.let { return "realdebrid=$it" }
        torboxToken?.takeIf { it.isNotBlank() }?.let { return "torbox=$it" }
        allDebridToken?.takeIf { it.isNotBlank() }?.let { return "alldebrid=$it" }
        return null
    }

    val isDebridEnabled: Boolean
        get() = getActiveDebridPrefix() != null
}
