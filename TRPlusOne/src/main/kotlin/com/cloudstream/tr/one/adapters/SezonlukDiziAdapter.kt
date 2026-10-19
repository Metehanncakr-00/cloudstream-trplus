package com.cloudstream.tr.one.adapters

import com.cloudstream.tr.one.models.ProviderListing
import com.cloudstream.tr.one.models.TREpisodeInfo
import com.cloudstream.tr.sezonlukdizi.SezonlukDizi
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink

class SezonlukDiziAdapter(
    val api: SezonlukDizi = SezonlukDizi()
) : TRProviderAdapter {

    override val providerId: String = "SezonlukDizi"
    override val displayName: String = "SezonlukDizi"
    override var isEnabled: Boolean = true
    override val supportedTypes: Set<TvType> = setOf(TvType.TvSeries)

    private val categoryUrls = mapOf(
        "Son Eklenenler" to "${api.mainUrl}/diziler.asp?siralama_tipi=id",
        "Diziler" to "${api.mainUrl}/diziler.asp?siralama_tipi=trend"
    )

    override suspend fun fetchCatalog(category: String, page: Int): List<ProviderListing> {
        val targetUrl = categoryUrls[category] ?: "${api.mainUrl}/diziler.asp?siralama_tipi=id"
        return try {
            val response = api.getMainPage(page, MainPageRequest(category, targetUrl, false))
            response.items.flatMap { it.list }.map { item ->
                ProviderListing(
                    providerId = providerId,
                    providerUrl = item.url,
                    title = item.name,
                    mediaType = item.type ?: TvType.TvSeries,
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
                    mediaType = item.type ?: TvType.TvSeries,
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
                mediaType = TvType.TvSeries,
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
