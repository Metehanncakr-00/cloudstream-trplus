package com.cloudstream.tr.sinemacx

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.extractors.FilmizleInExtractor
import com.cloudstream.tr.core.model.ProviderModels
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class SinemaCX : MainAPI() {
    override var mainUrl = "https://sinemacc.com"
    override var name = "SinemaCX"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)

    private fun decodeIframeUrl(src: String): String? {
        val trimmed = src.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("//")) {
            return fixUrlNull(trimmed)
        }
        return try {
            val decoded = String(android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
            if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                decoded
            } else {
                fixUrlNull(trimmed)
            }
        } catch (_: Exception) {
            try {
                val decoded = String(android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
                if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                    decoded
                } else {
                    fixUrlNull(trimmed)
                }
            } catch (_: Exception) {
                fixUrlNull(trimmed)
            }
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenen Filmler",
        "${mainUrl}/dil/turkce-dublaj/" to "Türkçe Dublaj",
        "${mainUrl}/dil/turkce-altyazi/" to "Türkçe Altyazılı",
        "${mainUrl}/en-cok-izlenen-filmler/" to "En Çok İzlenenler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        mainUrl = com.cloudstream.tr.core.network.DynamicDomainResolver.resolve(name, mainUrl)
        val reqUrl = request.data.replace(Regex("https?://[^/]+"), mainUrl)

        val targetUrl = if (page <= 1) {
            reqUrl
        } else {
            val base = reqUrl.removeSuffix("/")
            "${base}/page/${page}/"
        }

        try {
            val doc = app.get(targetUrl).document
            val items = doc.select(".film_kutusu, div.frag-k, div.film-k").mapNotNull { el ->
                parseFragCard(el)
            }.distinctBy { it.url }

            return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
        } catch (e: Exception) {
            com.cloudstream.tr.core.network.DynamicDomainResolver.fallbackToNextMirror(name)
            throw e
        }
    }

    fun parseFragCard(element: Element): SearchResponse? {
        val linkEl = element.selectFirst("a") ?: return null
        val href = fixUrlNull(linkEl.attr("href")) ?: return null

        val imgEl = element.selectFirst("img")
        val rawTitle = linkEl.attr("title").ifBlank { null }
            ?: element.selectFirst(".film_adi, div.f-baslik, h2, h3, .baslik")?.text()?.ifBlank { null }
            ?: imgEl?.attr("alt")?.ifBlank { null }
            ?: return null

        val poster = fixUrlNull(
            imgEl?.attr("data-src")?.ifBlank { null }
                ?: imgEl?.attr("src")?.ifBlank { null }
        )

        val year = element.selectFirst("span.yil, span.f-yil, div.yil")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
            ?: Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()

        val cleanTitle = rawTitle
            .replace(Regex("""\s*\(\d{4}\)$"""), "")
            .replace(" Türkçe Dublaj İzle", "")
            .replace(" Türkçe Altyazı İzle", "")
            .replace(" Film Posteri", "")
            .replace(" İzle", "")
            .replace(" izle", "")
            .trim()

        return newMovieSearchResponse(cleanTitle, href, TvType.Movie) {
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
        val items = doc.select(".film_kutusu, div.frag-k, div.film-k, article.film").mapNotNull { el ->
            parseFragCard(el)
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
        }?.replace(" - SinemaCX", "")?.replace(" - Sinema.gg", "")?.replace(" Full HD İzle", "")?.replace(" İzle", "")?.trim() ?: return null

        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst("div.film_resmi img, div.f-afis img, div.f-bilgi img, .afis img")?.let {
                    it.attr("data-src").ifBlank { null } ?: it.attr("src").ifBlank { null }
                }
        )

        val plot = doc.selectFirst("meta[property='og:description']")?.attr("content")
            ?: doc.selectFirst("div.film_ozeti, div.f-ozet, div.konu")?.text()?.trim()

        val year = doc.selectFirst("span.f-yil, span.yil, div.f-detay")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
            ?: Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()

        val cleanTitle = rawTitle.replace(Regex("""\s*\(\d{4}\)$"""), "").trim()
        val score = doc.selectFirst("span.imdb, span.puan, div.f-puan, .film_puani")?.text()?.trim()
        val tags = doc.select("a[href*='/kategori/'], a[href*='/tur/']").map { it.text().trim() }.filter { it.isNotBlank() }

        return newMovieLoadResponse(cleanTitle, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            this.score = Score.from10(score)
        }
    }

    data class FilmizleVideoResponse(
        @JsonProperty("securedLink") val securedLink: String? = null,
        @JsonProperty("hls") val hls: Boolean? = null,
        @JsonProperty("videoSource") val videoSource: String? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var linksFound = false
        val doc = app.get(data).document

        val pagesToCheck = mutableListOf(doc)
        val part2Url = doc.selectFirst("a[href*='/2/'], .part-sayfala a")?.attr("href")
            ?: if (!data.endsWith("/2/")) "${data.removeSuffix("/")}/2/" else null

        if (part2Url != null) {
            try {
                val p2Doc = app.get(fixUrl(part2Url)).document
                pagesToCheck.add(p2Doc)
            } catch (e: Exception) {
                // ignore
            }
        }

        val allIframes = mutableListOf<String>()
        for (pageDoc in pagesToCheck) {
            for (iframe in pageDoc.select("iframe, [data-vsrc]")) {
                val rawSrc = iframe.attr("data-vsrc").ifEmpty { iframe.attr("data-src").ifEmpty { iframe.attr("src") } }
                if (rawSrc.isNotBlank() && !rawSrc.contains("youtube", ignoreCase = true)) {
                    val resolved = decodeIframeUrl(rawSrc)
                    if (resolved != null && !resolved.contains("vr_set=") && !resolved.contains("/fragman")) {
                        allIframes.add(resolved)
                    }
                }
            }
        }

        val distinctIframes = allIframes.distinct()
        val count = BoundedParallelResolver.resolveProgressive(
            candidates = distinctIframes,
            provider = name,
            resolver = { iframeUrl, emitLink ->
                if (iframeUrl.contains("filmizle.in")) {
                    FilmizleInExtractor().getUrl(iframeUrl, referer = "https://sinemacc.com/", subtitleCallback, emitLink)
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
