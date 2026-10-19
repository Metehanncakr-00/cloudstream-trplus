package com.cloudstream.tr.one.models

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.TvType

val trJsonMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

/**
 * Tek bir sağlayıcıdan çekilen içerik kaydı.
 */
data class ProviderListing(
    val providerId: String,
    val providerUrl: String,
    val title: String,
    val mediaType: TvType,
    val year: Int? = null,
    val posterUrl: String? = null,
    val seasons: List<TREpisodeInfo>? = null,
    val languageEvidence: List<String> = emptyList(),
    val qualityEvidence: String? = null,
    val confidenceScore: Double = 1.0,
    val lastVerifiedMs: Long = System.currentTimeMillis()
)

/**
 * Dizi ve anime bölümlerinin ortak modeli.
 */
data class TREpisodeInfo(
    val name: String,
    val season: Int,
    val episode: Int,
    val dataUrl: String,
    val posterUrl: String? = null
)

/**
 * Tek kart altında birleştirilmiş kanonik medya modeli.
 */
data class CanonicalMedia(
    val canonicalId: String,
    val title: String,
    val mediaType: TvType,
    val year: Int? = null,
    val posterUrl: String? = null,
    val plot: String? = null,
    val score: Double? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val listings: MutableList<ProviderListing> = mutableListOf()
) {
    fun toPayload(): CanonicalPayload {
        return CanonicalPayload(
            canonicalId = canonicalId,
            title = title,
            mediaType = mediaType.name,
            year = year,
            primaryUrl = listings.firstOrNull()?.providerUrl ?: "",
            primaryProvider = listings.firstOrNull()?.providerId ?: "",
            sources = listings.map { CanonicalSourceRef(it.providerId, it.providerUrl) }
        )
    }
}

/**
 * CloudStream SearchResponse.url içine gömülen JSON yükü.
 */
data class CanonicalPayload(
    @JsonProperty("canonicalId") val canonicalId: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("mediaType") val mediaType: String,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("primaryUrl") val primaryUrl: String,
    @JsonProperty("primaryProvider") val primaryProvider: String,
    @JsonProperty("sources") val sources: List<CanonicalSourceRef> = emptyList()
) {
    fun toJsonString(): String = trJsonMapper.writeValueAsString(this)

    companion object {
        fun fromJsonString(json: String): CanonicalPayload? {
            return try {
                trJsonMapper.readValue(json, CanonicalPayload::class.java)
            } catch (_: Throwable) {
                null
            }
        }
    }
}

data class CanonicalSourceRef(
    @JsonProperty("providerId") val providerId: String,
    @JsonProperty("url") val url: String
)

/**
 * Dizi / Anime bölümlerinde çoklu kaynak referansı taşıyan JSON yükü.
 */
data class CanonicalEpisodePayload(
    @JsonProperty("canonicalId") val canonicalId: String,
    @JsonProperty("season") val season: Int,
    @JsonProperty("episode") val episode: Int,
    @JsonProperty("sources") val sources: List<CanonicalEpisodeSourceRef> = emptyList()
) {
    fun toJsonString(): String = trJsonMapper.writeValueAsString(this)

    companion object {
        fun fromJsonString(json: String): CanonicalEpisodePayload? {
            return try {
                trJsonMapper.readValue(json, CanonicalEpisodePayload::class.java)
            } catch (_: Throwable) {
                null
            }
        }
    }
}

data class CanonicalEpisodeSourceRef(
    @JsonProperty("providerId") val providerId: String,
    @JsonProperty("data") val data: String
)
