package com.cloudstream.tr.core.extractors

import com.cloudstream.tr.core.network.SafeHttpClient
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.regex.Pattern

@JsonIgnoreProperties(ignoreUnknown = true)
data class RapidVidCaption(
    @JsonProperty("kind") val kind: String? = null,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RapidVidPayload(
    @JsonProperty("m") val m: String? = null,
    @JsonProperty("i") val i: String? = null,
    @JsonProperty("cm") val cm: String? = null,
    @JsonProperty("tm") val tm: String? = null,
    @JsonProperty("ct") val ct: List<RapidVidCaption>? = null
)

open class RapidVidExtractor : ExtractorApi() {
    override val name = "RapidVid"
    override val mainUrl = "https://rapidvid.org"
    override val requiresReferer = true

    companion object {
        private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)


        fun decryptRapidvidAv(token: String): String {
            return try {
                val rev = token.reversed()
                val padLen = (4 - rev.length % 4) % 4
                val padded = rev + "=".repeat(padLen)
                val decodedBytes = com.cloudstream.tr.core.utils.Base64Utils.decode(padded)
                val decodedStr = String(decodedBytes, Charsets.ISO_8859_1)
                val key = "K9L"
                val sb = StringBuilder()
                for (i in decodedStr.indices) {
                    val r = key[i % 3]
                    val n = decodedStr[i].code - (r.code % 5 + 1)
                    sb.append(n.toChar())
                }
                val inner = sb.toString()
                val innerPad = (4 - inner.length % 4) % 4
                val innerPadded = inner + "=".repeat(innerPad)
                String(com.cloudstream.tr.core.utils.Base64Utils.decode(innerPadded), Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        }

        suspend fun parseHtmlResponse(
            html: String,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            var foundStream = false
            try {
                // 1. Check new window._p8 payload (Eylül 2026 JSON format)
                val p8Pattern = Pattern.compile("""window\._p8\s*=\s*['"]([^'"]+)['"]""")
                val p8Matcher = p8Pattern.matcher(html)
                if (p8Matcher.find()) {
                    val token = p8Matcher.group(1)
                    if (!token.isNullOrBlank()) {
                        val jsonStr = decryptRapidvidAv(token)
                        if (jsonStr.isNotBlank()) {
                            try {
                                val payload = runCatching { mapper.readValue<RapidVidPayload>(jsonStr) }.getOrNull()

                                val cmUrl = payload?.cm ?: Regex(""""cm"\s*:\s*"([^"]+)"""").find(jsonStr)?.groupValues?.get(1)?.replace("\\/", "/")
                                val tmUrl = payload?.tm ?: Regex(""""tm"\s*:\s*"([^"]+)"""").find(jsonStr)?.groupValues?.get(1)?.replace("\\/", "/")
                                val streams = listOfNotNull(cmUrl, tmUrl).distinct()
                                for (streamUrl in streams) {
                                    if (streamUrl.startsWith("http")) {
                                        val streamName = if (streamUrl == tmUrl) "RapidVid Dublaj" else "RapidVid"
                                        callback(
                                            newExtractorLink(
                                                source = "RapidVid",
                                                name = streamName,
                                                url = streamUrl,
                                                type = ExtractorLinkType.M3U8
                                            ) {
                                                this.referer = "https://rapidvid.org/"
                                                this.headers = mapOf(
                                                    "Referer" to "https://rapidvid.org/",
                                                    "User-Agent" to SafeHttpClient.DEFAULT_USER_AGENT
                                                )
                                            }
                                        )
                                        foundStream = true
                                    }
                                }

                                for (cap in payload?.ct.orEmpty()) {
                                    val capFile = cap.file?.replace("\\/", "/")
                                    if (!capFile.isNullOrBlank()) {
                                        subtitleCallback(
                                            SubtitleFile(
                                                lang = cap.label?.trim() ?: "Türkçe",
                                                url = capFile
                                            )
                                        )
                                    }
                                }
                            } catch (_: Exception) {}

                        }
                    }
                }

                // 2. Legacy av(...) file regex fallback
                if (!foundStream) {
                    val avPattern = Pattern.compile(""""?file"?\s*:\s*av\(['"]([^'"]+)['"]\)""")
                    val avMatcher = avPattern.matcher(html)
                    if (avMatcher.find()) {
                        val token = avMatcher.group(1)
                        if (!token.isNullOrBlank()) {
                            val streamUrl = decryptRapidvidAv(token)
                            if (streamUrl.isNotBlank()) {
                                val preflight = com.cloudstream.tr.core.network.StreamValidator.validateStream(
                                    url = streamUrl,
                                    headers = mapOf("Referer" to "https://rapidvid.org/"),
                                    provider = "RapidVid"
                                )
                                if (preflight.isValid) {
                                    callback(
                                        newExtractorLink(
                                            source = "RapidVid",
                                            name = "RapidVid",
                                            url = streamUrl,
                                            type = preflight.streamType
                                        ) {
                                            this.referer = "https://rapidvid.org/"
                                            this.headers = mapOf(
                                                "Referer" to "https://rapidvid.org/",
                                                "User-Agent" to SafeHttpClient.DEFAULT_USER_AGENT
                                            )
                                        }
                                    )
                                    foundStream = true
                                }
                            }
                        }
                    }
                }

                // Subtitles fallback (jwSetup.tracks)
                val tracksPattern = Pattern.compile("""jwSetup\.tracks\s*=\s*(\[.+?\]);""", Pattern.DOTALL)
                val tracksMatcher = tracksPattern.matcher(html)
                if (tracksMatcher.find()) {
                    val tracksJson = tracksMatcher.group(1)
                    val trackPattern = Pattern.compile(""""file"\s*:\s*"([^"]+)"[^}]+?"label"\s*:\s*"([^"]+)"""")
                    val trackMatcher = trackPattern.matcher(tracksJson ?: "")
                    while (trackMatcher.find()) {
                        val file = trackMatcher.group(1)?.replace("\\/", "/") ?: continue
                        val label = trackMatcher.group(2) ?: "Türkçe"
                        subtitleCallback(SubtitleFile(lang = label.trim(), url = file))
                    }
                }
            } catch (e: Exception) {
                com.cloudstream.tr.core.diagnostics.DiagnosticLogger.log(
                    provider = "RapidVid",
                    stage = com.cloudstream.tr.core.diagnostics.DiagnosticStage.EXTRACTOR,
                    category = com.cloudstream.tr.core.diagnostics.DiagnosticCategory.EXTRACTOR,
                    message = "RapidVid HTML parsing failed: ${e.message}",
                    throwable = e
                )
            }
            return foundStream
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val resp = app.get(url, headers = SafeHttpClient.defaultHeaders(referer = referer ?: mainUrl)).text
            parseHtmlResponse(resp, subtitleCallback, callback)
        } catch (e: Exception) {
            com.cloudstream.tr.core.diagnostics.DiagnosticLogger.log(
                provider = name,
                stage = com.cloudstream.tr.core.diagnostics.DiagnosticStage.EXTRACTOR,
                category = com.cloudstream.tr.core.diagnostics.DiagnosticCategory.EXTRACTOR,
                message = "RapidVid getUrl failed: ${e.message}",
                url = url,
                throwable = e
            )
        }
    }
}
