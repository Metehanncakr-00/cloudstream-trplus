package com.cloudstream.tr.core.resolvers

import java.net.URLEncoder

object TorrentTrackers {
    val list = listOf(
        // High-availability HTTP/HTTPS trackers (resistant to ISP UDP blocks)
        "http://tracker.openbittorrent.com:80/announce",
        "http://tracker.dler.org:6969/announce",
        "http://tracker2.dler.org:80/announce",
        "https://tracker.tamersunion.org:443/announce",
        "http://tracker.renfei.net:8080/announce",

        // WebTorrent WebSocket trackers (modern streaming clients)
        "wss://tracker.openwebtorrent.com",
        "wss://tracker.btorrent.xyz",
        "wss://tracker.fastcast.nz",


        // Top reliable UDP trackers (ngosang trackerslist verified)
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://explodie.org:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker-udp.gbitt.info:80/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://tracker.qu.ax:6969/announce",
        "udp://tracker.skynetcloud.site:6969/announce",
        "udp://tracker.nyaa.vc:6969/announce",
        "udp://open.stealth.si:80/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://opentracker.i2p.rocks:6969/announce"
    )

    internal fun isValidHash(hash: String): Boolean =
        Regex("[a-fA-F0-9]{40}|[a-zA-Z2-7]{32}").matches(hash)

    fun magnetParams(sources: List<String> = emptyList()): String {
        val trackers = (sources.map { it.removePrefix("tracker:").trim() } + list)
            .filter { tracker ->
                try {
                    val uri = java.net.URI(tracker)
                    uri.scheme?.lowercase() in setOf("udp", "http", "https", "ws", "wss") &&
                        !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null
                } catch (_: Exception) {
                    false
                }
            }.distinct()
        return trackers.joinToString("") { "&tr=" + URLEncoder.encode(it, "UTF-8") }
    }

    val asMagnetParam: String by lazy { magnetParams() }
}
