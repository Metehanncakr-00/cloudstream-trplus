package com.cloudstream.tr.animecix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.cloudstream.tr.core.model.ProviderModels

class AnimeciX : MainAPI() {
    override var mainUrl = "https://animecix.tv"
    override var name = "AnimeciX"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 200L

    private val apiHeaders = mapOf(
        "x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk",
        "Referer" to "${mainUrl}/"
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/secure/titles?type=series&onlyStreamable=true" to "Seriler",
        "${mainUrl}/secure/titles?type=movie&onlyStreamable=true" to "Filmler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(
            "${request.data}&page=${page}&perPage=16",
            headers = apiHeaders
        ).parsedSafe<Category>()

        val home = response?.pagination?.data?.mapNotNull { anime ->
            val title = anime.name ?: anime.title ?: return@mapNotNull null
            newAnimeSearchResponse(
                title,
                "${mainUrl}/secure/titles/${anime.id}?titleId=${anime.id}",
                TvType.Anime
            ) {
                this.posterUrl = fixUrlNull(anime.poster)
            }
        } ?: emptyList()

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val response = app.get(
            "${mainUrl}/secure/search/${query}?limit=20",
            headers = apiHeaders
        ).parsedSafe<Search>()

        val items = response?.results?.mapNotNull { anime ->
            val title = anime.name ?: anime.title ?: return@mapNotNull null
            newAnimeSearchResponse(
                title,
                "${mainUrl}/secure/titles/${anime.id}?titleId=${anime.id}",
                TvType.Anime
            ) {
                this.posterUrl = fixUrlNull(anime.poster)
            }
        } ?: emptyList()

        val deduped = ProviderModels.dedupSearchResults(items)
        return newSearchResponseList(deduped, hasNext = false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(
            url,
            headers = apiHeaders
        ).parsedSafe<Title>() ?: return null

        val anime = response.title ?: return null
        val titleId = url.substringAfter("?titleId=").substringBefore("&")
        val episodes = mutableListOf<Episode>()

        if (anime.seasons.isNotEmpty()) {
            for (sezon in anime.seasons) {
                val seasonNum = sezon.number
                val sezonResponse = app.get(
                    "${mainUrl}/secure/related-videos?episode=1&season=${seasonNum}&videoId=0&titleId=${titleId}",
                    headers = apiHeaders
                ).parsedSafe<TitleVideos>()

                sezonResponse?.videos?.forEach { video ->
                    val epNum = video.episodeNum ?: 1
                    val sNum = video.seasonNum ?: seasonNum
                    episodes.add(
                        newEpisode(video.url) {
                            this.name = "${sNum}. Sezon ${epNum}. Bölüm"
                            this.season = sNum
                            this.episode = epNum
                        }
                    )
                }
            }
        } else if (anime.videos.isNotEmpty()) {
            episodes.add(
                newEpisode(anime.videos.first().url) {
                    this.name = "Filmi İzle"
                    this.season = 1
                    this.episode = 1
                }
            )
        }

        val animeName = anime.name ?: anime.title ?: "Anime"
        val actors = anime.actors.mapNotNull { it.name?.let { name -> Actor(name, fixUrlNull(it.poster)) } }
        val tags = anime.tags.mapNotNull { it.name }

        return newTvSeriesLoadResponse(
            animeName,
            url,
            TvType.Anime,
            episodes
        ) {
            this.posterUrl = fixUrlNull(anime.poster)
            this.plot = anime.description
            this.year = anime.year
            this.score = Score.from10(anime.rating)
            this.tags = tags
            addActors(actors)
            addTrailer(anime.trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return loadExtractor(data, "${mainUrl}/", subtitleCallback, callback)
    }
}
