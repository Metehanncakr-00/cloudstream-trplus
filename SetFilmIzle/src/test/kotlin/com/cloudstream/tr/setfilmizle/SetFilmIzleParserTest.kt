package com.cloudstream.tr.setfilmizle

import com.lagradost.cloudstream3.MovieLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetFilmIzleParserTest {
    private val api = SetFilmIzle()

    @Test
    fun parseSearchResults_extractsMoviesAndShows() {
        val stream = javaClass.getResourceAsStream("/setfilm_search.html")
        assertNotNull("Search fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://www.setfilmizle.ltd")

        val results = api.parseSearchResults(document)
        assertTrue("Search results should not be empty", results.isNotEmpty())
        val first = results.first()
        assertTrue("Title should not be blank", first.name.isNotBlank())
        assertTrue("Url should be valid film or dizi link", first.url.contains("setfilmizle.ltd/"))
    }

    @Test
    fun parseLoadMetadata_extractsMovieDetail() = runBlocking {
        val stream = javaClass.getResourceAsStream("/setfilm_detail.html")
        assertNotNull("Detail fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://www.setfilmizle.ltd/film/the-get-out/")

        val load = api.parseLoadMetadata(document, "https://www.setfilmizle.ltd/film/the-get-out/")
        assertNotNull("Load response must not be null", load)
        assertTrue("Load response should be MovieLoadResponse", load is MovieLoadResponse)
        val movie = load as MovieLoadResponse
        assertTrue("Title should contain The Get Out", movie.name.contains("The Get Out"))
    }
}
