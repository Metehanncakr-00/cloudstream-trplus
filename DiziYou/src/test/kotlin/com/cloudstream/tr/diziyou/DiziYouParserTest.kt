package com.cloudstream.tr.diziyou

import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI
import java.net.URLDecoder

class DiziYouParserTest {
    private val provider = DiziYou()

    private fun fixture(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.use {
        Jsoup.parse(it, "UTF-8", provider.mainUrl)
    }

    @Test
    fun testCurrentSearchCardsExcludeGlobalArchiveAndKeepLegacyCards() {
        val items = provider.parseSearchResults(fixture("diziyou_search_current.html"))
        assertEquals(listOf("The Office", "Arcane"), items.map { it.name })
        val office = items[0] as TvSeriesSearchResponse
        assertEquals("${provider.mainUrl}/the-office/", office.url)
        assertEquals("${provider.mainUrl}/posters/the-office.jpg", office.posterUrl)
        assertEquals(2005, office.year)
        val arcane = items[1] as TvSeriesSearchResponse
        assertEquals("${provider.mainUrl}/dizi/arcane", arcane.url)
        assertEquals("${provider.mainUrl}/posters/arcane.jpg", arcane.posterUrl)
        assertEquals(2021, arcane.year)
    }

    @Test
    fun testSearchUrlEncodesQueryOnceAndPreservesPagination() {
        for (query in listOf("A&B#1+C?", "İstanbul ışığı Çığ öyküsü")) {
            val first = URI(provider.buildSearchUrl(query, 1))
            val second = URI(provider.buildSearchUrl(query, 2))
            assertEquals("/", first.path)
            assertEquals("/page/2/", second.path)
            assertEquals(first.rawQuery, second.rawQuery)
            assertNull(first.rawFragment)
            assertNull(second.rawFragment)
            assertEquals(1, first.rawQuery.split('&').size)
            assertEquals("s", first.rawQuery.substringBefore('='))
            assertEquals(query, URLDecoder.decode(first.rawQuery.substringAfter('='), "UTF-8"))
        }
    }

    @Test
    fun testCurrentDetailReadsLabeledYearAndSynopsisWithoutMetadata() = runBlocking {
        val doc = fixture("diziyou_detail_current.html")
        val res = provider.parseLoadMetadata(doc, "${provider.mainUrl}/the-office/") as TvSeriesLoadResponse
        assertEquals("The Office", res.name)
        assertEquals(2005, res.year)
        assertEquals("Dunder-Mifflin synopsis", res.plot)
        assertTrue("Parsing must not mutate the source document", doc.select("#icerikcat2").isNotEmpty())
        val episode = res.episodes.single()
        assertEquals("${provider.mainUrl}/the-office-1-sezon-1-bolum/", episode.data)
        assertEquals(1, episode.season)
        assertEquals(1, episode.episode)
        assertEquals("1. Sezon 1. Bölüm", episode.name)
    }

    @Test
    fun testSyntheticDetailPrefersNormalizedExplicitYearOverTitleAndDistractors() = runBlocking {
        val res = provider.parseLoadMetadata(fixture("diziyou_detail_synthetic.html"), "${provider.mainUrl}/kayip-gezegen/")!!
        assertEquals("Kayıp Gezegen", res.name)
        assertEquals(2017, res.year)
        assertEquals("Bir keşif yolculuğu.", res.plot)
    }

    @Test
    fun testDetailYearFallbacksRejectCountsAndDates() = runBlocking {
        suspend fun year(body: String) = provider.parseLoadMetadata(Jsoup.parse(body), "${provider.mainUrl}/synthetic/")?.year
        assertNull(year("<h1>Synthetic</h1><div class='info'><span>192 episodes</span><span>24 Mart 2005</span></div>"))
        assertEquals(1998, year("<h1>Synthetic (1998)</h1><div class='info'><span>192 episodes</span></div>"))
        assertEquals(2021, year("<h1>Synthetic</h1><span class='year'>2021</span>"))
        assertEquals(2007, year("<h1>Synthetic</h1><div class='info'><span>192 episodes</span><span>2007</span></div>"))
        assertNull(year("<h1>Synthetic</h1><span class='dizimeta'>Yapım Yılı:</span> <span class='dizimeta'>Oyuncular:</span>1984<br>"))
        assertNull(year("<h1>Synthetic</h1><span class='dizimeta'>Yapım Yılı:</span>9999<br>"))
    }

    @Test
    fun testParseCard() {
        val stream = javaClass.classLoader?.getResourceAsStream("diziyou_home.html")
        assertNotNull("diziyou_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val cards = doc.select("div.cat-item")
        assertEquals(1, cards.size)

        val item1 = provider.parseCard(cards[0]) as? TvSeriesSearchResponse
        assertNotNull("Item 1 failed to parse", item1)
        assertEquals("Arcane", item1?.name)
        assertEquals("https://www.diziyou.one/dizi/arcane", item1?.url)
        assertEquals(2021, item1?.year)
    }

    @Test
    fun testParseLoadMetadataAndStablePayload() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("diziyou_detail.html")
        assertNotNull("diziyou_detail.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val url = "https://www.diziyou.one/dizi/arcane"
        val res = provider.parseLoadMetadata(doc, url) as? TvSeriesLoadResponse

        assertNotNull("Detail failed to parse", res)
        assertEquals("Arcane", res?.name)
        assertEquals(url, res?.url)
        assertEquals(2021, res?.year)
        assertTrue("Plot should contain Piltover", res?.plot?.contains("Piltover") ?: false)
        assertTrue("Tags should contain Animasyon", res?.tags?.contains("Animasyon") ?: false)

        val episodes = res?.episodes ?: emptyList()
        assertTrue("Episodes should not be empty", episodes.isNotEmpty())
        assertEquals(2, episodes.size)

        for (ep in episodes) {
            // Invariant: Episode.data must be stable episode page URL, never an ephemeral stream URL
            assertTrue("Episode data must start with http", ep.data.startsWith("http"))
            assertFalse("Episode data must not contain .m3u8", ep.data.contains(".m3u8"))
            assertFalse("Episode data must not contain token", ep.data.contains("token"))
        }
    }

    @Test
    fun testOfflinePlayerExtraction() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("diziyou_player.html")
        assertNotNull("fixture missing", stream)
        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)

        val itemId = provider.extractPlayerId(doc)
        assertEquals("test_item_id_123", itemId)

        val extLinks = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
        val subFiles = mutableListOf<com.lagradost.cloudstream3.SubtitleFile>()

        val result = provider.emitNativeLinks(itemId, { subFiles.add(it) }, { extLinks.add(it) })
        assertTrue(result)

        assertEquals(2, extLinks.size)
        assertEquals("DiziYou HLS", extLinks[0].name)
        assertEquals("https://storage.diziyou.one/episodes/test_item_id_123/play.m3u8", extLinks[0].url)
        assertEquals(Qualities.Unknown.value, extLinks[0].quality)
        assertEquals("https://www.diziyou.one/", extLinks[0].referer)

        assertEquals("DiziYou HLS (_tr adayı; ses doğrulanmadı)", extLinks[1].name)
        assertEquals("https://storage.diziyou.one/episodes/test_item_id_123_tr/play.m3u8", extLinks[1].url)
        assertEquals("https://www.diziyou.one/", extLinks[1].referer)

        assertEquals(2, subFiles.size)
        assertEquals("Türkçe", subFiles[0].lang)
        assertEquals("https://storage.diziyou.one/subtitles/test_item_id_123/tr.vtt", subFiles[0].url)
        assertEquals("İngilizce", subFiles[1].lang)
        assertEquals("https://storage.diziyou.one/subtitles/test_item_id_123/en.vtt", subFiles[1].url)
        assertTrue("Subtitle and video trees must remain distinct", subFiles.none { it.url.contains("/episodes/") })

        val candidates = provider.getFallbackIframeCandidates(doc)
        assertEquals(1, candidates.size)
        assertEquals("https://example.invalid/fallback", candidates[0])
    }

    @Test
    fun testOfflinePlayerMalformed() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("diziyou_player_malformed.html")
        assertNotNull("fixture missing", stream)
        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)

        val itemId = provider.extractPlayerId(doc)
        assertNull(itemId)

        val result = provider.emitNativeLinks(itemId, { fail() }, { fail() })
        assertFalse(result)
    }
}
