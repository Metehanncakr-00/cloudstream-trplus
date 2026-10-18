package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.diagnostics.DiagnosticCategory
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.diagnostics.DiagnosticStage
import com.cloudstream.tr.core.model.ProviderModels
import com.cloudstream.tr.core.network.StreamValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class CloudStreamHub : MainAPI() {
    override var mainUrl = "https://api.themoviedb.org/3"
    override var name = "CloudStreamHub"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.Cartoon,
        TvType.Documentary
    )

    // TMDB Credential Policy: Public read-only client credential model.
    // Client-side plugins (.cs3) cannot achieve zero-knowledge secret storage against decompilation.
    // Tokens are redacted from all diagnostic logs and network traces by DiagnosticLogger.
    private val tmdbApiKey = "90ad3ec891e5923150283b99719d890f"
    private val tmdbToken = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI5MGFkM2VjODkxZTU5MjMxNTAyODNiOTk3MTlkODkwZiIsIm5iZiI6MTc4OTgxODY1Mi40NzksInN1YiI6IjZhYWU3NzFjOTZlY2VmMDkzYmExZGU4NSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.2iN-8AMjIO4zMGL60TwvBY0sVdDThmf66GRfkanrtvc"
    private val imageBase = "https://image.tmdb.org/t/p/w500"

    private val jsonAcceptHeaders = mapOf(
        "Accept" to "application/json"
    )

    private val authHeaders = mapOf(
        "Authorization" to "Bearer $tmdbToken",
        "Accept" to "application/json"
    )

    private suspend fun fetchTmdbPage(targetUrl: String): TmdbPageResponse? {
        return try {
            app.get(targetUrl, headers = jsonAcceptHeaders).parsedSafe<TmdbPageResponse>()
                ?: app.get(targetUrl, headers = authHeaders).parsedSafe<TmdbPageResponse>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            try {
                app.get(targetUrl, headers = authHeaders).parsedSafe<TmdbPageResponse>()
            } catch (_: Throwable) {
                null
            }
        }
    }

    private suspend fun fetchTmdbDetail(targetUrl: String): TmdbDetailResponse? {
        return try {
            app.get(targetUrl, headers = jsonAcceptHeaders).parsedSafe<TmdbDetailResponse>()
                ?: app.get(targetUrl, headers = authHeaders).parsedSafe<TmdbDetailResponse>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            try {
                app.get(targetUrl, headers = authHeaders).parsedSafe<TmdbDetailResponse>()
            } catch (_: Throwable) {
                null
            }
        }
    }

    private val availableHomePages by lazy { mainPageOf(
        "${mainUrl}/trending/all/day?language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.ALL_CONTENT,
        "${mainUrl}/discover/movie?sort_by=popularity.desc&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.MOVIES,
        "${mainUrl}/discover/tv?sort_by=popularity.desc&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.SERIES,
        "${mainUrl}/discover/tv?with_origin_country=TR&sort_by=popularity.desc&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.TURKISH_SERIES,
        "${mainUrl}/discover/tv?with_origin_country=TR&first_air_date.lte=2015-12-31&sort_by=popularity.desc&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.NOSTALGIC_TURKISH_SERIES,
        "${mainUrl}/discover/movie?with_genres=16,10751&sort_by=popularity.desc&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.CARTOONS,
        "${mainUrl}/discover/tv?with_genres=16,10762&sort_by=popularity.desc&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.CARTOON_SERIES,
        "${mainUrl}/discover/movie?with_genres=16&sort_by=popularity.desc&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.ANIMATION_MOVIES,
        "${mainUrl}/discover/tv?with_genres=16&with_origin_country=JP&sort_by=popularity.desc&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.ANIME,
        "${mainUrl}/discover/movie?with_genres=99&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.DOCUMENTARIES,
        "${mainUrl}/discover/movie?with_origin_country=TR&primary_release_date.lte=1995-12-31&sort_by=popularity.desc&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.YESILCAM,
        "${mainUrl}/discover/movie?sort_by=popularity.desc&with_original_language=en&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.TR_DUB,
        "${mainUrl}/discover/tv?sort_by=vote_average.desc&vote_count.gte=200&language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.TR_SUB,
        "${mainUrl}/movie/now_playing?language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.NEW_RELEASES,
        "${mainUrl}/movie/popular?language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.POPULAR,
        "${mainUrl}/trending/all/week?language=tr-TR&api_key=${tmdbApiKey}" to UnifiedCategories.CONTINUE_WATCHING
    ) }

    // The CloudStream host may snapshot this list; reopening the app refreshes it.
    override val mainPage: List<MainPageData>
        get() = HubHomePreferences.filtered(availableHomePages)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Even with a previously captured host tab, no hidden category should fetch TMDB.
        if (!HubHomePreferences.isVisible(request.name)) {
            return newHomePageResponse(request.name, emptyList(), hasNext = false)
        }

        val context = HubContextHolder.pluginContext

        // Fast-path for Continue Watching from recently cached/visited catalog items
        if (request.name == UnifiedCategories.CONTINUE_WATCHING && page == 1) {
            val recent = HubCatalogCache.getRecentCatalogItems(limit = 40, context = context)
            if (recent.isNotEmpty()) {
                return newHomePageResponse(request.name, recent, hasNext = false)
            }
        }

        val providers = CloudStreamProviderRegistryAdapter.getRegisteredProviders(
            excludeName = this@CloudStreamHub.name,
            onlyEnabled = true
        )

        val tmdbFetch: (suspend () -> List<SearchResponse>)? = if (request.data.startsWith(mainUrl)) {
            suspend {
                val targetUrl = "${request.data}&page=${page}"
                val resp = fetchTmdbPage(targetUrl)
                resp?.results?.mapNotNull { parseTmdbItem(it) } ?: emptyList()
            }
        } else null

        if (page == 1 && context != null) {
            UnifiedCatalogEngine.triggerBackgroundPrewarm(providers, context)
        }

        val (items, hasNext) = UnifiedCatalogEngine.getCategoryPage(
            categoryName = request.name,
            page = page,
            allProviders = providers,
            tmdbFetch = tmdbFetch,
            context = context
        )

        return newHomePageResponse(request.name, items, hasNext = hasNext)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val targetUrl = "${mainUrl}/search/multi?query=${query}&language=tr-TR&page=${page}&api_key=${tmdbApiKey}"
        val tmdbResp = fetchTmdbPage(targetUrl)
        val tmdbItems = tmdbResp?.results?.mapNotNull { parseTmdbItem(it) } ?: emptyList()
        val totalPages = tmdbResp?.totalPages ?: 1

        val allFound = mutableListOf<SearchResponse>()
        allFound.addAll(tmdbItems)

        // Augment with indexed provider catalog cache (getProviderCatalog) and live federated search on ALL enabled providers for page 1
        if (page == 1) {
            val enabledProviders = CloudStreamProviderRegistryAdapter.getRegisteredProviders(
                excludeName = this@CloudStreamHub.name,
                onlyEnabled = true
            )

            // 1. Consume crawled provider catalog cache first (zero network overhead)
            val indexedItems = UnifiedCatalogEngine.searchIndexedCatalog(
                query = query,
                allProviders = enabledProviders,
                context = HubContextHolder.pluginContext
            )
            allFound.addAll(indexedItems)

            // 2. Live federated search across enabled providers
            val searchTimeoutMs = HubSettingsManager.snapshot().effectiveSearchTimeoutMs
            val maxConcurrency = HubSettingsManager.snapshot().maxConcurrency.coerceIn(1, 10)
            val semaphore = Semaphore(maxConcurrency)

            coroutineScope {
                val searches = enabledProviders.map { provider ->
                    async {
                        semaphore.withPermit {
                            try {
                                withTimeoutOrNull(searchTimeoutMs) {
                                    HubMatchingEngine.searchProvider(provider, query)
                                } ?: emptyList()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Throwable) {
                                emptyList<SearchResponse>()
                            }
                        }
                    }
                }
                val batches = searches.awaitAll()
                for (b in batches) allFound.addAll(b)
            }
        }

        val deduped = HubSingleCardMerger.mergeCandidates(allFound)
        return newSearchResponseList(deduped, hasNext = page < totalPages)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    fun parseTmdbItem(item: TmdbItem): SearchResponse? {
        val id = item.id ?: return null
        val isMovie = when (item.mediaType) {
            "movie" -> true
            "tv" -> false
            else -> item.title != null
        }

        val rawTitle = if (isMovie) {
            item.title ?: item.originalTitle
        } else {
            item.name ?: item.originalName
        } ?: return null

        val poster = item.posterPath?.let { "$imageBase$it" }
        val date = if (isMovie) item.releaseDate else item.firstAirDate
        val year = date?.take(4)?.toIntOrNull()
        val score = item.voteAverage?.toString()

        val dataUrl = if (isMovie) {
            "${mainUrl}/movie/${id}?api_key=${tmdbApiKey}&language=tr-TR&append_to_response=credits,videos,external_ids"
        } else {
            "${mainUrl}/tv/${id}?api_key=${tmdbApiKey}&language=tr-TR&append_to_response=credits,videos,external_ids"
        }

        return if (isMovie) {
            newMovieSearchResponse(rawTitle, dataUrl, HubContentRouting.type(true, item.genreIds.orEmpty())) {
                this.posterUrl = poster
                this.year = year
                this.score = Score.from10(score)
            }
        } else {
            newTvSeriesSearchResponse(rawTitle, dataUrl, HubContentRouting.type(false, item.genreIds.orEmpty())) {
                this.posterUrl = poster
                this.year = year
                this.score = Score.from10(score)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val context = HubContextHolder.pluginContext
        if (url.startsWith(mainUrl)) {
            val resp = fetchTmdbDetail(url)
            if (resp != null) {
                return parseTmdbDetail(resp, url)
            }
            // Fallback to HubMediaRegistry if TMDB detail endpoint fails transiently
            val tmdbId = Regex("/(?:movie|tv)/(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()
            val isMovie = url.contains("/movie/")
            val fallbackMedia = (if (tmdbId != null) HubMediaRegistry.findByTmdb(tmdbId, isMovie, context) else null)
                ?: HubMediaRegistry.getByUrl(url, context)
            if (fallbackMedia != null) {
                val provUrls = fallbackMedia.providerReferences
                    .filter { it.providerName != "TMDB" }
                    .associate { it.providerName to it.url }
                if (fallbackMedia.isMovie) {
                    val payload = AggregatorLinkPayload(
                        title = fallbackMedia.title,
                        year = fallbackMedia.year,
                        isMovie = true,
                        tmdbId = fallbackMedia.tmdbId,
                        imdbId = fallbackMedia.imdbId,
                        originalTitle = fallbackMedia.originalTitle,
                        canonicalId = fallbackMedia.id,
                        providerUrls = provUrls
                    ).toUrlData()
                    return newMovieLoadResponse(fallbackMedia.title, url, fallbackMedia.tvType, payload) {
                        this.posterUrl = fallbackMedia.posterUrl
                        this.backgroundPosterUrl = fallbackMedia.backdropUrl
                        this.plot = fallbackMedia.plot
                        this.year = fallbackMedia.year
                        this.score = Score.from10(fallbackMedia.score)
                    }
                }
            }
            return null
        }

        val canonicalMedia = HubMediaRegistry.getByUrl(url, context)
        val providers = CloudStreamProviderRegistryAdapter.getRegisteredProviders(excludeName = this@CloudStreamHub.name)
        val matchedProvider = CloudStreamProviderRegistryAdapter.findProviderForUrl(url, providers)
            ?: canonicalMedia?.providerReferences?.firstNotNullOfOrNull { ref ->
                providers.firstOrNull { it.name.equals(ref.providerName, ignoreCase = true) }
            }

        if (matchedProvider != null) {
            val baseLoad = matchedProvider.load(url) ?: return null
            val provUrls = buildMap {
                canonicalMedia?.providerReferences
                    ?.filter { it.providerName != "TMDB" }
                    ?.forEach { put(it.providerName, it.url) }
                put(matchedProvider.name, url)
            }
            val effectiveYear = baseLoad.year ?: canonicalMedia?.year
            val effectiveCanonicalId = canonicalMedia?.id

            when (baseLoad) {
                is MovieLoadResponse -> {
                    val payload = AggregatorLinkPayload(
                        title = baseLoad.name,
                        year = effectiveYear,
                        isMovie = true,
                        canonicalId = effectiveCanonicalId,
                        providerUrls = provUrls
                    ).toUrlData()
                    return newMovieLoadResponse(baseLoad.name, url, baseLoad.type, payload) {
                        this.posterUrl = baseLoad.posterUrl ?: canonicalMedia?.posterUrl
                        this.backgroundPosterUrl = baseLoad.backgroundPosterUrl
                        this.plot = baseLoad.plot
                        this.year = effectiveYear
                        this.tags = baseLoad.tags
                        this.score = baseLoad.score
                    }
                }
                is TvSeriesLoadResponse -> {
                    val wrappedEpisodes = baseLoad.episodes.map { ep ->
                        val epPayload = AggregatorLinkPayload(
                            title = baseLoad.name,
                            year = effectiveYear,
                            isMovie = false,
                            season = ep.season ?: 1,
                            episode = ep.episode ?: 1,
                            canonicalId = effectiveCanonicalId,
                            providerUrls = provUrls
                        ).toUrlData()
                        newEpisode(epPayload) {
                            this.name = ep.name
                            this.season = ep.season
                            this.episode = ep.episode
                            this.posterUrl = ep.posterUrl
                            this.description = ep.description
                            this.data = epPayload
                        }
                    }
                    return newTvSeriesLoadResponse(baseLoad.name, url, baseLoad.type, wrappedEpisodes) {
                        this.posterUrl = baseLoad.posterUrl ?: canonicalMedia?.posterUrl
                        this.backgroundPosterUrl = baseLoad.backgroundPosterUrl
                        this.plot = baseLoad.plot
                        this.year = effectiveYear
                        this.tags = baseLoad.tags
                        this.score = baseLoad.score
                    }
                }
                is AnimeLoadResponse -> {
                    val allRawEpisodes = baseLoad.episodes.values.flatten()
                    val wrappedEpisodes = allRawEpisodes.map { ep ->
                        val epPayload = AggregatorLinkPayload(
                            title = baseLoad.name,
                            year = effectiveYear,
                            isMovie = false,
                            season = ep.season ?: 1,
                            episode = ep.episode ?: 1,
                            canonicalId = effectiveCanonicalId,
                            providerUrls = provUrls
                        ).toUrlData()
                        newEpisode(epPayload) {
                            this.name = ep.name
                            this.season = ep.season
                            this.episode = ep.episode
                            this.posterUrl = ep.posterUrl
                            this.description = ep.description
                            this.data = epPayload
                        }
                    }
                    return newTvSeriesLoadResponse(baseLoad.name, url, baseLoad.type, wrappedEpisodes) {
                        this.posterUrl = baseLoad.posterUrl ?: canonicalMedia?.posterUrl
                        this.backgroundPosterUrl = baseLoad.backgroundPosterUrl
                        this.plot = baseLoad.plot
                        this.year = effectiveYear
                        this.tags = baseLoad.tags
                        this.score = baseLoad.score
                    }
                }
                else -> return baseLoad
            }
        }

        return null
    }

    suspend fun parseTmdbDetail(resp: TmdbDetailResponse, url: String): LoadResponse? {
        val id = resp.id ?: return null
        val isMovie = url.contains("/movie/")
        val title = (if (isMovie) resp.title ?: resp.originalTitle else resp.name ?: resp.originalName) ?: return null
        val poster = resp.posterPath?.let { "$imageBase$it" }
        val backdrop = resp.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
        val date = if (isMovie) resp.releaseDate else resp.firstAirDate
        val year = date?.take(4)?.toIntOrNull()
        val score = resp.voteAverage?.toString()
        val tags = resp.genres?.mapNotNull { it.name } ?: emptyList()
        val actors = resp.credits?.cast?.mapNotNull { cast ->
            cast.name?.let { name ->
                Actor(name, cast.profilePath?.let { "$imageBase$it" })
            }
        } ?: emptyList()
        val trailerKey = resp.videos?.results?.firstOrNull { it.site == "YouTube" && (it.type == "Trailer" || it.type == "Teaser") }?.key
        val trailerUrl = trailerKey?.let { "https://www.youtube.com/watch?v=$it" }

        val context = HubContextHolder.pluginContext
        val canonicalMedia = HubMediaRegistry.findByTmdb(id, isMovie, context)
            ?: HubMediaRegistry.find(title, year, isMovie, context)
        val provUrls = canonicalMedia?.providerReferences
            ?.filter { it.providerName != "TMDB" }
            ?.associate { it.providerName to it.url }.orEmpty()

        if (isMovie) {
            val payload = AggregatorLinkPayload(
                title = title,
                year = year,
                isMovie = true,
                tmdbId = id,
                imdbId = resp.externalIds?.imdbId,
                originalTitle = resp.originalTitle ?: resp.originalName,
                category = HubContentRouting.category(resp.genres.orEmpty().mapNotNull { it.id }),
                canonicalId = canonicalMedia?.id,
                providerUrls = provUrls
            ).toUrlData()

            return newMovieLoadResponse(title, url, HubContentRouting.type(true, resp.genres.orEmpty().mapNotNull { it.id }), payload) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = resp.overview
                this.year = year
                this.tags = tags
                this.score = Score.from10(score)
                addActors(actors)
                addTrailer(trailerUrl)
            }
        } else {
            val episodes = mutableListOf<Episode>()
            val seasons = resp.seasons?.filter { (it.seasonNumber ?: 0) > 0 } ?: emptyList()

            for (season in seasons) {
                val sNum = season.seasonNumber ?: 1
                val epCount = season.episodeCount ?: 0
                for (ep in 1..epCount) {
                    val epPayload = AggregatorLinkPayload(
                        title = title,
                        year = year,
                        isMovie = false,
                        season = sNum,
                        episode = ep,
                        tmdbId = id,
                        imdbId = resp.externalIds?.imdbId,
                        originalTitle = resp.originalTitle ?: resp.originalName,
                        category = HubContentRouting.category(resp.genres.orEmpty().mapNotNull { it.id }),
                        canonicalId = canonicalMedia?.id,
                        providerUrls = provUrls
                    ).toUrlData()

                    episodes.add(
                        newEpisode(epPayload) {
                            this.name = "${sNum}. Sezon ${ep}. Bölüm"
                            this.season = sNum
                            this.episode = ep
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(title, url, HubContentRouting.type(false, resp.genres.orEmpty().mapNotNull { it.id }), episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = resp.overview
                this.year = year
                this.tags = tags
                this.score = Score.from10(score)
                addActors(actors)
                addTrailer(trailerUrl)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val payload = AggregatorLinkPayload.fromUrlData(data) ?: run {
            val providers = CloudStreamProviderRegistryAdapter.getRegisteredProviders(excludeName = this@CloudStreamHub.name)
            val directProvider = CloudStreamProviderRegistryAdapter.findProviderForUrl(data, providers)
            return if (directProvider != null) {
                directProvider.loadLinks(data, isCasting, subtitleCallback, callback)
            } else {
                false
            }
        }
        val torrentSourcesEnabled = com.cloudstream.tr.core.resolvers.DebridConfig.enableTorrentSources
        val debridEnabled = com.cloudstream.tr.core.resolvers.DebridConfig.isDebridEnabled
        val collector = HubLinkCollector(torrentSourcesEnabled, debridEnabled, callback, rankedDelivery = true)
        val federationTorrents = Collections.synchronizedList(mutableListOf<ExtractorLink>())
        val interceptedCallback: (ExtractorLink) -> Unit = { link ->
            if (HubTorrentSelector.isTorrent(link)) federationTorrents.add(link)
            else collector.direct(link)
        }

        suspend fun fetchSubtitlesInBackground() {
            val imdbId = payload.imdbId ?: return
            CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
                com.cloudstream.tr.core.resolvers.SubtitlesResolver.resolveTurkishSubtitles(
                    imdbId = imdbId,
                    isMovie = payload.isMovie,
                    season = payload.season,
                    episode = payload.episode,
                    title = payload.title,
                    callback = subtitleCallback
                )
            }
        }

        // 0. Zero-Latency Stream Cache Check (instant links)
        val cacheKey = com.cloudstream.tr.core.streaming.StreamCacheManager.buildKey(
            title = payload.title,
            year = payload.year,
            isMovie = payload.isMovie,
            season = payload.season,
            episode = payload.episode
        ) + (payload.tmdbId?.let { "_tmdb_$it" } ?: "")

        fun flushCollectedLinks() {
            val collectedLinks = collector.snapshot()
            if (collectedLinks.isEmpty()) return

            val linksForCache = collectedLinks.toList().map { link ->
                val baseName = link.name.removePrefix("[⚡ Smart Play] ")
                if (baseName == link.name) {
                    link
                } else {
                    ExtractorLink(
                        source = link.source,
                        name = baseName,
                        url = link.url,
                        referer = link.referer,
                        quality = link.quality,
                        type = link.type,
                        headers = link.headers,
                        extractorData = link.extractorData,
                        audioTracks = link.audioTracks
                    )
                }
            }
            val deterministicLinks = linksForCache.sortedWith(
                compareBy<ExtractorLink> { it.source.lowercase() }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.url }
            )
            val sortedLinks = com.cloudstream.tr.core.model.StreamPrioritySorter.sortByPriority(deterministicLinks)
            // Direct/CDN stream URLs often contain short-lived signatures.
            // Only torrent candidates are safe to reuse without re-resolving HTTP media.
            HubTorrentCandidateCache.put(cacheKey, cacheableHubLinks(sortedLinks))
        }

        val cachedLinks = HubTorrentCandidateCache.get(cacheKey)
        // Do not deliver cached magnets through collector.direct(): it drops torrents.
        // Publish cached candidates only after fresh resolution so they cannot occupy
        // the limited quality quotas before fresher 4K/REMUX links are considered.

        // 1. Automated Turkish Subtitles via OpenSubtitles
        fetchSubtitlesInBackground()

        return coroutineScope {
            // Direct HTTP resolution and federation must not block the torrent pair or each other.
            val directJob = launch {
                val originStart = System.nanoTime()
                val budget = HubAdaptiveTimeout.limit("direct", HubSourceBudget.DIRECT_DISCOVERY_MS)
                val completed = HubSourceBudget.run(budget) {
                    try {
                        com.cloudstream.tr.core.resolvers.FourKhubResolver.resolve(
                            title = payload.title, year = payload.year, isMovie = payload.isMovie,
                            season = payload.season, episode = payload.episode,
                            onValidatedLink = { link, evidence ->
                                collector.recordEvidence(link, evidence)
                                interceptedCallback(link)
                            }
                        )
                    } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                }
                val elapsed = (System.nanoTime() - originStart) / 1_000_000
                HubAdaptiveTimeout.observe("direct", elapsed, completed)
                HubPerformanceTelemetry.record("direct_discovery", elapsed, if (completed) "ok" else "timeout")
                if (!completed) DiagnosticLogger.log(
                    provider = name, stage = DiagnosticStage.LOAD, category = DiagnosticCategory.NETWORK,
                    message = "Direct source discovery exceeded adaptive time budget."
                )
            }
            val torrentJob = launch {
                val imdbId = payload.imdbId
                if (imdbId != null && (torrentSourcesEnabled || debridEnabled)) {
                    val fresh = Collections.synchronizedList(mutableListOf<ExtractorLink>())
                    val torrentDiscoveryStartedNs = System.nanoTime()
                    val resolvers = mutableListOf<suspend () -> List<ExtractorLink>>(
                        {
                            val getLinks: suspend () -> List<ExtractorLink> = {
                                com.cloudstream.tr.core.resolvers.TorrentioResolver.resolve(
                                    imdbId, payload.isMovie, payload.season, payload.episode
                                )
                            }
                            if (debridEnabled) getLinks()
                            else HubSourceMemoRegistry.torrentio.resolve(
                                listOf("torrentio", imdbId, payload.isMovie, payload.season,
                                    payload.episode).joinToString(":")
                            ) {
                                val evidence = com.cloudstream.tr.core.resolvers.TorrentioResolver.resolveWithEvidence(
                                    imdbId, payload.isMovie, payload.season, payload.episode
                                )
                                HubLookupOutcome(evidence.links, evidence.authoritativeEmpty)
                            }
                        }
                    )
                    if (payload.isMovie && torrentSourcesEnabled) {
                        resolvers.add {
                            if (debridEnabled) {
                                com.cloudstream.tr.core.resolvers.YtsResolver.resolve(imdbId, true)
                            } else {
                                HubSourceMemoRegistry.yts.resolve("yts:" + imdbId) {
                                    HubLookupOutcome(
                                        com.cloudstream.tr.core.resolvers.YtsResolver.resolve(imdbId, true)
                                    )
                                }
                            }
                        }
                    }
                    // Retain completed batches; publish globally ranked links after bounded discovery.
                    val discoveryBudget = HubAdaptiveTimeout.limit("torrent", HubSourceBudget.TORRENT_DISCOVERY_MS)
                    val completed = HubSourceBudget.run(discoveryBudget) {
                    resolveHubSourcesProgressively(
                        resolvers = resolvers,
                        onBatch = { batch ->
                            DiagnosticLogger.log(
                                provider = name, stage = DiagnosticStage.LOAD,
                                category = DiagnosticCategory.SOURCE_DISCOVERY,
                                message = "Torrent source batch: ${batch.size} results in ${(System.nanoTime() - torrentDiscoveryStartedNs) / 1_000_000} ms"
                            )
                            fresh.addAll(batch)
                            batch.filterNot(HubTorrentSelector::isTorrent).forEach(collector::direct)
                            collector.torrents(batch.filter(HubTorrentSelector::isTorrent))
                        },
                        onFailure = { failure ->
                            DiagnosticLogger.log(
                                provider = name, stage = DiagnosticStage.LOAD,
                                category = DiagnosticCategory.NETWORK,
                                message = "Torrent source failed (${failure.javaClass.simpleName})"
                            )
                        }
                    )
                    }
                    val elapsed = (System.nanoTime() - torrentDiscoveryStartedNs) / 1_000_000
                    HubAdaptiveTimeout.observe("torrent", elapsed, completed)
                    HubPerformanceTelemetry.record("torrent_discovery", elapsed, if (completed) "ok" else "timeout")
                    if (!completed) DiagnosticLogger.log(
                        provider = name, stage = DiagnosticStage.LOAD, category = DiagnosticCategory.NETWORK,
                        message = "Torrent discovery exceeded adaptive time budget; completed batches retained."
                    )
                    // Reuse only candidates from origins that returned no fresh sources.
                    val fallback = HubTorrentSelector.cachedFallback(cachedLinks.orEmpty(), fresh.toList(), true)
                    collector.torrents(fallback)
                }
            }

            val discoveredProviders = CloudStreamProviderRegistryAdapter.getRegisteredProviders(
                excludeName = this@CloudStreamHub.name,
                onlyEnabled = true
            )
            val providers = discoveredProviders.filter { HubContentRouting.eligible(it, payload) }
            if (providers.isEmpty()) DiagnosticLogger.log(
                provider = name,
                stage = DiagnosticStage.LOAD,
                category = DiagnosticCategory.SOURCE_DISCOVERY,
                message = "No eligible providers available from the CloudStream registry or CloudStreamHub bundle."
            )
            val federationFinished = HubFederationBudget.run {
            BoundedParallelResolver.resolveProgressive(
                candidates = providers,
                maxConcurrency = HubSettingsManager.snapshot().maxConcurrency,
                provider = name,
                resolver = { provider, emitLink ->
                    val providerTimeout = HubSettingsManager.snapshot().effectiveProviderTimeoutMs
                    val completed = HubProviderTimeout.resolve(providerTimeout) {
                        try {
                            val directMatchedUrl = payload.providerUrls[provider.name]
                                ?: HubMediaRegistry.getReferences(payload, HubContextHolder.pluginContext).firstOrNull { it.providerName == provider.name }?.url

                            var matchName = provider.name
                            val loadRes = if (directMatchedUrl != null) {
                                matchName = directMatchedUrl
                                DiagnosticLogger.log(
                                    provider = provider.name,
                                    stage = DiagnosticStage.LOAD,
                                    category = DiagnosticCategory.SOURCE_DISCOVERY,
                                    message = "Federation result: using direct canonical matched URL '$directMatchedUrl'"
                                )
                                provider.load(directMatchedUrl)
                            } else {
                                val targetTitles = listOfNotNull(payload.title, payload.originalTitle).distinct()
                                val searchQueries = targetTitles
                                    .filter { it.isNotBlank() }
                                    .distinctBy { it.trim().lowercase() }
                                val matched = HubMatchingEngine.findConfidentMatchFromSearches(
                                    searchQueries = searchQueries,
                                    targetTitles = targetTitles,
                                    targetYear = payload.year,
                                    isMovie = payload.isMovie,
                                    search = { query -> HubMatchingEngine.searchProvider(provider, query) }
                                ) ?: run {
                                    DiagnosticLogger.log(
                                        provider = provider.name,
                                        stage = DiagnosticStage.SEARCH,
                                        category = DiagnosticCategory.SOURCE_DISCOVERY,
                                        message = "Federation result: no confident match for '${payload.title}' or '${payload.originalTitle}' (${payload.year ?: "N/A"}); queries=${searchQueries.size}; arbitrary first results rejected."
                                    )
                                    return@resolve
                                }
                                matchName = matched.name

                                DiagnosticLogger.log(
                                    provider = provider.name,
                                    stage = DiagnosticStage.SEARCH,
                                    category = DiagnosticCategory.SOURCE_DISCOVERY,
                                    message = "Federation result: confident ${matched.type} match '${matched.name}'; loading provider result."
                                )
                                provider.load(matched.url)
                            } ?: run {
                                DiagnosticLogger.log(
                                    provider = provider.name,
                                    stage = DiagnosticStage.LOAD,
                                    category = DiagnosticCategory.SOURCE_DISCOVERY,
                                    message = "Federation result: provider.load returned no response for '${provider.name}'."
                                )
                                return@resolve
                            }
                            val targetLinkData = HubContentRouting.linkData(loadRes, payload)

                            if (targetLinkData != null) {
                                // Hard cap avoids uncontrolled extraction/preflight work on low-RAM TVs.
                                val channel = Channel<ExtractorLink>(capacity = HubCandidateBudget.MAX_PER_PROVIDER)
                                val seenUrls = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
                                val rawLinksReceived = AtomicInteger(0)
                                val standardCandidates = AtomicInteger(0)
                                val featureCandidates = HubFeatureTopK(HubCandidateBudget.RESERVED_FEATURE - 1)
                                val firstFeature = java.util.concurrent.atomic.AtomicBoolean(false)
                                val duplicatesDropped = AtomicInteger(0)
                                val channelOverflowDropped = AtomicInteger(0)
                                val preflightValid = AtomicInteger(0)
                                val preflightInvalid = AtomicInteger(0)
                                val preflightIndeterminate = AtomicInteger(0)
                                val linksEmitted = AtomicInteger(0)

                                coroutineScope {
                                    // The provider may return hundreds of links quickly.
                                    // Limit HTTP verification to three in flight, preserve
                                    // torrent candidates without unnecessary HTTP preflight.
                                    val verifySlots = Semaphore(3)
                                    val consumerJob = launch {
                                        val checks = mutableListOf<Job>()
                                        for (rawLink in channel) {
                                            currentCoroutineContext().ensureActive()
                                            val rawUrl = rawLink.url
                                            if (rawUrl.isBlank() || rawUrl.contains("youtube.com") || rawUrl.contains("youtu.be")) continue
                                            if (!seenUrls.add(rawUrl)) {
                                                duplicatesDropped.incrementAndGet()
                                                continue
                                            }
                                            if (HubTorrentSelector.isTorrent(rawLink)) {
                                                emitLink(rawLink)
                                                linksEmitted.incrementAndGet()
                                                continue
                                            }
                                            checks.add(launch {
                                                verifySlots.withPermit {
                                                    val reqHeaders = rawLink.headers.toMutableMap()
                                                    if (rawLink.referer.isNotBlank() &&
                                                        !reqHeaders.containsKey("Referer") &&
                                                        !reqHeaders.containsKey("referer")) {
                                                        reqHeaders["Referer"] = rawLink.referer
                                                    }
                                                    val started = System.nanoTime()
                                                    val result = HubGlobalPreflight.run {
                                                        StreamValidator.validateStream(
                                                            url = rawUrl,
                                                            headers = reqHeaders,
                                                            provider = provider.name,
                                                            timeoutMs = HubSettingsManager.snapshot().effectivePreflightTimeoutMs
                                                        )
                                                    }
                                                    HubPerformanceTelemetry.record(
                                                        "preflight", (System.nanoTime() - started) / 1_000_000,
                                                        result.status.name
                                                    )
                                                    when (result.status) {
                                                        com.cloudstream.tr.core.network.ValidationStatus.VALID -> preflightValid.incrementAndGet()
                                                        com.cloudstream.tr.core.network.ValidationStatus.INVALID -> preflightInvalid.incrementAndGet()
                                                        com.cloudstream.tr.core.network.ValidationStatus.INDETERMINATE -> preflightIndeterminate.incrementAndGet()
                                                    }
                                                    if (result.isValid ||
                                                        result.status == com.cloudstream.tr.core.network.ValidationStatus.INDETERMINATE) {
                                                        val tagged = tagHubPreflightLink(provider.name, rawLink, result, reqHeaders)
                                                        currentCoroutineContext().ensureActive()
                                                        collector.recordEvidence(tagged, result.languageEvidence)
                                                        emitLink(tagged)
                                                        linksEmitted.incrementAndGet()
                                                    }
                                                }
                                            })
                                        }
                                        checks.joinAll()
                                    }

                                    try {
                                        provider.loadLinks(
                                            data = targetLinkData,
                                            isCasting = isCasting,
                                            subtitleCallback = subtitleCallback,
                                            callback = { rawLink ->
                                                rawLinksReceived.incrementAndGet()
                                                when {
                                                    HubCandidateBudget.isFeatureCandidate(rawLink) -> {
                                                        // Publish the first feature promptly; keep the best
                                                        // later ones in bounded Top-K rather than first 32.
                                                        if (firstFeature.compareAndSet(false, true)) {
                                                            if (!channel.trySend(rawLink).isSuccess)
                                                                featureCandidates.offer(rawLink)
                                                        } else {
                                                            featureCandidates.offer(rawLink)
                                                        }
                                                    }
                                                    standardCandidates.incrementAndGet() <= HubCandidateBudget.MAX_STANDARD -> {
                                                        if (!channel.trySend(rawLink).isSuccess)
                                                            channelOverflowDropped.incrementAndGet()
                                                    }
                                                    else -> channelOverflowDropped.incrementAndGet()
                                                }
                                            }
                                        )
                                    } finally {
                                        try {
                                            // Provider complete: deliver best 31 held feature candidates.
                                            // No retroactive retraction of previously published sources.
                                            for (feature in featureCandidates.snapshot()) channel.send(feature)
                                        } finally {
                                            // Avoid host coroutine default-argument bridge.
                                            channel.close(null)
                                        }
                                    }

                                    consumerJob.join()

                                    DiagnosticLogger.log(
                                        provider = provider.name,
                                        stage = DiagnosticStage.STREAM_PREFLIGHT,
                                        category = if (linksEmitted.get() > 0) DiagnosticCategory.SOURCE_DISCOVERY else DiagnosticCategory.NETWORK,
                                        message = "Federation result: matched='$matchName', raw=$rawLinksReceived, providerEmitted=$linksEmitted (before hub dedupe and source-policy filtering), valid=$preflightValid, indeterminate=$preflightIndeterminate, invalid=$preflightInvalid, duplicates=$duplicatesDropped, dropped=$channelOverflowDropped. Preflight does not confirm sustained playback."
                                    )
                                }
                            } else {
                                DiagnosticLogger.log(
                                    provider = provider.name,
                                    stage = if (payload.isMovie) DiagnosticStage.LOAD else DiagnosticStage.EPISODE_DISCOVERY,
                                    category = DiagnosticCategory.SOURCE_DISCOVERY,
                                    message = "Federation result: matched '$matchName', but no movie or requested episode link data was available."
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            DiagnosticLogger.log(
                                provider = provider.name,
                                stage = DiagnosticStage.LOAD,
                                category = DiagnosticCategory.EXTRACTOR,
                                message = "Provider resolution failed during aggregation: ${e.message}",
                                throwable = e
                            )
                        }
                    }
                    if (!completed) DiagnosticLogger.log(
                        provider = provider.name,
                        stage = DiagnosticStage.LOAD,
                        category = DiagnosticCategory.NETWORK,
                        message = "Federation result: provider timed out after 30 seconds; previously emitted links retained."
                    )
                },
                onLinkFound = { link ->
                    interceptedCallback(link)
                }
            )

            }
            if (!federationFinished) DiagnosticLogger.log(
                provider = name,
                stage = DiagnosticStage.LOAD,
                category = DiagnosticCategory.NETWORK,
                message = "Federation time budget reached; already emitted streams retained."
            )
            torrentJob.join()
            directJob.join()
            val freshFederation = federationTorrents.toList()
            collector.torrents(freshFederation + HubTorrentSelector.cachedFallback(
                cachedLinks.orEmpty(), collector.snapshot() + freshFederation, false
            ))
            // If a torrent origin was not reachable during this run, cached candidates
            // are retained as a fallback; they never preempt fresher sources.
            if (!torrentSourcesEnabled) DiagnosticLogger.log(
                provider = name, stage = DiagnosticStage.LOAD, category = DiagnosticCategory.SOURCE_DISCOVERY,
                message = "P2P disabled; cached torrents were not emitted."
            )
            currentCoroutineContext().ensureActive()
            collector.finish()
            flushCollectedLinks()
            collector.snapshot().isNotEmpty()
        }
    }
}
