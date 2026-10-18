package com.cloudstream.tr.core.resolvers

import com.cloudstream.tr.core.diagnostics.DiagnosticCategory
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.diagnostics.DiagnosticStage
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

data class TorrentioResponse(
    @param:JsonProperty("streams") val streams: List<TorrentioStream>? = null
)

data class TorrentioBehaviorHints(
    @param:JsonProperty("filename") val filename: String? = null,
    @param:JsonProperty("bingeGroup") val bingeGroup: String? = null
)

data class TorrentioStream(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("infoHash") val infoHash: String? = null,
    @param:JsonProperty("fileIdx") val fileIdx: Int? = null,
    @param:JsonProperty("behaviorHints") val behaviorHints: TorrentioBehaviorHints? = null,
    @param:JsonProperty("url") val url: String? = null,
    @param:JsonProperty("sources") val sources: List<String>? = null
)

data class TorrentioLookupEvidence(
    val links: List<ExtractorLink>,
    /** True only when Torrentio successfully returned an explicitly empty streams array. */
    val authoritativeEmpty: Boolean = false
)

object TorrentioResolver {
    internal fun keepTopThreePerQuality(links: List<ExtractorLink>): List<ExtractorLink> {
        val qualityCounts = mutableMapOf<Int, Int>()
        return links.filter { link ->
            val count = qualityCounts.getOrDefault(link.quality, 0)
            if (count >= 3) {
                false
            } else {
                qualityCounts[link.quality] = count + 1
                true
            }
        }
    }

    internal fun qualityOf(vararg descriptions: String?): Int {
        for (description in descriptions) {
            val match = Regex("""\b(4K|[0-9]{3,4}p)\b""", RegexOption.IGNORE_CASE)
                .find(description.orEmpty()) ?: continue
            return if (match.value.equals("4K", ignoreCase = true)) 2160
            else match.value.dropLast(1).toIntOrNull() ?: Qualities.Unknown.value
        }
        return Qualities.Unknown.value
    }

    /** Unknown seed count is not zero peers. */
    internal fun reportedSeeds(title: String): Int? {
        val value = Regex("""(?:\uD83D\uDC64|\uD83D\uDC65|\bseeds?\s*[:=]?)\s*([0-9][0-9,]*)""", RegexOption.IGNORE_CASE)
            .find(title)?.groupValues?.get(1)
            ?: Regex("""([0-9][0-9,]*)\s+seeds?\b""", RegexOption.IGNORE_CASE)
                .find(title)?.groupValues?.get(1)
        return value?.replace(",", "")?.toIntOrNull()
    }

    internal fun seedCount(title: String): Int = reportedSeeds(title) ?: 0

    suspend fun resolve(imdbId: String, isMovie: Boolean, season: Int? = null, episode: Int? = null): List<ExtractorLink> =
        resolveWithEvidence(imdbId, isMovie, season, episode).links

    /** Distinguish authoritative empty results from timeout, parse, and network failures. */
    suspend fun resolveWithEvidence(imdbId: String, isMovie: Boolean, season: Int? = null, episode: Int? = null): TorrentioLookupEvidence {
        if (!Regex("tt[0-9]+").matches(imdbId)) return TorrentioLookupEvidence(emptyList())
        val type = if (isMovie) "movie" else "series"
        val idPath = if (isMovie) imdbId else "$imdbId:$season:$episode"
        val options = "qualityfilter=scr,cam|sort=quality|limit=20"
        val prefix = DebridConfig.getActiveDebridPrefix()?.let { "$it|" } ?: ""
        val fullOptions = "$prefix$options"
        val url = "https://torrentio.strem.fun/$fullOptions/stream/$type/$idPath.json"
        val hasDebrid = DebridConfig.isDebridEnabled

        try {
            val response = app.get(url, timeout = 10).parsedSafe<TorrentioResponse>()
            return parseStreamEvidence(response, hasDebrid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLogger.log(
                provider = "Torrentio", stage = DiagnosticStage.LOAD,
                category = DiagnosticCategory.NETWORK,
                message = "Torrentio request failed (${e.javaClass.simpleName})"
            )
            return TorrentioLookupEvidence(emptyList())
        }
    }

    internal suspend fun parseStreamEvidence(response: TorrentioResponse?, hasDebrid: Boolean): TorrentioLookupEvidence {
        val streams = response?.streams ?: return TorrentioLookupEvidence(emptyList())
        return TorrentioLookupEvidence(mapStreams(streams, hasDebrid), authoritativeEmpty = streams.isEmpty())
    }

    internal suspend fun mapStreams(streams: List<TorrentioStream>, hasDebrid: Boolean): List<ExtractorLink> {
        return streams.mapNotNull { stream ->
            val rawTitle = stream.title ?: stream.name ?: "Unknown"

            // Filter out CAM/TS/SCR if any slipped through
            if (Regex("""\b(CAM|HDCAM|TELESYNC|TS|SCR)\b""", RegexOption.IGNORE_CASE).containsMatchIn(rawTitle)) {
                return@mapNotNull null
            }

            // Exclude .avi container files to eliminate ERROR_CODE_DECODING_FAILED (4003) in ExoPlayer OMX.google.mp3.decoder
            val filename = stream.behaviorHints?.filename.orEmpty()
            if (rawTitle.contains(".avi", ignoreCase = true) || filename.endsWith(".avi", ignoreCase = true)) {
                return@mapNotNull null
            }

            val seedCount = reportedSeeds(rawTitle)
            val isDirectUrl = stream.url?.let { it.startsWith("https://", true) || it.startsWith("http://", true) } == true
            if (!hasDebrid && !isDirectUrl && seedCount != null && seedCount < 3) return@mapNotNull null

            val sizeMatch = Regex("""\b(\d+(?:\.\d+)?\s*[GgMm]B)\b""").find(rawTitle)
            val size = sizeMatch?.groupValues?.get(1) ?: ""

            val mappedQuality = qualityOf(stream.title, stream.behaviorHints?.filename, stream.name)
            val resolution = if (mappedQuality == Qualities.Unknown.value) "Unknown" else "${mappedQuality}p"

            val isRemux = rawTitle.contains("REMUX", ignoreCase = true)
            val isDV = Regex("""(?i)(?:\bDV\b|\bDOVI\b|\bDolby[ ._-]?Vision\b)""").containsMatchIn(rawTitle)
            val isHDR10Plus = rawTitle.contains("HDR10+", ignoreCase = true)
            val isHDR = rawTitle.contains("HDR", ignoreCase = true) && !isHDR10Plus
            val isTrueHD = rawTitle.contains("TrueHD", ignoreCase = true)
            val isDtsHdMa = rawTitle.contains("DTS-HD MA", ignoreCase = true) || rawTitle.contains("DTS-HD", ignoreCase = true)
            val isAtmos = rawTitle.contains("Atmos", ignoreCase = true)
            val is71 = rawTitle.contains("7.1")
            val is51 = rawTitle.contains("5.1") || rawTitle.contains("DD")

            var tags = ""
            val languageClaims = listOfNotNull(stream.title, stream.name, stream.behaviorHints?.filename).joinToString(" ")
            val claimed = linkedSetOf<String>()
            if (Regex("""(?iu)(?:(?<![\p{L}\p{N}])(?:tr|tur|turkish|türkçe|dublaj)(?![\p{L}\p{N}])|🇹🇷)""").containsMatchIn(languageClaims)) claimed.add("TR")
            if (Regex("""(?iu)(?:(?<![\p{L}\p{N}])(?:en|eng|english)(?![\p{L}\p{N}])|🇬🇧|🇺🇸)""").containsMatchIn(languageClaims)) claimed.add("EN")
            for ((code, labels) in mapOf("DE" to "de|deu|ger|german", "FR" to "fr|fra|fre|french",
                "ES" to "es|spa|spanish", "IT" to "it|ita|italian", "JA" to "ja|jpn|japanese",
                "KO" to "ko|kor|korean", "RU" to "ru|rus|russian", "AR" to "ar|ara|arabic",
                "PT" to "pt|por|portuguese", "ZH" to "zh|zho|chi|chinese", "HI" to "hi|hin|hindi")) {
                if (Regex("""(?iu)(?<![\p{L}\p{N}])(?:$labels)(?![\p{L}\p{N}])""").containsMatchIn(languageClaims)) claimed.add(code)
            }
            if (claimed.isNotEmpty()) tags += " [reported language: ${claimed.joinToString("/")}; unverified]"
            if (isRemux) tags += " [REMUX]"
            if (isDV) tags += " [DV]"
            if (isHDR10Plus) tags += " [HDR10+]"
            else if (isHDR) tags += " [HDR]"

            if (isTrueHD && isAtmos && is71) tags += " [TrueHD Atmos 7.1]"
            else if (isDtsHdMa && is71) tags += " [DTS-HD MA 7.1]"
            else if (isAtmos && is71) tags += " [Atmos 7.1]"
            else if (isAtmos) tags += " [Atmos]"
            else if (is71) tags += " [7.1]"
            else if (is51) tags += " [5.1]"

            val seedStr = if (seedCount == null) " (seeds unknown)" else " ($seedCount seeds)"
            val prefixStr = if (hasDebrid) "🚀 Debrid " else "Torrentio • "
            val displayName = "$prefixStr$resolution$tags ${if (size.isNotBlank()) "[$size] " else ""}$seedStr".trim()

            if (isDirectUrl) {
                return@mapNotNull com.lagradost.cloudstream3.utils.newExtractorLink(
                    source = "Torrentio",
                    name = displayName,
                    url = stream.url ?: return@mapNotNull null,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.quality = mappedQuality
                }
            }

            val hash = stream.infoHash?.takeIf { TorrentTrackers.isValidHash(it) } ?: return@mapNotNull null
            val rawFirstLine = rawTitle.lines().firstOrNull()?.trim() ?: "Torrent"
            val sanitizedTitle = rawFirstLine.replace(Regex("""[^\w\s\.\-\(\)\[\]]"""), "").trim().ifBlank { "Torrent" }
            val cleanTitle = stream.behaviorHints?.filename?.takeIf { it.isNotBlank() } ?: sanitizedTitle
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")

            // The first file of a multi-file feature is not necessarily the movie.
            val fileIndex = stream.fileIdx
            if (fileIndex == null || fileIndex < 0) {
                DiagnosticLogger.log(
                    provider = "Torrentio", stage = DiagnosticStage.EXTRACTOR,
                    category = DiagnosticCategory.SOURCE_DISCOVERY,
                    message = "Torrent candidate skipped: file index missing or invalid"
                )
                return@mapNotNull null
            }
            val magnetUrl = "magnet:?xt=urn:btih:$hash&dn=$encodedTitle&index=$fileIndex${TorrentTrackers.magnetParams(stream.sources.orEmpty())}"

            com.lagradost.cloudstream3.utils.newExtractorLink(
                source = "Torrentio",
                name = displayName,
                url = magnetUrl,
                type = ExtractorLinkType.MAGNET
            ) {
                this.quality = mappedQuality
            }
        }
    }
}
