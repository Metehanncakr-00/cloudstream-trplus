package com.cloudstream.tr.hub

import com.cloudstream.tr.core.diagnostics.DiagnosticCategory
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.diagnostics.DiagnosticStage
import com.lagradost.cloudstream3.MainAPI

object CloudStreamProviderRegistryAdapter {

    internal val knownProviderClasses = listOf(
        "com.cloudstream.tr.hdfilmcehennemi.HDFilmCehennemi",
        "com.cloudstream.tr.filmmakinesi.FilmMakinesi",
        "com.cloudstream.tr.kultfilmler.KultFilmler",
        "com.cloudstream.tr.sinemacx.SinemaCX",
        "com.cloudstream.tr.hdfilmdelisi.HDFilmDelisi",
        "com.cloudstream.tr.yesilcamtv.YesilCamTv",
        "com.cloudstream.tr.sezonlukdizi.SezonlukDizi",
        "com.cloudstream.tr.startv.StarTV",
        "com.cloudstream.tr.diziizle.DiziIzle",
        "com.cloudstream.tr.diziyou.DiziYou",
        "com.cloudstream.tr.dizikorea.DiziKorea",
        "com.cloudstream.tr.dizilla.Dizilla",
        "com.cloudstream.tr.jetfilmizle.JetFilmIzle",
        "com.cloudstream.tr.fullhdfilmizlesene.FullHDFilmizlesene",
        "com.cloudstream.tr.animecix.AnimeciX",
        "com.cloudstream.tr.animeler.Animeler",
        "com.cloudstream.tr.belgeselx.BelgeselX",
        "com.cloudstream.tr.dramadizilerim.DramaDizilerim",
        "com.cloudstream.tr.webdramaturkey.WebDramaTurkey",
        "com.cloudstream.tr.setfilmizle.SetFilmIzle",
        "com.cloudstream.tr.dizigecesi.Dizigecesi",
        "com.cloudstream.tr.rarefilmm.RareFilmm",
        "com.cloudstream.tr.blenderopenmovies.BlenderOpenMovies"
    )

    private const val DISCOVERY_CACHE_TTL_MS = 30_000L

    @Volatile
    private var cachedProviders: List<MainAPI>? = null

    @Volatile
    private var lastDiscoveryTimestampMs: Long = 0L

    @Volatile
    internal var bundledInstantiationCount: Int = 0
        private set

    /**
     * Instantiate bundled fallback classes at most ONCE per process lifetime
     * to avoid reflection and object allocation storms on Android TV.
     */
    private val bundledProviderInstances: List<MainAPI> by lazy {
        val list = mutableListOf<MainAPI>()
        for (className in knownProviderClasses) {
            try {
                val clazz = Class.forName(className)
                val instance = clazz.getDeclaredConstructor().newInstance() as? MainAPI
                if (instance != null) {
                    bundledInstantiationCount++
                    list.add(instance)
                }
            } catch (_: Throwable) {}
        }
        list
    }

    fun invalidateCache() {
        cachedProviders = null
        lastDiscoveryTimestampMs = 0L
    }

    fun normalizeHost(url: String): String? {
        if (url.isBlank()) return null
        return try {
            java.net.URI(url.trim()).host?.lowercase(java.util.Locale.ROOT)?.removePrefix("www.")
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Fast provider lookup for a URL without re-running reflection.
     * Supports exact mainUrl prefix/substring, normalized host match (ignoring www. / scheme),
     * and provider name slug matching for rotated domains.
     */
    fun findProviderForUrl(
        url: String,
        candidates: List<MainAPI> = getAllDiscoverableProviders()
    ): MainAPI? {
        if (url.isBlank()) return null
        // 1. Direct mainUrl substring match
        candidates.firstOrNull { it.mainUrl.isNotBlank() && url.contains(it.mainUrl, ignoreCase = true) }
            ?.let { return it }

        // 2. Normalized host comparison
        val targetHost = normalizeHost(url) ?: return null
        candidates.firstOrNull { prov ->
            val provHost = normalizeHost(prov.mainUrl)
            provHost != null && (targetHost == provHost || targetHost.endsWith(".$provHost") || provHost.endsWith(".$targetHost"))
        }?.let { return it }

        // 3. Domain core slug match against provider name (handles TLD rotation e.g. .com -> .cx)
        val hostSlug = targetHost.substringBefore(".").replace(Regex("[^a-z0-9]"), "")
        if (hostSlug.length >= 4) {
            candidates.firstOrNull { prov ->
                val provSlug = prov.name.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
                provSlug == hostSlug || provSlug.contains(hostSlug) || hostSlug.contains(provSlug)
            }?.let { return it }
        }

        return null
    }

    fun detectProviderNameForUrl(
        url: String,
        candidates: List<MainAPI> = getAllDiscoverableProviders()
    ): String? = findProviderForUrl(url, candidates)?.name

    /**
     * Discovers all providers from CloudStream's APIHolder or bundled modules.
     * Cached for [DISCOVERY_CACHE_TTL_MS] to prevent repeated reflection on Android TV.
     */
    fun getAllDiscoverableProviders(): List<MainAPI> {
        val now = System.currentTimeMillis()
        cachedProviders?.let { cached ->
            if (now - lastDiscoveryTimestampMs < DISCOVERY_CACHE_TTL_MS && cached.isNotEmpty()) {
                return cached
            }
        }

        synchronized(this) {
            cachedProviders?.let { cached ->
                if (now - lastDiscoveryTimestampMs < DISCOVERY_CACHE_TTL_MS && cached.isNotEmpty()) {
                    return cached
                }
            }

            val discovered = mutableListOf<MainAPI>()

            // 1. Try reflection on CloudStream APIHolder
            try {
                val holderClass = Class.forName("com.lagradost.cloudstream3.APIHolder")
                val holderInstance = try {
                    holderClass.getField("INSTANCE").get(null)
                } catch (_: Exception) {
                    null
                }

                val getterNames = listOf("getAllProviders", "getApis", "getPlugins", "getAllApis")
                for (mName in getterNames) {
                    try {
                        val m = holderClass.getMethod(mName)
                        m.isAccessible = true
                        val obj = m.invoke(holderInstance)
                        val items = when (obj) {
                            is Array<*> -> obj.filterIsInstance<MainAPI>()
                            is Collection<*> -> obj.filterIsInstance<MainAPI>()
                            else -> emptyList()
                        }
                        if (items.isNotEmpty()) {
                            discovered.addAll(items)
                            break
                        }
                    } catch (_: Exception) {}
                }

                if (discovered.isEmpty()) {
                    val candidateFields = listOf("allProviders", "apis", "loadedPlugins", "plugins")
                    for (fieldName in candidateFields) {
                        try {
                            val field = holderClass.getDeclaredField(fieldName)
                            field.isAccessible = true
                            val obj = field.get(holderInstance)
                            val items = when (obj) {
                                is Array<*> -> obj.filterIsInstance<MainAPI>()
                                is Collection<*> -> obj.filterIsInstance<MainAPI>()
                                else -> emptyList()
                            }
                            if (items.isNotEmpty()) {
                                discovered.addAll(items)
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                DiagnosticLogger.log(
                    provider = "CloudStreamHub",
                    stage = DiagnosticStage.LOAD,
                    category = DiagnosticCategory.SOURCE_DISCOVERY,
                    message = "APIHolder reflection failed: ${e.message}",
                    throwable = e
                )
            }

            // 2. Merge cached bundled provider instances without re-instantiating classes.
            for (instance in bundledProviderInstances) {
                if (discovered.none { it.name == instance.name }) {
                    discovered.add(instance)
                }
            }

            val result = discovered.distinctBy { it.name }
            cachedProviders = result
            lastDiscoveryTimestampMs = now
            return result
        }
    }

    /**
     * Combines providers registered in CloudStream's APIHolder with known bundled providers.
     * YTS and 4KHDHub are excluded from general HTTP federation because they are separately
     * resolved via FourKhubResolver and YtsResolver to avoid duplicate link processing.
     */
    fun getRegisteredProviders(
        excludeName: String = "CloudStreamHub",
        onlyEnabled: Boolean = false
    ): List<MainAPI> {
        val all = getAllDiscoverableProviders()
        val filtered = all.filter {
            it.name != excludeName && it.name !in setOf("CloudStreamHub", "YTS", "4KHDHub")
        }
        val result = if (onlyEnabled) {
            filtered.filter { HubSettingsManager.isProviderEnabled(it.name) }
        } else {
            filtered
        }

        if (result.isEmpty()) {
            DiagnosticLogger.log(
                provider = "CloudStreamHub",
                stage = DiagnosticStage.LOAD,
                category = DiagnosticCategory.SOURCE_DISCOVERY,
                message = "No external providers found in CloudStream's registry or bundled modules."
            )
        }
        return result
    }
}
