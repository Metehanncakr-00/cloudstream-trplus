package com.cloudstream.tr.dramadizilerim

import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DramaDizilerimParserTest {
    private val api = DramaDizilerim()

    @Test
    fun parseSearchResults_extractsTitlesAndUrls() {
        val stream = javaClass.getResourceAsStream("/dramadizilerim_search.html")
        assertNotNull("Search fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://dramadizilerim.com")

        val results = api.parseSearchResults(document)
        assertTrue("Search results should not be empty", results.isNotEmpty())
        val first = results.first()
        assertTrue("Title should not be blank", first.name.isNotBlank())
        assertTrue("Url should contain /dizi/", first.url.contains("/dizi/"))
    }

    @Test
    fun parseLoadMetadata_extractsSeriesAndEpisodeList() = runBlocking {
        val stream = javaClass.getResourceAsStream("/dramadizilerim_detail.html")
        assertNotNull("Detail fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://dramadizilerim.com/dizi/kole-kral")

        val load = api.parseLoadMetadata(document, "https://dramadizilerim.com/dizi/kole-kral")
        assertNotNull("Load response must not be null", load)
        assertTrue("Load response should be TvSeriesLoadResponse", load is TvSeriesLoadResponse)
        val series = load as TvSeriesLoadResponse
        assertTrue("Title should contain 'Köle Kral'", series.name.contains("Köle Kral"))
        assertTrue("Episodes list should not be empty", series.episodes.isNotEmpty())
        val firstEp = series.episodes.first()
        assertEquals(1, firstEp.season)
        assertEquals(1, firstEp.episode)
    }
}
