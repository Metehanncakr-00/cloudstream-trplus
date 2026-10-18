package com.cloudstream.tr.filmmodu

import com.lagradost.cloudstream3.MovieSearchResponse
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FilmModuParserTest {
    private val provider = FilmModu()

    @Test
    fun testParseMovieCard() {
        val stream = javaClass.classLoader?.getResourceAsStream("filmmodu_home.html")
        assertNotNull("filmmodu_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val cards = doc.select(".col-movie")
        assertEquals(2, cards.size)

        val item1 = provider.parseSearchItem(cards[0]) as? MovieSearchResponse
        assertNotNull("Item 1 failed to parse", item1)
        assertEquals("Yıldızlararası", item1?.name)
        assertEquals("${provider.mainUrl}/film/interstellar-turkce-dublaj-altyazili-izle", item1?.url)
        assertEquals(2014, item1?.year)

        val item2 = provider.parseSearchItem(cards[1]) as? MovieSearchResponse
        assertNotNull("Item 2 failed to parse", item2)
        assertEquals("Başlangıç", item2?.name)
        assertEquals("${provider.mainUrl}/film/inception-turkce-dublaj-altyazili-izle", item2?.url)
        assertEquals(2010, item2?.year)
    }
}
