package com.cloudstream.tr.hub

import android.content.Context
import com.cloudstream.tr.core.diagnostics.DiagnosticCategory
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.diagnostics.DiagnosticStage
import com.cloudstream.tr.core.model.ProviderModels
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates unified catalog aggregation across TMDB and all active providers.
 * Enforces:
 * - Global bounded concurrency & low RAM usage for Android TV stability
 * - In-flight request deduplication per (category, page) within the same session
 * - TTL caching & incremental indexing with zero-loss overflow pagination
 * - Stale cache preservation (never overwrites valid catalog cache with empty results)
 * - Consumption of crawled provider catalog pages (getProviderCatalog) in homepage & search
 * - Opt-in, cooldown-guarded background indexing (disabled by default on TV)
 */
object UnifiedCatalogEngine {

    private const val DEFAULT_PROVIDER_FETCH_TIMEOUT_MS = 6000L
    private const val MIN_PREWARM_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes cooldown

    private val categoryLocks = ConcurrentHashMap<String, Mutex>()

    @Volatile
    private var activeSemaphoreLimit: Int = 4

    @Volatile
    private var globalProviderSemaphore: Semaphore = Semaphore(4)

    @Synchronized
    private fun getGlobalSemaphore(desiredConcurrency: Int): Semaphore {
        val bounded = desiredConcurrency.coerceIn(1, 8)
        if (bounded != activeSemaphoreLimit) {
            activeSemaphoreLimit = bounded
            globalProviderSemaphore = Semaphore(bounded)
        }
        return globalProviderSemaphore
    }

    /**
     * Fetches unified page items for a given category across eligible providers.
     * Concurrency is strictly bounded by a global semaphore and in-flight per-page mutex,
     * ensuring smooth Android TV performance without redundant network calls.
     */
    suspend fun getCategoryPage(
        categoryName: String,
        page: Int,
        allProviders: List<MainAPI>,
        tmdbFetch: (suspend () -> List<SearchResponse>)? = null,
        context: Context? = null
    ): Pair<List<SearchResponse>, Boolean> {
        val lockKey = "$categoryName:$page"
        val mutex = categoryLocks.getOrPut(lockKey) { Mutex() }

        return mutex.withLock {
            getCategoryPageInternal(
                categoryName = categoryName,
                page = page,
                allProviders = allProviders,
                tmdbFetch = tmdbFetch,
                context = context
            )
        }
    }

    private suspend fun getCategoryPageInternal(
        categoryName: String,
        page: Int,
        allProviders: List<MainAPI>,
        tmdbFetch: (suspend () -> List<SearchResponse>)?,
        context: Context?
    ): Pair<List<SearchResponse>, Boolean> {
        val startedNs = System.nanoTime()
        val providerTargets = ProviderCapabilityMatrix.getProvidersForCategory(categoryName, allProviders)

        // 1. Check TTL cache (memory + persistent disk)
        HubCatalogCache.getUnified(categoryName, page, context)?.let { cached ->
            // If crawlBatch() has indexed new provider catalog pages into putProviderCatalog(),
            // incorporate them into the unified catalog without making redundant network calls.
            if (page == 1 && providerTargets.isNotEmpty()) {
                val crawledBatches = mutableListOf<SearchResponse>()
                var crawledHasNext = false
                for ((provider, categoryInfo) in providerTargets) {
                    if (categoryInfo == null) continue
                    val cursorKey = categoryInfo.categoryUrl.ifBlank { categoryInfo.categoryName }
                    val (provItems, provHasNext) = HubCatalogCache.getAllCachedProviderPages(provider.name, cursorKey, context)
                    if (provItems.isNotEmpty()) {
                        crawledBatches.addAll(provItems)
                    }
                    if (provHasNext) crawledHasNext = true
                }
                if (crawledBatches.isNotEmpty()) {
                    val (allUnifiedItems, unifiedLastHasNext) = HubCatalogCache.getAllUnifiedItems(categoryName, context)
                    val coveredUrls = HashSet<String>(allUnifiedItems.size * 2)
                    for (uItem in allUnifiedItems) {
                        coveredUrls.add(uItem.url)
                        HubSingleCardMerger.getCanonicalMedia(uItem, context)?.providerReferences?.forEach { ref ->
                            coveredUrls.add(ref.url)
                        }
                    }
                    val hasUncoveredCrawledItems = crawledBatches.any { it.url.isNotBlank() && !coveredUrls.contains(it.url) }
                    if (hasUncoveredCrawledItems) {
                        val combined = ArrayList<SearchResponse>(allUnifiedItems.size + crawledBatches.size)
                        combined.addAll(allUnifiedItems)
                        combined.addAll(crawledBatches)
                        val remerged = HubSingleCardMerger.mergeCandidates(combined)
                        val updated = HubCatalogCache.putUnified(
                            category = categoryName,
                            page = 1,
                            items = remerged,
                            hasNext = unifiedLastHasNext || crawledHasNext,
                            context = context
                        )
                        val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000
                        HubCatalogDiagnostics.record(
                            category = categoryName,
                            source = "HubCatalogCache",
                            stage = "CACHE_HIT_EXPANDED",
                            status = "OK",
                            itemCount = updated.first.size,
                            elapsedMs = elapsedMs
                        )
                        return updated
                    }
                }
            }
            val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000
            HubCatalogDiagnostics.record(
                category = categoryName,
                source = "HubCatalogCache",
                stage = "CACHE_HIT",
                status = "OK",
                itemCount = cached.first.size,
                elapsedMs = elapsedMs
            )
            return cached
        }

        // Determine the next upstream provider page to fetch on cache miss
        val upstreamPage = if (page <= 1) {
            1
        } else {
            val lastUpstream = HubCatalogCache.getLastUpstreamPage(context, categoryName)
            if (lastUpstream >= 1) lastUpstream + 1 else page
        }

        val collected = mutableListOf<SearchResponse>()
        var hasMorePages = false
        val settings = HubSettingsManager.snapshot()
        val fetchTimeoutMs = settings.effectiveCategoryTimeoutMs.coerceAtLeast(DEFAULT_PROVIDER_FETCH_TIMEOUT_MS)
        val semaphore = getGlobalSemaphore(settings.maxConcurrency)

        // First, consume all already-cached provider pages across all targets (0 network cost)
        val uncachedTargets = mutableListOf<Pair<MainAPI, ProviderCategoryInfo>>()
        for ((provider, categoryInfo) in providerTargets) {
            if (categoryInfo == null) continue
            val cursorKey = categoryInfo.categoryUrl.ifBlank { categoryInfo.categoryName }
            if (upstreamPage == 1) {
                val (cachedProvItems, cachedProvHasNext) =
                    HubCatalogCache.getAllCachedProviderPages(provider.name, cursorKey, context)
                if (cachedProvItems.isNotEmpty()) {
                    if (cachedProvHasNext) hasMorePages = true
                    collected.addAll(cachedProvItems)
                    continue
                }
            } else {
                val cachedPage = HubCatalogCache.getProviderCatalog(provider.name, cursorKey, upstreamPage, context)
                if (cachedPage != null && cachedPage.first.isNotEmpty()) {
                    if (cachedPage.second) hasMorePages = true
                    collected.addAll(cachedPage.first)
                    continue
                }
            }
            uncachedTargets.add(provider to categoryInfo)
        }

        // Deduplicate live network requests so a single provider is not scraped multiple times in one category call
        val distinctUncachedTargets = uncachedTargets.distinctBy { it.first.name }

        suspend fun fetchProviderTarget(
            provider: MainAPI,
            categoryInfo: ProviderCategoryInfo,
            budgetMs: Long = fetchTimeoutMs
        ): List<SearchResponse> {
            val cursorKey = categoryInfo.categoryUrl.ifBlank { categoryInfo.categoryName }
            val provStartNs = System.nanoTime()
            return try {
                // Two-tier timeout:
                // 1. Outer bound (budgetMs * 4) prevents unbounded semaphore queue starvation.
                // 2. Inner bound (budgetMs) inside semaphore.withPermit ensures each provider gets its full execution budget once scheduled and releases the permit promptly on timeout.
                val outcome = withTimeoutOrNull(budgetMs * 4) {
                    semaphore.withPermit {
                        withTimeoutOrNull(budgetMs) {
                            val req = MainPageRequest(
                                name = categoryInfo.categoryName,
                                data = categoryInfo.categoryUrl,
                                horizontalImages = false
                            )
                            val resp = provider.getMainPage(upstreamPage, req)
                            val list = resp?.items?.flatMap { it.list } ?: emptyList()
                            val provHasNext = resp?.hasNext == true
                            if (provHasNext) hasMorePages = true

                            if (list.isNotEmpty()) {
                                HubCatalogCache.putProviderCatalog(
                                    providerName = provider.name,
                                    categoryKey = cursorKey,
                                    page = upstreamPage,
                                    items = list,
                                    hasNext = provHasNext,
                                    context = context
                                )
                                val currentCursor = HubCatalogCache.getCrawlerCursor(context, provider.name, cursorKey)
                                if (currentCursor == upstreamPage) {
                                    if (!provHasNext) {
                                        HubCatalogCache.setCrawlerFinished(context, provider.name, cursorKey, true)
                                    } else {
                                        HubCatalogCache.saveCrawlerCursor(context, provider.name, cursorKey, upstreamPage + 1)
                                    }
                                }
                            }
                            list
                        }
                    }
                }
                val provElapsedMs = (System.nanoTime() - provStartNs) / 1_000_000
                if (outcome == null) {
                    HubCatalogDiagnostics.record(
                        category = categoryName,
                        source = provider.name,
                        stage = "PROVIDER_FETCH",
                        status = "TIMEOUT",
                        itemCount = 0,
                        elapsedMs = provElapsedMs
                    )
                    emptyList()
                } else {
                    HubCatalogDiagnostics.record(
                        category = categoryName,
                        source = provider.name,
                        stage = "PROVIDER_FETCH",
                        status = if (outcome.isNotEmpty()) "OK" else "EMPTY",
                        itemCount = outcome.size,
                        elapsedMs = provElapsedMs
                    )
                    outcome
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val provElapsedMs = (System.nanoTime() - provStartNs) / 1_000_000
                HubCatalogDiagnostics.record(
                    category = categoryName,
                    source = provider.name,
                    stage = "PROVIDER_FETCH",
                    status = "ERROR:${e.javaClass.simpleName}",
                    itemCount = 0,
                    elapsedMs = provElapsedMs
                )
                DiagnosticLogger.log(
                    provider = provider.name,
                    stage = DiagnosticStage.HOMEPAGE,
                    category = DiagnosticCategory.SOURCE_DISCOVERY,
                    message = "Provider catalog fetch failed for category '$categoryName': ${e.message}"
                )
                emptyList()
            }
        }

        coroutineScope {
            // 2. Fetch primary TMDB items immediately (never blocked behind provider HTML scraping semaphore)
            val tmdbItems = if (tmdbFetch != null) {
                val tmdbStartNs = System.nanoTime()
                try {
                    val res = withTimeoutOrNull(fetchTimeoutMs) { tmdbFetch() }
                    val tmdbElapsedMs = (System.nanoTime() - tmdbStartNs) / 1_000_000
                    if (res == null) {
                        HubCatalogDiagnostics.record(
                            category = categoryName,
                            source = "TMDB",
                            stage = "TMDB_FETCH",
                            status = "TIMEOUT",
                            itemCount = 0,
                            elapsedMs = tmdbElapsedMs
                        )
                        emptyList()
                    } else {
                        res
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    val tmdbElapsedMs = (System.nanoTime() - tmdbStartNs) / 1_000_000
                    HubCatalogDiagnostics.record(
                        category = categoryName,
                        source = "TMDB",
                        stage = "TMDB_FETCH",
                        status = "ERROR:${e.javaClass.simpleName}",
                        itemCount = 0,
                        elapsedMs = tmdbElapsedMs
                    )
                    emptyList()
                }
            } else {
                emptyList()
            }

            if (tmdbItems.isNotEmpty()) {
                collected.addAll(tmdbItems)
            }

            // 3. Determine whether live provider HTML scrapes are needed for this row:
            // - Always when tmdbFetch == null (e.g. Türkçe Dublaj, Türkçe Altyazı, or provider-only catalog rows)
            // - Always when TMDB returned empty (automatic provider catalog fallback when TMDB fails/times out!)
            // - Also when in COMPREHENSIVE mode or when a small provider list (<= 6) is passed for direct merging
            val shouldQueryLiveProviders = tmdbFetch == null ||
                tmdbItems.isEmpty() ||
                settings.perfMode == HubSettingsManager.PERF_MODE_COMPREHENSIVE ||
                allProviders.size <= 6

            if (shouldQueryLiveProviders && distinctUncachedTargets.isNotEmpty()) {
                if (tmdbFetch != null && tmdbItems.isEmpty()) {
                    HubCatalogDiagnostics.record(
                        category = categoryName,
                        source = "UnifiedCatalog",
                        stage = "FALLBACK_TO_PROVIDERS",
                        status = "TMDB_EMPTY_FALLBACK",
                        itemCount = distinctUncachedTargets.size,
                        elapsedMs = (System.nanoTime() - startedNs) / 1_000_000
                    )
                }

                val liveTargets = if (tmdbFetch != null && settings.perfMode != HubSettingsManager.PERF_MODE_COMPREHENSIVE) {
                    val maxLivePerRow = if (settings.perfMode == HubSettingsManager.PERF_MODE_FAST) 3 else 5
                    distinctUncachedTargets.take(maxLivePerRow)
                } else {
                    distinctUncachedTargets
                }

                val providerBatches = liveTargets.map { (provider, categoryInfo) ->
                    async { fetchProviderTarget(provider, categoryInfo, fetchTimeoutMs) }
                }.awaitAll()

                for (batch in providerBatches) {
                    collected.addAll(batch)
                }

                // Secondary fallback if the first wave of fallback providers all failed/returned empty
                if (collected.isEmpty() && distinctUncachedTargets.size > liveTargets.size) {
                    val fallbackTargets = distinctUncachedTargets.drop(liveTargets.size).take(6)
                    val fallbackBatches = fallbackTargets.map { (provider, categoryInfo) ->
                        async { fetchProviderTarget(provider, categoryInfo, fetchTimeoutMs) }
                    }.awaitAll()
                    for (batch in fallbackBatches) {
                        collected.addAll(batch)
                    }
                }
            }
        }

        if (tmdbFetch != null && collected.isNotEmpty()) {
            hasMorePages = true
        }

        // Only advance upstream page cursor when items were actually obtained
        if (collected.isNotEmpty()) {
            HubCatalogCache.saveLastUpstreamPage(context, categoryName, upstreamPage)
        }

        // 4. If all live fetches failed/returned empty, serve stale cache if available rather than wiping catalog
        if (collected.isEmpty()) {
            HubCatalogCache.getUnifiedStale(categoryName, page, context)?.let { stale ->
                if (stale.first.isNotEmpty()) {
                    val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000
                    HubCatalogDiagnostics.record(
                        category = categoryName,
                        source = "HubCatalogCache",
                        stage = "STALE_FALLBACK",
                        status = "OK_STALE",
                        itemCount = stale.first.size,
                        elapsedMs = elapsedMs
                    )
                    return stale
                }
            }
            val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000
            HubCatalogDiagnostics.record(
                category = categoryName,
                source = "UnifiedCatalog",
                stage = "CATEGORY_EMPTY",
                status = "ALL_SOURCES_EMPTY",
                itemCount = 0,
                elapsedMs = elapsedMs
            )
            return emptyList<SearchResponse>() to false
        }

        // 5. Merge items into single cards with high confidence
        val merged = HubSingleCardMerger.mergeCandidates(collected)

        // 6. Store in unified TTL cache (memory + persistent disk) with zero-loss overflow slicing
        val finalSlice = HubCatalogCache.putUnified(categoryName, page, merged, hasMorePages, context)
        val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000
        HubCatalogDiagnostics.record(
            category = categoryName,
            source = "UnifiedCatalog",
            stage = "MERGE_COMPLETE",
            status = "OK",
            itemCount = finalSlice.first.size,
            elapsedMs = elapsedMs
        )
        return finalSlice
    }

    /**
     * Searches all provider catalog pages already cached via [HubCatalogCache.getProviderCatalog]
     * so that crawled catalog items are immediately discoverable in search without redundant network requests.
     */
    fun searchIndexedCatalog(
        query: String,
        allProviders: List<MainAPI>,
        context: Context? = null
    ): List<SearchResponse> {
        val normQuery = ProviderModels.normalizeTitle(query)
        val cleanQuery = HubSingleCardMerger.cleanTitle(normQuery, null)
        if (normQuery.isBlank() && cleanQuery.isBlank()) return emptyList()

        val matched = mutableListOf<SearchResponse>()
        for (provider in allProviders) {
            val cap = ProviderCapabilityMatrix.getCapability(provider.name)
                ?: ProviderCapabilityMatrix.evaluate(provider)
            for (cat in cap.categories) {
                val cursorKey = cat.categoryUrl.ifBlank { cat.categoryName }
                val (cachedItems, _) = HubCatalogCache.getAllCachedProviderPages(provider.name, cursorKey, context)
                for (item in cachedItems) {
                    val normName = ProviderModels.normalizeTitle(item.name)
                    val cleanName = HubSingleCardMerger.cleanTitle(normName, null)
                    if ((normQuery.isNotBlank() && normName.contains(normQuery)) ||
                        (cleanQuery.isNotBlank() && cleanName.contains(cleanQuery))
                    ) {
                        matched.add(item)
                    }
                }
            }
        }
        return if (matched.isEmpty()) emptyList() else HubSingleCardMerger.mergeCandidates(matched)
    }

    /**
     * Crawls an incremental batch of pages safely for background/offline indexing.
     * Iterates over all categories of each provider, maintaining fine-grained
     * per-category cursors and storing results in isolated provider disk cache.
     * Guaranteed:
     * - Never overwrites unified catalog cache entries.
     * - Consumes existing getProviderCatalog entries to avoid redundant network queries.
     * - Only advances cursor upon verified, successful page retrieval.
     * - Handles hasNext == false to avoid querying beyond catalog end.
     */
    suspend fun crawlBatch(
        providers: List<MainAPI>,
        context: Context? = null,
        maxPagesPerProvider: Int = 1
    ): Int {
        var totalCrawled = 0
        val settings = HubSettingsManager.snapshot()
        val maxConcurrency = settings.maxConcurrency.coerceIn(1, 6)
        val fetchTimeoutMs = settings.effectiveCategoryTimeoutMs.coerceAtLeast(DEFAULT_PROVIDER_FETCH_TIMEOUT_MS)
        val semaphore = Semaphore(maxConcurrency)

        coroutineScope {
            val jobs = providers.map { provider ->
                async {
                    val cap = ProviderCapabilityMatrix.getCapability(provider.name)
                        ?: ProviderCapabilityMatrix.evaluate(provider)
                    if (!cap.isEnabled || !cap.hasMainPage || cap.categories.isEmpty()) return@async 0

                    var crawledForProvider = 0
                    for (cat in cap.categories) {
                        val cursorKey = cat.categoryUrl.ifBlank { cat.categoryName }
                        if (HubCatalogCache.isCrawlerFinished(context, provider.name, cursorKey)) {
                            continue
                        }
                        val currentCursor = HubCatalogCache.getCrawlerCursor(context, provider.name, cursorKey)
                        var p = currentCursor
                        var pagesProcessed = 0
                        while (pagesProcessed < maxPagesPerProvider) {
                            // Check if this page was already fetched and cached in getProviderCatalog
                            val alreadyCached = HubCatalogCache.getProviderCatalog(provider.name, cursorKey, p, context)
                            if (alreadyCached != null) {
                                pagesProcessed++
                                val (cachedItems, cachedHasNext) = alreadyCached
                                if (cachedItems.isNotEmpty()) {
                                    HubSingleCardMerger.mergeCandidates(cachedItems, provider.name)
                                    crawledForProvider += cachedItems.size
                                }
                                if (!cachedHasNext) {
                                    HubCatalogCache.setCrawlerFinished(context, provider.name, cursorKey, true)
                                    break
                                }
                                p++
                                HubCatalogCache.saveCrawlerCursor(context, provider.name, cursorKey, p)
                                HubCatalogCache.recordCrawlerRetry(context, provider.name, cursorKey, 0)
                                continue
                            }

                            pagesProcessed++
                            var pageSuccess = false
                            var hasNext = true
                            var fetchedItems: List<SearchResponse> = emptyList()

                            semaphore.withPermit {
                                try {
                                    withTimeoutOrNull(fetchTimeoutMs) {
                                        val req = MainPageRequest(
                                            name = cat.categoryName,
                                            data = cat.categoryUrl,
                                            horizontalImages = false
                                        )
                                        val resp = provider.getMainPage(p, req)
                                        val list = resp?.items?.flatMap { it.list } ?: emptyList()
                                        hasNext = resp?.hasNext == true
                                        if (list.isNotEmpty()) {
                                            fetchedItems = list
                                            pageSuccess = true
                                        } else if (resp != null && !hasNext) {
                                            pageSuccess = true
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    DiagnosticLogger.log(
                                        provider = provider.name,
                                        stage = DiagnosticStage.HOMEPAGE,
                                        category = DiagnosticCategory.SOURCE_DISCOVERY,
                                        message = "Crawler error on page $p for ${cat.categoryName}: ${e.message}"
                                    )
                                }
                            }

                            if (pageSuccess) {
                                if (fetchedItems.isNotEmpty()) {
                                    // Isolated into provider catalog cache namespace (never overwrites unified category cache!)
                                    HubCatalogCache.putProviderCatalog(provider.name, cursorKey, p, fetchedItems, hasNext, context)
                                    // Register discovered items into canonical single card merger registry
                                    HubSingleCardMerger.mergeCandidates(fetchedItems, provider.name)
                                    crawledForProvider += fetchedItems.size
                                }
                                if (!hasNext) {
                                    HubCatalogCache.setCrawlerFinished(context, provider.name, cursorKey, true)
                                    break
                                }
                                p++
                                HubCatalogCache.saveCrawlerCursor(context, provider.name, cursorKey, p)
                                HubCatalogCache.recordCrawlerRetry(context, provider.name, cursorKey, 0)
                            } else {
                                // Timeout or error: DO NOT advance cursor!
                                val retry = HubCatalogCache.getCrawlerRetry(context, provider.name, cursorKey) + 1
                                HubCatalogCache.recordCrawlerRetry(context, provider.name, cursorKey, retry)
                                break
                            }
                        }
                    }
                    crawledForProvider
                }
            }
            totalCrawled = jobs.awaitAll().sum()
        }
        return totalCrawled
    }

    private var prewarmJob: Job? = null

    @Volatile
    private var lastPrewarmTimestampMs: Long = 0L

    /**
     * Non-blocking, opt-in background pre-warming of catalog pages.
     * - Disabled by default (`enableBackgroundCrawler == false`) so Android TV startup and UI navigation never lag.
     * - Even when enabled in Settings, enforces a 30-minute session cooldown and a 15-second startup delay,
     *   crawling at most 4 providers per pass.
     */
    fun triggerBackgroundPrewarm(providers: List<MainAPI>, context: Context? = null) {
        if (!HubSettingsManager.snapshot().enableBackgroundCrawler) return
        if (prewarmJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (now - lastPrewarmTimestampMs < MIN_PREWARM_INTERVAL_MS) return
        lastPrewarmTimestampMs = now

        prewarmJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(15_000L)
                crawlBatch(providers.take(4), context, maxPagesPerProvider = 1)
            } catch (_: Throwable) {}
        }
    }
}
