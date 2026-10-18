package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * Bounded late-arrival reservoir: holds the strongest pending 4K/REMUX candidates,
 * independent of arrival order. The first high-quality link is emitted immediately
 * by the caller; this buffer cannot retract links already shown to the user.
 */
internal class HubFeatureTopK(private val limit: Int) {
    private val pending = mutableMapOf<String, ExtractorLink>()
    private val order = compareByDescending<ExtractorLink> { it.quality }
        .thenByDescending { HubTorrentSelector.sourceScore(it) }
        .thenBy { it.source }
        .thenBy { it.url }

    init { require(limit >= 0) }

    @Synchronized fun offer(link: ExtractorLink) {
        if (limit == 0 || link.url.isBlank()) return
        val old = pending[link.url]
        if (old != null && order.compare(old, link) <= 0) return
        pending[link.url] = link
        if (pending.size > limit) {
            val worst = pending.values.sortedWith(order).last()
            pending.remove(worst.url)
        }
    }

    @Synchronized fun snapshot(): List<ExtractorLink> = pending.values.sortedWith(order)
    @Synchronized fun size(): Int = pending.size
}
