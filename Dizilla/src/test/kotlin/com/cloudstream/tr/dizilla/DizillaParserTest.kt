package com.cloudstream.tr.dizilla

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DizillaParserTest {
    private val provider = Dizilla()

    @Test
    fun testAesDecryption() {
        val cipher = "+JiCucVLlbpjJ0I90H5Jplk+838LBRiaJU/zbneGW5PX1WNyQy7kFH15Ea6bpV9O/Mm8Y20W/NLZv/t+wa6U5v/WfBUSnuCUc85YpSnnvdByoVhh81U5J8Nng7ZTYaitEJu1R4AC3W1yj1p3C+4jTAW3JdQgOvnxP7kFlS3T1jYfkkmW81oAiUFF1LjtjMmUzJ2NG7DA5VdIgLixS2+Q18aGwfmcY1+DrIrB3heNtITkYMnCRl5zmeJqlgvczn+SzA8ENT8oTYmBPpT/IVZtng=="
        val plain = Dizilla.decryptSecureData(cipher)
        assertTrue("Plain text missing series title", plain.contains("Breaking Bad"))
        assertTrue("Plain text missing release date", plain.contains("2008"))
    }

    @Test
    fun testParseLoadMetadata() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("dizilla_detail.html")
        assertNotNull("dizilla_detail.html fixture missing", stream)

        val doc = Jsoup.parse(stream!!, "UTF-8", provider.mainUrl)
        val loadResponse = provider.parseLoadMetadata(doc, "https://dizilla.now/dizi/breaking-bad")
        assertNotNull("Load response failed to parse", loadResponse)
        assertEquals("Breaking Bad", loadResponse?.name)
        assertEquals(2008, loadResponse?.year)
    }

    @Test
    fun testSearchResponseDecryption() {
        val stream = javaClass.classLoader?.getResourceAsStream("real_search_resp.txt")
        val cipher = stream?.bufferedReader()?.readText()?.trim()
        assertNotNull("real_search_resp.txt missing", cipher)
        val decrypted = Dizilla.decryptSecureData(cipher!!)
        assertTrue(decrypted.contains("Breaking Bad"))
    }

    @Test
    fun testEpisodeDecryption() {
        val stream = javaClass.classLoader?.getResourceAsStream("dizilla_ep_cipher.txt")
        val cipher = stream?.bufferedReader()?.readText()?.trim()
        assertNotNull("dizilla_ep_cipher.txt missing", cipher)
        val decrypted = Dizilla.decryptSecureData(cipher!!)
        
        val iframeSrcs = Regex("""<iframe[^>]+src=\\?["']([^\\"'>]+)""").findAll(decrypted).map { it.groupValues[1].replace("\\/", "/") }.toList()
        println("Found iframeSrcs in decrypted episode: $iframeSrcs")
        assertTrue(iframeSrcs.isNotEmpty())
    }
}
