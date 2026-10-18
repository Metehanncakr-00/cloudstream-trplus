package com.cloudstream.tr.ddizi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import com.cloudstream.tr.core.network.StreamValidator
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DDizi : MainAPI() {
    override var mainUrl = "https://www.ddizi.im"
    override var name = "DDizi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenenler",
        "${mainUrl}/diziler/" to "Tüm Diziler",
        "${mainUrl}/yerli-diziler/" to "Yerli Diziler",
        "${mainUrl}/yabanci-diziler/" to "Yabancı Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val base = request.data.removeSuffix("/")
            "$base/sayfa/$page/"
        }

        val doc = app.get(targetUrl).document
        val items = doc.select(".dizikutu, .post, article, div.dizi-listesi li, div.video-box").mapNotNull { el ->
            parseSearchItem(el)
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val targetUrl = if (page <= 1) {
            "${mainUrl}/arama?q=${query}"
        } else {
            "${mainUrl}/arama?q=${query}&sayfa=$page"
        }

        val doc = app.get(targetUrl).document
        val items = doc.select(".dizikutu, .post, article, div.dizi-listesi li, div.video-box").mapNotNull { el ->
            parseSearchItem(el)
        }
        val deduped = ProviderModels.dedupSearchResults(items)

        return newSearchResponseList(deduped, hasNext = deduped.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    fun parseSearchItem(element: Element): SearchResponse? {
        val linkEl = element.selectFirst("a[href]") ?: return null
        val href = fixUrlNull(linkEl.attr("href")) ?: return null
        if (href == mainUrl || href == "${mainUrl}/") return null

        val imgEl = element.selectFirst("img")
        val title = element.selectFirst(".title, h2, h3, .diziadi, .baslik")?.text()?.trim()
            ?: imgEl?.attr("alt")?.trim()
            ?: linkEl.attr("title").trim()
        if (title.isBlank()) return null

        val poster = fixUrlNull(
            imgEl?.attr("data-src")?.ifBlank { null }
                ?: imgEl?.attr("data-lazy-src")?.ifBlank { null }
                ?: imgEl?.attr("src")?.ifBlank { null }
        )

        val year = element.selectFirst(".yil, .year, .date")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
            this.year = year
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        return parseLoadMetadata(doc, url)
    }

    suspend fun parseLoadMetadata(doc: Document, url: String): LoadResponse? {
        val title = doc.selectFirst("h1.entry-title, h1.diziadi, h1, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }?.replace(" - DDizi", "")?.replace(" ddizi", "")?.replace(" izle", "")?.trim() ?: return null

        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst(".afis img, .dizi-afis img, img.wp-post-image")?.let {
                    it.attr("data-src").ifBlank { null } ?: it.attr("src").ifBlank { null }
                }
        )

        val description = doc.selectFirst("meta[property='og:description'], .dizi-ozeti, .entry-content p")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }

        val year = doc.selectFirst("a[href*='/yil/'], .yil, .year")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val tags = doc.select("a[href*='/kategori/'], .tur a, .kategori a").map { it.text().trim() }.filter { it.isNotBlank() }

        val episodes = mutableListOf<Episode>()
        val episodeElements = doc.select("ul.bolumler li, .bolum-item, a[href*='/bolum/'], a[href*='-bolum-']")

        if (episodeElements.isNotEmpty()) {
            episodeElements.forEachIndexed { index, el ->
                val epLink = el.selectFirst("a[href]")?.attr("href") ?: el.attr("href")
                val epHref = fixUrlNull(epLink) ?: return@forEachIndexed
                val epName = el.selectFirst(".bolumadi, .title")?.text()?.trim()
                    ?: el.text().trim().ifBlank { "${index + 1}. Bölüm" }

                val sNum = Regex("(\\d+)\\.\\s*[Ss]ezon").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val eNum = Regex("(\\d+)\\.\\s*[Bb]ölüm").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

                episodes.add(
                    newEpisode(epHref) {
                        this.name = epName
                        this.season = sNum
                        this.episode = eNum
                    }
                )
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.TvSeries, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
            }
        }
    }

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
            if (src.isNotBlank() && !src.contains("wp-embedded-content")) {
                fixUrlNull(src)?.let { iframes.add(it) }
            }
        }

        doc.select("video source[src], video[src]").forEach { v ->
            val src = fixUrlNull(v.attr("src")) ?: return@forEach
            val preflight = StreamValidator.validateStream(src, mapOf("Referer" to mainUrl), name)
            if (preflight.isValid) {
                val typeTag = if (preflight.streamType == ExtractorLinkType.M3U8) "HLS" else "MP4"
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name $typeTag",
                        url = src,
                        type = preflight.streamType
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
                linksFound = true
            }
        }

        val resolved = BoundedParallelResolver.resolveProgressive(
            candidates = iframes.distinct(),
            maxConcurrency = 4,
            resolver = { iframeUrl, emitLink ->
                val fixed = fixUrl(iframeUrl)
                loadExtractor(fixed, referer = mainUrl, subtitleCallback, emitLink)
            },
            onLinkFound = { link ->
                callback(link)
                linksFound = true
            }
        )

        return linksFound || resolved > 0
    }
}
