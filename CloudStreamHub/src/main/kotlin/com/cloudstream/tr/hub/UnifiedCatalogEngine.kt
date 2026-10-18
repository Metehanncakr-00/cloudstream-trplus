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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Orchestrates unified catalog aggregation across TMDB and all active providers.
 * Enforces:
 * - Bounded concurrency & low RAM usage
 * - TTL caching & incremental indexing with zero-loss overflow pagination
 * - Consumption of crawled provider catalog pages (getProviderCatalog) in homepage & search
 * - Fallback to live federated search
 * - Truthful coverage and capability matrix
 */
object UnifiedCatalogEngine {

    private const val PROVIDER_FETCH_TIMEOUT_MS = 8000L

    /**
     * Fetches unified page items for a given category across ALL eligible providers.
     * Concurrency is strictly bounded by maxConcurrency semaphore, ensuring TV stability
     * while giving full coverage without dropping any providers or truncating >300 items.
     */
    suspend fun getCategoryPage(
        categoryName: String,
        page: Int,
        allProviders: List<MainAPI>,
        tmdbFetch: (suspend () -> List<SearchResponse>)? = null,
        context: Context? = null
    ): Pair<List<SearchResponse>, Boolean> {
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
                        return HubCatalogCache.putUnified(
                            category = categoryName,
                            page = 1,
                            items = remerged,
                            hasNext = unifiedLastHasNext || crawledHasNext,
                            context = context
                        )
                    }
                }
            }
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

        coroutineScope {
            // 2. Fetch primary TMDB items if available
            val tmdbDeferred = async {
                if (tmdbFetch != null) {
                    try {
                        withTimeoutOrNull(PROVIDER_FETCH_TIMEOUT_MS) { tmdbFetch() } ?: emptyList()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        emptyList()
                    }
                } else emptyList()
            }

            // 3. Fetch from all matching providers for this category (consuming getProviderCatalog first)
            val maxConcurrency = HubSettingsManager.snapshot().maxConcurrency.coerceIn(1, 10)
            val semaphore = Semaphore(maxConcurrency)

            val providerDeferreds = providerTargets.map { (provider, categoryInfo) ->
                async {
                    if (categoryInfo == null) return@async emptyList<SearchResponse>()
                    val cursorKey = categoryInfo.categoryUrl.ifBlank { categoryInfo.categoryName }

                    // Consume already-crawled provider catalog pages before making any network request
                    if (upstreamPage == 1) {
                        val (cachedProvItems, cachedProvHasNext) =
                            HubCatalogCache.getAllCachedProviderPages(provider.name, cursorKey, context)
                        if (cachedProvItems.isNotEmpty()) {
                            if (cachedProvHasNext) hasMorePages = true
                            return@async cachedProvItems
                        }
                    } else {
                        val cachedPage = HubCatalogCache.getProviderCatalog(provider.name, cursorKey, upstreamPage, context)
                        if (cachedPage != null) {
                            if (cachedPage.second) hasMorePages = true
                            return@async cachedPage.first
                        }
                    }

                    semaphore.withPermit {
                        try {
                            withTimeoutOrNull(PROVIDER_FETCH_TIMEOUT_MS) {
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
                            } ?: emptyList()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            DiagnosticLogger.log(
                                provider = provider.name,
                                stage = DiagnosticStage.HOMEPAGE,
                                category = DiagnosticCategory.SOURCE_DISCOVERY,
                                message = "Provider catalog fetch failed for category '$categoryName': ${e.message}"
                            )
                            emptyList()
                        }
                    }
                }
            }

            val tmdbItems = tmdbDeferred.await()
            val providerBatches = providerDeferreds.awaitAll()

            collected.addAll(tmdbItems)
            for (batch in providerBatches) {
                collected.addAll(batch)
            }
        }

        if (tmdbFetch != null && collected.isNotEmpty()) {
            hasMorePages = true
        }

        HubCatalogCache.saveLastUpstreamPage(context, categoryName, upstreamPage)

        // 4. Merge items into single cards with high confidence
        val merged = HubSingleCardMerger.mergeCandidates(collected)

        // 5. Store in unified TTL cache (memory + persistent disk) with zero-loss overflow slicing
        return HubCatalogCache.putUnified(categoryName, page, merged, hasMorePages, context)
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
        val maxConcurrency = HubSettingsManager.snapshot().maxConcurrency.coerceIn(1, 6)
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
                                    withTimeoutOrNull(PROVIDER_FETCH_TIMEOUT_MS) {
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

    /**
     * Non-blocking background pre-warming of catalog pages across all providers.
     * Guaranteed safe for Android TV lifecycle; never executes concurrently with another prewarm pass.
     */
    fun triggerBackgroundPrewarm(providers: List<MainAPI>, context: Context? = null) {
        if (prewarmJob?.isActive == true) return
        prewarmJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                crawlBatch(providers, context, maxPagesPerProvider = 1)
            } catch (_: Throwable) {}
        }
    }
}
