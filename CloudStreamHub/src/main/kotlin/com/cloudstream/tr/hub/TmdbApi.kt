package com.cloudstream.tr.hub

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URLDecoder
import java.net.URLEncoder

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbPageResponse(
    @JsonProperty("page") val page: Int? = null,
    @JsonProperty("results") val results: List<TmdbItem>? = null,
    @JsonProperty("total_pages") val totalPages: Int? = null,
    @JsonProperty("total_results") val totalResults: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbItem(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("media_type") val mediaType: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("genre_ids") val genreIds: List<Int>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbGenre(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbCast(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("profile_path") val profilePath: String? = null,
    @JsonProperty("character") val character: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbCredits(
    @JsonProperty("cast") val cast: List<TmdbCast>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbVideo(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("site") val site: String? = null,
    @JsonProperty("type") val type: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbVideos(
    @JsonProperty("results") val results: List<TmdbVideo>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbSeason(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("episode_count") val episodeCount: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbExternalIds(
    @JsonProperty("imdb_id") val imdbId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TmdbDetailResponse(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("genres") val genres: List<TmdbGenre>? = null,
    @JsonProperty("credits") val credits: TmdbCredits? = null,
    @JsonProperty("videos") val videos: TmdbVideos? = null,
    @JsonProperty("seasons") val seasons: List<TmdbSeason>? = null,
    @JsonProperty("external_ids") val externalIds: TmdbExternalIds? = null
)

/**
 * Deterministic, reflection-free AST parser for TMDB responses.
 * Bypasses Kotlin metadata reflection (which can fail when D8 rewrites Kotlin 2.3 metadata in .cs3 dex files).
 */
object TmdbJsonParser {
    private val treeMapper = ObjectMapper()

    private fun JsonNode?.optString(field: String): String? {
        val node = this?.get(field) ?: return null
        if (node.isNull || node.isMissingNode) return null
        return node.asText(null)?.takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun JsonNode?.optInt(field: String): Int? {
        val node = this?.get(field) ?: return null
        if (node.isNull || node.isMissingNode || !node.isNumber) return null
        return node.asInt()
    }

    private fun JsonNode?.optDouble(field: String): Double? {
        val node = this?.get(field) ?: return null
        if (node.isNull || node.isMissingNode || !node.isNumber) return null
        return node.asDouble()
    }

    fun parsePage(rawJson: String?): TmdbPageResponse? {
        if (rawJson.isNullOrBlank()) return null
        return try {
            val root = treeMapper.readTree(rawJson) ?: return null
            if (!root.isObject) return null
            val resultsNode = root.get("results")
            val items = if (resultsNode != null && resultsNode.isArray) {
                resultsNode.mapNotNull { itemNode ->
                    val id = itemNode.optInt("id") ?: return@mapNotNull null
                    val genreIdsNode = itemNode.get("genre_ids")
                    val genreIds = if (genreIdsNode != null && genreIdsNode.isArray) {
                        genreIdsNode.mapNotNull { g -> if (g.isNumber) g.asInt() else null }
                    } else null
                    TmdbItem(
                        id = id,
                        title = itemNode.optString("title"),
                        name = itemNode.optString("name"),
                        originalTitle = itemNode.optString("original_title"),
                        originalName = itemNode.optString("original_name"),
                        overview = itemNode.optString("overview"),
                        posterPath = itemNode.optString("poster_path"),
                        backdropPath = itemNode.optString("backdrop_path"),
                        mediaType = itemNode.optString("media_type"),
                        releaseDate = itemNode.optString("release_date"),
                        firstAirDate = itemNode.optString("first_air_date"),
                        voteAverage = itemNode.optDouble("vote_average"),
                        genreIds = genreIds
                    )
                }
            } else null

            if (items == null && root.optInt("page") == null) return null
            TmdbPageResponse(
                page = root.optInt("page") ?: 1,
                results = items ?: emptyList(),
                totalPages = root.optInt("total_pages") ?: 1,
                totalResults = root.optInt("total_results") ?: (items?.size ?: 0)
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun parseDetail(rawJson: String?): TmdbDetailResponse? {
        if (rawJson.isNullOrBlank()) return null
        return try {
            val root = treeMapper.readTree(rawJson) ?: return null
            val id = root.optInt("id") ?: return null
            val genres = root.get("genres")?.takeIf { it.isArray }?.map { g ->
                TmdbGenre(id = g.optInt("id"), name = g.optString("name"))
            }
            val cast = root.get("credits")?.get("cast")?.takeIf { it.isArray }?.map { c ->
                TmdbCast(
                    id = c.optInt("id"),
                    name = c.optString("name"),
                    profilePath = c.optString("profile_path"),
                    character = c.optString("character")
                )
            }
            val videos = root.get("videos")?.get("results")?.takeIf { it.isArray }?.map { v ->
                TmdbVideo(
                    id = v.optString("id"),
                    key = v.optString("key"),
                    site = v.optString("site"),
                    type = v.optString("type")
                )
            }
            val seasons = root.get("seasons")?.takeIf { it.isArray }?.map { s ->
                TmdbSeason(
                    id = s.optInt("id"),
                    seasonNumber = s.optInt("season_number"),
                    episodeCount = s.optInt("episode_count"),
                    name = s.optString("name"),
                    posterPath = s.optString("poster_path")
                )
            }
            val imdbId = root.get("external_ids")?.optString("imdb_id") ?: root.optString("imdb_id")

            TmdbDetailResponse(
                id = id,
                title = root.optString("title"),
                name = root.optString("name"),
                originalTitle = root.optString("original_title"),
                originalName = root.optString("original_name"),
                overview = root.optString("overview"),
                posterPath = root.optString("poster_path"),
                backdropPath = root.optString("backdrop_path"),
                releaseDate = root.optString("release_date"),
                firstAirDate = root.optString("first_air_date"),
                voteAverage = root.optDouble("vote_average"),
                genres = genres,
                credits = if (cast != null) TmdbCredits(cast) else null,
                videos = if (videos != null) TmdbVideos(videos) else null,
                seasons = seasons,
                externalIds = if (imdbId != null) TmdbExternalIds(imdbId) else null
            )
        } catch (_: Throwable) {
            null
        }
    }
}


data class AggregatorLinkPayload(
    val title: String,
    val year: Int?,
    val isMovie: Boolean,
    val season: Int? = null,
    val episode: Int? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val originalTitle: String? = null,
    val category: String? = null,
    val canonicalId: String? = null,
    val providerUrls: Map<String, String> = emptyMap()
) {
    fun toUrlData(): String {
        val encTitle = URLEncoder.encode(title, "UTF-8")
        val y = year?.toString() ?: ""
        val s = season?.toString() ?: ""
        val ep = episode?.toString() ?: ""
        val tId = tmdbId?.toString() ?: ""
        val iId = imdbId ?: ""
        val oTitle = originalTitle?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
        val cId = canonicalId?.let { "&canonicalId=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        val pUrls = if (providerUrls.isNotEmpty()) {
            val serialized = providerUrls.entries.joinToString(";") { (prov, url) ->
                "${URLEncoder.encode(prov, "UTF-8")}:${URLEncoder.encode(url, "UTF-8")}"
            }
            "&providers=" + URLEncoder.encode(serialized, "UTF-8")
        } else ""

        return "hub://item?title=$encTitle&year=$y&isMovie=$isMovie&season=$s&episode=$ep&tmdbId=$tId&imdbId=$iId&originalTitle=$oTitle" +
            (category?.let { "&category=$it" } ?: "") + cId + pUrls
    }

    companion object {
        fun fromUrlData(data: String): AggregatorLinkPayload? {
            return try {
                val query = data.substringAfter("?", "")
                if (query.isBlank()) return null
                val params = query.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    parts[0] to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
                }
                val providersMap = mutableMapOf<String, String>()
                params["providers"]?.takeIf { it.isNotBlank() }?.let { raw ->
                    raw.split(";").forEach { entry ->
                        val pair = entry.split(":", limit = 2)
                        if (pair.size == 2) {
                            val prov = URLDecoder.decode(pair[0], "UTF-8")
                            val u = URLDecoder.decode(pair[1], "UTF-8")
                            if (prov.isNotBlank() && u.isNotBlank()) {
                                providersMap[prov] = u
                            }
                        }
                    }
                }
                AggregatorLinkPayload(
                    title = params["title"] ?: return null,
                    year = params["year"]?.toIntOrNull(),
                    isMovie = params["isMovie"]?.toBoolean() ?: true,
                    season = params["season"]?.toIntOrNull(),
                    episode = params["episode"]?.toIntOrNull(),
                    tmdbId = params["tmdbId"]?.toIntOrNull(),
                    imdbId = params["imdbId"]?.takeIf { it.isNotBlank() },
                    originalTitle = params["originalTitle"]?.takeIf { it.isNotBlank() },
                    category = params["category"]?.takeIf { it in setOf("documentary", "animation", "general") },
                    canonicalId = params["canonicalId"]?.takeIf { it.isNotBlank() },
                    providerUrls = providersMap
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

