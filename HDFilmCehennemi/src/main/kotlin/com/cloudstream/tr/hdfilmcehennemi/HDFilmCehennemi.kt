package com.cloudstream.tr.hdfilmcehennemi

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HDFilmCehennemi : MainAPI() {
    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenen Filmler",
        "${mainUrl}/tur/animasyon-izle/" to "Animasyon Filmleri",
        "${mainUrl}/category/film-izle-2/" to "Filmler",
        "${mainUrl}/yabancidiziizle-5/" to "Diziler",
        "${mainUrl}/dil/turkce-dublajli-film-izleyin-6/" to "Türkçe Dublaj",
        "${mainUrl}/dil/turkce-altyazili-filmleri-izleme-sitesi-3/" to "Türkçe Altyazılı"
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        mainUrl = com.cloudstream.tr.core.network.DynamicDomainResolver.resolve(name, mainUrl)
        val reqUrl = request.data.replace(Regex("https?://[^/]+"), mainUrl)

        val url = if (page <= 1) {
            reqUrl
        } else {
            val base = if (reqUrl.endsWith("/")) reqUrl else "${reqUrl}/"
            "${base}page/$page/"
        }
        try {
            val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
            val home = parseHomePage(doc)
            return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
        } catch (e: Exception) {
            com.cloudstream.tr.core.network.DynamicDomainResolver.fallbackToNextMirror(name)
            throw e
        }
    }

    fun parseHomePage(doc: Document): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        for (el in doc.select("a.poster, a.card, div.poster, div.card")) {
            parseSearchElement(el)?.let { items.add(it) }
        }
        return items.distinctBy { it.url }
    }

    fun parseSearchElement(element: Element): SearchResponse? {
        val linkEl = if (element.tagName() == "a") element else element.selectFirst("a") ?: return null
        val href = fixUrlNull(linkEl.attr("href")) ?: return null
        val title = element.selectFirst("h2.title, h3.title, h4.title, div.title, .poster-title")?.text()?.trim()
            ?: linkEl.attr("title").ifEmpty { null }
            ?: element.selectFirst("img")?.attr("alt")?.trim()
            ?: return null

        val posterUrl = fixUrlNull(
            element.selectFirst("img")?.let {
                it.attr("data-src").ifEmpty { null }
                    ?: it.attr("srcset").split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                    ?: it.attr("src")
            }
        )

        val isTv = href.contains("/dizi/")
        val type = if (isTv) TvType.TvSeries else TvType.Movie

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        }
    }

    data class SearchApiResponse(
        @JsonProperty("results") val results: List<String>? = null
    )

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val res = app.get(
                "${mainUrl}/search?q=${query}",
                headers = mapOf(
                    "User-Agent" to userAgent,
                    "X-Requested-With" to "fetch",
                    "Content-Type" to "application/json"
                )
            ).parsed<SearchApiResponse>()

            val searchResponses = mutableListOf<SearchResponse>()
            for (rawHtml in res.results.orEmpty()) {
                val doc = Jsoup.parse(rawHtml)
                doc.selectFirst("a.search-result")?.let { a ->
                    val url = fixUrlNull(a.attr("href")) ?: return@let
                    val title = a.selectFirst("h4.title, .title")?.text()?.trim() ?: a.attr("aria-label")
                    val poster = fixUrlNull(
                        a.selectFirst("img")?.let { img ->
                            img.attr("src").ifEmpty { null }
                                ?: img.attr("srcset").split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                        }
                    )
                    val isTv = url.contains("/dizi/")
                    val type = if (isTv) TvType.TvSeries else TvType.Movie

                    if (type == TvType.TvSeries) {
                        searchResponses.add(newTvSeriesSearchResponse(title, url, type) {
                            this.posterUrl = poster
                        })
                    } else {
                        searchResponses.add(newMovieSearchResponse(title, url, type) {
                            this.posterUrl = poster
                        })
                    }
                }
            }
            ProviderModels.dedupSearchResults(searchResponses)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        return parseLoadMetadata(doc, url)
    }

    suspend fun parseLoadMetadata(doc: Document, url: String): LoadResponse? {
        val title = doc.selectFirst("h1.section-title, h1, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else {
                it.select("small").remove()
                it.text().trim()
            }
        }?.replace(" - HDFilmCehennemi", "")?.replace(" Full HD izle", "")?.trim() ?: return null

        val poster = fixUrlNull(doc.selectFirst("meta[property='og:image']")?.attr("content"))
        val description = doc.selectFirst("meta[property='og:description'], div.description, article.text-white")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }
        val year = doc.selectFirst("span.year, div.year")?.text()?.trim()?.toIntOrNull()
        val score = doc.selectFirst("span.imdb")?.text()?.trim()

        val isTv = url.contains("/dizi/") || doc.select(".seasons, .seasons-tabs-wrapper").isNotEmpty()

        if (isTv) {
            val episodes = mutableListOf<Episode>()
            for (a in doc.select("div.seasons-tab-content a.mini-poster, div.seasons a[href*='bolum']")) {
                val epHref = fixUrlNull(a.attr("href")) ?: continue
                val epTitle = a.selectFirst(".mini-poster-title")?.text()?.trim() ?: a.text().trim()

                // e.g. 1. Sezon 1. Bölüm
                val sMatch = Regex("""(\d+)\.\s*Sezon""").find(epTitle)
                val eMatch = Regex("""(\d+)\.\s*B[öo]l[üu]m""").find(epTitle)

                val seasonNum = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val epNum = eMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                episodes.add(
                    newEpisode(epHref) {
                        this.name = epTitle
                        this.season = seasonNum
                        this.episode = epNum
                    }
                )
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.score = Score.from10(score)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.score = Score.from10(score)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        val wrappedCallback: (ExtractorLink) -> Unit = { link ->
            found = true
            callback(link)
        }
        val doc = app.get(data, headers = mapOf("User-Agent" to userAgent)).document

        // 1. Direct iframes or rapidrame embeds
        val iframes = mutableListOf<String>()
        for (iframe in doc.select("iframe")) {
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
            if (src.isNotEmpty() && !src.contains("youtube.com") && !src.contains("youtu.be")) {
                fixUrlNull(src)?.let { iframes.add(it) }
            }
        }

        val rapidrameExtractor = RapidrameExtractor()
        val distinctIframes = iframes.distinct()

        val count = BoundedParallelResolver.resolveProgressive(
            candidates = distinctIframes,
            resolver = { iframeUrl, emitLink ->
                if (iframeUrl.contains("rapidrame") || iframeUrl.contains("hdfilmcehennemi.mobi")) {
                    rapidrameExtractor.getUrl(iframeUrl, data, subtitleCallback, emitLink)
                } else {
                    loadExtractor(iframeUrl, data, subtitleCallback, emitLink)
                }
            },
            onLinkFound = { link ->
                callback(link)
                found = true
            }
        )

        return found || count > 0
    }
}


