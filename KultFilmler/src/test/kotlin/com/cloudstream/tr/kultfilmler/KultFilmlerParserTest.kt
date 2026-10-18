package com.cloudstream.tr.kultfilmler

import com.lagradost.cloudstream3.MovieSearchResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KultFilmlerParserTest {
    private val provider = KultFilmler()

    @Test
    fun testParseSearchElement() {
        val stream = javaClass.classLoader?.getResourceAsStream("kultfilmler_home.html")
        assertNotNull("kultfilmler_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val articles = doc.select("article")
        assertEquals(2, articles.size)

        val item1 = provider.parseSearchElement(articles[0]) as? MovieSearchResponse
        assertNotNull("Search element 1 failed to parse", item1)
        assertEquals("Pulp Fiction", item1?.name)
        assertEquals("https://kultfilmler.net/pulp-fiction-izle/", item1?.url)
        assertEquals(1994, item1?.year)

        val item2 = provider.parseSearchElement(articles[1]) as? MovieSearchResponse
        assertNotNull("Search element 2 failed to parse", item2)
        assertEquals("Fight Club", item2?.name)
        assertEquals("https://kultfilmler.net/fight-club-izle/", item2?.url)
        assertEquals(1999, item2?.year)
    }

    @Test
    fun testParseLoadMetadata() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("kultfilmler_detail.html")
        assertNotNull("kultfilmler_detail.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val loadResponse = provider.parseLoadMetadata(doc, "https://kultfilmler.net/pulp-fiction-izle/")
        assertNotNull("Load response failed to parse", loadResponse)
        assertEquals("Pulp Fiction", loadResponse?.name)
        assertEquals(1994, loadResponse?.year)
    }

    @Test
    fun testVidpapiResponseParsing() {
        val sampleJson = """
            {
              "hls": true,
              "videoImage": "https://moliy5.com/cdn/down/130dad/fvp.jpg",
              "videoSource": "https://vidpapi.xyz/cdn/hls/130dad/master.txt",
              "securedLink": "https://vidpapi.xyz/cdn/hls/130dad/master.m3u8?md5=test&expires=123",
              "downloadLinks": [],
              "attachmentLinks": []
            }
        """.trimIndent()

        val mapper = com.fasterxml.jackson.databind.ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
        val parsed = mapper.readValue(sampleJson, KultFilmler.VidpapiResponse::class.java)

        assertNotNull("VidpapiResponse parsing failed", parsed)
        assertEquals(true, parsed.hls)
        assertEquals("https://vidpapi.xyz/cdn/hls/130dad/master.m3u8?md5=test&expires=123", parsed.securedLink)
        assertEquals("https://vidpapi.xyz/cdn/hls/130dad/master.txt", parsed.videoSource)
    }
}

