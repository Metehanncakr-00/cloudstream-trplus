package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnifiedCatalogEngineTest {

    private val hub = CloudStreamHub()

    @Before
    fun setUp() {
        HubCatalogCache.clear()
        HubCatalogCache.resetCrawlerCursors(null)
        HubMediaRegistry.clear()
    }

    @Test
    fun testProviderCapabilityTruthfulReporting() {
        val fullProvider = object : MainAPI() {
            override var name = "FullCatalogProvider"
            override var mainUrl = "https://full.com"
            override val hasMainPage = true
            override val supportedTypes = setOf(TvType.Movie)
            override val mainPage = listOf(
                MainPageData("Popüler Filmler", "https://full.com/populer"),
                MainPageData("Yeşilçam Klasikleri", "https://full.com/yesilcam")
            )
        }

        val searchOnlyProvider = object : MainAPI() {
            override var name = "SearchOnlyProvider"
            override var mainUrl = "https://search.com"
            override val hasMainPage = false
            override val hasQuickSearch = true
            override val supportedTypes = setOf(TvType.TvSeries)
        }

        val noListingProvider = object : MainAPI() {
            override var name = "NoListingProvider"
            override var mainUrl = "https://nolisting.com"
            override val hasMainPage = false
            override val hasQuickSearch = false
            override val supportedTypes = setOf(TvType.Documentary)
        }

        val capFull = ProviderCapabilityMatrix.evaluate(fullProvider, isEnabled = true)
        val capSearch = ProviderCapabilityMatrix.evaluate(searchOnlyProvider, isEnabled = true)
        val capNoListing = ProviderCapabilityMatrix.evaluate(noListingProvider, isEnabled = true)
        val capDisabled = ProviderCapabilityMatrix.evaluate(fullProvider, isEnabled = false)

        assertEquals(CatalogCoverageStatus.FULL_CATALOG, capFull.coverageStatus)
        assertEquals(2, capFull.categories.size)
        assertEquals(UnifiedCategories.YESILCAM, capFull.categories.first { it.categoryName == "Yeşilçam Klasikleri" }.unifiedCategory)

        assertEquals(CatalogCoverageStatus.SEARCH_ONLY, capSearch.coverageStatus)
        assertEquals(CatalogCoverageStatus.NO_LISTING, capNoListing.coverageStatus)
        assertEquals(CatalogCoverageStatus.DISABLED, capDisabled.coverageStatus)
    }

    @Test
    fun testCategoryMappingCoversCanonicalCategories() {
        assertEquals(
            UnifiedCategories.YESILCAM,
            ProviderCapabilityMatrix.mapCategoryNameToUnified("Yeşilçam Arşivi", setOf(TvType.Movie))
        )
        assertEquals(
            UnifiedCategories.NOSTALGIC_TURKISH_SERIES,
            ProviderCapabilityMatrix.mapCategoryNameToUnified("90lar Nostalji Dizileri", setOf(TvType.TvSeries))
        )
        assertEquals(
            UnifiedCategories.TURKISH_SERIES,
            ProviderCapabilityMatrix.mapCategoryNameToUnified("Türk Dizileri", setOf(TvType.TvSeries))
        )
        assertEquals(
            UnifiedCategories.ANIME,
            ProviderCapabilityMatrix.mapCategoryNameToUnified("Yeni Çıkan Animeler", setOf(TvType.Anime))
        )
        assertEquals(
            UnifiedCategories.DOCUMENTARIES,
            ProviderCapabilityMatrix.mapCategoryNameToUnified("Doğa Belgeselleri", setOf(TvType.Documentary))
        )
        assertEquals(
            UnifiedCategories.TR_DUB,
            ProviderCapabilityMatrix.mapCategoryNameToUnified("Türkçe Dublaj Filmler", setOf(TvType.Movie))
        )
        assertEquals(
            UnifiedCategories.TR_SUB,
            ProviderCapabilityMatrix.mapCategoryNameToUnified("Türkçe Altyazılı Diziler", setOf(TvType.TvSeries))
        )
    }

    @Test
    fun testCatalogTtlCacheStoresAndClearsCorrectly() {
        val dummyItem = hub.newMovieSearchResponse("Test Film", "https://test.com/film", TvType.Movie)
        val dummyList = listOf(dummyItem)

        // Cache miss initially
        val initial = HubCatalogCache.get("Filmler", 1)
        assertEquals(null, initial)

        // Put in cache
        HubCatalogCache.put("Filmler", 1, dummyList, hasNext = true)

        // Cache hit
        val cached = HubCatalogCache.get("Filmler", 1)
        assertNotNull(cached)
        assertEquals(1, cached?.first?.size)
        assertEquals("Test Film", cached?.first?.first()?.name)
        assertTrue(cached?.second == true)

        // Clear cache
        HubCatalogCache.clear()
        val afterClear = HubCatalogCache.get("Filmler", 1)
        assertEquals(null, afterClear)
    }

    @Test
    fun testProviderTimeoutPreservesOtherResults() = runTest {
        val workingItem = hub.newMovieSearchResponse("Hızlı Film", "https://working.com/film", TvType.Movie)

        val workingProvider = object : MainAPI() {
            override var name = "WorkingProvider"
            override var mainUrl = "https://working.com"
            override val hasMainPage = true
            override val supportedTypes = setOf(TvType.Movie)
            override val mainPage = listOf(MainPageData("Filmler", "https://working.com/filmler"))

            override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
                return newHomePageResponse(request.name, listOf(workingItem), hasNext = false)
            }
        }

        val failingProvider = object : MainAPI() {
            override var name = "FailingProvider"
            override var mainUrl = "https://failing.com"
            override val hasMainPage = true
            override val supportedTypes = setOf(TvType.Movie)
            override val mainPage = listOf(MainPageData("Filmler", "https://failing.com/filmler"))

            override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
                throw RuntimeException("Network down")
            }
        }

        ProviderCapabilityMatrix.evaluate(workingProvider)
        ProviderCapabilityMatrix.evaluate(failingProvider)

        val (items, _) = UnifiedCatalogEngine.getCategoryPage(
            categoryName = "Filmler",
            page = 1,
            allProviders = listOf(workingProvider, failingProvider),
            tmdbFetch = null
        )

        // Failure in failingProvider must NOT abort the whole request
        assertEquals(1, items.size)
        assertEquals("Hızlı Film", items.first().name)
    }

    @Test
    fun testTwentySixProvidersQueriedWithConcurrencyThree() = runTest {
        // Concurrency = 3 must query ALL 26 providers without truncating to 4 or 3
        val queriedProviders = java.util.Collections.synchronizedList(mutableListOf<String>())
        val providers = (1..26).map { i ->
            val movie = hub.newMovieSearchResponse("Film $i", "https://prov$i.com/film$i", TvType.Movie)
            object : MainAPI() {
                override var name = "Provider_$i"
                override var mainUrl = "https://prov$i.com"
                override val hasMainPage = true
                override val supportedTypes = setOf(TvType.Movie)
                override val mainPage = listOf(MainPageData("Filmler", "https://prov$i.com/filmler"))

                override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
                    queriedProviders.add(name)
                    return newHomePageResponse(request.name, listOf(movie), hasNext = false)
                }
            }
        }

        // Register capabilities
        providers.forEach { ProviderCapabilityMatrix.evaluate(it) }

        val (items, _) = UnifiedCatalogEngine.getCategoryPage(
            categoryName = "Filmler",
            page = 1,
            allProviders = providers,
            tmdbFetch = null
        )

        assertEquals("All 26 providers must eventually be queried when concurrency=3", 26, queriedProviders.size)
        assertEquals("All 26 items must be present in the merged catalog", 26, items.size)
        assertTrue(
            "Item from the 26th provider must be discoverable",
            items.any { it.name == "Film 26" }
        )
    }

    @Test
    fun testAllContentIncludesAllCategoriesForProvider() {
        val multiCatProvider = object : MainAPI() {
            override var name = "MultiCatProvider"
            override var mainUrl = "https://multi.com"
            override val hasMainPage = true
            override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Documentary)
            override val mainPage = listOf(
                MainPageData("Filmler", "https://multi.com/filmler"),
                MainPageData("Diziler", "https://multi.com/diziler"),
                MainPageData("Belgeseller", "https://multi.com/belgesel")
            )
        }

        ProviderCapabilityMatrix.evaluate(multiCatProvider)

        val allContentProviders = ProviderCapabilityMatrix.getProvidersForCategory(
            UnifiedCategories.ALL_CONTENT,
            listOf(multiCatProvider)
        )

        assertEquals("ALL_CONTENT must yield all 3 categories of the multi-category provider", 3, allContentProviders.size)
        val categoryUrls = allContentProviders.mapNotNull { it.second?.categoryUrl }.toSet()
        assertTrue(categoryUrls.contains("https://multi.com/filmler"))
        assertTrue(categoryUrls.contains("https://multi.com/diziler"))
        assertTrue(categoryUrls.contains("https://multi.com/belgesel"))
    }

    @Test
    fun testConcurrentCrawlerAndHomepageCacheIsolation() = runTest {
        // P0-A Regression: Background crawler must not overwrite unified catalog cache
        val homeMovie = hub.newMovieSearchResponse("Unified Home Film", "https://home.com/film", TvType.Movie)
        val crawlMovie = hub.newMovieSearchResponse("Raw Crawled Film", "https://prov.com/film", TvType.Movie)

        // Seed unified catalog cache
        HubCatalogCache.putUnified("Filmler", 1, listOf(homeMovie), hasNext = true)

        val testProvider = object : MainAPI() {
            override var name = "TestCrawlProvider"
            override var mainUrl = "https://prov.com"
            override val hasMainPage = true
            override val supportedTypes = setOf(TvType.Movie)
            override val mainPage = listOf(MainPageData("Filmler", "https://prov.com/filmler"))

            override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
                return newHomePageResponse(request.name, listOf(crawlMovie), hasNext = false)
            }
        }
        ProviderCapabilityMatrix.evaluate(testProvider)

        // Run crawlBatch which indexes raw provider content
        val crawledCount = UnifiedCatalogEngine.crawlBatch(listOf(testProvider), context = null, maxPagesPerProvider = 1)
        assertEquals("Crawler should process 1 item", 1, crawledCount)

        // Verify that Unified cache entry was NOT overwritten
        val unifiedCached = HubCatalogCache.getUnified("Filmler", 1)
        assertNotNull("Unified cache must still exist", unifiedCached)
        assertEquals("Unified cache must retain the homepage item, not the crawled raw item", "Unified Home Film", unifiedCached?.first?.firstOrNull()?.name)

        // Verify that crawler entry exists in its isolated provider namespace
        val provCached = HubCatalogCache.getProviderCatalog("TestCrawlProvider", "https://prov.com/filmler", 1)
        assertNotNull("Provider raw cache must exist in its isolated namespace", provCached)
        assertEquals("Raw Crawled Film", provCached?.first?.firstOrNull()?.name)
    }

    @Test
    fun testCrawlerDoesNotAdvanceCursorOnTimeoutOrError() = runTest {
        // P0-B Regression: Failed or timed out page fetches must NOT advance the crawler cursor
        var failRequest = true
        val item1 = hub.newMovieSearchResponse("Page 1 Item", "https://prov.com/1", TvType.Movie)

        val flappyProvider = object : MainAPI() {
            override var name = "FlappyProvider"
            override var mainUrl = "https://flappy.com"
            override val hasMainPage = true
            override val supportedTypes = setOf(TvType.Movie)
            override val mainPage = listOf(MainPageData("Filmler", "https://flappy.com/filmler"))

            override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
                if (failRequest) {
                    throw RuntimeException("Simulated network timeout/error")
                }
                return newHomePageResponse(request.name, listOf(item1), hasNext = false)
            }
        }
        ProviderCapabilityMatrix.evaluate(flappyProvider)

        val cursorKey = "https://flappy.com/filmler"
        HubCatalogCache.saveCrawlerCursor(null, flappyProvider.name, cursorKey, 1)

        // 1. Crawler run when network fails
        UnifiedCatalogEngine.crawlBatch(listOf(flappyProvider), context = null, maxPagesPerProvider = 1)

        // Cursor MUST remain at page 1, retry count incremented, NOT marked finished
        assertEquals("Cursor must not advance on failure", 1, HubCatalogCache.getCrawlerCursor(null, flappyProvider.name, cursorKey))
        assertEquals("Retry count must increment", 1, HubCatalogCache.getCrawlerRetry(null, flappyProvider.name, cursorKey))
        assertFalse("Must not be marked finished on error", HubCatalogCache.isCrawlerFinished(null, flappyProvider.name, cursorKey))

        // 2. Crawler run when network recovers
        failRequest = false
        UnifiedCatalogEngine.crawlBatch(listOf(flappyProvider), context = null, maxPagesPerProvider = 1)

        // Since hasNext is false, crawler should mark finished
        assertTrue("Crawler must be marked finished when hasNext is false", HubCatalogCache.isCrawlerFinished(null, flappyProvider.name, cursorKey))
    }

    @Test
    fun testUnifiedCatalogPreservesOverOneHundredFiftyItemsWithoutDataLoss() {
        // P1-D Regression: 150 item limit caused truncation/data loss; now bounded up to 300 per slice
        val twoHundredItems = (1..200).map { i ->
            hub.newMovieSearchResponse("Film $i", "https://site.com/film$i", TvType.Movie)
        }

        HubCatalogCache.putUnified("Katalog", 1, twoHundredItems, hasNext = true)

        val cached = HubCatalogCache.getUnified("Katalog", 1)
        assertNotNull("Cached unified page must exist", cached)
        assertEquals("All 200 items must be preserved without truncation at 150", 200, cached?.first?.size)
    }

    @Test
    fun testZeroDataLossOverflowPaginationFor301_500_And1000ItemsAcrossInitialCacheHitContinuationAndRestart() = runTest {
        // P0-1 Regression (4th Independent Audit):
        // Verify 0 data loss and 0 duplicates for 301, 500, and 1000 items across:
        // 1. Initial load + continuation pages (page = 1, 2, 3, 4...)
        // 2. Cache hit
        // 3. Cold restart disk rehydration
        val targetSizes = listOf(301, 500, 1000)

        for (totalCount in targetSizes) {
            val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "hub_p01_${totalCount}_" + System.nanoTime())
            tempDir.mkdirs()
            HubCatalogCache.customDiskCacheDir = tempDir
            HubCatalogCache.clear()
            HubCatalogCache.resetCrawlerCursors(null)
            HubMediaRegistry.clear()

            try {
                var networkFetchCalls = 0
                val generatedItems = (1..totalCount).map { i ->
                    hub.newMovieSearchResponse("Unique Film $i ($totalCount)", "https://largeprov.com/$totalCount/film-$i", TvType.Movie) {
                        year = 2000 + (i % 25)
                    }
                }

                val largeProvider = object : MainAPI() {
                    override var name = "LargeCatalogProvider_$totalCount"
                    override var mainUrl = "https://largeprov.com"
                    override val hasMainPage = true
                    override val supportedTypes = setOf(TvType.Movie)
                    override val mainPage = listOf(MainPageData("Filmler", "https://largeprov.com/$totalCount/filmler"))

                    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
                        networkFetchCalls++
                        return if (page == 1) {
                            newHomePageResponse(request.name, generatedItems, hasNext = false)
                        } else {
                            newHomePageResponse(request.name, emptyList(), hasNext = false)
                        }
                    }
                }
                ProviderCapabilityMatrix.evaluate(largeProvider)

                // Phase 1: Initial load + continuation pages
                val initialCollected = mutableListOf<SearchResponse>()
                var currentPage = 1
                var hasNextPage = true
                while (hasNextPage && currentPage <= 10) {
                    val (pageItems, hasNext) = UnifiedCatalogEngine.getCategoryPage(
                        categoryName = UnifiedCategories.MOVIES,
                        page = currentPage,
                        allProviders = listOf(largeProvider),
                        tmdbFetch = null
                    )
                    if (currentPage == 1) {
                        assertEquals("Page 1 must be sliced to exactly 300 items for totalCount=$totalCount", 300, pageItems.size)
                        assertTrue("Page 1 must report hasNext=true when totalCount=$totalCount > 300", hasNext)
                    }
                    initialCollected.addAll(pageItems)
                    hasNextPage = hasNext
                    currentPage++
                }

                val expectedPages = (totalCount + 299) / 300
                assertEquals("Must traverse exactly $expectedPages pages for $totalCount items", expectedPages, currentPage - 1)
                assertEquals("Zero data loss on initial traversal for $totalCount items", totalCount, initialCollected.size)
                assertEquals("Zero duplicates on initial traversal for $totalCount items", totalCount, initialCollected.map { it.url }.toSet().size)
                assertEquals("Upstream provider must only be queried once", 1, networkFetchCalls)

                // Phase 2: Memory Cache Hit
                val (cacheHitPage1, cacheHitHasNext1) = UnifiedCatalogEngine.getCategoryPage(
                    categoryName = UnifiedCategories.MOVIES,
                    page = 1,
                    allProviders = listOf(largeProvider),
                    tmdbFetch = null
                )
                assertEquals(300, cacheHitPage1.size)
                assertTrue(cacheHitHasNext1)
                assertEquals("Memory cache hit must not trigger network fetch", 1, networkFetchCalls)

                // Phase 3: Cold Restart (clear in-memory caches, reload all pages from disk)
                HubCatalogCache.clear()
                HubMediaRegistry.clear()

                val restartCollected = mutableListOf<SearchResponse>()
                var restartPage = 1
                var restartHasNext = true
                while (restartHasNext && restartPage <= 10) {
                    val (pageItems, hasNext) = UnifiedCatalogEngine.getCategoryPage(
                        categoryName = UnifiedCategories.MOVIES,
                        page = restartPage,
                        allProviders = listOf(largeProvider),
                        tmdbFetch = null
                    )
                    restartCollected.addAll(pageItems)
                    restartHasNext = hasNext
                    restartPage++
                }

                assertEquals("Cold restart must not trigger network fetch when disk cache is valid", 1, networkFetchCalls)
                assertEquals("Zero data loss after cold restart for $totalCount items", totalCount, restartCollected.size)
                assertEquals("Zero duplicates after cold restart for $totalCount items", totalCount, restartCollected.map { it.url }.toSet().size)
            } finally {
                tempDir.deleteRecursively()
                HubCatalogCache.customDiskCacheDir = null
            }
        }
    }

    @Test
    fun testCrawlBatchPutProviderCatalogIsConsumedByHomepageAndSearchWithoutRedundantNetworkQueries() = runTest {
        // P0-2 Regression (4th Independent Audit):
        // Verify that putProviderCatalog() written by crawlBatch() is actively consumed by:
        // 1. UnifiedCatalogEngine.getCategoryPage() (homepage) without redundant network calls
        // 2. Subsequent crawlBatch() passes expand homepage catalog coverage (page 1 -> page 1+2)
        // 3. UnifiedCatalogEngine.searchIndexedCatalog() / CloudStreamHub.search()
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "hub_p02_test_" + System.nanoTime())
        tempDir.mkdirs()
        HubCatalogCache.customDiskCacheDir = tempDir
        HubCatalogCache.clear()
        HubCatalogCache.resetCrawlerCursors(null)
        HubMediaRegistry.clear()

        try {
            var page1NetworkCalls = 0
            var page2NetworkCalls = 0

            val page1Items = listOf(
                hub.newMovieSearchResponse("Kış Uykusu", "https://cineprov.com/kis-uykusu", TvType.Movie) { year = 2014 },
                hub.newMovieSearchResponse("Ahlat Ağacı", "https://cineprov.com/ahlat-agaci", TvType.Movie) { year = 2018 }
            )
            val page2Items = listOf(
                hub.newMovieSearchResponse("Kuru Otlar Üstüne", "https://cineprov.com/kuru-otlar", TvType.Movie) { year = 2023 },
                hub.newMovieSearchResponse("Bir Zamanlar Anadolu'da", "https://cineprov.com/anadolu", TvType.Movie) { year = 2011 }
            )

            val cineProvider = object : MainAPI() {
                override var name = "CineProvider"
                override var mainUrl = "https://cineprov.com"
                override val hasMainPage = true
                override val supportedTypes = setOf(TvType.Movie)
                override val mainPage = listOf(MainPageData("Filmler", "https://cineprov.com/filmler"))

                override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
                    return when (page) {
                        1 -> {
                            page1NetworkCalls++
                            newHomePageResponse(request.name, page1Items, hasNext = true)
                        }
                        2 -> {
                            page2NetworkCalls++
                            newHomePageResponse(request.name, page2Items, hasNext = false)
                        }
                        else -> newHomePageResponse(request.name, emptyList(), hasNext = false)
                    }
                }
            }
            ProviderCapabilityMatrix.evaluate(cineProvider)

            // Step 1: crawlBatch crawls Page 1 into putProviderCatalog
            val crawledBatch1 = UnifiedCatalogEngine.crawlBatch(listOf(cineProvider), context = null, maxPagesPerProvider = 1)
            assertEquals(2, crawledBatch1)
            assertEquals("Crawler must fetch page 1 once", 1, page1NetworkCalls)
            assertEquals("Crawler must not fetch page 2 yet", 0, page2NetworkCalls)

            // Step 2: Homepage (getCategoryPage) must consume getProviderCatalog(page=1) WITHOUT calling cineProvider.getMainPage(1) again!
            val (homeAfterBatch1, _) = UnifiedCatalogEngine.getCategoryPage(
                categoryName = UnifiedCategories.MOVIES,
                page = 1,
                allProviders = listOf(cineProvider),
                tmdbFetch = null
            )
            assertEquals("Homepage must return the 2 crawled items from page 1", 2, homeAfterBatch1.size)
            assertEquals("Homepage must consume getProviderCatalog without re-querying page 1 over network", 1, page1NetworkCalls)

            // Step 3: Next crawlBatch crawls Page 2 into putProviderCatalog
            val crawledBatch2 = UnifiedCatalogEngine.crawlBatch(listOf(cineProvider), context = null, maxPagesPerProvider = 1)
            assertEquals(2, crawledBatch2)
            assertEquals("Page 1 must still have only 1 network call", 1, page1NetworkCalls)
            assertEquals("Crawler must fetch page 2 once", 1, page2NetworkCalls)

            // Step 4: Homepage (getCategoryPage) must automatically expand coverage with newly crawled Page 2 items (now 4 items) with 0 new network calls!
            val (homeAfterBatch2, _) = UnifiedCatalogEngine.getCategoryPage(
                categoryName = UnifiedCategories.MOVIES,
                page = 1,
                allProviders = listOf(cineProvider),
                tmdbFetch = null
            )
            assertEquals("Homepage catalog coverage must expand from 2 to 4 items as crawling progresses", 4, homeAfterBatch2.size)
            assertTrue(homeAfterBatch2.any { it.name == "Kuru Otlar Üstüne" })
            assertTrue(homeAfterBatch2.any { it.name == "Bir Zamanlar Anadolu'da" })
            assertEquals("No redundant network calls for page 1", 1, page1NetworkCalls)
            assertEquals("No redundant network calls for page 2", 1, page2NetworkCalls)

            // Step 5: Search must also consume getProviderCatalog via searchIndexedCatalog
            val searchHits = UnifiedCatalogEngine.searchIndexedCatalog("Anadolu", listOf(cineProvider))
            assertEquals("Search must find crawled page 2 item from getProviderCatalog", 1, searchHits.size)
            assertEquals("Bir Zamanlar Anadolu'da", searchHits.first().name)
        } finally {
            tempDir.deleteRecursively()
            HubCatalogCache.customDiskCacheDir = null
        }
    }
}

