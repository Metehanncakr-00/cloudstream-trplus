package com.cloudstream.tr.one

import java.security.MessageDigest
import java.util.Locale
import java.text.Normalizer

/** Pure Kotlin value models. No dependency on the previous CloudStreamHub. */
enum class OneKind { MOVIE, SERIES, ANIME_SERIES, ANIME_MOVIE }

data class ProviderListing(
    val providerId: String,
    val url: String,
    val name: String,
    val year: Int? = null,
    val kind: OneKind,
    val posterUrl: String? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val lastSeenMs: Long = 0L
)

data class CanonicalMedia(
    val id: String,
    val name: String,
    val year: Int?,
    val kind: OneKind,
    val posterUrl: String?,
    val listings: List<ProviderListing>
)

object OneIdentity {
    private val noise = Regex("\\b(?:turkce dublaj|turkce altyazili|full hd|1080p|720p|izle)\\b", RegexOption.IGNORE_CASE)
    private val yearText = Regex("\\b(?:19|20)\\d{2}\\b")
    fun normalizedTitle(title: String, year: Int?): String {
        // ROOT lowercase + Unicode folding prevents "Interstellar" -> "ınterstellar".
        // Dotted/dotless Turkish I and accent variants must compare consistently.
        val raw = Normalizer.normalize(title.lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(noise, " ")
            .let { if (year != null) it.replace(year.toString(), " ") else it }
        return raw.replace(yearText, " ")
            .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
    }
    fun sha(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .take(12).joinToString("") { "%02x".format(it) }

    /** Unknown-year cross-provider matches are deliberately not considered safe. */
    fun key(listing: ProviderListing): String {
        val trustworthy = listing.imdbId?.takeIf { it.matches(Regex("tt\\d{6,12}")) }
            ?.let { "imdb:$it" }
            ?: listing.tmdbId?.takeIf { it > 0 }?.let { "tmdb:${listing.kind}:$it" }
        val name = normalizedTitle(listing.name, listing.year)
        val fallback = if (name.isNotBlank() && listing.year != null) {
            "${listing.kind}:$name:${listing.year}"
        } else "${listing.kind}:${listing.providerId}:${listing.url}"
        return sha(trustworthy ?: fallback)
    }
}
