package com.cloudstream.tr.hub

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.actions.temp.VlcPackage
import com.lagradost.cloudstream3.ui.player.Torrent
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.txt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URI
import kotlin.coroutines.resume

/** Uses the host torrent server, while VLC handles demuxing and decoding. */
class HubTorrentVlcAction : VlcPackage() {
    override val name = txt("CloudStreamHub: Torrent → VLC")
    override val isPlayer = false
    override val sourceTypes = setOf(ExtractorLinkType.MAGNET, ExtractorLinkType.TORRENT)

    override suspend fun putExtra(
        context: Context,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        val converted = prepareTorrentStream(result.links, index,
            consent = { requestTorrentConsent(context) },
            readiness = { prepareTorrentForExternalPlayer(context, it.url) },
            transform = { Torrent.transformLink(it).first })
        // Explicit constructor avoids Kotlin copy/default-argument ABI differences.
        val convertedResult = LinkLoadingResult(listOf(converted), result.subs, result.syncData)
        super.putExtra(context, intent, video, convertedResult, 0)
    }

}

internal suspend fun prepareTorrentForExternalPlayer(context: Context, url: String) {
    val budget = torrentReadinessBudget(HubPerformanceSettings.profile)
    withContext(Dispatchers.Main) {
        android.widget.Toast.makeText(context,
            "Torrent hazırlanıyor; başlangıç ve son parçalar bekleniyor (en fazla " +
                (budget / 1000) + " saniye).",
            android.widget.Toast.LENGTH_LONG).show()
    }
    val started = System.nanoTime()
    try {
        awaitTorrentReadiness(url, budgetMs = budget)
        HubPerformanceTelemetry.record("player_handoff", (System.nanoTime() - started) / 1_000_000)
    } catch (e: kotlinx.coroutines.CancellationException) {
        HubPerformanceTelemetry.record("player_handoff", (System.nanoTime() - started) / 1_000_000, "cancelled")
        throw e
    } catch (e: Exception) {
        HubPerformanceTelemetry.record("player_handoff", (System.nanoTime() - started) / 1_000_000, e.javaClass.simpleName)
        throw e
    }
}

internal suspend fun requestTorrentConsent(context: Context): Boolean {
    Torrent.hasAcceptedTorrentForThisSession?.let { return it }
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            fun finish(accepted: Boolean) {
                if (continuation.isActive) {
                    Torrent.hasAcceptedTorrentForThisSession = accepted
                    continuation.resume(accepted)
                }
            }
            val dialog = AlertDialog.Builder(context)
                .setTitle("P2P torrent oynatma")
                .setMessage("Torrent oynatırken IP adresiniz diğer eşlerle paylaşılır ve veri yüklenebilir. Bu oturumda torrent kullanımına izin veriyor musunuz?")
                .setPositiveButton("İzin ver") { _, _ -> finish(true) }
                .setNegativeButton("İptal") { _, _ -> finish(false) }
                .setOnCancelListener { finish(false) }
                .create()
            continuation.invokeOnCancellation {
                android.os.Handler(android.os.Looper.getMainLooper()).post { dialog.dismiss() }
            }
            if (continuation.isActive) dialog.show()
        }
    }
}

internal suspend fun prepareTorrentStream(
    links: List<ExtractorLink>,
    index: Int?,
    consent: suspend () -> Boolean,
    readiness: suspend (ExtractorLink) -> Unit = {},
    transform: suspend (ExtractorLink) -> ExtractorLink
): ExtractorLink {
    val link = index?.let { links.getOrNull(it) }
        ?: throw ErrorLoadingException("Bir torrent kaynağı seçiniz.")
    if (link.type != ExtractorLinkType.MAGNET && link.type != ExtractorLinkType.TORRENT) {
        throw ErrorLoadingException("Bu işlem yalnızca torrent kaynakları içindir.")
    }
    if (!consent()) throw ErrorLoadingException("Torrent oynatma iptal edildi.")
    currentCoroutineContext().ensureActive()
    val converted = transform(link)
    currentCoroutineContext().ensureActive()
    if (!isLocalTorrentStream(converted.url)) {
        throw ErrorLoadingException("Torrent sunucusu oynatılabilir bağlantı oluşturamadı.")
    }
    readiness(converted)
    currentCoroutineContext().ensureActive()
    return converted
}

internal fun isLocalTorrentStream(url: String): Boolean = try {
    val uri = URI(url)
    uri.scheme == "http" && uri.host == "127.0.0.1" &&
        uri.port in 1..65535 && uri.rawUserInfo == null && !uri.rawPath.isNullOrEmpty()
} catch (_: java.net.URISyntaxException) {
    false
}
