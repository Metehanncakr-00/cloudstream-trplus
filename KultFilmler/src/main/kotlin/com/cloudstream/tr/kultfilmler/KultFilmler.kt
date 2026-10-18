package com.cloudstream.tr.kultfilmler

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.diagnostics.DiagnosticCategory
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.diagnostics.DiagnosticStage
import com.cloudstream.tr.core.model.ProviderModels
import com.cloudstream.tr.core.network.StreamValidator
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class KultFilmler : MainAPI() {
    override var mainUrl = "https://kultfilmler.net"
    override var name = "KultFilmler"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenenler",
        "${mainUrl}/film-arsivi/" to "Film Arşivi",
        "${mainUrl}/dizi-kategori/mini-dizi-izle/" to "Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val sep = if (request.data.contains("?")) "&" else "?"
            "${request.data}${sep}sayfa=${page}"
        }

        val doc = app.get(targetUrl).document
        val items = doc.select("a.mcard, a.dcard").mapNotNull { el ->
            parseSearchElement(el)
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val targetUrl = if (page <= 1) {
            "${mainUrl}/?s=${query}"
        } else {
            "${mainUrl}/?s=${query}&sayfa=${page}"
        }

        val doc = app.get(targetUrl).document
        val items = doc.select("a.mcard, a.dcard, article, div.item").mapNotNull { el ->
            parseSearchElement(el)
        }
        val deduped = ProviderModels.dedupSearchResults(items)
        return newSearchResponseList(deduped, hasNext = deduped.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    fun parseSearchElement(element: Element): SearchResponse? {
        val linkEl = if (element.tagName() == "a") element else element.selectFirst("a") ?: return null
        val href = fixUrlNull(linkEl.attr("href")) ?: return null

        val imgEl = element.selectFirst("img")
        val title = imgEl?.attr("alt")?.ifBlank { null }
            ?: element.selectFirst("h2, h3, .title, .entry-title")?.text()?.trim()
            ?: linkEl.attr("title").ifBlank { null }
            ?: return null

        val poster = fixUrlNull(
            imgEl?.attr("data-src")?.ifBlank { null }
                ?: imgEl?.attr("data-lazy-src")?.ifBlank { null }
                ?: imgEl?.attr("src")?.ifBlank { null }
        )

        val year = element.selectFirst(".year, .release-date, span.C a, span.date")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()

        val isTv = href.contains("/dizi/") || element.hasClass("dcard")
        val type = if (isTv) TvType.TvSeries else TvType.Movie

        return if (isTv) {
            newTvSeriesSearchResponse(title.trim(), href, type) {
                this.posterUrl = poster
                this.year = year
            }
        } else {
            newMovieSearchResponse(title.trim(), href, type) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        return parseLoadMetadata(doc, url)
    }

    suspend fun parseLoadMetadata(doc: Document, url: String): LoadResponse? {
        val title = doc.selectFirst("h1, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }?.replace(" - Kült Filmler", "")?.replace(" İzle", "")?.trim() ?: return null

        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst("div.poster img, img.pimg")?.let {
                    it.attr("data-src").ifBlank { null } ?: it.attr("src").ifBlank { null }
                }
        )
        val description = doc.selectFirst("meta[property='og:description'], div.wp-content p, .overview p")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }
        val year = doc.selectFirst("a[href*='/yil/'], span.date, span.year")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val score = doc.selectFirst("span.rating, .score, span.imdb")?.text()?.trim()
        val tags = doc.select("a[href*='/tur/'], a[href*='/kategori/']").map { it.text().trim() }.filter { it.isNotBlank() }

        val episodeElements = doc.select("a[href*='/bolum/']").filter { el ->
            val text = el.text()
            text.contains("Sezon", ignoreCase = true) || text.contains("Bölüm", ignoreCase = true)
        }.distinctBy { it.attr("href") }

        val isTvSeries = url.contains("/dizi/") || episodeElements.isNotEmpty()

        return if (isTvSeries) {
            val episodes = episodeElements.mapIndexedNotNull { index, el ->
                val epHref = fixUrlNull(el.attr("href")) ?: return@mapIndexedNotNull null
                val epText = el.text().trim()

                val sMatch = Regex("""(\d+)\.\s*Sezon""").find(epText)
                val eMatch = Regex("""(\d+)\.\s*B[öo]l[üu]m""").find(epText)

                val seasonNum = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val epNum = eMatch?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                val epTitle = "${seasonNum}. Sezon ${epNum}. Bölüm"

                newEpisode(epHref) {
                    this.name = epTitle
                    this.season = seasonNum
                    this.episode = epNum
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(score)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(score)
            }
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    data class VidpapiResponse(
        @JsonProperty("videoSource") val videoSource: String? = null,
        @JsonProperty("securedLink") val securedLink: String? = null,
        @JsonProperty("hls") val hls: Boolean? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        var linksFound = false

        // 1. Collect potential iframe embed sources
        val iframes = mutableListOf<String>()
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
            if (src.isNotBlank() && !src.contains("youtube.com") && !src.contains("youtu.be") && !src.contains("wp-embedded-content")) {
                fixUrlNull(src)?.let { iframes.add(it) }
            }
        }

        // Also inspect JSON-embedded iframes in script blocks
        val scriptContent = doc.select("script").map { it.data() }.joinToString("\n")
        Regex("""https?:\\?/\\?/[^"'\s<>]+vidpapi\.xyz[^"'\s<>]*""").findAll(scriptContent).forEach { match ->
            val clean = match.value.replace("""\/""", "/")
            fixUrlNull(clean)?.let { iframes.add(it) }
        }

        val candidates = iframes.distinct()
        val count = BoundedParallelResolver.resolveProgressive(
            candidates = candidates,
            resolver = { iframeUrl, emitLink ->
                if (iframeUrl.contains("vidpapi.xyz")) {
                    try {
                        val vidpapiDoc = app.get(iframeUrl, referer = mainUrl).text

                        // Subtitle discovery from playerjsSubtitle
                        Regex("""playerjsSubtitle\s*=\s*["']([^"']+)["']""").find(vidpapiDoc)?.let { match ->
                            val rawSub = match.groupValues[1]
                            val subLang = Regex("""\[(.*?)\]""").find(rawSub)?.groupValues?.get(1) ?: "Türkçe"
                            val subUrl = rawSub.replace(Regex("""\[.*?\]"""), "").trim()
                            if (subUrl.startsWith("http")) {
                                subtitleCallback(
                                    SubtitleFile(
                                        lang = subLang,
                                        url = subUrl
                                    )
                                )
                            }
                        }

                        // Request direct video stream via vidpapi getVideo API
                        val dataId = iframeUrl.substringAfter("/video/").substringBefore("/").substringBefore("?")
                        if (dataId.isNotBlank()) {
                            val hash = Regex("""(?:hash|FirePlayer)\s*[:=\(]\s*["']([^"']+)["']""").find(vidpapiDoc)?.groupValues?.get(1) ?: dataId
                            val apiUrl = "https://vidpapi.xyz/player/index.php?data=${dataId}&do=getVideo"
                            val apiResp = app.post(
                                apiUrl,
                                data = mapOf(
                                    "hash" to hash,
                                    "r" to data,
                                    "s" to ""
                                ),
                                headers = mapOf(
                                    "Referer" to iframeUrl,
                                    "Origin" to "https://vidpapi.xyz",
                                    "X-Requested-With" to "XMLHttpRequest"
                                )
                            ).parsedSafe<VidpapiResponse>()

                            val candidateList = listOfNotNull(
                                apiResp?.securedLink?.ifBlank { null },
                                apiResp?.videoSource?.ifBlank { null }
                            )

                            for (streamCandidate in candidateList) {
                                val preflight = StreamValidator.validateStream(
                                    url = streamCandidate,
                                    headers = mapOf("Referer" to iframeUrl),
                                    provider = name
                                )
                                when (preflight.status) {
                                    com.cloudstream.tr.core.network.ValidationStatus.VALID -> {
                                        val typeTag = if (preflight.streamType == ExtractorLinkType.M3U8) "HLS" else "MP4"
                                        emitLink(
                                            newExtractorLink(
                                                source = name,
                                                name = "$name $typeTag",
                                                url = streamCandidate,
                                                type = preflight.streamType
                                            ) {
                                                this.referer = iframeUrl
                                                this.quality = Qualities.Unknown.value
                                            }
                                        )
                                        break
                                    }
                                    com.cloudstream.tr.core.network.ValidationStatus.INDETERMINATE -> {
                                        DiagnosticLogger.log(
                                            provider = name,
                                            stage = DiagnosticStage.STREAM_PREFLIGHT,
                                            category = DiagnosticCategory.NETWORK,
                                            message = "Indeterminate preflight for $streamCandidate (${preflight.failureReason})"
                                        )
                                    }
                                    com.cloudstream.tr.core.network.ValidationStatus.INVALID -> {
                                        DiagnosticLogger.log(
                                            provider = name,
                                            stage = DiagnosticStage.STREAM_PREFLIGHT,
                                            category = DiagnosticCategory.NETWORK,
                                            message = "Rejected invalid stream $streamCandidate (${preflight.failureReason})"
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        DiagnosticLogger.log(
                            provider = name,
                            stage = DiagnosticStage.EXTRACTOR,
                            category = DiagnosticCategory.EXTRACTOR,
                            message = "Vidpapi resolution failed: ${e.message}",
                            throwable = e
                        )
                    }
                } else {
                    loadExtractor(iframeUrl, referer = mainUrl, subtitleCallback, emitLink)
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
