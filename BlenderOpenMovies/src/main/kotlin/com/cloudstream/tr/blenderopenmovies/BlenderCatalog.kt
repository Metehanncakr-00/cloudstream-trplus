package com.cloudstream.tr.blenderopenmovies

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI

/** Public channel data only. No federated search, memberships, asset archives or cached file URLs. */
internal class BlenderCatalog(private val fetch: suspend (String) -> String) {
    data class Film(val uuid: String, val title: String, val year: Int?, val poster: String?, val description: String, val attribution: String)
    data class Page(val films: List<Film>, val hasNext: Boolean)
    data class Media(val url: String, val quality: Int)

    suspend fun page(page: Int): Page {
        if (page !in 1..MAX_PAGES) return Page(emptyList(), false)
        val start = (page - 1) * PAGE_SIZE
        val root = mapper.readTree(fetch("$BASE/api/v1/video-channels/blender_open_movies/videos?count=$PAGE_SIZE&start=$start&sort=-publishedAt"))
        val rows = root.path("data")
        val films = if (rows.isArray) canonicalFilms(rows.mapNotNull(::parseFilm)) else emptyList()
        return Page(films, rows.isArray && rows.size() == PAGE_SIZE && start + PAGE_SIZE < root.path("total").asInt() && page < MAX_PAGES)
    }

    suspend fun search(query: String): List<Film> {
        val results = mutableListOf<Film>()
        for (page in 1..MAX_PAGES) {
            val result = page(page)
            results += result.films.filter { it.title.contains(query.trim(), ignoreCase = true) }
            if (!result.hasNext) break
        }
        return canonicalFilms(results)
    }

    suspend fun detail(payload: String): Pair<Film, List<Media>>? {
        val uuid = uuidFromUrl(payload) ?: return null
        val root = mapper.readTree(fetch("$BASE/api/v1/videos/$uuid"))
        if (root.path("uuid").asText() != uuid) return null
        val film = parseFilm(root) ?: return null
        val media = root.path("files").takeIf { it.isArray }?.mapNotNull { file ->
            val url = file.path("fileUrl").asText()
            val quality = reportedQuality(file.path("resolution"))
            if (!isPublicFile(url, uuid) || quality == null || file.path("hasVideo").asBoolean(true).not()) null
            else Media(url, quality)
        }.orEmpty().distinctBy { it.url }
        return film to media
    }

    companion object {
        const val BASE = "https://video.blender.org"
        const val PAGE_SIZE = 20
        const val MAX_PAGES = 10
        private val mapper = jacksonObjectMapper()
        private val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val knownFilms = mapOf(
            "bf1f3fb5-b119-4f9f-9930-8e20e892b898" to ("Big Buck Bunny" to 2008),
            "0eb052d0-fd51-43e6-aa33-ecdbf77a5d40" to ("Sintel" to 2010),
            "8533ea43-4271-4a57-9694-e9d0b35e1aa1" to ("Tears of Steel" to 2012),
            "3eb93cab-79a2-4fd5-a636-d42e5c59ddea" to ("HERO" to 2018),
            "1ba07bbb-1456-4293-99a6-833fa450eda0" to ("Singularity" to 2026),
            "3d95fb3d-c866-42c8-9db1-fe82f48ccb95" to ("Spring" to 2019),
            "f507dfdc-e73e-45a4-9778-d758cbe1ce96" to ("Cosmos Laundromat" to 2015),
            "64222c8a-c4c7-4b3b-9850-7fb2078edcf6" to ("Glass Half" to 2015),
            "6402b77c-b61f-4a06-96ca-c8420a2becf4" to ("Big Buck Bunny" to 2008)
        )
        // These three UUIDs have independently documented CC BY 3.0 rights (ATTRIBUTION.md).
        private val documentedRights = mapOf(
            "bf1f3fb5-b119-4f9f-9930-8e20e892b898" to "https://peach.blender.org/about/",
            "0eb052d0-fd51-43e6-aa33-ecdbf77a5d40" to "https://durian.blender.org/about/",
            "8533ea43-4271-4a57-9694-e9d0b35e1aa1" to "https://mango.blender.org/about/"
        )

        private fun canonicalFilms(films: List<Film>): List<Film> = films.groupBy { film ->
            // Only curated, known-year aliases identify the same work. Titles alone do not.
            knownFilms[film.uuid]?.let { (title, year) -> "work:$title:$year" } ?: "uuid:${film.uuid}"
        }.values.map { aliases ->
            aliases.minWith(compareBy<Film> { it.uuid !in documentedRights }.thenBy { it.uuid })
        }

        private fun reportedQuality(resolution: JsonNode): Int? {
            // API labels describe quality tiers, not independently verified pixel dimensions.
            val label = Regex("([1-9][0-9]{0,3})p", RegexOption.IGNORE_CASE)
                .matchEntire(resolution.path("label").asText().trim())
                ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..8640 }
            val id = resolution.path("id").takeIf { it.isIntegralNumber && it.canConvertToInt() }
                ?.asInt()?.takeIf { it in 1..8640 }
            return label ?: id
        }

        fun watchUrl(uuid: String): String = "$BASE/w/$uuid"

        private fun trustedUri(value: String): URI? = try {
            URI(value).takeIf {
                it.scheme == "https" && it.host == "video.blender.org" && it.rawUserInfo == null &&
                    it.port == -1 && it.rawQuery == null && it.rawFragment == null && it.rawPath == it.path
            }
        } catch (_: Exception) { null }

        fun uuidFromUrl(value: String): String? {
            val uri = trustedUri(value) ?: return null
            val path = uri.path.trimEnd('/')
            val uuid = path.removePrefix("/w/")
            return uuid.takeIf { path == "/w/$it" && uuidPattern.matches(it) }
        }

        fun isPublicFile(value: String, uuid: String): Boolean {
            if (!uuidPattern.matches(uuid)) return false
            val path = trustedUri(value)?.path ?: return false
            return Regex("/object-storage/web_videos/${Regex.escape(uuid)}-[0-9]{1,4}\\.mp4").matches(path) ||
                Regex("/static/webseed/${Regex.escape(uuid)}-[0-9]{1,4}\\.mp4").matches(path)
        }

        fun parseFilm(root: JsonNode): Film? {
            val uuid = root.path("uuid").asText()
            if (!uuidPattern.matches(uuid) || root.path("privacy").path("id").asInt(-1) != 1 ||
                root.path("channel").path("name").asText() != "blender_open_movies" ||
                root.path("channel").path("host").asText() != "video.blender.org" ||
                root.path("isLive").asBoolean(false)) return null
            val licence = root.path("licence").path("id").asInt(-1)
            if (licence !in setOf(1, 2) && uuid !in documentedRights) return null
            val sourceTitle = root.path("name").asText().trim()
            if (sourceTitle.isBlank()) return null
            val known = knownFilms[uuid]
            val thumbnail = root.path("thumbnailPath").asText().takeIf {
                Regex("/lazy-static/thumbnails/[0-9a-f-]+\\.(jpg|png|webp)").matches(it)
            }?.let { BASE + it }
            val rights = documentedRights[uuid]?.let {
                "CC BY 3.0: https://creativecommons.org/licenses/by/3.0/ . Rights and full credits: $it"
            } ?: "API licence: ${if (licence == 1) "Attribution" else "Attribution–ShareAlike"}. " +
                "Rights and credits: https://studio.blender.org/terms-and-conditions/"
            return Film(uuid, known?.first ?: sourceTitle, known?.second, thumbnail,
                root.path("description").asText().ifBlank { root.path("truncatedDescription").asText() },
                "Blender Foundation / Blender Studio and the film's credited artists. $rights " +
                    "Original film with full credits (unmodified): ${watchUrl(uuid)}")
        }
    }
}
