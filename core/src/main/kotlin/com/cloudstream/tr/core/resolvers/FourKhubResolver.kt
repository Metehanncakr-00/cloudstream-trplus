package com.cloudstream.tr.core.resolvers

import com.cloudstream.tr.core.diagnostics.DiagnosticCategory
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.diagnostics.DiagnosticStage
import com.cloudstream.tr.core.network.StreamValidator
import com.cloudstream.tr.core.network.ValidationStatus
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder

data class RemoteDomainsConfig(
    @JsonProperty("4khdhub") val fourKhub: String? = null
)

object FourKhubResolver {
    private const val DOMAINS_CONFIG_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
    private val defaultDomains = listOf(
        "https://4khdhub.one",
        "https://4khdhub.com"
    )

    private var activeDomains: List<String>? = null

    private fun normalizeSearchResultUrl(href: String, domain: String): String = when {
        href.startsWith("//") -> "https:$href"
        href.startsWith("/") -> "$domain$href"
        else -> href
    }

    private suspend fun getBaseDomains(): List<String> {
        activeDomains?.let { return it }

        var remoteDomain: String? = null
        try {
            val cfg = app.get(DOMAINS_CONFIG_URL, timeout = 5).parsedSafe<RemoteDomainsConfig>()
            remoteDomain = cfg?.fourKhub?.takeIf { it.isNotBlank() }?.trimEnd('/')
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {}

        activeDomains = listOfNotNull(defaultDomains.first(), remoteDomain, defaultDomains.last()).distinct()
        return activeDomains!!
    }

    suspend fun resolve(
        title: String,
        year: Int? = null,
        isMovie: Boolean,
        season: Int? = null,
        episode: Int? = null,
        onValidatedLink: (ExtractorLink, com.cloudstream.tr.core.model.ManifestLanguageEvidence) -> Unit = { _, _ -> }
    ): List<ExtractorLink> {
        if (title.isBlank()) return emptyList()

        val cleanTitle = title.replace(Regex("""[^\w\s]"""), " ").replace(Regex("""\s+"""), " ").trim()
        for (domain in getBaseDomains()) {
            try {
                val encodedQuery = URLEncoder.encode(cleanTitle, "UTF-8")
                val searchUrl = "$domain/?s=$encodedQuery"
                val searchDoc = Jsoup.parse(app.get(searchUrl, timeout = 10).text)

                // Extract articles / entries
                val entries = searchDoc.select("article, div.post-item, div.entry-content, h2.entry-title a, a[href*='-movie-'], a[href*='-series-'], a.movie-card, .movie-card")
                var matchedUrl: String? = null

                for (entry in entries) {
                    val linkTag = if (entry.tagName() == "a") entry else (entry.selectFirst("h2 a, h3 a, a[rel='bookmark']") ?: entry.selectFirst("a"))
                    val itemTitle = (entry.selectFirst("h3.movie-card-title, h2.entry-title")?.text() ?: linkTag?.text() ?: "").trim()
                    val rawHref = linkTag?.attr("href") ?: ""
                    if (rawHref.isBlank()) continue
                    val itemHref = normalizeSearchResultUrl(rawHref, domain)

                    // Check title match
                    val isTitleMatch = itemTitle.contains(cleanTitle, ignoreCase = true) ||
                            cleanTitle.split(" ").all { word -> itemTitle.contains(word, ignoreCase = true) }

                    val isYearMatch = year == null || itemTitle.contains(year.toString())

                    if (isTitleMatch && isYearMatch) {
                        matchedUrl = itemHref
                        break
                    }
                }

                val targetPageUrl = matchedUrl?.takeIf { it.isNotBlank() } ?: continue
                val detailDoc = Jsoup.parse(app.get(targetPageUrl, timeout = 10).text)

                // Extract HubCloud / HubDrive / download links
                val downloadHrefs = detailDoc.select("a[href*='hubcloud'], a[href*='hubdrive'], a[href*='greenmotors.club'], div.download-item a, a.btn")
                    .map { it.attr("href") }
                    .filter { it.isNotBlank() && (it.contains("hubcloud") || it.contains("hubdrive") || it.contains("drive") || it.contains("greenmotors")) }
                    .distinct()

                if (downloadHrefs.isEmpty()) continue

                val links = mutableListOf<ExtractorLink>()

                for (hubUrl in downloadHrefs.take(4)) {
                    try {
                        var hubPageHtml = app.get(hubUrl, timeout = 10).text
                        var hubDoc = Jsoup.parse(hubPageHtml)
                        var buttonPageUrl = hubUrl

                        if (hubUrl.contains("greenmotors.club")) {
                            val nextUrl = hubDoc.select("a[href*='hubcloud'], a[href*='hubdrive']").firstOrNull()?.attr("href") ?: hubDoc.select("a.btn, a[rel='nofollow']").firstOrNull()?.attr("href")
                            if (nextUrl != null && nextUrl.isNotBlank()) {
                                buttonPageUrl = URI(hubUrl).resolve(nextUrl).toString()
                                hubPageHtml = app.get(buttonPageUrl, timeout = 10).text
                                hubDoc = Jsoup.parse(hubPageHtml)
                            }
                        }

                        // Find download buttons: FSL Server, Download File (HubCloud), V-Cloud
                        val buttons = hubDoc.select("div.card-body a.btn, a.btn")
                        val headerText = hubDoc.select("div.card-header, h1, title").text()

                        for (btn in buttons) {
                            val btnText = btn.text()
                            val btnHref = btn.attr("href")
                            if (btnHref.isBlank() || !btnHref.startsWith("http")) continue

                            val isFsl = btnText.contains("FSL Server", ignoreCase = true)
                            val isHubCloud = btnText.contains("Download", ignoreCase = true) || btnText.contains("HubCloud", ignoreCase = true)
                            val isVCloud = btnText.contains("V-Cloud", ignoreCase = true) || btnText.contains("VCloud", ignoreCase = true)

                            if (!isFsl && !isHubCloud && !isVCloud) continue

                            val serverType = when {
                                isFsl -> "FSL Server"
                                isVCloud -> "V-Cloud"
                                else -> "Hub-Cloud"
                            }

                            // Technology and resolution tags
                            val fullInfo = "$headerText $btnText"
                            val is4K = fullInfo.contains("4K", ignoreCase = true) || fullInfo.contains("2160p", ignoreCase = true)
                            val is1080p = fullInfo.contains("1080p", ignoreCase = true)
                            val isAtmos = fullInfo.contains("Atmos", ignoreCase = true)
                            val isDD = fullInfo.contains("DD5", ignoreCase = true) || fullInfo.contains("DD 5", ignoreCase = true) || fullInfo.contains("DD 7", ignoreCase = true) || fullInfo.contains("5.1", ignoreCase = true)
                            val isHevc = fullInfo.contains("HEVC", ignoreCase = true) || fullInfo.contains("x265", ignoreCase = true)
                            val isBluRay = fullInfo.contains("BluRay", ignoreCase = true) || fullInfo.contains("Blu-Ray", ignoreCase = true)

                            var techTags = ""
                            if (isHevc) techTags += " HEVC"
                            if (isBluRay) techTags += " Blu-Ray"
                            if (isAtmos) techTags += " Atmos"
                            if (isDD && !isAtmos) techTags += " DD 5.1"

                            val qualityLabel = when {
                                is4K -> "4K"
                                is1080p -> "1080p"
                                else -> "HD"
                            }

                            val mappedQuality = when (qualityLabel) {
                                "4K" -> Qualities.P2160.value
                                "1080p" -> Qualities.P1080.value
                                else -> Qualities.P720.value
                            }

                            val displayName = "⚡ 4KHDHub $serverType [$qualityLabel$techTags]".trim()
                            val preflight = StreamValidator.validateStream(
                                url = btnHref,
                                headers = mapOf("Referer" to buttonPageUrl),
                                provider = "FourKhubResolver"
                            )
                            if (preflight.status != ValidationStatus.VALID || !preflight.hasMediaEvidence) continue

                            links.add(
                                ExtractorLink(
                                    source = "4KHDHub",
                                    name = displayName,
                                    url = btnHref,
                                    referer = buttonPageUrl,
                                    quality = mappedQuality,
                                    type = preflight.streamType
                                ).also { onValidatedLink(it, preflight.languageEvidence) }
                            )
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                        DiagnosticLogger.log(
                            provider = "FourKhubResolver",
                            stage = DiagnosticStage.LOAD,
                            category = DiagnosticCategory.EXTRACTOR,
                            message = "Error resolving HubCloud page $hubUrl: ${e.message}"
                        )
                    }
                }

                if (links.isNotEmpty()) return links
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                DiagnosticLogger.log(
                    provider = "FourKhubResolver",
                    stage = DiagnosticStage.SEARCH,
                    category = DiagnosticCategory.NETWORK,
                    message = "Failed to search 4KHDHub at $domain for $cleanTitle: ${e.message}"
                )
            }
        }

        return emptyList()
    }
}
