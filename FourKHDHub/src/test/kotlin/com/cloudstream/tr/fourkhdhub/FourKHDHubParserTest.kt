package com.cloudstream.tr.fourkhdhub

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FourKHDHubParserTest {
    @Test
    fun parsesCardFixtureAndSkipsBlankTitles() {
        val document = Jsoup.parse(
            """
            <article>
              <h2 class="entry-title"><a href="https://4khdhub.one/movie/arrival/">Arrival</a></h2>
              <img src="https://4khdhub.one/posters/arrival.jpg">
            </article>
            <div class="post-item">
              <a rel="bookmark" href="https://4khdhub.one/movie/blank/"></a>
            </div>
            """.trimIndent()
        )

        val results = FourKHDHub().parseCards(document)

        assertEquals(1, results.size)
        assertEquals("Arrival", results.single().name)
        assertEquals("https://4khdhub.one/movie/arrival/", results.single().url)
        assertEquals("https://4khdhub.one/posters/arrival.jpg", results.single().posterUrl)
    }

    @Test
    fun parsesFallbackAnchorWhenExpectedHeadingIsAbsent() {
        val document = Jsoup.parse("<article><a href='/movie/fallback/'>Fallback</a></article>")

        val result = FourKHDHub().parseCards(document)

        assertTrue(result.single().name == "Fallback")
        assertEquals("https://4khdhub.one/movie/fallback/", result.single().url)
    }
}
