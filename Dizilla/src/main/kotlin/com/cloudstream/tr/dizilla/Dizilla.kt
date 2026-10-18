package com.cloudstream.tr.dizilla

import com.cloudstream.tr.core.concurrency.BoundedParallelResolver
import com.cloudstream.tr.core.extractors.PichiveExtractor
import com.cloudstream.tr.core.model.ProviderModels
import com.cloudstream.tr.core.network.StreamValidator
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Dizilla : MainAPI() {
    override var mainUrl = "https://dizilla.now"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    companion object {
        private const val AES_KEY = "9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI"

        fun decryptSecureData(base64Cipher: String): String {
            val key = AES_KEY.toByteArray(Charsets.UTF_8)
            val iv = ByteArray(16)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val decoded = com.cloudstream.tr.core.utils.Base64Utils.decode(base64Cipher.trim())
            return String(cipher.doFinal(decoded), Charsets.UTF_8)
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenen Bölümler",
        "${mainUrl}/arsiv" to "Dizi Arşivi",
        "${mainUrl}/yabanci-dizi-izle" to "Yabancı Diziler",
        "${mainUrl}/anime-izle" to "Anime",
        "${mainUrl}/kdrama-izle" to "Kdrama",
        "${mainUrl}/imdb-top-100" to "Top 100 Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            val sep = if (request.data.contains("?")) "&" else "?"
            "${request.data}${sep}page=${page}"
        }

        val doc = app.get(targetUrl).document
        val items = mutableListOf<SearchResponse>()

        val nextData = doc.selectFirst("script#__NEXT_DATA__")?.data()
        if (!nextData.isNullOrBlank()) {
            try {
                val enc = Regex(""""secureData"\s*:\s*"([^"]+)"""").find(nextData)?.groupValues?.get(1)
                if (!enc.isNullOrBlank()) {
                    val dec = decryptSecureData(enc)
                    val episodeMatches = Regex(""""series_title"\s*:\s*"([^"]+)".*?"series_slug"\s*:\s*"([^"]+)".*?"poster_url"\s*:\s*"([^"]+)"""").findAll(dec)
                    for (m in episodeMatches) {
                        val title = m.groupValues[1]
                        val slug = m.groupValues[2]
                        val poster = m.groupValues[3].replace("\\/", "/")
                        val href = if (slug.startsWith("http")) slug else "${mainUrl}/${slug.removePrefix("/")}"
                        items.add(
                            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                                this.posterUrl = poster
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                // fallback to HTML
            }
        }

        if (items.isEmpty()) {
            for (el in doc.select("div.serie-card, div.episode-card, a[href*='/dizi/']")) {
                val link = if (el.tagName() == "a") el else el.selectFirst("a") ?: continue
                val href = fixUrlNull(link.attr("href")) ?: continue
                val title = el.selectFirst(".title, h2, h3")?.text()?.ifBlank { null }
                    ?: link.attr("title").ifBlank { null }
                    ?: continue
                val poster = fixUrlNull(el.selectFirst("img")?.attr("src") ?: el.selectFirst("img")?.attr("data-src"))
                items.add(
                    newTvSeriesSearchResponse(title.trim(), href, TvType.TvSeries) {
                        this.posterUrl = poster
                    }
                )
            }
        }

        val distinctItems = items.distinctBy { it.url }
        return newHomePageResponse(request.name, distinctItems, hasNext = distinctItems.isNotEmpty())
    }

    data class SearchResultItem(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("poster_url") val posterUrl: String? = null,
        @JsonProperty("face_url") val faceUrl: String? = null
    )

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val searchUrl = "${mainUrl}/api/bg/searchContent?searchterm=${URLEncoder.encode(query, "UTF-8")}"
        val items = mutableListOf<SearchResponse>()

        try {
            val respText = app.post(
                searchUrl,
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to "${mainUrl}/"
                )
            ).text

            val cipher = Regex(""""response"\s*:\s*"([^"]+)"""").find(respText)?.groupValues?.get(1)
            val jsonText = if (!cipher.isNullOrBlank()) {
                decryptSecureData(cipher)
            } else if (respText.trim().startsWith("\"") && !respText.contains("{")) {
                decryptSecureData(respText.trim().removeSurrounding("\""))
            } else {
                respText
            }

            val parsedList = AppUtils.tryParseJson<List<SearchResultItem>>(jsonText)
            for (item in parsedList.orEmpty()) {
                val title = item.name?.ifBlank { null } ?: item.title ?: continue
                val slug = item.slug ?: continue
                val href = if (slug.startsWith("http")) slug else "${mainUrl}/${slug.removePrefix("/")}"
                val poster = item.posterUrl ?: item.faceUrl

                items.add(
                    newTvSeriesSearchResponse(title.trim(), href, TvType.TvSeries) {
                        this.posterUrl = fixUrlNull(poster)
                    }
                )
            }
        } catch (e: Exception) {
            try {
                val doc = app.get("${mainUrl}/arama?q=${URLEncoder.encode(query, "UTF-8")}").document
                for (el in doc.select("div.serie-card, a[href*='/dizi/']")) {
                    val a = if (el.tagName() == "a") el else el.selectFirst("a") ?: continue
                    val href = fixUrlNull(a.attr("href")) ?: continue
                    val title = a.attr("title").ifBlank { el.selectFirst("h2, h3")?.text() } ?: continue
                    val poster = fixUrlNull(el.selectFirst("img")?.attr("src"))
                    items.add(
                        newTvSeriesSearchResponse(title.trim(), href, TvType.TvSeries) {
                            this.posterUrl = poster
                        }
                    )
                }
            } catch (ignored: Exception) {}
        }

        val deduped = ProviderModels.dedupSearchResults(items)
        return newSearchResponseList(deduped, hasNext = false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        return parseLoadMetadata(doc, url)
    }

    suspend fun parseLoadMetadata(doc: Document, url: String): LoadResponse? {
        val nextData = doc.selectFirst("script#__NEXT_DATA__")?.data()
        var decryptedJson: String? = null

        if (!nextData.isNullOrBlank()) {
            val enc = Regex(""""secureData"\s*:\s*"([^"]+)"""").find(nextData)?.groupValues?.get(1)
            if (!enc.isNullOrBlank()) {
                try {
                    decryptedJson = decryptSecureData(enc)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        val episodes = mutableListOf<Episode>()

        if (decryptedJson != null) {
            val epMatches = Regex(""""episode_no"\s*:\s*(\d+).*?"season_no"\s*:\s*(\d+).*?"episode_slug"\s*:\s*"([^"]+)"""").findAll(decryptedJson)
            for (m in epMatches) {
                val epNum = m.groupValues[1].toIntOrNull() ?: 1
                val seasonNum = m.groupValues[2].toIntOrNull() ?: 1
                val epSlug = m.groupValues[3].removePrefix("/")
                val epHref = "${mainUrl}/${epSlug}"

                episodes.add(
                    newEpisode(epHref) {
                        this.name = "${seasonNum}. Sezon ${epNum}. Bölüm"
                        this.season = seasonNum
                        this.episode = epNum
                    }
                )
            }
        }

        if (episodes.isEmpty()) {
            for (a in doc.select("a[href*='-sezon-']")) {
                val epHref = fixUrlNull(a.attr("href")) ?: continue
                val epText = a.text().trim()
                val sMatch = Regex("""(\d+)\.\s*Sezon""").find(epText) ?: Regex("""(\d+)-sezon""").find(epHref)
                val eMatch = Regex("""(\d+)\.\s*B[öo]l[üu]m""").find(epText) ?: Regex("""(\d+)-bolum""").find(epHref)
                val seasonNum = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val epNum = eMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                episodes.add(
                    newEpisode(epHref) {
                        this.name = "${seasonNum}. Sezon ${epNum}. Bölüm"
                        this.season = seasonNum
                        this.episode = epNum
                    }
                )
            }
        }

        val rawTitle = doc.selectFirst("h1, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }?.replace(" - Dizilla", "")?.replace(" İzle", "")?.trim()
            ?: Regex(""""series_title"\s*:\s*"([^"]+)"""").find(decryptedJson ?: "")?.groupValues?.get(1)
            ?: return null

        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: Regex(""""poster_url"\s*:\s*"([^"]+)"""").find(decryptedJson ?: "")?.groupValues?.get(1)?.replace("\\/", "/")
        )

        val plot = doc.selectFirst("meta[property='og:description']")?.attr("content")
            ?: Regex(""""series_description"\s*:\s*"([^"]+)"""").find(decryptedJson ?: "")?.groupValues?.get(1)

        val year = doc.selectFirst("span.year, .date")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
            ?: Regex(""""release_date"\s*:\s*"(\d{4})""").find(decryptedJson ?: "")?.groupValues?.get(1)?.toIntOrNull()

        val score = doc.selectFirst("span.imdb, .score")?.text()?.trim()
            ?: Regex(""""imdb_point"\s*:\s*([0-9.]+)""").find(decryptedJson ?: "")?.groupValues?.get(1)

        return newTvSeriesLoadResponse(rawTitle, url, TvType.TvSeries, episodes.distinctBy { it.data }) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.score = Score.from10(score)
        }
    }

    data class PichiveSource(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("title") val title: String? = null
    )

    data class PichivePlaylist(
        @JsonProperty("sources") val sources: List<PichiveSource>? = null
    )

    data class PichiveResponse(
        @JsonProperty("state") val state: Boolean? = null,
        @JsonProperty("playlist") val playlist: List<PichivePlaylist>? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var linksFound = false
        val doc = app.get(data).document

        val nextData = doc.selectFirst("script#__NEXT_DATA__")?.data()
        val iframes = mutableListOf<String>()

        if (!nextData.isNullOrBlank()) {
            val enc = Regex(""""secureData"\s*:\s*"([^"]+)"""").find(nextData)?.groupValues?.get(1)
            if (!enc.isNullOrBlank()) {
                try {
                    val dec = decryptSecureData(enc)
                    val iframeMatches = Regex("""(?:src|source_content)\s*[:=]\s*["'](?:\\/\\/|//)?([^"'\s<>]*(?:pichive|four\.pichive)[^"'\s<>]*)["']""").findAll(dec)
                    for (m in iframeMatches) {
                        val rawSrc = m.groupValues[1].replace("""\/""", "/")
                        val fullUrl = if (rawSrc.startsWith("http")) rawSrc else "https://${rawSrc.removePrefix("//")}"
                        val normalized = if (fullUrl.contains("pichive.online") && !fullUrl.contains("four.pichive.online")) {
                            fullUrl.replace("pichive.online", "four.pichive.online")
                        } else fullUrl
                        iframes.add(normalized)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        for (iframe in doc.select("iframe")) {
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
            if (src.isNotBlank()) {
                val fullUrl = if (src.startsWith("http")) src else if (src.startsWith("//")) "https:$src" else "${mainUrl}/$src"
                iframes.add(fullUrl)
            }
        }

        val distinctIframes = iframes.distinct()
        val count = BoundedParallelResolver.resolveProgressive(
            candidates = distinctIframes,
            resolver = { iframeUrl, emitLink ->
                val collected = mutableListOf<ExtractorLink>()
                if (iframeUrl.contains("pichive.online")) {
                    PichiveExtractor().getUrl(iframeUrl, referer = mainUrl, subtitleCallback, collected::add)
                } else {
                    loadExtractor(iframeUrl, referer = data, subtitleCallback, collected::add)
                }
                for (link in collected) {
                    val preflight = StreamValidator.validateStream(link.url, link.headers, name)
                    if (preflight.status != com.cloudstream.tr.core.network.ValidationStatus.INVALID) {
                        emitLink(link)
                    }
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
