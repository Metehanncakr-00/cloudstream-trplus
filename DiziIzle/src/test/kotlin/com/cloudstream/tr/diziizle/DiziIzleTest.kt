package com.cloudstream.tr.diziizle

import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class DiziIzleTest {
    private val provider = DiziIzle()

    private fun fixture(name: String) = Jsoup.parse(javaClass.classLoader!!
        .getResourceAsStream(name)!!.bufferedReader().use { it.readText() })

    @Test
    fun homepageUsesActualCatalogAndObservedPagination() {
        val root = fixture("diziizle_homepage.html")
        assertTrue(provider.parseCatalog(root).isEmpty())
        assertEquals(3, root.select("#content .right ul a[title$=' izle']").size)
        assertEquals("/diziler/", provider.mainPage.single().data)
        assertEquals("https://www.diziizle.you/diziler/", provider.catalogPageUrl(1))
        assertEquals("https://www.diziizle.you/diziler/sayfa-2/", provider.catalogPageUrl(2))
        val catalog = fixture("diziizle_catalog.html")
        assertEquals(listOf("Alıkara", "1 Erkek 1 Kadın", "1 Erkek 1 Kadın 1 Çocuk"),
            provider.parseCatalog(catalog).map { it.name })
        assertTrue(provider.hasNextCatalogPage(catalog, 1))
        assertFalse(provider.hasNextCatalogPage(catalog, 36))
        assertFalse(provider.hasNextCatalogPage(root, 1))
    }

    @Test
    fun sourceCatalogDoesNotAppendEpisodeCountToTitle() {
        val items = provider.parseCatalog(fixture("tuzlu_kahve_search.html"))
        assertEquals(listOf("Tuzlu Kahve"), items.map { it.name })
        assertEquals("https://www.diziizle.you/tuzlu-kahve/", items.single().url)
        assertTrue(items.single().posterUrl!!.endsWith("/tuzlu-kahve.jpg"))
    }

    @Test
    fun fullEpisodeFixtureExcludesTrailerAndPreservesGaps() {
        val document = fixture("tuzlu_kahve_detail.html")
        assertEquals((1..7).toList(), provider.parseEpisodes(document).map { it.episode })
        document.select("li.bl-satir[data-b=2]").remove()
        val episodes = provider.parseEpisodes(document)
        assertEquals(listOf(1, 3, 4, 5, 6, 7), episodes.map { it.episode })
        assertTrue(episodes.all { it.season == 1 })
        assertTrue(episodes.none { it.data.contains("bolum-8") })
    }

    @Test
    fun allKnownPagesAreFetchedWithoutReturningPartialSeason() = runTest {
        val first = fixture("tuzlu_kahve_detail.html")
        first.selectFirst("ol")!!.attr("data-sayfa-son", "2")
        val calls = mutableListOf<String>()
        val next = Jsoup.parse("""
            <ol class="bl-liste" data-sayfa="2"><li class="bl-satir">
            <a href="/tuzlu-kahve/bolum-9/"><span class="bl-no">9</span>
            <span class="bl-ad">9. Bölüm</span></a></li></ol>
        """)
        val episodes = provider.fetchEpisodes(first) { calls.add(it); next }
        assertEquals(listOf("https://www.diziizle.you/tuzlu-kahve/sayfa-2/"), calls)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 9), episodes!!.map { it.episode })
        assertNull(provider.fetchEpisodes(first) { null })
        assertNull(provider.fetchEpisodes(first) { first })
        first.selectFirst("ol")!!.removeAttr("data-sayfa-son")
        assertNull(provider.fetchEpisodes(first) { next })
    }

    @Test
    fun playerUrlKeepsRawQueryAndRejectsForeignOrMalformedUrls() {
        fun parse(value: String) = provider.parsePlayerUrl(Jsoup.parse("<iframe id='player' src='$value'></iframe>"))
        assertEquals("https://www.diziizle.you/player/?v=123", parse("/player/?v=123"))
        assertNull(parse("https://other.example/player/?v=123"))
        assertNull(parse("/player/?v=123&amp;next=evil"))
        assertNull(parse("/player/?v=not-a-number"))
        assertNull(parse("//user@www.diziizle.you/player/?v=123"))
    }

    @Test
    fun escapedPublicHlsIsDecodedAndUnsafeMediaIsRejected() {
        assertEquals("https://video.twimg.com/public/master.m3u8", provider.parseHls(
            """sources: [{"file":"https:\/\/video.twimg.com\/public\/master.m3u8"}]"""))
        for (url in listOf("http://video.twimg.com/a.m3u8", "https://other.example/a.m3u8",
            "https://user@video.twimg.com/a.m3u8", "https://video.twimg.com:444/a.m3u8",
            "https://video.twimg.com/a.mp4")) {
            assertNull(provider.parseHls("""{"file":"$url"}"""))
        }
    }

    @Test
    fun masterIsEmittedIntactWithUnknownQualityToKeepSeparateAudio() = runTest {
        val master = "https://video.twimg.com/public/master.m3u8"
        val link = provider.masterLink(master)
        assertEquals(master, link.url)
        assertEquals(ExtractorLinkType.M3U8, link.type)
        assertEquals(Qualities.Unknown.value, link.quality)
        assertTrue(link.headers.isEmpty())
    }
}
