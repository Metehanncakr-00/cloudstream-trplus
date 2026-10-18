package com.cloudstream.tr.hub

import com.cloudstream.tr.core.model.ProviderModels
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.max

object HubMatchingEngine {

    /**
     * Uses the provider's quick-search contract, falling back to the first paged-search result
     * when quickSearch is unsupported or returns no result list.
     */
    suspend fun searchProvider(provider: MainAPI, query: String): List<SearchResponse>? {
        val quickResults = try {
            provider.quickSearch(query)
        } catch (_: NotImplementedError) {
            null
        }
        if (quickResults != null) return quickResults

        return try {
            provider.search(query, 1)?.items
        } catch (_: NotImplementedError) {
            emptyList()
        }
    }

    /**
     * Searches titles in priority order and returns the first confident match.
     * Search failures and unconfident result lists are skipped without selecting an arbitrary result.
     */
    suspend fun findConfidentMatchFromSearches(
        searchQueries: List<String>,
        targetTitles: List<String>,
        targetYear: Int? = null,
        isMovie: Boolean? = null,
        search: suspend (String) -> List<SearchResponse>?
    ): SearchResponse? {
        for (query in searchQueries.distinct()) {
            val candidates = try {
                search(query).orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            val match = findConfidentMatch(
                candidates = candidates,
                targetTitles = targetTitles,
                targetYear = targetYear,
                isMovie = isMovie
            )
            if (match != null) return match
        }
        return null
    }

    /**
     * Calculates Jaccard token overlap similarity between two normalized strings.
     */
    fun tokenSimilarity(s1: String, s2: String): Double {
        val t1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
        val t2 = s2.split(" ").filter { it.isNotBlank() }.toSet()
        if (t1.isEmpty() || t2.isEmpty()) return 0.0
        val intersection = t1.intersect(t2).size
        val union = t1.union(t2).size
        return intersection.toDouble() / union.toDouble()
    }

    /**
     * Matches search candidates against target title, year, and media type with high confidence.
     * Returns null if no candidate reaches the confidence threshold (>= 0.80).
     * NEVER falls back to an arbitrary first result.
     */
    fun findConfidentMatch(
        candidates: List<SearchResponse>,
        targetTitles: List<String>,
        targetYear: Int? = null,
        isMovie: Boolean? = null,
        minConfidence: Double = 0.70
    ): SearchResponse? {
        if (candidates.isEmpty() || targetTitles.isEmpty()) return null

        val normTargets = targetTitles.map { ProviderModels.normalizeTitle(it) }
        var bestCandidate: SearchResponse? = null
        var highestScore = 0.0
        val topUrls = mutableSetOf<String>()

        for (item in candidates) {
            val normName = ProviderModels.normalizeTitle(item.name)
            var bestTitleScore = 0.0

            for (normTarget in normTargets) {
                var score = 0.0

                // 1. Title match
                if (normName == normTarget) {
                    score += 0.85
                } else if (hasDelimitedTitleAlias(item.name, normTarget)) {
                    score += 0.75
                } else if (normName.contains(normTarget)) {
                    val ratio = normTarget.length.toDouble() / max(normName.length, 1)
                    score += (0.65 * ratio).coerceIn(0.40, 0.75)
                } else {
                    val sim = tokenSimilarity(normTarget, normName)
                    score += (0.70 * sim)
                }
                if (score > bestTitleScore) bestTitleScore = score
            }
            var score = bestTitleScore

            // 2. Year validation
            val itemYear: Int? = when (item) {
                is MovieSearchResponse -> item.year
                is TvSeriesSearchResponse -> item.year
                is AnimeSearchResponse -> item.year
                else -> null
            }
            if (targetYear != null && itemYear != null) {
                val diff = abs(targetYear - itemYear)
                if (diff == 0) {
                    score += 0.20
                } else if (diff == 1) {
                    score += 0.05
                } else {
                    // Mismatched year penalty
                    score -= 0.35
                }
            } else if (score >= 0.80) {
                score += 0.05
            }

            // 3. Media type validation (prevent cross-matching movie with tv series)
            if (isMovie != null && item.type != TvType.Documentary) {
                val candidateIsMovie = when {
                    item.type == TvType.AnimeMovie -> true
                    item is MovieSearchResponse -> true
                    item is AnimeSearchResponse -> false
                    item is TvSeriesSearchResponse -> false
                    else -> item.type == TvType.Movie
                }
                val candidateIsSeries = when {
                    item.type == TvType.AnimeMovie -> false
                    item is TvSeriesSearchResponse -> true
                    item is AnimeSearchResponse -> true
                    item is MovieSearchResponse -> false
                    else -> item.type == TvType.TvSeries
                }
                if (isMovie && candidateIsSeries) {
                    score -= 0.50
                } else if (!isMovie && candidateIsMovie) {
                    score -= 0.50
                }
            }

            if (score > highestScore) {
                highestScore = score
                bestCandidate = item
                topUrls.clear()
                topUrls.add(item.url)
            } else if (kotlin.math.abs(score - highestScore) < 0.000001) {
                topUrls.add(item.url)
            }
        }

        return if (highestScore >= minConfidence && topUrls.size == 1) bestCandidate else null
    }

    private fun hasDelimitedTitleAlias(title: String, phrase: String): Boolean {
        if (phrase.isBlank() || (!phrase.contains(' ') && phrase.length < 6)) return false
        val segments = title.split(Regex("\\s[-–—|/]\\s"))
            .map(ProviderModels::normalizeTitle)
        return segments.any { it == phrase } && segments.none { it.startsWith("$phrase ") }
    }
}
