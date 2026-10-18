package com.cloudstream.tr.jetfilmizle

import com.lagradost.cloudstream3.MovieSearchResponse
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.lagradost.cloudstream3.Score
import org.junit.Test

class JetFilmIzleParserTest {
    private val provider = JetFilmIzle()

    @Test
    fun testParseMovieCard() {
        val stream = javaClass.classLoader?.getResourceAsStream("jetfilmizle_home.html")
        assertNotNull("jetfilmizle_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val cards = doc.select("article.movie")
        assertEquals(2, cards.size)

        val item1 = provider.parseSearchItem(cards[0]) as? MovieSearchResponse
        assertNotNull("Item 1 failed to parse", item1)
        assertEquals("Kara Şövalye", item1?.name)
        assertEquals("https://jetfilmizle.vip/film/the-dark-knight-izle.html", item1?.url)
        assertEquals(2008, item1?.year)

        val item2 = provider.parseSearchItem(cards[1]) as? MovieSearchResponse
        assertNotNull("Item 2 failed to parse", item2)
        assertEquals("Gladyatör", item2?.name)
        assertEquals("https://jetfilmizle.vip/film/gladiator-izle.html", item2?.url)
        assertEquals(2000, item2?.year)
    }
    @Test
    fun testCurrentLiveCardsUseExactTitleAndMetadata() {
        val stream = javaClass.classLoader?.getResourceAsStream("jetfilmizle_current_search.html")
        assertNotNull("Current live fixture missing", stream)
        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        assertEquals(0, doc.select("article.movie, article.post, div.movie-item, div.film-kutusu").size)
        val items = provider.parseSearchPage(doc)
        assertEquals(2, items.size)
        val movie = items.first() as MovieSearchResponse
        assertEquals("Batman Ninja vs. Yakuza League", movie.name)
        assertEquals("https://jetfilmizle.now/film/batman-ninja-vs-yakuza-league", movie.url)
        assertEquals(2025, movie.year)
        assertEquals(Score.from10("6.2"), movie.score)
        assertEquals("https://jetfilmizle.now/uploads/films/2025/b/batman-ninja-vs-yakuza-league-poster-medium.webp", movie.posterUrl)
        assertTrue(provider.hasNextPage(doc, 1))
        assertFalse(provider.hasNextPage(doc, 2))
        assertFalse(provider.hasNextPage(Jsoup.parse("<div>No results</div>"), 1))
    }

    @Test
    fun testCurrentSearchRoutesEncodeQueryAndFollowLivePagination() {
        assertEquals("https://jetfilmizle.now/arama?q=Batman+%26+Robin", provider.searchUrl("Batman & Robin", 1))
        assertEquals("https://jetfilmizle.now/search/sayfa-2?q=batman", provider.searchUrl("batman", 2))
        assertEquals("https://jetfilmizle.now/filmler/sayfa-2", provider.mainPageUrl(provider.mainUrl + "/", 2))
        assertEquals("https://jetfilmizle.now/tur/aksiyon/sayfa-2", provider.mainPageUrl(provider.mainUrl + "/tur/aksiyon", 2))
    }
    @Test
    fun testCurrentSourceTabsPreserveZeroBasedIndicesAndPlayerTypes() {
        val stream = javaClass.classLoader?.getResourceAsStream("jetfilmizle_current_players.html")
        assertNotNull("Current player fixture missing", stream)
        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val requests = provider.playerRequests(doc)
        assertEquals(13, requests.size)
        val dubbed = requests.filter { it["player_type"] == "dublaj" }
        assertEquals((0..11).map(Int::toString), dubbed.map { it["source_index"] })
        assertTrue(requests.all { it["film_id"] == "17278" })
        assertEquals(mapOf("film_id" to "17278", "source_index" to "0", "player_type" to "genel"), requests.last())
        doc.body().append("""
            <button class="player-source-btn" data-source-index="0" data-player-type="dublaj">Duplicate</button>
            <button class="player-source-btn" data-source-index="-1" data-player-type="dublaj">Invalid</button>
            <button class="player-source-btn" data-source-index="bad" data-player-type="dublaj">Invalid</button>
            <button class="player-source-btn" data-source-index="1" data-player-type="invalid">Invalid</button>
        """.trimIndent())
        assertEquals(requests, provider.playerRequests(doc))
        doc.select("input[name=film_id]").remove()
        assertTrue(provider.playerRequests(doc).isEmpty())
        doc.body().append("<input name='film_id' value='bad'>")
        assertTrue(provider.playerRequests(doc).isEmpty())
    }

    @Test
    fun testAjaxIframeResponsesResolveRelativeUrlsAndExcludeTrailers() {
        // Public jetplayer responses use iframe HTML; source 6 is protocol relative.
        val doc = Jsoup.parse("""
            <iframe src="https://vk.com/video_ext.php?oid=-230451560&amp;id=456239283&amp;hd=1"></iframe>
            <iframe src="//ok.ru/videoembed/10137925978671"></iframe>
            <iframe data-src="https://vtbe.to/embed-zzc3wzkaok2x.html"></iframe>
            <iframe src="//ok.ru/videoembed/10137925978671"></iframe>
            <iframe id="trailerIframe" src="https://media.example/trailer"></iframe>
            <iframe src="https://www.youtube.com/embed/trailer"></iframe>
            <iframe src="https://www.youtube-nocookie.com/embed/trailer"></iframe>
            <iframe src="https://youtu.be/trailer"></iframe>
            <iframe src="about:blank"></iframe>
            <iframe src="javascript:void(0)"></iframe>
        """.trimIndent(), provider.mainUrl)
        assertEquals(listOf(
            "https://vk.com/video_ext.php?oid=-230451560&id=456239283&hd=1",
            "https://ok.ru/videoembed/10137925978671",
            "https://vtbe.to/embed-zzc3wzkaok2x.html"
        ), provider.playbackFrames(doc))
    }
}
