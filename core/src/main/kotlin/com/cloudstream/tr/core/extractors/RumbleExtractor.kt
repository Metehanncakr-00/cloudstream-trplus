package com.cloudstream.tr.core.extractors

import com.cloudstream.tr.core.diagnostics.DiagnosticCategory
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.diagnostics.DiagnosticStage
import com.cloudstream.tr.core.network.SafeHttpClient
import com.cloudstream.tr.core.network.StreamValidator
import com.cloudstream.tr.core.network.ValidationStatus
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class RumbleExtractor : ExtractorApi() {
    override val name = "Rumble"
    override val mainUrl = "https://rumble.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        extract(url, referer, subtitleCallback, callback)
    }

    companion object {
        suspend fun extract(
            url: String,
            referer: String?,
            subtitleCallback: ((SubtitleFile) -> Unit)? = null,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            val cleanEmbed = url.substringBefore("#")
            var linksFound = false

            try {
                val doc = app.get(
                    cleanEmbed,
                    headers = SafeHttpClient.defaultHeaders(referer = referer ?: "https://rumble.com/")
                ).text

                val candidateStreams = extractStreamCandidates(doc)

                for ((streamUrl, streamType, quality) in candidateStreams) {
                    val preflight = StreamValidator.validateStream(
                        url = streamUrl,
                        headers = mapOf("Referer" to cleanEmbed),
                        provider = "Rumble"
                    )

                    when (preflight.status) {
                        ValidationStatus.VALID -> {
                            val typeTag = if (preflight.streamType == ExtractorLinkType.M3U8) "HLS" else "MP4"
                            callback(
                                newExtractorLink(
                                    source = "Rumble",
                                    name = "Rumble $typeTag",
                                    url = streamUrl,
                                    type = preflight.streamType
                                ) {
                                    this.referer = cleanEmbed
                                    this.quality = quality
                                }
                            )
                            linksFound = true
                            if (preflight.streamType == ExtractorLinkType.M3U8) {
                                break
                            }
                        }
                        ValidationStatus.INDETERMINATE -> {
                            DiagnosticLogger.log(
                                provider = "Rumble",
                                stage = DiagnosticStage.STREAM_PREFLIGHT,
                                category = DiagnosticCategory.NETWORK,
                                message = "Indeterminate preflight for $streamUrl (${preflight.failureReason})"
                            )
                        }
                        ValidationStatus.INVALID -> {
                            DiagnosticLogger.log(
                                provider = "Rumble",
                                stage = DiagnosticStage.STREAM_PREFLIGHT,
                                category = DiagnosticCategory.NETWORK,
                                message = "Rejected invalid Rumble stream $streamUrl (${preflight.failureReason})"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                DiagnosticLogger.log(
                    provider = "Rumble",
                    stage = DiagnosticStage.EXTRACTOR,
                    category = DiagnosticCategory.EXTRACTOR,
                    message = "Rumble extraction failed: ${e.message}",
                    throwable = e
                )
            }

            return linksFound
        }

        fun extractStreamCandidates(html: String): List<Triple<String, ExtractorLinkType, Int>> {
            val candidates = mutableListOf<Triple<String, ExtractorLinkType, Int>>()

            // 1. Primary: HLS Master Playlist from hls: { url: ... } or rumble.com/hls-vod/...
            val hlsMatch = Regex("""["']hls["']\s*:\s*\{[^}]*["']url["']\s*:\s*["']([^"']+)["']""").find(html)
                ?: Regex("""https?:\\?/\\?/[^"'\s<>]+\.rumble\.com/hls-vod/[^"'\s<>]+\.m3u8[^"'\s<>]*""").find(html)
                ?: Regex("""["']url["']\s*:\s*["'](https?:\\?/\\?/[^"'\s<>]+\.m3u8[^"'\s<>]*)["']""").find(html)

            hlsMatch?.let { match ->
                val rawUrl = if (match.groupValues.size > 1 && match.groupValues[1].isNotBlank()) match.groupValues[1] else match.value
                val cleanUrl = rawUrl.replace("""\/""", "/")
                candidates.add(Triple(cleanUrl, ExtractorLinkType.M3U8, Qualities.Unknown.value))
            }

            // 2. Secondary: Direct MP4 video streams (excluding timeline thumbnail scrubbers)
            val mp4ResRegex = Regex("""["'](\d{3,4})["']\s*:\s*\{[^}]*["']url["']\s*:\s*["']([^"']+\.mp4[^"']*)["']""")
            for (match in mp4ResRegex.findAll(html)) {
                val heightStr = match.groupValues[1]
                val rawUrl = match.groupValues[2]
                val cleanUrl = rawUrl.replace("""\/""", "/")
                if (!cleanUrl.contains(".Faa.mp4") && !cleanUrl.contains("timeline")) {
                    val quality = when (heightStr.toIntOrNull()) {
                        1080 -> Qualities.P1080.value
                        720 -> Qualities.P720.value
                        480 -> Qualities.P480.value
                        360 -> Qualities.P360.value
                        240 -> Qualities.P240.value
                        else -> Qualities.Unknown.value
                    }
                    candidates.add(Triple(cleanUrl, ExtractorLinkType.VIDEO, quality))
                }
            }

            // 3. Fallback: Direct progressive MP4 if present in "mp4": { "url": "..." }
            val mp4DirectMatch = Regex("""["']mp4["']\s*:\s*\{[^}]*["']url["']\s*:\s*["']([^"']+)["']""").find(html)
            mp4DirectMatch?.let { match ->
                val cleanUrl = match.groupValues[1].replace("""\/""", "/")
                if (!cleanUrl.contains("timeline") && !candidates.any { it.first == cleanUrl }) {
                    candidates.add(Triple(cleanUrl, ExtractorLinkType.VIDEO, Qualities.Unknown.value))
                }
            }

            return candidates.distinctBy { it.first }
        }
    }
}
