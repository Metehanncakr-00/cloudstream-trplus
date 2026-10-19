package com.cloudstream.tr.one

/** Deterministic, conservative merger: never drops original provider URLs. */
object CanonicalEngine {
    fun merge(existing: Collection<CanonicalMedia>, incoming: Collection<ProviderListing>): List<CanonicalMedia> {
        val records = LinkedHashMap<String, CanonicalMedia>()
        existing.forEach { records[it.id] = it }
        for (listing in incoming) {
            if (listing.name.isBlank() || listing.url.isBlank()) continue
            val id = OneIdentity.key(listing)
            val prior = records[id]
            // Stable cross-provider identity relies on verified IDs or exact title/year/type.
            if (prior == null) {
                records[id] = CanonicalMedia(id, listing.name, listing.year, listing.kind, listing.posterUrl, listOf(listing))
            } else if (prior.kind == listing.kind && (prior.year == null || listing.year == null || prior.year == listing.year)) {
                val dedup = (prior.listings + listing)
                    .distinctBy { it.providerId + "\u0000" + it.url }
                records[id] = prior.copy(
                    posterUrl = prior.posterUrl ?: listing.posterUrl,
                    year = prior.year ?: listing.year,
                    listings = dedup
                )
            } else {
                // Extremely rare conflicting metadata: do not overwrite the earlier title.
                val alternativeId = OneIdentity.sha("conflict:${listing.providerId}:${listing.url}")
                records[alternativeId] = CanonicalMedia(alternativeId, listing.name, listing.year, listing.kind, listing.posterUrl, listOf(listing))
            }
        }
        return records.values.toList()
    }
}
