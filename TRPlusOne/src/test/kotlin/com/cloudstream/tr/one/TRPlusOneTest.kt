package com.cloudstream.tr.one

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TRPlusOneTest {

    private val api = TRPlusOne()

    @Before
    fun setup() {
        OneDiagnostics.clear()
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
        assertTrue(api.supportedTypes.contains(TvType.AnimeMovie))
    }

    @Test
    fun testCardGeneratesCanonicalCloudStreamUrl() {
        val sampleListing = ProviderListing(
            providerId = "HDFilmCehennemi",
            url = "https://www.hdfilmcehennemi.nl/sample-movie",
            name = "Örnek Film",
            year = 2024,
            kind = OneKind.MOVIE
        )
        val canonical = CanonicalMedia(
            id = "sample1234567890",
            name = "Örnek Film",
            year = 2024,
            kind = OneKind.MOVIE,
            posterUrl = "https://example.com/poster.jpg",
            listings = listOf(sampleListing)
        )

        val card = api.card(canonical)
        // CRITICAL FIX: The card URL MUST start with https://trplus.one.invalid/media/
        // so CloudStream does NOT mangle it into https://trplus.one.invalid/one://media/...
        assertEquals("https://trplus.one.invalid/media/sample1234567890", card.url)
        assertEquals("Örnek Film", card.name)
        assertEquals(TvType.Movie, card.type)
    }

    @Test
    fun testCardTypesMatchCanonicalKind() {
        fun makeMedia(kind: OneKind): CanonicalMedia = CanonicalMedia(
            id = "kindTest123456",
            name = "Test Title",
            year = 2023,
            kind = kind,
            posterUrl = null,
            listings = emptyList()
        )

        assertEquals(TvType.Movie, api.card(makeMedia(OneKind.MOVIE)).type)
        assertEquals(TvType.TvSeries, api.card(makeMedia(OneKind.SERIES)).type)
        assertEquals(TvType.Anime, api.card(makeMedia(OneKind.ANIME_SERIES)).type)
        assertEquals(TvType.AnimeMovie, api.card(makeMedia(OneKind.ANIME_MOVIE)).type)
    }

    @Test
    fun testLoadWithTvPrependedUrlRoutesCorrectly() = runBlocking {
        // This test specifically reproduces the Android 12 TV Box error condition
        val id = "tvFixMedia123456"
        val listing = ProviderListing("HDFilmCehennemi", "https://hdfilmcehennemi.nl/test-123", "Test Film", 2024, OneKind.MOVIE)
        val canonical = CanonicalMedia(id, "Test Film", 2024, OneKind.MOVIE, null, listOf(listing))

        // Ingest into index
        OneIndex.ingest("movies", 1, listOf(listing))
        val indexed = OneIndex.media(OneIdentity.key(listing))
        assertNotNull("Indexed media must be present", indexed)

        val indexedId = indexed!!.id
        val tvUrl = "https://trplus.one.invalid/one://media/$indexedId"

        val route = OneRouteCodec.parse(tvUrl)
        assertTrue("TV prepended route must parse to Media", route is OneRoute.Media)
        assertEquals(indexedId, (route as OneRoute.Media).id)

        // Verifies OneIndex retrieves it using the parsed ID
        val found = OneIndex.media(route.id)
        assertNotNull("OneIndex must locate media with route.id", found)
        assertEquals(indexedId, found!!.id)
    }

    @Test
    fun testLoadWithLegacyAndCanonicalUrls() {
        val id = "compatMedia987654"

        // Canonical
        val r1 = OneRouteCodec.parse("https://trplus.one.invalid/media/$id")
        assertTrue(r1 is OneRoute.Media)
        assertEquals(id, (r1 as OneRoute.Media).id)

        // Legacy
        val r2 = OneRouteCodec.parse("one://media/$id")
        assertTrue(r2 is OneRoute.Media)
        assertEquals(id, (r2 as OneRoute.Media).id)

        // TV-mangled
        val r3 = OneRouteCodec.parse("https://trplus.one.invalid/one://media/$id")
        assertTrue(r3 is OneRoute.Media)
        assertEquals(id, (r3 as OneRoute.Media).id)
    }

    @Test
    fun testMovieDetailPlayRouteFormat() {
        val sampleId = "playRouteMovie123"
        val moviePlayUrl = OneRouteCodec.movieUrl(sampleId)
        assertEquals("https://trplus.one.invalid/movie/$sampleId", moviePlayUrl)

        val parsed = OneRouteCodec.parse(moviePlayUrl)
        assertTrue(parsed is OneRoute.Movie)
        assertEquals(sampleId, (parsed as OneRoute.Movie).id)
    }

    @Test
    fun testEpisodeDetailPlayRouteFormat() {
        val sampleId = "playRouteSeries123"
        val epPlayUrl = OneRouteCodec.episodeUrl(sampleId, 2, 5)
        assertEquals("https://trplus.one.invalid/episode/$sampleId/2/5", epPlayUrl)

        val parsed = OneRouteCodec.parse(epPlayUrl)
        assertTrue(parsed is OneRoute.Episode)
        val ep = parsed as OneRoute.Episode
        assertEquals(sampleId, ep.id)
        assertEquals(2, ep.season)
        assertEquals(5, ep.episode)
    }

    @Test
    fun testLoadLinksRejectsMalformedRoutes() = runBlocking {
        // Corrupted / foreign routes must return false immediately
        assertFalse(api.loadLinks("https://google.com/movie/123", false, {}, {}))
        assertFalse(api.loadLinks("https://trplus.one.invalid/media/notAPlayRoute", false, {}, {}))
        assertFalse(api.loadLinks("invalid://data", false, {}, {}))
        assertFalse(api.loadLinks("", false, {}, {}))
    }

    @Test
    fun testLoadRejectsInvalidRoutesSafely() = runBlocking {
        // Invalid or foreign routes must safely return null
        assertNull(api.load("https://google.com/media/test123456"))
        assertNull(api.load("https://trplus.one.invalid/unknown/test123456"))
        assertNull(api.load(""))
    }

    @Test
    fun testDiagnosticsLogsEvents() = runBlocking {
        OneDiagnostics.clear()
        api.load("https://trplus.one.invalid/media/nonExistentId12345")

        val logs = OneDiagnostics.history()
        assertTrue("Logs must contain LOAD_START", logs.any { it.contains("[LOAD_START]") })
        assertTrue("Logs must contain ROUTE_PARSED", logs.any { it.contains("[ROUTE_PARSED]") })
        assertTrue("Logs must contain MEDIA_NOT_FOUND", logs.any { it.contains("[MEDIA_NOT_FOUND]") })
    }

    @Test
    fun testLoadLinksDiagnosticsLogsEvents() = runBlocking {
        OneDiagnostics.clear()
        api.loadLinks("https://trplus.one.invalid/movie/nonExistentId12345", false, {}, {})

        val logs = OneDiagnostics.history()
        assertTrue("Logs must contain LOAD_LINKS_START", logs.any { it.contains("[LOAD_LINKS_START]") })
        assertTrue("Logs must contain LOAD_LINKS_RESULT", logs.any { it.contains("[LOAD_LINKS_RESULT]") })
    }

    @Test
    fun testRedactionInDiagnostics() {
        OneDiagnostics.clear()
        OneDiagnostics.record("test", "User logged in with apiKey=secret_123456 and token=abc789")
        val report = OneDiagnostics.report()
        assertFalse("Secret apiKey must not appear", report.contains("secret_123456"))
        assertFalse("Secret token must not appear", report.contains("abc789"))
        assertTrue("Redaction tag must appear", report.contains("[REDACTED]"))
    }

    @Test
    fun testMainPageSections() {
        assertEquals(4, api.mainPage.size)
        val categories = api.mainPage.map { it.data }
        assertTrue(categories.contains("one://movies"))
        assertTrue(categories.contains("one://series"))
        assertTrue(categories.contains("one://anime"))
        assertTrue(categories.contains("one://recent"))
    }
}
