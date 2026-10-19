package com.cloudstream.tr.one

/** Deterministic, conservative merger: never drops original provider URLs. */
object CanonicalEngine {
    fun isMatch(existing: CanonicalMedia, listing: ProviderListing): Boolean =
        OneIdentity.isMatch(existing, listing)

    fun merge(
        existing: Collection<CanonicalMedia>,
        incoming: Collection<ProviderListing>,
        aliasRegistrar: ((aliasId: String, canonicalId: String) -> Unit)? = null
    ): List<CanonicalMedia> {
        val records = LinkedHashMap<String, CanonicalMedia>()
        existing.forEach { records[it.id] = it }

        for (listing in incoming) {
            if (listing.name.isBlank() || listing.url.isBlank()) continue
            val id = OneIdentity.key(listing)

            // 1. Direct ID match
            val directPrior = records[id]
            if (directPrior != null && (directPrior.year == null || listing.year == null || directPrior.year == listing.year)) {
                val dedup = (directPrior.listings + listing)
                    .distinctBy { it.providerId + "\u0000" + it.url }
                records[id] = directPrior.copy(
                    posterUrl = directPrior.posterUrl ?: listing.posterUrl,
                    year = directPrior.year ?: listing.year,
                    listings = dedup
                )
                continue
            }

            // 2. Cross-provider match (e.g. DiziYou with null year matching SezonlukDizi with year 2013)
            val matched = records.values.filter { isMatch(it, listing) }
            if (matched.size == 1) {
                val prior = matched.first()
                val dedup = (prior.listings + listing)
                    .distinctBy { it.providerId + "\u0000" + it.url }
                records[prior.id] = prior.copy(
                    posterUrl = prior.posterUrl ?: listing.posterUrl,
                    year = prior.year ?: listing.year,
                    listings = dedup
                )
                if (id != prior.id) {
                    aliasRegistrar?.invoke(id, prior.id)
                }
                OneDiagnostics.log(
                    "CANONICAL_MATCH",
                    "incoming='${listing.name}' prov=${listing.providerId} -> matched='${prior.name}' canonicalId=${prior.id}"
                )
            } else if (directPrior == null) {
                // New distinct card
                records[id] = CanonicalMedia(id, listing.name, listing.year, listing.kind, listing.posterUrl, listOf(listing))
            } else {
                // Extremely rare conflicting metadata: do not overwrite the earlier title
                val alternativeId = OneIdentity.sha("conflict:${listing.providerId}:${listing.url}")
                records[alternativeId] = CanonicalMedia(alternativeId, listing.name, listing.year, listing.kind, listing.posterUrl, listOf(listing))
            }
        }
        return records.values.toList()
    }
}
