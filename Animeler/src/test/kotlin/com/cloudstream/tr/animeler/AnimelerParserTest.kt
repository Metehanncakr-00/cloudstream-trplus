package com.cloudstream.tr.animeler

import com.lagradost.cloudstream3.AnimeLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimelerParserTest {
    private val api = Animeler()

    @Test
    fun parseSearchResults_extractsAnimeTitlesAndPosters() {
        val stream = javaClass.getResourceAsStream("/animeler_search.html")
        assertNotNull("Search fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://animeler.pw")

        val results = api.parseSearchResults(document)
        assertTrue("Search results should not be empty", results.isNotEmpty())
        val first = results.first()
        assertTrue("Title should not be blank", first.name.isNotBlank())
        assertTrue("Url should be valid anime link", first.url.contains("animeler.pw/"))
    }

    @Test
    fun parseLoadMetadata_extractsAnimeAndEpisodes() = runBlocking {
        val stream = javaClass.getResourceAsStream("/animeler_detail.html")
        assertNotNull("Detail fixture must exist", stream)
        val html = stream!!.bufferedReader().use { it.readText() }
        val document = Jsoup.parse(html, "https://animeler.pw/steinsgate")

        val load = api.parseLoadMetadata(document, "https://animeler.pw/steinsgate")
        assertNotNull("Load response must not be null", load)
        assertTrue("Load response should be AnimeLoadResponse", load is AnimeLoadResponse)
        val anime = load as AnimeLoadResponse
        assertTrue("Title should contain Steins;Gate", anime.name.contains("Steins;Gate"))
        assertNotNull("Episodes list should exist", anime.episodes)
        assertTrue("Episodes list should not be empty", anime.episodes.isNotEmpty())
    }
}
