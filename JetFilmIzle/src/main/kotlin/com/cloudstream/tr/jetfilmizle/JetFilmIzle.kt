package com.cloudstream.tr.jetfilmizle

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import com.cloudstream.tr.core.network.StreamValidator
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.net.URI
import com.cloudstream.tr.core.network.SafeHttpClient

class JetFilmIzle : MainAPI() {
    override var mainUrl = "https://jetfilmizle.now"
    override var name = "JetFilmIzle"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenenler",
        "${mainUrl}/tur/aksiyon" to "Aksiyon",
        "${mainUrl}/tur/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/tur/komedi" to "Komedi",
        "${mainUrl}/tur/korku" to "Korku",
        "${mainUrl}/tur/macera" to "Macera",
        "${mainUrl}/tur/dram" to "Dram",
        "${mainUrl}/tur/animasyon" to "Animasyon"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = mainPageUrl(request.data, page)

        val doc = app.get(targetUrl).document
        val items = parseSearchPage(doc)

        return newHomePageResponse(request.name, items, hasNext = hasNextPage(doc, page))
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val doc = app.get(searchUrl(query, page)).document
        val items = parseSearchPage(doc)
        return newSearchResponseList(items, hasNext = hasNextPage(doc, page))
    }

    internal fun mainPageUrl(baseUrl: String, page: Int): String {
        if (page <= 1) return baseUrl
        val base = baseUrl.removeSuffix("/")
        return if (base == mainUrl.removeSuffix("/")) "$mainUrl/filmler/sayfa-$page"
        else "$base/sayfa-$page"
    }

    internal fun searchUrl(query: String, page: Int): String {
        val path = if (page <= 1) "/arama" else "/search/sayfa-$page"
        return "$mainUrl$path?q=${URLEncoder.encode(query, "UTF-8")}"
    }

    internal fun parseSearchPage(doc: Document): List<SearchResponse> =
        ProviderModels.dedupSearchResults(
            doc.select("div.film-card, article.movie, article.post, div.movie-item, div.film-kutusu")
                .mapNotNull(::parseSearchItem)
        )

    internal fun hasNextPage(doc: Document, page: Int): Boolean =
        doc.select("a[href]").any {
            it.attr("href").substringBefore('?').trimEnd('/').endsWith("/sayfa-${page + 1}")
        }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    fun parseSearchItem(element: Element): SearchResponse? {
        val linkEl = element.selectFirst("a[href*='/film/'], a[href]") ?: return null
        val href = fixUrlNull(linkEl.attr("href")) ?: return null
        if (href == mainUrl || href == "${mainUrl}/") return null

        val imgEl = element.selectFirst("img")
        val title = element.selectFirst(".card-title, .title, h2, h3, .entry-title")?.text()?.trim()
            ?: imgEl?.attr("alt")?.trim()
            ?: linkEl.attr("title").trim()
        if (title.isBlank()) return null

        val poster = fixUrlNull(
            imgEl?.attr("data-src")?.ifBlank { null }
                ?: imgEl?.attr("data-lazy-src")?.ifBlank { null }
                ?: imgEl?.attr("src")?.ifBlank { null }
        )

        val year = element.selectFirst(".film-year-footer, .year, .film-yil, .date")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val score = element.selectFirst("small[title*='IMDb'], .imdb, .score, .rating, .puan")?.text()?.trim()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            this.year = year
            this.score = Score.from10(score)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        return parseLoadMetadata(doc, url)
    }

    suspend fun parseLoadMetadata(doc: Document, url: String): LoadResponse? {
        val title = doc.selectFirst("h1.entry-title, h1, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }?.replace(" - JetFilmizle", "")?.replace(" Jet Film izle", "")?.replace(" izle", "")?.trim() ?: return null

        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst(".poster img, .movie-poster img, img.wp-post-image")?.let {
                    it.attr("data-src").ifBlank { null } ?: it.attr("src").ifBlank { null }
                }
        )

        val description = doc.selectFirst("meta[property='og:description'], .entry-content p, .movie-desc, div.ozet")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }

        val year = doc.selectFirst("a[href*='/yil/'], .year, .film-yil")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val tags = doc.select("a[href*='/kategori/'], .categories a, .tags a").map { it.text().trim() }.filter { it.isNotBlank() }
        val score = doc.selectFirst(".imdb, .puan, .score")?.text()?.trim()
        val actors = doc.select("a[href*='/oyuncu/'], .actors a").map { Actor(it.text().trim()) }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.score = Score.from10(score)
            addActors(actors)
        }
    }

    internal fun playerRequests(doc: Document): List<Map<String, String>> {
        val filmId = doc.selectFirst("input[name=film_id]")?.attr("value")
            ?.takeIf { it.toIntOrNull()?.let { id -> id > 0 } == true } ?: return emptyList()
        return doc.select(".player-source-btn[data-source-index][data-player-type]").mapNotNull { button ->
            val index = button.attr("data-source-index").toIntOrNull()?.takeIf { it >= 0 }
                ?: return@mapNotNull null
            val playerType = button.attr("data-player-type").takeIf { it in setOf("dublaj", "altyazi", "genel") }
                ?: return@mapNotNull null
            mapOf("film_id" to filmId, "source_index" to index.toString(), "player_type" to playerType)
        }.distinct()
    }

    internal fun playbackFrames(doc: Document): List<String> = doc.select("iframe").mapNotNull { iframe ->
        if (iframe.id().contains("trailer", ignoreCase = true) ||
            iframe.parents().any { it.id().contains("trailer", ignoreCase = true) }) return@mapNotNull null
        val src = iframe.attr("data-src").ifBlank { iframe.attr("src") }.trim()
        if (src.isBlank() || src.contains("wp-embedded-content") ||
            src.startsWith("about:") || src.startsWith("javascript:") || src.startsWith("data:")) return@mapNotNull null
        val url = fixUrlNull(src) ?: return@mapNotNull null
        val uri = try { URI(url) } catch (_: Exception) { return@mapNotNull null }
        val host = uri.host?.lowercase() ?: return@mapNotNull null
        if (uri.scheme !in setOf("https", "http") || host == "youtu.be" ||
            host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")) return@mapNotNull null
        url
    }.distinct()

    private suspend fun resolvePlayerDocument(
        doc: Document,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        doc.select("video source[src], video[src]").forEach { video ->
            val src = fixUrlNull(video.attr("src")) ?: return@forEach
            val preflight = StreamValidator.validateStream(src, mapOf("Referer" to referer), name)
            if (preflight.isValid) {
                val typeTag = if (preflight.streamType == ExtractorLinkType.M3U8) "HLS" else "MP4"
                callback(newExtractorLink(source = name, name = "$name $typeTag", url = src, type = preflight.streamType) {
                    this.referer = referer
                    this.quality = Qualities.Unknown.value
                })
            }
        }
        playbackFrames(doc).forEach { iframe ->
            if (iframe.contains("playerx.info")) {
                extractPlayerX(iframe, referer, subtitleCallback, callback)
            } else {
                loadExtractor(iframe, referer, subtitleCallback, callback)
            }
        }
    }

    internal suspend fun extractPlayerX(
        playerxUrl: String,
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = app.get(
            playerxUrl,
            headers = mapOf("Referer" to pageUrl, "User-Agent" to SafeHttpClient.DEFAULT_USER_AGENT)
        ).text

        val fileMatch = Regex("""file:\s*["'](watch/[^"']+)["']""").find(html)?.groupValues?.get(1)
            ?: Regex("""file:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)?.groupValues?.get(1)
            ?: return

        val m3u8Url = if (fileMatch.startsWith("http")) fileMatch else "https://playerx.info/$fileMatch"

        // Extract subtitles
        val subRegex = Regex("""file:\s*["'](https?://[^\s"']+/s/[^"']+)["'],\s*label:\s*["']([^"']+)["']""")
        subRegex.findAll(html).forEach { subMatch ->
            val subUrl = subMatch.groupValues[1]
            val subLabel = subMatch.groupValues[2]
            subtitleCallback(newSubtitleFile(subLabel, subUrl))
        }

        callback(newExtractorLink(
            source = name,
            name = "$name PlayerX HLS",
            url = m3u8Url,
            type = ExtractorLinkType.M3U8
        ) {
            this.referer = "https://playerx.info/"
            this.headers = mapOf(
                "Referer" to "https://playerx.info/",
                "User-Agent" to SafeHttpClient.DEFAULT_USER_AGENT
            )
            this.quality = Qualities.P1080.value
        })
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val origin = URI(data).let { "${it.scheme}://${it.authority}" }
        // The null candidate handles pages with inline players; other candidates are the public source tabs.
        val candidates: List<Map<String, String>?> = listOf(null) + playerRequests(doc)
        val resolved = BoundedParallelResolver.resolveProgressive(
            candidates = candidates,
            maxConcurrency = 4,
            provider = name,
            resolver = { form, emitLink ->
                val playerDoc = if (form == null) doc else app.post(
                    "$origin/jetplayer",
                    data = form,
                    headers = mapOf(
                        "Referer" to data,
                        "Origin" to origin,
                        "X-Requested-With" to "XMLHttpRequest",
                        "User-Agent" to SafeHttpClient.DEFAULT_USER_AGENT
                    ),
                    timeout = 10
                ).document
                resolvePlayerDocument(playerDoc, data, subtitleCallback, emitLink)
            },
            onLinkFound = callback
        )
        return resolved > 0
    }
}
