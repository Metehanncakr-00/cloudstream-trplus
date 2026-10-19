package com.cloudstream.tr.one.adapters

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI

object TRAdapterRegistry {

    private val bundledAdapters = mutableMapOf<String, TRProviderAdapter>()

    init {
        registerAdapter(HDFilmCehennemiAdapter())
        registerAdapter(SezonlukDiziAdapter())
        registerAdapter(AnimeciXAdapter())
    }

    fun registerAdapter(adapter: TRProviderAdapter) {
        bundledAdapters[adapter.providerId.lowercase()] = adapter
    }

    fun getAdapter(providerId: String): TRProviderAdapter? {
        return bundledAdapters[providerId.lowercase()]
    }

    fun getAllAdapters(): List<TRProviderAdapter> {
        return bundledAdapters.values.toList()
    }

    fun getEnabledAdapters(): List<TRProviderAdapter> {
        return bundledAdapters.values.filter { it.isEnabled }
    }

    /**
     * APIHolder üzerinden ev sahibi CloudStream uygulamasında kayıtlı
     * harici MainAPI nesnelerini opsiyonel olarak tespit eder.
     * Bu fonksiyon eklenti bağımsızlığını bozmaz; cihazda başka eklenti yoksa
     * boş liste döner ve TR+ ONE kendi gömülü adaptörleriyle çalışmaya devam eder.
     */
    fun getRegisteredExternalProviders(): List<MainAPI> {
        return try {
            val list = APIHolder.allProviders
            list.filter { it.name != "TR+ ONE" && it.name != "TRPlusOne" }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
