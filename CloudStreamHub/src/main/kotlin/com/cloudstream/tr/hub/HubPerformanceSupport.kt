package com.cloudstream.tr.hub

import android.content.Context
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** User preferences and policy recommendations; never mutate native TorrServer internals. */
internal object HubPerformanceSettings {
    const val AUTO = "AUTO"
    const val COMPATIBLE = "COMPATIBLE"
    const val FEATURE = "FEATURE"
    private const val KEY = "performance_profile"
    @Volatile var profile = AUTO

    fun load(context: Context) {
        val saved = context.getSharedPreferences("CloudStreamHub_Performance", Context.MODE_PRIVATE)
            .getString(KEY, AUTO)
        profile = saved?.takeIf(::valid) ?: AUTO
    }

    fun save(context: Context, selection: String) {
        require(valid(selection))
        context.getSharedPreferences("CloudStreamHub_Performance", Context.MODE_PRIVATE)
            .edit().putString(KEY, selection).apply()
        profile = selection
    }

    fun valid(value: String): Boolean = value in setOf(AUTO, COMPATIBLE, FEATURE)
}

/** Bounded aggregate timings only: never store torrent URLs, hashes, tokens or file paths. */
internal object HubPerformanceTelemetry {
    private const val MAX_ENTRIES = 80
    private val entries = ArrayDeque<String>()
    @Synchronized fun record(stage: String, elapsedMs: Long, status: String = "ok") {
        val cleanStage = stage.take(45).replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val cleanStatus = status.take(45).replace(Regex("[^A-Za-z0-9_.-]"), "_")
        if (entries.size >= MAX_ENTRIES) entries.removeFirst()
        entries.addLast(cleanStage + " " + elapsedMs.coerceAtLeast(0) + "ms " + cleanStatus)
    }
    @Synchronized fun report(): String = if (entries.isEmpty()) {
        "Henüz ölçüm yok. Bir kaynak veya torrent açmayı deneyin."
    } else entries.joinToString("\n")
    @Synchronized fun clear() = entries.clear()
}

/** Adaptive source latency budgets, in-session only; all values clamped. */
internal object HubAdaptiveTimeout {
    private data class Sample(var millis: Long, var failures: Int)
    private val samples = ConcurrentHashMap<String, Sample>()

    @Synchronized fun limit(origin: String, baselineMs: Long, minMs: Long = 8_000L, maxMs: Long = 35_000L): Long {
        val sample = samples[origin] ?: return baselineMs.coerceIn(minMs, maxMs)
        if (sample.failures >= 2) return (baselineMs * 3 / 4).coerceIn(minMs, maxMs)
        return (sample.millis * 2 + 5_000L).coerceIn(minMs, maxMs)
    }

    @Synchronized fun observe(origin: String, elapsedMs: Long, successful: Boolean) {
        val sample = samples[origin]
        val duration = elapsedMs.coerceIn(1, 60_000L)
        if (sample == null) samples[origin] = Sample(duration, if (successful) 0 else 1)
        else {
            sample.millis = (sample.millis * 3 + duration) / 4
            sample.failures = if (successful) 0 else (sample.failures + 1).coerceAtMost(5)
        }
    }

    @Synchronized fun clear() = samples.clear()
}

/** Model-only read-ahead sizing. Host controls the actual torrent buffer and seek priorities. */
internal object HubReadAheadPolicy {
    const val MAX_BUFFER_BYTES = 12L * 1024 * 1024

    /** Extra 192 KiB only when the user opts into feature-size release preparation. */
    fun warmupProbeBytes(profile: String): Int =
        if (profile == HubPerformanceSettings.FEATURE) 262_144 else 65_536

    fun averageMbps(fileBytes: Long, durationSeconds: Long): Double? =
        if (fileBytes <= 0 || durationSeconds <= 0) null
        else fileBytes.toDouble() * 8 / durationSeconds / 1_000_000

    fun recommendBytes(fileBytes: Long, durationSeconds: Long, targetSeconds: Int = 20,
                       budgetBytes: Long = MAX_BUFFER_BYTES): Long {
        if (fileBytes <= 0 || durationSeconds <= 0 || targetSeconds <= 0 || budgetBytes <= 0) return 0
        val estimated = fileBytes.toDouble() * targetSeconds / durationSeconds
        return estimated.coerceIn(0.0, minOf(fileBytes, budgetBytes).toDouble()).toLong()
    }
}

/** Across all simultaneous providers and loadLinks invocations, at most six HTTP probes. */
internal object HubGlobalPreflight {
    private val permits = Semaphore(6)
    suspend fun <T> run(block: suspend () -> T): T = permits.withPermit { block() }
}

/** Concurrency capped at four; coroutine cancellation propagates. */
internal suspend fun <T> resolveHubPreflight(
    candidates: List<T>, concurrency: Int = 3,
    verify: suspend (T) -> Unit
) = coroutineScope {
    val permits = Semaphore(concurrency.coerceIn(1, 4))
    candidates.map { candidate ->
        launch { permits.withPermit { verify(candidate) } }
    }.joinAll()
}

/** Advice is informational, not automatic selection of hardware decoders or audio passthrough. */
internal object HubPlaybackCompatibility {
    fun guidance(title: String, supportsHevcDecoder: Boolean): String {
        val lower = title.lowercase(Locale.ROOT)
        val knownHevc = Regex("""\b(hevc|h\.?265|x265)\b""").containsMatchIn(lower)
        val remux = Regex("""\b(remux|blu[ .-]?ray|bdremux)\b""").containsMatchIn(lower)
        val hdr = Regex("""\b(dv|dovi|hdr10\+?|dolby[ .-]?vision)\b""").containsMatchIn(lower)
        val audio = Regex("""\b(truehd|dts-hd|atmos)\b""").containsMatchIn(lower)
        return when {
            knownHevc && !supportsHevcDecoder -> "HEVC decoder bulunamadı; farklı oynatıcı gerekebilir."
            audio || hdr -> "HDR ve kayıpsız ses desteği oynatıcı, cihaz ve çıkış sistemine bağlıdır."
            remux -> "REMUX yüksek bitrate gerektirebilir; indirme hızıyla karşılaştırın."
            else -> "Codec ve ses uyumluluğu gerçek cihazda doğrulanmalıdır."
        }
    }
}
