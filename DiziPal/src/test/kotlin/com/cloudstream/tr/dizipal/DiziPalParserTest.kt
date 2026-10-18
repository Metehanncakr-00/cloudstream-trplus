package com.cloudstream.tr.dizipal

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class DiziPalParserTest {
    private val api = DiziPal()

    @Test
    fun testParseSearchElement() {
        val html = "<a href=\"https://dizipal1430.com/dizi/reacher\" title=\"Reacher\"><img src=\"/uploads/content/poster.webp\" /><div class=\"name\">Reacher</div></a>"

        val doc = Jsoup.parse(html)
        val el = doc.selectFirst("a")!!
        val res = api.parseSearchElement(el)

        assertNotNull("Search element result should not be null", res)
        assertEquals("Reacher", res!!.name)
        assertEquals("https://dizipal1430.com/dizi/reacher", res.url)
        assertEquals(TvType.TvSeries, res.type)
    }

    @Test
    fun testParseLoadMetadata() = runBlocking {
        val detailStream = javaClass.classLoader?.getResourceAsStream("dizipal_detail.html")
        assertNotNull("dizipal_detail.html fixture must exist", detailStream)
        val html = detailStream!!.bufferedReader().use { it.readText() }
        val doc = Jsoup.parse(html)

        val loadRes = api.parseLoadMetadata(doc, "https://dizipal1430.com/dizi/lanterns")
        assertNotNull("Load response should not be null", loadRes)
        assertTrue(loadRes is TvSeriesLoadResponse)

        val series = loadRes as TvSeriesLoadResponse
        assertEquals("Lanterns", series.name)
        assertTrue("Episodes should not be empty", series.episodes.isNotEmpty())
        val ep1 = series.episodes.first()
        assertEquals(1, ep1.season)
        assertEquals(1, ep1.episode)
    }
}

