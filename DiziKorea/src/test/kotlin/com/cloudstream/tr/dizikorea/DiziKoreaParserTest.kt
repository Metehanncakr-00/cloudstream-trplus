package com.cloudstream.tr.dizikorea

import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class DiziKoreaParserTest {
    private val api = DiziKorea()

    @Test
    fun buildSearchUrl_encodesQueryOnceAndPreservesPagination() {
        for (query in listOf("A&B #1+C?", "İstanbul ışığı Türkçe Çığ öyküsü")) {
            val first = URI(api.buildSearchUrl(query, 1))
            val second = URI(api.buildSearchUrl(query, 2))
            for (uri in listOf(first, second)) {
                assertEquals("https", uri.scheme)
                assertEquals("dizikorea3.com", uri.host)
                assertEquals("/arama", uri.path)
                assertNull(uri.rawFragment)
                val pairs = uri.rawQuery.split('&').map { it.split('=', limit = 2) }
                assertEquals(if (uri == first) listOf("q") else listOf("q", "page"), pairs.map { it[0] })
                assertEquals(query, URLDecoder.decode(pairs.first()[1], "UTF-8"))
            }
            assertEquals(first.rawQuery + "&page=2", second.rawQuery)
            assertEquals(first, URI(api.buildSearchUrl(query, 0)))
        }
    }

    @Test
    fun parseSearchResults_extractsExpectedTitlesAndLinks() {
        val stream = javaClass.getResourceAsStream("/dizikorea_search.html")
        assertNotNull("Search fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://dizikorea3.com")

        val results = api.parseSearchResults(document)
        assertTrue("Search results should not be empty", results.isNotEmpty())
        val first = results.first()
        assertTrue("Title should not be blank", first.name.isNotBlank())
        assertTrue("Url should be valid dizi link", first.url.contains("dizikorea3.com/dizi/"))
        assertEquals("Love on the Menu", first.name)
        assertEquals("${api.mainUrl}/dizi/love-on-the-menu-izle", first.url)
        assertEquals("${api.mainUrl}/assets/uploads/series/.thumbs/love-on-the-menu-180w-q70.webp?v=1786118465", first.posterUrl)

        val episodeCard = document.selectFirst("a.poster-card")!!.clone()
        episodeCard.attr("href", first.url + "/sezon-1/bolum-1")
        document.body().appendChild(episodeCard)
        val repeated = api.parseSearchResults(document)
        assertEquals(results.map { it.url }, repeated.map { it.url })
        assertEquals(first.url, api.toSearchResult(episodeCard)!!.url)
    }

    @Test
    fun parseLoadMetadata_extractsSeriesAndEpisodes() = runBlocking {
        val stream = javaClass.getResourceAsStream("/dizikorea_detail.html")
        assertNotNull("Detail fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://dizikorea3.com/dizi/flex-x-cop-izle-dq")

        val load = api.parseLoadMetadata(document, "https://dizikorea3.com/dizi/flex-x-cop-izle-dq")
        assertNotNull("Load response must not be null", load)
        assertTrue("Load response should be TvSeriesLoadResponse", load is TvSeriesLoadResponse)
        val series = load as TvSeriesLoadResponse
        assertEquals("Flex X Cop", series.name)
        assertTrue("Episodes list should not be empty", series.episodes.isNotEmpty())
        val firstEp = series.episodes.first()
        assertEquals(1, firstEp.season)
        assertEquals(1, firstEp.episode)
        val episodeUrls = document.select("a[href*='/sezon-']").map { it.attr("href") }
        assertTrue("Fixture must exercise duplicate episode links", episodeUrls.size > episodeUrls.distinct().size)
        assertEquals(episodeUrls.distinct(), series.episodes.map { it.data })
        series.episodes.forEach { episode ->
            val match = Regex("/sezon-(\\d+)/bolum-(\\d+)").find(episode.data)!!
            assertEquals(match.groupValues[1].toInt(), episode.season)
            assertEquals(match.groupValues[2].toInt(), episode.episode)
        }
    }
}
