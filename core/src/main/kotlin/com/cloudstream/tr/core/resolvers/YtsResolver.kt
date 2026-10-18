package com.cloudstream.tr.core.resolvers

import com.cloudstream.tr.core.diagnostics.DiagnosticCategory
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.diagnostics.DiagnosticStage
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import java.net.URLEncoder

data class YtsResponse(
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("data") val data: YtsData? = null
)

data class YtsData(
    @param:JsonProperty("movies") val movies: List<YtsMovie>? = null
)

data class YtsMovie(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("imdb_code") val imdbCode: String? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("year") val year: Int? = null,
    @param:JsonProperty("rating") val rating: Double? = null,
    @param:JsonProperty("genres") val genres: List<String>? = null,
    @param:JsonProperty("description_full") val descriptionFull: String? = null,
    @param:JsonProperty("yt_trailer_code") val ytTrailerCode: String? = null,
    @param:JsonProperty("medium_cover_image") val mediumCoverImage: String? = null,
    @param:JsonProperty("large_cover_image") val largeCoverImage: String? = null,
    @param:JsonProperty("background_image_original") val backgroundImage: String? = null,
    @param:JsonProperty("torrents") val torrents: List<YtsTorrent>? = null
)

data class YtsTorrent(
    @param:JsonProperty("url") val url: String? = null,
    @param:JsonProperty("hash") val hash: String? = null,
    @param:JsonProperty("quality") val quality: String? = null,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("video_codec") val videoCodec: String? = null,
    @param:JsonProperty("size") val size: String? = null,
    @param:JsonProperty("seeds") val seeds: Int? = null,
    @param:JsonProperty("peers") val peers: Int? = null
)

/**
 * Return first usable mirror response without waiting for its slower sibling.
 * Each competing request must handle ordinary network errors itself.
 */
internal suspend fun <T> firstNonEmptyMirror(fetchers: List<suspend () -> List<T>>): List<T> =
    coroutineScope {
        if (fetchers.isEmpty()) return@coroutineScope emptyList()
        val results = Channel<Result<List<T>>>(fetchers.size)
        var userCancellation: CancellationException? = null
        val jobs = fetchers.map { fetch ->
            launch {
                try {
                    results.send(Result.success(fetch()))
                } catch (c: CancellationException) {
                    userCancellation = c
                    results.trySend(Result.failure(c))
                    throw c
                } catch (error: Throwable) {
                    results.trySend(Result.failure(error))
                }
            }
        }
        try {
            repeat(fetchers.size) {
                val candidateRes = results.receive()
                val candidate = candidateRes.getOrNull()
                if (candidate != null && candidate.isNotEmpty()) {
                    coroutineContext.cancelChildren()
                    return@coroutineScope candidate
                }
            }
            userCancellation?.let { throw it }
            emptyList()
        } finally {
            coroutineContext.cancelChildren()
            results.close()
        }
    }

object YtsResolver {
    private val mirrors = listOf(
        "https://yts.lt",
        "https://yts.bz",
        "https://yts.gg",
        "https://yts.rs",
        "https://yts.do"
    )

    suspend fun resolve(imdbId: String, isMovie: Boolean): List<ExtractorLink> {
        if (!isMovie || !Regex("tt[0-9]+").matches(imdbId)) return emptyList()

        // Two-mirror waves avoid a sequence of multiple 4-second timeouts.
        // Each batch waits at most for its slowest mirror, not both sequentially.
        for (wave in mirrors.chunked(2)) {
            val first = firstNonEmptyMirror(wave.map { mirror ->
                suspend {
                    try {
                        val targetUrl = mirror + "/api/v2/list_movies.json?query_term=" + imdbId
                        val response = parseResponse(app.get(targetUrl, timeout = 4).text)
                        mapResponse(response, imdbId, DebridConfig.isDebridEnabled)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        DiagnosticLogger.log(
                            provider = "YTS", stage = DiagnosticStage.LOAD,
                            category = DiagnosticCategory.NETWORK,
                            message = "YTS mirror request failed (" + e.javaClass.simpleName + ")"
                        )
                        emptyList()
                    }
                }
            })
            if (first.isNotEmpty()) return first
        }
        return emptyList()
    }

    internal fun parseResponse(body: String): YtsResponse? {
        if (!body.trimStart().startsWith("{")) return null
        return try {
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue<YtsResponse>(body)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun mapTorrents(torrents: List<YtsTorrent>?, movieTitle: String, hasDebrid: Boolean = DebridConfig.isDebridEnabled): List<ExtractorLink> {
        if (torrents.isNullOrEmpty()) return emptyList()
        return torrents.mapNotNull { t ->
            val hash = t.hash?.takeIf { TorrentTrackers.isValidHash(it) } ?: return@mapNotNull null
            val encodedTitle = URLEncoder.encode(movieTitle, "UTF-8")
            val magnet = "magnet:?xt=urn:btih:$hash&dn=$encodedTitle&index=0${TorrentTrackers.asMagnetParam}"

            val qStr = t.quality ?: "Unknown"
            val codecStr = t.videoCodec?.let { " $it" } ?: ""
            val sizeStr = t.size?.let { " [$it]" } ?: ""
            val seedCount = t.seeds

            if (!hasDebrid && seedCount != null && seedCount < 3) return@mapNotNull null

            val seedStr = if (seedCount == null) " (seeds unknown)" else " ($seedCount seeds)"
            val displayName = "YTS $qStr$codecStr$sizeStr$seedStr".trim()

            val mappedQuality = TorrentioResolver.qualityOf(qStr)

            com.lagradost.cloudstream3.utils.newExtractorLink(
                source = "YTS",
                name = displayName,
                url = magnet,
                type = ExtractorLinkType.TORRENT
            ) {
                this.quality = mappedQuality
            }
        }
    }

    internal suspend fun mapResponse(response: YtsResponse?, imdbId: String, hasDebrid: Boolean): List<ExtractorLink> {
        if (response?.status != "ok") return emptyList()
        val movies = response.data?.movies
        if (movies.isNullOrEmpty()) return emptyList()

        val movie = movies.firstOrNull { it.imdbCode == imdbId } ?: return emptyList()
        return mapTorrents(movie.torrents, movie.title ?: "Movie", hasDebrid)
    }
}
