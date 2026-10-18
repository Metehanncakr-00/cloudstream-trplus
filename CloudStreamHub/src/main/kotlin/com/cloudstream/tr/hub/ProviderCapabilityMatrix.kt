package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the capability matrix across all discoverable providers.
 * Truthfully inspects mainPage, search, supported types, and category inventory.
 */
object ProviderCapabilityMatrix {

    private val capabilities = ConcurrentHashMap<String, ProviderCapability>()

    /**
     * Maps provider raw category names into canonical UnifiedCategories.
     */
    fun mapCategoryNameToUnified(categoryName: String, providerTypes: Set<TvType>): String {
        val lower = categoryName.lowercase(Locale("tr", "TR"))
        return when {
            "yeşilçam" in lower || "yesilcam" in lower || "klasik türk" in lower -> UnifiedCategories.YESILCAM
            "nostalji" in lower || "eski dizi" in lower || "90lar" in lower || "80ler" in lower -> UnifiedCategories.NOSTALGIC_TURKISH_SERIES
            "türk dizi" in lower || "yerli dizi" in lower -> UnifiedCategories.TURKISH_SERIES
            "belgesel" in lower || TvType.Documentary in providerTypes -> UnifiedCategories.DOCUMENTARIES
            "anime" in lower || TvType.Anime in providerTypes || TvType.AnimeMovie in providerTypes -> UnifiedCategories.ANIME
            "çizgi dizi" in lower || "animasyon dizi" in lower -> UnifiedCategories.CARTOON_SERIES
            "çizgi" in lower || "çocuk" in lower || TvType.Cartoon in providerTypes -> UnifiedCategories.CARTOONS
            "animasyon" in lower -> UnifiedCategories.ANIMATION_MOVIES
            "dublaj" in lower -> UnifiedCategories.TR_DUB
            "altyazı" in lower || "altyazi" in lower -> UnifiedCategories.TR_SUB
            "yeni" in lower || "vizyon" in lower || "son eklenen" in lower || "güncel" in lower -> UnifiedCategories.NEW_RELEASES
            "popüler" in lower || "trend" in lower || "en çok" in lower || "hit" in lower -> UnifiedCategories.POPULAR
            "dizi" in lower || TvType.TvSeries in providerTypes || TvType.AsianDrama in providerTypes -> UnifiedCategories.SERIES
            "film" in lower || "sinema" in lower || TvType.Movie in providerTypes -> UnifiedCategories.MOVIES
            else -> UnifiedCategories.ALL_CONTENT
        }
    }

    /**
     * Inspects a provider and compiles its capability profile.
     */
    fun evaluate(provider: MainAPI, isEnabled: Boolean = true): ProviderCapability {
        val name = provider.name
        val types = provider.supportedTypes
        val hasMain = provider.hasMainPage
        val categories = mutableListOf<ProviderCategoryInfo>()

        var coverage = when {
            !isEnabled -> CatalogCoverageStatus.DISABLED
            hasMain -> {
                val rawPages = try {
                    provider.mainPage
                } catch (_: Throwable) {
                    emptyList()
                }
                if (rawPages.isNotEmpty()) {
                    for (page in rawPages) {
                        val mapped = mapCategoryNameToUnified(page.name, types)
                        categories.add(
                            ProviderCategoryInfo(
                                providerName = name,
                                categoryName = page.name,
                                categoryUrl = page.data,
                                unifiedCategory = mapped
                            )
                        )
                    }
                    CatalogCoverageStatus.FULL_CATALOG
                } else {
                    CatalogCoverageStatus.SEARCH_ONLY
                }
            }
            else -> CatalogCoverageStatus.SEARCH_ONLY
        }

        // If no main page and cannot search, it has no listing
        if (coverage == CatalogCoverageStatus.SEARCH_ONLY) {
            val hasSearch = try {
                provider.hasQuickSearch
            } catch (_: Throwable) {
                false
            }
            if (!hasSearch && categories.isEmpty()) {
                coverage = CatalogCoverageStatus.NO_LISTING
            }
        }

        val capability = ProviderCapability(
            providerName = name,
            mainUrl = provider.mainUrl,
            hasMainPage = hasMain,
            supportedTypes = types,
            categories = categories,
            coverageStatus = coverage,
            isEnabled = isEnabled
        )

        capabilities[name] = capability
        return capability
    }

    /**
     * Updates capabilities for a list of providers.
     */
    fun updateAll(providers: List<MainAPI>, disabledProviders: Set<String> = emptySet()): Map<String, ProviderCapability> {
        for (p in providers) {
            evaluate(p, isEnabled = p.name !in disabledProviders)
        }
        return capabilities.toMap()
    }

    fun getCapability(providerName: String): ProviderCapability? = capabilities[providerName]

    fun getAllCapabilities(): Map<String, ProviderCapability> = capabilities.toMap()

    /**
     * Returns enabled providers capable of supplying items for the given unified category.
     */
    fun getProvidersForCategory(category: String, allProviders: List<MainAPI>): List<Pair<MainAPI, ProviderCategoryInfo?>> {
        val matches = mutableListOf<Pair<MainAPI, ProviderCategoryInfo?>>()
        for (provider in allProviders) {
            val cap = capabilities[provider.name] ?: evaluate(provider)
            if (!cap.isEnabled || cap.coverageStatus == CatalogCoverageStatus.DISABLED || cap.coverageStatus == CatalogCoverageStatus.NO_LISTING) {
                continue
            }

            if (category == UnifiedCategories.ALL_CONTENT) {
                // ALL_CONTENT must cover all accessible categories across all providers
                if (cap.categories.isNotEmpty()) {
                    for (cat in cap.categories) {
                        matches.add(provider to cat)
                    }
                } else if (cap.hasMainPage) {
                    matches.add(provider to null)
                }
                continue
            }

            // Check direct category matches (support all matching categories for the same provider)
            val catMatches = cap.categories.filter { it.unifiedCategory == category }
            if (catMatches.isNotEmpty()) {
                for (cat in catMatches) {
                    matches.add(provider to cat)
                }
            } else {
                // Secondary match based on supported types (select primary category to prevent fan-out explosion)
                val typeMatch = when (category) {
                    UnifiedCategories.MOVIES, UnifiedCategories.TR_DUB, UnifiedCategories.TR_SUB -> TvType.Movie in cap.supportedTypes
                    UnifiedCategories.SERIES, UnifiedCategories.TURKISH_SERIES -> TvType.TvSeries in cap.supportedTypes || TvType.AsianDrama in cap.supportedTypes
                    UnifiedCategories.ANIME -> TvType.Anime in cap.supportedTypes || TvType.AnimeMovie in cap.supportedTypes
                    UnifiedCategories.CARTOONS, UnifiedCategories.CARTOON_SERIES, UnifiedCategories.ANIMATION_MOVIES ->
                        TvType.Cartoon in cap.supportedTypes || TvType.AnimeMovie in cap.supportedTypes
                    UnifiedCategories.DOCUMENTARIES -> TvType.Documentary in cap.supportedTypes
                    UnifiedCategories.NEW_RELEASES, UnifiedCategories.POPULAR -> true
                    else -> false
                }
                if (typeMatch && cap.categories.isNotEmpty()) {
                    matches.add(provider to cap.categories.first())
                }
            }
        }
        return matches
    }
}
