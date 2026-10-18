package com.cloudstream.tr.diziyou

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import com.cloudstream.tr.core.streaming.BoundedLinkDelivery
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

class DiziYou : MainAPI() {
    override var mainUrl = "https://www.diziyou.one"
    override var name = "DiziYou"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenen Bölümler",
        "${mainUrl}/dizi-arsivi/?filtrele=tarih&sirala=DESC" to "Son Eklenen Diziler",
        "${mainUrl}/dizi-arsivi/?filtrele=alfabetik&sirala=ASC" to "Dizi Arşivi",
        "${mainUrl}/dizi-arsivi/?filtrele=imdb&sirala=DESC&yil=&imdb=7" to "Popüler Diziler (IMDb 7+)"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val base = request.data
            if (base.contains("?")) {
                val parts = base.split("?", limit = 2)
                "${parts[0].removeSuffix("/")}/page/${page}/?${parts[1]}"
            } else {
                "${base.removeSuffix("/")}/page/${page}/"
            }
        }

        val doc = app.get(targetUrl).document
        val items = mutableListOf<SearchResponse>()

        // 1. Parse episode list if present on homepage / latest episodes
        doc.select(".listepisodes a, div.bolumust a, .latest-episodes a").forEach { a ->
            val href = fixUrlNull(a.attr("href")) ?: return@forEach
            val title = a.attr("title").ifBlank { a.text() }.trim()
            if (title.isBlank()) return@forEach
            val img = a.selectFirst("img")
            val poster = fixUrlNull(img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src"))
            items.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            })
        }

        // 2. Parse series archive / categories
        doc.select(".alphabetical-category-wrapper a, div.cat-item, div.series-item, div.post, div#categorytitle").forEach { el ->
            if (el.tagName() == "a") {
                val href = fixUrlNull(el.attr("href")) ?: return@forEach
                if (href.contains("#")) return@forEach
                val title = el.attr("title").ifBlank { el.text() }.trim()
                if (title.isNotBlank()) {
                    items.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries))
                }
            } else {
                parseCard(el)?.let { items.add(it) }
            }
        }

        val distinctItems = items.distinctBy { it.url }
        return newHomePageResponse(request.name, distinctItems, hasNext = distinctItems.isNotEmpty())
    }

    fun parseCard(element: Element): SearchResponse? {
        val linkEl = element.selectFirst("a[href*='/dizi/']") ?: element.selectFirst("a") ?: return null
        val href = fixUrlNull(linkEl.attr("href")) ?: return null
        if (href.contains("#")) return null

        val imgEl = element.selectFirst("img")
        val title = element.selectFirst("div#categorytitle, h2, h3, .series-title, .title")?.text()?.ifBlank { null }
            ?: linkEl.attr("title").ifBlank { null }
            ?: imgEl?.attr("alt")?.ifBlank { null }
            ?: linkEl.text().ifBlank { null }
            ?: return null

        val poster = fixUrlNull(
            imgEl?.attr("data-src")?.ifBlank { null }
                ?: imgEl?.attr("src")?.ifBlank { null }
        )

        val year = element.selectFirst("span.year, div.year")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
            ?: Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()

        val cleanTitle = title.replace(Regex("""\s*\(\d{4}\)$"""), "").replace(" İzle", "").trim()

        return newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries) {
            this.posterUrl = poster
            this.year = year
        }
    }

    internal fun buildSearchUrl(query: String, page: Int): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return if (page <= 1) {
            "${mainUrl}/?s=${encodedQuery}"
        } else {
            "${mainUrl}/page/${page}/?s=${encodedQuery}"
        }
    }

    internal fun parseSearchResults(doc: Document): List<SearchResponse> {
        val items = doc.select("div#list-series:has(> div.cat-container), div#list-series .cat-item, div.cat-item, div.post, div.search-result, div.incontent a[href*='/dizi/']")
            .filter { el -> el.parents().none { it.hasClass("alphabetical-category-wrapper") } }
            .mapNotNull { el ->
            if (el.tagName() == "a") {
                val href = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
                if (href.contains("#")) return@mapNotNull null
                val title = el.attr("title").ifBlank { el.text() }.trim()
                if (title.isBlank()) return@mapNotNull null
                val img = el.selectFirst("img")
                val poster = fixUrlNull(img?.attr("data-src") ?: img?.attr("src"))
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            } else {
                parseCard(el)
            }
        }.distinctBy { it.url }

        return ProviderModels.dedupSearchResults(items)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val doc = app.get(buildSearchUrl(query, page)).document
        val deduped = parseSearchResults(doc)
        return newSearchResponseList(deduped, hasNext = deduped.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        return parseLoadMetadata(doc, url)
    }

    suspend fun parseLoadMetadata(doc: Document, url: String): LoadResponse? {
        // If url is an episode page and has a parent series link, we can inspect both
        var targetDoc = doc
        var targetUrl = url

        if (url.contains("-sezon-") && url.contains("-bolum")) {
            val parentSeriesLink = doc.select("a").firstOrNull { a ->
                val href = a.attr("href")
                href.isNotBlank() && !href.contains("-bolum") && !href.contains("#") && href.startsWith(mainUrl) && href != "${mainUrl}/"
            }?.attr("href")

            if (!parentSeriesLink.isNullOrBlank()) {
                try {
                    val pDoc = app.get(parentSeriesLink).document
                    if (pDoc.select("a[href*='-sezon-']").isNotEmpty()) {
                        targetDoc = pDoc
                        targetUrl = parentSeriesLink
                    }
                } catch (_: Exception) {}
            }
        }

        val rawTitle = targetDoc.selectFirst("h1, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }?.replace(" - DiziYou", "")?.replace(" İzle", "")?.replace(" izle", "")?.trim() ?: return null

        val poster = fixUrlNull(
            targetDoc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: targetDoc.selectFirst("div.cat-img img, div.poster img, img.series-poster, div.afis img")?.let {
                    it.attr("data-src").ifBlank { null } ?: it.attr("src").ifBlank { null }
                }
        )

        val plot = targetDoc.selectFirst("meta[property='og:description']")?.attr("content")?.trim()?.ifBlank { null }
            ?: targetDoc.select("div.cat-desc, div.description, div.summary, div.konu, .diziyou_desc")
                .firstNotNullOfOrNull { element ->
                    element.clone().apply { select("#icerikcat2").remove() }.text().trim().ifBlank { null }
                }

        fun normalizedLabel(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "").replace('ı', 'i')
            .replace(Regex("""[:\s\u00a0]+"""), " ").trim()

        fun validYear(value: String?): Int? = value?.trim()?.takeIf { it.matches(Regex("""\d{4}""")) }
            ?.toIntOrNull()?.takeIf { it in 1800..2099 }

        val explicitYear = targetDoc.select(".dizimeta").firstNotNullOfOrNull { label ->
            if (normalizedLabel(label.text()) !in setOf("yapim yili", "yil", "yili")) return@firstNotNullOfOrNull null
            val value = StringBuilder()
            var sibling = label.nextSibling()
            while (sibling != null) {
                if (sibling is Element && (sibling.tagName() == "br" || sibling.hasClass("dizimeta"))) break
                if (sibling is TextNode) value.append(sibling.text())
                sibling = sibling.nextSibling()
            }
            validYear(value.toString())
        }
        val year = explicitYear
            ?: targetDoc.select("span.year").firstNotNullOfOrNull { validYear(it.text()) }
            ?: validYear(Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1))
            ?: targetDoc.select("div.info span").firstNotNullOfOrNull {
                validYear(normalizedLabel(it.text()).removePrefix("yapim yili ").removePrefix("yil "))
            }

        val score = targetDoc.selectFirst("span.imdb, span.rating, div.score")?.text()?.trim()
        val tags = targetDoc.select("div.cat-tax a, a[href*='/kategori/'], a[href*='/tur/']").map { it.text().trim() }.filter { it.isNotBlank() }

        val episodeElements = targetDoc.select("a[href*='-sezon-']").filter { el ->
            val href = el.attr("href")
            href.contains("-sezon-") && href.contains("-bolum")
        }.distinctBy { it.attr("href") }

        val episodes = if (episodeElements.isNotEmpty()) {
            episodeElements.mapNotNull { el ->
                val epHref = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
                val epText = el.text().trim()

                val sMatch = Regex("""(\d+)\.\s*Sezon""").find(epText)
                    ?: Regex("""(\d+)-sezon""").find(epHref)

                val eMatch = Regex("""(\d+)\.\s*B[öo]l[üu]m""").find(epText)
                    ?: Regex("""(\d+)-bolum""").find(epHref)

                val seasonNum = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val epNum = eMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                newEpisode(epHref) {
                    this.name = "${seasonNum}. Sezon ${epNum}. Bölüm"
                    this.season = seasonNum
                    this.episode = epNum
                }
            }
        } else if (url.contains("-sezon-") && url.contains("-bolum")) {
            val sMatch = Regex("""(\d+)-sezon""").find(url)
            val eMatch = Regex("""(\d+)-bolum""").find(url)
            val seasonNum = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val epNum = eMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            listOf(
                newEpisode(url) {
                    this.name = "${seasonNum}. Sezon ${epNum}. Bölüm"
                    this.season = seasonNum
                    this.episode = epNum
                }
            )
        } else {
            emptyList()
        }

        val cleanTitle = rawTitle.replace(Regex("""\s*\(\d{4}\)$"""), "").trim()

        return newTvSeriesLoadResponse(cleanTitle, targetUrl, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            this.score = Score.from10(score)
        }
    }

    internal fun extractPlayerId(doc: Document): String? {
        val playerIframe = doc.selectFirst("iframe#diziyouPlayer, iframe[src*='/player/']")?.attr("src")
        return if (!playerIframe.isNullOrBlank()) {
            Regex("""/player/([a-zA-Z0-9_-]+)\.html""").find(playerIframe)?.groupValues?.get(1)
        } else {
            Regex("""itemId\s*=\s*['"]([a-zA-Z0-9_-]+)['"]""").find(doc.html())?.groupValues?.get(1)
                ?: Regex("""data-id=['"]([a-zA-Z0-9_-]+)['"]""").find(doc.html())?.groupValues?.get(1)
        }
    }

    internal suspend fun emitNativeLinks(
        itemId: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (itemId.isNullOrBlank()) return false

        val streamUrl = "https://storage.diziyou.one/episodes/${itemId}/play.m3u8"
        callback(
            newExtractorLink(
                source = name,
                name = "$name HLS",
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://www.diziyou.one/"
                this.quality = Qualities.Unknown.value
            }
        )

        subtitleCallback(
            SubtitleFile(
                lang = "Türkçe",
                url = "https://storage.diziyou.one/subtitles/${itemId}/tr.vtt"
            )
        )
        subtitleCallback(
            SubtitleFile(
                lang = "İngilizce",
                url = "https://storage.diziyou.one/subtitles/${itemId}/en.vtt"
            )
        )

        val dubUrl = "https://storage.diziyou.one/episodes/${itemId}_tr/play.m3u8"
        callback(
            newExtractorLink(
                source = name,
                name = "$name HLS (_tr adayı; ses doğrulanmadı)",
                url = dubUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://www.diziyou.one/"
                this.quality = Qualities.Unknown.value
            }
        )

        return true
    }

    internal fun getFallbackIframeCandidates(doc: Document): List<String> {
        return doc.select("iframe").mapNotNull { iframe ->
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
            if (src.isNotBlank() && !src.contains("diziyouPlayer")) src else null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        return deliverPageLinks(doc, data, subtitleCallback, callback) > 0
    }

    /** Standalone delivery boundary; budgets reserve Hub time for document loading and follow-up probes. */
    internal suspend fun deliverPageLinks(
        doc: Document,
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        discoveryBudgetMs: Long = 8_000L,
        preflightBudgetMs: Long = 6_000L,
        resolveFallback: suspend (String, (ExtractorLink) -> Unit) -> Unit = { fixed, emit ->
            loadExtractor(fixed, referer = data, subtitleCallback, emit)
        }
    ): Int = BoundedLinkDelivery.collectAndDeliver(
        discover = { emit ->
            emitNativeLinks(extractPlayerId(doc), subtitleCallback, emit)
            val fallbacks = getFallbackIframeCandidates(doc).mapNotNull { fixUrlNull(it) }.distinct().take(32)
            BoundedParallelResolver.resolveProgressive(
                candidates = fallbacks,
                resolver = resolveFallback,
                onLinkFound = emit
            )
        },
        callback = callback,
        preference = com.cloudstream.tr.core.resolvers.DebridConfig.audioPreference,
        discoveryBudgetMs = discoveryBudgetMs,
        preflightBudgetMs = preflightBudgetMs
    )
}
