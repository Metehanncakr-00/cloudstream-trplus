package com.cloudstream.tr.fullhdfilmizlesene

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.extractors.RapidVidExtractor
import com.cloudstream.tr.core.model.ProviderModels
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

data class AutocompleteItem(
    @JsonProperty("baslik") val baslik: String? = null,
    @JsonProperty("altbaslik") val altbaslik: String? = null,
    @JsonProperty("dizilink") val dizilink: String? = null,
    @JsonProperty("vidresim") val vidresim: String? = null,
    @JsonProperty("imdb") val imdb: String? = null,
    @JsonProperty("yil") val yil: String? = null,
    @JsonProperty("prefix") val prefix: String? = null
)

class FullHDFilmizlesene : MainAPI() {
    override var mainUrl = "https://www.fullhdfilmizlesene.now"
    override var name = "FullHDFilmizlesene"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenen Filmler",
        "${mainUrl}/filmizle/turkce-dublaj-filmler-1" to "Türkçe Dublaj Filmler",
        "${mainUrl}/filmizle/turkce-altyazili-filmler" to "Türkçe Altyazılı Filmler",
        "${mainUrl}/filmizle/1080p-filmler-2" to "1080p Filmler",
        "${mainUrl}/filmizle/aksiyon-filmleri" to "Aksiyon Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            val reqData = request.data.removeSuffix("/")
            if (reqData == mainUrl) "${mainUrl}/" else request.data
        } else {
            val base = request.data.removeSuffix("/")
            if (base == mainUrl) {
                "${mainUrl}/yeni-filmler/${page}"
            } else {
                "${base}/${page}"
            }
        }

        val doc = app.get(targetUrl, headers = mapOf("User-Agent" to userAgent)).document
        val items = doc.select(".list li.film, ul.film-list li, div.film").mapNotNull { el ->
            parseCard(el)
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    fun parseCard(element: Element): SearchResponse? {
        val linkEl = element.selectFirst("a.tt") ?: element.selectFirst("a[href*='/film/']") ?: element.selectFirst("a") ?: return null
        val href = fixUrlNull(linkEl.attr("href")) ?: return null

        val turkishTitle = element.selectFirst("span.film-title")?.text()?.trim()
        val originalTitle = element.selectFirst("span.kt")?.text()?.trim()
        val rawTitle = linkEl.text().trim()

        val title = when {
            !turkishTitle.isNullOrBlank() && !originalTitle.isNullOrBlank() && !turkishTitle.equals(originalTitle, ignoreCase = true) ->
                "$turkishTitle - $originalTitle"
            !turkishTitle.isNullOrBlank() -> turkishTitle
            rawTitle.isNotBlank() -> rawTitle.replace(" izle", "").replace(" İzle", "").trim()
            else -> return null
        }

        val imgEl = element.selectFirst("picture img") ?: element.selectFirst("img")
        val poster = fixUrlNull(
            imgEl?.attr("data-src")?.ifBlank { null }
                ?: imgEl?.attr("src")?.ifBlank { null }
        )

        val year = element.selectFirst("span.film-yil")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
            ?: Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            this.year = year
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val searchUrl = "${mainUrl}/autocomplete/q.php?q=${query.trim().replace(" ", "+")}&callback="
        val response = app.get(
            searchUrl,
            headers = mapOf("User-Agent" to userAgent, "Referer" to "${mainUrl}/")
        ).parsedSafe<List<AutocompleteItem>>() ?: emptyList()

        val results = response.mapNotNull { item ->
            val slug = item.dizilink?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val prefix = item.prefix?.takeIf { it.isNotBlank() } ?: "film"
            val url = fixUrl("/${prefix}/${slug}/")

            val title = when {
                !item.baslik.isNullOrBlank() && !item.altbaslik.isNullOrBlank() -> "${item.baslik} (${item.altbaslik})"
                !item.baslik.isNullOrBlank() -> item.baslik
                else -> return@mapNotNull null
            }

            val poster = fixUrlNull(item.vidresim)
            val year = item.yil?.filter { it.isDigit() }?.take(4)?.toIntOrNull()

            newMovieSearchResponse(title, url, TvType.Movie) {
                this.posterUrl = poster
                this.year = year
            }
        }

        val deduped = ProviderModels.dedupSearchResults(results)
        return newSearchResponseList(deduped, hasNext = false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        return parseLoadMetadata(doc, url)
    }

    suspend fun parseLoadMetadata(doc: Document, url: String): LoadResponse? {
        val mainTitle = doc.selectFirst("h1.film-title, h1")?.text()?.trim()
        val originalTitle = doc.selectFirst("span.film-sub-title, div.detay-orig")?.text()?.trim()

        val title = when {
            !mainTitle.isNullOrBlank() && !originalTitle.isNullOrBlank() && !mainTitle.equals(originalTitle, ignoreCase = true) ->
                "$mainTitle - $originalTitle"
            !mainTitle.isNullOrBlank() -> mainTitle
            else -> doc.selectFirst("meta[property='og:title']")?.attr("content")?.replace(" izle", "")?.trim() ?: return null
        }

        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst("div.film-afis img, img.afis")?.let {
                    if (it.tagName() == "meta") it.attr("content") else it.attr("data-src").ifBlank { it.attr("src") }
                }
        )

        val plot = doc.selectFirst(".ozet-ic, .film-ozeti, div.film-ozet, div.ozet, meta[property='og:description']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }

        val year = doc.selectFirst(".film-info")?.select("li, div, p")?.find {
            it.text().contains("Yapım", ignoreCase = true)
        }?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
            ?: doc.selectFirst("span.film-yil, div.detay-yil")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
            ?: Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()

        val score = doc.selectFirst(".imdb, span.imdb, span.puan")?.text()?.trim()

        val tags = doc.selectFirst(".film-info")?.select("li, div, p")?.find {
            it.text().contains("Tür", ignoreCase = true)
        }?.select("a")?.map { it.text().trim() }
            ?: doc.select("div.film-tur a, a[href*='/kategori/']").map { it.text().trim() }.filter { it.isNotBlank() }

        val cleanTitle = title.replace(" izle", "").replace(" İzle", "").trim()

        return newMovieLoadResponse(cleanTitle, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            this.score = Score.from10(score)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var anyFound = false
        val wrappedCallback: (ExtractorLink) -> Unit = { link ->
            anyFound = true
            callback(link)
        }

        val html = app.get(data, headers = mapOf("User-Agent" to userAgent)).text
        val doc = org.jsoup.Jsoup.parse(html, data)

        val iframes = mutableListOf<String>()

        // 1. Direct iframe tags
        for (iframe in doc.select("iframe")) {
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
            if (src.isNotEmpty() && !src.contains("youtube.com") && !src.contains("youtu.be")) {
                fixUrlNull(src)?.let { iframes.add(it) }
            }
        }

        // 2. Decode scx tokens
        val scxUrls = extractScxIframeUrls(html)
        iframes.addAll(scxUrls)

        val candidates = iframes.distinct()
        val count = BoundedParallelResolver.resolveProgressive(
            candidates = candidates,
            resolver = { candidateUrl, emitLink ->
                if (candidateUrl.contains("rapidvid.org") || candidateUrl.contains("/vx/")) {
                    resolveRapidvid(candidateUrl, subtitleCallback, emitLink)
                } else {
                    loadExtractor(candidateUrl, data, subtitleCallback, emitLink)
                }
            },
            onLinkFound = { link ->
                callback(link)
                anyFound = true
            }
        )

        return anyFound || count > 0
    }

    companion object {
        fun rot13(input: String): String {
            return input.map { ch ->
                when (ch) {
                    in 'a'..'m', in 'A'..'M' -> (ch.code + 13).toChar()
                    in 'n'..'z', in 'N'..'Z' -> (ch.code - 13).toChar()
                    else -> ch
                }
            }.joinToString("")
        }

        fun decodeScxItem(encoded: String): String? {
            return try {
                val rtt = rot13(encoded)
                val padLen = (4 - rtt.length % 4) % 4
                val padded = rtt + "=".repeat(padLen)
                val decoded = com.cloudstream.tr.core.utils.Base64Utils.decode(padded)
                String(decoded, Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        }

        fun extractScxIframeUrls(html: String): List<String> {
            val scxPattern = Pattern.compile("""var\s+scx\s*=\s*(\{.+?\});""", Pattern.DOTALL)
            val matcher = scxPattern.matcher(html)
            if (!matcher.find()) return emptyList()

            val rawJson = matcher.group(1) ?: return emptyList()
            val itemPattern = Pattern.compile(""""([a-zA-Z0-9+/=_-]{10,})"""")
            val itemMatcher = itemPattern.matcher(rawJson)
            val urls = mutableListOf<String>()

            while (itemMatcher.find()) {
                val token = itemMatcher.group(1) ?: continue
                if (token.length < 15) continue
                val decoded = decodeScxItem(token) ?: continue
                if (decoded.startsWith("http://") || decoded.startsWith("https://") || decoded.startsWith("//")) {
                    val fullUrl = if (decoded.startsWith("//")) "https:$decoded" else decoded
                    urls.add(fullUrl)
                }
            }

            return urls.distinct()
        }

        fun decryptRapidvidAv(token: String): String {
            return RapidVidExtractor.decryptRapidvidAv(token)
        }
    }

    internal suspend fun parseRapidvidResponse(
        res: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return RapidVidExtractor.parseHtmlResponse(res, subtitleCallback, callback)
    }

    private suspend fun resolveRapidvid(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val res = app.get(
                url,
                headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer" to "${mainUrl}/"
                )
            ).text
            parseRapidvidResponse(res, subtitleCallback, callback)
        } catch (e: Exception) {
            false
        }
    }
}
