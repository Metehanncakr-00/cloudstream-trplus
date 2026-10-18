package com.cloudstream.tr.startv

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.AppUtils
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class StarTVTest {
    private val provider = StarTV()

    @Test
    fun episodePagingContinuesUntilShortPageWithoutUsingCount() = kotlinx.coroutines.test.runTest {
        val all = (1..101).map { episode(1, it) }
        val offsets = mutableListOf<Int>()
        val items = provider.fetchEpisodeItems { skip ->
            offsets.add(skip)
            StarEpisodePage(all.drop(skip).take(48))
        }
        assertEquals(listOf(0, 48, 96), offsets)
        assertEquals(101, items?.size)
    }

    @Test
    fun repeatedEpisodePageFailsWithoutReturningPartialSeason() = kotlinx.coroutines.test.runTest {
        assertNull(provider.fetchEpisodeItems { StarEpisodePage((1..48).map { episode(1, it) }) })
    }

    @Test
    fun catalogKeepsSeriesAndFiltersEpisodeLinks() {
        val document = Jsoup.parse("""
            <a href="/dizi/tuzlu-kahve"><img alt="Tuzlu Kahve" src="/poster.jpg"></a>
            <a href="/dizi/tuzlu-kahve"><img alt="Tuzlu Kahve" src="/poster.jpg"></a>
            <a href="/dizi/tuzlu-kahve/bolumler/1-bolum"><img alt="1. Bölüm"></a>
            <a href="/dizi/yali-capkini"><img alt="Yalı Çapkını"></a>
        """)
        val items = provider.parseCatalog(document)
        assertEquals(listOf("Tuzlu Kahve", "Yalı Çapkını"), items.map { it.name })
        assertEquals(TvType.TvSeries, items.first().type)
        assertEquals("https://www.startv.com.tr/dizi/tuzlu-kahve", items.first().url)
    }

    @Test
    fun episodeApiIdIsExtractedFromOfficialPage() {
        assertEquals("ab0123", provider.parseSeriesId("/api/tv-series/ab0123/episodes?skip=0"))
        assertNull(provider.parseSeriesId("/api/tv-series/not-an-id/episodes"))
    }

    @Test
    fun cumulativeEpisodeNumbersBecomeSeasonRelativeWithoutClosingGaps() {
        val items = listOf(episode(1, 1), episode(1, 3), episode(1, 36),
            episode(2, 37), episode(2, 39), episode(2, 73), episode(3, 74))
        val mapped = provider.parseEpisodes(items.reversed())
        assertEquals(listOf(1, 3, 36, 1, 3, 37, 1), mapped.map { it.episode })
        assertEquals(listOf(1, 1, 1, 2, 2, 2, 3), mapped.map { it.season })
        assertEquals("37. Bölüm", mapped[3].name)
        assertEquals("ref-37", mapped[3].data)
    }

    @Test
    fun missingPrecedingSeasonDoesNotGuessEpisodeOffset() {
        assertTrue(provider.parseEpisodes(listOf(episode(3, 74))).isEmpty())
    }

    @Test
    fun missingSeasonBoundaryDoesNotShiftLaterEpisodes() {
        val episodes = provider.parseEpisodes(listOf(episode(1, 35), episode(2, 37), episode(2, 38)))
        assertEquals(listOf(1), episodes.map { it.season })
    }

    @Test
    fun tuzluKahveAvailableEpisodesDoNotInventMissingTmdbEpisodes() {
        val fixture = javaClass.classLoader!!.getResourceAsStream("tuzlu_kahve_episodes.json")!!
            .bufferedReader().use { it.readText() }
        val response = AppUtils.parseJson<StarEpisodePage>(fixture)
        val episodes = provider.parseEpisodes(response.items.orEmpty())
        assertEquals(listOf(1, 2, 3), episodes.map { it.episode })
        assertEquals(listOf("1. Bölüm", "2. Bölüm", "3. Bölüm"), episodes.map { it.name })
        assertEquals(response.items.orEmpty().sortedBy { it.episodeNo }.map { it.video!!.referenceId },
            episodes.map { it.data })
        assertTrue(episodes.first().posterUrl!!.startsWith("https://www.startv.com.tr/images/"))
        assertFalse(episodes.any { it.episode == 7 })
    }

    @Test
    fun playerUsesFlavorsHlsAndRejectsForeignHosts() {
        val response = AppUtils.parseJson<StarVideoResponse>("""
            {"data":{"hls":"https://wrong.invalid/master.m3u8",
            "flavors":{"hls":"https://startv-p3.mncdn.com/path/master.m3u8?token=example"}}}
        """)
        assertEquals("https://startv-p3.mncdn.com/path/master.m3u8?token=example", provider.parseHls(response))
        assertNull(provider.parseHls(StarVideoResponse(StarVideoData(StarFlavors("https://foreign.invalid/a.m3u8")))))
        assertNull(provider.parseHls(StarVideoResponse(StarVideoData(StarFlavors("http://startv-p3.mncdn.com/a.m3u8")))))
    }

    @Test
    fun publicPlayerRuntimeParameterIsParsedWithoutEmbeddingItsValue() {
        assertEquals("example-runtime", provider.parseRuntimeParameter("this.secret=e.SecretKey||\"example-runtime\";"))
        assertNull(provider.parseRuntimeParameter("this.secret=e.SecretKey;"))
    }

    private fun episode(season: Int, number: Int) = StarEpisode(
        url = "/dizi/example/bolumler/$number-bolum",
        title = "$number. Bölüm",
        video = StarVideo("ref-$number"),
        seasonNo = season,
        episodeNo = number
    )
}
