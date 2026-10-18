package com.cloudstream.tr.core.extractors

import com.cloudstream.tr.core.network.SafeHttpClient
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class VidmolyTrExtractor : ExtractorApi() {
    override val name = "Vidmoly"
    override val mainUrl = "https://vidmoly.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val cleanUrl = url.substringBefore("#")
            val fullUrl = if (cleanUrl.contains("/w/")) cleanUrl.replace("/w/", "/embed-") else cleanUrl
            val host = Regex("""https?://[^/]+""").find(fullUrl)?.value ?: mainUrl
            val resp = app.get(
                fullUrl,
                headers = mapOf(
                    "User-Agent" to SafeHttpClient.DEFAULT_USER_AGENT,
                    "Referer" to (referer ?: host)
                )
            ).text

            val m3u8Match = Regex("""sources:\s*\[\s*\{\s*file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(resp)
                ?: Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(resp)
                ?: Regex("""https?://[^"'\s<>]+\.m3u8[^"'\s<>]*""").find(resp)

            val m3u8Url = m3u8Match?.groupValues?.getOrNull(1) ?: m3u8Match?.value
            if (!m3u8Url.isNullOrBlank()) {
                val preflight = com.cloudstream.tr.core.network.StreamValidator.validateStream(
                    url = m3u8Url,
                    headers = mapOf("Referer" to "${host}/"),
                    provider = name
                )
                if (preflight.isValid) {
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name HLS",
                            url = m3u8Url,
                            type = preflight.streamType
                        ) {
                            this.referer = "${host}/"
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            com.cloudstream.tr.core.diagnostics.DiagnosticLogger.log(
                provider = name,
                stage = com.cloudstream.tr.core.diagnostics.DiagnosticStage.EXTRACTOR,
                category = com.cloudstream.tr.core.diagnostics.DiagnosticCategory.EXTRACTOR,
                message = "Vidmoly getUrl failed: ${e.message}",
                url = url,
                throwable = e
            )
        }
    }
}

class VidmolyNetExtractor : VidmolyTrExtractor() {
    override val mainUrl = "https://vidmoly.net"
}

class VidmolyMeExtractor : VidmolyTrExtractor() {
    override val mainUrl = "https://vidmoly.me"
}

