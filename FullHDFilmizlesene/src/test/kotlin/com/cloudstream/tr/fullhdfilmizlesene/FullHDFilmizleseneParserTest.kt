package com.cloudstream.tr.fullhdfilmizlesene

import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.cloudstream.tr.core.network.StreamValidator
import com.cloudstream.tr.core.network.StreamHttpTransport
import com.cloudstream.tr.core.network.StreamHttpResponse

class FullHDFilmizleseneParserTest {
    private val provider = FullHDFilmizlesene()

    @Before
    fun setUp() {
        StreamValidator.transport = StreamHttpTransport { _, _ ->
            StreamHttpResponse(
                code = 200,
                contentType = "application/vnd.apple.mpegurl",
                openStream = { "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n".byteInputStream() }
            )
        }
    }

    @After
    fun tearDown() {
        StreamValidator.resetTransport()
    }

    @Test
    fun testParseCard() {
        val stream = javaClass.classLoader?.getResourceAsStream("fullhdfilmizlesene_home.html")
        assertNotNull("fullhdfilmizlesene_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val cards = doc.select(".list li.film")
        assertEquals(1, cards.size)

        val item = provider.parseCard(cards[0]) as? MovieSearchResponse
        assertNotNull("Item failed to parse", item)
        assertEquals("Hızlı ve Öfkeli 10 - Fast X", item?.name)
        assertEquals("https://www.fullhdfilmizlesene.now/film/hizli-ve-ofkeli-10/", item?.url)
        assertEquals(2023, item?.year)
        assertEquals("https://img.fullhdfilmizlesene.now/poster/film/hizli-ve-ofkeli-10.jpg", item?.posterUrl)
    }

    @Test
    fun testParseLoadMetadataAndStablePayload() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("fullhdfilmizlesene_detail.html")
        assertNotNull("fullhdfilmizlesene_detail.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val url = "https://www.fullhdfilmizlesene.now/film/hizli-ve-ofkeli-10/"
        val res = provider.parseLoadMetadata(doc, url) as? MovieLoadResponse

        assertNotNull("Detail failed to parse", res)
        assertEquals("Hızlı ve Öfkeli 10", res?.name)
        assertEquals(url, res?.url)
        assertEquals(url, res?.dataUrl)
        assertFalse("dataUrl must not be an m3u8 stream URL", res?.dataUrl?.contains(".m3u8") ?: false)
        assertFalse("dataUrl must not contain rapidvid stream tokens", res?.dataUrl?.contains("token") ?: false)
        assertEquals(2023, res?.year)
        assertTrue("Plot should match fixture", res?.plot?.contains("Dom Toretto") ?: false)
        assertTrue("Tags should contain Aksiyon Filmleri", res?.tags?.contains("Aksiyon Filmleri") ?: false)
    }

    @Test
    fun testScxUrlExtraction() {
        val stream = javaClass.classLoader?.getResourceAsStream("fullhdfilmizlesene_detail.html")
        assertNotNull("detail fixture missing", stream)
        val html = stream!!.bufferedReader().readText()

        val urls = FullHDFilmizlesene.extractScxIframeUrls(html)
        assertEquals(1, urls.size)
        assertEquals("https://rapidvid.org/vod/v1xc70229b9", urls[0])
    }

    @Test
    fun testRapidvidAvDecryption() {
        val target = "https://example.com/fake_playlist.m3u8"
        val inner = java.util.Base64.getEncoder().encodeToString(target.toByteArray(Charsets.UTF_8))
        val key = "K9L"
        val sb = java.lang.StringBuilder()
        for (i in inner.indices) {
            val r = key[i % 3]
            val c = (inner[i].code + (r.code % 5 + 1)).toChar()
            sb.append(c)
        }
        val encodedBytes = sb.toString().toByteArray(Charsets.ISO_8859_1)
        val b64 = java.util.Base64.getEncoder().encodeToString(encodedBytes)
        val sampleToken = b64.reversed().trimStart('=')
        
        val decrypted = FullHDFilmizlesene.decryptRapidvidAv(sampleToken)
        assertNotNull(decrypted)
        assertEquals(target, decrypted)
    }

    @Test
    fun testOfflineRapidvidParsing() = runBlocking {
        val target = "https://example.invalid/rapidvid.m3u8"
        // Generate synthetic token for `target`
        val inner = java.util.Base64.getEncoder().encodeToString(target.toByteArray(Charsets.UTF_8))
        val key = "K9L"
        val sb = java.lang.StringBuilder()
        for (i in inner.indices) {
            val r = key[i % 3]
            val c = (inner[i].code + (r.code % 5 + 1)).toChar()
            sb.append(c)
        }
        val encodedBytes = sb.toString().toByteArray(Charsets.ISO_8859_1)
        val b64 = java.util.Base64.getEncoder().encodeToString(encodedBytes)
        val token = b64.reversed().trimStart('=')

        val stream = javaClass.classLoader?.getResourceAsStream("fullhdfilmizlesene_rapidvid_response.html")
        assertNotNull("fixture missing", stream)
        val rawHtml = stream!!.bufferedReader().readText()
        val html = rawHtml.replace("SYNTHETIC_TOKEN", token)

        val extLinks = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
        val subFiles = mutableListOf<com.lagradost.cloudstream3.SubtitleFile>()

        val result = provider.parseRapidvidResponse(html, { subFiles.add(it) }, { extLinks.add(it) })
        assertTrue(result)

        assertEquals(1, extLinks.size)
        assertEquals("RapidVid", extLinks[0].name)
        assertEquals(target, extLinks[0].url)
        assertEquals("https://rapidvid.org/", extLinks[0].referer)

        assertEquals(1, subFiles.size)
        assertEquals("Türkçe", subFiles[0].lang)
        assertEquals("https://example.invalid/tr.vtt", subFiles[0].url)
    }

    @Test
    fun testOfflineRapidvidMalformed() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("fullhdfilmizlesene_rapidvid_malformed.html")
        assertNotNull("fixture missing", stream)
        val html = stream!!.bufferedReader().readText()

        val result = provider.parseRapidvidResponse(html, { fail() }, { fail() })
        assertFalse(result)
    }
}
