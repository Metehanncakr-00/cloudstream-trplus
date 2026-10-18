package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.TvType

/**
 * Coverage status of a provider in the Unified Catalog.
 * Truthful reporting: never claim full indexing if provider has no listing.
 */
enum class CatalogCoverageStatus {
    FULL_CATALOG,      // Provides mainPage categories and paginated listing
    CATEGORIES_ONLY,   // Provides category mainPage but no deep pagination
    SEARCH_ONLY,       // No mainPage listing, discoverable only via live search
    NO_LISTING,        // Inaccessible or no listing supported
    DISABLED           // Explicitly disabled in Hub settings
}

/**
 * Metadata about an individual category discovered from a provider.
 */
data class ProviderCategoryInfo(
    val providerName: String,
    val categoryName: String,
    val categoryUrl: String,
    val unifiedCategory: String
)

/**
 * Capability profile of a provider.
 */
data class ProviderCapability(
    val providerName: String,
    val mainUrl: String,
    val hasMainPage: Boolean,
    val supportedTypes: Set<TvType>,
    val categories: List<ProviderCategoryInfo> = emptyList(),
    val coverageStatus: CatalogCoverageStatus = CatalogCoverageStatus.FULL_CATALOG,
    val isEnabled: Boolean = true
)

/**
 * Canonical 16 Unified Categories requested for CloudStream TR+ v42 Super Hub.
 */
object UnifiedCategories {
    const val ALL_CONTENT = "Tüm İçerikler"
    const val MOVIES = "Filmler"
    const val SERIES = "Diziler"
    const val TURKISH_SERIES = "Türk Dizileri"
    const val NOSTALGIC_TURKISH_SERIES = "Eski / Nostaljik Türk Dizileri"
    const val CARTOONS = "Çizgi Filmler"
    const val CARTOON_SERIES = "Çizgi Diziler"
    const val ANIMATION_MOVIES = "Animasyon Filmleri"
    const val ANIME = "Anime"
    const val DOCUMENTARIES = "Belgeseller"
    const val YESILCAM = "Yeşilçam"
    const val TR_DUB = "Türkçe Dublaj"
    const val TR_SUB = "Türkçe Altyazı"
    const val NEW_RELEASES = "Yeni Eklenenler"
    const val POPULAR = "Popüler İçerikler"
    const val CONTINUE_WATCHING = "İzlemeye Devam Et"

    val ALL = listOf(
        ALL_CONTENT,
        MOVIES,
        SERIES,
        TURKISH_SERIES,
        NOSTALGIC_TURKISH_SERIES,
        CARTOONS,
        CARTOON_SERIES,
        ANIMATION_MOVIES,
        ANIME,
        DOCUMENTARIES,
        YESILCAM,
        TR_DUB,
        TR_SUB,
        NEW_RELEASES,
        POPULAR,
        CONTINUE_WATCHING
    )
}

/**
 * Preserves the exact origin and metadata of each matching provider candidate.
 */
data class ProviderContentReference(
    val providerName: String,
    val url: String,
    val realId: String? = null,
    val title: String,
    val year: Int? = null,
    val tvType: TvType? = null,
    val confidenceScore: Double = 1.0
)

/**
 * High-confidence unified media model representing a single canonical item
 * that can be fulfilled by multiple underlying providers.
 */
data class HubUnifiedMedia(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val isMovie: Boolean = true,
    val tvType: TvType = if (isMovie) TvType.Movie else TvType.TvSeries,
    val posterUrl: String? = null,
    val plot: String? = null,
    val score: Double? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val providerUrls: Map<String, String> = emptyMap(), // providerName -> directUrl
    val verifiedLanguages: Set<String> = emptySet() // "TR_DUB", "TR_SUB", etc.
)

/**
 * High-confidence canonical media model representing a single canonical item
 * and its origin provider references.
 */
data class CanonicalMedia(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val isMovie: Boolean = true,
    val tvType: TvType = if (isMovie) TvType.Movie else TvType.TvSeries,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val plot: String? = null,
    val score: String? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val providerReferences: List<ProviderContentReference> = emptyList()
)

