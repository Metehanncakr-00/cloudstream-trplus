package com.cloudstream.tr.core.model

import com.cloudstream.tr.core.network.*
import com.cloudstream.tr.core.streaming.SmartPlayEngine
import com.lagradost.cloudstream3.utils.*
import org.junit.Assert.*
import org.junit.Test

class StreamCandidateOrderingTest {
    private fun link(id: String, quality: Int = 2160, name: String = "Debrid REMUX TrueHD Atmos 7.1") = ExtractorLink(
        "source", name, "https://test.invalid/$id", "", quality, type = ExtractorLinkType.M3U8, audioTracks = emptyList())

    @Test fun explicitTierAlwaysPrecedesTechnicalScoreAndDoesNotClone() {
        val unknown = link("unknown")
        val other = link("fa")
        val subs = link("subs")
        val audio = link("tr", 720, "plain")
        val evidence = mapOf(other to ManifestLanguageEvidence(setOf("fa")),
            subs to ManifestLanguageEvidence(subtitles = setOf("tr")), audio to ManifestLanguageEvidence(setOf("tr")))
        val comparator = StreamPrioritySorter.comparator(evidenceFor = { evidence[it] ?: ManifestLanguageEvidence() })
        val result = listOf(unknown, other, audio, subs).sortedWith(comparator)
        assertEquals(listOf(audio, subs, unknown, other), result)
        assertSame(audio, result.first())
        val original = StreamPrioritySorter.comparator("ORIGINAL", { evidence[it] ?: ManifestLanguageEvidence() })
        assertSame(other, result.sortedWith(original).first())
    }

    @Test fun unknownNameAndUrlNeverUpgradeLanguageAndTiesAreDeterministic() {
        val guessed = link("z_tr.m3u8", 720, "TR Dublaj")
        val plain = link("a", 720, "plain")
        val result = PreflightResult(ValidationStatus.VALID, ExtractorLinkType.M3U8)
        assertEquals(SmartPlayEngine.calculateScore(plain, result), SmartPlayEngine.calculateScore(guessed, result))
        val pending = result.copy(status = ValidationStatus.INDETERMINATE)
        assertEquals(SmartPlayEngine.calculateScore(plain, pending), SmartPlayEngine.calculateScore(guessed, pending))
        val comparator = StreamPrioritySorter.comparator()
        assertEquals(listOf(plain, guessed).sortedWith(comparator), listOf(guessed, plain).sortedWith(comparator))
        val high = link("high")
        assertSame(high, listOf(guessed, high).sortedWith(comparator).first())
    }
}
