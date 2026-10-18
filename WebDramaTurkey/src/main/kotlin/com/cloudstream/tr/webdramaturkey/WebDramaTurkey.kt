package com.cloudstream.tr.webdramaturkey

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class WebDramaTurkey : MainAPI() {
    override var mainUrl = "https://webdramaturkey2.com"
    override var name = "WebDramaTurkey"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler" to "Diziler",
        "${mainUrl}/filmler" to "Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            "${request.data}?page=${page}"
        }

        val document = app.get(targetUrl).document
        val items = parseSearchResults(document)
        return newHomePageResponse(request.name, items)
    }

    fun toSearchResult(element: Element): SearchResponse? {
        val link = if (element.tagName() == "a") element else element.selectFirst("a[href*='/dizi/'], a[href*='/film/']") ?: return null
        val href = fixUrlNull(link.attr("href")) ?: return null
        if (!href.contains("/dizi/") && !href.contains("/film/")) return null

        val img = element.selectFirst("img")
        val title = img?.attr("alt")?.trim()
            ?.ifBlank { null }
            ?: element.selectFirst(".title, h3, h2, h4, span")?.text()?.trim()
            ?: link.text().trim().takeIf { it.isNotBlank() }
            ?: return null

        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null }
                ?: img?.attr("src")?.ifBlank { null }
        )

        val isMovie = href.contains("/film/")
        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.AsianDrama) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val targetUrl = if (page <= 1) {
            "${mainUrl}/arama?q=${query}"
        } else {
            "${mainUrl}/arama?q=${query}&page=${page}"
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
        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.title().substringBefore("İzle").substringBefore("-").trim()
        val poster = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst("div.app-detail-poster img, img[src*='cover']")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }
        )
        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst("div.app-detail-overview, p.overview, div.description")?.text()?.trim()

        val isMovie = url.contains("/film/")
        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.AsianDrama, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }

        val episodeElements = document.select("a[href*='-bolum']")
        val episodes = mutableListOf<Episode>()

        val epRegex = Regex("""/(\d+)-sezon/(\d+)-bolum""")
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

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
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

        // Collect embed IDs from buttons, anchors and dropdowns
        val embedIds = document.select("[data-embed]").mapNotNull {
            it.attr("data-embed").trim().takeIf { id -> id.isNotBlank() }
        }.distinct()

        val count = BoundedParallelResolver.resolveProgressive(
            candidates = embedIds,
            resolver = { embedId, emitLink ->
                try {
                    val resp = app.post(
                        "${mainUrl}/ajax/embed",
                        data = mapOf("id" to embedId),
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "Referer" to data
                        )
                    ).text

                    val iframeRegex = Regex("""src=["']([^"']+)["']""")
                    val videoPhpUrl = iframeRegex.find(resp)?.groupValues?.getOrNull(1)

                    if (!videoPhpUrl.isNullOrBlank()) {
                        val fixedVideoPhp = fixUrl(videoPhpUrl)
                        val videoDoc = app.get(fixedVideoPhp, headers = mapOf("Referer" to data)).document
                        val finalIframe = videoDoc.selectFirst("iframe#main-iframe, iframe[src]")?.attr("src")

                        if (!finalIframe.isNullOrBlank()) {
                            val cleanIframe = fixUrl(finalIframe).substringBefore('#')
                            if (cleanIframe.contains("dtpasn.asia")) {
                                val dtHtml = app.get(cleanIframe, headers = mapOf("Referer" to fixedVideoPhp)).text
                                val m3u8Match = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""").find(dtHtml)?.groupValues?.get(1)
                                if (!m3u8Match.isNullOrBlank()) {
                                    emitLink(newExtractorLink(
                                        source = name,
                                        name = "$name HLS",
                                        url = m3u8Match,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.referer = cleanIframe
                                        this.quality = Qualities.P1080.value
                                    })
                                }
                            } else {
                                loadExtractor(cleanIframe, fixedVideoPhp, subtitleCallback, emitLink)
                            }
                        } else {
                            // Check direct m3u8 in html
                            val html = videoDoc.html()
                            val m3u8Regex = Regex("""["'](https?://[^\s"']+\.m3u8[^\s"']*)["']""")
                            m3u8Regex.findAll(html).forEach { m ->
                                val streamUrl = m.groupValues[1]
                                emitLink(
                                    newExtractorLink(
                                        source = name,
                                        name = "$name HLS",
                                        url = streamUrl,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.referer = fixedVideoPhp
                                        this.quality = Qualities.P1080.value
                                    }
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            },
            onLinkFound = { link ->
                callback(link)
                found = true
            }
        )

        return found || count > 0
    }
}
