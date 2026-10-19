package com.cloudstream.tr.one

import com.cloudstream.tr.one.adapters.*
import com.cloudstream.tr.one.catalog.TRCatalogCache
import com.cloudstream.tr.one.catalog.TRUnifiedCatalog
import com.cloudstream.tr.one.dedup.TRCanonicalEngine
import com.cloudstream.tr.one.models.CanonicalEpisodePayload
import com.cloudstream.tr.one.models.CanonicalPayload
import com.cloudstream.tr.one.models.ProviderListing
import com.cloudstream.tr.one.playback.TRPlaybackDispatcher
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TRPlusOneTest {

    private val api = TRPlusOne()

    @Before
    fun setup() {
        TRCatalogCache.clear()
    }

    @After
    fun tearDown() {
        TRCatalogCache.clear()
    }

    @Test
    fun testProductIdentity() {
        assertEquals("TR+ ONE", api.name)
        assertEquals("https://trplus.one.invalid", api.mainUrl)
        assertEquals("tr", api.lang)
        assertTrue(api.hasMainPage)
        assertTrue(api.hasQuickSearch)
        assertTrue(api.supportedTypes.contains(TvType.Movie))
        assertTrue(api.supportedTypes.contains(TvType.TvSeries))
        assertTrue(api.supportedTypes.contains(TvType.Anime))
    }

    @Test
    fun testTitleNormalizationAndNoiseRemoval() {
        val raw1 = "Dune: Çöl Gezegeni Bölüm İki (2024) Türkçe Dublaj Full HD 1080p İzle"
        val norm1 = TRCanonicalEngine.normalizeTitle(raw1)
        assertEquals("dune çöl gezegeni bölüm iki", norm1)

        val raw2 = "Breaking Bad 1. Sezon 1. Bölüm Türkçe Altyazılı Tek Parça izle"
        val norm2 = TRCanonicalEngine.normalizeTitle(raw2)
        assertEquals("breaking bad 1 sezon 1 bölüm", norm2)

        val raw3 = "Attack on Titan Final Season Filmi Sinema Çekimi izle full"
        val norm3 = TRCanonicalEngine.normalizeTitle(raw3)
        assertEquals("attack on titan final season", norm3)
    }

    @Test
    fun testSingleCardDeduplicationSameMovieDifferentSources() {
        // Kontrollü test verileriyle tek kart birleştirme doğrulaması
        val listingFromHdfc = ProviderListing(
            providerId = "HDFilmCehennemi",
            providerUrl = "https://www.hdfilmcehennemi.nl/interstellar-izle/",
            title = "Yıldızlararası - Interstellar (2014) Türkçe Dublaj",
            mediaType = TvType.Movie,
            year = 2014,
            posterUrl = "https://cdn.site.com/posters/interstellar1.jpg"
        )

        val listingFromSecondSource = ProviderListing(
            providerId = "SinemaCX",
            providerUrl = "https://sinemacc.com/interstellar-2014",
            title = "Yıldızlararası (Interstellar) 1080p Full HD İzle",
            mediaType = TvType.Movie,
            year = 2014,
            posterUrl = "https://cdn.site.com/posters/interstellar2.jpg"
        )

        val merged = TRCanonicalEngine.mergeListings(listOf(listingFromHdfc, listingFromSecondSource))

        // İki farklı sağlayıcıdaki aynı film tek kart altında birleşmeli
        assertEquals(1, merged.size)
        val canonical = merged[0]
        assertEquals(TvType.Movie, canonical.mediaType)
        assertEquals(2014, canonical.year)
        // Her iki sağlayıcının da linki korunmalı!
        assertEquals(2, canonical.listings.size)
        assertTrue(canonical.listings.any { it.providerId == "HDFilmCehennemi" })
        assertTrue(canonical.listings.any { it.providerId == "SinemaCX" })
    }

    @Test
    fun testRemakesWithDifferentYearsPreservedSeparately() {
        // Dune 1984 ve Dune 2021 kesinlikle ayrı kartlar olarak kalmalı!
        val dune1984 = ProviderListing(
            providerId = "HDFilmCehennemi",
            providerUrl = "https://hdfilm.nl/dune-1984",
            title = "Dune (1984)",
            mediaType = TvType.Movie,
            year = 1984
        )
        val dune2021 = ProviderListing(
            providerId = "HDFilmCehennemi",
            providerUrl = "https://hdfilm.nl/dune-2021",
            title = "Dune (2021)",
            mediaType = TvType.Movie,
            year = 2021
        )

        val merged = TRCanonicalEngine.mergeListings(listOf(dune1984, dune2021))
        assertEquals(2, merged.size)
        assertFalse("1984 ve 2021 yapımları asla birleştirilemez", TRCanonicalEngine.canMerge(dune1984, dune2021))
    }

    @Test
    fun testMovieAndTvSeriesNeverMerged() {
        // Film ve Dizi kesinlikle ayrı kartlar olarak kalmalı!
        val movieListing = ProviderListing(
            providerId = "HDFilmCehennemi",
            providerUrl = "https://hdfilm.nl/fargo-film",
            title = "Fargo",
            mediaType = TvType.Movie,
            year = 1996
        )
        val seriesListing = ProviderListing(
            providerId = "SezonlukDizi",
            providerUrl = "https://sezonlukdizi.cc/diziler/fargo.html",
            title = "Fargo",
            mediaType = TvType.TvSeries,
            year = 2014
        )

        assertFalse("Film ve Dizi asla birleştirilemez", TRCanonicalEngine.canMerge(movieListing, seriesListing))
        val merged = TRCanonicalEngine.mergeListings(listOf(movieListing, seriesListing))
        assertEquals(2, merged.size)
    }

    @Test
    fun testCanonicalPayloadSerialization() {
        val listing = ProviderListing(
            providerId = "HDFilmCehennemi",
            providerUrl = "https://hdfilm.nl/matrix",
            title = "Matrix",
            mediaType = TvType.Movie,
            year = 1999
        )
        val canonical = TRCanonicalEngine.mergeListings(listOf(listing)).first()
        val payload = canonical.toPayload()
        val json = payload.toJsonString()

        val parsed = CanonicalPayload.fromJsonString(json)
        assertNotNull(parsed)
        assertEquals("Matrix", parsed?.title)
        assertEquals("Movie", parsed?.mediaType)
        assertEquals("https://hdfilm.nl/matrix", parsed?.primaryUrl)
        assertEquals(1, parsed?.sources?.size)
    }

    @Test
    fun testSlowProviderDoesNotLockCatalog() = runBlocking {
        // Yavaş sağlayıcı simülasyonu
        val fastAdapter = object : TRProviderAdapter {
            override val providerId = "FastProvider"
            override val displayName = "Fast"
            override val isEnabled = true
            override val supportedTypes = setOf(TvType.Movie)
            override suspend fun fetchCatalog(category: String, page: Int): List<ProviderListing> {
                return listOf(
                    ProviderListing(providerId, "https://fast.com/m1", "Hızlı Film", TvType.Movie, 2023)
                )
            }
            override suspend fun search(query: String, page: Int) = emptyList<ProviderListing>()
            override suspend fun loadDetail(url: String) = null
            override suspend fun loadStreamLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) = false
        }

        val slowAdapter = object : TRProviderAdapter {
            override val providerId = "SlowProvider"
            override val displayName = "Slow"
            override val isEnabled = true
            override val supportedTypes = setOf(TvType.Movie)
            override suspend fun fetchCatalog(category: String, page: Int): List<ProviderListing> {
                delay(6000L) // 4 saniye zaman aşımını aşar!
                return listOf(
                    ProviderListing(providerId, "https://slow.com/m2", "Yavaş Film", TvType.Movie, 2023)
                )
            }
            override suspend fun search(query: String, page: Int) = emptyList<ProviderListing>()
            override suspend fun loadDetail(url: String) = null
            override suspend fun loadStreamLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) = false
        }

        // Test adapter registry
        TRAdapterRegistry.registerAdapter(fastAdapter)
        TRAdapterRegistry.registerAdapter(slowAdapter)

        val startNs = System.nanoTime()
        val items = TRUnifiedCatalog.getCategoryPage("Son Eklenenler", 1)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        // Yavaş sağlayıcı zaman aşımına uğramalı, hızlı sağlayıcının içeriği dönmeli
        assertTrue("Zaman aşımı süresi makul olmalı (< 5500ms)", elapsedMs < 5500)
        assertTrue("Hızlı sağlayıcının içeriği gelmeli", items.any { it.title.contains("Hızlı Film") })
    }

    @Test
    fun testNoFakeLinksProducedWhenStreamUnavailable() = runBlocking {
        // Gerçek bağlantı bulunamadığında sahte link üretilmediğini doğrula
        var linkFound = false
        val success = TRPlaybackDispatcher.loadLinks(
            data = "https://unsupported-or-dead.com/nonexistent",
            isCasting = false,
            subtitleCallback = {},
            callback = { linkFound = true }
        )

        assertFalse("Başarısız çağrı true dönemez", success)
        assertFalse("Sahte link asla tetiklenemez", linkFound)
    }

    @Test
    fun testPlaybackDispatcherMultiSourceLinkTagging() = runBlocking {
        // Çoklu kaynak link etiketleme testi
        val testAdapter = object : TRProviderAdapter {
            override val providerId = "MockProvider"
            override val displayName = "MockProvider"
            override val isEnabled = true
            override val supportedTypes = setOf(TvType.Movie)
            override suspend fun fetchCatalog(category: String, page: Int) = emptyList<ProviderListing>()
            override suspend fun search(query: String, page: Int) = emptyList<ProviderListing>()
            override suspend fun loadDetail(url: String) = null
            override suspend fun loadStreamLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
                callback(
                    newExtractorLink("MockProvider", "Rapidrame", "https://mock.stream/video.mp4", ExtractorLinkType.VIDEO)
                )
                return true
            }
        }
        TRAdapterRegistry.registerAdapter(testAdapter)

        val episodePayload = CanonicalEpisodePayload(
            canonicalId = "mock-series",
            season = 1,
            episode = 1,
            sources = listOf(
                com.cloudstream.tr.one.models.CanonicalEpisodeSourceRef("MockProvider", "https://mock.com/ep1")
            )
        )

        val emittedLinks = mutableListOf<ExtractorLink>()
        val success = TRPlaybackDispatcher.loadLinks(
            data = episodePayload.toJsonString(),
            isCasting = false,
            subtitleCallback = {},
            callback = { emittedLinks.add(it) }
        )

        assertTrue(success)
        assertEquals(1, emittedLinks.size)
        // Oynatıcı menüsünde sağlayıcı etiketi açıkça yer almalı: [MockProvider]
        assertTrue(emittedLinks[0].source.contains("[MockProvider]"))
    }

    @Test
    fun testApiHolderObjectAccessVersusDirectClassLoading() {
        // Aşama 0 düzeltmesi:
        // 1. APIHolder üzerinden kayıtlı MainAPI nesnelerine erişim ortak arayüz üzerinden mümkündür.
        val externalProviders = TRAdapterRegistry.getRegisteredExternalProviders()
        assertNotNull(externalProviders)

        // 2. Fakat TRPlusOne, harici eklentiler kurulu olmasa dahi kendi gömülü 3 adaptörüyle
        // (HDFilmCehennemi, SezonlukDizi, AnimeciX) bağımsız olarak çalışır!
        val adapters = TRAdapterRegistry.getAllAdapters()
        assertTrue(adapters.any { it.providerId == "HDFilmCehennemi" })
        assertTrue(adapters.any { it.providerId == "SezonlukDizi" })
        assertTrue(adapters.any { it.providerId == "AnimeciX" })
    }
}
