package com.cloudstream.tr.animecix

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.TvType
import org.junit.Assert.*
import org.junit.Test

class AnimeciXParserTest {
    @Test
    fun testSearchModelParsing() {
        val json = """
            {
                "results": [
                    {
                        "id": 9207,
                        "name": "Naruto x UT",
                        "title_type": "anime",
                        "poster": "https://image.tmdb.org/t/p/original/poster.jpg"
                    }
                ]
            }
        """.trimIndent()

        val mapper = jacksonObjectMapper()
        val search = mapper.readValue<Search>(json)

        assertNotNull(search)
        assertEquals(1, search.results.size)
        val item = search.results[0]
        assertEquals(9207, item.id)
        assertEquals("Naruto x UT", item.name)
        assertEquals("https://image.tmdb.org/t/p/original/poster.jpg", item.poster)
    }

    @Test
    fun testTitleModelParsing() {
        val json = """
            {
                "title": {
                    "id": 9207,
                    "name": "Naruto x UT",
                    "title_type": "anime",
                    "poster": "https://image.tmdb.org/t/p/original/poster.jpg",
                    "description": "Short OVA",
                    "year": 2011,
                    "mal_vote_average": "7.9",
                    "genres": [{"display_name": "Action"}],
                    "credits": [{"name": "Director"}],
                    "seasons": [{"number": 1}],
                    "videos": [{"url": "https://tau-video.xyz/embed/test", "season_num": 1, "episode_num": 1}]
                }
            }
        """.trimIndent()

        val mapper = jacksonObjectMapper()
        val res = mapper.readValue<Title>(json)

        assertNotNull(res.title)
        val anime = res.title!!
        assertEquals("Naruto x UT", anime.name)
        assertEquals(2011, anime.year)
        assertEquals("7.9", anime.rating)
        assertEquals(1, anime.videos.size)
    }
}
