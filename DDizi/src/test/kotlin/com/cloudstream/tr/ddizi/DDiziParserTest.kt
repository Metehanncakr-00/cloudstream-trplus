package com.cloudstream.tr.ddizi

import com.lagradost.cloudstream3.TvSeriesSearchResponse
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DDiziParserTest {
    private val provider = DDizi()

    @Test
    fun testParseCard() {
        val stream = javaClass.classLoader?.getResourceAsStream("ddizi_home.html")
        assertNotNull("ddizi_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val cards = doc.select(".dizikutu")
        assertEquals(2, cards.size)

        val item1 = provider.parseSearchItem(cards[0]) as? TvSeriesSearchResponse
        assertNotNull("Item 1 failed to parse", item1)
        assertEquals("Kurtlar Vadisi", item1?.name)
        assertEquals("https://www.ddizi.pro/dizi/kurtlar-vadisi", item1?.url)
        assertEquals(2003, item1?.year)

        val item2 = provider.parseSearchItem(cards[1]) as? TvSeriesSearchResponse
        assertNotNull("Item 2 failed to parse", item2)
        assertEquals("Ezel", item2?.name)
        assertEquals("https://www.ddizi.pro/dizi/ezel", item2?.url)
        assertEquals(2009, item2?.year)
    }
}
