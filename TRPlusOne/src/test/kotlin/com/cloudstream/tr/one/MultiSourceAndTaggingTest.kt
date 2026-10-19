package com.cloudstream.tr.one

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MultiSourceAndTaggingTest {

    private val api = TRPlusOne()

    @Before
    fun setup() {
        OneDiagnostics.clear()
        OneEpisodeRegistry.clear()
        OneProviders.resetForTests()
    }

    @Test
    fun testMultiSourceSingleCardDeduplication() {
        // Same film across 3 different providers in Group 1
        val l1 = ProviderListing("HDFilmCehennemi", "https://hdfilmcehennemi.nl/the-wait", "The Wait", 2023, OneKind.MOVIE, imdbId = "tt1234567")
        val l2 = ProviderListing("FilmMakinesi", "https://filmmakinesi.to/the-wait-izle", "The Wait", 2023, OneKind.MOVIE, imdbId = "tt1234567")
        val l3 = ProviderListing("FullHDFilmizlesene", "https://fullhdfilmizlesene.now/the-wait", "The Wait", 2023, OneKind.MOVIE, imdbId = "tt1234567")

        val merged = CanonicalEngine.merge(emptyList(), listOf(l1, l2, l3))
        assertEquals("Must deduplicate into exactly 1 canonical card", 1, merged.size)
        val canonical = merged.first()
        assertEquals(3, canonical.listings.size)
        assertTrue(canonical.listings.any { it.providerId == "HDFilmCehennemi" })
        assertTrue(canonical.listings.any { it.providerId == "FilmMakinesi" })
        assertTrue(canonical.listings.any { it.providerId == "FullHDFilmizlesene" })

        // Check card URL
        val card = api.card(canonical)
        assertEquals("https://trplus.one.invalid/media/${canonical.id}", card.url)
    }

    @Test
    fun testAudioAndSubtitleTagging() = runBlocking {
        val listing = ProviderListing("MockDubSub", "https://mock.invalid/show", "Mock Show", 2024, OneKind.SERIES)
        OneIndex.ingest("series", 1, listOf(listing))
        val mediaId = OneIdentity.key(listing)

        // Pre-register episode in OneEpisodeRegistry
        OneEpisodeRegistry.register(mediaId, 1, 1, "MockDubSub", "https://mock.invalid/show/1-1")

        // Mock adapter that returns 1 dubbed link, 1 subbed link, and 1 clean link
        val mockAdapter = object : TRProviderAdapter {
            override val providerId: String = "MockDubSub"
            override val displayName: String = "Mock Provider"
            override val supportedTypes: Set<OneKind> = setOf(OneKind.SERIES)

            override suspend fun fetchCatalog(category: String, page: Int): List<ProviderListing> = emptyList()
            override suspend fun search(query: String): List<ProviderListing> = emptyList()
            override suspend fun loadDetail(url: String): LoadResponse? = null

            override suspend fun loadLinks(
                data: String,
                isCasting: Boolean,
                subtitleCallback: (SubtitleFile) -> Unit,
                callback: (ExtractorLink) -> Unit
            ): Boolean {
                // Link 1: Dublaj
                @Suppress("DEPRECATION")
                callback(
                    ExtractorLink(
                        source = "SourceA",
                        name = "SourceA 1080p Dublaj",
                        url = "https://stream.invalid/1",
                        referer = "",
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.VIDEO
                    )
                )
                // Link 2: Altyazılı
                @Suppress("DEPRECATION")
                callback(
                    ExtractorLink(
                        source = "SourceB",
                        name = "SourceB 720p [Altyazı]",
                        url = "https://stream.invalid/2",
                        referer = "",
                        quality = Qualities.P720.value,
                        type = ExtractorLinkType.VIDEO
                    )
                )
                // Link 3: Clean (no dub/sub flag)
                @Suppress("DEPRECATION")
                callback(
                    ExtractorLink(
                        source = "SourceC",
                        name = "SourceC 1080p",
                        url = "https://stream.invalid/3",
                        referer = "",
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.VIDEO
                    )
                )
                return true
            }
        }

        OneProviders.resetForTests(listOf(mockAdapter))

        val emitted = mutableListOf<ExtractorLink>()
        val success = api.loadLinks("https://trplus.one.invalid/episode/$mediaId/1/1", false, {}, { emitted.add(it) })

        assertTrue(success)
        assertEquals(3, emitted.size)

        // Verifications
        // Link 1 must contain [Türkçe Dublaj]
        assertTrue("Link 1 name must contain [Türkçe Dublaj]", emitted[0].name.contains("[Türkçe Dublaj]"))
        assertTrue("Link 1 name must contain provider prefix [MockDubSub]", emitted[0].name.startsWith("[MockDubSub]"))
        assertTrue("Link 1 source must contain provider prefix", emitted[0].source.startsWith("[MockDubSub]"))

        // Link 2 must contain [Türkçe Altyazı]
        assertTrue("Link 2 name must contain [Türkçe Altyazı]", emitted[1].name.contains("[Türkçe Altyazı]"))
        assertTrue("Link 2 name must contain provider prefix [MockDubSub]", emitted[1].name.startsWith("[MockDubSub]"))

        // Link 3 must NOT contain fake dub/sub tag
        assertFalse("Link 3 without evidence must NOT contain [Türkçe Dublaj]", emitted[2].name.contains("[Türkçe Dublaj]"))
        assertFalse("Link 3 without evidence must NOT contain [Türkçe Altyazı]", emitted[2].name.contains("[Türkçe Altyazı]"))
        assertTrue("Link 3 name must contain provider prefix [MockDubSub]", emitted[2].name.startsWith("[MockDubSub]"))
    }

    @Test
    fun testEpisodeDirectLinkPlaybackAndDiagnostics() = runBlocking {
        val listing = ProviderListing("FastProvider", "https://fast.invalid/show", "Fast Show", 2024, OneKind.SERIES)
        OneIndex.ingest("series", 1, listOf(listing))
        val mediaId = OneIdentity.key(listing)

        // Put target in registry
        val directUrl = "https://fast.invalid/ep/1/2"
        OneEpisodeRegistry.register(mediaId, 1, 2, "FastProvider", directUrl)

        var directUrlCalledWith: String? = null
        var detailCalled = false

        val testAdapter = object : TRProviderAdapter {
            override val providerId: String = "FastProvider"
            override val displayName: String = "Fast Provider"
            override val supportedTypes: Set<OneKind> = setOf(OneKind.SERIES)

            override suspend fun fetchCatalog(category: String, page: Int): List<ProviderListing> = emptyList()
            override suspend fun search(query: String): List<ProviderListing> = emptyList()
            override suspend fun loadDetail(url: String): LoadResponse? {
                detailCalled = true
                return null
            }

            override suspend fun loadLinks(
                data: String,
                isCasting: Boolean,
                subtitleCallback: (SubtitleFile) -> Unit,
                callback: (ExtractorLink) -> Unit
            ): Boolean {
                directUrlCalledWith = data
                @Suppress("DEPRECATION")
                callback(
                    ExtractorLink(
                        source = "FastServer",
                        name = "FastServer 1080p",
                        url = "https://stream.invalid/play.m3u8",
                        referer = "",
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.M3U8
                    )
                )
                return true
            }
        }

        OneProviders.resetForTests(listOf(testAdapter))
        OneDiagnostics.clear()

        val success = api.loadLinks("https://trplus.one.invalid/episode/$mediaId/1/2", false, {}, {})
        assertTrue(success)

        // PROOF: loadDetail was NEVER called because OneEpisodeRegistry directly provided the target URL!
        assertFalse("Direct episode routing must NOT call loadDetail on episode playback!", detailCalled)
        assertEquals("loadLinks must be called with direct registered URL", directUrl, directUrlCalledWith)

        // PROOF OF DIAGNOSTIC EVENTS:
        val logs = OneDiagnostics.history()
        assertTrue("Logs must contain EPISODE_DATA_FOUND", logs.any { it.contains("[EPISODE_DATA_FOUND]") })
        assertTrue("Logs must contain PROVIDER_LINKS_START", logs.any { it.contains("[PROVIDER_LINKS_START]") })
        assertTrue("Logs must contain PROVIDER_LINKS_RESULT", logs.any { it.contains("[PROVIDER_LINKS_RESULT]") })
        assertTrue("Logs must contain PLAYABLE_LINKS_TOTAL", logs.any { it.contains("[PLAYABLE_LINKS_TOTAL]") })
    }

    @Test
    fun testEpisodeMissingFromRegistryTriggersFallbackAndRegisters() = runBlocking {
        val listing = ProviderListing("FallbackProvider", "https://fallback.invalid/show", "Fallback Show", 2024, OneKind.SERIES)
        OneIndex.ingest("series", 1, listOf(listing))
        val mediaId = OneIdentity.key(listing)

        var detailCalled = false
        var loadLinksCalledWith: String? = null

        val testAdapter = object : TRProviderAdapter {
            override val providerId: String = "FallbackProvider"
            override val displayName: String = "Fallback Provider"
            override val supportedTypes: Set<OneKind> = setOf(OneKind.SERIES)

            override suspend fun fetchCatalog(category: String, page: Int): List<ProviderListing> = emptyList()
            override suspend fun search(query: String): List<ProviderListing> = emptyList()

            override suspend fun loadDetail(url: String): LoadResponse? {
                detailCalled = true
                val eps = listOf(
                    api.newEpisode("https://fallback.invalid/ep/1/1") { this.season = 1; this.episode = 1 },
                    api.newEpisode("https://fallback.invalid/ep/1/2") { this.season = 1; this.episode = 2 }
                )
                return api.newTvSeriesLoadResponse("Fallback Show", url, TvType.TvSeries, eps)
            }

            override suspend fun loadLinks(
                data: String,
                isCasting: Boolean,
                subtitleCallback: (SubtitleFile) -> Unit,
                callback: (ExtractorLink) -> Unit
            ): Boolean {
                loadLinksCalledWith = data
                @Suppress("DEPRECATION")
                callback(
                    ExtractorLink(
                        source = "FallbackServer",
                        name = "FallbackServer 1080p",
                        url = "https://stream.invalid/ep2.mp4",
                        referer = "",
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.VIDEO
                    )
                )
                return true
            }
        }

        OneProviders.resetForTests(listOf(testAdapter))
        OneDiagnostics.clear()

        // Notice: registry is empty for fallbackMedia789
        assertFalse(OneEpisodeRegistry.hasEpisode(mediaId, 1, 2))

        val success = api.loadLinks("https://trplus.one.invalid/episode/$mediaId/1/2", false, {}, {})
        assertTrue(success)

        // PROOF: fallback was invoked
        assertTrue("Fallback must call loadDetail when missing from registry", detailCalled)
        assertEquals("https://fallback.invalid/ep/1/2", loadLinksCalledWith)

        // PROOF: Newly resolved episode is now saved in OneEpisodeRegistry for subsequent plays!
        assertTrue("Newly resolved episode must be saved into OneEpisodeRegistry", OneEpisodeRegistry.hasEpisode(mediaId, 1, 2))
        assertEquals("https://fallback.invalid/ep/1/2", OneEpisodeRegistry.get(mediaId, 1, 2)["FallbackProvider"])

        // PROOF OF DIAGNOSTIC EVENTS:
        val logs = OneDiagnostics.history()
        assertTrue("Logs must contain EPISODE_DATA_MISSING", logs.any { it.contains("[EPISODE_DATA_MISSING]") })
        assertTrue("Logs must contain EPISODE_MATCH", logs.any { it.contains("[EPISODE_MATCH]") })
        assertTrue("Logs must contain PLAYABLE_LINKS_TOTAL", logs.any { it.contains("[PLAYABLE_LINKS_TOTAL]") })
    }
}
