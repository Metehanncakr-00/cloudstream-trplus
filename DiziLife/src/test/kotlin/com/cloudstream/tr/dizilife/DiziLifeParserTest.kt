package com.cloudstream.tr.dizilife

import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiziLifeParserTest {
    private val api = DiziLife()

    @Test
    fun parseSearchResults_extractsShows() {
        val stream = javaClass.getResourceAsStream("/dizilife_search.html")
        assertNotNull("Search fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://dizi74.life")

        val results = api.parseSearchResults(document)
        assertTrue("Search results should not be empty", results.isNotEmpty())
        val first = results.first()
        assertTrue("Title should not be blank", first.name.isNotBlank())
        assertTrue("Url should contain /dizi/ or /film/", first.url.contains("/dizi/") || first.url.contains("/film/"))
    }

    @Test
    fun parseLoadMetadata_extractsSeriesAndEpisodes() = runBlocking {
        val stream = javaClass.getResourceAsStream("/dizilife_detail.html")
        assertNotNull("Detail fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://dizi74.life/dizi/the-walking-dead")

        val load = api.parseLoadMetadata(document, "https://dizi74.life/dizi/the-walking-dead")
        assertNotNull("Load response must not be null", load)
        assertTrue("Load response should be TvSeriesLoadResponse", load is TvSeriesLoadResponse)
        val series = load as TvSeriesLoadResponse
        assertTrue("Title should contain The Walking Dead", series.name.contains("The Walking Dead"))
        assertTrue("Episodes list should not be empty", series.episodes.isNotEmpty())
        val firstEp = series.episodes.first()
        assertEquals(1, firstEp.season)
        assertEquals(1, firstEp.episode)
    }
}
