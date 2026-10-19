package com.cloudstream.tr.one.playback

import com.cloudstream.tr.one.adapters.TRAdapterRegistry
import com.cloudstream.tr.one.models.CanonicalEpisodePayload
import com.cloudstream.tr.one.models.CanonicalEpisodeSourceRef
import com.cloudstream.tr.one.models.CanonicalPayload
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.concurrent.atomic.AtomicBoolean

object TRPlaybackDispatcher {

    /**
     * Tıklanan kartın detaylarını çözer.
     */
    suspend fun load(dataUrl: String, api: MainAPI): LoadResponse? {
        val payload = CanonicalPayload.fromJsonString(dataUrl)

        val primaryProviderId = payload?.primaryProvider ?: detectProviderId(dataUrl)
        val primaryUrl = payload?.primaryUrl ?: dataUrl

        val adapter = TRAdapterRegistry.getAdapter(primaryProviderId)
            ?: TRAdapterRegistry.getEnabledAdapters().firstOrNull()
            ?: return null

        val detail = adapter.loadDetail(primaryUrl) ?: return null

        // TV Dizisi veya Anime ise bölümleri kanonik formatta oluştur
        if (detail.mediaType == TvType.TvSeries || detail.mediaType == TvType.Anime) {
            val episodes = detail.seasons?.map { ep ->
                val epPayload = CanonicalEpisodePayload(
                    canonicalId = payload?.canonicalId ?: "canonical",
                    season = ep.season,
                    episode = ep.episode,
                    sources = listOf(
                        CanonicalEpisodeSourceRef(
                            providerId = detail.providerId,
                            data = ep.dataUrl
                        )
                    )
                )

                api.newEpisode(epPayload.toJsonString()) {
                    this.name = ep.name
                    this.season = ep.season
                    this.episode = ep.episode
                    this.posterUrl = ep.posterUrl ?: detail.posterUrl
                }
            } ?: emptyList()

            return if (detail.mediaType == TvType.Anime) {
                api.newAnimeLoadResponse(detail.title, dataUrl, TvType.Anime) {
                    this.posterUrl = detail.posterUrl
                    this.year = detail.year
                    this.showStatus = ShowStatus.Ongoing
                    addEpisodes(DubStatus.Subbed, episodes)
                }
            } else {
                api.newTvSeriesLoadResponse(detail.title, dataUrl, TvType.TvSeries, episodes) {
                    this.posterUrl = detail.posterUrl
                    this.year = detail.year
                }
            }
        }

        // Film ise doğrudan FilmLoadResponse oluştur
        return api.newMovieLoadResponse(detail.title, dataUrl, TvType.Movie, dataUrl) {
            this.posterUrl = detail.posterUrl
            this.year = detail.year
        }
    }

    /**
     * Oynatma bağlantılarını çözer ve her bağlantıyı sağlayıcı adıyla etiketler.
     */
    suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val anyFound = AtomicBoolean(false)

        // 1. Dizi / Anime bölüm yükü mü?
        val epPayload = CanonicalEpisodePayload.fromJsonString(data)
        if (epPayload != null && epPayload.sources.isNotEmpty()) {
            for (src in epPayload.sources) {
                val adapter = TRAdapterRegistry.getAdapter(src.providerId) ?: continue
                try {
                    val success = adapter.loadStreamLinks(
                        data = src.data,
                        isCasting = isCasting,
                        subtitleCallback = subtitleCallback,
                        callback = { link ->
                            anyFound.set(true)
                            // Oynatıcı menüsünde kaynak adını açıkça belirt: [Sağlayıcı] LinkAdı
                            val taggedLink = ExtractorLink(
                                source = "[${adapter.displayName}] ${link.source}",
                                name = "[${adapter.displayName}] ${link.name}",
                                url = link.url,
                                referer = link.referer,
                                quality = link.quality,
                                type = link.type,
                                headers = link.headers
                            )
                            callback(taggedLink)
                        }
                    )
                    if (success) anyFound.set(true)
                } catch (_: Throwable) {
                    // Bir sağlayıcı çökerse diğerini engelleme
                }
            }
            return anyFound.get()
        }

        // 2. Film kanonik yükü mü?
        val moviePayload = CanonicalPayload.fromJsonString(data)
        if (moviePayload != null && moviePayload.sources.isNotEmpty()) {
            for (src in moviePayload.sources) {
                val adapter = TRAdapterRegistry.getAdapter(src.providerId) ?: continue
                try {
                    val success = adapter.loadStreamLinks(
                        data = src.url,
                        isCasting = isCasting,
                        subtitleCallback = subtitleCallback,
                        callback = { link ->
                            anyFound.set(true)
                            val taggedLink = ExtractorLink(
                                source = "[${adapter.displayName}] ${link.source}",
                                name = "[${adapter.displayName}] ${link.name}",
                                url = link.url,
                                referer = link.referer,
                                quality = link.quality,
                                type = link.type,
                                headers = link.headers
                            )
                            callback(taggedLink)
                        }
                    )
                    if (success) anyFound.set(true)
                } catch (_: Throwable) {
                    // Diğer kaynakları koru
                }
            }
            return anyFound.get()
        }

        // 3. Düz URL ise sağlayıcıyı tespit et ve yönlendir
        val detectedId = detectProviderId(data)
        val adapter = TRAdapterRegistry.getAdapter(detectedId)
        if (adapter != null) {
            return adapter.loadStreamLinks(data, isCasting, subtitleCallback, callback)
        }

        return false
    }

    private fun detectProviderId(url: String): String {
        return when {
            url.contains("hdfilmcehennemi", ignoreCase = true) -> "HDFilmCehennemi"
            url.contains("sezonlukdizi", ignoreCase = true) -> "SezonlukDizi"
            url.contains("animecix", ignoreCase = true) -> "AnimeciX"
            else -> "HDFilmCehennemi"
        }
    }
}
