package com.cloudstream.tr.filmhane

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class FilmHane : MainAPI() {
    override var mainUrl = "https://www.filmhane.shop"
    override var name = "FilmHane"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler" to "Yeni Filmler",
        "${mainUrl}/diziler" to "Yabancı Diziler",
        "${mainUrl}/yerli-filmler" to "Yerli Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) request.data else "${request.data}?page=${page}"
        val document = app.get(targetUrl).document
        val items = parseSearchResults(document)
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val link = if (element.tagName() == "a") element else element.selectFirst("a") ?: return null
        val href = fixUrlNull(link.attr("href")) ?: return null
        if (!href.contains("/film/") && !href.contains("/dizi/")) return null

        val img = element.selectFirst("img")
        val rawTitle = img?.attr("alt")?.trim()
            ?.ifBlank { null }
            ?: element.selectFirst("h2, h3, h4, .title")?.text()?.trim()
            ?: element.text().trim().ifBlank { null }
            ?: return null

        val cleanTitle = rawTitle.substringBefore("Yeni Sezonu")
            .substringBefore("Tüm Bölüm")
            .substringBefore("Full HD")
            .replace("izle", "", ignoreCase = true)
            .trim()

        if (cleanTitle.isBlank()) return null

        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null }
                ?: img?.attr("src")?.ifBlank { null }
        )

        val isMovie = href.contains("/film/")
        return if (isMovie) {
            newMovieSearchResponse(cleanTitle, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val targetUrl = if (page <= 1) {
            "${mainUrl}/?s=${query}"
        } else {
            "${mainUrl}/page/${page}/?s=${query}"
        }

        val document = app.get(targetUrl).document
        val items = parseSearchResults(document)
        return newSearchResponseList(items, hasNext = false)
    }

    fun parseSearchResults(document: Document): List<SearchResponse> {
        val elements = document.select("div.film-item, div.movie-card, a[href*='/film/']:has(img), a[href*='/dizi/']:has(img)")
        val results = elements.mapNotNull { toSearchResult(it) }
        return ProviderModels.dedupSearchResults(results)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        return parseLoadMetadata(document, url)
    }

    suspend fun parseLoadMetadata(document: Document, url: String): LoadResponse? {
        val isMovie = url.contains("/film/")
        val rawTitle = document.selectFirst("h1")?.text()?.trim()
            ?: document.title().substringBefore("izle").substringBefore("-").trim()

        val cleanTitle = rawTitle.replace("izle", "", ignoreCase = true).trim()

        val poster = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst("div.poster img, img.cover, img[src*='covers']")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }
        )

        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst("div.description, div.overview, p")?.text()?.trim()

        if (isMovie) {
            return newMovieLoadResponse(cleanTitle, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }

        // TV Series
        val episodeLinks = document.select("a[href*='/bolum-']").filter { a ->
            a.attr("href").contains("sezon")
        }

        val seasonRegex = Regex("""sezon-(\d+)/bolum-(\d+)""")
        val episodes = episodeLinks.mapNotNull { a ->
            val epHref = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val match = seasonRegex.find(epHref)
            val seasonNum = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val epNum = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1

            newEpisode(epHref) {
                this.name = "${seasonNum}. Sezon ${epNum}. Bölüm"
                this.season = seasonNum
                this.episode = epNum
            }
        }.distinctBy { it.data }

        return newTvSeriesLoadResponse(cleanTitle, url, TvType.TvSeries, episodes) {
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

        document.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src").trim()
            if (src.isNotBlank() && !src.contains("google") && !src.contains("facebook")) {
                iframes.add(fixUrl(src))
            }
        }

        val candidates = iframes.distinct()
        val count = BoundedParallelResolver.resolveProgressive(
            candidates = candidates,
            resolver = { iframeUrl, emitLink ->
                try {
                    // Map known custom CDN mirrors to standard vidmoly if necessary
                    val normalizedUrl = if (iframeUrl.contains(".cfd/embed-")) {
                        iframeUrl.replace(Regex("""https?://[^/]+/embed-"""), "https://vidmoly.to/embed-")
                    } else {
                        iframeUrl
                    }

                    loadExtractor(normalizedUrl, data, subtitleCallback, emitLink)
                } catch (e: Exception) {
                    // Gracefully ignore individual extractor error
                }
            },
            onLinkFound = { link ->
                found = true
                callback(link)
            }
        )

        return found || count > 0
    }
}
