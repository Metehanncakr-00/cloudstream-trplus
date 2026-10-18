package com.cloudstream.tr.animeler

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Animeler : MainAPI() {
    override var mainUrl = "https://animeler.pw"
    override var name = "Animeler"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        "${mainUrl}" to "Son Eklenen Bölümler",
        "${mainUrl}/filter?sort=popular" to "Popüler Animeler",
        "${mainUrl}/filter?sort=new" to "Yeni Animeler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            if (request.data.contains("?")) "${request.data}&page=${page}" else "${request.data}?page=${page}"
        }

        val document = app.get(targetUrl).document
        val items = parseSearchResults(document)
        return newHomePageResponse(request.name, items)
    }

    fun toSearchResult(element: Element): SearchResponse? {
        val link = if (element.tagName() == "a") element else element.selectFirst("a") ?: return null
        var href = fixUrlNull(link.attr("href")) ?: return null
        if (href.contains("/user/") || href.contains("/filter") || href.contains("/discord")) return null

        if (href.contains("/bolum-")) {
            href = href.substringBefore("/bolum-")
        }

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

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val targetUrl = if (page <= 1) {
            "${mainUrl}/filter?search=${query}"
        } else {
            "${mainUrl}/filter?search=${query}&page=${page}"
        }

        val document = app.get(targetUrl).document
        val items = parseSearchResults(document)
        return newSearchResponseList(items, hasNext = false)
    }

    fun parseSearchResults(document: Document): List<SearchResponse> {
        val elements = document.select("a.anime-card-modern, a:has(img[src*='animecover']), a:has(img[src*='portrait']), div.anime-card, div.col a")
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

        var poster: String? = null
        for (img in document.select("img")) {
            val src = img.attr("data-src").ifBlank { img.attr("src") }
            if (src.contains("animecover") || src.contains("portrait")) {
                poster = fixUrlNull(src)
                break
            }
        }

        val description = document.selectFirst("div.anime-synopsis, p.synopsis, div.synopsis, div.desc")?.text()?.trim()

        val episodeElements = document.select("a[href*='/bolum-']")
        val episodes = mutableListOf<Episode>()

        val epRegex = Regex("""/bolum-(\d+)""")
        val seenUrls = mutableSetOf<String>()

        for (el in episodeElements) {
            val href = fixUrlNull(el.attr("href")) ?: continue
            if (!seenUrls.add(href)) continue

            val match = epRegex.find(href)
            val epNum = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

            val epName = el.text().trim().takeIf { it.isNotBlank() } ?: "$epNum. Bölüm"

            episodes.add(
                newEpisode(href) {
                    this.name = epName
                    this.season = 1
                    this.episode = epNum
                }
            )
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            addEpisodes(DubStatus.Subbed, episodes)
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
        document.select("iframe[id='fansubPlayerIframe'], iframe[src*='embed'], iframe[data-player-src]").forEach {
            val src = fixUrlNull(it.attr("src").ifBlank { it.attr("data-player-src") })
            if (!src.isNullOrBlank()) {
                iframes.add(src)
            }
        }

        val candidates = iframes.distinct()

        val count = BoundedParallelResolver.resolveProgressive(
            candidates = candidates,
            resolver = { embedUrl, emitLink ->
                try {
                    val embedDoc = app.get(embedUrl, headers = mapOf("Referer" to data)).document
                    val html = embedDoc.html()

                    val innerPlayer = embedDoc.selectFirst("iframe#innerPlayer, iframe[src]")?.attr("src")
                    val uRedirect = Regex("""var\s+u\s*=\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1)?.replace("""\/""", "/")
                        ?: Regex("""location\.(?:replace|href)\s*=\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1)?.replace("""\/""", "/")

                    val targetPlayerUrl = innerPlayer?.takeIf { it.isNotBlank() } ?: uRedirect
                    if (!targetPlayerUrl.isNullOrBlank()) {
                        val fixedInner = fixUrl(targetPlayerUrl)
                        loadExtractor(fixedInner, embedUrl, subtitleCallback, emitLink)
                    } else {
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
                                    this.referer = embedUrl
                                    this.quality = Qualities.P1080.value
                                }
                            )
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
