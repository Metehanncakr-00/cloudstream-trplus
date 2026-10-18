package com.cloudstream.tr.dizigecesi

import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DizigecesiParserTest {
    private val api = Dizigecesi()

    @Test
    fun parseSearchResults_extractsCards() {
        val stream = javaClass.getResourceAsStream("/dizigecesi_search.html")
        assertNotNull("Search fixture must exist", stream)
        val html = stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val document = Jsoup.parse(html, "https://dizigecesi.com")

        val results = api.parseSearchResults(document)
        assertTrue("Search results should not be empty", results.isNotEmpty())
        val first = results.first()
        assertTrue("Title should not be blank", first.name.isNotBlank())
        assertTrue("Poster should not be null", first.posterUrl != null)
    }

    @Test
    fun parseLoadMetadata_extractsTvSeriesAndEpisodes() = runBlocking {
        val stream = javaClass.getResourceAsStream("/dizigecesi_detail.html")
        assertNotNull("Detail fixture must exist", stream)
        val html = stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val document = Jsoup.parse(html, "https://dizigecesi.com/dizi/spartacus")

        val load = api.parseLoadMetadata(document, "https://dizigecesi.com/dizi/spartacus")
        assertNotNull("Load response must not be null", load)
        assertTrue("Load response should be TvSeriesLoadResponse", load is TvSeriesLoadResponse)
        val tvSeries = load as TvSeriesLoadResponse
        assertEquals("Spartacus", tvSeries.name)
        assertTrue("Episodes should not be empty", tvSeries.episodes.isNotEmpty())
        val firstEp = tvSeries.episodes.first()
        assertEquals(1, firstEp.season)
        assertEquals(1, firstEp.episode)
    }
}
