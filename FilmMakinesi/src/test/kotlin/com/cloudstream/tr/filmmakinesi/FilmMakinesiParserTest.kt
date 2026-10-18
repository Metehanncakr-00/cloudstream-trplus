package com.cloudstream.tr.filmmakinesi

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.Score
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class FilmMakinesiParserTest {
    private val api = FilmMakinesi()

    @Test
    fun testParseSearchElement() {
        val html = """
            <div class="col-6 col-sm-4 col-md-3 col-lg-2">
                <div class="item-relative">
                    <a class="item" href="/film/mayday-2026/" data-title="Mayday" data-score="6.8">
                        <div class="thumbnail-outer">
                            <img src="/uploads/postlar/kapak/mayday-2026.jpg" alt="Mayday" class="thumbnail" />
                        </div>
                        <div class="item-footer">
                            <div class="title">Mayday</div>
                        </div>
                    </a>
                </div>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val el = doc.selectFirst("a.item")!!
        val res = api.parseSearchElement(el)

        assertNotNull("Search element result should not be null", res)
        assertEquals("Mayday", res!!.name)
        assertEquals("https://filmmakinesi.to/film/mayday-2026/", res.url)
        assertEquals(TvType.Movie, res.type)
        assertEquals(Score.from10("6.8"), res.score)
    }

    @Test
    fun testCloseLoadDecoder() {
        val js = """
            function nc0(uvx) {
                var anzgu = uvx.join('');
                var d25w = "xj1kV8NUjhX1BYHA71jJadM";
                var om88 = "bvY";
                return "";
            }
        """.trimIndent()

        val arr = """["==f1zOuNp","aPbkyAql0","fN2lM6ffq","6rF/nd13+","acP9LSu8g","utaac/6E8","e+vmPliVe","CWodoyhm/","yJtXiB/6N","yVcH41h5y","4Ct7u453Q","+2Of5ZUpn","7G+nOjjGN","TN+BGg+Rp","v1Eoi5j7u","43W+jk8+y"]"""
        val res = CloseLoadExtractor.decodeCloseLoad(js, arr)
        assertEquals("https://srv9.cdnimages3055.shop/hls/mayday-2026-webdl-trdual-tt28014327mp4-OSxZYB25jjD4.mp4/txt/master.txt", res)
    }
}
