package com.cloudstream.tr.dizimom

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.extractors.PeacemakerExtractor
import com.cloudstream.tr.core.model.ProviderModels
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DiziMom : MainAPI() {
    override var mainUrl = "https://www.dizimom.diy"
    override var name = "DiziMom"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenen Bölümler",
        "${mainUrl}/diziler/" to "Tüm Diziler",
        "${mainUrl}/populer-diziler/" to "Popüler Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val base = request.data.removeSuffix("/")
            "${base}/page/${page}/"
        }

        val doc = app.get(targetUrl).document
        val items = doc.select("article.post, div.post, div.video-item, div.item").mapNotNull { el ->
            parseSearchElement(el)
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    fun parseSearchElement(element: Element): SearchResponse? {
        val linkEl = element.selectFirst("a") ?: return null
        val href = fixUrlNull(linkEl.attr("href")) ?: return null

        val imgEl = element.selectFirst("img")
        val title = element.selectFirst("h2, h3, .entry-title, .title")?.text()?.ifBlank { null }
            ?: linkEl.attr("title").ifBlank { null }
            ?: imgEl?.attr("alt")?.ifBlank { null }
            ?: return null

        val poster = fixUrlNull(
            imgEl?.attr("data-src")?.ifBlank { null }
                ?: imgEl?.attr("src")?.ifBlank { null }
        )

        val year = element.selectFirst("span.year, .date")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
            ?: Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()

        val cleanTitle = title.replace(Regex("""\s*\(\d{4}\)$"""), "").replace(" İzle", "").trim()

        return newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries) {
            this.posterUrl = poster
            this.year = year
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val targetUrl = if (page <= 1) {
            "${mainUrl}/?s=${query}"
        } else {
            "${mainUrl}/page/${page}/?s=${query}"
        }

        val doc = app.get(targetUrl).document
        val items = doc.select("article.post, div.post, div.video-item, div.search-result").mapNotNull { el ->
            parseSearchElement(el)
        }
        val deduped = ProviderModels.dedupSearchResults(items)
        return newSearchResponseList(deduped, hasNext = deduped.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        return parseLoadMetadata(doc, url)
    }

    suspend fun parseLoadMetadata(doc: Document, url: String): LoadResponse? {
        val rawTitle = doc.selectFirst("h1, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }?.replace(" - DiziMom", "")?.replace(" İzle", "")?.trim() ?: return null

        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst("div.poster img, div.single-media img, img.wp-post-image")?.let {
                    it.attr("data-src").ifBlank { null } ?: it.attr("src").ifBlank { null }
                }
        )

        val plot = doc.selectFirst("meta[property='og:description']")?.attr("content")
            ?: doc.selectFirst("div.entry-content, div.description, div.overview")?.text()?.trim()

        val year = doc.selectFirst("span.year, div.info-date")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
            ?: Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()

        val score = doc.selectFirst("span.rating, .score, span.imdb")?.text()?.trim()
        val tags = doc.select("a[href*='/kategori/'], a[href*='/tur/'], a[href*='/genre/']").map { it.text().trim() }.filter { it.isNotBlank() }

        val episodeElements = doc.select("a[href*='/bolum/'], div.seasons-wrap a, ul.episodes a").filter { el ->
            val href = el.attr("href")
            href.contains("/bolum/") || href.contains("-sezon-")
        }.distinctBy { it.attr("href") }

        val episodes = episodeElements.mapNotNull { el ->
            val epHref = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
            val epText = el.text().trim()

            val sMatch = Regex("""(\d+)\.\s*Sezon""").find(epText)
                ?: Regex("""sezon-(\d+)""").find(epHref)
                ?: Regex("""/(\d+)-sezon""").find(epHref)

            val eMatch = Regex("""(\d+)\.\s*B[öo]l[üu]m""").find(epText)
                ?: Regex("""bolum-(\d+)""").find(epHref)
                ?: Regex("""/(\d+)-bolum""").find(epHref)

            val seasonNum = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val epNum = eMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

            newEpisode(epHref) {
                this.name = "${seasonNum}. Sezon ${epNum}. Bölüm"
                this.season = seasonNum
                this.episode = epNum
            }
        }

        val cleanTitle = rawTitle.replace(Regex("""\s*\(\d{4}\)$"""), "").trim()

        return newTvSeriesLoadResponse(cleanTitle, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            this.score = Score.from10(score)
        }
    }

    data class VideoSourceItem(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("label") val label: String? = null
    )

    data class PeacemakerResponse(
        @JsonProperty("videoSources") val videoSources: List<VideoSourceItem>? = null,
        @JsonProperty("securedLink") val securedLink: String? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var linksFound = false
        val doc = app.get(data).document

        val iframes = mutableListOf<String>()
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
            if (src.isNotBlank()) {
                fixUrlNull(src)?.let { iframes.add(it) }
            }
        }

        val distinctIframes = iframes.distinct()
        val count = BoundedParallelResolver.resolveProgressive(
            candidates = distinctIframes,
            resolver = { iframeUrl, emitLink ->
                if (iframeUrl.contains("peacemakerst.com")) {
                    PeacemakerExtractor().getUrl(iframeUrl, referer = "https://www.dizimom.diy/", subtitleCallback, emitLink)
                } else {
                    loadExtractor(iframeUrl, referer = data, subtitleCallback, emitLink)
                }
            },
            onLinkFound = { link ->
                callback(link)
                linksFound = true
            }
        )

        return linksFound || count > 0
    }
}
