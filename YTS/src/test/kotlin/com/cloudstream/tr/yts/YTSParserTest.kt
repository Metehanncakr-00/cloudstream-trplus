package com.cloudstream.tr.yts

import com.cloudstream.tr.core.resolvers.YtsMovie
import com.cloudstream.tr.core.resolvers.YtsResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YTSParserTest {
    @Test
    fun mapsApiMovieFixtureToDetailsSearchResponse() {
        val response = jacksonObjectMapper().readValue<YtsResponse>(
            """
            {
              "status":"ok",
              "data":{"movies":[{
                "id":42,"title":"Arrival","year":2016,"rating":7.9,
                "medium_cover_image":"https://img.example/arrival-medium.jpg",
                "large_cover_image":"https://img.example/arrival-large.jpg"
              }]}
            }
            """.trimIndent()
        )

        val movie = requireNotNull(response?.data?.movies?.single())
        val result = requireNotNull(YTS().parseMovie(movie))

        assertEquals("Arrival", result.name)
        assertEquals("https://yts.gg/api/v2/movie_details.json?movie_id=42&with_images=true", result.url)
        assertEquals("https://img.example/arrival-medium.jpg", result.posterUrl)
    }

    @Test
    fun rejectsSearchResultsMissingTitleOrId() {
        assertNull(YTS().parseMovie(YtsMovie(id = 1)))
        assertNull(YTS().parseMovie(YtsMovie(title = "Arrival")))
    }
}
