package com.cloudstream.tr.rarefilmm

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class RareFilmm : MainAPI() {
    override var mainUrl = "https://rarefilmm.com"
    override var name = "RareFilmm"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Latest Rare Films",
        "${mainUrl}/category/rare-films/" to "Rare Films",
        "${mainUrl}/category/world-cinema/" to "World Cinema"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) request.data else "${request.data}page/${page}/"
        val document = app.get(targetUrl).document
        val items = parseSearchResults(document)
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val link = element.selectFirst("a[href*='rarefilmm.com/']") ?: element
        val href = fixUrlNull(link.attr("href")) ?: return null
        if (href.endsWith("#respond") || href == mainUrl || href == "${mainUrl}/") return null

        val primarySpan = element.selectFirst("span.entry-title-primary")
        val rawTitle = primarySpan?.text()?.trim()
            ?: link.text().trim()

        val cleanTitle = rawTitle.replace("WATCH HERE", "", ignoreCase = true)
            .replace("DOWNLOAD HERE", "", ignoreCase = true)
            .trim()

        if (cleanTitle.isBlank()) return null

        val img = element.selectFirst("img")
        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null }
                ?: img?.attr("src")?.ifBlank { null }
        )

        return newMovieSearchResponse(cleanTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
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
        val articles = document.select("article")
        val results = if (articles.isNotEmpty()) {
            articles.mapNotNull { toSearchResult(it) }
        } else {
            document.select("div.post, div.entry-title a").mapNotNull { toSearchResult(it) }
        }
        return ProviderModels.dedupSearchResults(results)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        return parseLoadMetadata(document, url)
    }

    suspend fun parseLoadMetadata(document: Document, url: String): LoadResponse? {
        val titleSpan = document.selectFirst("h1.entry-title span.entry-title-primary")
        val rawTitle = titleSpan?.text()?.trim()
            ?: document.selectFirst("h1.entry-title, h1")?.text()?.trim()
            ?: document.title().substringBefore("|").substringBefore("-").trim()

        val cleanTitle = rawTitle.replace("WATCH HERE", "", ignoreCase = true).trim()

        val poster = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst("div.entry-content img")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }
        )

        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst("div.entry-content p")?.text()?.trim()

        return newMovieLoadResponse(cleanTitle, url, TvType.Movie, url) {
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

        // 1. Direct iframes in entry-content
        document.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src").trim()
            if (src.isNotBlank() && !src.contains("google") && !src.contains("facebook")) {
                iframes.add(fixUrl(src))
            }
        }

        // 2. Links to ok.ru, archive.org, mega or other streaming hosts
        document.select("div.entry-content a[href]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.contains("ok.ru/video") || href.contains("archive.org/embed")) {
                iframes.add(fixUrl(href))
            }
        }

        val candidates = iframes.distinct()
        val count = BoundedParallelResolver.resolveProgressive(
            candidates = candidates,
            resolver = { iframeUrl, emitLink ->
                try {
                    if (iframeUrl.contains("ok.ru/")) {
                        extractOkRu(iframeUrl, emitLink)
                    } else {
                        loadExtractor(iframeUrl, data, subtitleCallback, emitLink)
                    }
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

    internal suspend fun extractOkRu(iframeUrl: String, emitLink: (ExtractorLink) -> Unit) {
        val okHtml = app.get(iframeUrl).text
        val jsonOptions = Regex("""data-options="([^"]+)"""").find(okHtml)?.groupValues?.get(1)
            ?.let { org.jsoup.parser.Parser.unescapeEntities(it, false) } ?: return

        val root = try {
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(jsonOptions)
        } catch (_: Exception) { null } ?: return

        val metadata = root.path("flashvars").path("metadata").asText()
        val metaJson = if (!metadata.isNullOrBlank()) {
            try {
                com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(metadata)
            } catch (_: Exception) { null }
        } else root

        metaJson?.path("videos")?.forEach { v ->
            val vUrl = v.path("url").asText()
            val vName = v.path("name").asText()
            val quality = when (vName.lowercase()) {
                "quad" -> 1440
                "full" -> 1080
                "hd" -> 720
                "sd" -> 480
                "low" -> 360
                "lowest", "mobile" -> 240
                else -> Qualities.Unknown.value
            }
            if (vUrl.isNotBlank()) {
                emitLink(newExtractorLink(
                    source = name,
                    name = "$name OK.ru ${vName.uppercase()}",
                    url = vUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = iframeUrl
                    this.quality = quality
                })
            }
        }

        val hlsUrl = metaJson?.path("hlsManifestUrl")?.asText()
        if (!hlsUrl.isNullOrBlank()) {
            emitLink(newExtractorLink(
                source = name,
                name = "$name OK.ru HLS",
                url = hlsUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = iframeUrl
                this.quality = Qualities.Unknown.value
            })
        }
    }
}
