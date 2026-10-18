package com.cloudstream.tr.yts

import com.cloudstream.tr.core.resolvers.YtsMovie
import com.cloudstream.tr.core.resolvers.YtsResolver
import com.cloudstream.tr.core.resolvers.YtsResponse
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.net.URLEncoder

data class YtsDetailResponse(
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("data") val data: YtsDetailData? = null
)

data class YtsDetailData(
    @JsonProperty("movie") val movie: YtsMovie? = null
)

data class YtsStoredData(
    @JsonProperty("imdb") val imdb: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("torrents") val torrents: List<com.cloudstream.tr.core.resolvers.YtsTorrent>? = null
)

class YTS : MainAPI() {
    override var mainUrl = "https://yts.gg"
    override var name = "YTS"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Torrent, TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/api/v2/list_movies.json?sort_by=download_count" to "En Çok İndirilenler",
        "${mainUrl}/api/v2/list_movies.json?sort_by=like_count" to "En Popüler Filmler",
        "${mainUrl}/api/v2/list_movies.json?sort_by=year" to "En Yeni Filmler",
        "${mainUrl}/api/v2/list_movies.json?quality=2160p" to "4K Ultra HD Filmler"
    )

    internal fun parseMovie(m: YtsMovie): SearchResponse? {
        val title = m.title ?: return null
        val id = m.id ?: return null
        val poster = m.mediumCoverImage ?: m.largeCoverImage
        val year = m.year
        val rating = m.rating?.toString()
        val dataUrl = "${mainUrl}/api/v2/movie_details.json?movie_id=${id}&with_images=true"

        return newMovieSearchResponse(title, dataUrl, TvType.Torrent) {
            this.posterUrl = poster
            this.year = year
            this.score = Score.from10(rating)
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = "${request.data}&page=${page}&limit=20"
        val resp = app.get(targetUrl).parsedSafe<YtsResponse>()
        val movies = resp?.data?.movies ?: emptyList()
        val items = movies.mapNotNull { parseMovie(it) }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val targetUrl = "${mainUrl}/api/v2/list_movies.json?query_term=${URLEncoder.encode(query, "UTF-8")}&limit=20"
        val resp = app.get(targetUrl).parsedSafe<YtsResponse>()
        val movies = resp?.data?.movies ?: emptyList()
        return movies.mapNotNull { parseMovie(it) }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val resp = app.get(url).parsedSafe<YtsDetailResponse>() ?: return null
        val movie = resp.data?.movie ?: return null
        val title = movie.title ?: return null
        val poster = movie.largeCoverImage ?: movie.mediumCoverImage
        val backdrop = movie.backgroundImage
        val imdbCode = movie.imdbCode

        val trailerUrl = movie.ytTrailerCode?.let { "https://www.youtube.com/watch?v=$it" }

        // Store direct torrents or imdbCode as payload for loadLinks
        val loadData = if (!movie.torrents.isNullOrEmpty()) {
            try {
                com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(
                    YtsStoredData(
                        imdb = imdbCode,
                        title = title,
                        torrents = movie.torrents
                    )
                )
            } catch (_: Exception) {
                imdbCode ?: movie.id?.toString() ?: ""
            }
        } else {
            imdbCode ?: movie.id?.toString() ?: ""
        }

        return newMovieLoadResponse(title, url, TvType.Torrent, loadData) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backdrop
            this.plot = movie.descriptionFull
            this.year = movie.year
            this.tags = movie.genres ?: emptyList()
            this.score = Score.from10(movie.rating?.toString())
            addTrailer(trailerUrl)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var linksFound = false
        if (data.isNotBlank()) {
            if (data.trimStart().startsWith("{")) {
                try {
                    val stored = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                        .readValue(data, YtsStoredData::class.java)
                    val directLinks = YtsResolver.mapTorrents(stored.torrents, stored.title ?: "Movie")
                    for (link in directLinks) {
                        callback(link)
                        linksFound = true
                    }
                    if (linksFound) return true
                    if (!stored.imdb.isNullOrBlank()) {
                        val fallbackLinks = YtsResolver.resolve(imdbId = stored.imdb, isMovie = true)
                        for (link in fallbackLinks) {
                            callback(link)
                            linksFound = true
                        }
                    }
                } catch (_: Exception) {
                    // Fall back to resolving as plain string
                }
            }
            if (!linksFound) {
                val links = YtsResolver.resolve(imdbId = data, isMovie = true)
                for (link in links) {
                    callback(link)
                    linksFound = true
                }
            }
        }
        return linksFound
    }
}

