package com.cloudstream.tr.hub

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HubPerformanceSupportTest {
    @Test fun readAheadRecommendationIsBoundedAndBitrateIsCalculated() {
        val size = 60L * 1024 * 1024 * 1024
        val bytes = HubReadAheadPolicy.recommendBytes(size, 7_200)
        assertTrue(bytes > 0)
        assertTrue(bytes <= HubReadAheadPolicy.MAX_BUFFER_BYTES)
        assertTrue(HubReadAheadPolicy.averageMbps(size, 7_200)!! > 50.0)
        assertNull(HubReadAheadPolicy.averageMbps(size, 0))
        assertEquals(0, HubReadAheadPolicy.recommendBytes(size, 0))
    }

    @Test fun adaptiveTimeoutHonorsBoundsAndFailures() {
        HubAdaptiveTimeout.clear()
        assertEquals(20_000L, HubAdaptiveTimeout.limit("Torrentio", 20_000L))
        HubAdaptiveTimeout.observe("Torrentio", 12_000L, true)
        assertEquals(29_000L, HubAdaptiveTimeout.limit("Torrentio", 20_000L))
        HubAdaptiveTimeout.observe("Torrentio", 10_000L, false)
        HubAdaptiveTimeout.observe("Torrentio", 10_000L, false)
        assertEquals(15_000L, HubAdaptiveTimeout.limit("Torrentio", 20_000L))
        HubAdaptiveTimeout.clear()
    }

    @Test fun telemetryIsBoundedAndDoesNotIncludeRawTokens() {
        HubPerformanceTelemetry.clear()
        repeat(100) { HubPerformanceTelemetry.record("torrent/link?token=abc", it.toLong(), "status#secret") }
        assertEquals(80, HubPerformanceTelemetry.report().lines().size)
        assertFalse(HubPerformanceTelemetry.report().contains("token=abc"))
        HubPerformanceTelemetry.clear()
    }

    @Test fun parallelPreflightIsBounded() = runBlocking {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        val count = AtomicInteger()
        resolveHubPreflight((1..12).toList(), 3) {
            val n = running.incrementAndGet()
            peak.updateAndGet { maxOf(it, n) }
            try { delay(10); count.incrementAndGet() }
            finally { running.decrementAndGet() }
        }
        assertEquals(12, count.get())
        assertEquals(3, peak.get())
    }

    @Test fun globalPreflightBudgetIsSharedAcrossConcurrentBatches() = runBlocking {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        kotlinx.coroutines.coroutineScope {
            repeat(4) {
                launch {
                    resolveHubPreflight((1..9).toList(), 3) {
                        HubGlobalPreflight.run {
                            val count = running.incrementAndGet()
                            peak.updateAndGet { previous -> maxOf(previous, count) }
                            try { delay(20) } finally { running.decrementAndGet() }
                        }
                    }
                }
            }
        }
        assertTrue("Global HTTP preflight budget must be shared", peak.get() <= 6)
        assertTrue(peak.get() >= 3)
    }

    @Test fun compatibilityIsAdvisoryAndProfilesValidated() {
        assertTrue(HubPlaybackCompatibility.guidance("Movie.HEVC.REMUX", false).contains("HEVC"))
        assertTrue(HubPlaybackCompatibility.guidance("Movie.DOVI.TrueHD", true).contains("bağlıdır"))
        assertTrue(HubPerformanceSettings.valid(HubPerformanceSettings.FEATURE))
        assertFalse(HubPerformanceSettings.valid("unknown"))
    }
}
