package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.CompletableDeferred

internal data class HubLookupOutcome(
    val links: List<ExtractorLink>,
    /** True only for a successfully decoded, authoritative *empty* response. */
    val conclusiveEmpty: Boolean = false
)

/**
 * Share duplicate in-flight discovery without running detached background jobs.
 *
 * - The first caller owns the request; its cancellation propagates to waiters.
 * - Only magnet/torrent candidates are reusable. Short-lived direct/CDN URLs are never cached.
 * - An empty result is cached only when explicitly marked conclusive; network failures aren't.
 * - Short TTL + bounded LRU keep both stale peer data and Android TV memory limited.
 */
internal class HubSourceMemo(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maximum: Int = 64
) {
    private data class Entry(val createdMs: Long, val ttlMs: Long, val outcome: HubLookupOutcome)
    private val guard = Any()
    private val inFlight = mutableMapOf<String, CompletableDeferred<HubLookupOutcome>>()
    private val stored = LinkedHashMap<String, Entry>(16, 0.75f, true)

    suspend fun resolve(key: String, fetch: suspend () -> HubLookupOutcome): List<ExtractorLink> {
        var leader = false
        val deferred: CompletableDeferred<HubLookupOutcome>
        synchronized(guard) {
            val now = clock()
            val saved = stored[key]
            if (saved != null) {
                if (now >= saved.createdMs && now - saved.createdMs < saved.ttlMs) {
                    return saved.outcome.links.toList()
                }
                stored.remove(key)
            }
            val existing = inFlight[key]
            if (existing != null) {
                deferred = existing
            } else {
                deferred = CompletableDeferred()
                inFlight[key] = deferred
                leader = true
            }
        }
        if (!leader) return deferred.await().links.toList()
        try {
            val outcome = fetch()
            synchronized(guard) {
                inFlight.remove(key)
                val ttlMs = when {
                    outcome.links.isEmpty() && outcome.conclusiveEmpty -> 10_000L
                    outcome.links.isNotEmpty() && outcome.links.all(HubTorrentSelector::isTorrent) -> 30_000L
                    else -> 0L
                }
                if (ttlMs > 0L && maximum > 0) {
                    stored[key] = Entry(clock(), ttlMs, outcome.copy(links = outcome.links.toList()))
                    while (stored.size > maximum) {
                        val iterator = stored.entries.iterator()
                        iterator.next()
                        iterator.remove()
                    }
                }
            }
            deferred.complete(outcome)
            return outcome.links.toList()
        } catch (failure: Throwable) {
            synchronized(guard) { inFlight.remove(key) }
            deferred.completeExceptionally(failure)
            throw failure
        }
    }

    fun size(): Int = synchronized(guard) { stored.size }
    fun clear() = synchronized(guard) { stored.clear() }
}

internal object HubSourceMemoRegistry {
    val torrentio = HubSourceMemo()
    val yts = HubSourceMemo()
}
