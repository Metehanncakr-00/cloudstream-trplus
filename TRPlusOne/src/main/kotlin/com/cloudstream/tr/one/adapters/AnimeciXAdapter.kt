package com.cloudstream.tr.one.adapters

import com.cloudstream.tr.animecix.AnimeciX
import com.cloudstream.tr.one.models.ProviderListing
import com.cloudstream.tr.one.models.TREpisodeInfo
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink

class AnimeciXAdapter(
    val api: AnimeciX = AnimeciX()
) : TRProviderAdapter {

    override val providerId: String = "AnimeciX"
    override val displayName: String = "AnimeciX"
    override var isEnabled: Boolean = true
    override val supportedTypes: Set<TvType> = setOf(TvType.Anime, TvType.AnimeMovie)

    private val categoryUrls = mapOf(
        "Anime" to "${api.mainUrl}/secure/titles?type=series&onlyStreamable=true",
        "Son Eklenenler" to "${api.mainUrl}/secure/titles?type=series&onlyStreamable=true"
    )

    override suspend fun fetchCatalog(category: String, page: Int): List<ProviderListing> {
        val targetUrl = categoryUrls[category] ?: "${api.mainUrl}/secure/titles?type=series&onlyStreamable=true"
        return try {
            val response = api.getMainPage(page, MainPageRequest(category, targetUrl, false))
            response.items.flatMap { it.list }.map { item ->
                ProviderListing(
                    providerId = providerId,
                    providerUrl = item.url,
                    title = item.name,
                    mediaType = item.type ?: TvType.Anime,
                    posterUrl = item.posterUrl,
                    confidenceScore = 1.0
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<ProviderListing> {
        return try {
            val results = api.search(query, page)
            results.items.map { item ->
                ProviderListing(
                    providerId = providerId,
                    providerUrl = item.url,
                    title = item.name,
                    mediaType = item.type ?: TvType.Anime,
                    posterUrl = item.posterUrl,
                    confidenceScore = 1.0
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun loadDetail(url: String): ProviderListing? {
        return try {
            val res = api.load(url) ?: return null
            val episodes = when (res) {
                is TvSeriesLoadResponse -> {
                    res.episodes.map { ep ->
                        TREpisodeInfo(
                            name = ep.name ?: "${ep.season}. Sezon ${ep.episode}. Bölüm",
                            season = ep.season ?: 1,
                            episode = ep.episode ?: 1,
                            dataUrl = ep.data,
                            posterUrl = ep.posterUrl
                        )
                    }
                }
                else -> null
            }

            ProviderListing(
                providerId = providerId,
                providerUrl = url,
                title = res.name,
                mediaType = res.type,
                year = res.year,
                posterUrl = res.posterUrl,
                seasons = episodes,
                languageEvidence = listOf("TR Altyazı"),
                confidenceScore = 1.0
            )
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun loadStreamLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            api.loadLinks(data, isCasting, subtitleCallback, callback)
        } catch (_: Throwable) {
            false
        }
    }
}
