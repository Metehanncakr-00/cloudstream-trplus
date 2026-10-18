package com.cloudstream.tr.hub

import android.content.Context
import com.cloudstream.tr.core.resolvers.DebridConfig

/**
 * Unified settings manager consolidating:
 * 1. Genel: Provider enablement, failure reporting
 * 2. Katalog: Category order, visibility, indexing
 * 3. Dil: Audio language preference, unknown source policy
 * 4. Oynatma: Quality profile, P2P toggle, CDN priority, torrent ordering
 * 5. Performans: Speed profile, timeouts, concurrency, cache TTL
 * 6. Tanılama: Telemetry, sanitized log export
 * 7. Görünüm: Theme and accent colors
 */
object HubSettingsManager {

    private const val PREFS_NAME = "CloudStreamHub_Unified_Settings_v1"

    // Keys
    private const val KEY_DISABLED_PROVIDERS = "disabled_providers"
    private const val KEY_REPORT_FAILURES = "report_failures"
    private const val KEY_UNKNOWN_AUDIO_POLICY = "unknown_audio_policy"
    private const val KEY_TORRENTS_AT_BOTTOM = "torrents_at_bottom"
    private const val KEY_PERF_MODE = "perf_mode"
    private const val KEY_SEARCH_TIMEOUT_SEC = "search_timeout_sec"
    private const val KEY_CONCURRENCY = "max_concurrency"
    private const val KEY_CACHE_TTL_MIN = "cache_ttl_min"
    private const val KEY_ENABLE_BG_CRAWLER = "enable_bg_crawler"

    // Defaults
    const val PERF_MODE_FAST = "FAST"
    const val PERF_MODE_BALANCED = "BALANCED"
    const val PERF_MODE_COMPREHENSIVE = "COMPREHENSIVE"

    data class State(
        val disabledProviders: Set<String> = emptySet(),
        val reportFailures: Boolean = true,
        val unknownAudioPolicy: String = "DEPRIORITIZE",
        val torrentsAtBottom: Boolean = false,
        val perfMode: String = PERF_MODE_BALANCED,
        val searchTimeoutSec: Int = 30,
        val maxConcurrency: Int = 4,
        val cacheTtlMin: Int = 60,
        val enableBackgroundCrawler: Boolean = false
    ) {
        val effectiveSearchTimeoutMs: Long
            get() = when (perfMode) {
                PERF_MODE_FAST -> (searchTimeoutSec.coerceAtMost(10) * 1000L).coerceAtLeast(3000L)
                PERF_MODE_COMPREHENSIVE -> (searchTimeoutSec.coerceAtLeast(30) * 1000L)
                else -> (searchTimeoutSec * 1000L).coerceIn(5000L, 60000L)
            }

        val effectiveProviderTimeoutMs: Long
            get() = when (perfMode) {
                PERF_MODE_FAST -> 12000L
                PERF_MODE_COMPREHENSIVE -> 45000L
                else -> 30000L
            }

        val effectiveCategoryTimeoutMs: Long
            get() = when (perfMode) {
                PERF_MODE_FAST -> 4500L
                PERF_MODE_COMPREHENSIVE -> 9000L
                else -> 6000L
            }

        val effectivePreflightTimeoutMs: Long
            get() = when (perfMode) {
                PERF_MODE_FAST -> 1500L
                PERF_MODE_COMPREHENSIVE -> 5000L
                else -> 3000L
            }
    }

    @Volatile private var state = State()

    fun snapshot(): State = state

    @Synchronized fun load(context: Context): State {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val disabled = prefs.getString(KEY_DISABLED_PROVIDERS, "").orEmpty()
            .split(",").filter(String::isNotBlank).toSet()
        val loaded = State(
            disabledProviders = disabled,
            reportFailures = prefs.getBoolean(KEY_REPORT_FAILURES, true),
            unknownAudioPolicy = prefs.getString(KEY_UNKNOWN_AUDIO_POLICY, "DEPRIORITIZE") ?: "DEPRIORITIZE",
            torrentsAtBottom = prefs.getBoolean(KEY_TORRENTS_AT_BOTTOM, false),
            perfMode = prefs.getString(KEY_PERF_MODE, PERF_MODE_BALANCED) ?: PERF_MODE_BALANCED,
            searchTimeoutSec = prefs.getInt(KEY_SEARCH_TIMEOUT_SEC, 30),
            maxConcurrency = prefs.getInt(KEY_CONCURRENCY, 4),
            cacheTtlMin = prefs.getInt(KEY_CACHE_TTL_MIN, 60),
            enableBackgroundCrawler = prefs.getBoolean(KEY_ENABLE_BG_CRAWLER, false)
        )
        state = loaded
        HubCatalogCache.setTtlMinutes(loaded.cacheTtlMin)
        return loaded
    }

    @Synchronized fun save(context: Context, newState: State) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_DISABLED_PROVIDERS, newState.disabledProviders.joinToString(","))
            .putBoolean(KEY_REPORT_FAILURES, newState.reportFailures)
            .putString(KEY_UNKNOWN_AUDIO_POLICY, newState.unknownAudioPolicy)
            .putBoolean(KEY_TORRENTS_AT_BOTTOM, newState.torrentsAtBottom)
            .putString(KEY_PERF_MODE, newState.perfMode)
            .putInt(KEY_SEARCH_TIMEOUT_SEC, newState.searchTimeoutSec)
            .putInt(KEY_CONCURRENCY, newState.maxConcurrency)
            .putInt(KEY_CACHE_TTL_MIN, newState.cacheTtlMin)
            .putBoolean(KEY_ENABLE_BG_CRAWLER, newState.enableBackgroundCrawler)
            .apply()
        state = newState
        HubCatalogCache.setTtlMinutes(newState.cacheTtlMin)
    }

    fun isProviderEnabled(providerName: String): Boolean = providerName !in state.disabledProviders

    fun setProviderEnabled(context: Context, providerName: String, enabled: Boolean) {
        val nextDisabled = if (enabled) {
            state.disabledProviders - providerName
        } else {
            state.disabledProviders + providerName
        }
        save(context, state.copy(disabledProviders = nextDisabled))
    }

    /**
     * Sanitizes telemetry and catalog diagnostic reports by stripping API tokens and credentials.
     */
    fun getSanitizedDiagnosticsReport(): String {
        val catalogSection = "=== Katalog Tanılama Kayıtları ===\n" + HubCatalogDiagnostics.report()
        val telemetrySection = "=== Kaynak ve Oynatma Ölçümleri ===\n" + HubPerformanceTelemetry.report()
        var sanitized = "$catalogSection\n\n$telemetrySection"
        DebridConfig.realDebridToken?.takeIf { it.isNotBlank() }?.let { token ->
            sanitized = sanitized.replace(token, "[REDACTED_RD_TOKEN]")
        }
        DebridConfig.torboxToken?.takeIf { it.isNotBlank() }?.let { token ->
            sanitized = sanitized.replace(token, "[REDACTED_TORBOX_TOKEN]")
        }
        return HubCatalogDiagnostics.sanitize(sanitized)
    }

    fun resetToDefaults(context: Context) {
        save(context, State())
        HubAppearancePreferences.save(context, HubAppearancePreferences.Config())
        HubHomePreferences.save(context, HubHomePreferences.normalized(emptyList(), emptySet()))
        HubCatalogCache.clearAll(context)
        HubCatalogCache.resetCrawlerCursors(context)
        HubMediaRegistry.clear()
    }
}

