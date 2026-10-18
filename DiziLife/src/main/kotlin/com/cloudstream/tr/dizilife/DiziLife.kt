package com.cloudstream.tr.dizilife

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DiziLife : MainAPI() {
    override var mainUrl = "https://dizi74.life"
    override var name = "DiziLife"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler" to "Popüler Diziler",
        "${mainUrl}/filmler" to "Popüler Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            "${request.data}?sayfa=${page}"
        }

        val document = app.get(targetUrl).document
        val items = parseSearchResults(document)
        return newHomePageResponse(request.name, items)
    }

    fun toSearchResult(element: Element): SearchResponse? {
        val link = if (element.tagName() == "a") element else element.selectFirst("a[href*='/dizi/'], a[href*='/film/']") ?: return null
        val href = fixUrlNull(link.attr("href")) ?: return null
        if (href.endsWith("/diziler") || href.endsWith("/filmler") || href.contains("/sezon/")) return null

        val img = element.selectFirst("img")
        val rawTitle = img?.attr("alt")?.trim()
            ?.ifBlank { null }
            ?: element.selectFirst(".title, h3, h2, h4, span")?.text()?.trim()
            ?: link.text().trim().takeIf { it.isNotBlank() }
            ?: return null

        val cleanTitle = rawTitle.replace("İzle", "", ignoreCase = true)
            .replace("izle", "", ignoreCase = true)
            .trim()

        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null }
                ?: img?.attr("src")?.ifBlank { null }
        )

        val isTvSeries = href.contains("/dizi/")
        return if (isTvSeries) {
            newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(cleanTitle, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val targetUrl = if (page <= 1) {
            "${mainUrl}/ara?q=${query}"
        } else {
            "${mainUrl}/ara?q=${query}&sayfa=${page}"
        }

        val document = app.get(targetUrl).document
        val items = parseSearchResults(document)
        return newSearchResponseList(items, hasNext = false)
    }

    fun parseSearchResults(document: Document): List<SearchResponse> {
        val elements = document.select("a[href*='/dizi/'], a[href*='/film/']")
        val results = elements.mapNotNull { toSearchResult(it) }
        return ProviderModels.dedupSearchResults(results)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        return parseLoadMetadata(document, url)
    }

    suspend fun parseLoadMetadata(document: Document, url: String): LoadResponse? {
        val rawTitle = document.selectFirst("h1")?.text()?.trim()
            ?: document.title().substringBefore("İzle").substringBefore("—").trim()

        val title = rawTitle.replace("İzle", "", ignoreCase = true).trim()

        val poster = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst("img[src*='poster'], img[src*='cover'], img[src*='upload']")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }
        )

        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst("div.description, div.overview, p")?.text()?.trim()

        val isTvSeries = url.contains("/dizi/")
        if (!isTvSeries) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }

        val episodeElements = document.select("a[href*='/sezon/'][href*='/bolum/']")
        val episodes = mutableListOf<Episode>()

        val epRegex = Regex("""/sezon/(\d+)/bolum/(\d+)""")
        val seenUrls = mutableSetOf<String>()

        for (el in episodeElements) {
            val href = fixUrlNull(el.attr("href")) ?: continue
            if (!seenUrls.add(href)) continue

            val match = epRegex.find(href)
            val seasonNum = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val epNum = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1

            val epName = el.text().trim().takeIf { it.isNotBlank() } ?: "$seasonNum. Sezon $epNum. Bölüm"

            episodes.add(
                newEpisode(href) {
                    this.name = epName
                    this.season = seasonNum
                    this.episode = epNum
                }
            )
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        val document = app.get(data).document

        val iframes = mutableListOf<String>()
        for (it in document.select("iframe[src]")) {
            val src = fixUrlNull(it.attr("src"))
            if (!src.isNullOrBlank() && !src.contains("google") && !src.contains("recaptcha")) {
                iframes.add(src)
            }
        }

        val candidates = iframes.distinct()

        val count = BoundedParallelResolver.resolveProgressive(
            candidates = candidates,
            resolver = { iframeUrl, emitLink ->
                loadExtractor(iframeUrl, data, subtitleCallback, emitLink)
            },
            onLinkFound = { link ->
                callback(link)
                found = true
            }
        )

        return found || count > 0
    }
}
