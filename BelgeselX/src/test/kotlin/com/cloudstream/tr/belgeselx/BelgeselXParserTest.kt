package com.cloudstream.tr.belgeselx

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class BelgeselXParserTest {
    private val api = BelgeselX()

    @Test
    fun testParseSearchElement() {
        val html = """
            <a class="px-toplist-item" href="https://belgeselx.com/belgesel/dag-adamlari">
                <img alt="DAĞ ADAMLARI" src="https://belgeselx.com/images/diziresimleri/dag-adamlari.webp" />
            </a>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val el = doc.selectFirst("a")!!
        val res = api.parseSearchElement(el)

        assertNotNull("Search element result should not be null", res)
        assertEquals("DAĞ ADAMLARI", res!!.name)
        assertEquals("https://belgeselx.com/belgesel/dag-adamlari", res.url)
        assertEquals(TvType.Documentary, res.type)
    }

    @Test
    fun testParseLoadMetadata() = runBlocking {
        val detailStream = javaClass.classLoader?.getResourceAsStream("belgeselx_detail.html")
        assertNotNull("belgeselx_detail.html fixture must exist", detailStream)
        val html = detailStream!!.bufferedReader().use { it.readText() }
        val doc = Jsoup.parse(html)

        val loadRes = api.parseLoadMetadata(doc, "https://belgeselx.com/belgesel/astro-arastirmalar")
        assertNotNull("Load response should not be null", loadRes)
        assertTrue(loadRes is TvSeriesLoadResponse)

        val series = loadRes as TvSeriesLoadResponse
        assertEquals("ASTRO ARAŞTIRMALAR", series.name)
        assertEquals(TvType.Documentary, series.type)
        assertTrue("Episodes should not be empty", series.episodes.isNotEmpty())
        val ep1 = series.episodes.first()
        assertEquals(1, ep1.season)
        assertEquals(1, ep1.episode)
    }
}
