package com.cloudstream.tr.sezonlukdizi

import com.lagradost.cloudstream3.TvSeriesSearchResponse
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SezonlukDiziParserTest {
    private val provider = SezonlukDizi()

    @Test
    fun testParseAfis() {
        val stream = javaClass.classLoader?.getResourceAsStream("sezonlukdizi_home.html")
        assertNotNull("sezonlukdizi_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val cards = doc.select("div.afis")
        assertEquals(2, cards.size)

        val item1 = provider.parseAfis(cards[0]) as? TvSeriesSearchResponse
        assertNotNull("Item 1 failed to parse", item1)
        assertEquals("Breaking Bad", item1?.name)
        assertEquals("https://sezonlukdizi.cc/diziler/breaking-bad.html", item1?.url)
        assertEquals(2008, item1?.year)

        val item2 = provider.parseAfis(cards[1]) as? TvSeriesSearchResponse
        assertNotNull("Item 2 failed to parse", item2)
        assertEquals("Better Call Saul", item2?.name)
        assertEquals("https://sezonlukdizi.cc/diziler/better-call-saul.html", item2?.url)
        assertEquals(2015, item2?.year)
    }
}
