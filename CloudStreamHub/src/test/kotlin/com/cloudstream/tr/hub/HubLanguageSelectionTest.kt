package com.cloudstream.tr.hub

import com.cloudstream.tr.core.model.ManifestLanguageEvidence
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class HubLanguageSelectionTest {
    @Test fun hubFinalDeliveryUsesTheSharedComparatorWithoutLosingIdentity() = runBlocking {
        val delivered = mutableListOf<ExtractorLink>()
        val collector = HubLinkCollector(true, true, delivered::add, true, "TR_DUB")
        val candidates = (1..5).map { link(it, if (it == 1) 720 else 2160) }
        val evidence = mapOf(candidates[0] to ManifestLanguageEvidence(setOf("tr")),
            candidates[1] to ManifestLanguageEvidence(subtitles = setOf("tr")),
            candidates[2] to ManifestLanguageEvidence(setOf("fa")),
            candidates[3] to ManifestLanguageEvidence(setOf("nl")))
        candidates.reversed().forEach { candidate ->
            evidence[candidate]?.let { collector.recordEvidence(candidate, it) }
            collector.direct(candidate)
        }
        collector.finish()
        val expected = candidates.sortedWith(com.cloudstream.tr.core.model.StreamPrioritySorter.comparator(
            "TR_DUB", { evidence[it] ?: ManifestLanguageEvidence() }))
        assertEquals(expected, delivered)
        expected.zip(delivered).forEach { (original, actual) -> assertSame(original, actual) }
    }
    @Test fun wrapperAndSmartPlayClonePreserveHostMetadataAndTracks() = runBlocking {
        val track = com.lagradost.cloudstream3.newAudioFile("https://audio.test/tr")
        val raw = link(1).apply { audioTracks = listOf(track) }
        val result = com.cloudstream.tr.core.network.PreflightResult(
            com.cloudstream.tr.core.network.ValidationStatus.INDETERMINATE, ExtractorLinkType.VIDEO)
        val tagged = tagHubPreflightLink("Provider", raw, result, raw.headers)
        val clone = com.cloudstream.tr.core.model.StreamPrioritySorter.sortByPriority(listOf(tagged)).single()
        for (candidate in listOf(tagged, clone)) {
            assertEquals(raw.headers, candidate.headers)
            assertEquals(raw.referer, candidate.referer)
            assertEquals(raw.extractorData, candidate.extractorData)
            assertEquals(raw.type, candidate.type)
            assertEquals(raw.audioTracks, candidate.audioTracks)
        }
    }
    private fun link(id: Int, quality: Int = 2160, torrent: Boolean = false, source: String = "Torrentio") = ExtractorLink(
        source = source, name = "Debrid REMUX DV TrueHD Atmos 7.1 ($id seeds)",
        url = if (torrent) "magnet:?xt=urn:btih:${id.toString(16).padStart(40, '0')}&index=0" else "https://cdn.test/$id/guess_tr.m3u8",
        referer = "https://ref.test", quality = quality,
        type = if (torrent) ExtractorLinkType.MAGNET else ExtractorLinkType.M3U8,
        headers = mapOf("X-Test" to "preserved"), extractorData = "opaque host data")

    @Test fun lateTurkish720BeatsEarlyUnknown4kAndAllAlternativesRemain() = runBlocking {
        val delivered = mutableListOf<ExtractorLink>()
        val collector = HubLinkCollector(true, true, delivered::add, rankedDelivery = true, audioPreference = "TR_DUB")
        val unknown = link(1)
        val english = link(2)
        val subtitles = link(3)
        val turkish = link(4, 720)
        collector.direct(unknown)
        assertTrue(delivered.isEmpty())
        collector.recordEvidence(english, ManifestLanguageEvidence(setOf("en")))
        collector.direct(english)
        collector.recordEvidence(subtitles, ManifestLanguageEvidence(subtitles = setOf("tr")))
        collector.direct(subtitles)
        collector.recordEvidence(turkish, ManifestLanguageEvidence(setOf("tr")))
        collector.direct(turkish)
        collector.direct(turkish)
        collector.finish()
        assertEquals(listOf(turkish, subtitles, unknown, english), delivered)
        assertSame(turkish, delivered.first())
        assertEquals("opaque host data", delivered.first().extractorData)
        assertEquals(mapOf("X-Test" to "preserved"), delivered.first().headers)
        assertEquals("https://ref.test", delivered.first().referer)
        assertEquals(ExtractorLinkType.M3U8, delivered.first().type)
        collector.finish()
        assertEquals(4, delivered.size)
    }

    @Test fun lateVerifiedTurkishTorrentWinsBeforeQualityAndOriginQuotas() = runBlocking {
        val delivered = mutableListOf<ExtractorLink>()
        val collector = HubLinkCollector(true, true, delivered::add, true, "TR_DUB")
        collector.torrents((10..20).map { link(it, 1080, true) })
        val late = link(1, 1080, true)
        collector.recordEvidence(late, ManifestLanguageEvidence(setOf("tr")))
        collector.torrents(listOf(late))
        assertTrue(delivered.isEmpty())
        collector.finish()
        assertEquals(3, delivered.size)
        assertSame(late, delivered.first())
    }

    @Test fun unknownFallbackAndSavedOriginalNeverInferAudioFromName() = runBlocking {
        val delivered = mutableListOf<ExtractorLink>()
        val collector = HubLinkCollector(true, true, delivered::add, true, "ORIGINAL")
        val known = link(1, 720)
        collector.recordEvidence(known, ManifestLanguageEvidence(setOf("en")))
        (2..6).map { link(it) }.forEach(collector::direct)
        collector.direct(known)
        collector.finish()
        assertEquals(6, delivered.size)
        assertSame(known, delivered.first())
    }

    @Test fun disabledP2pAndDebridGatesAlsoApplyToDeferredAndCachedCandidates() = runBlocking {
        val delivered = mutableListOf<ExtractorLink>()
        val collector = HubLinkCollector(false, false, delivered::add, true)
        collector.torrents(listOf(link(1, torrent = true)))
        collector.direct(link(2))
        collector.direct(link(3, source = "YTS"))
        val allowed = link(4, source = "Provider")
        collector.direct(allowed)
        collector.finish()
        assertEquals(listOf(allowed), delivered)
        val debrid = HubLinkCollector(false, true, delivered::add, true)
        debrid.direct(link(5))
        debrid.torrents(listOf(link(6, torrent = true)))
        debrid.finish()
        assertEquals(2, delivered.size)
    }

    @Test fun internalTimeoutRetainsPartialCandidatesButExternalCancellationPublishesNothing() = runBlocking {
        val delivered = mutableListOf<ExtractorLink>()
        val collector = HubLinkCollector(true, true, delivered::add, true)
        assertFalse(HubSourceBudget.run(10) { collector.direct(link(1)); delay(1000) })
        collector.finish()
        assertEquals(1, delivered.size)
        val cancelled = HubLinkCollector(true, true, delivered::add, true)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            cancelled.direct(link(2))
            delay(1000)
            cancelled.finish()
        }
        job.cancelAndJoin()
        assertEquals(1, delivered.size)
    }

    @Test fun cancellationInsideFirstCallbackPreventsAnyLaterCallbacks() = runBlocking {
        val delivered = mutableListOf<ExtractorLink>()
        val job = launch(start = CoroutineStart.LAZY) {
            val context = currentCoroutineContext()
            val collector = HubLinkCollector(true, true, { delivered.add(it); context.cancel() }, true)
            (1..5).map { link(it) }.forEach(collector::direct)
            collector.finish()
        }
        job.start()
        job.join()
        assertEquals(1, delivered.size)
        assertTrue(job.isCancelled)
    }

    @Test fun completionOrderCannotChangeRankedDelivery() = runBlocking {
        suspend fun deliver(reverse: Boolean): List<String> {
            val result = mutableListOf<ExtractorLink>()
            val collector = HubLinkCollector(true, true, result::add, true, "TR_DUB")
            val candidates = (1..4).map { link(it, if (it == 1) 720 else 2160) }
            collector.recordEvidence(candidates.first(), ManifestLanguageEvidence(setOf("tr")))
            coroutineScope {
                candidates.forEachIndexed { i, candidate -> launch { delay((if (reverse) 4 - i else i).toLong()); collector.direct(candidate) } }
            }
            collector.finish()
            return result.map { it.url }
        }
        assertEquals(deliver(false), deliver(true))
    }
}
