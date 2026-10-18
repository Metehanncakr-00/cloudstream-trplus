package com.cloudstream.tr.sinewix

import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SinewixParserTest {
    private val provider = Sinewix()

    @Test
    fun testParseCard() {
        val stream = javaClass.classLoader?.getResourceAsStream("sinewix_home.html")
        assertNotNull("sinewix_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val cards = doc.select(".movie-card")
        assertEquals(2, cards.size)

        val item1 = provider.parseSearchItem(cards[0]) as? MovieSearchResponse
        assertNotNull("Item 1 failed to parse", item1)
        assertEquals("Dövüş Kulübü", item1?.name)
        assertEquals("https://sinewix.net/film/fight-club-izle", item1?.url)
        assertEquals(1999, item1?.year)

        val item2 = provider.parseSearchItem(cards[1]) as? TvSeriesSearchResponse
        assertNotNull("Item 2 failed to parse", item2)
        assertEquals("Breaking Bad", item2?.name)
        assertEquals("https://sinewix.net/dizi/breaking-bad-izle", item2?.url)
        assertEquals(2008, item2?.year)
    }
}
