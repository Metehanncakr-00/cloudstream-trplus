package com.cloudstream.tr.core.streaming

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ManifestLanguageEvidence
import com.cloudstream.tr.core.model.StreamPrioritySorter
import com.cloudstream.tr.core.network.PreflightResult
import com.cloudstream.tr.core.network.StreamValidator
import com.cloudstream.tr.core.network.ValidationStatus
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.IdentityHashMap

/** Per-load bounded discovery, one preflight per admitted candidate, then ordered delivery.
 * Preflight proves response/track metadata only, not segment access or player compatibility.
 */
object BoundedLinkDelivery {
    suspend fun collectAndDeliver(
        discover: suspend (emit: (ExtractorLink) -> Unit) -> Unit,
        callback: (ExtractorLink) -> Unit,
        preference: String = "TR_DUB",
        maxCandidates: Int = 128,
        maxConcurrency: Int = BoundedParallelResolver.DEFAULT_MAX_CONCURRENCY,
        discoveryBudgetMs: Long = 20_000L,
        preflightBudgetMs: Long = 10_000L,
        probeTimeoutMs: Long = StreamValidator.DEFAULT_TIMEOUT_MS,
        probe: suspend (ExtractorLink, Map<String, String>) -> PreflightResult = { link, headers ->
            StreamValidator.validateStream(link.url, headers, link.source, probeTimeoutMs)
        }
    ): Int {
        val candidates = mutableListOf<ExtractorLink>()
        val seen = mutableSetOf<List<Any?>>()
        val lock = Any()
        var accepting = true
        try {
            withTimeoutOrNull(discoveryBudgetMs) {
                discover { link -> synchronized(lock) {
                    // Keep distinct authenticated/header/track variants, not just distinct URLs.
                    val key = listOf(link.url.trim(), link.referer, link.headers.toMap(),
                        link.type, link.extractorData, link.audioTracks)
                    if (accepting && link.url.isNotBlank() && candidates.size < maxCandidates && seen.add(key)) candidates.add(link)
                } }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A failed extractor does not erase candidates already discovered.
        } finally {
            synchronized(lock) { accepting = false }
        }
        currentCoroutineContext().ensureActive()
        val admitted = synchronized(lock) { candidates.toList() }
        val results = IdentityHashMap<ExtractorLink, PreflightResult>()
        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        withTimeoutOrNull(preflightBudgetMs) {
            coroutineScope {
                admitted.map { link -> launch {
                    // Non-HTTP transports remain unknown; never feed magnets to HTTP preflight.
                    if (!link.url.startsWith("https://", true) && !link.url.startsWith("http://", true)) return@launch
                    semaphore.withPermit {
                        val headers = link.headers.toMutableMap()
                        if (link.referer.isNotBlank() && headers.keys.none { it.equals("Referer", true) }) headers["Referer"] = link.referer
                        val result = try {
                            withTimeoutOrNull(probeTimeoutMs) { probe(link, headers) }
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { null }
                        if (result != null) synchronized(results) { results[link] = result }
                    }
                } }.joinAll()
            }
        }
        currentCoroutineContext().ensureActive()
        // Internal timeouts preserve valid/indeterminate/unprobed fallback candidates.
        val ordered = admitted.filter { results[it]?.status != ValidationStatus.INVALID }
            .sortedWith(StreamPrioritySorter.comparator(preference,
                evidenceFor = { results[it]?.languageEvidence ?: ManifestLanguageEvidence() },
                preflightFor = { results[it] }))
        for (link in ordered) {
            currentCoroutineContext().ensureActive()
            // Preserve object identity and every mutable host field, including tracks/data.
            callback(link)
        }
        return ordered.size
    }
}
