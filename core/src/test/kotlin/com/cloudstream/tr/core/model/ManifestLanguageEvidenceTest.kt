package com.cloudstream.tr.core.model

import com.cloudstream.tr.core.network.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ManifestLanguageEvidenceTest {
    @Test fun normalizesValidatedIsoLanguagesAliasesAndScriptRegionTags() {
        listOf("fa", "fas", "per", "fa-IR", "fas-Arab-IR").forEach { assertEquals("fa", ManifestLanguageParser.normalize(it)) }
        listOf("nl", "nld", "dut", "nl-NL").forEach { assertEquals("nl", ManifestLanguageParser.normalize(it)) }
        assertEquals("zh", ManifestLanguageParser.normalize("zho-Hant-TW"))
        assertEquals("sw", ManifestLanguageParser.normalize("swa-KE"))
        assertEquals("he", ManifestLanguageParser.normalize("iw-IL"))
        listOf("und", "mul", "zxx", "xyz", "garbage", "TR Dublaj", "tr-", "tr--TR", "tr-private", "tr-x-foo", "x-tr", "fake-TR").forEach {
            assertNull(it, ManifestLanguageParser.normalize(it))
        }
        java.util.Locale.getISOLanguages().forEach { assertNotNull(it, ManifestLanguageParser.normalize(it)) }
        assertEquals(0, ManifestLanguageEvidence(subtitles = setOf("fa")).tier())
    }
    private fun parse(text: String, truncated: Boolean = false) = ManifestLanguageParser.parse(text.toByteArray(), truncated)
    private val master = """#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="English",LANGUAGE="en"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="Turkish",LANGUAGE="tur"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="unused",NAME="Fake",LANGUAGE="fr"
#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="s",NAME="Turkish",LANGUAGE="tr-TR",URI="subs.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=2000000,AUDIO="a",SUBTITLES="s"
video.m3u8
"""

    @Test fun hlsUsesOnlyReferencedExplicitLanguageAndSeparatesSubtitles() {
        assertEquals(ManifestLanguageEvidence(setOf("en", "tr"), setOf("tr")), parse(master))
        assertEquals(3, parse(master).tier())
        assertEquals(4, parse(master).tier("ORIGINAL"))
    }

    @Test fun fakeNamesMissingReferencesAndPartialAttributesCannotCertifyTurkish() {
        assertEquals(ManifestLanguageEvidence(), parse("#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",NAME=\"TR Dublaj\"\n#EXT-X-STREAM-INF:AUDIO=\"a\"\nx_tr.m3u8"))
        assertEquals(ManifestLanguageEvidence(), parse("#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",LANGUAGE=\"tr"))
        assertEquals(ManifestLanguageEvidence(), parse(master.substringBefore("#EXT-X-STREAM-INF")))
        assertEquals(ManifestLanguageEvidence(), parse("<html>$master</html>"))
        assertEquals(ManifestLanguageEvidence(), parse(""))
        assertEquals(ManifestLanguageEvidence(), parse(master.substringBefore("video.m3u8") + "vid", true))
    }

    @Test fun dashInheritsExplicitAudioAndTextMetadataButRejectsEntitiesAndPartialXml() {
        val dash = """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"><Period><AdaptationSet contentType="audio" lang="tr-TR"><Representation id="a"/></AdaptationSet><AdaptationSet mimeType="text/vtt" lang="eng"><Representation id="s"/></AdaptationSet></Period></MPD>"""
        assertEquals(ManifestLanguageEvidence(setOf("tr"), setOf("en")), parse(dash))
        assertEquals(setOf("en"), parse(dash.replace("<Representation id=\"a\"/>", "<Representation id=\"a\" lang=\"en\"/>")).audio)
        assertTrue(parse(dash.replace("<Representation id=\"a\"/>", "<Representation id=\"a\" lang=\"und\"/>")).audio.isEmpty())
        assertEquals(ManifestLanguageEvidence(), parse(dash, true))
        assertEquals(ManifestLanguageEvidence(), parse("<!DOCTYPE MPD SYSTEM 'https://invalid.test/evil'><MPD/>"))
        assertNull(ManifestLanguageParser.normalize("und"))
        assertNull(ManifestLanguageParser.normalize("fake-TR"))
    }

    @Test fun tierIsLexicographicAndUnknownIsNotOriginal() {
        assertEquals(listOf(3, 2, 1, 0), listOf(
            ManifestLanguageEvidence(setOf("tr")), ManifestLanguageEvidence(subtitles = setOf("tr")),
            ManifestLanguageEvidence(), ManifestLanguageEvidence(setOf("en"))).map { it.tier() })
        assertEquals(1, ManifestLanguageEvidence().tier("ORIGINAL"))
    }

    @Test fun preflightReusesOneBoundedResponseForTrackMetadata() = runBlocking {
        var requests = 0
        var reads = 0
        StreamValidator.transport = StreamHttpTransport { _, _ ->
            requests++
            StreamHttpResponse(200, "application/vnd.apple.mpegurl") { reads++; master.byteInputStream() }
        }
        try {
            val result = StreamValidator.validateStream("https://test.invalid/guessed_tr.m3u8")
            assertTrue(result.isValid)
            assertEquals(setOf("tr", "en"), result.languageEvidence.audio)
            assertEquals(1, requests)
            assertEquals(1, reads)
        } finally { StreamValidator.resetTransport() }
    }

    @Test fun deniedMissingAndPartialGuessedTurkishManifestsStayUnknown() = runBlocking {
        suspend fun check(code: Int, body: String) = run {
            StreamValidator.transport = StreamHttpTransport { _, _ ->
                StreamHttpResponse(code, "application/vnd.apple.mpegurl") { body.byteInputStream() }
            }
            StreamValidator.validateStream("https://test.invalid/episodes/123_tr/play.m3u8")
        }
        try {
            assertEquals(ManifestLanguageEvidence(), check(403, master).languageEvidence)
            assertEquals(ManifestLanguageEvidence(), check(404, "").languageEvidence)
            assertEquals(ManifestLanguageEvidence(), check(206, master.substringBefore("video.m3u8") + "vid").languageEvidence)
            assertEquals(setOf("en"), check(200, master.replace("LANGUAGE=\"tur\"", "LANGUAGE=\"und\"")).languageEvidence.audio)
        } finally { StreamValidator.resetTransport() }
    }
}
