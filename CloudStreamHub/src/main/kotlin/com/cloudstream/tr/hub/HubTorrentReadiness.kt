package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.ErrorLoadingException
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class TorrentProbeResponse(val code: Int, val range: String?, val bytes: ByteArray)
internal fun interface TorrentReadinessTransport {
    suspend fun get(url: String, range: String?, timeoutMs: Long, maxBytes: Int): TorrentProbeResponse
}

private val readinessClient by lazy {
    OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .build()
}

/** Never follows redirects; cancellation remains registered while the bounded body is read. */
internal object LocalTorrentTransport : TorrentReadinessTransport {
    override suspend fun get(url: String, range: String?, timeoutMs: Long, maxBytes: Int): TorrentProbeResponse {
        require(isLocalTorrentStream(url))
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
        range?.let { request.header("Range", it) }
        val call = readinessClient.newCall(request.build())
        call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            val input = it.body?.byteStream() ?: throw IOException("Empty response")
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                if (output.size() > maxBytes) throw IOException("Response too large")
                            }
                            val bytes = output.toByteArray()
                            TorrentProbeResponse(it.code, it.header("Content-Range"), bytes)
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
}

internal fun torrentPreparationUrl(url: String, preload: Boolean): String {
    if (!isLocalTorrentStream(url)) throw ErrorLoadingException("Geçersiz yerel torrent bağlantısı.")
    val parsed = url.toHttpUrl()
    if (!parsed.encodedPath.startsWith("/stream/") ||
        parsed.queryParameterValues("index").size != 1 ||
        parsed.queryParameter("index")?.toIntOrNull()?.let { it >= 0 } != true ||
        parsed.queryParameterValues("link").size != 1 || parsed.queryParameter("link").isNullOrBlank()) {
        throw ErrorLoadingException("Torrent dosyası ve indeksi doğrulanamadı.")
    }
    val builder = parsed.newBuilder().fragment(null)
    listOf("play", "preload", "stat", "m3u", "save", "fromlast").forEach { builder.removeAllQueryParameters(it) }
    if (preload) builder.addQueryParameter("preload", "").addQueryParameter("stat", "")
    else builder.addQueryParameter("play", "")
    return builder.build().toString()
}

/** Stat-only avoids restarting a synchronous preload after its HTTP response times out. */
internal fun torrentStatusUrl(url: String): String = torrentPreparationUrl(url, true)
    .toHttpUrl().newBuilder().removeAllQueryParameters("preload").build().toString()

internal fun torrentReadinessBudget(profile: String): Long =
    if (profile == HubPerformanceSettings.FEATURE) 120_000L else 90_000L

private fun readinessBackoff(attempt: Int): Long =
    minOf(1_500L, 250L * (1L shl attempt.coerceIn(0, 3)))

private fun retryableTorrentStatus(code: Int): Boolean =
    code == 408 || code == 425 || code == 429 || code in 500..599


/**
 * TorrServer's preload&stat response contains zero-based file_stats.
 * Checking this before handing off to VLC/MX prevents accidentally playing
 * a subtitle/sample/wrong file from a multi-file Blu-ray or episode torrent.
 * File ID 0 is omitted by the server's json:"id,omitempty" contract.
 */
internal data class SelectedTorrentVideo(val index: Int, val byteLength: Long, val extension: String)

/**
 * Read-only throughput and peer snapshot returned by TorrServer preload/stat.
 * Loaded torrent bytes are NOT a guarantee of bytes buffered at playback position.
 */
internal data class TorrentTransferHealth(
    val downloadMbps: Double?,
    val activePeers: Int?,
    val connectedSeeders: Int?,
    val preloadPercent: Int?,
    val requiredAverageMbps: Double?
) {
    fun safeSummary(): String = buildString {
        if (downloadMbps != null) append("down_").append("%.1f".format(Locale.ROOT, downloadMbps)).append("Mbps")
        if (activePeers != null) append("_peers_").append(activePeers)
        if (connectedSeeders != null) append("_seeds_").append(connectedSeeders)
        if (preloadPercent != null) append("_preload_").append(preloadPercent).append("pct")
        if (requiredAverageMbps != null) append("_avg_").append("%.1f".format(Locale.ROOT, requiredAverageMbps)).append("Mbps")
    }.take(100)
}

internal fun torrentTransferHealth(response: TorrentProbeResponse, selectedBytes: Long): TorrentTransferHealth? {
    if (response.code != 200) return null
    val root = try { ObjectMapper().readTree(response.bytes) } catch (_: Exception) { return null }
    if (root == null || !root.isObject) return null
    fun number(key: String): Double? = root.path(key).takeIf { it.isNumber }?.asDouble()
    fun count(key: String): Int? = root.path(key).takeIf { it.isIntegralNumber && it.asInt() >= 0 }?.asInt()
    val speed = number("download_speed")?.takeIf { it >= 0.0 && it.isFinite() }?.div(125_000)
    val progress = number("preloaded_bytes")
    val target = number("preload_size")
    val percent = if (progress != null && target != null && target > 0)
        (progress / target * 100).toInt().coerceIn(0, 100) else null
    val duration = number("duration_seconds")?.takeIf { it > 0.0 && it.isFinite() }
    val avgMbps = duration?.let { selectedBytes.toDouble() * 8 / it / 1_000_000 }
    val snapshot = TorrentTransferHealth(speed, count("active_peers"), count("connected_seeders"),
        percent, avgMbps)
    return if (listOf(snapshot.downloadMbps, snapshot.activePeers, snapshot.connectedSeeders,
            snapshot.preloadPercent, snapshot.requiredAverageMbps).all { it == null }) null else snapshot
}

private fun recordTorrentHealth(response: TorrentProbeResponse, selectedBytes: Long) {
    torrentTransferHealth(response, selectedBytes)?.let {
        HubPerformanceTelemetry.record("torrent_network", 0, it.safeSummary())
    }
}

internal fun selectedTorrentVideo(response: TorrentProbeResponse, index: Int): SelectedTorrentVideo {
    if (response.code != 200) throw ErrorLoadingException("Torrent sunucusu dosya bilgisi döndürmedi (HTTP ${response.code}).")
    val root = try {
        ObjectMapper().readTree(response.bytes)
    } catch (_: Exception) {
        throw ErrorLoadingException("Torrent sunucusu geçerli dosya bilgisi döndürmedi.")
    } ?: throw ErrorLoadingException("Torrent dosya bilgisi boş.")

    val files = root.path("file_stats")
    if (!files.isArray || files.size() == 0) {
        throw ErrorLoadingException("Torrent dosya listesi alınamadı; dosya seçimi doğrulanamadı.")
    }
    // TorrServer omits id=0 only for the first file. Missing/malformed IDs
    // elsewhere must not silently select sample or subtitle files.
    val matches = files.mapIndexedNotNull { position, entry ->
        val id = entry.get("id")
        val fileIndex = when {
            id == null && position == 0 -> 0
            id != null && id.isIntegralNumber && id.canConvertToInt() -> id.asInt()
            else -> -1
        }
        if (fileIndex == index) entry else null
    }
    val file = matches.singleOrNull()
        ?: throw ErrorLoadingException("Seçilen torrent dosyası bulunamadı veya belirsiz (index=$index).")
    val length = file.path("length").asLong(0L)
    val path = file.path("path").asText("")
    val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (length <= 0L) throw ErrorLoadingException("Seçilen torrent dosyası boş veya geçersiz.")
    if (extension !in setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "mpg", "mpeg")) {
        throw ErrorLoadingException("Seçilen torrent dosyası video değil veya doğrudan oynatılamaz (.$extension).")
    }
    return SelectedTorrentVideo(index, length, extension)
}

internal fun validateTorrentRange(response: TorrentProbeResponse, start: Long, end: Long, total: Long? = null): Long {
    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.range.orEmpty())
        ?: throw ErrorLoadingException("Torrent veri aralığı doğrulanamadı.")
    val values = match.groupValues.drop(1).map { it.toLongOrNull() }
    val size = values[2] ?: throw ErrorLoadingException("Torrent dosya boyutu doğrulanamadı.")
    val actualEnd = minOf(end, size - 1)
    if (response.code != 206 || size <= 0 || values[0] != start || values[1] != actualEnd ||
        (total != null && total != size) || response.bytes.size.toLong() != actualEnd - start + 1) {
        throw ErrorLoadingException("Torrent başlangıç/son verisi eksik veya geçersiz.")
    }
    return size
}

internal suspend fun awaitTorrentReadiness(
    url: String,
    transport: TorrentReadinessTransport = LocalTorrentTransport,
    budgetMs: Long = 90_000,
    diagnostic: (String) -> Unit = { android.util.Log.i("CloudStreamHub", "Torrent hazırlık: $it") }
) {
    val preloadUrl = torrentPreparationUrl(url, true)
    val statusUrl = torrentStatusUrl(url)
    val playUrl = torrentPreparationUrl(url, false)
    val selectedIndex = preloadUrl.toHttpUrl().queryParameter("index")!!.toInt()
    val ready = withTimeoutOrNull(budgetMs) {
        var selectedVideo: SelectedTorrentVideo? = null
        var statusRetries = 0
        var rangeRetries = 0
        val preloadStarted = System.nanoTime()
        diagnostic("ön yükleme")
        try {
            val result = transport.get(preloadUrl, null, minOf(60_000L, budgetMs), 1_048_576)
            diagnostic("ön yükleme HTTP ${result.code}")
            if (!retryableTorrentStatus(result.code)) {
                selectedVideo = selectedTorrentVideo(result, selectedIndex)
                recordTorrentHealth(result, selectedVideo.byteLength)
            }
        } catch (e: IOException) {
            // Client-side timeout does not cancel the server's synchronous preload.
            // A stat-only follow-up must confirm the file before any byte probes.
            diagnostic("ön yükleme yanıtı beklenemedi; ayrı dosya kontrolü")
        } finally {
            HubPerformanceTelemetry.record("torrent_preload",
                (System.nanoTime() - preloadStarted) / 1_000_000,
                if (selectedVideo != null) "metadata_ready" else "metadata_pending")
        }

        while (selectedVideo == null) {
            currentCoroutineContext().ensureActive()
            try {
                val statStart = System.nanoTime()
                val result = transport.get(statusUrl, null, 10_000L, 1_048_576)
                HubPerformanceTelemetry.record("torrent_stat",
                    (System.nanoTime() - statStart) / 1_000_000, "HTTP_" + result.code)
                diagnostic("dosya durumu HTTP ${result.code}")
                if (retryableTorrentStatus(result.code)) throw IOException("Torrent metadata henüz hazır değil")
                selectedVideo = selectedTorrentVideo(result, selectedIndex)
                recordTorrentHealth(result, selectedVideo.byteLength)
            } catch (e: IOException) {
                diagnostic("dosya bilgisi bekleniyor")
                delay(readinessBackoff(statusRetries++))
            }
        }

        // File identity is mandatory, including when preload's initial request timed out.
        val expectedFileSize = selectedVideo.byteLength
        val headBytes = HubReadAheadPolicy.warmupProbeBytes(HubPerformanceSettings.profile)
        diagnostic("seçilen video doğrulandı: .${selectedVideo.extension}, $expectedFileSize bayt")
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                diagnostic("başlangıç verisi")
                val headStart = System.nanoTime()
                val head = transport.get(playUrl, "bytes=0-" + (headBytes - 1), 15_000L, headBytes)
                HubPerformanceTelemetry.record("torrent_head_range",
                    (System.nanoTime() - headStart) / 1_000_000, "HTTP_" + head.code)
                diagnostic("başlangıç HTTP ${head.code}")
                if (retryableTorrentStatus(head.code)) throw IOException("Torrent henüz veri sunamıyor")
                val size = validateTorrentRange(head, 0, headBytes - 1L, expectedFileSize)
                diagnostic("son veri")
                val start = maxOf(0, size - 65_536)
                val tailStart = System.nanoTime()
                val tail = transport.get(playUrl, "bytes=$start-${size - 1}", 15_000L, 65_536)
                HubPerformanceTelemetry.record("torrent_tail_range",
                    (System.nanoTime() - tailStart) / 1_000_000, "HTTP_" + tail.code)
                diagnostic("son HTTP ${tail.code}")
                if (retryableTorrentStatus(tail.code)) throw IOException("Torrentin son parçaları henüz hazır değil")
                validateTorrentRange(tail, start, size - 1, size)
                diagnostic("hazır")
                HubPerformanceTelemetry.record("torrent_ready", 0L)
                return@withTimeoutOrNull true
            } catch (e: IOException) {
                diagnostic("parçalar bekleniyor")
                delay(readinessBackoff(rangeRetries++))
            } catch (e: CancellationException) {
                throw e
            }
        }
        @Suppress("UNREACHABLE_CODE") false
    }
    currentCoroutineContext().ensureActive()
    if (ready != true) {
        diagnostic("süre doldu")
        HubPerformanceTelemetry.record("torrent_readiness", budgetMs, "timeout")
        throw ErrorLoadingException("Torrentin başlangıç ve son parçaları " +
            (budgetMs / 1000) + " saniyede hazırlanamadı. Başka bir kaynak deneyiniz.")
    }
}
