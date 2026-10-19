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
        val startNs = System.nanoTime()

        // 1. Controlled, time-bounded search across all enabled providers
        val fetched = withTimeoutOrNull(4000L) {
            OneProviders.search(query)
        }.orEmpty()

        // 2. Persist newly fetched listings into index (merging onto canonical cards)
        val results = withContext(Dispatchers.IO) {
            if (fetched.isNotEmpty()) {
                OneIndex.saveSearch(fetched)
            }
            OneIndex.searchLocal(query)
        }

        val durationMs = (System.nanoTime() - startNs) / 1_000_000
        OneDiagnostics.log(
            "SEARCH_PROVIDER_RESULTS",
            "query='$query' remote=${fetched.size} local=${results.size} durationMs=$durationMs"
        )
        return newSearchResponseList(if (page == 1) results.map(::card) else emptyList(), hasNext = false)
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
                val adapter = OneProviders.byId(listing.providerId) ?: return@async null
                if (!OneSettings.isEnabled(listing.providerId)) return@async null
                val startNs = System.nanoTime()
                OneDiagnostics.log("PROVIDER_LOAD_START", "provider=${listing.providerId} url=${listing.url}")
                try {
                    val load = withTimeoutOrNull(4500L) { adapter.loadDetail(listing.url) }
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

        val initialMedia = OneIndex.media(mediaId)
        if (initialMedia == null) {
            OneDiagnostics.log("MEDIA_NOT_FOUND", "id=$mediaId")
            return null
        }
        OneDiagnostics.log("MEDIA_FOUND", "id=${initialMedia.id} name=${initialMedia.name} kind=${initialMedia.kind} listings=${initialMedia.listings.size}")

        // Multi-source enrichment: if this card only has a single provider, check other enabled providers on-demand
        val searchTitle = initialMedia.name
        val presentProviderIds = initialMedia.listings.map { it.providerId }.toSet()
        val missingAdapters = OneProviders.enabledAdapters().filter { adapter ->
            adapter.providerId !in presentProviderIds && adapter.supportedTypes.contains(initialMedia.kind)
        }

        var activeMedia = initialMedia
        if (missingAdapters.isNotEmpty() && initialMedia.listings.size < 4) {
            val supplemental = withTimeoutOrNull(3000L) {
                coroutineScope {
                    missingAdapters.map { adapter ->
                        async(Dispatchers.IO) {
                            try {
                                adapter.search(searchTitle)
                            } catch (_: Throwable) {
                                emptyList()
                            }
                        }
                    }.awaitAll().flatten()
                }
            }.orEmpty()

            val current = initialMedia
            val verified = supplemental.filter { listing ->
                CanonicalEngine.isMatch(current, listing)
            }

            if (verified.isNotEmpty()) {
                val dedup = (current.listings + verified).distinctBy { it.providerId + "\u0000" + it.url }
                activeMedia = current.copy(
                    year = current.year ?: verified.firstNotNullOfOrNull { it.year },
                    posterUrl = current.posterUrl ?: verified.firstNotNullOfOrNull { it.posterUrl },
                    listings = dedup
                )
                OneIndex.updateMedia(activeMedia)
                OneDiagnostics.log(
                    "CANONICAL_MATCH",
                    "on-demand attached ${verified.map { it.providerId }} to '${activeMedia.name}'"
                )
            }
        }

        OneDiagnostics.log(
            "MEDIA_PROVIDER_LIST",
            "id=${activeMedia.id} providers=${activeMedia.listings.map { it.providerId }}"
        )

        val detailed = withTimeoutOrNull(8500L) { providerDetails(activeMedia) }.orEmpty()
        if (detailed.isEmpty()) {
            OneDiagnostics.log("LOAD_FAILED", "REASON=NO_PROVIDER_DETAIL id=$mediaId")
            return null
        }

        val base = detailed.first().second
        val response = if (activeMedia.kind == OneKind.MOVIE) {
            val m = detailed.firstNotNullOfOrNull { it.second as? MovieLoadResponse }
                ?: return null
            val moviePlayUrl = OneRouteCodec.movieUrl(activeMedia.id)
            newMovieLoadResponse(activeMedia.name, url, m.type, moviePlayUrl) {
                this.posterUrl = m.posterUrl ?: activeMedia.posterUrl
                this.plot = m.plot
                this.year = activeMedia.year ?: m.year
                this.tags = m.tags
                this.score = m.score
            }
        } else {
            val episodes = linkedMapOf<String, Episode>()
            detailed.forEach { (listing, data) ->
                if (data is TvSeriesLoadResponse) data.episodes.forEach { ep ->
                    val s = ep.season ?: 1
                    val e = ep.episode ?: 1
                    if (ep.data.isNotBlank()) {
                        OneEpisodeRegistry.register(activeMedia.id, s, e, listing.providerId, ep.data)
                        OneDiagnostics.log("EPISODE_MATCH", "id=${activeMedia.id} s=$s e=$e provider=${listing.providerId} data=${ep.data}")
                    }
                    val key = "$s:$e"
                    if (key !in episodes) {
                        val epPlayUrl = OneRouteCodec.episodeUrl(activeMedia.id, s, e)
                        episodes[key] = newEpisode(epPlayUrl) {
                            this.name = ep.name ?: "${s}. Sezon ${e}. Bölüm"
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
            OneDiagnostics.log("EPISODE_DATA_FOUND", "id=${activeMedia.id} totalEpisodes=${episodes.size}")
            background.launch { OneEpisodeRegistry.persist() }
            newTvSeriesLoadResponse(
                activeMedia.name,
                url,
                if (activeMedia.kind == OneKind.ANIME_SERIES || activeMedia.kind == OneKind.ANIME_MOVIE) TvType.Anime else TvType.TvSeries,
                episodes.values.toList()
            ) {
                this.posterUrl = base.posterUrl ?: activeMedia.posterUrl
                this.plot = base.plot
                this.year = activeMedia.year ?: base.year
                this.tags = base.tags
                this.score = base.score
            }
        }

        val durationMs = (System.nanoTime() - startNs) / 1_000_000
        val epCount = (response as? TvSeriesLoadResponse)?.episodes?.size ?: 1
        OneDiagnostics.log("LOAD_RESPONSE_READY", "id=${activeMedia.id} type=${response.type} episodes=$epCount durationMs=$durationMs")
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
        val season = (route as? OneRoute.Episode)?.season ?: 1
        val episode = (route as? OneRoute.Episode)?.episode ?: 1

        val providerTargets = mutableMapOf<String, String>()
        if (isEpisode) {
            val fromRegistry = OneEpisodeRegistry.get(mediaId, season, episode)
            if (fromRegistry.isNotEmpty()) {
                OneDiagnostics.log("EPISODE_DATA_FOUND", "id=$mediaId s=$season e=$episode providers=${fromRegistry.keys}")
                providerTargets.putAll(fromRegistry)
            } else {
                OneDiagnostics.log("EPISODE_DATA_MISSING", "id=$mediaId s=$season e=$episode")
            }
        }

        var totalCount = 0

        for (listing in media.listings) {
            val provId = listing.providerId
            if (!OneSettings.isEnabled(provId)) continue
            val adapter = OneProviders.byId(provId) ?: continue

            var targetData = providerTargets[provId]

            if (targetData == null) {
                try {
                    val load = withTimeoutOrNull(4500L) { adapter.loadDetail(listing.url) }
                    if (load != null) {
                        targetData = if (!isEpisode) {
                            (load as? MovieLoadResponse)?.dataUrl ?: listing.url
                        } else {
                            val eps = (load as? TvSeriesLoadResponse)?.episodes.orEmpty()
                            val matched = eps.firstOrNull { (it.season ?: 1) == season && (it.episode ?: 1) == episode }
                            matched?.data?.also { directUrl ->
                                OneEpisodeRegistry.register(mediaId, season, episode, provId, directUrl)
                            }
                        }
                        if (targetData != null) {
                            providerTargets[provId] = targetData
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    OneDiagnostics.log("EXTRACTOR_FAILED", "provider=$provId stage=FALLBACK_DETAIL error=${e.javaClass.simpleName}")
                }
            }

            if (targetData == null) continue

            try {
                val pStartNs = System.nanoTime()
                var pCount = 0
                OneDiagnostics.log("PROVIDER_LINKS_START", "provider=$provId url=$targetData")

                val worked = withTimeoutOrNull(6500L) {
                    adapter.loadLinks(targetData, isCasting, subtitleCallback) { link ->
                        val formatted = tagLink(link, provId)
                        callback(formatted)
                        pCount++
                        totalCount++
                    }
                } ?: false

                val pMs = (System.nanoTime() - pStartNs) / 1_000_000
                OneDiagnostics.log("PROVIDER_LINKS_RESULT", "provider=$provId links=$pCount worked=$worked ms=$pMs")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                OneDiagnostics.log("EXTRACTOR_FAILED", "provider=$provId error=${e.javaClass.simpleName}")
            }
        }

        val totalMs = (System.nanoTime() - startNs) / 1_000_000
        OneDiagnostics.log("PLAYABLE_LINKS_TOTAL", "id=$mediaId links=$totalCount totalMs=$totalMs")
        return totalCount > 0
    }

    private fun tagLink(link: ExtractorLink, providerId: String): ExtractorLink {
        val rawName = link.name
        val rawSource = link.source
        val hasDub = rawName.contains("dublaj", ignoreCase = true) || rawName.contains("[dil:0]", ignoreCase = true) || rawSource.contains("dublaj", ignoreCase = true)
        val hasSub = rawName.contains("altyaz", ignoreCase = true) || rawName.contains("[dil:1]", ignoreCase = true) || rawSource.contains("altyaz", ignoreCase = true)

        val cleanName = rawName
            .replace(Regex("\\[?$providerId\\]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[(?:Türkçe )?(?:Dublaj|Altyazılı?)\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[dil:[01]\\]", RegexOption.IGNORE_CASE), "")
            .trim()

        val langTag = when {
            hasDub -> "[Türkçe Dublaj]"
            hasSub -> "[Türkçe Altyazı]"
            else -> ""
        }

        val finalName = buildString {
            append("[$providerId] ")
            if (cleanName.isNotBlank()) {
                append(cleanName)
                append(" ")
            }
            if (langTag.isNotBlank() && !cleanName.contains(langTag, ignoreCase = true)) {
                append(langTag)
            }
        }.replace(Regex("\\s+"), " ").trim()

        @Suppress("DEPRECATION")
        return ExtractorLink(
            source = "[$providerId] $rawSource",
            name = finalName,
            url = link.url,
            referer = link.referer,
            quality = link.quality,
            type = link.type,
            headers = link.headers,
            extractorData = link.extractorData
        )
    }
}
