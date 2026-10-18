package com.cloudstream.tr.rarefilmm

import com.lagradost.cloudstream3.MovieLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RareFilmmParserTest {
    private val api = RareFilmm()

    @Test
    fun parseSearchResults_extractsMovies() {
        val stream = javaClass.getResourceAsStream("/rarefilmm_search.html")
        assertNotNull("Search fixture must exist", stream)
        val html = stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val document = Jsoup.parse(html, "https://rarefilmm.com")

        val results = api.parseSearchResults(document)
        assertTrue("Search results should not be empty", results.isNotEmpty())
        val first = results.first()
        assertTrue("Title should not be blank", first.name.isNotBlank())
        assertTrue("URL should be a valid film link", first.url.contains("rarefilmm.com"))
    }

    @Test
    fun parseLoadMetadata_extractsMovieDetail() = runBlocking {
        val stream = javaClass.getResourceAsStream("/rarefilmm_detail.html")
        assertNotNull("Detail fixture must exist", stream)
        val html = stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val document = Jsoup.parse(html, "https://rarefilmm.com/2026/07/paris-seveille-1991/")

        val load = api.parseLoadMetadata(document, "https://rarefilmm.com/2026/07/paris-seveille-1991/")
        assertNotNull("Load response must not be null", load)
        assertTrue("Load response should be MovieLoadResponse", load is MovieLoadResponse)
        val movie = load as MovieLoadResponse
        assertTrue("Title should contain Paris", movie.name.contains("Paris"))
        assertNotNull("Poster should not be null", movie.posterUrl)
        assertTrue("Poster should contain wp-content", movie.posterUrl!!.contains("wp-content"))
    }
}
