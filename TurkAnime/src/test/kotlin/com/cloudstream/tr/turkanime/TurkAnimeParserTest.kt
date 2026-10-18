package com.cloudstream.tr.turkanime

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class TurkAnimeParserTest {
    private val api = TurkAnime()

    @Test
    fun testParseSearchElement() {
        val html = """
            <div class="panel">
                <div class="panel-title">
                    <a href="//www.turkanime.tv/anime/boruto-naruto-next-generations">Boruto: Naruto Next Generations</a>
                </div>
                <img data-src="//www.turkanime.tv/imajlar/serilerb/3218.jpg" />
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val el = doc.selectFirst("div.panel")!!
        val res = api.toMainPageResult(el)

        assertNotNull("Result should not be null", res)
        assertEquals("Boruto: Naruto Next Generations", res!!.name)
        assertEquals("https://www.turkanime.tv/anime/boruto-naruto-next-generations", res.url)
        assertEquals(TvType.Anime, res.type)
    }

    @Test
    fun testParseLoadMetadata() = runBlocking {
        val detailStream = javaClass.classLoader?.getResourceAsStream("turkanime_detail.html")
        assertNotNull("turkanime_detail.html fixture must exist", detailStream)
        val html = detailStream!!.bufferedReader().use { it.readText() }
        val doc = Jsoup.parse(html)

        val loadRes = api.parseLoadMetadata(doc, "https://www.turkanime.tv/anime/boruto-naruto-next-generations")
        assertNotNull("Load response should not be null", loadRes)
        assertTrue(loadRes is TvSeriesLoadResponse)

        val series = loadRes as TvSeriesLoadResponse
        assertEquals("Boruto: Naruto Next Generations", series.name)
        assertEquals(TvType.Anime, series.type)
        assertNotNull(series.posterUrl)
    }
}
