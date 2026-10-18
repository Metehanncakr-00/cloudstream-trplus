package com.cloudstream.tr.dizikorea

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.model.ProviderModels
import com.cloudstream.tr.core.network.SafeHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class DiziKorea : MainAPI() {
    override var mainUrl = "https://dizikorea3.com"
    override var name = "DiziKorea"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenenler",
        "${mainUrl}/kore-dizileri" to "Kore Dizileri",
        "${mainUrl}/cin-dizileri" to "Çin Dizileri",
        "${mainUrl}/tayland-dizileri" to "Tayland Dizileri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val base = request.data.removeSuffix("/")
            if (base.contains("?")) "${base}&page=${page}" else "${base}?page=${page}"
        }

        val document = app.get(targetUrl, headers = mapOf("User-Agent" to userAgent)).document
        val items = document.select("a.poster-card, div.content-grid a[href*='/dizi/'], div.grid a[href*='/dizi/']").mapNotNull { toSearchResult(it) }
        val deduped = ProviderModels.dedupSearchResults(items)

        return newHomePageResponse(request.name, deduped, hasNext = deduped.isNotEmpty())
    }

    fun toSearchResult(element: Element): SearchResponse? {
        val link = if (element.tagName() == "a") element else element.selectFirst("a[href*='/dizi/']") ?: return null
        var href = fixUrlNull(link.attr("href")) ?: return null
        
        if (href.contains("/sezon-")) {
            href = href.substringBefore("/sezon-")
        }

        val img = element.selectFirst("img")
        val title = img?.attr("alt")?.trim()
            ?.ifBlank { null }
            ?: element.selectFirst(".poster-card-title, h3, h2, .title")?.text()?.trim()
            ?: link.text().trim().takeIf { it.isNotBlank() }
            ?: return null

        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null }
                ?: img?.attr("src")?.ifBlank { null }
        )

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    internal fun buildSearchUrl(query: String, page: Int): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return if (page <= 1) {
            "${mainUrl}/arama?q=${encodedQuery}"
        } else {
            "${mainUrl}/arama?q=${encodedQuery}&page=${page}"
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = app.get(buildSearchUrl(query, page), headers = mapOf("User-Agent" to userAgent)).document
        val items = parseSearchResults(document)
        return newSearchResponseList(items, hasNext = false)
    }

    fun parseSearchResults(document: Document): List<SearchResponse> {
        val elements = document.select("a.poster-card, div.content-grid a[href*='/dizi/'], div.grid a[href*='/dizi/']")
        val results = elements.mapNotNull { toSearchResult(it) }
        return ProviderModels.dedupSearchResults(results)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        return parseLoadMetadata(document, url)
    }

    suspend fun parseLoadMetadata(document: Document, url: String): LoadResponse? {
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(
            document.selectFirst("div.series-profile-poster img, div.series-header img, img[src*='series/']")?.let {
                it.attr("data-src").ifBlank { null } ?: it.attr("src").ifBlank { null }
            }
        )
        val description = document.selectFirst("p.series-about-text, div.series-about-body p, div.description")?.text()?.trim()

        val episodeElements = document.select("a[href*='/sezon-']")
        val episodes = mutableListOf<Episode>()

        val epRegex = Regex("""/sezon-(\d+)/bolum-(\d+)""")
        val seenUrls = mutableSetOf<String>()

        for (el in episodeElements) {
            val href = fixUrlNull(el.attr("href")) ?: continue
            if (!seenUrls.add(href)) continue

            val match = epRegex.find(href)
            val seasonNum = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val epNum = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1

            val epName = el.text().trim().takeIf { it.isNotBlank() } ?: "$seasonNum. Sezon $epNum. Bölüm"

            episodes.add(
                newEpisode(href) {
                    this.name = epName
                    this.season = seasonNum
                    this.episode = epNum
                }
            )
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    private suspend fun resolvePlayerDkorea(
        playerUrl: String,
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val videoId = Regex("""/video/([a-zA-Z0-9_-]+)""").find(playerUrl)?.groupValues?.get(1) ?: return false
            val host = Regex("""https?://[^/]+""").find(playerUrl)?.value ?: "https://playerdkorea.xyz"
            val apiUrl = "${host}/player/index.php?data=${videoId}&do=getVideo"

            val apiResp = app.post(
                apiUrl,
                data = mapOf(
                    "hash" to videoId,
                    "r" to pageUrl,
                    "s" to ""
                ),
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to playerUrl,
                    "User-Agent" to userAgent
                )
            ).text

            val streamMatch = Regex(""""securedLink"\s*:\s*"([^"]+)"""").find(apiResp)
                ?: Regex(""""videoSource"\s*:\s*"([^"]+)"""").find(apiResp)
                ?: Regex("""https?://[^"'\s<>]+\.m3u8[^"'\s<>]*""").find(apiResp)

            val streamUrl = streamMatch?.groupValues?.getOrNull(1)?.replace("\\/", "/")
                ?: streamMatch?.value?.replace("\\/", "/")

            // Extract subtitles from player API response
            val subRegex = Regex("""["']?(?:file|src)["']?\s*:\s*["']([^"']+\.vtt[^"']*)["'](?:\s*,\s*["']?label["']?\s*:\s*["']([^"']+)["'])?""")
            subRegex.findAll(apiResp).forEach { m ->
                val subUrl = m.groupValues[1]
                val label = m.groupValues.getOrNull(2)?.ifBlank { null } ?: "Türkçe"
                subtitleCallback(newSubtitleFile(label, fixUrl(subUrl)))
            }

            if (!streamUrl.isNullOrBlank()) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name HLS",
                        url = streamUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "${host}/"
                        this.headers = mapOf(
                            "Referer" to "${host}/",
                            "User-Agent" to userAgent
                        )
                        this.quality = Qualities.P1080.value
                    }
                )
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = mapOf("User-Agent" to userAgent)).document
        val iframes = mutableListOf<String>()

        // Page level tracks
        document.select("track[src]").forEach { track ->
            val src = fixUrlNull(track.attr("src")) ?: return@forEach
            val label = track.attr("label").ifBlank { track.attr("srclang") }.ifBlank { "Türkçe" }
            subtitleCallback(newSubtitleFile(label, src))
        }

        for (it in document.select("iframe[data-src], iframe[src], [data-src], [data-embed]")) {
            val src = fixUrlNull(
                it.attr("data-src").ifBlank {
                    it.attr("src").ifBlank {
                        it.attr("data-embed")
                    }
                }
            )
            if (!src.isNullOrBlank() && !src.contains("google") && !src.contains("recaptcha")) {
                iframes.add(src)
            }
        }

        var found = false
        val candidates = iframes.distinct()

        val count = BoundedParallelResolver.resolveProgressive(
            candidates = candidates,
            resolver = { iframeUrl, emitLink ->
                if (iframeUrl.contains("playerdkorea") || iframeUrl.contains("/video/")) {
                    if (resolvePlayerDkorea(iframeUrl, data, subtitleCallback, emitLink)) {
                        found = true
                    }
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
