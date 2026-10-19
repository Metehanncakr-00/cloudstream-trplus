package com.cloudstream.tr.one

sealed class OneRoute {
    data class Media(val id: String) : OneRoute()
    data class Movie(val id: String) : OneRoute()
    data class Episode(val id: String, val season: Int, val episode: Int) : OneRoute()
}

/**
 * Pure, isolated codec for internal TR+ ONE routing identifiers.
 * CloudStream automatically normalizes card and episode URLs against MainAPI.mainUrl ("https://trplus.one.invalid").
 * This codec parses both the canonical HTTPS format and legacy / TV Box prepend variations,
 * while safely rejecting foreign or malformed addresses.
 */
object OneRouteCodec {
    const val DOMAIN = "trplus.one.invalid"
    const val BASE_URL = "https://$DOMAIN"
    private const val HTTP_BASE_URL = "http://$DOMAIN"
    private const val SCHEME = "one://"

    private val ID_REGEX = Regex("^[a-zA-Z0-9_-]{6,64}$")

    fun mediaUrl(id: String): String = "$BASE_URL/media/$id"
    fun movieUrl(id: String): String = "$BASE_URL/movie/$id"
    fun episodeUrl(id: String, season: Int, episode: Int): String = "$BASE_URL/episode/$id/$season/$episode"

    fun isValidId(id: String): Boolean = id.isNotBlank() && ID_REGEX.matches(id)

    /**
     * Parses and normalizes any route string from CloudStream, TV Box, or local cache.
     * Supported formats:
     * - New canonical: https://trplus.one.invalid/media/{id}
     * - TV Box fixed-up: https://trplus.one.invalid/one://media/{id}
     * - Legacy v1: one://media/{id}
     * - Relative path: /media/{id} or media/{id}
     *
     * Similarly for movie and episode routes.
     * Safely returns null for invalid, malformed, or foreign URLs.
     */
    fun parse(raw: String?): OneRoute? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        // Strip known base hosts / protocols
        val path = when {
            trimmed.startsWith(BASE_URL) -> trimmed.removePrefix(BASE_URL)
            trimmed.startsWith(HTTP_BASE_URL) -> trimmed.removePrefix(HTTP_BASE_URL)
            trimmed.startsWith(SCHEME) -> trimmed.removePrefix(SCHEME)
            trimmed.startsWith("/") -> trimmed
            trimmed.startsWith("media/") || trimmed.startsWith("movie/") || trimmed.startsWith("episode/") -> "/$trimmed"
            else -> return null // Foreign domain or unrecognized scheme
        }.let {
            // In case TV prepended BASE_URL to a scheme-bearing URL (e.g. /one://media/{id})
            var s = it
            if (s.startsWith("/")) s = s.removePrefix("/")
            if (s.startsWith(SCHEME)) s = s.removePrefix(SCHEME)
            if (s.startsWith("/")) s = s.removePrefix("/")
            s
        }

        // Clean query parameters and fragments if any
        val cleanPath = path.substringBefore("?").substringBefore("#").trim()

        return when {
            cleanPath.startsWith("media/") -> {
                val id = cleanPath.removePrefix("media/").substringBefore("/")
                if (isValidId(id)) OneRoute.Media(id) else null
            }
            cleanPath.startsWith("movie/") -> {
                val id = cleanPath.removePrefix("movie/").substringBefore("/")
                if (isValidId(id)) OneRoute.Movie(id) else null
            }
            cleanPath.startsWith("episode/") -> {
                val parts = cleanPath.removePrefix("episode/").split("/").filter { it.isNotBlank() }
                if (parts.size >= 3) {
                    val id = parts[0]
                    val s = parts[1].toIntOrNull()
                    val e = parts[2].toIntOrNull()
                    if (isValidId(id) && s != null && s >= 0 && e != null && e >= 0) {
                        OneRoute.Episode(id, s, e)
                    } else null
                } else null
            }
            else -> null
        }
    }
}
