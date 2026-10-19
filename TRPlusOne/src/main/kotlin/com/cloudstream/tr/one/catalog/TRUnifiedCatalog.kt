package com.cloudstream.tr.one.catalog

import com.cloudstream.tr.one.adapters.TRAdapterRegistry
import com.cloudstream.tr.one.adapters.TRProviderAdapter
import com.cloudstream.tr.one.dedup.TRCanonicalEngine
import com.cloudstream.tr.one.diagnostics.TRDiagnostics
import com.cloudstream.tr.one.models.CanonicalMedia
import com.cloudstream.tr.one.models.ProviderListing
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object TRUnifiedCatalog {

    val CATEGORIES = listOf(
        "Son Eklenenler",
        "Filmler",
        "Diziler",
        "Anime"
    )

    private const val PROVIDER_TIMEOUT_MS = 4000L
    private val networkSemaphore = Semaphore(3) // Android TV Box eşzamanlılık sınırı

    /**
     * Kategoriye göre ilgili sağlayıcıları belirler.
     */
    fun getAdaptersForCategory(category: String): List<TRProviderAdapter> {
        val all = TRAdapterRegistry.getEnabledAdapters()
        return when (category) {
            "Filmler" -> all.filter { it.providerId == "HDFilmCehennemi" }
            "Diziler" -> all.filter { it.providerId in listOf("SezonlukDizi", "HDFilmCehennemi") }
            "Anime" -> all.filter { it.providerId == "AnimeciX" }
            "Son Eklenenler" -> all
            else -> all
        }
    }

    /**
     * Kategori ve sayfa numarasını çekip tekil kartlar halinde birleştirir.
     */
    suspend fun getCategoryPage(category: String, page: Int): List<CanonicalMedia> {
        // 1. Önce önbelleği kontrol et (Hızlı açılış)
        val cached = TRCatalogCache.get(category, page)
        if (!cached.isNullOrEmpty()) {
            return cached
        }

        val adapters = getAdaptersForCategory(category)
        if (adapters.isEmpty()) return emptyList()

        val rawListings = mutableListOf<ProviderListing>()

        // 2. Sağlayıcıları paralel olarak sorgula (Semaphore ve zaman aşımı korumalı)
        coroutineScope {
            val jobs = adapters.map { adapter ->
                async(Dispatchers.IO) {
                    val startNs = System.nanoTime()
                    try {
                        networkSemaphore.withPermit {
                            withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                                adapter.fetchCatalog(category, page)
                            }
                        }?.let { items ->
                            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                            TRDiagnostics.recordSuccess(adapter.providerId, items.size, elapsedMs)
                            synchronized(rawListings) {
                                rawListings.addAll(items)
                            }
                        } ?: run {
                            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                            TRDiagnostics.recordError(adapter.providerId, "TIMEOUT_${PROVIDER_TIMEOUT_MS}ms", elapsedMs)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                        TRDiagnostics.recordError(adapter.providerId, e.message ?: "UNKNOWN_ERROR", elapsedMs)
                    }
                }
            }
            jobs.awaitAll()
        }

        // 3. Tekil kart birleştirme motorunu çalıştır
        val canonicalList = TRCanonicalEngine.mergeListings(rawListings)

        // 4. Önbelleğe kaydet (Boş değilse)
        if (canonicalList.isNotEmpty()) {
            TRCatalogCache.put(category, page, canonicalList)
        }

        return canonicalList
    }

    /**
     * Tüm aktif sağlayıcılarda paralel arama yapar ve tekilleştirir.
     */
    suspend fun searchAll(query: String, page: Int = 1): List<CanonicalMedia> {
        val adapters = TRAdapterRegistry.getEnabledAdapters()
        if (adapters.isEmpty() || query.isBlank()) return emptyList()

        val rawListings = mutableListOf<ProviderListing>()

        coroutineScope {
            val jobs = adapters.map { adapter ->
                async(Dispatchers.IO) {
                    val startNs = System.nanoTime()
                    try {
                        networkSemaphore.withPermit {
                            withTimeoutOrNull(5000L) {
                                adapter.search(query, page)
                            }
                        }?.let { items ->
                            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                            TRDiagnostics.recordSuccess(adapter.providerId, items.size, elapsedMs)
                            synchronized(rawListings) {
                                rawListings.addAll(items)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                        TRDiagnostics.recordError(adapter.providerId, e.message ?: "SEARCH_ERROR", elapsedMs)
                    }
                }
            }
            jobs.awaitAll()
        }

        return TRCanonicalEngine.mergeListings(rawListings)
    }
}
