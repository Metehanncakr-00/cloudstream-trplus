package com.cloudstream.tr.sinemacx

import com.lagradost.cloudstream3.MovieSearchResponse
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SinemaCXParserTest {
    private val provider = SinemaCX()

    @Test
    fun testParseFragCard() {
        val stream = javaClass.classLoader?.getResourceAsStream("sinemacx_home.html")
        assertNotNull("sinemacx_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val cards = doc.select("div.frag-k")
        assertEquals(2, cards.size)

        val item1 = provider.parseFragCard(cards[0]) as? MovieSearchResponse
        assertNotNull("Item 1 failed to parse", item1)
        assertEquals("Avatar: Suyun Yolu", item1?.name)
        assertEquals("https://www.sinema.gg/film/avatar-the-way-of-water/", item1?.url)
        assertEquals(2022, item1?.year)

        val item2 = provider.parseFragCard(cards[1]) as? MovieSearchResponse
        assertNotNull("Item 2 failed to parse", item2)
        assertEquals("Oppenheimer", item2?.name)
        assertEquals("https://www.sinema.gg/film/oppenheimer/", item2?.url)
        assertEquals(2023, item2?.year)
    }
}
