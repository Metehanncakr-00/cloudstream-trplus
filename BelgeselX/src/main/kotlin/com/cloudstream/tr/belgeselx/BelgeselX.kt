package com.cloudstream.tr.belgeselx

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.Locale

class BelgeselX : MainAPI() {
    override var mainUrl = "https://belgeselx.com"
    override var name = "BelgeselX"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Documentary)

    override val mainPage = mainPageOf(
        "${mainUrl}/son-eklenenler" to "Son Eklenenler",
        "${mainUrl}/en-cok-izlenen-belgeseller" to "En Çok İzlenenler",
        "${mainUrl}/haftanin-trendleri" to "Haftanın Trendleri",
        "${mainUrl}/konu/turk-tarihi-belgeselleri" to "Türk Tarihi",
        "${mainUrl}/konu/tarih-belgeselleri" to "Tarih",
        "${mainUrl}/konu/doga-belgeselleri" to "Doğa",
        "${mainUrl}/konu/bilim-belgeselleri" to "Bilim",
        "${mainUrl}/konu/kozmik-belgeseller" to "Uzay ve Kozmik",
        "${mainUrl}/konu/savas-belgeselleri" to "Savaş",
        "${mainUrl}/konu/hayvan-belgeselleri" to "Hayvanlar",
        "${mainUrl}/konu/psikoloji-belgeselleri" to "Psikoloji",
        "${mainUrl}/konu/sanat-belgeselleri" to "Sanat"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val sep = if (request.data.contains("?")) "&" else "?"
            "${request.data}${sep}page=${page}"
        }

        val doc = app.get(targetUrl).document
        val items = doc.select("a[href*='/belgesel/']").mapNotNull { el ->
            parseSearchElement(el)
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items)
    }

    fun parseSearchElement(element: Element): SearchResponse? {
        val href = fixUrlNull(element.attr("href")) ?: return null
        if (!href.contains("/belgesel/")) return null
        if (href.endsWith("/belgesel") || href.endsWith("/belgeseller")) return null

        val title = element.selectFirst("img")?.attr("alt")?.ifBlank { null }
            ?: element.attr("title").ifBlank { null }
            ?: element.text().trim()
        if (title.isBlank()) return null

        val imgEl = element.selectFirst("img")
        val poster = fixUrlNull(
            imgEl?.attr("data-src")?.ifBlank { null }
                ?: imgEl?.attr("src")?.ifBlank { null }
        )

        val cleanTitle = title.replace("\n", " ").trim()

        return newTvSeriesSearchResponse(cleanTitle, href, TvType.Documentary) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val cx = "016376594590146270301:iwmy65ijgrm"

        return try {
            val tokenResponse = app.get("https://cse.google.com/cse.js?cx=${cx}")
            val cseLibVersion = Regex("cselibVersion\": \"(.*?)\"").find(tokenResponse.text)?.groupValues?.get(1) ?: ""
            val cseToken = Regex("cse_token\": \"(.*?)\"").find(tokenResponse.text)?.groupValues?.get(1) ?: ""

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val cseUrl = "https://cse.google.com/cse/element/v1?rsz=filtered_cse&num=20&hl=tr&source=gcsc&cselibv=${cseLibVersion}&cx=${cx}&q=${encodedQuery}&safe=off&cse_tok=${cseToken}&oq=${encodedQuery}&callback=google.search.cse.api9969&rurl=https%3A%2F%2Fbelgeselx.com%2F"

            val response = app.get(cseUrl).text

            val titles = Regex("\"titleNoFormatting\": \"(.*?)\"").findAll(response).map { it.groupValues[1] }.toList()
            val urls = Regex("\"url\": \"(.*?)\"").findAll(response).map { it.groupValues[1] }.toList()
            val posterUrls = Regex("\"ogImage\": \"(.*?)\"").findAll(response).map { it.groupValues[1] }.toList()

            val searchResponses = mutableListOf<SearchResponse>()
            for (i in titles.indices) {
                val title = titles[i].replace(" - belgeselx.com", "").replace(" İzle", "").trim()
                val url = urls.getOrNull(i) ?: continue
                val posterUrl = posterUrls.getOrNull(i)

                if (!url.contains("/belgesel/")) continue

                searchResponses.add(
                    newTvSeriesSearchResponse(title, url, TvType.Documentary) {
                        this.posterUrl = fixUrlNull(posterUrl)
                    }
                )
            }

            val deduped = ProviderModels.dedupSearchResults(searchResponses)
            newSearchResponseList(deduped, hasNext = false)
        } catch (e: Exception) {
            newSearchResponseList(emptyList(), hasNext = false)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        return parseLoadMetadata(doc, url)
    }

    suspend fun parseLoadMetadata(doc: Document, url: String): LoadResponse? {
        val rawTitle = doc.selectFirst("h2.px-info-title")?.let {
            val clone = it.clone()
            clone.select("span").remove()
            clone.text().trim()
        } ?: doc.selectFirst("h1, h2, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        } ?: doc.title().ifBlank { null }

        val title = rawTitle?.replace(" — belgeselx.com", "")
            ?.replace(" – belgeselx.com", "")
            ?.replace(" - belgeselx.com", "")
            ?.replace(" İzle", "")
            ?.trim() ?: return null

        val poster = fixUrlNull(doc.selectFirst("meta[property='og:image']")?.attr("content"))
        val description = doc.selectFirst("meta[property='og:description'], div.description, p.desc")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }

        val episodes = mutableListOf<Episode>()
        val epButtons = doc.select("a.px-ep-row, div.px-ep-row, a[onclick*='diziGetir']")

        val srcMap = mapOf("0" to "new5", "2" to "new1", "5" to "new4", "3" to "new2", "4" to "new3")

        if (epButtons.isNotEmpty()) {
            epButtons.forEachIndexed { index, epEl ->
                val onclick = epEl.attr("onclick")
                val params = Regex("diziGetir\\(([^)]+)\\)").find(onclick)?.groupValues?.get(1)
                val parts = params?.split(",")?.map { it.trim().removeSurrounding("'").removeSurrounding("\"") }

                val id = parts?.getOrNull(0) ?: ""
                val ic1 = parts?.getOrNull(1) ?: "0"
                val epNameParam = parts?.getOrNull(4)
                val seasonParam = parts?.getOrNull(7)?.toIntOrNull() ?: 1
                val epNumParam = parts?.getOrNull(8)?.toIntOrNull() ?: (index + 1)

                val file = srcMap[ic1] ?: "new5"
                val videoDataUrl = "${mainUrl}/video/data/${file}.php?id=${id}&sira=1"

                val epName = epNameParam?.ifBlank { null }
                    ?: epEl.text().trim().ifBlank { "${seasonParam}. Sezon ${epNumParam}. Bölüm" }

                episodes.add(
                    newEpisode(videoDataUrl) {
                        this.name = epName
                        this.season = seasonParam
                        this.episode = epNumParam
                    }
                )
            }

            return newTvSeriesLoadResponse(title, url, TvType.Documentary, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Documentary, url) {
                this.posterUrl = poster
                this.plot = description
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

        if (data.contains("/video/data/")) {
            val resp = app.get(data, referer = "${mainUrl}/").text

            // Check all iframes in data response
            Regex("<iframe\\s+[^>]*src=[\"']([^\"']+)[\"']").findAll(resp).forEach { match ->
                val fullIframe = fixUrl(match.groupValues[1])
                val cleanIframe = fullIframe.substringBefore('#')
                if (loadExtractor(cleanIframe, "${mainUrl}/", subtitleCallback, callback)) {
                    found = true
                }
            }

            // Check direct m3u8 / mp4 streams in response
            Regex("""["'](https?://[^\s"']+\.(?:m3u8|mp4)[^\s"']*)["']""").findAll(resp).forEach { m ->
                val streamUrl = m.groupValues[1]
                val isHls = streamUrl.contains(".m3u8", ignoreCase = true)
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = if (isHls) "${this.name} HLS" else this.name,
                        url = streamUrl,
                        type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "${mainUrl}/"
                        this.quality = Qualities.P1080.value
                    }
                )
                found = true
            }

            // Check direct file link in new4.php format
            Regex("file\\s*:\\s*[\"']([^\"']+)[\"']\\s*,\\s*label\\s*:\\s*[\"']([^\"']+)[\"']").findAll(resp).forEach { match ->
                val videoUrl = match.groupValues[1]
                val qualityLabel = match.groupValues[2]
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = videoUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "${mainUrl}/"
                        this.quality = getQualityFromName(qualityLabel)
                    }
                )
                found = true
            }
        } else {
            // Full documentary page fallback
            val doc = app.get(data, referer = "${mainUrl}/").document
            val iframes = doc.select("iframe[src]").mapNotNull { fixUrlNull(it.attr("src")) }
            val resolved = BoundedParallelResolver.resolveProgressive(
                candidates = iframes,
                maxConcurrency = 3,
                resolver = { src, emitLink ->
                    loadExtractor(src, "${mainUrl}/", subtitleCallback, emitLink)
                },
                onLinkFound = { link ->
                    callback(link)
                    found = true
                }
            )
            if (resolved > 0) found = true
        }

        return found
    }
}
