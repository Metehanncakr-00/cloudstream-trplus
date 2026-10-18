package com.cloudstream.tr.dizimom

import com.lagradost.cloudstream3.TvSeriesSearchResponse
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DiziMomParserTest {
    private val provider = DiziMom()

    @Test
    fun testParseSearchElement() {
        val stream = javaClass.classLoader?.getResourceAsStream("dizimom_home.html")
        assertNotNull("dizimom_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val cards = doc.select("article.post")
        assertEquals(2, cards.size)

        val item1 = provider.parseSearchElement(cards[0]) as? TvSeriesSearchResponse
        assertNotNull("Item 1 failed to parse", item1)
        assertEquals("Breaking Bad", item1?.name)
        assertEquals("https://www.dizimom.diy/dizi/breaking-bad/", item1?.url)
        assertEquals(2008, item1?.year)

        val item2 = provider.parseSearchElement(cards[1]) as? TvSeriesSearchResponse
        assertNotNull("Item 2 failed to parse", item2)
        assertEquals("Better Call Saul", item2?.name)
        assertEquals("https://www.dizimom.diy/dizi/better-call-saul/", item2?.url)
        assertEquals(2015, item2?.year)
    }
}
