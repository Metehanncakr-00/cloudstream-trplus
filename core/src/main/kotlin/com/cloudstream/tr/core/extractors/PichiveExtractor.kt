package com.cloudstream.tr.core.extractors

import com.cloudstream.tr.core.network.SafeHttpClient
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

open class PichiveExtractor : ExtractorApi() {
    override val name = "Pichive"
    override val mainUrl = "https://four.pichive.online"
    override val requiresReferer = true

    data class PichiveSource(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("title") val title: String? = null
    )

    data class PichivePlaylist(
        @JsonProperty("sources") val sources: List<PichiveSource>? = null
    )

    data class PichiveResponse(
        @JsonProperty("state") val state: Boolean? = null,
        @JsonProperty("playlist") val playlist: List<PichivePlaylist>? = null
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val playerResp = app.get(
                url,
                headers = SafeHttpClient.defaultHeaders(referer = referer ?: mainUrl)
            ).text

            val openPlayerMatch = Regex("""openPlayer\s*\(\s*['"]([^'"]+)['"]""").find(playerResp)
            val playlistToken = openPlayerMatch?.groupValues?.get(1)

            for (sm in Regex("""\{\s*["']file["']\s*:\s*["']([^"']+)["'].*?["']lang["']\s*:\s*["']([^"']+)["']""").findAll(playerResp)) {
                val subFile = sm.groupValues[1].replace("""\/""", "/")
                val subLang = sm.groupValues[2]
                subtitleCallback(
                    SubtitleFile(
                        lang = if (subLang == "tr") "Türkçe" else "İngilizce",
                        url = subFile
                    )
                )
            }

            if (!playlistToken.isNullOrBlank()) {
                val host = Regex("""https?://[^/]+""").find(url)?.value ?: mainUrl
                val sourceUrl = "${host}/source2.php?v=${URLEncoder.encode(playlistToken, "UTF-8")}"
                val pichiveJson = app.get(
                    sourceUrl,
                    headers = mapOf(
                        "Referer" to url,
                        "X-Requested-With" to "XMLHttpRequest"
                    )
                ).parsedSafe<PichiveResponse>()

                for (pl in pichiveJson?.playlist.orEmpty()) {
                    for (s in pl.sources.orEmpty()) {
                        val fileUrl = s.file ?: continue
                        callback(
                            newExtractorLink(
                                source = name,
                                name = "$name ${s.title ?: "HLS"}",
                                url = fileUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "${host}/"
                                this.headers = mapOf(
                                    "Referer" to "${host}/",
                                    "User-Agent" to SafeHttpClient.DEFAULT_USER_AGENT
                                )
                                this.quality = Qualities.P1080.value
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            com.cloudstream.tr.core.diagnostics.DiagnosticLogger.log(
                provider = name,
                stage = com.cloudstream.tr.core.diagnostics.DiagnosticStage.EXTRACTOR,
                category = com.cloudstream.tr.core.diagnostics.DiagnosticCategory.EXTRACTOR,
                message = "Pichive getUrl failed: ${e.message}",
                url = url,
                throwable = e
            )
        }
    }
}
