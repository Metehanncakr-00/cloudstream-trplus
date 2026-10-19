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
    override var mainUrl = "https://trplus.one.invalid" // stable local routing identity, never requested
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
        OneDiagnostics.record("home", "$catalogKey RETURNED items=${results.size} ms=${(System.nanoTime()-started)/1_000_000}")
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

    private fun card(media: CanonicalMedia): SearchResponse {
        val url = "one://media/${media.id}"
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
                try {
                    val load = withTimeoutOrNull(4500L) { api.load(listing.url) } ?: return@async null
                    listing to load
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    OneDiagnostics.record(listing.providerId, "load ERROR:${e.javaClass.simpleName}")
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.removePrefix("one://media/")
        val media = OneIndex.media(id) ?: return null
        val detailed = withTimeoutOrNull(8500L) { providerDetails(media) }.orEmpty()
        if (detailed.isEmpty()) {
            OneDiagnostics.record("details", "NO_PROVIDER_DETAIL id=$id")
            return null
        }
        val base = detailed.first().second
        OneDiagnostics.record("details", "id=$id listingCount=${media.listings.size} loaded=${detailed.size}")
        return if (media.kind == OneKind.MOVIE) {
            val m = detailed.firstNotNullOfOrNull { it.second as? MovieLoadResponse }
                ?: return null
            newMovieLoadResponse(media.name, url, m.type, "one://movie/$id") {
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
                        OneDiagnostics.record("episodes", "UNKNOWN_NUMBER_SKIPPED id=$id")
                        return@forEach
                    }
                    val key = "$s:$e"
                    if (key !in episodes) {
                        val route = "one://episode/$id/$s/$e"
                        episodes[key] = newEpisode(route) {
                            this.name = ep.name
                            this.season = s
                            this.episode = e
                            this.posterUrl = ep.posterUrl
                            this.description = ep.description
                        }
                    }
                }
            }
            if (episodes.isEmpty()) return null
            newTvSeriesLoadResponse(media.name, url, if (media.kind == OneKind.ANIME_SERIES || media.kind == OneKind.ANIME_MOVIE) TvType.Anime else TvType.TvSeries, episodes.values.toList()) {
                this.posterUrl = base.posterUrl ?: media.posterUrl
                this.plot = base.plot
                this.year = media.year ?: base.year
                this.tags = base.tags
                this.score = base.score
            }
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (!data.startsWith("one://movie/") && !data.startsWith("one://episode/")) return false
        val bits = data.removePrefix("one://").split("/")
        if (bits.size < 2) return false
        val media = OneIndex.media(bits[1]) ?: return false
        val episode = bits[0] == "episode"
        val season = bits.getOrNull(2)?.toIntOrNull()
        val number = bits.getOrNull(3)?.toIntOrNull()
        var count = 0
        // Preserve every provider choice; a broken provider must not hide working peers.
        for (listing in media.listings) {
            val api = OneProviders.byId(listing.providerId) ?: continue
            if (!OneSettings.isEnabled(listing.providerId)) continue
            try {
                val worked = withTimeoutOrNull(6500L) {
                    val load = api.load(listing.url) ?: return@withTimeoutOrNull false
                    val targetData = if (!episode) {
                        (load as? MovieLoadResponse)?.dataUrl
                    } else {
                        if (season == null || number == null || season < 0 || number < 0) null
                        else (load as? TvSeriesLoadResponse)?.episodes
                            ?.firstOrNull { it.season == season && it.episode == number }?.data
                    } ?: return@withTimeoutOrNull false
                    api.loadLinks(targetData, isCasting, subtitleCallback) { link ->
                        val wrapped = ExtractorLink(
                            source = "${listing.providerId} • ${link.source}",
                            name = "${listing.providerId} • ${link.name}",
                            url = link.url, referer = link.referer, quality = link.quality,
                            type = link.type, headers = link.headers, extractorData = link.extractorData
                        )
                        callback(wrapped)
                        count++
                    }
                }
                OneDiagnostics.record(listing.providerId, "play kind=${bits[0]} yielded=$worked links=$count")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                OneDiagnostics.record(listing.providerId, "play ERROR:${e.javaClass.simpleName}")
            }
        }
        return count > 0
    }
}
