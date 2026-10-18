package com.cloudstream.tr.hdfilmdelisi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

class HDFilmDelisi : MainAPI() {
    override var mainUrl = "https://hdfilmdelisi.one"
    override var name = "HDFilmDelisi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "${mainUrl}" to "Son Eklenen Filmler",
        "${mainUrl}/imdb-top-250" to "IMDb Top 250",
        "${mainUrl}/tur/aksiyon" to "Aksiyon Filmleri",
        "${mainUrl}/tur/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/tur/gerilim" to "Gerilim",
        "${mainUrl}/tur/animasyon" to "Animasyon"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val base = request.data.removeSuffix("/")
            if (base.contains("?")) {
                "${base}&sayfa=${page}"
            } else {
                "${base}?sayfa=${page}"
            }
        }

        val html = app.get(targetUrl, headers = mapOf("User-Agent" to userAgent)).text
        val document = org.jsoup.Jsoup.parse(html, targetUrl)
        val items = parseSearchResults(document, html)
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    fun toSearchResult(element: Element): SearchResponse? {
        val link = if (element.tagName() == "a") element else element.selectFirst("a[href*='/film/']") ?: return null
        val href = fixUrlNull(link.attr("href")) ?: return null
        if (!href.contains("/film/")) return null

        val img = element.selectFirst("img")
        val rawTitle = img?.attr("alt")?.trim()
            ?.ifBlank { null }
            ?: element.selectFirst(".title, h3, h2, h4, span")?.text()?.trim()
            ?: link.text().trim().takeIf { it.isNotBlank() }
            ?: return null

        val cleanTitle = rawTitle.replace("izle", "", ignoreCase = true)
            .replace("Türkçe Dublaj", "", ignoreCase = true)
            .replace("ve Altyazılı", "", ignoreCase = true)
            .trim()

        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null }
                ?: img?.attr("src")?.ifBlank { null }
        )

        return newMovieSearchResponse(cleanTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val targetUrl = if (page <= 1) {
            "${mainUrl}/?s=${query}"
        } else {
            "${mainUrl}/page/${page}/?s=${query}"
        }

        val html = app.get(targetUrl, headers = mapOf("User-Agent" to userAgent)).text
        val document = org.jsoup.Jsoup.parse(html, targetUrl)
        val items = parseSearchResults(document, html)
        return newSearchResponseList(items, hasNext = false)
    }

    fun parseSearchResults(document: Document, rawHtml: String = ""): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()

        // 1. Direct HTML elements
        val elements = document.select("a[href*='/film/']")
        for (el in elements) {
            toSearchResult(el)?.let { results.add(it) }
        }

        // 2. Parse JSON-LD or RSC payload if HTML returned empty (Next.js category pages)
        if (results.isEmpty() && rawHtml.isNotEmpty()) {
            val jsonLdPattern = Pattern.compile(""""@type"\s*:\s*"Movie".+?"url"\s*:\s*"([^"]+)".+?"name"\s*:\s*"([^"]+)"""")
            val m = jsonLdPattern.matcher(rawHtml)
            while (m.find()) {
                val url = m.group(1)?.replace("\\/", "/") ?: continue
                val name = m.group(2)?.trim() ?: continue
                val fixedUrl = fixUrlNull(url) ?: continue
                results.add(
                    newMovieSearchResponse(name, fixedUrl, TvType.Movie)
                )
            }

            if (results.isEmpty()) {
                // RSC slug fallback
                val slugPattern = Pattern.compile(""""/film/([a-zA-Z0-9_-]+)"""")
                val sm = slugPattern.matcher(rawHtml)
                val seen = mutableSetOf<String>()
                while (sm.find()) {
                    val slug = sm.group(1) ?: continue
                    if (seen.add(slug)) {
                        val fixedUrl = "${mainUrl}/film/$slug"
                        val title = slug.replace("-", " ").capitalize()
                        results.add(
                            newMovieSearchResponse(title, fixedUrl, TvType.Movie)
                        )
                    }
                }
            }
        }

        return ProviderModels.dedupSearchResults(results)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        return parseLoadMetadata(document, url)
    }

    suspend fun parseLoadMetadata(document: Document, url: String): LoadResponse? {
        val rawTitle = document.select("h1").firstOrNull { h ->
            val t = h.text().trim()
            !t.equals("Film izle", ignoreCase = true) && !t.equals("Dizi izle", ignoreCase = true)
        }?.text()?.trim()
            ?: document.title().substringBefore("izle").substringBefore("-").trim()

        val title = rawTitle.replace("izle", "", ignoreCase = true)
            .replace("Türkçe Dublaj", "", ignoreCase = true)
            .replace("ve Altyazılı", "", ignoreCase = true)
            .trim()

        val poster = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst("img[src*='poster'], img[src*='cover'], img[src*='upload']")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }
        )

        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst("div.description, div.overview, p")?.text()?.trim()

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    companion object {
        fun decryptVixolity(html: String): String? {
            val match = Regex("""\(\s*['"]([0-9a-fA-F]+)['"]\s*,\s*(\d+)\s*\)""").find(html) ?: return null
            val hex = match.groupValues[1]
            val offset = match.groupValues[2].toIntOrNull() ?: return null
            val chars = StringBuilder()
            for (i in 0 until hex.length step 2) {
                if (i + 2 <= hex.length) {
                    val byteVal = hex.substring(i, i + 2).toInt(16)
                    chars.append(byteVal.toChar())
                }
            }
            val reversed = chars.reverse().toString()
            val decoded = com.cloudstream.tr.core.utils.Base64Utils.decode(reversed) ?: return null
            val shifted = StringBuilder()
            for (b in decoded) {
                val code = (b.toInt() and 0xFF) - offset
                shifted.append(code.toChar())
            }
            val shiftedStr = shifted.toString()
            val fileMatch = Regex("""file\s*:\s*['"]([^'"]+)['"]""").find(shiftedStr)?.groupValues?.get(1) ?: return null
            return Regex("""\\x([0-9a-fA-F]{2})""").replace(fileMatch) { mr ->
                mr.groupValues[1].toInt(16).toChar().toString()
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
        val document = app.get(data, headers = mapOf("User-Agent" to userAgent)).document
        val html = document.html()

        val embedUrls = mutableListOf<String>()

        // 1. Extract real Vixolity player from RSC or page data
        val vixolityRegex = Regex("""https?://pl\.vixolity\.com/[a-zA-Z0-9_\-]+""")
        vixolityRegex.findAll(html).forEach { m ->
            embedUrls.add(m.value)
        }

        // 2. Direct iframes (excluding honeypot /embed/ and google/recaptcha)
        for (it in document.select("iframe[src]")) {
            val src = fixUrlNull(it.attr("src"))
            if (!src.isNullOrBlank() && !src.contains("google") && !src.contains("recaptcha") &&
                !src.contains("hdfilmdelisi.one/embed") && !src.contains("hdfilmdelisi.one/vids")) {
                embedUrls.add(src)
            }
        }

        val candidates = embedUrls.distinct()

        val count = BoundedParallelResolver.resolveProgressive(
            candidates = candidates,
            resolver = { embedUrl, emitLink ->
                try {
                    if (embedUrl.contains("vixolity.com")) {
                        val vixHtml = app.get(
                            embedUrl,
                            headers = mapOf(
                                "User-Agent" to userAgent,
                                "Referer" to data
                            )
                        ).text
                        val m3u8Url = decryptVixolity(vixHtml)
                        if (!m3u8Url.isNullOrBlank()) {
                            emitLink(
                                newExtractorLink(
                                    source = name,
                                    name = "$name Vixolity HLS",
                                    url = m3u8Url,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = "https://pl.vixolity.com/"
                                    this.headers = mapOf(
                                        "Referer" to "https://pl.vixolity.com/",
                                        "User-Agent" to userAgent
                                    )
                                    this.quality = Qualities.P1080.value
                                }
                            )
                        }
                    } else {
                        loadExtractor(embedUrl, data, subtitleCallback, emitLink)
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



