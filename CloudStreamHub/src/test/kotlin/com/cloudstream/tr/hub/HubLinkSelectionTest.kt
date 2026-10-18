package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
import com.cloudstream.tr.core.concurrency.BoundedParallelResolver

class HubLinkSelectionTest {
    private fun torrent(hash: Int, source: String = "Torrentio", quality: Int = 1080,
                        seeds: Int = 10, index: Int = 0, tracker: String = "a") = ExtractorLink(
        source = source, name = "$source ($seeds seeds)",
        url = "magnet:?xt=urn:btih:${hash.toString(16).padStart(40, '0')}&index=$index&tr=$tracker",
        referer = "", quality = quality, type = ExtractorLinkType.MAGNET
    )

    @Test fun perQualityQuotaPreservesDiversityAndSeedOrder() {
        val links = (1..5).map { torrent(it, seeds = 100 - it) } + torrent(6, "YTS", seeds = 0) +
            (7..10).map { torrent(it, quality = 720) } + torrent(11, quality = -1)
        val selected = HubTorrentSelector.select(links)
        assertEquals(3, selected.count { it.quality == 1080 })
        assertEquals(3, selected.count { it.quality == 720 })
        assertEquals(1, selected.count { it.quality == -1 })
        assertTrue(selected.any { it.source == "YTS" })
        assertTrue(selected.contains(links[0]))
        assertTrue(selected.contains(links[1]))
        assertEquals(selected, HubTorrentSelector.select(links.reversed()))
    }

    @Test fun profileRankingKeepsPeerHealthPrimaryButCanPreferBluRayFeature() {
        val regular = torrent(91, seeds = 30, quality = 2160)
        val remux = ExtractorLink(
            source = "Torrentio", name = "2160p BluRay REMUX (25 seeds)",
            url = torrent(92).url, referer = "", quality = 2160, type = ExtractorLinkType.MAGNET
        )
        assertTrue(HubTorrentSelector.sourceScore(regular, HubPerformanceSettings.AUTO) >
            HubTorrentSelector.sourceScore(remux, HubPerformanceSettings.AUTO))
        assertTrue(HubTorrentSelector.sourceScore(remux, HubPerformanceSettings.FEATURE) >
            HubTorrentSelector.sourceScore(regular, HubPerformanceSettings.FEATURE))
        val difficult = ExtractorLink(
            source = "Torrentio", name = "2160p DOVI TrueHD (30 seeds)",
            url = torrent(93).url, referer = "", quality = 2160, type = ExtractorLinkType.MAGNET
        )
        assertTrue(HubTorrentSelector.sourceScore(regular, HubPerformanceSettings.COMPATIBLE) >
            HubTorrentSelector.sourceScore(difficult, HubPerformanceSettings.COMPATIBLE))
    }

    @Test fun sameSwarmIsOneAlternativeButDifferentIndexIsNotRewritten() {
        val torrentio = torrent(1, seeds = 100)
        val yts = torrent(1, "YTS", seeds = 0, tracker = "b")
        assertEquals(listOf(yts), HubTorrentSelector.select(listOf(torrentio, yts)))
        val otherFile = torrent(1, "YTS", seeds = 0, index = 2)
        assertEquals(listOf(torrentio, otherFile), HubTorrentSelector.select(listOf(torrentio, otherFile)))
        val base32 = ExtractorLink(source = "YTS", name = "YTS", url = "magnet:?xt=urn:btih:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            referer = "", quality = 1080, type = ExtractorLinkType.MAGNET)
        assertEquals(HubTorrentSelector.swarm(torrent(0)), HubTorrentSelector.swarm(base32))
    }

    @Test fun late4kRemuxOriginGetsReservedSlotAfterYtsCandidates() {
        val yts = (1..6).map { torrent(100 + it, "YTS", quality = 2160, seeds = 40) }
        val early = HubTorrentSelector.select(yts)
        assertEquals("Early origin must leave one 4K slot open", 3, early.size)
        val lateRemux = torrent(150, "Torrentio", quality = 2160, seeds = 25)
        val acceptedLate = HubTorrentSelector.select(listOf(lateRemux), early)
        assertEquals(listOf(lateRemux), acceptedLate)
        val afterFour = early + acceptedLate
        assertTrue(HubTorrentSelector.select(listOf(torrent(151, "Other", quality = 2160)), afterFour).isEmpty())
        // Ordinary 1080p quotas must remain at three alternatives.
        assertEquals(3, HubTorrentSelector.select((1..6).map { torrent(300 + it, "YTS", quality = 1080) }).size)
    }

    @Test fun differentEpisodesFromSameTorrentHaveIndependentIdentity() {
        val s1e1 = torrent(7, index = 1)
        val s1e2 = torrent(7, index = 2)
        val s1e1Duplicate = torrent(7, index = 1, tracker = "other")
        val selected = HubTorrentSelector.select(listOf(s1e1, s1e2, s1e1Duplicate))
        assertEquals(2, selected.size)
        assertTrue(selected.any { it.url.contains("index=1") })
        assertTrue(selected.any { it.url.contains("index=2") })
        assertEquals(listOf(s1e2), HubTorrentSelector.select(listOf(s1e2), listOf(s1e1)))
    }

    @Test fun callbacksAreImmediateForEveryHttpLinkAndTorrentQuotaSpansBatches() {
        val emitted = mutableListOf<ExtractorLink>()
        val collector = HubLinkCollector(true, true, emitted::add)
        val http = (1..4).map { ExtractorLink(source = "FilmMakinesi", name = "HTTP $it", url = "https://cdn.test/$it.mp4",
            referer = "https://site.test", quality = 1080, type = ExtractorLinkType.VIDEO, headers = mapOf("Referer" to "https://site.test"),
            extractorData = "metadata") }
        http.forEachIndexed { index, link ->
            collector.direct(link)
            assertEquals(index + 1, emitted.size)
        }
        assertEquals("metadata", emitted.first().extractorData)
        collector.torrents(listOf(torrent(1), torrent(2), torrent(3, "YTS")))
        collector.torrents(listOf(torrent(1, tracker = "different"), torrent(4, "Federated")))
        assertEquals(7, emitted.size)
        assertEquals(3, emitted.count(HubTorrentSelector::isTorrent))
    }

    @Test fun disabledTorrentsStillPermitDebridHttp() {
        val emitted = mutableListOf<ExtractorLink>()
        val collector = HubLinkCollector(false, true, emitted::add)
        collector.torrents(listOf(torrent(1)))
        collector.direct(ExtractorLink(source = "Torrentio", name = "Debrid", url = "https://cdn.test/file", referer = "", quality = 2160,
            type = ExtractorLinkType.VIDEO))
        assertEquals(1, emitted.size)
    }

    @Test fun cachedMagnetsAreOnlyPublishedThroughTorrentPolicy() {
        val cached = torrent(10, "YTS", quality = 2160)
        val fresh = torrent(11, "Torrentio", quality = 2160)
        val emitted = mutableListOf<ExtractorLink>()
        val collector = HubLinkCollector(true, false, emitted::add)
        collector.direct(cached)
        assertTrue("Direct path must never publish a magnet", emitted.isEmpty())
        collector.torrents(listOf(fresh))
        collector.torrents(HubTorrentSelector.cachedFallback(listOf(cached), listOf(fresh), true))
        assertEquals(listOf(fresh, cached), emitted)
    }

    @Test fun cacheOnlySuppliesUnavailableOriginsWithoutExceedingQuota() {
        val fresh = listOf(torrent(1), torrent(2), torrent(3))
        val cached = listOf(torrent(4), torrent(5, "YTS"), torrent(6, "Federated"))
        val fallback = HubTorrentSelector.cachedFallback(cached, fresh, true)
        assertEquals(listOf(cached[1]), fallback)
        val emitted = mutableListOf<ExtractorLink>()
        val collector = HubLinkCollector(true, false, emitted::add)
        assertTrue(emitted.isEmpty())
        collector.torrents(fresh + fallback)
        assertEquals(3, emitted.size)
        assertTrue(emitted.contains(cached[1]))
        assertFalse(emitted.contains(cached[0]))
        assertEquals(listOf(cached[2]), HubTorrentSelector.cachedFallback(cached, fresh, false))
    }

    @Test fun lateBluRayCandidatesRetainReservedQueueSlots() {
        val standard = java.util.concurrent.atomic.AtomicInteger(0)
        val feature = java.util.concurrent.atomic.AtomicInteger(0)
        val low = torrent(91, quality = 720)
        val featureLink = torrent(92, quality = 2160)
        assertEquals(HubCandidateBudget.MAX_STANDARD, (1..140).count {
            HubCandidateBudget.admit(low, standard, feature)
        })
        assertEquals(HubCandidateBudget.RESERVED_FEATURE, (1..60).count {
            HubCandidateBudget.admit(featureLink, standard, feature)
        })
        assertEquals(128, HubCandidateBudget.MAX_STANDARD + HubCandidateBudget.RESERVED_FEATURE)
        assertTrue(HubCandidateBudget.isFeatureCandidate(ExtractorLink(source = "Torrentio", name = "Feature.BluRay.REMUX", url = "magnet:?xt=urn:btih:${"a".repeat(40)}&index=0", referer = "", quality = -1, type = ExtractorLinkType.MAGNET)))
        assertFalse(HubCandidateBudget.isFeatureCandidate(low))
    }

    @Test fun torrentSourceBudgetStopsSlowOriginWithoutDroppingFastBatch() = kotlinx.coroutines.test.runTest {
        val emitted = mutableListOf<String>()
        val finished = HubSourceBudget.run(20) {
            emitted.add("2160p Remux")
            delay(100_000)
        }
        assertFalse(finished)
        assertEquals(listOf("2160p Remux"), emitted)
        assertEquals(20L, testScheduler.currentTime)
    }

    @Test fun torrentSourceBudgetDoesNotSwallowCancellation() = kotlinx.coroutines.runBlocking {
        val cancelled = CancellationException("player closed")
        try {
            HubSourceBudget.run(HubSourceBudget.TORRENT_DISCOVERY_MS) { throw cancelled }
            fail("Must rethrow cancellation")
        } catch (e: CancellationException) {
            assertEquals("player closed", e.message)
            assertFalse(e is TimeoutCancellationException)
        }
    }

    @Test fun candidateCapKeepsProviderMemoryBounded() {
        assertTrue(HubCandidateBudget.allow(1))
        assertTrue(HubCandidateBudget.allow(HubCandidateBudget.MAX_PER_PROVIDER))
        assertFalse(HubCandidateBudget.allow(HubCandidateBudget.MAX_PER_PROVIDER + 1))
        assertFalse(HubCandidateBudget.allow(0))
    }

    @Test fun globalFederationBudgetKeepsAlreadyEmittedResults() = kotlinx.coroutines.test.runTest {
        val published = mutableListOf<String>()
        val completed = HubFederationBudget.run(25) {
            published.add("1080p")
            delay(60_000)
            published.add("late")
        }
        assertFalse(completed)
        assertEquals(listOf("1080p"), published)
        assertEquals(25L, testScheduler.currentTime)
    }

    @Test fun globalFederationBudgetPropagatesExplicitCancellation() = kotlinx.coroutines.test.runTest {
        val cancellation = CancellationException("user exited")
        try {
            HubFederationBudget.run { throw cancellation }
            fail("Cancellation swallowed")
        } catch (e: CancellationException) {
            assertEquals("user exited", e.message)
        }
    }

    @Test fun providerTimeoutStartsAfterPermitAndPreservesEmittedLinks() = kotlinx.coroutines.test.runTest {
        val emitted = mutableListOf<ExtractorLink>()
        val entered = mutableListOf<Int>()
        val count = BoundedParallelResolver.resolveProgressive(
            candidates = (1..5).toList(), maxConcurrency = 4,
            resolver = { candidate, emit ->
                HubProviderTimeout.resolve(30) {
                    entered.add(candidate)
                    emit(torrent(candidate))
                    delay(10_000)
                }
            }, onLinkFound = emitted::add
        )
        assertEquals(5, count)
        assertEquals(5, entered.size)
        assertEquals(5, emitted.size)
        assertEquals(60L, testScheduler.currentTime)
    }

    @Test fun providerTimeoutDoesNotSwallowUserCancellation() = kotlinx.coroutines.test.runTest {
        val cancellation = CancellationException("user cancelled")
        try {
            HubProviderTimeout.resolve { throw cancellation }
            fail("Cancellation must propagate")
        } catch (caught: CancellationException) {
            assertEquals(cancellation::class, caught::class)
            assertEquals(cancellation.message, caught.message)
            assertFalse(caught is TimeoutCancellationException)
        }
    }

    @Test fun payloadCategoryIsOptionalAndRoundTrips() {
        val old = AggregatorLinkPayload.fromUrlData("hub://item?title=Test&isMovie=false&season=1&episode=2")!!
        assertNull(old.category)
        val modern = old.copy(category = "animation")
        assertEquals(modern, AggregatorLinkPayload.fromUrlData(modern.toUrlData()))
        assertEquals("documentary", HubContentRouting.category(listOf(99, 16)))
        assertEquals(TvType.AnimeMovie, HubContentRouting.type(true, listOf(16)))
    }

    @Test fun categoryRoutesSpecialistsWithoutDroppingGeneralMovieSources() {
        val movie = object : MainAPI() { override val supportedTypes = setOf(TvType.Movie) }
        val doc = object : MainAPI() { override val supportedTypes = setOf(TvType.Documentary) }
        val drama = object : MainAPI() { override val supportedTypes = setOf(TvType.AsianDrama) }
        val anime = object : MainAPI() { override val supportedTypes = setOf(TvType.Anime) }
        val payload = AggregatorLinkPayload("Test", 2020, true, category = "documentary")
        assertTrue(HubContentRouting.eligible(drama, payload.copy(isMovie = false, category = "general")))
        assertTrue(HubContentRouting.eligible(drama, payload.copy(isMovie = false, category = null)))
        assertTrue(HubContentRouting.eligible(movie, payload))
        assertTrue(HubContentRouting.eligible(doc, payload))
        assertFalse(HubContentRouting.eligible(anime, payload))
        assertTrue(HubContentRouting.eligible(anime, payload.copy(isMovie = false, category = "animation")))
    }

    @Test fun bundledRuntimeClassesCoverAllEnabledFederationProviders() {
        assertTrue(CloudStreamProviderRegistryAdapter.knownProviderClasses.isNotEmpty())
        val instances = CloudStreamProviderRegistryAdapter.knownProviderClasses.map { className ->
            val clazz = Class.forName(className)
            assertTrue("Bundled class must be a provider: $className", MainAPI::class.java.isAssignableFrom(clazz))
            clazz.getDeclaredConstructor().newInstance() as MainAPI
        }
        assertEquals(CloudStreamProviderRegistryAdapter.knownProviderClasses.size, instances.map { it.name }.distinct().size)
        assertTrue(instances.all { it.supportedTypes.isNotEmpty() })
    }

    @Test fun exactEpisodeOnlyForSeriesAndAnime() = kotlinx.coroutines.test.runTest {
        val hub = CloudStreamHub()
        val episodes = listOf(hub.newEpisode("https://fixture.invalid/episode1") { season = 1; episode = 1 },
            hub.newEpisode("https://fixture.invalid/episode2") { season = 1; episode = 2 })
        val series = hub.newTvSeriesLoadResponse("Test", "https://test", TvType.TvSeries, episodes)
        val anime = hub.newAnimeLoadResponse("Test", "https://test", TvType.Anime) {
            addEpisodes(DubStatus.Subbed, episodes)
        }
        val payload = AggregatorLinkPayload("Test", 2020, false, 1, 2, category = "animation")
        assertEquals("https://fixture.invalid/episode2", HubContentRouting.linkData(series, payload))
        assertEquals("https://fixture.invalid/episode2", HubContentRouting.linkData(anime, payload))
        assertNull(HubContentRouting.linkData(anime, payload.copy(season = 2)))
        assertNull(HubContentRouting.linkData(series, payload.copy(episode = 3)))
        assertNull(HubContentRouting.linkData(series, payload.copy(isMovie = true)))
    }

    @Test fun documentarySearchShapeDefersMovieVersusSeriesDecisionToLoad() = kotlinx.coroutines.test.runTest {
        val hub = CloudStreamHub()
        // BelgeselX uses a series-shaped search response for both documentary forms.
        val candidate = hub.newTvSeriesSearchResponse("Documentary", "https://test/documentary", TvType.Documentary) {
            year = 2020
        }
        assertSame(candidate, HubMatchingEngine.findConfidentMatch(listOf(candidate), listOf("Documentary"),
            2020, isMovie = true))
        val movie = hub.newMovieLoadResponse("Documentary", candidate.url, TvType.Documentary, "movie-data")
        val series = hub.newTvSeriesLoadResponse("Documentary", candidate.url, TvType.Documentary,
            listOf(hub.newEpisode("episode-data") { season = 1; episode = 1 }))
        val payload = AggregatorLinkPayload("Documentary", 2020, true, category = "documentary")
        assertEquals("movie-data", HubContentRouting.linkData(movie, payload))
        assertNull(HubContentRouting.linkData(series, payload))
        assertNull(HubContentRouting.linkData(movie, payload.copy(isMovie = false, season = 1, episode = 1)))
    }
}
