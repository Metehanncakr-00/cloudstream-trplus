package com.cloudstream.tr.core.model

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.cloudstream.tr.core.network.PreflightResult
import com.cloudstream.tr.core.network.ValidationStatus
import com.cloudstream.tr.core.streaming.SmartPlayEngine

object StreamPrioritySorter {
    /** Pure ordering: language is explicit track evidence, never a title/URL guess.
     * Unknown metadata remains tier zero; no candidates are cloned or decorated here.
     */
    fun comparator(
        preference: String = "TR_DUB",
        evidenceFor: (ExtractorLink) -> ManifestLanguageEvidence = { ManifestLanguageEvidence() },
        preflightFor: (ExtractorLink) -> PreflightResult? = { null },
        torrentsAtBottom: Boolean = false,
        unknownPolicy: String = "DEPRIORITIZE"
    ): Comparator<ExtractorLink> = Comparator { a, b ->
        if (torrentsAtBottom) {
            val aTorrent = isTorrentLink(a)
            val bTorrent = isTorrentLink(b)
            if (!aTorrent && bTorrent) return@Comparator -1
            if (aTorrent && !bTorrent) return@Comparator 1
        }
        val aTier = evidenceFor(a).tier(preference, unknownPolicy)
        val bTier = evidenceFor(b).tier(preference, unknownPolicy)
        if (aTier != bTier) return@Comparator bTier.compareTo(aTier)

        val aScore = SmartPlayEngine.calculateScore(a, preflightFor(a) ?: PreflightResult(ValidationStatus.INDETERMINATE, a.type))
        val bScore = SmartPlayEngine.calculateScore(b, preflightFor(b) ?: PreflightResult(ValidationStatus.INDETERMINATE, b.type))
        if (aScore != bScore) return@Comparator bScore.compareTo(aScore)

        val sCmp = a.source.compareTo(b.source)
        if (sCmp != 0) return@Comparator sCmp
        val nCmp = a.name.compareTo(b.name)
        if (nCmp != 0) return@Comparator nCmp
        val uCmp = a.url.compareTo(b.url)
        if (uCmp != 0) return@Comparator uCmp
        val rCmp = a.referer.compareTo(b.referer)
        if (rCmp != 0) return@Comparator rCmp
        a.headers.entries.sortedBy { it.key }.toString().compareTo(b.headers.entries.sortedBy { it.key }.toString())
    }

    private fun isTorrentLink(link: ExtractorLink): Boolean {
        return link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET ||
            link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.TORRENT ||
            link.url.startsWith("magnet:", ignoreCase = true)
    }


    /**
     * Calculates an integer priority score for an ExtractorLink.
     * Higher score indicates higher playback preference (instant CDN > local TR > torrent fallback).
     */
    fun getPriorityScore(link: ExtractorLink): Int {
        return com.cloudstream.tr.core.streaming.SmartPlayEngine.calculateScore(link, null)
    }

    /**
     * Sorts a list of ExtractorLink descending by priority score.
     */
    fun sortByPriority(links: List<ExtractorLink>): List<ExtractorLink> {
        val sorted = links.sortedByDescending { getPriorityScore(it) }.toMutableList()
        if (sorted.isNotEmpty()) {
            val first = sorted[0]
            val smartPlayName = "[⚡ Smart Play] ${first.name}"
            val smartPlayLink = ExtractorLink(
                source = first.source,
                name = smartPlayName,
                url = first.url,
                referer = first.referer,
                quality = first.quality,
                type = first.type,
                headers = first.headers,
                extractorData = first.extractorData,
                audioTracks = first.audioTracks
            )
            sorted[0] = smartPlayLink
        }
        return sorted
    }
}
