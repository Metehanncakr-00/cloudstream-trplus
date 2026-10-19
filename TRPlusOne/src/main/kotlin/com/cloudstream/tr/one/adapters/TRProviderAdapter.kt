package com.cloudstream.tr.one.adapters

import com.cloudstream.tr.one.models.ProviderListing
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * TR+ ONE birleşik mimarisinde her bir sağlayıcı için sözleşme.
 */
interface TRProviderAdapter {
    val providerId: String
    val displayName: String
    val isEnabled: Boolean
    val supportedTypes: Set<TvType>

    /**
     * Sağlayıcının ana sayfa/kategori sayfasından belirli bir sayfayı çeker.
     */
    suspend fun fetchCatalog(category: String, page: Int): List<ProviderListing>

    /**
     * Sağlayıcı içinde arama yapar.
     */
    suspend fun search(query: String, page: Int): List<ProviderListing>

    /**
     * İçerik detay sayfasını çözümler (bölümler, çıkış yılı, özet).
     */
    suspend fun loadDetail(url: String): ProviderListing?

    /**
     * Gerçek oynatma akışlarını ve altyazıları çıkarır.
     */
    suspend fun loadStreamLinks(
        data: String,
        isCasting: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean
}
