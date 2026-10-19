package com.cloudstream.tr.one

import com.cloudstream.tr.hdfilmcehennemi.HDFilmCehennemi
import com.cloudstream.tr.sezonlukdizi.SezonlukDizi
import com.cloudstream.tr.animecix.AnimeciX
import com.cloudstream.tr.filmmakinesi.FilmMakinesi
import com.cloudstream.tr.sinemacx.SinemaCX
import com.cloudstream.tr.fullhdfilmizlesene.FullHDFilmizlesene
import com.cloudstream.tr.dizilla.Dizilla
import com.cloudstream.tr.diziyou.DiziYou
import com.cloudstream.tr.animeler.Animeler
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

open class BaseMainApiAdapter(
    val api: MainAPI,
    override val supportedTypes: Set<OneKind>,
    private val categoryMatcher: (category: String, sectionName: String) -> Boolean
) : TRProviderAdapter {
    override val providerId: String get() = api.name
    override val displayName: String get() = api.name

    override suspend fun fetchCatalog(category: String, page: Int): List<ProviderListing> {
        val matchingSections = api.mainPage.filter { categoryMatcher(category, it.name) }
        if (matchingSections.isEmpty()) return emptyList()
        val all = mutableListOf<ProviderListing>()
        for (section in matchingSections) {
            val resp = api.getMainPage(page, MainPageRequest(section.name, section.data, false))
            val items = resp?.items?.flatMap { it.list }.orEmpty()
            all.addAll(items.map { toListing(it, section.name) })
        }
        return all
    }

    override suspend fun search(query: String): List<ProviderListing> {
        val results: List<SearchResponse> = (try {
            api.search(query, 1)?.items
        } catch (_: Throwable) {
            null
        } ?: try {
            api.search(query)
        } catch (_: Throwable) {
            null
        }).orEmpty()
        return results.map { toListing(it, null) }
    }

    override suspend fun loadDetail(url: String): LoadResponse? = api.load(url)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = api.loadLinks(data, isCasting, subtitleCallback, callback)

    open fun toListing(search: SearchResponse, sectionName: String?): ProviderListing {
        val kind = when (search.type) {
            TvType.AnimeMovie -> OneKind.ANIME_MOVIE
            TvType.Anime -> OneKind.ANIME_SERIES
            TvType.TvSeries, TvType.AsianDrama -> OneKind.SERIES
            else -> if (supportedTypes.contains(OneKind.ANIME_SERIES) && !supportedTypes.contains(OneKind.MOVIE)) {
                OneKind.ANIME_SERIES
            } else OneKind.MOVIE
        }
        val year = (search as? MovieSearchResponse)?.year
            ?: (search as? TvSeriesSearchResponse)?.year
            ?: (search as? AnimeSearchResponse)?.year
        return ProviderListing(
            providerId = providerId,
            url = search.url,
            name = search.name,
            year = year,
            kind = kind,
            posterUrl = search.posterUrl,
            lastSeenMs = System.currentTimeMillis()
        )
    }
}

class HDFilmCehennemiAdapter : BaseMainApiAdapter(
    api = HDFilmCehennemi(),
    supportedTypes = setOf(OneKind.MOVIE, OneKind.SERIES),
    categoryMatcher = { category, section ->
        when (category) {
            "movies" -> section == "Filmler"
            "series" -> section == "Diziler"
            "recent" -> section == "Son Eklenen Filmler"
            else -> false
        }
    }
)

class SezonlukDiziAdapter : BaseMainApiAdapter(
    api = SezonlukDizi(),
    supportedTypes = setOf(OneKind.SERIES),
    categoryMatcher = { category, section ->
        when (category) {
            "series" -> section == "Son Eklenenler" || section == "Popüler Diziler"
            "recent" -> section == "Son Eklenenler"
            else -> false
        }
    }
)

class AnimeciXAdapter : BaseMainApiAdapter(
    api = AnimeciX(),
    supportedTypes = setOf(OneKind.ANIME_SERIES, OneKind.ANIME_MOVIE),
    categoryMatcher = { category, section ->
        when (category) {
            "anime" -> true
            "recent" -> section == "Seriler"
            else -> false
        }
    }
) {
    override fun toListing(search: SearchResponse, sectionName: String?): ProviderListing {
        val isMovie = sectionName == "Filmler" || search.type == TvType.AnimeMovie
        val kind = if (isMovie) OneKind.ANIME_MOVIE else OneKind.ANIME_SERIES
        val year = (search as? AnimeSearchResponse)?.year ?: (search as? MovieSearchResponse)?.year
        return ProviderListing(
            providerId = providerId,
            url = search.url,
            name = search.name,
            year = year,
            kind = kind,
            posterUrl = search.posterUrl,
            lastSeenMs = System.currentTimeMillis()
        )
    }
}

class FilmMakinesiAdapter : BaseMainApiAdapter(
    api = FilmMakinesi(),
    supportedTypes = setOf(OneKind.MOVIE, OneKind.SERIES),
    categoryMatcher = { category, section ->
        when (category) {
            "movies" -> section.contains("Film")
            "series" -> section.contains("Dizi")
            "recent" -> section.contains("Son Eklenen")
            else -> false
        }
    }
)

class SinemaCXAdapter : BaseMainApiAdapter(
    api = SinemaCX(),
    supportedTypes = setOf(OneKind.MOVIE),
    categoryMatcher = { category, section ->
        when (category) {
            "movies" -> true
            "recent" -> section.contains("Son Eklenen")
            else -> false
        }
    }
)

class FullHDFilmizleseneAdapter : BaseMainApiAdapter(
    api = FullHDFilmizlesene(),
    supportedTypes = setOf(OneKind.MOVIE),
    categoryMatcher = { category, section ->
        when (category) {
            "movies" -> true
            "recent" -> section.contains("Son Eklenen")
            else -> false
        }
    }
)

class DizillaAdapter : BaseMainApiAdapter(
    api = Dizilla(),
    supportedTypes = setOf(OneKind.SERIES, OneKind.ANIME_SERIES),
    categoryMatcher = { category, section ->
        when (category) {
            "series" -> section.contains("Dizi") || section.contains("Son Eklenen")
            "anime" -> section.contains("Anime")
            "recent" -> section.contains("Son Eklenen")
            else -> false
        }
    }
)

class DiziYouAdapter : BaseMainApiAdapter(
    api = DiziYou(),
    supportedTypes = setOf(OneKind.SERIES),
    categoryMatcher = { category, section ->
        when (category) {
            "series" -> true
            "recent" -> section.contains("Son Eklenen")
            else -> false
        }
    }
)

class AnimelerAdapter : BaseMainApiAdapter(
    api = Animeler(),
    supportedTypes = setOf(OneKind.ANIME_SERIES, OneKind.ANIME_MOVIE),
    categoryMatcher = { category, section ->
        when (category) {
            "anime" -> true
            "recent" -> section.contains("Son Eklenen")
            else -> false
        }
    }
) {
    override fun toListing(search: SearchResponse, sectionName: String?): ProviderListing {
        val kind = if (search.type == TvType.AnimeMovie) OneKind.ANIME_MOVIE else OneKind.ANIME_SERIES
        val year = (search as? AnimeSearchResponse)?.year
        return ProviderListing(
            providerId = providerId,
            url = search.url,
            name = search.name,
            year = year,
            kind = kind,
            posterUrl = search.posterUrl,
            lastSeenMs = System.currentTimeMillis()
        )
    }
}

/**
 * Modular registry of TR+ ONE provider adapters.
 * Designed so new providers can be added without modifying the TR+ ONE engine.
 */
object OneProviders {
    private val globalSlots = Semaphore(4)
    private val adapters = LinkedHashMap<String, TRProviderAdapter>()

    init {
        registerDefaults()
    }

    private fun registerDefaults() {
        register(HDFilmCehennemiAdapter())
        register(SezonlukDiziAdapter())
        register(AnimeciXAdapter())
        register(FilmMakinesiAdapter())
        register(SinemaCXAdapter())
        register(FullHDFilmizleseneAdapter())
        register(DizillaAdapter())
        register(DiziYouAdapter())
        register(AnimelerAdapter())
    }

    fun register(adapter: TRProviderAdapter) {
        adapters[adapter.providerId] = adapter
    }

    fun byId(id: String): TRProviderAdapter? = adapters[id]
    fun all(): List<TRProviderAdapter> = adapters.values.toList()
    fun enabledAdapters(): List<TRProviderAdapter> = adapters.values.filter { OneSettings.isEnabled(it.providerId) }

    suspend fun fetch(category: String, page: Int): List<ProviderListing> = coroutineScope {
        enabledAdapters().map { adapter ->
            async(Dispatchers.IO) {
                globalSlots.withPermit {
                    val start = System.nanoTime()
                    try {
                        val listings = withTimeoutOrNull(4500L) {
                            adapter.fetchCatalog(category, page)
                        }.orEmpty()
                        val elapsedMs = (System.nanoTime() - start) / 1_000_000
                        OneDiagnostics.record(adapter.providerId, "category=$category page=$page items=${listings.size} ms=$elapsedMs")
                        listings
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        OneDiagnostics.record(adapter.providerId, "category=$category ERROR:${e.javaClass.simpleName}")
                        emptyList()
                    }
                }
            }
        }.awaitAll().flatten()
    }

    suspend fun search(query: String): List<ProviderListing> = coroutineScope {
        enabledAdapters().map { adapter ->
            async(Dispatchers.IO) {
                globalSlots.withPermit {
                    val start = System.nanoTime()
                    try {
                        val results = withTimeoutOrNull(4500L) {
                            adapter.search(query)
                        }.orEmpty()
                        val elapsedMs = (System.nanoTime() - start) / 1_000_000
                        OneDiagnostics.record(adapter.providerId, "search=$query items=${results.size} ms=$elapsedMs")
                        results
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        OneDiagnostics.record(adapter.providerId, "search ERROR:${e.javaClass.simpleName}")
                        emptyList()
                    }
                }
            }
        }.awaitAll().flatten()
    }

    @Synchronized
    fun resetForTests(testAdapters: List<TRProviderAdapter>? = null) {
        adapters.clear()
        if (testAdapters != null) {
            testAdapters.forEach { register(it) }
        } else {
            registerDefaults()
        }
    }
}
