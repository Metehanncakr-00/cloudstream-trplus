package com.cloudstream.tr.webdramaturkey

import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDramaTurkeyParserTest {
    private val api = WebDramaTurkey()

    @Test
    fun parseSearchResults_extractsCardsCorrectly() {
        val stream = javaClass.getResourceAsStream("/webdrama_search.html")
        assertNotNull("Search fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://webdramaturkey2.com")

        val results = api.parseSearchResults(document)
        assertTrue("Search results should not be empty", results.isNotEmpty())
        val first = results.first()
        assertTrue("Title should not be blank", first.name.isNotBlank())
        assertTrue("Url should contain /dizi/ or /film/", first.url.contains("/dizi/") || first.url.contains("/film/"))
    }

    @Test
    fun parseLoadMetadata_extractsSeriesAndEpisodes() = runBlocking {
        val stream = javaClass.getResourceAsStream("/webdrama_detail.html")
        assertNotNull("Detail fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://webdramaturkey2.com/dizi/hidden-love-izle")

        val load = api.parseLoadMetadata(document, "https://webdramaturkey2.com/dizi/hidden-love-izle")
        assertNotNull("Load response must not be null", load)
        assertTrue("Load response should be TvSeriesLoadResponse", load is TvSeriesLoadResponse)
        val series = load as TvSeriesLoadResponse
        assertTrue("Title should contain 'Hidden Love'", series.name.contains("Hidden Love"))
        assertTrue("Episodes list should not be empty", series.episodes.isNotEmpty())
        val firstEp = series.episodes.first()
        assertEquals(1, firstEp.season)
        assertEquals(1, firstEp.episode)
    }
}
