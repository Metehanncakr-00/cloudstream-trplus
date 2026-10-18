package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.launch

class CloudStreamHubTest {
    private val hub = CloudStreamHub()

    @Test fun officeIdentityRejectsAmbiguousTiesAndKeepsKnownYear() {
        fun office(url: String, releaseYear: Int?) = hub.newTvSeriesSearchResponse("The Office", url, TvType.TvSeries) { year = releaseYear }
        val us = office("https://fixture/us", 2005)
        val uk = office("https://fixture/uk", 2001)
        assertSame(us, HubMatchingEngine.findConfidentMatch(listOf(uk, us), listOf("The Office"), 2005, false))
        assertNull(HubMatchingEngine.findConfidentMatch(listOf(uk), listOf("The Office"), 2005, false))
        assertNull(HubMatchingEngine.findConfidentMatch(listOf(us, uk), listOf("The Office"), null, false))
        val missing = listOf(office(us.url, null), office(uk.url, null))
        assertNull(HubMatchingEngine.findConfidentMatch(missing, listOf("The Office"), 2005, false))
        assertNull(HubMatchingEngine.findConfidentMatch(missing.reversed(), listOf("The Office"), 2005, false))
        assertNotNull(HubMatchingEngine.findConfidentMatch(listOf(us, us.copy()), listOf("The Office"), 2005, false))
    }

    @Test
    fun testTurkishSeriesDiscoveryUsesOriginCountryAndTvDetails() {
        val row = hub.mainPage.single { it.name == "Türk Dizileri" }
        val request = java.net.URI(row.data)
        val parameters = request.rawQuery.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        }

        assertEquals("/3/discover/tv", request.path)
        assertEquals("TR", parameters["with_origin_country"])
        assertEquals("tr-TR", parameters["language"])
        assertEquals("popularity.desc", parameters["sort_by"])

        // Discover responses omit media_type; the series name must select TV details.
        val result = hub.parseTmdbItem(TmdbItem(id = 123, name = "Türk Dizisi"))

        assertTrue(result is TvSeriesSearchResponse)
        assertEquals(TvType.TvSeries, result?.type)
        assertEquals("/3/tv/123", result?.url?.let { java.net.URI(it).path })
    }

    @Test
    fun testTorrentioKeepsAtMostThreeLinksPerQuality() {
        val qualities = listOf(
            Qualities.P2160.value,
            Qualities.P1080.value,
            Qualities.P720.value
        )
        val links = qualities.flatMap { quality ->
            (1..4).map { index ->
                ExtractorLink(
                    source = "Torrentio",
                    name = "Option $index",
                    url = "https://example.invalid/$quality/$index",
                    referer = "",
                    quality = quality,
                    type = ExtractorLinkType.VIDEO
                )
            }
        }

        val retained = com.cloudstream.tr.core.resolvers.TorrentioResolver.keepTopThreePerQuality(links)

        assertEquals(9, retained.size)
        qualities.forEach { quality ->
            assertEquals(3, retained.count { it.quality == quality })
        }
    }

    @Test
    fun testBundledProvidersAreAvailableToAggregator() {
        val providerNames = CloudStreamProviderRegistryAdapter
            .getRegisteredProviders(excludeName = hub.name)
            .map { it.name }
            .toSet()

        val bundledProviders = setOf(
            "FilmMakinesi",
            "HDFilmCehennemi",
            "SinemaCX",
            "SezonlukDizi",
            "StarTV",
            "DiziIzle",
            "KultFilmler",
            "Dizilla",
            "DiziYou",
            "HDFilmDelisi",
            "JetFilmIzle",
            "DiziKorea",
            "YesilCamTv",
            "FullHDFilmizlesene"
        )

        assertTrue("All bundled providers should be registered", providerNames.containsAll(bundledProviders))
    }

    @Test
    fun testParseTmdbPage() {
        val stream = javaClass.classLoader?.getResourceAsStream("tmdb_trending.json")
        assertNotNull("tmdb_trending.json fixture missing", stream)

        val json = stream!!.bufferedReader().use { it.readText() }
        val response = AppUtils.parseJson<TmdbPageResponse>(json)
        assertNotNull(response)
        assertEquals(2, response.results?.size)

        val item1 = hub.parseTmdbItem(response.results!![0]) as? MovieSearchResponse
        assertNotNull(item1)
        assertEquals("Geleceğe Dönüş", item1?.name)
        assertEquals(1985, item1?.year)
        assertTrue(item1?.url?.contains("/movie/105") == true)

        val item2 = hub.parseTmdbItem(response.results!![1]) as? TvSeriesSearchResponse
        assertNotNull(item2)
        assertEquals("Game of Thrones", item2?.name)
        assertEquals(2011, item2?.year)
        assertTrue(item2?.url?.contains("/tv/1399") == true)
    }

    @Test
    fun testHubMatchingEngineExactAndConfident() {
        val cand1 = hub.newMovieSearchResponse("Inception", "https://site.com/1", TvType.Movie) {
            this.year = 2010
        }
        val cand2 = hub.newMovieSearchResponse("Inception: The Beginning", "https://site.com/2", TvType.Movie) {
            this.year = 2015
        }
        val cand3 = hub.newMovieSearchResponse("Interstellar", "https://site.com/3", TvType.Movie) {
            this.year = 2014
        }

        val match1 = HubMatchingEngine.findConfidentMatch(listOf(cand1, cand2, cand3), listOf("Inception"), 2010, isMovie = true)
        assertNotNull(match1)
        assertEquals("Inception", match1?.name)

        // Test rejecting completely unrelated title (NEVER returns first result!)
        val match2 = HubMatchingEngine.findConfidentMatch(listOf(cand1, cand2, cand3), listOf("Matrix"), 1999, isMovie = true)
        assertNull("Unrelated title must NOT match first candidate!", match2)
    }

    @Test
    fun testFederatedProviderSearchPrefersQuickSearch() = kotlinx.coroutines.test.runTest {
        val expected = hub.newMovieSearchResponse("Saplantı", "https://site.com/saplantı", TvType.Movie)
        var quickSearchCalled = false
        var pagedSearchCalled = false
        val provider = object : MainAPI() {
            override var name = "QuickSearchProvider"
            override var mainUrl = "https://site.com"
            override val supportedTypes = setOf(TvType.Movie)

            override suspend fun quickSearch(query: String): List<SearchResponse> {
                quickSearchCalled = true
                assertEquals("Saplantı", query)
                return listOf(expected)
            }

            override suspend fun search(query: String, page: Int): SearchResponseList {
                pagedSearchCalled = true
                throw AssertionError("Paged search should not be used when quickSearch is implemented")
            }
        }

        val results = HubMatchingEngine.searchProvider(provider, "Saplantı")

        assertEquals(listOf(expected), results)
        assertTrue(quickSearchCalled)
        assertTrue(!pagedSearchCalled)
    }

    @Test
    fun testFederatedProviderSearchFallsBackToFirstPagedSearchPage() = kotlinx.coroutines.test.runTest {
        val expected = hub.newMovieSearchResponse("Saplantı", "https://site.com/saplantı", TvType.Movie)
        var requestedPage: Int? = null
        val provider = object : MainAPI() {
            override var name = "PagedSearchProvider"
            override var mainUrl = "https://site.com"
            override val supportedTypes = setOf(TvType.Movie)

            override suspend fun quickSearch(query: String): List<SearchResponse>? {
                throw NotImplementedError("quickSearch is not implemented")
            }

            override suspend fun search(query: String, page: Int): SearchResponseList {
                assertEquals("Saplantı", query)
                requestedPage = page
                return newSearchResponseList(listOf(expected), hasNext = false)
            }
        }

        val results = HubMatchingEngine.searchProvider(provider, "Saplantı")

        assertEquals(listOf(expected), results)
        assertEquals(1, requestedPage)
    }

    @Test
    fun testFederatedProviderSearchFallsBackWhenQuickSearchReturnsNull() = kotlinx.coroutines.test.runTest {
        val expected = hub.newMovieSearchResponse("Saplantı", "https://site.com/saplantı", TvType.Movie)
        var pagedSearchCalled = false
        val provider = object : MainAPI() {
            override var name = "NullableQuickSearchProvider"
            override var mainUrl = "https://site.com"
            override val supportedTypes = setOf(TvType.Movie)

            override suspend fun quickSearch(query: String): List<SearchResponse>? = null

            override suspend fun search(query: String, page: Int): SearchResponseList {
                pagedSearchCalled = true
                assertEquals(1, page)
                return newSearchResponseList(listOf(expected), hasNext = false)
            }
        }

        val results = HubMatchingEngine.searchProvider(provider, "Saplantı")

        assertEquals(listOf(expected), results)
        assertTrue(pagedSearchCalled)
    }

    @Test
    fun testFederatedProviderSearchSkipsProviderWhenBothSearchMethodsAreUnsupported() = kotlinx.coroutines.test.runTest {
        val provider = object : MainAPI() {
            override var name = "UnsupportedSearchProvider"
            override var mainUrl = "https://site.com"
            override val supportedTypes = setOf(TvType.Movie)
        }

        assertTrue(HubMatchingEngine.searchProvider(provider, "Saplantı").isNullOrEmpty())
    }

    @Test
    fun testOriginalTitleSearchRunsOnlyAfterLocalizedSearchHasNoConfidentMatch() = kotlinx.coroutines.test.runTest {
        val originalTitleMatch = hub.newMovieSearchResponse("Original Title", "https://site.com/original", TvType.Movie) {
            this.year = 2020
        }
        val searchedQueries = mutableListOf<String>()

        val matched = HubMatchingEngine.findConfidentMatchFromSearches(
            searchQueries = listOf("Localized Title", "Original Title"),
            targetTitles = listOf("Localized Title", "Original Title"),
            targetYear = 2020,
            isMovie = true,
            search = { query ->
                searchedQueries.add(query)
                if (query == "Localized Title") {
                    listOf(hub.newMovieSearchResponse("Unrelated Result", "https://site.com/unrelated", TvType.Movie) {
                        this.year = 2020
                    })
                } else {
                    listOf(originalTitleMatch)
                }
            }
        )

        assertEquals(listOf("Localized Title", "Original Title"), searchedQueries)
        assertEquals("Original Title", matched?.name)
    }

    @Test
    fun testConfidentLocalizedSearchSkipsOriginalTitleQuery() = kotlinx.coroutines.test.runTest {
        val localizedMatch = hub.newMovieSearchResponse("Localized Title", "https://site.com/localized", TvType.Movie) {
            this.year = 2020
        }
        val searchedQueries = mutableListOf<String>()

        val matched = HubMatchingEngine.findConfidentMatchFromSearches(
            searchQueries = listOf("Localized Title", "Original Title"),
            targetTitles = listOf("Localized Title", "Original Title"),
            targetYear = 2020,
            isMovie = true,
            search = { query ->
                searchedQueries.add(query)
                listOf(localizedMatch)
            }
        )

        assertEquals(listOf("Localized Title"), searchedQueries)
        assertEquals("Localized Title", matched?.name)
    }

    @Test
    fun testOriginalTitleSearchFallbackContinuesAfterLocalizedSearchFailure() = kotlinx.coroutines.test.runTest {
        val originalTitleMatch = hub.newMovieSearchResponse("Original Title", "https://site.com/original", TvType.Movie) {
            this.year = 2020
        }
        val searchedQueries = mutableListOf<String>()

        val matched = HubMatchingEngine.findConfidentMatchFromSearches(
            searchQueries = listOf("Localized Title", "Original Title"),
            targetTitles = listOf("Localized Title", "Original Title"),
            targetYear = 2020,
            isMovie = true,
            search = { query ->
                searchedQueries.add(query)
                if (query == "Localized Title") throw IllegalStateException("localized search unavailable")
                listOf(originalTitleMatch)
            }
        )

        assertEquals(listOf("Localized Title", "Original Title"), searchedQueries)
        assertEquals("Original Title", matched?.name)
    }

    @Test
    fun testConfidentSearchFallbackPropagatesCancellation() = kotlinx.coroutines.test.runTest {
        val cancellation = CancellationException("search cancelled")
        val searchedQueries = mutableListOf<String>()

        try {
            HubMatchingEngine.findConfidentMatchFromSearches(
                searchQueries = listOf("Localized Title", "Original Title"),
                targetTitles = listOf("Localized Title", "Original Title"),
                isMovie = true,
                search = { query ->
                    searchedQueries.add(query)
                    throw cancellation
                }
            )
            throw AssertionError("Search cancellation must propagate")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }

        assertEquals(listOf("Localized Title"), searchedQueries)
    }

    @Test
    fun testHubMatchingEngineYearTolerance() {
        val cand = hub.newMovieSearchResponse("Dune Part Two", "https://site.com/dune", TvType.Movie) {
            this.year = 2024
        }
        // +/- 1 year tolerance
        val match = HubMatchingEngine.findConfidentMatch(listOf(cand), listOf("Dune: Part Two"), 2023, isMovie = true)
        assertNotNull(match)
        assertEquals("Dune Part Two", match?.name)

        // Large year mismatch penalty
        val mismatch = HubMatchingEngine.findConfidentMatch(listOf(cand), listOf("Dune: Part Two"), 1984, isMovie = true)
        assertNull("Decade-mismatched year must be rejected", mismatch)
    }

    @Test
    fun testHubMatchingEngineMatchesWholeTitlePhraseAndRejectsWrongYear() {
        val matchingYear = hub.newMovieSearchResponse(
            "Saplantı - Obsession izle",
            "https://site.com/obsession-2026",
            TvType.Movie
        ) {
            this.year = 2026
        }
        val wrongYear = hub.newMovieSearchResponse(
            "Saplantı - Obsession izle",
            "https://site.com/obsession-2024",
            TvType.Movie
        ) {
            this.year = 2024
        }
        val yearMissing = hub.newMovieSearchResponse(
            "Saplantı - Obsession izle",
            "https://site.com/obsession-no-year",
            TvType.Movie
        )

        val match = HubMatchingEngine.findConfidentMatch(
            candidates = listOf(matchingYear),
            targetTitles = listOf("Saplantı"),
            targetYear = 2026,
            isMovie = true
        )
        val mismatch = HubMatchingEngine.findConfidentMatch(
            candidates = listOf(wrongYear),
            targetTitles = listOf("Saplantı"),
            targetYear = 2026,
            isMovie = true
        )
        val matchWithoutProviderYear = HubMatchingEngine.findConfidentMatch(
            candidates = listOf(yearMissing),
            targetTitles = listOf("Saplantı"),
            targetYear = 2026,
            isMovie = true
        )

        assertEquals("Saplantı - Obsession izle", match?.name)
        assertEquals("Saplantı - Obsession izle", matchWithoutProviderYear?.name)
        assertNull("Wrong-year title must remain rejected", mismatch)
    }

    @Test
    fun testHubMatchingEngineRejectsShorterOrVeryShortContainedTitles() {
        val lionKing = hub.newMovieSearchResponse("The Lion King", "https://site.com/lion-king", TvType.Movie)
        val itChapterTwo = hub.newMovieSearchResponse("It Chapter Two", "https://site.com/it-chapter-two", TvType.Movie)
        val matrixReloaded = hub.newMovieSearchResponse("The Matrix Reloaded", "https://site.com/matrix-reloaded", TvType.Movie)
        val matrixReloadedWithAlias = hub.newMovieSearchResponse(
            "The Matrix Reloaded - The Matrix",
            "https://site.com/matrix-reloaded-alias",
            TvType.Movie
        )
        val matrixOriginal = hub.newMovieSearchResponse("The Matrix", "https://site.com/matrix", TvType.Movie)

        val shortContainedTitle = HubMatchingEngine.findConfidentMatch(
            candidates = listOf(lionKing),
            targetTitles = listOf("Lion"),
            isMovie = true
        )
        val veryShortContainedTitle = HubMatchingEngine.findConfidentMatch(
            candidates = listOf(itChapterTwo),
            targetTitles = listOf("It"),
            isMovie = true
        )
        val sequelWithoutYear = HubMatchingEngine.findConfidentMatch(
            candidates = listOf(matrixReloaded),
            targetTitles = listOf("Matrix"),
            isMovie = true
        )
        val sequelWithOriginalAliasWithoutYear = HubMatchingEngine.findConfidentMatch(
            candidates = listOf(matrixReloadedWithAlias),
            targetTitles = listOf("The Matrix"),
            isMovie = true
        )
        val originalForSequelQueryWithoutYear = HubMatchingEngine.findConfidentMatch(
            candidates = listOf(matrixOriginal),
            targetTitles = listOf("The Matrix Reloaded"),
            isMovie = true
        )

        assertNull("A shorter candidate phrase must not receive a strong containment score", shortContainedTitle)
        assertNull("A short single-word title must not receive a strong containment score", veryShortContainedTitle)
        assertNull("A sequel without year metadata must not match the original title", sequelWithoutYear)
        assertNull("A sequel must not match via a repeated original-title alias", sequelWithOriginalAliasWithoutYear)
        assertNull("An original film must not match a sequel query", originalForSequelQueryWithoutYear)
    }

    @Test
    fun testHubMatchingEngineMediaTypeRejection() {
        val movieCand = hub.newMovieSearchResponse("Breaking Bad", "https://site.com/bb-movie", TvType.Movie) {
            this.year = 2008
        }
        val seriesCand = hub.newTvSeriesSearchResponse("Breaking Bad", "https://site.com/bb-series", TvType.TvSeries) {
            this.year = 2008
        }

        // Seeking a TV series must match seriesCand, not movieCand
        val matchSeries = HubMatchingEngine.findConfidentMatch(listOf(movieCand, seriesCand), listOf("Breaking Bad"), 2008, isMovie = false)
        assertNotNull(matchSeries)
        assertTrue(matchSeries is TvSeriesSearchResponse)

        // Seeking a Movie must match movieCand, not seriesCand
        val matchMovie = HubMatchingEngine.findConfidentMatch(listOf(seriesCand, movieCand), listOf("Breaking Bad"), 2008, isMovie = true)
        assertNotNull(matchMovie)
        assertTrue(matchMovie is MovieSearchResponse)
    }

    @Test
    fun testBoundedChannelBurstBehavior() = kotlinx.coroutines.test.runTest {
        val channel = kotlinx.coroutines.channels.Channel<com.lagradost.cloudstream3.utils.ExtractorLink>(capacity = 16)
        var successCount = 0
        var droppedCount = 0

        // Rapidly push 50 links without an active consumer to simulate burst
        for (i in 1..50) {
            val dummyLink = com.lagradost.cloudstream3.utils.ExtractorLink(
                source = "TestProvider",
                name = "Link $i",
                url = "https://example.invalid/stream$i.m3u8",
                referer = "",
                quality = com.lagradost.cloudstream3.utils.Qualities.P1080.value,
                type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
            )
            val result = channel.trySend(dummyLink)
            if (result.isSuccess) {
                successCount++
            } else {
                droppedCount++
            }
        }

        channel.close()

        // With capacity 16, exactly 16 links should be buffered and 34 dropped
        assertEquals("Capacity 16 should accept exactly 16 burst links", 16, successCount)
        assertEquals("34 links should be dropped to prevent memory exhaustion", 34, droppedCount)

        // Verify that consumer reads exactly 16 items
        var consumedCount = 0
        for (link in channel) {
            consumedCount++
        }
        assertEquals("Consumer should receive exactly 16 items from channel", 16, consumedCount)
    }

    @Test
    fun testChannelBurstWithValidLinkAtEnd() = kotlinx.coroutines.test.runTest {
        val channel = kotlinx.coroutines.channels.Channel<com.lagradost.cloudstream3.utils.ExtractorLink>(capacity = 16)
        var rawReceived = 0
        var channelOverflowDropped = 0
        val consumedLinks = mutableListOf<String>()

        // Launch consumer concurrently using backgroundScope
        val consumerJob = backgroundScope.launch {
            for (link in channel) {
                consumedLinks.add(link.url)
            }
        }

        // Producer pushes 49 junk links + 50th valid link
        for (i in 1..50) {
            val url = if (i == 50) "https://cdn.example.com/valid-master.m3u8" else "https://cdn.example.com/junk$i.html"
            val link = com.lagradost.cloudstream3.utils.ExtractorLink(
                source = "TestProvider",
                name = "Source $i",
                url = url,
                referer = "",
                quality = com.lagradost.cloudstream3.utils.Qualities.P1080.value,
                type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
            )
            rawReceived++
            val res = channel.trySend(link)
            if (!res.isSuccess) {
                channelOverflowDropped++
            }
            // Small yield to simulate cooperative coroutine scheduling between producer and consumer
            kotlinx.coroutines.yield()
        }

        channel.close()
        consumerJob.join()

        assertEquals(50, rawReceived)
        // With cooperative progressive consumption, all 50 items are processed without drop
        assertEquals("No items should be dropped when consumer yields and consumes progressively", 0, channelOverflowDropped)
        assertEquals(50, consumedLinks.size)
        assertTrue("50th valid link must reach consumer", consumedLinks.contains("https://cdn.example.com/valid-master.m3u8"))
    }

    @Test
    fun testTmdbJsonParserDeterministicallyParsesPageAndDetailWithoutReflection() = kotlinx.coroutines.test.runTest {
        // v44 Regression: TmdbJsonParser must deterministically parse TMDB page and detail responses
        // even when unknown fields are present, without relying on Kotlin metadata reflection.
        val stream = javaClass.classLoader?.getResourceAsStream("tmdb_trending.json")
        assertNotNull("tmdb_trending.json fixture missing", stream)
        val json = stream!!.bufferedReader().use { it.readText() }

        val pageResp = TmdbJsonParser.parsePage(json)
        assertNotNull(pageResp)
        assertEquals(2, pageResp?.results?.size)

        val item1 = hub.parseTmdbItem(pageResp!!.results!![0]) as? MovieSearchResponse
        assertNotNull(item1)
        assertEquals("Geleceğe Dönüş", item1?.name)
        assertEquals(1985, item1?.year)

        val detailJson = """
            {
              "id": 105,
              "title": "Geleceğe Dönüş",
              "original_title": "Back to the Future",
              "overview": "Marty McFly 1955 yılına gider.",
              "poster_path": "/bttf.jpg",
              "release_date": "1985-07-03",
              "vote_average": 8.3,
              "unknown_future_field": {"nested": true},
              "genres": [{"id": 12, "name": "Macera"}],
              "external_ids": {"imdb_id": "tt0088763"}
            }
        """.trimIndent()
        val detailResp = TmdbJsonParser.parseDetail(detailJson)
        assertNotNull(detailResp)
        assertEquals(105, detailResp?.id)
        assertEquals("tt0088763", detailResp?.externalIds?.imdbId)
        val loadResp = hub.parseTmdbDetail(detailResp!!, "https://api.themoviedb.org/3/movie/105")
        assertNotNull(loadResp)
        assertEquals("Geleceğe Dönüş", loadResp?.name)
    }

    @Test
    fun testTrDubAndTrSubHomePagesUseProviderCategoryUrisInsteadOfFakeTmdbUrls() {
        // v44 Regression: Türkçe Dublaj and Türkçe Altyazı must NOT pull generic English TMDB discover lists.
        val trDubRow = hub.mainPage.single { it.name == UnifiedCategories.TR_DUB }
        val trSubRow = hub.mainPage.single { it.name == UnifiedCategories.TR_SUB }

        assertEquals("hub://category/tr_dub", trDubRow.data)
        assertEquals("hub://category/tr_sub", trSubRow.data)
        assertTrue("TR_DUB must not point to TMDB", !trDubRow.data.contains("themoviedb.org"))
        assertTrue("TR_SUB must not point to TMDB", !trSubRow.data.contains("themoviedb.org"))
    }

    @Test
    fun testGetMainPageReturnsNonEmptyForVisibleCategoryAndEmptyForHiddenCategory() = kotlinx.coroutines.test.runTest {
        // v44 Regression:
        // 1. CloudStreamHub.getMainPage() returns non-empty results for a visible category (backed by unified cache/provider)
        // 2. Hidden category requests return emptyList() and record HIDDEN_BY_USER in diagnostics
        HubCatalogCache.clear()
        HubCatalogDiagnostics.clear()

        val seededMovie = hub.newMovieSearchResponse("Kış Uykusu", "https://prov.com/kis-uykusu", TvType.Movie) {
            year = 2014
        }
        HubCatalogCache.putUnified(UnifiedCategories.MOVIES, 1, listOf(seededMovie), hasNext = true)

        val visibleReq = com.lagradost.cloudstream3.MainPageRequest(
            name = UnifiedCategories.MOVIES,
            data = "hub://category/movies",
            horizontalImages = false
        )
        val visibleResp = hub.getMainPage(1, visibleReq)
        val visibleItems = visibleResp.items.flatMap { it.list }
        assertEquals("Visible category must return non-empty catalog items", 1, visibleItems.size)
        assertEquals("Kış Uykusu", visibleItems.first().name)

        // Legacy v41 category label ("Popüler Filmler") must also resolve to canonical "Filmler"
        val legacyReq = com.lagradost.cloudstream3.MainPageRequest(
            name = "Popüler Filmler",
            data = "hub://category/movies",
            horizontalImages = false
        )
        val legacyResp = hub.getMainPage(1, legacyReq)
        assertEquals("Legacy host category label must resolve and return catalog items", 1, legacyResp.items.flatMap { it.list }.size)

        // Unknown/hidden category request must return empty and record diagnostics
        val hiddenReq = com.lagradost.cloudstream3.MainPageRequest(
            name = "Gizli Kategori",
            data = "hub://category/hidden",
            horizontalImages = false
        )
        val hiddenResp = hub.getMainPage(1, hiddenReq)
        assertTrue("Hidden category request must return empty", hiddenResp.items.flatMap { it.list }.isEmpty())
        assertTrue(
            "Hidden category must record HIDDEN_BY_USER in diagnostics",
            HubCatalogDiagnostics.report().contains("HIDDEN_BY_USER")
        )
    }
}

