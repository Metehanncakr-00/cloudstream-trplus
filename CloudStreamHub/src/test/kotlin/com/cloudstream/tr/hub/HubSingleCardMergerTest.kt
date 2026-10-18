package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HubSingleCardMergerTest {

    private val hub = CloudStreamHub()

    @Before
    fun setUp() {
        HubMediaRegistry.clear()
        HubCatalogCache.clear()
    }

    @Test
    fun sameFilmAcrossThreeProvidersMergesIntoSingleCard() {
        HubMediaRegistry.register(
            CanonicalMedia(
                id = "tmdb:movie:157336",
                title = "Yıldızlararası",
                originalTitle = "Interstellar",
                year = 2014,
                isMovie = true,
                tvType = TvType.Movie,
                posterUrl = "https://img.prov1.com/poster.jpg",
                score = "8.7",
                tmdbId = 157336,
                providerReferences = emptyList()
            )
        )

        val cand1 = hub.newMovieSearchResponse("Interstellar", "https://prov1.com/interstellar", TvType.Movie) {
            year = 2014
            posterUrl = "https://img.prov1.com/poster.jpg"
        }
        val cand2 = hub.newMovieSearchResponse("Interstellar (2014) Türkçe Dublaj", "https://prov2.com/interstellar-dub", TvType.Movie) {
            year = 2014
        }
        val cand3 = hub.newMovieSearchResponse("Yıldızlararası", "https://prov3.com/yildizlararasi", TvType.Movie) {
            year = 2014
        }

        val merged = HubSingleCardMerger.mergeCandidates(listOf(cand1, cand2, cand3))
        assertEquals("Same film across 3 providers must merge into exactly 1 card", 1, merged.size)
        assertEquals("Interstellar", merged.first().name)
        val movieRes = merged.first() as MovieSearchResponse
        assertEquals(2014, movieRes.year)

        val canonical = HubSingleCardMerger.getCanonicalMedia(merged.first())
        assertNotNull("Canonical media must be registered in HubMediaRegistry", canonical)
        assertEquals("Must retain all 3 provider references", 3, canonical?.providerReferences?.size)
        assertTrue(canonical?.providerReferences?.any { it.url == "https://prov1.com/interstellar" } == true)
        assertTrue(canonical?.providerReferences?.any { it.url == "https://prov2.com/interstellar-dub" } == true)
        assertTrue(canonical?.providerReferences?.any { it.url == "https://prov3.com/yildizlararasi" } == true)
    }

    @Test
    fun testThreeProvidersSingleCardMultiSourceResolutionAndFaultTolerance() = kotlinx.coroutines.test.runTest {
        val cand1 = hub.newMovieSearchResponse("Interstellar", "https://prov1.com/interstellar", TvType.Movie) { year = 2014 }
        val cand2 = hub.newMovieSearchResponse("Interstellar 2014", "https://prov2.com/interstellar", TvType.Movie) { year = 2014 }
        val cand3 = hub.newMovieSearchResponse("Interstellar Dublaj", "https://prov3.com/interstellar", TvType.Movie) { year = 2014 }

        val merged = HubSingleCardMerger.mergeCandidates(listOf(cand1, cand2, cand3))
        assertEquals(1, merged.size)
        val card = merged.first()

        val canonical = HubSingleCardMerger.getCanonicalMedia(card)
        assertNotNull(canonical)
        assertEquals(3, canonical?.providerReferences?.size)

        // Mock 3 providers
        val p1 = object : MainAPI() {
            override var name = "Prov1"
            override var mainUrl = "https://prov1.com"
            override suspend fun load(url: String) = hub.newMovieLoadResponse("Interstellar", url, TvType.Movie, "prov1_payload")
            override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit, callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit): Boolean {
                callback(com.lagradost.cloudstream3.utils.newExtractorLink("Prov1", "Prov1 1080p", "https://stream.prov1.com/video.m3u8", type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8))
                return true
            }
        }

        val p2 = object : MainAPI() {
            override var name = "Prov2"
            override var mainUrl = "https://prov2.com"
            override suspend fun load(url: String) = hub.newMovieLoadResponse("Interstellar", url, TvType.Movie, "prov2_payload")
            override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit, callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit): Boolean {
                callback(com.lagradost.cloudstream3.utils.newExtractorLink("Prov2", "Prov2 720p", "https://stream.prov2.com/video.m3u8", type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8))
                // Duplicate link from another source with same URL should be handled
                callback(com.lagradost.cloudstream3.utils.newExtractorLink("Prov2 Mirror", "Prov2 720p Mirror", "https://stream.prov2.com/video.m3u8", type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8))
                return true
            }
        }

        val p3 = object : MainAPI() {
            override var name = "Prov3"
            override var mainUrl = "https://prov3.com"
            override suspend fun load(url: String): com.lagradost.cloudstream3.LoadResponse? {
                throw RuntimeException("Prov3 connection timed out")
            }
        }

        // Simulating loadLinks aggregation using canonical provider references
        val collectedLinks = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
        val seenUrls = mutableSetOf<String>()
        val providerMap = mapOf("Prov1" to p1, "Prov2" to p2, "Prov3" to p3)

        canonical?.providerReferences?.forEach { ref ->
            val prov = providerMap[ref.providerName] ?: return@forEach
            try {
                val loadRes = prov.load(ref.url)
                if (loadRes != null) {
                    prov.loadLinks(loadRes.url, false, {}) { link ->
                        if (seenUrls.add(link.url)) {
                            collectedLinks.add(link)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Provider 3 failure must NOT abort Provider 1 and 2!
            }
        }

        // Verify:
        // 1. Links from Provider 1 and Provider 2 are preserved
        assertEquals(2, collectedLinks.size)
        assertTrue(collectedLinks.any { it.source == "Prov1" })
        assertTrue(collectedLinks.any { it.source == "Prov2" })
        // 2. Duplicate link URL from Prov2 was deduplicated (seenUrls prevented 2nd https://stream.prov2.com/video.m3u8)
        assertEquals(2, seenUrls.size)
    }

    @Test
    fun testDiskCacheRestoresCanonicalMediaAndAllProviderReferencesAcrossRestart() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "hub_test_cache_" + System.currentTimeMillis())
        tempDir.mkdirs()
        HubCatalogCache.customDiskCacheDir = tempDir
        try {
            val cand1 = hub.newMovieSearchResponse("Interstellar", "https://prov1.com/interstellar", TvType.Movie) { year = 2014 }
            val cand2 = hub.newMovieSearchResponse("Interstellar 2014", "https://prov2.com/interstellar", TvType.Movie) { year = 2014 }
            val cand3 = hub.newMovieSearchResponse("Interstellar Dublaj", "https://prov3.com/interstellar", TvType.Movie) { year = 2014 }

            val merged = HubSingleCardMerger.mergeCandidates(listOf(cand1, cand2, cand3))
            assertEquals(1, merged.size)

            // Persist to disk cache
            HubCatalogCache.put("Filmler", 1, merged, false)

            // Simulate app restart / cold boot: clear in-memory cache and persistent registry to test catalog cache rehydration
            HubCatalogCache.clear()
            HubMediaRegistry.clearAll()

            assertNull("Registry must be empty before cache rehydration", HubMediaRegistry.getByUrl("https://prov1.com/interstellar"))

            // Reload from disk cache
            val reloaded = HubCatalogCache.get("Filmler", 1)
            assertNotNull("Disk cache hit must succeed", reloaded)
            assertEquals(1, reloaded?.first?.size)

            // Verify that CanonicalMedia was rehydrated into HubMediaRegistry with all 3 provider references!
            val restored = HubMediaRegistry.getByUrl("https://prov1.com/interstellar")
            assertNotNull("CanonicalMedia must be automatically restored in HubMediaRegistry upon cache load", restored)
            assertEquals(3, restored?.providerReferences?.size)
            assertTrue(restored?.providerReferences?.any { it.url == "https://prov1.com/interstellar" } == true)
            assertTrue(restored?.providerReferences?.any { it.url == "https://prov2.com/interstellar" } == true)
            assertTrue(restored?.providerReferences?.any { it.url == "https://prov3.com/interstellar" } == true)
        } finally {
            tempDir.deleteRecursively()
            HubCatalogCache.customDiskCacheDir = null
        }
    }

    @Test
    fun differentReleaseYearsRemainSeparateCards() {
        val film1984 = hub.newMovieSearchResponse("Dune", "https://prov1.com/dune-1984", TvType.Movie) {
            year = 1984
        }
        val film2021 = hub.newMovieSearchResponse("Dune", "https://prov2.com/dune-2021", TvType.Movie) {
            year = 2021
        }

        val merged = HubSingleCardMerger.mergeCandidates(listOf(film1984, film2021))
        assertEquals("Different release years / remakes must NEVER merge", 2, merged.size)
    }

    @Test
    fun movieAndSeriesWithSameTitleNeverMerge() {
        val movie = hub.newMovieSearchResponse("Breaking Bad", "https://site.com/bb-movie", TvType.Movie) {
            year = 2008
        }
        val series = hub.newTvSeriesSearchResponse("Breaking Bad", "https://site.com/bb-series", TvType.TvSeries) {
            year = 2008
        }

        val merged = HubSingleCardMerger.mergeCandidates(listOf(movie, series))
        assertEquals("Movie and Series must remain completely separate cards", 2, merged.size)
    }

    @Test
    fun animeMovieAndAnimeSeriesNeverMerge() {
        val animeMovie = hub.newMovieSearchResponse("Demon Slayer: Mugen Train", "https://site.com/mugen-movie", TvType.AnimeMovie) {
            year = 2020
        }
        val animeSeries = hub.newAnimeSearchResponse("Demon Slayer: Mugen Train", "https://site.com/mugen-tv", TvType.Anime) {
            year = 2020
        }

        val merged = HubSingleCardMerger.mergeCandidates(listOf(animeMovie, animeSeries))
        assertEquals("Anime Movie and Anime TV Series must never merge", 2, merged.size)
    }

    @Test
    fun seriesEpisodeMatchingStrictlySeparatesSpecials() {
        val epSpecial = hub.newEpisode("payload_s0_e1") {
            name = "Special: Behind the Scenes"
            season = 0
            episode = 1
        }
        val epRegular = hub.newEpisode("payload_s1_e1") {
            name = "1. Sezon 1. Bölüm"
            season = 1
            episode = 1
        }
        val episodes = listOf(epSpecial, epRegular)

        // Matching Season 1 Episode 1 must NOT match special
        val matchedRegular = HubSingleCardMerger.matchEpisode(episodes, targetSeason = 1, targetEpisode = 1)
        assertNotNull(matchedRegular)
        assertEquals("1. Sezon 1. Bölüm", matchedRegular?.name)
        assertEquals(1, matchedRegular?.season)

        // Matching Season 0 Episode 1 must NOT match regular episode
        val matchedSpecial = HubSingleCardMerger.matchEpisode(episodes, targetSeason = 0, targetEpisode = 1)
        assertNotNull(matchedSpecial)
        assertEquals("Special: Behind the Scenes", matchedSpecial?.name)
        assertEquals(0, matchedSpecial?.season)

        // Non-existent episode returns null
        val notFound = HubSingleCardMerger.matchEpisode(episodes, targetSeason = 2, targetEpisode = 5)
        assertNull(notFound)
    }

    @Test
    fun consecutiveReleaseYearsMustNeverMerge() {
        // P0-C Regression: Previously 1 year difference was tolerated (diff <= 1). Now strictly rejected.
        val film2021 = hub.newMovieSearchResponse("The Batman", "https://prov1.com/batman-2021", TvType.Movie) { year = 2021 }
        val film2022 = hub.newMovieSearchResponse("The Batman", "https://prov2.com/batman-2022", TvType.Movie) { year = 2022 }

        val merged = HubSingleCardMerger.mergeCandidates(listOf(film2021, film2022))
        assertEquals("Different consecutive release years must NEVER merge into a single card", 2, merged.size)
    }

    @Test
    fun sequelsAndNumberedPartsMustNeverMerge() {
        // P0-C Regression: Sequels and numbered parts must never merge
        val part1 = hub.newMovieSearchResponse("Dune", "https://prov1.com/dune", TvType.Movie) { year = 2021 }
        val part2 = hub.newMovieSearchResponse("Dune: Part Two", "https://prov2.com/dune-part-two", TvType.Movie) { year = 2024 }

        val mergedDune = HubSingleCardMerger.mergeCandidates(listOf(part1, part2))
        assertEquals("Dune and Dune Part Two must remain separate cards", 2, mergedDune.size)

        val spidey1 = hub.newMovieSearchResponse("Spider-Man", "https://prov1.com/spidey", TvType.Movie) { year = 2002 }
        val spidey2 = hub.newMovieSearchResponse("Spider-Man 2", "https://prov2.com/spidey-2", TvType.Movie) { year = 2004 }

        val mergedSpidey = HubSingleCardMerger.mergeCandidates(listOf(spidey1, spidey2))
        assertEquals("Spider-Man and Spider-Man 2 must remain separate cards", 2, mergedSpidey.size)
    }

    @Test
    fun testColdBootFavoritesDirectLoadRestoresAllProviders() {
        // P1-E Regression: Opening an item from Favorites / History after cold boot
        // must restore all provider references directly from canonical_media_registry_v4.json
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "hub_fav_test_" + System.currentTimeMillis())
        tempDir.mkdirs()
        HubCatalogCache.customDiskCacheDir = tempDir
        try {
            val cand1 = hub.newMovieSearchResponse("Matrix", "https://prov1.com/matrix", TvType.Movie) { year = 1999 }
            val cand2 = hub.newMovieSearchResponse("Matrix 1999", "https://prov2.com/matrix", TvType.Movie) { year = 1999 }
            val cand3 = hub.newMovieSearchResponse("Matrix Dublaj", "https://prov3.com/matrix", TvType.Movie) { year = 1999 }

            val merged = HubSingleCardMerger.mergeCandidates(listOf(cand1, cand2, cand3))
            assertEquals(1, merged.size)

            // Simulate device shutdown / process kill
            HubMediaRegistry.clear()
            HubCatalogCache.clear()

            // User opens Matrix directly from Favorites via provider URL:
            val restored = HubMediaRegistry.getByUrl("https://prov1.com/matrix")
            assertNotNull("Favorites cold boot direct lookup must restore CanonicalMedia from disk", restored)
            assertEquals("Matrix", restored?.title)
            assertEquals("Must have all 3 providers restored from persistent storage", 3, restored?.providerReferences?.size)
            assertTrue(restored?.providerReferences?.any { it.providerName == "Prov1" || it.url == "https://prov1.com/matrix" } == true)
            assertTrue(restored?.providerReferences?.any { it.providerName == "Prov2" || it.url == "https://prov2.com/matrix" } == true)
            assertTrue(restored?.providerReferences?.any { it.providerName == "Prov3" || it.url == "https://prov3.com/matrix" } == true)
        } finally {
            tempDir.deleteRecursively()
            HubCatalogCache.customDiskCacheDir = null
        }
    }

    @Test
    fun testIncrementalMultiProviderRegistrationsAcrossSeparateCrawlsPreserveAllReferencesAcrossRestartAndCacheEviction() {
        // P0-3 + EK-1 + EK-2 Regression (4th Independent Audit):
        // When 3 providers return the same movie at different times (3 separate mergeCandidates / crawl passes),
        // HubMediaRegistry.register() must merge providerReferences rather than overwriting prior providers.
        // Furthermore, persistent registry must survive HubCatalogCache eviction (cacheDir) and cold restart.
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "hub_p03_test_" + System.nanoTime())
        tempDir.mkdirs()
        HubCatalogCache.customDiskCacheDir = tempDir
        HubMediaRegistry.clear()
        HubCatalogCache.clear()

        try {
            val cand1 = hub.newMovieSearchResponse("Inception", "https://prov1.com/inception", TvType.Movie) {
                year = 2010
                posterUrl = "https://prov1.com/inception.jpg"
            }
            val cand2 = hub.newMovieSearchResponse("Inception (2010) Türkçe Dublaj", "https://prov2.com/inception-tr", TvType.Movie) {
                year = 2010
            }
            val cand3 = hub.newMovieSearchResponse("Inception 1080p Full HD", "https://prov3.com/inception-hd", TvType.Movie) {
                year = 2010
            }

            // Pass 1 (Time T1): Only Provider 1 is crawled
            HubSingleCardMerger.mergeCandidates(listOf(cand1), defaultProvider = "Prov1")
            val afterT1 = HubMediaRegistry.getByUrl("https://prov1.com/inception")
            assertNotNull(afterT1)
            assertEquals("After T1, 1 provider reference must be registered", 1, afterT1?.providerReferences?.size)

            // Pass 2 (Time T2): Only Provider 2 is crawled separately
            HubSingleCardMerger.mergeCandidates(listOf(cand2), defaultProvider = "Prov2")
            val afterT2 = HubMediaRegistry.getByUrl("https://prov1.com/inception")
            assertNotNull(afterT2)
            assertEquals("After T2, both Prov1 and Prov2 must be preserved (no overwrite)", 2, afterT2?.providerReferences?.size)

            // Pass 3 (Time T3): Only Provider 3 is crawled separately
            HubSingleCardMerger.mergeCandidates(listOf(cand3), defaultProvider = "Prov3")
            val afterT3 = HubMediaRegistry.getByUrl("https://prov1.com/inception")
            assertNotNull(afterT3)
            assertEquals("After T3, all 3 providers (Prov1, Prov2, Prov3) must be preserved in a single canonical record", 3, afterT3?.providerReferences?.size)

            // Verify all 3 URLs resolve to the exact same canonical ID
            val byUrl2 = HubMediaRegistry.getByUrl("https://prov2.com/inception-tr")
            val byUrl3 = HubMediaRegistry.getByUrl("https://prov3.com/inception-hd")
            assertEquals(afterT3?.id, byUrl2?.id)
            assertEquals(afterT3?.id, byUrl3?.id)

            // Simulate Android cacheDir eviction (wiping HubCatalogCache completely) + Cold Process Restart
            HubCatalogCache.clearAll()
            HubMediaRegistry.clear()

            // Verify persistent registry file lives in dedicated hub_persistent_registry_v4 directory and survived cacheDir wipe
            val persistentFile = HubMediaRegistry.getStorageFile(null)
            assertTrue("Persistent registry file must exist in dedicated persistent directory after cacheDir wipe", persistentFile.exists())

            // Simulate opening directly from Favorites after cold restart using Provider 1 URL
            val restoredFromFavorites = HubMediaRegistry.getByUrl("https://prov1.com/inception")
            assertNotNull("Favorites direct load after cold restart + cache eviction must succeed", restoredFromFavorites)
            assertEquals("All 3 incrementally crawled providers must survive cold restart and cache eviction", 3, restoredFromFavorites?.providerReferences?.size)
            val restoredUrls = restoredFromFavorites?.providerReferences?.map { it.url }?.toSet().orEmpty()
            assertTrue(restoredUrls.contains("https://prov1.com/inception"))
            assertTrue(restoredUrls.contains("https://prov2.com/inception-tr"))
            assertTrue(restoredUrls.contains("https://prov3.com/inception-hd"))
        } finally {
            tempDir.deleteRecursively()
            HubCatalogCache.customDiskCacheDir = null
        }
    }
}
