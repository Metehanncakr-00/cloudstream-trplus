package com.cloudstream.tr.core.model

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import java.util.Locale

object ProviderModels {

    /**
     * Standardizes source display names with explicit language / dubbing tags
     */
    fun formatSourceTitle(
        sourceName: String,
        isDubbed: Boolean = false,
        isSubbed: Boolean = false,
        resolution: String? = null
    ): String {
        val tags = mutableListOf<String>()
        if (isDubbed) tags.add("TR Dublaj")
        if (isSubbed) tags.add("TR Altyazı")
        if (resolution != null && resolution.isNotBlank()) tags.add(resolution.trim())

        return if (tags.isNotEmpty()) {
            "$sourceName [${tags.joinToString(" • ")}]"
        } else {
            sourceName
        }
    }

    /**
     * Normalizes a title for deduplication and matching (lowercased, punctuation stripped)
     */
    fun normalizeTitle(rawTitle: String): String {
        var processed = rawTitle.lowercase(Locale("tr", "TR"))
        
        val fillers = listOf("türkçe dublaj", "türkçe altyazılı", "tr dublaj", "tr altyazı", "full hd izle", "full hd", "tek parça", "izle", "1080p", "720p", "4k")
        for (filler in fillers) {
            processed = processed.replace(filler, "")
        }
        
        processed = processed.replace("&", " ve ")

        return processed
            .replace(Regex("[^a-z0-9ğüşıöç\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Deduplicates search results across multiple queries or providers by URL and compound key
     * (normalized title + year + type) so remakes and cross-type titles are preserved.
     */
    fun dedupSearchResults(results: List<SearchResponse>): List<SearchResponse> {
        val seenUrls = mutableSetOf<String>()
        val seenKeys = mutableSetOf<String>()
        val output = mutableListOf<SearchResponse>()

        for (item in results) {
            val normUrl = item.url.trim().removeSuffix("/")
            val normTitle = normalizeTitle(item.name)
            val itemYear: Int? = when (item) {
                is MovieSearchResponse -> item.year
                is TvSeriesSearchResponse -> item.year
                is AnimeSearchResponse -> item.year
                else -> null
            }
            val itemType = item.type?.name ?: "unknown"
            val dedupKey = "$normTitle:${itemYear ?: "any"}:$itemType"

            if (seenUrls.contains(normUrl) || seenKeys.contains(dedupKey)) {
                continue
            }
            seenUrls.add(normUrl)
            seenKeys.add(dedupKey)
            output.add(item)
        }
        return output
    }
}
