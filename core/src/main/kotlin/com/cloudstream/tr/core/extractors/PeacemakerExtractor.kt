package com.cloudstream.tr.core.extractors

import com.cloudstream.tr.core.network.SafeHttpClient
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class PeacemakerExtractor : ExtractorApi() {
    override val name = "Peacemaker"
    override val mainUrl = "https://peacemakerst.com"
    override val requiresReferer = true

    data class VideoSourceItem(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("label") val label: String? = null
    )

    data class PeacemakerResponse(
        @JsonProperty("videoSources") val videoSources: List<VideoSourceItem>? = null,
        @JsonProperty("securedLink") val securedLink: String? = null
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val videoId = Regex("""/video/([a-zA-Z0-9_-]+)""").find(url)?.groupValues?.get(1) ?: return
            val apiUrl = "https://peacemakerst.com/tv/video/${videoId}?do=getVideo"

            val resp = app.post(
                apiUrl,
                data = mapOf(
                    "hash" to videoId,
                    "r" to (referer ?: "https://www.dizimom.diy/"),
                    "s" to ""
                ),
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to url,
                    "User-Agent" to SafeHttpClient.DEFAULT_USER_AGENT
                )
            ).parsedSafe<PeacemakerResponse>()

            for (vs in resp?.videoSources.orEmpty()) {
                val streamUrl = vs.file ?: continue
                val preflight = com.cloudstream.tr.core.network.StreamValidator.validateStream(
                    url = streamUrl,
                    headers = mapOf("Referer" to "https://peacemakerst.com/"),
                    provider = name
                )
                if (preflight.isValid) {
                    val resolvedQuality = getQualityFromName(vs.label)
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name ${vs.label ?: "Stream"}",
                            url = streamUrl,
                            type = preflight.streamType
                        ) {
                            this.referer = "https://peacemakerst.com/"
                            this.quality = resolvedQuality
                        }
                    )
                }
            }
        } catch (e: Exception) {
            com.cloudstream.tr.core.diagnostics.DiagnosticLogger.log(
                provider = name,
                stage = com.cloudstream.tr.core.diagnostics.DiagnosticStage.EXTRACTOR,
                category = com.cloudstream.tr.core.diagnostics.DiagnosticCategory.EXTRACTOR,
                message = "Peacemaker getUrl failed: ${e.message}",
                url = url,
                throwable = e
            )
        }
    }
}
