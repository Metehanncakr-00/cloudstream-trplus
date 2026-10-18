package com.cloudstream.tr.yesilcamtv

import com.lagradost.cloudstream3.MovieSearchResponse
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class YesilCamTvParserTest {
    private val provider = YesilCamTv()

    @Test
    fun testParseSearchItem() {
        val stream = javaClass.classLoader?.getResourceAsStream("yesilcam_home.html")
        assertNotNull("yesilcam_home.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val posts = doc.select("article.post")
        assertEquals(2, posts.size)

        val item1 = provider.parseSearchItem(posts[0]) as? MovieSearchResponse
        assertNotNull("Item 1 failed to parse", item1)
        assertEquals("Tosun Paşa", item1?.name)
        assertEquals("https://yesilcamtv.com.tr/tosun-pasa-izle/", item1?.url)
        assertEquals(1976, item1?.year)

        val item2 = provider.parseSearchItem(posts[1]) as? MovieSearchResponse
        assertNotNull("Item 2 failed to parse", item2)
        assertEquals("Kibar Feyzo", item2?.name)
        assertEquals(1978, item2?.year)
    }

    @Test
    fun testParseLoadMetadata() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("yesilcam_detail.html")
        assertNotNull("yesilcam_detail.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val loadResponse = provider.parseLoadMetadata(doc, "https://yesilcamtv.com.tr/tosun-pasa-izle/")
        assertNotNull("Load response failed to parse", loadResponse)
        assertEquals("Tosun Paşa", loadResponse?.name)
        assertEquals(1976, loadResponse?.year)
    }

    @Test
    fun testRumbleExtractorCandidateDiscovery() {
        val sampleHtml = """
            <script>
            m.f["v6ybxgw"]={"fps":30,"w":640,"h":360,"u":{"timeline":{"url":"https:\/\/hugh.cdn.rumble.cloud\/video\/fwe2\/f6\/s8\/2\/S\/J\/7\/r\/SJ7rz.Faa.mp4","meta":{"bitrate":11,"size":7051956,"w":320,"h":180}},"audio":{"url":"https:\/\/hugh.cdn.rumble.cloud\/video\/fwe2\/f6\/s8\/2\/S\/J\/7\/r\/SJ7rz.Gaa.aac","meta":{"bitrate":192,"size":113293689,"w":0,"h":0}},"hls":{"url":"https:\/\/rumble.com\/hls-vod\/zRncRStP5sY\/playlist.m3u8"}},"ua":{"tar":{"360":{"url":"https:\/\/hugh.cdn.rumble.cloud\/video\/fwe2\/f6\/s8\/2\/S\/J\/7\/r\/SJ7rz.baa.tar?r_file=chunklist.m3u8&r_type=application%2Fvnd.apple.mpegurl&r_range=401625088-401672295","meta":{"bitrate":680,"size":401674240,"w":640,"h":360}},"480":{"url":"https:\/\/hugh.cdn.rumble.cloud\/video\/fwe2\/f6\/s8\/2\/S\/J\/7\/r\/SJ7rz.caa.tar?r_file=chunklist.m3u8&r_type=application%2Fvnd.apple.mpegurl&r_range=624546816-624594108","meta":{"bitrate":1058,"size":624599040,"w":854,"h":480}}},"timeline":{"180":{"url":"https:\/\/hugh.cdn.rumble.cloud\/video\/fwe2\/f6\/s8\/2\/S\/J\/7\/r\/SJ7rz.Faa.mp4","meta":{"bitrate":11,"size":7051956,"w":320,"h":180}}},"hls":{"auto":{"url":"https:\/\/rumble.com\/hls-vod\/zRncRStP5sY\/playlist.m3u8"}}}};
            </script>
        """.trimIndent()

        val candidates = com.cloudstream.tr.core.extractors.RumbleExtractor.extractStreamCandidates(sampleHtml)
        assertNotNull("Candidates should not be null", candidates)
        assertEquals("Should discover HLS stream", 1, candidates.size)
        assertEquals("https://rumble.com/hls-vod/zRncRStP5sY/playlist.m3u8", candidates[0].first)
        assertEquals(com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8, candidates[0].second)
    }
}
