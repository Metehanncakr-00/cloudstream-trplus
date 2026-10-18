package com.cloudstream.tr.fourkhdhub

import com.cloudstream.tr.core.resolvers.FourKhubResolver
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Document
import java.net.URLEncoder

class FourKHDHub : MainAPI() {
    override var mainUrl = "https://4khdhub.one"
    override var name = "4KHDHub"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Ana Sayfa"
    )

    internal fun parseCards(document: Document): List<SearchResponse> =
        document.select("article, div.post-item, a.movie-card, .movie-card, a[href*='-movie-'], a[href*='-series-'], a[href*='-season-']").mapNotNull { element ->
            val a = if (element.tagName() == "a") element else (element.selectFirst("h2.entry-title a, h3.movie-card-title a, a[rel='bookmark']") ?: element.selectFirst("a")) ?: return@mapNotNull null
            val rawTitle = (element.selectFirst("h3.movie-card-title, h2.entry-title, .title")?.text() ?: a.text()).trim()
            val title = rawTitle.replace(Regex("""^\s*(?:4K|DV|HDR|BD|1080p|10Bit|2160p|\s+)+"""), "").trim().ifBlank { rawTitle }
            if (title.isBlank()) return@mapNotNull null
            val href = fixUrl(a.attr("href"))
            val poster = element.selectFirst("img")?.attr("src")

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }.distinctBy { it.url }


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page == 1) request.data else "${request.data}page/$page/"
        val document = app.get(targetUrl).document
        val items = parseCards(document)

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val targetUrl = "$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}"
        val document = app.get(targetUrl).document
        return parseCards(document)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val rawTitle = document.selectFirst("h1.entry-title, h1")?.text() ?: document.title()
        val title = rawTitle.replace(" - 4KHDHub", "").replace(" Download", "").trim()
        val poster = document.selectFirst("div.entry-content img")?.attr("src")
        val plot = document.select("div.entry-content p").firstOrNull { it.text().isNotBlank() }?.text()

        val loadData = title

        return newMovieLoadResponse(title, url, TvType.Movie, loadData) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        
        val links = FourKhubResolver.resolve(title = data, isMovie = true)
        var found = false
        for (link in links) {
            callback(link)
            found = true
        }
        return found
    }
}

