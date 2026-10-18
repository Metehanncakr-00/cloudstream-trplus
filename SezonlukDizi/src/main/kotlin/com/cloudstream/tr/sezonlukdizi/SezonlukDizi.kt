package com.cloudstream.tr.sezonlukdizi

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class SezonlukDizi : MainAPI() {
    override var mainUrl = "https://sezonlukdizi.cc"
    override var name = "SezonlukDizi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler.asp?siralama_tipi=id" to "Son Eklenenler",
        "${mainUrl}/diziler.asp?siralama_tipi=trend" to "Popüler Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=imdb" to "En Yüksek IMDb"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val sep = if (request.data.contains("?")) "&" else "?"
            "${request.data}${sep}s=${page}"
        }

        val doc = app.get(targetUrl).document
        val items = doc.select("div.afis a.column, div.afis .column, a[href*='/diziler/']").mapNotNull { el ->
            parseAfis(el)
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    fun parseAfis(element: Element): SearchResponse? {
        val linkEl = if (element.tagName() == "a") element else element.selectFirst("a") ?: return null
        val href = fixUrlNull(linkEl.attr("href")) ?: return null
        if (!href.contains("/diziler/")) return null

        val imgEl = element.selectFirst("img")
        val rawTitle = linkEl.attr("title").ifBlank { null }
            ?: element.selectFirst("span.dizi-adi, .header, h3, h2")?.text()?.ifBlank { null }
            ?: imgEl?.attr("alt")?.ifBlank { null }
            ?: return null

        val poster = fixUrlNull(
            imgEl?.attr("data-src")?.ifBlank { null }
                ?: imgEl?.attr("src")?.ifBlank { null }
        )

        val year = element.selectFirst("span.yil, .meta, span.dizi-yil")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val cleanTitle = rawTitle.replace(" izle", "").replace(" İzle", "").trim()

        return newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries) {
            this.posterUrl = poster
            this.year = year
        }
    }

    data class SearchAjaxResponse(
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("results") val results: SearchResultsWrapper? = null
    )

    data class SearchResultsWrapper(
        @JsonProperty("diziler") val diziler: SearchCategoryResult? = null
    )

    data class SearchCategoryResult(
        @JsonProperty("results") val results: List<SearchItem>? = null
    )

    data class SearchItem(
        @JsonProperty("did") val did: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("image") val image: String? = null,
        @JsonProperty("imdb") val imdb: Double? = null
    )

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val resp = app.post(
            "${mainUrl}/ajax/arama.asp",
            data = mapOf("q" to query),
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "${mainUrl}/"
            )
        ).parsedSafe<SearchAjaxResponse>()

        val items = resp?.results?.diziler?.results?.mapNotNull { item ->
            val href = fixUrlNull(item.url) ?: return@mapNotNull null
            val rawTitle = item.title ?: return@mapNotNull null
            val cleanTitle = rawTitle.replace(Regex("""\s*\(\d{4}\)$"""), "").trim()
            val year = Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
            val poster = fixUrlNull(item.image)

            newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.year = year
            }
        } ?: emptyList()

        val deduped = ProviderModels.dedupSearchResults(items)
        return newSearchResponseList(deduped, hasNext = false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("meta[property='og:title']")?.attr("content")
            ?.replace(" İzle", "")?.replace(" - Sezonluk Dizi", "")?.trim()
            ?: doc.selectFirst("h1, .dizi-adi")?.text()?.trim()
            ?: return null

        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst("div.afis img, div.dizi-afis img, .poster img")?.let {
                    it.attr("data-src").ifBlank { null } ?: it.attr("src").ifBlank { null }
                }
        )

        val plot = doc.selectFirst("meta[property='og:description']")?.attr("content")
            ?: doc.selectFirst("div#ozet, div.ozet, .dizi-ozet")?.text()?.trim()

        val year = doc.selectFirst("span.yil, .dizi-bilgi")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val score = doc.selectFirst("span.imdb, .puan")?.text()?.trim()
        val tags = doc.select("div.etiketler a, a[href*='kategori']").map { it.text().trim() }.filter { it.isNotBlank() }

        val bolumlerUrl = if (url.contains("/diziler/")) {
            url.replace("/diziler/", "/bolumler/")
        } else {
            doc.selectFirst("a[href*='/bolumler/']")?.attr("href")?.let { fixUrlNull(it) } ?: url
        }

        val epDoc = if (bolumlerUrl != url) {
            try {
                app.get(bolumlerUrl).document
            } catch (e: Exception) {
                doc
            }
        } else {
            doc
        }

        val episodeElements = epDoc.select("table#bolumler tr, table tr, a[href*='-sezon-']").filter { el ->
            val href = el.selectFirst("a")?.attr("href") ?: el.attr("href")
            href.contains("-sezon-") && href.contains("-bolum")
        }

        val episodes = episodeElements.mapNotNull { el ->
            val a = if (el.tagName() == "a") el else el.selectFirst("a") ?: return@mapNotNull null
            val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val match = Regex("""/(\d+)-sezon-(\d+)-bolum""").find(href)
            val seasonNum = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val epNum = match?.groupValues?.get(2)?.toIntOrNull() ?: 1

            newEpisode(href) {
                this.name = "${seasonNum}. Sezon ${epNum}. Bölüm"
                this.season = seasonNum
                this.episode = epNum
            }
        }.distinctBy { it.data }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            this.score = Score.from10(score)
        }
    }

    data class AlternatifResponse(
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("data") val data: List<AlternatifItem>? = null
    )

    data class AlternatifItem(
        @JsonProperty("id") val id: Long? = null,
        @JsonProperty("baslik") val baslik: String? = null,
        @JsonProperty("kalite") val kalite: Int? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        var linksFound = false

        val bid = doc.selectFirst("div#dilsec")?.attr("data-id")
            ?: Regex("""data-id=["'](\d+)["']""").find(doc.html())?.groupValues?.get(1)

        data class SourceCandidate(val id: Long, val isDubbed: Boolean, val isSubbed: Boolean)
        val candidates = mutableListOf<SourceCandidate>()

        if (!bid.isNullOrBlank()) {
            val languages = listOf("1" to false, "0" to true) // 1: Altyazılı, 0: Dublaj
            for ((dil, isDubbed) in languages) {
                try {
                    val altResp = app.post(
                        "${mainUrl}/ajax/dataAlternatif22.asp",
                        data = mapOf("bid" to bid, "dil" to dil),
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "Referer" to data
                        )
                    ).parsedSafe<AlternatifResponse>()

                    altResp?.data?.forEach { item ->
                        val sourceId = item.id ?: return@forEach
                        candidates.add(SourceCandidate(sourceId, isDubbed = isDubbed, isSubbed = !isDubbed))
                    }
                } catch (_: Exception) {}
            }
        }

        val count = BoundedParallelResolver.resolveProgressive(
            candidates = candidates,
            resolver = { cand, emitLink ->
                try {
                    val embedHtml = app.post(
                        "${mainUrl}/ajax/dataEmbed22.asp",
                        data = mapOf("id" to cand.id.toString()),
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "Referer" to data
                        )
                    ).text

                    val iframeSrc = Jsoup.parse(embedHtml).selectFirst("iframe")?.attr("src")
                    if (!iframeSrc.isNullOrBlank() && !iframeSrc.contains("reCAPTCHA", ignoreCase = true)) {
                        val fixedSrc = fixUrl(iframeSrc)
                        loadExtractor(fixedSrc, referer = data, subtitleCallback) { link ->
                            val tagged = ExtractorLink(
                                source = link.source,
                                name = ProviderModels.formatSourceTitle(link.name, isDubbed = cand.isDubbed, isSubbed = cand.isSubbed),
                                url = link.url,
                                referer = link.referer,
                                quality = link.quality,
                                type = link.type,
                                headers = link.headers,
                                extractorData = link.extractorData
                            )
                            emitLink(tagged)
                        }
                    }
                } catch (_: Exception) {}
            },
            onLinkFound = { link ->
                callback(link)
                linksFound = true
            }
        )

        // Also check any existing iframes directly on page
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
            if (src.isNotBlank() && !src.contains("reCAPTCHA", ignoreCase = true)) {
                val fixed = fixUrl(src)
                val success = loadExtractor(fixed, referer = data, subtitleCallback) { link ->
                    callback(link)
                    linksFound = true
                }
                if (success) linksFound = true
            }
        }

        return linksFound || count > 0
    }
}
