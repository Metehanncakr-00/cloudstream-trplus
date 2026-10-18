package com.cloudstream.tr.hub

import com.cloudstream.tr.core.resolvers.DebridConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubSettingsManagerTest {

    @Test
    fun testProviderEnableDisable() {
        val initial = HubSettingsManager.State(disabledProviders = setOf("BadProvider"))
        assertTrue(initial.disabledProviders.contains("BadProvider"))
        assertFalse(initial.disabledProviders.contains("GoodProvider"))
    }

    @Test
    fun testSanitizedDiagnosticsRedactsSecrets() {
        // Set fake sensitive tokens
        DebridConfig.realDebridToken = "secret_rd_token_12345"
        DebridConfig.torboxToken = "secret_torbox_key_67890"

        // Record a mock telemetry event that might have logged something
        HubPerformanceTelemetry.record("test_stage", 120, "rd_test:secret_rd_token_12345")

        val report = HubSettingsManager.getSanitizedDiagnosticsReport()

        // Report must NOT contain the plain text secret tokens!
        assertFalse("Report must not leak Real-Debrid token", report.contains("secret_rd_token_12345"))
        assertFalse("Report must not leak Torbox token", report.contains("secret_torbox_key_67890"))
        assertTrue("Report must contain redacted marker", report.contains("[REDACTED_RD_TOKEN]"))

        // Cleanup
        DebridConfig.realDebridToken = null
        DebridConfig.torboxToken = null
        HubPerformanceTelemetry.clear()
    }

    @Test
    fun testSettingsDefaultsAreSaneForAndroidTv() {
        val defaultState = HubSettingsManager.State()
        assertEquals(HubSettingsManager.PERF_MODE_BALANCED, defaultState.perfMode)
        assertEquals(30, defaultState.searchTimeoutSec)
        assertEquals(4, defaultState.maxConcurrency)
        assertEquals(60, defaultState.cacheTtlMin)
        assertTrue(defaultState.reportFailures)
        assertFalse(defaultState.torrentsAtBottom)
        assertEquals("DEPRIORITIZE", defaultState.unknownAudioPolicy)
        assertTrue(defaultState.disabledProviders.isEmpty())
    }

    @Test
    fun testPerfModeControlsEffectiveTimeouts() {
        val fast = HubSettingsManager.State(perfMode = HubSettingsManager.PERF_MODE_FAST, searchTimeoutSec = 30)
        assertTrue("Fast mode must bound search timeout to <= 10s", fast.effectiveSearchTimeoutMs <= 10_000L)
        assertEquals(12_000L, fast.effectiveProviderTimeoutMs)
        assertEquals(1500L, fast.effectivePreflightTimeoutMs)

        val comp = HubSettingsManager.State(perfMode = HubSettingsManager.PERF_MODE_COMPREHENSIVE, searchTimeoutSec = 40)
        assertEquals(40_000L, comp.effectiveSearchTimeoutMs)
        assertEquals(45_000L, comp.effectiveProviderTimeoutMs)
        assertEquals(5000L, comp.effectivePreflightTimeoutMs)
    }

    @Test
    fun testTorrentsAtBottomSortsTorrentBelowDirectStreams() = runBlocking {
        val directLink = com.lagradost.cloudstream3.utils.newExtractorLink(
            source = "DirectStream",
            name = "Direct 720p",
            url = "https://cdn.prov.com/video.mp4",
            type = com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
        )
        val torrentLink = com.lagradost.cloudstream3.utils.newExtractorLink(
            source = "TorrentSource",
            name = "Torrent 4K",
            url = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
            type = com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET
        )

        // With torrentsAtBottom = true
        val cmpBottom = com.cloudstream.tr.core.model.StreamPrioritySorter.comparator(
            preference = "TR_DUB",
            torrentsAtBottom = true
        )
        val sortedBottom = listOf(torrentLink, directLink).sortedWith(cmpBottom)
        assertEquals("Direct stream must be placed before torrent when torrentsAtBottom=true", directLink.source, sortedBottom.first().source)
        assertEquals("Torrent must be at bottom", torrentLink.source, sortedBottom.last().source)
    }

    @Test
    fun testUnknownAudioPolicyAffectsTierRanking() {
        val unknownEvidence = com.cloudstream.tr.core.model.ManifestLanguageEvidence(audio = emptySet(), subtitles = emptySet())
        val trSubEvidence = com.cloudstream.tr.core.model.ManifestLanguageEvidence(audio = emptySet(), subtitles = setOf("tr"))
        val foreignEvidence = com.cloudstream.tr.core.model.ManifestLanguageEvidence(audio = setOf("en"), subtitles = emptySet())

        // DEPRIORITIZE: Sub(2) > Unknown(1) > Foreign(0)
        assertEquals(1, unknownEvidence.tier("TR_DUB", "DEPRIORITIZE"))
        assertTrue(trSubEvidence.tier("TR_DUB", "DEPRIORITIZE") > unknownEvidence.tier("TR_DUB", "DEPRIORITIZE"))
        assertTrue(unknownEvidence.tier("TR_DUB", "DEPRIORITIZE") > foreignEvidence.tier("TR_DUB", "DEPRIORITIZE"))

        // BOTTOM: Foreign(0) > Unknown(-1)
        assertEquals(-1, unknownEvidence.tier("TR_DUB", "BOTTOM"))
        assertTrue(foreignEvidence.tier("TR_DUB", "BOTTOM") > unknownEvidence.tier("TR_DUB", "BOTTOM"))
    }
}

