package com.cloudstream.tr.one

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** No CloudStreamHub imports; this provider is independently registered by TRPlusOnePlugin. */
class TRPlusOne : MainAPI() {
    override var name = "TR+ ONE"
    override var mainUrl = OneRouteCodec.BASE_URL // "https://trplus.one.invalid", never requested on network
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AnimeMovie)
    override val mainPage = mainPageOf(
        "one://movies" to "Filmler",
        "one://series" to "Diziler",
        "one://anime" to "Anime",
        "one://recent" to "Son Eklenenler"
    )

    private val background = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val refreshedAt = ConcurrentHashMap<String, Long>()
    private fun lock(key: String) = locks.getOrPut(key) { Mutex() }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val category = request.data.removePrefix("one://")
        if (category !in setOf("movies", "series", "anime", "recent")) {
            return newHomePageResponse(request.name, emptyList<SearchResponse>(), hasNext = false)
        }
        val started = System.nanoTime()
        val catalogKey = "$category:$page"
        val existing = OneIndex.page(category, page)
        if (existing.isNotEmpty()) {
            if (page == 1 && (System.currentTimeMillis() - (refreshedAt[catalogKey] ?: 0L)) > 600_000L) {
                refreshedAt[catalogKey] = System.currentTimeMillis()
                background.launch { fetchAndStore(category, page, forceRefresh = true) }
            }
            OneDiagnostics.record("home", "$catalogKey CACHE_HIT items=${existing.size}")
            return newHomePageResponse(request.name, existing.map(::card), hasNext = existing.size >= 15)
        }
        val results = withTimeoutOrNull(8500L) { fetchAndStore(category, page) }.orEmpty()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        OneDiagnostics.record("home", "$catalogKey RETURNED items=${results.size} ms=$elapsedMs")
        return newHomePageResponse(request.name, results.map(::card), hasNext = results.isNotEmpty())
    }

    private suspend fun fetchAndStore(category: String, page: Int, forceRefresh: Boolean = false): List<CanonicalMedia> {
        val key = "$category:$page"
        return lock(key).withLock {
            val already = OneIndex.page(category, page)
            if (!forceRefresh && already.isNotEmpty()) return@withLock already
            val listings = withTimeoutOrNull(7500L) { OneProviders.fetch(category, page) }.orEmpty()
            withContext(Dispatchers.IO) {
                OneIndex.ingest(category, page, listings)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (query.isBlank()) return newSearchResponseList(emptyList(), hasNext = false)
        val local = OneIndex.searchLocal(query)
        if (local.isNotEmpty() && page == 1) {
            background.launch {
                val additional = withTimeoutOrNull(8500L) { OneProviders.search(query) }.orEmpty()
                OneIndex.saveSearch(additional)
            }
            OneDiagnostics.record("search", "LOCAL items=${local.size}")
            return newSearchResponseList(local.map(::card), hasNext = false)
        }
        val fetched = withTimeoutOrNull(8500L) { OneProviders.search(query) }.orEmpty()
        val merged = withContext(Dispatchers.IO) {
            OneIndex.saveSearch(fetched)
            OneIndex.searchLocal(query)
        }
        OneDiagnostics.record("search", "REMOTE items=${merged.size}")
        return newSearchResponseList(if (page == 1) merged.map(::card) else emptyList(), hasNext = false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    internal fun card(media: CanonicalMedia): SearchResponse {
        val url = OneRouteCodec.mediaUrl(media.id)
        return when (media.kind) {
            OneKind.MOVIE -> newMovieSearchResponse(media.name, url, TvType.Movie) {
                this.posterUrl = media.posterUrl
                this.year = media.year
            }
            OneKind.ANIME_MOVIE -> newAnimeSearchResponse(media.name, url, TvType.AnimeMovie) {
                this.posterUrl = media.posterUrl
                this.year = media.year
            }
            OneKind.ANIME_SERIES -> newAnimeSearchResponse(media.name, url, TvType.Anime) {
                this.posterUrl = media.posterUrl
                this.year = media.year
            }
            OneKind.SERIES -> newTvSeriesSearchResponse(media.name, url, TvType.TvSeries) {
                this.posterUrl = media.posterUrl
                this.year = media.year
            }
        }
    }

    private suspend fun providerDetails(media: CanonicalMedia): List<Pair<ProviderListing, LoadResponse>> = coroutineScope {
        media.listings.map { listing ->
            async(Dispatchers.IO) {
                val api = OneProviders.byId(listing.providerId) ?: return@async null
                if (!OneSettings.isEnabled(listing.providerId)) return@async null
                val startNs = System.nanoTime()
                OneDiagnostics.log("PROVIDER_LOAD_START", "provider=${listing.providerId} url=${listing.url}")
                try {
                    val load = withTimeoutOrNull(4500L) { api.load(listing.url) }
                    val durationMs = (System.nanoTime() - startNs) / 1_000_000
                    if (load != null) {
                        OneDiagnostics.log("PROVIDER_LOAD_SUCCESS", "provider=${listing.providerId} type=${load.javaClass.simpleName} ms=$durationMs")
                        listing to load
                    } else {
                        OneDiagnostics.log("PROVIDER_LOAD_FAILED", "provider=${listing.providerId} reason=TIMEOUT_OR_NULL ms=$durationMs")
                        null
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    val durationMs = (System.nanoTime() - startNs) / 1_000_000
                    OneDiagnostics.log("PROVIDER_LOAD_FAILED", "provider=${listing.providerId} reason=${e.javaClass.simpleName} ms=$durationMs")
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun load(url: String): LoadResponse? {
        val startNs = System.nanoTime()
        OneDiagnostics.log("LOAD_START", "url=$url")

        val route = OneRouteCodec.parse(url)
        val routeDesc = when (route) {
            is OneRoute.Media -> "Media(id=${route.id})"
            is OneRoute.Movie -> "Movie(id=${route.id})"
            is OneRoute.Episode -> "Episode(id=${route.id}, s=${route.season}, e=${route.episode})"
            null -> "INVALID"
        }
        OneDiagnostics.log("ROUTE_PARSED", "route=$routeDesc raw=$url")

        val mediaId = (route as? OneRoute.Media)?.id
        if (mediaId == null) {
            OneDiagnostics.log("LOAD_FAILED", "REASON=INVALID_ROUTE url=$url")
            return null
        }

        val media = OneIndex.media(mediaId)
        if (media == null) {
            OneDiagnostics.log("MEDIA_NOT_FOUND", "id=$mediaId")
            return null
        }
        OneDiagnostics.log("MEDIA_FOUND", "id=${media.id} name=${media.name} kind=${media.kind} listings=${media.listings.size}")

        val detailed = withTimeoutOrNull(8500L) { providerDetails(media) }.orEmpty()
        if (detailed.isEmpty()) {
            OneDiagnostics.log("LOAD_FAILED", "REASON=NO_PROVIDER_DETAIL id=$mediaId")
            return null
        }

        val base = detailed.first().second
        val response = if (media.kind == OneKind.MOVIE) {
            val m = detailed.firstNotNullOfOrNull { it.second as? MovieLoadResponse }
                ?: return null
            val moviePlayUrl = OneRouteCodec.movieUrl(media.id)
            newMovieLoadResponse(media.name, url, m.type, moviePlayUrl) {
                this.posterUrl = m.posterUrl ?: media.posterUrl
                this.plot = m.plot
                this.year = media.year ?: m.year
                this.tags = m.tags
                this.score = m.score
            }
        } else {
            val episodes = linkedMapOf<String, Episode>()
            detailed.forEach { (_, data) ->
                if (data is TvSeriesLoadResponse) data.episodes.forEach { ep ->
                    val s = ep.season
                    val e = ep.episode
                    // Unknown-number episodes cannot safely route between providers.
                    if (s == null || e == null) {
                        OneDiagnostics.record("episodes", "UNKNOWN_NUMBER_SKIPPED id=$mediaId")
                        return@forEach
                    }
                    val key = "$s:$e"
                    if (key !in episodes) {
                        val epPlayUrl = OneRouteCodec.episodeUrl(media.id, s, e)
                        episodes[key] = newEpisode(epPlayUrl) {
                            this.name = ep.name
                            this.season = s
                            this.episode = e
                            this.posterUrl = ep.posterUrl
                            this.description = ep.description
                        }
                    }
                }
            }
            if (episodes.isEmpty()) {
                OneDiagnostics.log("LOAD_FAILED", "REASON=NO_EPISODES id=$mediaId")
                return null
            }
            newTvSeriesLoadResponse(
                media.name,
                url,
                if (media.kind == OneKind.ANIME_SERIES || media.kind == OneKind.ANIME_MOVIE) TvType.Anime else TvType.TvSeries,
                episodes.values.toList()
            ) {
                this.posterUrl = base.posterUrl ?: media.posterUrl
                this.plot = base.plot
                this.year = media.year ?: base.year
                this.tags = base.tags
                this.score = base.score
            }
        }

        val durationMs = (System.nanoTime() - startNs) / 1_000_000
        val epCount = (response as? TvSeriesLoadResponse)?.episodes?.size ?: 1
        OneDiagnostics.log("LOAD_RESPONSE_READY", "id=${media.id} type=${response.type} episodes=$epCount durationMs=$durationMs")
        return response
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val startNs = System.nanoTime()
        OneDiagnostics.log("LOAD_LINKS_START", "data=$data")

        val route = OneRouteCodec.parse(data)
        if (route == null || (route !is OneRoute.Movie && route !is OneRoute.Episode)) {
            val totalMs = (System.nanoTime() - startNs) / 1_000_000
            OneDiagnostics.log("LOAD_LINKS_RESULT", "yielded=0 links REASON=INVALID_ROUTE data=$data totalMs=$totalMs")
            return false
        }

        val mediaId = when (route) {
            is OneRoute.Movie -> route.id
            is OneRoute.Episode -> route.id
        }

        val media = OneIndex.media(mediaId)
        if (media == null) {
            val totalMs = (System.nanoTime() - startNs) / 1_000_000
            OneDiagnostics.log("LOAD_LINKS_RESULT", "yielded=0 links REASON=MEDIA_NOT_FOUND id=$mediaId totalMs=$totalMs")
            return false
        }

        val isEpisode = route is OneRoute.Episode
        val season = (route as? OneRoute.Episode)?.season
        val number = (route as? OneRoute.Episode)?.episode

        var count = 0
        // Preserve every provider choice; a broken provider must not hide working peers.
        for (listing in media.listings) {
            val api = OneProviders.byId(listing.providerId) ?: continue
            if (!OneSettings.isEnabled(listing.providerId)) continue
            try {
                val pStartNs = System.nanoTime()
                val worked = withTimeoutOrNull(6500L) {
                    val load = api.load(listing.url) ?: return@withTimeoutOrNull false
                    val targetData = if (!isEpisode) {
                        (load as? MovieLoadResponse)?.dataUrl
                    } else {
                        if (season == null || number == null || season < 0 || number < 0) null
                        else (load as? TvSeriesLoadResponse)?.episodes
                            ?.firstOrNull { it.season == season && it.episode == number }?.data
                    } ?: return@withTimeoutOrNull false

                    api.loadLinks(targetData, isCasting, subtitleCallback) { link ->
                        @Suppress("DEPRECATION")
                        val wrapped = ExtractorLink(
                            source = "[${listing.providerId}] ${link.source}",
                            name = "[${listing.providerId}] ${link.name}",
                            url = link.url,
                            referer = link.referer,
                            quality = link.quality,
                            type = link.type,
                            headers = link.headers,
                            extractorData = link.extractorData
                        )
                        callback(wrapped)
                        count++
                    }
                }
                val pMs = (System.nanoTime() - pStartNs) / 1_000_000
                OneDiagnostics.record(listing.providerId, "play route=${route.javaClass.simpleName} yielded=$worked links=$count ms=$pMs")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                OneDiagnostics.record(listing.providerId, "play ERROR:${e.javaClass.simpleName}")
            }
        }

        val totalMs = (System.nanoTime() - startNs) / 1_000_000
        OneDiagnostics.log("LOAD_LINKS_RESULT", "yielded=$count links totalMs=$totalMs")
        return count > 0
    }
}
