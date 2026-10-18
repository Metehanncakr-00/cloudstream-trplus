package com.cloudstream.tr.filmhane

import com.lagradost.cloudstream3.MovieLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmHaneParserTest {
    private val api = FilmHane()

    @Test
    fun parseSearchResults_extractsCards() {
        val stream = javaClass.getResourceAsStream("/filmhane_search.html")
        assertNotNull("Search fixture must exist", stream)
        val html = stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val document = Jsoup.parse(html, "https://www.filmhane.shop")

        val results = api.parseSearchResults(document)
        assertTrue("Search results should not be empty", results.isNotEmpty())
        val first = results.first()
        assertTrue("Title should not be blank", first.name.isNotBlank())
        assertTrue("URL should be valid", first.url.contains("/film/") || first.url.contains("/dizi/"))
    }

    @Test
    fun parseLoadMetadata_extractsMovieDetail() = runBlocking {
        val stream = javaClass.getResourceAsStream("/filmhane_detail.html")
        assertNotNull("Detail fixture must exist", stream)
        val html = stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val document = Jsoup.parse(html, "https://www.filmhane.shop/film/the-lord-of-the-rings-the-war-of-the-rohirrim")

        val load = api.parseLoadMetadata(document, "https://www.filmhane.shop/film/the-lord-of-the-rings-the-war-of-the-rohirrim")
        assertNotNull("Load response must not be null", load)
        assertTrue("Load response should be MovieLoadResponse", load is MovieLoadResponse)
        val movie = load as MovieLoadResponse
        assertTrue("Title should contain Lord of the Rings", movie.name.contains("Lord of the Rings"))
        assertNotNull("Poster should not be null", movie.posterUrl)
        assertTrue("Poster should contain storage/uploads", movie.posterUrl!!.contains("storage/uploads"))
    }
}
