package com.cloudstream.tr.one

import com.cloudstream.tr.hdfilmcehennemi.HDFilmCehennemi
import com.cloudstream.tr.sezonlukdizi.SezonlukDizi
import com.cloudstream.tr.animecix.AnimeciX
import com.lagradost.cloudstream3.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class SourcePage(val provider: MainAPI, val pageData: MainPageData)

/** Compiled-in instances: this code works even if the other 25 plugins are absent. */
object OneProviders {
    // Shared by every homepage category AND search request, not per page.
    private val globalSlots = Semaphore(2)
    private val providers by lazy {
        linkedMapOf<String, MainAPI>(
            "HDFilmCehennemi" to HDFilmCehennemi(),
            "SezonlukDizi" to SezonlukDizi(),
            "AnimeciX" to AnimeciX()
        )
    }

    fun byId(id: String): MainAPI? = providers[id]
    fun enabledProviders(): List<MainAPI> = providers.values.filter { OneSettings.isEnabled(it.name) }

    internal fun sections(category: String): List<SourcePage> = enabledProviders().flatMap { provider ->
        provider.mainPage.filter { section ->
            when (category) {
                "movies" -> provider.name == "HDFilmCehennemi" && section.name == "Filmler"
                "series" -> (provider.name == "SezonlukDizi" && section.name == "Son Eklenenler") ||
                    (provider.name == "HDFilmCehennemi" && section.name == "Diziler")
                "anime" -> provider.name == "AnimeciX" // films AND series
                "recent" -> (provider.name == "HDFilmCehennemi" && section.name == "Son Eklenen Filmler") ||
                    (provider.name == "SezonlukDizi" && section.name == "Son Eklenenler") ||
                    (provider.name == "AnimeciX" && section.name == "Seriler")
                else -> false
            }
        }.map { SourcePage(provider, it) }
    }

    fun listing(provider: MainAPI, search: SearchResponse, categoryTitle: String? = null): ProviderListing {
        val kind = when {
            provider.name == "AnimeciX" && categoryTitle == "Filmler" -> OneKind.ANIME_MOVIE
            else -> when (search.type) {
            TvType.AnimeMovie -> OneKind.ANIME_MOVIE
            TvType.Anime -> OneKind.ANIME_SERIES
            TvType.TvSeries, TvType.AsianDrama -> OneKind.SERIES
            else -> OneKind.MOVIE
            }
        }
        val year = when (search) {
            is MovieSearchResponse -> search.year
            is TvSeriesSearchResponse -> search.year
            is AnimeSearchResponse -> search.year
            else -> null
        }
        return ProviderListing(provider.name, search.url, search.name, year, kind,
            search.posterUrl, lastSeenMs = System.currentTimeMillis())
    }

    suspend fun fetch(category: String, page: Int): List<ProviderListing> = coroutineScope {
        sections(category).map { section ->
            async(Dispatchers.IO) {
                globalSlots.withPermit {
                    val start = System.nanoTime()
                    try {
                        val response = withTimeoutOrNull(3500L) {
                            section.provider.getMainPage(page, MainPageRequest(
                                section.pageData.name, section.pageData.data, false
                            ))
                        }
                        val listings = response?.items?.flatMap { it.list }.orEmpty()
                            .map { listing(section.provider, it, section.pageData.name) }
                        OneDiagnostics.record(section.provider.name, "category=$category page=$page items=${listings.size} ms=${(System.nanoTime()-start)/1_000_000}")
                        listings
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        OneDiagnostics.record(section.provider.name, "category=$category ERROR:${e.javaClass.simpleName}")
                        emptyList()
                    }
                }
            }
        }.awaitAll().flatten()
    }

    suspend fun search(query: String): List<ProviderListing> = coroutineScope {
        enabledProviders().map { provider ->
            async(Dispatchers.IO) {
                globalSlots.withPermit {
                    try {
                        val results = withTimeoutOrNull(3500L) {
                            // Support providers implementing only the paged API.
                            when (provider.name) {
                                "HDFilmCehennemi" -> provider.search(query)
                                else -> provider.search(query, 1)?.items
                            }
                        }.orEmpty()
                        OneDiagnostics.record(provider.name, "search=$query items=${results.size}")
                        results.map { listing(provider, it) }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        OneDiagnostics.record(provider.name, "search ERROR:${e.javaClass.simpleName}")
                        emptyList()
                    }
                }
            }
        }.awaitAll().flatten()
    }
}
