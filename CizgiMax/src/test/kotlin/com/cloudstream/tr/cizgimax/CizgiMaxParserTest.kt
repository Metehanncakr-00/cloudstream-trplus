package com.cloudstream.tr.cizgimax

import com.lagradost.cloudstream3.TvSeriesSearchResponse
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CizgiMaxParserTest {
    private val provider = CizgiMax()

    @Test
    fun testParseCard() {
        val stream = javaClass.classLoader?.getResourceAsStream("cizgimax_home.html")
        assertNotNull("cizgimax_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val cards = doc.select(".poster-box")
        assertEquals(2, cards.size)

        val item1 = provider.parseSearchItem(cards[0]) as? TvSeriesSearchResponse
        assertNotNull("Item 1 failed to parse", item1)
        assertEquals("Avatar: Son Hava Bükücü", item1?.name)
        assertEquals("https://cizgimax.online/cizgi-dizi/avatar-son-hava-bukucu/", item1?.url)
        assertEquals(2005, item1?.year)

        val item2 = provider.parseSearchItem(cards[1]) as? TvSeriesSearchResponse
        assertNotNull("Item 2 failed to parse", item2)
        assertEquals("Samurai Jack", item2?.name)
        assertEquals("https://cizgimax.online/cizgi-dizi/samurai-jack/", item2?.url)
        assertEquals(2001, item2?.year)
    }
}
