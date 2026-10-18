package com.cloudstream.tr.hub

import android.content.Context
import com.cloudstream.tr.core.model.ProviderModels
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import kotlin.math.abs
import kotlin.math.max

/**
 * Merges search and catalog items from multiple providers and TMDB into a single unified card.
 * Enforces strict boundaries:
 * - High-confidence matches merge automatically.
 * - Ambiguous matches remain separate.
 * - Never merge movie with series, or anime movie with anime series.
 * - Never merge different years / remakes.
 * - Strict season and episode matching with specials (season 0) isolation.
 * - Retains all matched provider content references in CanonicalMedia & HubMediaRegistry.
 */
object HubSingleCardMerger {

    private object HubSearchResponseBuilder : MainAPI() {
        override var name = "CloudStreamHub"
        override var mainUrl = "https://api.themoviedb.org/3"
    }

    data class CardCandidate(
        val item: SearchResponse,
        val normTitle: String,
        val year: Int?,
        val isMovie: Boolean,
        val isAnimeMovie: Boolean,
        val isAnimeSeries: Boolean,
        val providerName: String
    )

    private val RELEASE_FILLERS = listOf(
        "türkçe dublaj", "türkçe altyazılı", "turkce dublaj", "turkce altyazili",
        "tr dublaj", "tr altyazı", "tr altyazi", "dublaj", "dublajlı", "dublajli",
        "altyazılı", "altyazili", "altyazı", "altyazi", "full hd izle", "full hd",
        "hd izle", "tek parça", "tek parca", "filmi izle", "izle", "filmi",
        "sinema çekimi", "1080p", "720p", "480p", "4k", "uhd", "bluray", "web-dl",
        "remastered", "extended cut", "director's cut"
    )

    internal fun cleanTitle(title: String, year: Int?): String {
        var t = title.lowercase(java.util.Locale("tr", "TR"))
        for (filler in RELEASE_FILLERS) {
            t = t.replace(filler, " ")
        }
        if (year != null) {
            t = t.replace(year.toString(), " ")
        }
        t = t.replace(Regex("\\b(19\\d\\d|20\\d\\d)\\b"), " ")
        t = t.replace(Regex("[^a-z0-9ğüşıöç\\s]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun toCandidate(
        item: SearchResponse,
        defaultProvider: String = "CloudStreamHub",
        discoverableProviders: List<MainAPI> = emptyList()
    ): CardCandidate {
        val norm = ProviderModels.normalizeTitle(item.name)
        val y: Int? = when (item) {
            is MovieSearchResponse -> item.year
            is TvSeriesSearchResponse -> item.year
            is AnimeSearchResponse -> item.year
            else -> null
        }
        val isAnimeMovie = item.type == TvType.AnimeMovie
        val isAnimeSeries = item.type == TvType.Anime
        val isMovie = when {
            isAnimeMovie -> true
            isAnimeSeries -> false
            item is MovieSearchResponse -> true
            item is TvSeriesSearchResponse -> false
            item.type == TvType.Movie -> true
            item.type == TvType.TvSeries || item.type == TvType.AsianDrama -> false
            else -> true
        }
        val provider = if (item.url.contains("themoviedb.org")) {
            "TMDB"
        } else {
            val api = item.apiName.takeIf { it.isNotBlank() && it != "CloudStreamHub" }
            val detected = if (api == null && discoverableProviders.isNotEmpty()) {
                CloudStreamProviderRegistryAdapter.detectProviderNameForUrl(item.url, discoverableProviders)
            } else null
            val hostName = try {
                java.net.URI(item.url).host?.removePrefix("www.")?.substringBefore(".")?.replaceFirstChar { it.uppercase() }
            } catch (_: Throwable) { null }
            api ?: detected ?: hostName ?: defaultProvider
        }
        return CardCandidate(item, norm, y, isMovie, isAnimeMovie, isAnimeSeries, provider)
    }

    fun extractSequelTag(title: String): String? {
        val lower = title.lowercase(java.util.Locale("tr", "TR"))
        val patterns = listOf(
            Regex("\\b(?:part|bölüm|kisim|kısım)\\s*(\\d+|[ivx]+|bir|iki|üç|dört|one|two|three|four)\\b"),
            Regex("\\b(\\d+)\\s*(?:part|bölüm|kisim|kısım)\\b"),
            Regex("\\b(?:film|filmi)\\s*(\\d+)\\b"),
            Regex("\\b(\\d+)\\b"),
            Regex("\\b([ivx]{1,4})\\b"),
            Regex("\\b(two|three|four|iki|üç|dört)\\b")
        )
        for (pat in patterns) {
            val m = pat.find(lower)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    /**
     * Determines whether two candidates represent the EXACT same underlying media with high confidence.
     */
    fun isConfidentSameMedia(a: CardCandidate, b: CardCandidate): Boolean {
        // 1. Strict media type separation: Never merge movie with tv series
        if (a.isMovie != b.isMovie) return false

        // 2. Strict anime separation: Never merge anime movie with anime series
        if (a.isAnimeMovie && b.isAnimeSeries) return false
        if (a.isAnimeSeries && b.isAnimeMovie) return false

        // 3. Strict year boundary: Never merge different release years (e.g. remakes, 1984 vs 2021, 2001 vs 2005)
        if (a.year != null && b.year != null && a.year != b.year) {
            return false
        }

        // 4. Check registered canonical media registry (e.g. TMDB alias link)
        val regA = HubMediaRegistry.find(a.item.name, a.year, a.isMovie)
        val regB = HubMediaRegistry.find(b.item.name, b.year, b.isMovie)
        if (regA != null && regB != null && regA.id == regB.id) {
            return true
        }

        val cleanA = cleanTitle(a.normTitle, a.year)
        val cleanB = cleanTitle(b.normTitle, b.year)

        // 5. Strict sequel tag check (e.g. Spider-Man vs Spider-Man 2, Dune vs Dune Part Two)
        val seqA = extractSequelTag(cleanA)
        val seqB = extractSequelTag(cleanB)
        if (seqA != seqB) {
            return false
        }

        // 6. Direct normalized title comparison
        if (a.normTitle.isNotBlank() && a.normTitle == b.normTitle) return true

        // 7. Clean titles without release years or video release fillers
        if (cleanA.isNotBlank() && cleanA == cleanB) {
            return true
        }

        // 8. Token set containment check (only when release years are known and identical)
        val tokensA = cleanA.split(" ").filter { it.isNotBlank() }.toSet()
        val tokensB = cleanB.split(" ").filter { it.isNotBlank() }.toSet()
        if (tokensA.isNotEmpty() && tokensB.isNotEmpty()) {
            val common = tokensA.intersect(tokensB)
            if ((common == tokensA || common == tokensB) && a.year != null && b.year != null && a.year == b.year) {
                return true
            }
        }

        return false
    }

    /**
     * Groups a heterogeneous list of search items into deduplicated single-card responses,
     * registering all constituent provider content references into HubMediaRegistry.
     */
    fun mergeCandidates(items: List<SearchResponse>, defaultProvider: String = "CloudStreamHub"): List<SearchResponse> {
        if (items.isEmpty()) return emptyList()

        val discoverableProviders = CloudStreamProviderRegistryAdapter.getAllDiscoverableProviders()
        val candidates = items.map { toCandidate(it, defaultProvider, discoverableProviders) }
        val clusters = mutableListOf<MutableList<CardCandidate>>()

        for (cand in candidates) {
            var matchedCluster: MutableList<CardCandidate>? = null
            for (cluster in clusters) {
                val representative = cluster.first()
                if (isConfidentSameMedia(representative, cand)) {
                    matchedCluster = cluster
                    break
                }
            }
            if (matchedCluster != null) {
                matchedCluster.add(cand)
            } else {
                clusters.add(mutableListOf(cand))
            }
        }

        val merged = clusters.map { cluster ->
            buildMergedCard(cluster, flushToDisk = false)
        }
        HubMediaRegistry.flushToDisk(HubContextHolder.pluginContext)
        return merged
    }

    private fun buildMergedCard(cluster: List<CardCandidate>, flushToDisk: Boolean = false): SearchResponse {
        val references = cluster.map { cand ->
            ProviderContentReference(
                providerName = cand.providerName,
                url = cand.item.url,
                realId = cand.item.url.substringAfterLast("/").substringBefore("?").ifBlank { null },
                title = cand.item.name,
                year = cand.year,
                tvType = cand.item.type,
                confidenceScore = if (cand.providerName == "TMDB") 1.0 else 0.95
            )
        }

        val primary = cluster.firstOrNull { it.providerName == "TMDB" } ?: cluster.first()
        val canonicalItem = primary.item
        val bestPoster = cluster.mapNotNull { it.item.posterUrl }.firstOrNull { it.isNotBlank() } ?: canonicalItem.posterUrl
        val bestYear = cluster.mapNotNull { it.year }.firstOrNull() ?: primary.year

        val tmdbId = Regex("/(?:movie|tv)/(\\d+)").find(canonicalItem.url)?.groupValues?.get(1)?.toIntOrNull()
        val cleanNorm = cleanTitle(primary.normTitle, bestYear).ifBlank { primary.normTitle }
        val existingMedia = (if (tmdbId != null) HubMediaRegistry.findByTmdb(tmdbId, primary.isMovie, HubContextHolder.pluginContext) else null)
            ?: HubMediaRegistry.find(canonicalItem.name, bestYear, primary.isMovie, HubContextHolder.pluginContext)
            ?: cluster.firstNotNullOfOrNull { HubMediaRegistry.getByUrl(it.item.url, HubContextHolder.pluginContext) }

        val canonicalId = existingMedia?.id ?: if (tmdbId != null) {
            "tmdb:${if (primary.isMovie) "movie" else "tv"}:$tmdbId"
        } else {
            val safeNorm = cleanNorm.replace(" ", "_")
            "hub:${if (primary.isMovie) "movie" else "tv"}:${safeNorm}_${bestYear ?: 0}"
        }

        val canonicalMedia = CanonicalMedia(
            id = canonicalId,
            title = canonicalItem.name,
            originalTitle = null,
            year = bestYear,
            isMovie = primary.isMovie,
            tvType = canonicalItem.type ?: (if (primary.isMovie) TvType.Movie else TvType.TvSeries),
            posterUrl = bestPoster,
            score = canonicalItem.score?.toString(),
            tmdbId = tmdbId ?: existingMedia?.tmdbId,
            providerReferences = references
        )
        val registered = HubMediaRegistry.register(
            canonicalMedia,
            HubContextHolder.pluginContext,
            flushImmediate = flushToDisk
        )

        return if (primary.isMovie) {
            HubSearchResponseBuilder.newMovieSearchResponse(
                canonicalItem.name,
                canonicalItem.url,
                canonicalItem.type ?: TvType.Movie
            ) {
                this.posterUrl = registered.posterUrl ?: bestPoster
                this.year = registered.year ?: bestYear
                this.score = canonicalItem.score
            }
        } else {
            HubSearchResponseBuilder.newTvSeriesSearchResponse(
                canonicalItem.name,
                canonicalItem.url,
                canonicalItem.type ?: TvType.TvSeries
            ) {
                this.posterUrl = registered.posterUrl ?: bestPoster
                this.year = registered.year ?: bestYear
                this.score = canonicalItem.score
            }
        }
    }

    fun getCanonicalMedia(item: SearchResponse, context: Context? = HubContextHolder.pluginContext): CanonicalMedia? {
        return HubMediaRegistry.getByUrl(item.url, context) ?: HubMediaRegistry.find(item.name, null, item is MovieSearchResponse, context)
    }

    /**
     * Strictly matches a series episode, ensuring Season 0 (specials) are never mapped to regular episodes.
     */
    fun matchEpisode(episodes: List<Episode>, targetSeason: Int, targetEpisode: Int): Episode? {
        if (targetSeason == 0) {
            return episodes.firstOrNull { (it.season ?: 0) == 0 && it.episode == targetEpisode }
        }
        return episodes.firstOrNull {
            val s = it.season ?: 1
            s == targetSeason && it.episode == targetEpisode
        }
    }
}
