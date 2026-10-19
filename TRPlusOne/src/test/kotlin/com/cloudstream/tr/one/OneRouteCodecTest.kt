package com.cloudstream.tr.one

import org.junit.Assert.*
import org.junit.Test

class OneRouteCodecTest {

    private val sampleId = "41bd11428d2324befba9e91"

    @Test
    fun testCanonicalMediaRoute() {
        val raw = "https://trplus.one.invalid/media/$sampleId"
        val route = OneRouteCodec.parse(raw)
        assertTrue(route is OneRoute.Media)
        assertEquals(sampleId, (route as OneRoute.Media).id)
    }

    @Test
    fun testTvPrependedMediaRoute() {
        // This is the EXACT URL generated on Android 12 TV Box when clicking a card in v1
        val raw = "https://trplus.one.invalid/one://media/$sampleId"
        val route = OneRouteCodec.parse(raw)
        assertTrue(route is OneRoute.Media)
        assertEquals(sampleId, (route as OneRoute.Media).id)
    }

    @Test
    fun testLegacyMediaRoute() {
        val raw = "one://media/$sampleId"
        val route = OneRouteCodec.parse(raw)
        assertTrue(route is OneRoute.Media)
        assertEquals(sampleId, (route as OneRoute.Media).id)
    }

    @Test
    fun testRelativeMediaRoute() {
        val raw = "/media/$sampleId"
        val route = OneRouteCodec.parse(raw)
        assertTrue(route is OneRoute.Media)
        assertEquals(sampleId, (route as OneRoute.Media).id)
    }

    @Test
    fun testCanonicalMovieRoute() {
        val raw = "https://trplus.one.invalid/movie/$sampleId"
        val route = OneRouteCodec.parse(raw)
        assertTrue(route is OneRoute.Movie)
        assertEquals(sampleId, (route as OneRoute.Movie).id)
    }

    @Test
    fun testTvPrependedMovieRoute() {
        val raw = "https://trplus.one.invalid/one://movie/$sampleId"
        val route = OneRouteCodec.parse(raw)
        assertTrue(route is OneRoute.Movie)
        assertEquals(sampleId, (route as OneRoute.Movie).id)
    }

    @Test
    fun testLegacyMovieRoute() {
        val raw = "one://movie/$sampleId"
        val route = OneRouteCodec.parse(raw)
        assertTrue(route is OneRoute.Movie)
        assertEquals(sampleId, (route as OneRoute.Movie).id)
    }

    @Test
    fun testCanonicalEpisodeRoute() {
        val raw = "https://trplus.one.invalid/episode/$sampleId/2/14"
        val route = OneRouteCodec.parse(raw)
        assertTrue(route is OneRoute.Episode)
        val ep = route as OneRoute.Episode
        assertEquals(sampleId, ep.id)
        assertEquals(2, ep.season)
        assertEquals(14, ep.episode)
    }

    @Test
    fun testTvPrependedEpisodeRoute() {
        val raw = "https://trplus.one.invalid/one://episode/$sampleId/1/5"
        val route = OneRouteCodec.parse(raw)
        assertTrue(route is OneRoute.Episode)
        val ep = route as OneRoute.Episode
        assertEquals(sampleId, ep.id)
        assertEquals(1, ep.season)
        assertEquals(5, ep.episode)
    }

    @Test
    fun testLegacyEpisodeRoute() {
        val raw = "one://episode/$sampleId/0/1"
        val route = OneRouteCodec.parse(raw)
        assertTrue(route is OneRoute.Episode)
        val ep = route as OneRoute.Episode
        assertEquals(sampleId, ep.id)
        assertEquals(0, ep.season)
        assertEquals(1, ep.episode)
    }

    @Test
    fun testRoundTripGenerators() {
        val mediaUrl = OneRouteCodec.mediaUrl(sampleId)
        assertEquals("https://trplus.one.invalid/media/$sampleId", mediaUrl)
        val parsedMedia = OneRouteCodec.parse(mediaUrl)
        assertEquals(OneRoute.Media(sampleId), parsedMedia)

        val movieUrl = OneRouteCodec.movieUrl(sampleId)
        assertEquals("https://trplus.one.invalid/movie/$sampleId", movieUrl)
        val parsedMovie = OneRouteCodec.parse(movieUrl)
        assertEquals(OneRoute.Movie(sampleId), parsedMovie)

        val epUrl = OneRouteCodec.episodeUrl(sampleId, 3, 7)
        assertEquals("https://trplus.one.invalid/episode/$sampleId/3/7", epUrl)
        val parsedEp = OneRouteCodec.parse(epUrl)
        assertEquals(OneRoute.Episode(sampleId, 3, 7), parsedEp)
    }

    @Test
    fun testSafeRejectionOfInvalidRoutes() {
        // Foreign domains
        assertNull(OneRouteCodec.parse("https://google.com/media/$sampleId"))
        assertNull(OneRouteCodec.parse("https://malicious.org/one://media/$sampleId"))
        assertNull(OneRouteCodec.parse("https://otherdomain.com/movie/$sampleId"))

        // Empty / blank / null
        assertNull(OneRouteCodec.parse(null))
        assertNull(OneRouteCodec.parse(""))
        assertNull(OneRouteCodec.parse("   "))

        // Missing ID
        assertNull(OneRouteCodec.parse("https://trplus.one.invalid/media/"))
        assertNull(OneRouteCodec.parse("one://media/"))
        assertNull(OneRouteCodec.parse("https://trplus.one.invalid/movie/"))

        // Invalid characters in ID
        assertNull(OneRouteCodec.parse("https://trplus.one.invalid/media/bad id with spaces"))
        assertNull(OneRouteCodec.parse("https://trplus.one.invalid/media/bad<script>"))

        // Invalid episode numbers
        assertNull(OneRouteCodec.parse("https://trplus.one.invalid/episode/$sampleId/-1/1"))
        assertNull(OneRouteCodec.parse("https://trplus.one.invalid/episode/$sampleId/1/-1"))
        assertNull(OneRouteCodec.parse("https://trplus.one.invalid/episode/$sampleId/notanumber/1"))
        assertNull(OneRouteCodec.parse("https://trplus.one.invalid/episode/$sampleId")) // incomplete parts

        // Unknown route prefix
        assertNull(OneRouteCodec.parse("https://trplus.one.invalid/unknown/$sampleId"))
        assertNull(OneRouteCodec.parse("one://unknown/$sampleId"))
    }
}
