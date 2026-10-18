package com.cloudstream.tr.hub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.actions.updateDurationAndPosition
import com.lagradost.cloudstream3.ui.player.Torrent
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.txt

// Intent contract: https://sites.google.com/site/mxvpen/api
class HubTorrentMxAction : OpenInAppAction(
    appName = txt("MX Player (Torrent)"),
    packageName = "com.mxtech.videoplayer.ad",
    intentClass = "com.mxtech.videoplayer.ad.ActivityScreen",
    action = Intent.ACTION_VIEW
) {
    override val name = txt("CloudStreamHub: Torrent → MX Player")
    override val isPlayer = false
    override val oneSource = true
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
        intent.setDataAndType(Uri.parse(converted.url), "video/*")
        intent.putExtra("title", video.name)
        intent.putExtra("secure_uri", true)
        intent.putExtra("return_result", true)
        intent.putExtra("position", mxPlayerPosition(getViewPos(video.id)?.position))
        if (result.subs.isNotEmpty()) {
            intent.putExtra("subs", result.subs.map { Uri.parse(it.url) }.toTypedArray())
            intent.putExtra("subs.name", result.subs.map { it.name }.toTypedArray())
        }
    }

    override fun onResult(activity: Activity, intent: Intent?) {
        val position = intent?.getIntExtra("position", -1) ?: -1
        val duration = intent?.getIntExtra("duration", -1) ?: -1
        updateDurationAndPosition(position.toLong(), duration.toLong())
    }
}

internal fun mxPlayerPosition(position: Long?): Int =
    (position ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
