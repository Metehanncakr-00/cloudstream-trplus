package com.cloudstream.tr.dizipal

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class DiziPal : MainAPI() {
    override var mainUrl = "https://dizipal1430.com"
    override var name = "DiziPal"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 100L

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenenler",
        "${mainUrl}/diziler" to "Yeni Diziler",
        "${mainUrl}/filmler" to "Yeni Filmler",
        "${mainUrl}/platform/netflix" to "Netflix",
        "${mainUrl}/platform/exxen" to "Exxen",
        "${mainUrl}/platform/prime-video" to "Prime Video",
        "${mainUrl}/platform/gain" to "Gain",
        "${mainUrl}/platform/disney-plus" to "Disney+",
        "${mainUrl}/platform/hbo-max" to "HBO Max",
        "${mainUrl}/platform/tabii" to "tabii",
        "${mainUrl}/platform/apple-tv" to "Apple TV",
        "${mainUrl}/animeler" to "Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val sep = if (request.data.contains("?")) "&" else "?"
            "${request.data}${sep}page=${page}"
        }

        val doc = app.get(targetUrl).document
        val items = doc.select("a[href*='/dizi/'], a[href*='/film/']").mapNotNull { el ->
            parseSearchElement(el)
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items)
    }

    fun parseSearchElement(element: Element): SearchResponse? {
        val href = fixUrlNull(element.attr("href")) ?: return null
        if (!href.contains("/dizi/") && !href.contains("/film/")) return null
        if (href.endsWith("/diziler") || href.endsWith("/filmler")) return null

        val title = element.attr("title").ifBlank {
            element.selectFirst(".title, h2, h3, h4, span.title, div.name")?.text()?.trim()
                ?: element.text().trim()
        }
        if (title.isBlank() || title.equals("Diziler", ignoreCase = true) || title.equals("Filmler", ignoreCase = true)) return null

        val imgEl = element.selectFirst("img")
        val poster = fixUrlNull(
            imgEl?.attr("data-src")?.ifBlank { null }
                ?: imgEl?.attr("src")?.ifBlank { null }
        )

        val isSeries = href.contains("/dizi/")
        val tvType = if (isSeries) TvType.TvSeries else TvType.Movie

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, tvType) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, tvType) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val targetUrl = "${mainUrl}/api/search.php?q=${encodedQuery}"

        val response = app.get(
            targetUrl,
            headers = mapOf(
                "Referer" to "${mainUrl}/",
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).parsedSafe<DiziPalSearchRoot>()

        val items = response?.results?.mapNotNull { res ->
            val title = res.title ?: return@mapNotNull null
            val slug = res.slug ?: return@mapNotNull null
            val isSeries = res.type == "series"
            val href = if (isSeries) "${mainUrl}/dizi/${slug}" else "${mainUrl}/film/${slug}"
            val poster = fixUrlNull(res.poster)
            val tvType = if (isSeries) TvType.TvSeries else TvType.Movie

            if (isSeries) {
                newTvSeriesSearchResponse(title, href, tvType) {
                    this.posterUrl = poster
                }
            } else {
                newMovieSearchResponse(title, href, tvType) {
                    this.posterUrl = poster
                }
            }
        }?.distinctBy { it.url } ?: emptyList()

        val deduped = ProviderModels.dedupSearchResults(items)
        return newSearchResponseList(deduped, hasNext = false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        return parseLoadMetadata(doc, url)
    }

    suspend fun parseLoadMetadata(doc: Document, url: String): LoadResponse? {
        val title = doc.selectFirst("h1, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }?.substringBefore(" izle")?.trim() ?: return null

        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst("div.cover img, div.poster img")?.attr("src")
        )

        val description = doc.selectFirst("div.summary, div.overview, p.description, meta[property='og:description']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }

        val year = doc.selectFirst("span.year, div.year, a[href*='yil/']")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val score = doc.selectFirst("span.imdb-rating, div.rating, span.score")?.text()?.trim()

        val tags = doc.select("a[href*='/tur/'], a[href*='/kategori/']").map { it.text().trim() }.filter { it.isNotBlank() }

        val isSeries = url.contains("/dizi/")

        if (isSeries) {
            val episodes = doc.select("a[href*='/bolum/']").mapNotNull { epEl ->
                val epHref = fixUrlNull(epEl.attr("href")) ?: return@mapNotNull null
                val epText = epEl.text().trim()

                val seasonNum = Regex("(\\d+)\\.\\s*Sezon").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("-?(\\d+)-sezon").find(epHref)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val epNum = Regex("(\\d+)\\.\\s*Bölüm").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("-?(\\d+)-bolum").find(epHref)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                val epName = epText.ifBlank { "${seasonNum}. Sezon ${epNum}. Bölüm" }

                newEpisode(epHref) {
                    this.name = epName
                    this.season = seasonNum
                    this.episode = epNum
                }
            }.distinctBy { it.data }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.score = Score.from10(score)
                this.tags = tags
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.score = Score.from10(score)
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
        val doc = app.get(data, referer = "${mainUrl}/").document

        val candidateUrls = mutableListOf<String>()

        // Try base64 encodedContent in script
        for (s in doc.select("script")) {
            val text = s.data()
            val encodedMatch = Regex("const\\s+encodedContent\\s*=\\s*'([A-Za-z0-9+/=]+)'").find(text)
            if (encodedMatch != null) {
                val b64 = encodedMatch.groupValues[1]
                val decoded = String(base64DecodeArray(b64), StandardCharsets.UTF_8)
                val iframeSrc = Regex("src=\"([^\"]+)\"").find(decoded)?.groupValues?.get(1)
                if (iframeSrc != null) {
                    candidateUrls.add(fixUrl(iframeSrc))
                }
            }
        }

        // Direct iframes fallback
        for (iframe in doc.select("iframe[src]")) {
            val src = fixUrlNull(iframe.attr("src")) ?: continue
            candidateUrls.add(src)
        }

        var anyFound = false
        val count = BoundedParallelResolver.resolveProgressive(
            candidates = candidateUrls.distinct(),
            resolver = { candidate, emitLink ->
                if (candidate.contains("videoplay.vip")) {
                    if (resolveVideoPlay(candidate, data, subtitleCallback, emitLink)) {
                        anyFound = true
                    }
                } else {
                    if (loadExtractor(candidate, "${mainUrl}/", subtitleCallback, emitLink)) {
                        anyFound = true
                    }
                }
            },
            onLinkFound = { link ->
                callback(link)
                anyFound = true
            }
        )

        return anyFound || count > 0
    }

    private suspend fun resolveVideoPlay(
        videoPlayUrl: String,
        refererUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val resp = app.get(videoPlayUrl, referer = refererUrl).text
            val m3uPath = Regex("const\\s+src\\s*=\\s*'([^']+)'").find(resp)?.groupValues?.get(1) ?: return false

            val fullM3u = if (m3uPath.startsWith("http")) m3uPath else "https://videoplay.vip${m3uPath}"

            // parse subtitles if available
            val tracksJson = Regex("const\\s+tracksData\\s*=\\s*(\\{.*?\\});").find(resp)?.groupValues?.get(1)
            if (tracksJson != null) {
                val tracks = AppUtils.tryParseJson<VideoPlayTracks>(tracksJson)
                tracks?.subtitles?.forEach { sub ->
                    val subUrl = sub.url ?: return@forEach
                    val subFull = if (subUrl.startsWith("http")) subUrl else "https://videoplay.vip/play.m3u8?id=${sub.id}&p=${URLEncoder.encode(subUrl, "UTF-8")}"
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = sub.name ?: "Türkçe",
                            url = subFull
                        )
                    )
                }
            }

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = fullM3u,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://videoplay.vip/"
                    this.quality = Qualities.Unknown.value
                }
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    data class DiziPalSearchRoot(
        @JsonProperty("results") val results: List<DiziPalSearchResult>? = null
    )

    data class DiziPalSearchResult(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("poster") val poster: String? = null
    )

    data class VideoPlayTracks(
        @JsonProperty("subtitles") val subtitles: List<VideoPlaySubtitle>? = null
    )

    data class VideoPlaySubtitle(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("lang") val lang: String? = null,
        @JsonProperty("url") val url: String? = null
    )
}
