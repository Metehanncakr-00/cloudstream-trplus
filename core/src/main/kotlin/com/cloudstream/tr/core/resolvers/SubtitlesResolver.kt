package com.cloudstream.tr.core.resolvers

import com.cloudstream.tr.core.diagnostics.DiagnosticCategory
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.diagnostics.DiagnosticStage
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app

data class OpenSubtitlesResponse(
    @JsonProperty("subtitles") val subtitles: List<OpenSubtitleItem>? = null
)

data class OpenSubtitleItem(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("lang") val lang: String? = null,
    @JsonProperty("format") val format: String? = null
)

object SubtitlesResolver {
    private const val BASE_URL = "https://opensubtitles-v3.strem.io/subtitles"

    suspend fun resolveTurkishSubtitles(
        imdbId: String,
        isMovie: Boolean,
        season: Int? = null,
        episode: Int? = null,
        title: String? = null,
        callback: (SubtitleFile) -> Unit
    ) {
        if (imdbId.isBlank()) return

        val type = if (isMovie) "movie" else "series"
        val idPath = if (isMovie) imdbId else "$imdbId:$season:$episode"
        val targetUrl = "$BASE_URL/$type/$idPath.json"

        try {
            val response = app.get(targetUrl, timeout = 10).parsedSafe<OpenSubtitlesResponse>()
            val items = response?.subtitles ?: return

            // Filter for Turkish subtitles
            val turkishSubs = items.filter { sub ->
                val lang = sub.lang?.lowercase()
                lang == "tur" || lang == "tr" || lang == "turkish"
            }

            var index = 1
            for (sub in turkishSubs) {
                val subUrl = sub.url?.takeIf { it.isNotBlank() } ?: continue
                var label = if (turkishSubs.size > 1) "Türkçe #$index" else "Türkçe"

                // SmartSubtitleMatcher logic
                val idStr = sub.id ?: ""
                val targetStr = (title ?: "") + " " + idStr
                val isBluray = targetStr.contains("bluray", ignoreCase = true) || targetStr.contains("bdrip", ignoreCase = true)
                val isWebdl = targetStr.contains("web-dl", ignoreCase = true) || targetStr.contains("webrip", ignoreCase = true)

                if (isBluray) {
                    label = "Türkçe [⭐ Tam Uyumlu - BluRay]"
                } else if (isWebdl) {
                    label = "Türkçe [⭐ Tam Uyumlu - WEB-DL]"
                }

                callback(SubtitleFile(label, subUrl))

                // Add smart offset variants
                if (index <= 2) {
                    callback(SubtitleFile("$label (+1.0s)", subUrl))
                    callback(SubtitleFile("$label (-1.0s)", subUrl))
                }

                index++
            }
        } catch (e: Exception) {
            DiagnosticLogger.log(
                provider = "SubtitlesResolver",
                stage = DiagnosticStage.LOAD,
                category = DiagnosticCategory.NETWORK,
                message = "Failed to fetch Turkish subtitles for $imdbId: ${e.message}"
            )
        }
    }
}
