package com.cloudstream.tr.one

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * Standard contract for TR+ ONE provider adapters.
 * Isolates individual provider differences from the unified engine.
 */
interface TRProviderAdapter {
    val providerId: String
    val displayName: String
    val supportedTypes: Set<OneKind>

    suspend fun fetchCatalog(category: String, page: Int): List<ProviderListing>
    suspend fun search(query: String): List<ProviderListing>
    suspend fun loadDetail(url: String): LoadResponse?
    suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean
}
