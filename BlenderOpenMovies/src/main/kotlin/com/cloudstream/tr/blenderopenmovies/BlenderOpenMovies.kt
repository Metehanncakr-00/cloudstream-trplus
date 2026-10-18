package com.cloudstream.tr.blenderopenmovies

import com.cloudstream.tr.core.network.DynamicDomainResolver
import com.cloudstream.tr.core.network.StreamValidator
import com.cloudstream.tr.core.network.ValidationStatus
import com.cloudstream.tr.core.resolvers.DebridConfig
import com.cloudstream.tr.core.streaming.BoundedLinkDelivery
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

class BlenderOpenMovies : MainAPI() {
    override var mainUrl = BlenderCatalog.BASE
    override var name = "BlenderOpenMovies"
    override var lang = "en" // Metadata/UI language; not an audio-language assertion.
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)
    override val mainPage = mainPageOf("open-movies" to "Blender Open Movies")
    internal var catalogOverride: BlenderCatalog? = null

    private suspend fun catalog(): BlenderCatalog {
        catalogOverride?.let { return it }
        // Runtime resolution uses the shared mechanism, but this official-source provider remains host bounded.
        mainUrl = DynamicDomainResolver.resolve(name, BlenderCatalog.BASE).takeIf { it == BlenderCatalog.BASE }
            ?: BlenderCatalog.BASE
        return BlenderCatalog { url -> app.get(url).text }
    }

    private fun BlenderCatalog.Film.toSearch(): SearchResponse =
        newMovieSearchResponse(title, BlenderCatalog.watchUrl(uuid), TvType.Movie) {
            posterUrl = poster
            year = this@toSearch.year
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val result = catalog().page(page)
        return newHomePageResponse(request.name, result.films.map { it.toSearch() }, result.hasNext)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList =
        newSearchResponseList(if (page == 1) catalog().search(query).map { it.toSearch() } else emptyList(), false)

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val film = catalog().detail(url)?.first ?: return null
        val stableUrl = BlenderCatalog.watchUrl(film.uuid)
        return newMovieLoadResponse(film.title, stableUrl, TvType.Movie, stableUrl) {
            posterUrl = film.poster
            year = film.year
            plot = film.description + "\n\n" + film.attribution
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return BoundedLinkDelivery.collectAndDeliver(
            discover = { emit ->
                // Refresh within the shared discovery budget; never cache transient media URLs.
                catalog().detail(data)?.let { (film, media) ->
                    val referer = BlenderCatalog.watchUrl(film.uuid)
                    media.forEach { file ->
                        emit(ExtractorLink(name, "$name ${file.quality}p", file.url, referer, file.quality,
                            type = ExtractorLinkType.VIDEO))
                    }
                }
            },
            callback = callback,
            preference = DebridConfig.audioPreference,
            probe = { link, headers ->
                val result = StreamValidator.validateStream(link.url, headers, link.source)
                // Native files only: reject a detected manifest/type mismatch, but retain
                // indeterminate probes as UNKNOWN fallback, not proof of working playback.
                if (result.status == ValidationStatus.VALID && result.streamType != ExtractorLinkType.VIDEO) result.copy(
                    status = ValidationStatus.INVALID, failureReason = "Expected a native video file"
                ) else result
            }
        ) > 0
    }
}
