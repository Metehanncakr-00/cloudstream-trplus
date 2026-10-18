package com.cloudstream.tr.startv

import com.cloudstream.tr.core.network.DynamicDomainResolver
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class StarTV : MainAPI() {
    override var mainUrl = "https://www.startv.com.tr"
    override var name = "StarTV"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)
    override val mainPage = mainPageOf("/dizi" to "Türk Dizileri")

    private suspend fun resolveDomain() {
        mainUrl = DynamicDomainResolver.resolve(name, mainUrl)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        resolveDomain()
        val items = if (page == 1) parseCatalog(app.get("$mainUrl/dizi").document) else emptyList()
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        resolveDomain()
        val normalizedQuery = query.lowercase(Locale.forLanguageTag("tr"))
        val items = if (page == 1) {
            parseCatalog(app.get("$mainUrl/dizi").document).filter {
                it.name.lowercase(Locale.forLanguageTag("tr")).contains(normalizedQuery)
            }
        } else emptyList()
        return newSearchResponseList(items, hasNext = false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    fun parseCatalog(document: Document): List<SearchResponse> = document.select("a[href^='/dizi/']")
        .mapNotNull { link ->
            val path = link.attr("href").trimEnd('/')
            if (!Regex("^/dizi/[^/?#]+$").matches(path)) return@mapNotNull null
            val image = link.selectFirst("img") ?: return@mapNotNull null
            val title = image.attr("alt").ifBlank { link.attr("title") }.trim()
            if (title.isBlank()) return@mapNotNull null
            newTvSeriesSearchResponse(title, "$mainUrl$path", TvType.TvSeries) {
                posterUrl = fixUrlNull(image.attr("src"))
            }
        }.distinctBy { it.url }

    fun parseSeriesId(html: String): String? =
        Regex("/api/tv-series/([a-f0-9]+)/episodes").find(html)?.groupValues?.get(1)

    override suspend fun load(url: String): LoadResponse? {
        resolveDomain()
        val path = runCatching { URI(url).path.trimEnd('/') }.getOrNull() ?: return null
        if (!Regex("^/dizi/[^/]+$").matches(path)) return null
        val detailUrl = "$mainUrl$path"
        val document = app.get(detailUrl).document
        val episodeHtml = app.get("$detailUrl/bolumler").text
        val seriesId = parseSeriesId(episodeHtml) ?: return null
        val items = fetchEpisodeItems { skip ->
            app.get("$mainUrl/api/tv-series/$seriesId/episodes?sort=episodeNo%20desc&skip=$skip&limit=48")
                .parsedSafe<StarEpisodePage>()
        } ?: return null
        val episodes = parseEpisodes(items)
        if (episodes.isEmpty()) return null
        val title = document.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" |")
            ?: return null
        return newTvSeriesLoadResponse(title, detailUrl, TvType.TvSeries, episodes) {
            posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")
            plot = document.selectFirst("meta[name=description]")?.attr("content")
        }
    }

    internal suspend fun fetchEpisodeItems(fetch: suspend (Int) -> StarEpisodePage?): List<StarEpisode>? {
        val items = mutableListOf<StarEpisode>()
        var skip = 0
        while (true) {
            val response = fetch(skip) ?: return null
            val batch = response.items ?: return null
            items.addAll(batch)
            // Repeated pages indicate a broken paging contract; never silently truncate.
            if (items.distinctBy { it.video?.referenceId ?: it.url }.size < items.size) return null
            if (batch.size < 48) return items
            skip += 48
        }
    }

    fun parseEpisodes(items: List<StarEpisode>): List<Episode> {
        val seasons = items.filter { (it.seasonNo ?: 0) > 0 && (it.episodeNo ?: 0) > 0 }
            .groupBy { it.seasonNo!! }
        return seasons.toSortedMap().flatMap { (season, entries) ->
            val offset = if (season == 1) 0 else seasons[season - 1]?.maxOf { it.episodeNo!! }
                ?: return@flatMap emptyList()
            // A missing boundary makes cumulative numbering ambiguous; fail closed.
            if (season > 1 && entries.minOf { it.episodeNo!! } != offset + 1) {
                return@flatMap emptyList()
            }
            entries.sortedBy { it.episodeNo }.mapNotNull { item ->
                val relativeEpisode = item.episodeNo!! - offset
                val reference = item.video?.referenceId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
                    ?: return@mapNotNull null
                if (relativeEpisode <= 0) return@mapNotNull null
                newEpisode(reference, initializer = {
                    this.name = item.title ?: "${item.episodeNo}. Bölüm"
                    this.season = season
                    this.episode = relativeEpisode
                    this.posterUrl = fixUrlNull(item.image?.fullPath)
                    this.description = item.plainSummary
                }, fix = false)
            }
        }.distinctBy { it.data }
    }

    fun parseRuntimeParameter(script: String): String? =
        Regex("this\\.secret=.*?\\|\\|\"([^\"]+)\"").find(script)?.groupValues?.get(1)

    fun parseHls(response: StarVideoResponse): String? {
        val url = response.data?.flavors?.hls ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return url.takeIf {
            uri.scheme == "https" && uri.host == "startv-p3.mncdn.com" && uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443) && uri.path.endsWith(".m3u8")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (!data.matches(Regex("[A-Za-z0-9_-]+"))) return false
        resolveDomain()
        val referer = "$mainUrl/"
        val script = app.get("https://player-assets.dygdigital.com/prod/js/bundle.js", referer = referer).text
        val parameter = parseRuntimeParameter(script) ?: return false
        val infoUrl = "https://dygvideo.dygdigital.com/api/video_info?akamai=true&PublisherId=1" +
            "&ReferenceId=StarTv_${URLEncoder.encode(data, "UTF-8")}" +
            "&SecretKey=${URLEncoder.encode(parameter, "UTF-8")}"
        val info = app.get(infoUrl, referer = referer).parsedSafe<StarVideoResponse>() ?: return false
        val hls = parseHls(info) ?: return false
        val links = M3u8Helper.generateM3u8(name, hls, referer)
        links.forEach(callback)
        return links.isNotEmpty()
    }
}

data class StarEpisodePage(@JsonProperty("items") val items: List<StarEpisode>? = null)

data class StarEpisode(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("heading") val heading: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("plain_summary") val plainSummary: String? = null,
    @JsonProperty("image") val image: StarImage? = null,
    @JsonProperty("video") val video: StarVideo? = null,
    @JsonProperty("seasonNo") val seasonNo: Int? = null,
    @JsonProperty("episodeNo") val episodeNo: Int? = null
)

data class StarImage(@JsonProperty("fullPath") val fullPath: String? = null)
data class StarVideo(@JsonProperty("referenceId") val referenceId: String? = null)
data class StarVideoResponse(@JsonProperty("data") val data: StarVideoData? = null)
data class StarVideoData(@JsonProperty("flavors") val flavors: StarFlavors? = null)
data class StarFlavors(@JsonProperty("hls") val hls: String? = null)
