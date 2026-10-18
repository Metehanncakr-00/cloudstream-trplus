package com.cloudstream.tr.filmmodu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.diagnostics.DiagnosticCategory
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.diagnostics.DiagnosticStage
import com.cloudstream.tr.core.model.ProviderModels
import com.cloudstream.tr.core.network.StreamValidator
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class FilmModu : MainAPI() {
    override var mainUrl = "https://www.filmmodu.one"
    override var name = "FilmModu"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Filmler",
        "${mainUrl}/turkce-dublaj-hd-film-izle" to "Türkçe Dublaj",
        "${mainUrl}/turkce-altyazili-hd-filmler-izle" to "Türkçe Altyazılı",
        "${mainUrl}/film-tur/aksiyon-filmleri-izle" to "Aksiyon",
        "${mainUrl}/film-tur/komedi-filmleri-izle" to "Komedi",
        "${mainUrl}/film-tur/korku-filmleri-izle" to "Korku",
        "${mainUrl}/film-tur/bilim-kurgu-filmleri-izle" to "Bilim Kurgu"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val base = request.data.removeSuffix("/")
            "$base?page=$page"
        }

        val doc = app.get(targetUrl).document
        val items = doc.select(".movie, .film, article, .movie-item, div.col-movie").mapNotNull { el ->
            parseSearchItem(el)
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val targetUrl = if (page <= 1) {
            "${mainUrl}/ara?q=${query}"
        } else {
            "${mainUrl}/ara?q=${query}&page=$page"
        }

        val doc = app.get(targetUrl).document
        val items = doc.select(".movie, .film, article, .movie-item, div.col-movie").mapNotNull { el ->
            parseSearchItem(el)
        }
        val deduped = ProviderModels.dedupSearchResults(items)

        return newSearchResponseList(deduped, hasNext = deduped.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    fun parseSearchItem(element: Element): SearchResponse? {
        val linkEl = element.selectFirst("a[href*='/film/'], a[href]") ?: return null
        val href = fixUrlNull(linkEl.attr("href")) ?: return null
        if (href == mainUrl || href == "${mainUrl}/") return null

        val imgEl = element.selectFirst("img")
        val title = element.selectFirst(".turkish-name, .original-name, .title, h2, h3, .movie-title")?.text()?.trim()
            ?: imgEl?.attr("alt")?.trim()
            ?: linkEl.attr("title").trim()
        if (title.isBlank()) return null

        val poster = fixUrlNull(
            imgEl?.attr("data-src")?.ifBlank { null }
                ?: imgEl?.attr("data-lazy-src")?.ifBlank { null }
                ?: imgEl?.attr("src")?.ifBlank { null }
        )

        val year = element.selectFirst(".year, .film-yil, .date")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val score = element.selectFirst(".imdb, .score, .rating")?.text()?.trim()

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
        val title = doc.selectFirst("h1.title, h1.movie-title, h1, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }?.replace(" - Filmmodu", "")?.replace(" Film Modu", "")?.replace(" izle", "")?.trim() ?: return null

        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst(".poster img, .movie-poster img, img.cover")?.let {
                    it.attr("data-src").ifBlank { null } ?: it.attr("src").ifBlank { null }
                }
        )

        val description = doc.selectFirst("meta[property='og:description'], .description, .movie-desc, .story")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }

        val year = doc.selectFirst("a[href*='/yil/'], .year, .date")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val tags = doc.select("a[href*='/tur/'], .genre a, .tags a").map { it.text().trim() }.filter { it.isNotBlank() }
        val score = doc.selectFirst(".imdb-score, .score, .rating")?.text()?.trim()
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

    data class FilmModuSource(
        val type: String? = null,
        val src: String? = null,
        val label: String? = null,
        val res: String? = null
    )

    data class FilmModuSourceResponse(
        val sources: List<FilmModuSource>? = null,
        val subtitle: String? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var linksFound = false
        val doc = app.get(data).document

        // 1. FilmModu Native /get-source API (Extracts genuine HLS streams and subtitles)
        val scriptText = doc.select("script").joinToString("\n") { it.data() }
        val defaultVideoId = Regex("""var\s+videoId\s*=\s*['"](\d+)['"]""").find(scriptText)?.groupValues?.get(1)
        val defaultVideoType = Regex("""var\s+videoType\s*=\s*['"]([^'"]*)['"]""").find(scriptText)?.groupValues?.get(1) ?: ""

        // Collect audio/subtitle versions if available (e.g. altyazili or dublaj links)
        val pagesToProbe = mutableListOf(data)
        doc.select("a[href*='-film-izle']").forEach { a ->
            val href = fixUrlNull(a.attr("href")) ?: return@forEach
            if (!href.contains("uyelik") && !href.contains("kategori") && href != data) {
                pagesToProbe.add(href)
            }
        }

        for (targetPage in pagesToProbe.distinct().take(3)) {
            val pageDoc = if (targetPage == data) doc else app.get(targetPage).document
            val pageScript = pageDoc.select("script").joinToString("\n") { it.data() }
            val vId = Regex("""var\s+videoId\s*=\s*['"](\d+)['"]""").find(pageScript)?.groupValues?.get(1) ?: defaultVideoId
            val vType = Regex("""var\s+videoType\s*=\s*['"]([^'"]*)['"]""").find(pageScript)?.groupValues?.get(1) ?: defaultVideoType

            if (!vId.isNullOrBlank()) {
                try {
                    val apiResp = app.get(
                        url = "${mainUrl}/get-source?movie_id=${vId}&type=${vType}",
                        headers = mapOf(
                            "Referer" to targetPage,
                            "X-Requested-With" to "XMLHttpRequest"
                        )
                    ).parsedSafe<FilmModuSourceResponse>()

                    val langTag = if (vType.contains("en")) "Altyazılı" else if (vType.contains("tr")) "Dublaj" else ""

                    apiResp?.sources?.forEach { s ->
                        val streamUrl = s.src?.ifBlank { null } ?: return@forEach
                        val qualStr = s.res ?: s.label ?: ""
                        val quality = qualStr.filter { it.isDigit() }.toIntOrNull() ?: Qualities.Unknown.value

                        val preflight = StreamValidator.validateStream(
                            url = streamUrl,
                            headers = mapOf("Referer" to "${mainUrl}/"),
                            provider = name
                        )
                        if (preflight.isValid) {
                            val qualityLabel = if (quality == Qualities.Unknown.value) (s.label ?: "HD") else "${quality}p"
                            callback(
                                newExtractorLink(
                                    source = name,
                                    name = "$name $langTag $qualityLabel".trim(),
                                    url = streamUrl,
                                    type = preflight.streamType
                                ) {
                                    this.referer = "${mainUrl}/"
                                    this.quality = quality
                                }
                            )
                            linksFound = true
                        }
                    }

                    apiResp?.subtitle?.ifBlank { null }?.let { sub ->
                        val subUrl = fixUrl(sub)
                        subtitleCallback(SubtitleFile("Türkçe", subUrl))
                    }
                } catch (e: Exception) {
                    DiagnosticLogger.log(
                        provider = name,
                        stage = DiagnosticStage.EXTRACTOR,
                        category = DiagnosticCategory.EXTRACTOR,
                        message = "FilmModu get-source failed: ${e.message}",
                        throwable = e
                    )
                }
            }
        }

        // 2. Check embedded iframes, EXCLUDING YouTube trailers
        val iframes = mutableListOf<String>()
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
            if (src.isNotBlank() && !src.contains("wp-embedded-content") && !src.contains("youtube.com") && !src.contains("youtu.be")) {
                fixUrlNull(src)?.let { iframes.add(it) }
            }
        }

        val resolved = BoundedParallelResolver.resolveProgressive(
            candidates = iframes.distinct(),
            maxConcurrency = 4,
            resolver = { iframeUrl, emitLink ->
                val fixed = fixUrl(iframeUrl)
                loadExtractor(fixed, referer = mainUrl, subtitleCallback, emitLink)
            },
            onLinkFound = { link ->
                callback(link)
                linksFound = true
            }
        )

        return linksFound || resolved > 0
    }
}
