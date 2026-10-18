package com.cloudstream.tr.core.network

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.util.Locale

@JsonIgnoreProperties(ignoreUnknown = true)
data class DomainConfig(
    @JsonProperty("allowedHosts") val allowedHosts: List<String>? = null,
    @JsonProperty("mirrors") val mirrors: List<String>? = null,
    @JsonProperty("canonical") val canonical: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DomainDocument(
    @JsonProperty("providers") val providers: Map<String, DomainConfig>? = null
)

object DynamicDomainResolver {
    private const val GITHUB_DOMAINS_URL = "https://raw.githubusercontent.com/Emre-Kahveci/CloudStreamHub/main/config/domains.json"
    private var lastFetchTime = 0L
    private const val CACHE_DURATION_MS = 12 * 60 * 60 * 1000L // 12 hours
    private val domainsCache = mutableMapOf<String, List<String>>()
    private val currentDomainIndex = mutableMapOf<String, Int>()
    private val mutex = Mutex()

    suspend fun resolve(providerId: String, defaultUrl: String): String = mutex.withLock {
        fetchDomainsIfNeeded()
        selectEndpoint(domainsCache[providerId], currentDomainIndex[providerId] ?: 0, defaultUrl)
    }

    suspend fun fallbackToNextMirror(providerId: String) {
        mutex.withLock {
            val hosts = domainsCache[providerId]
            if (!hosts.isNullOrEmpty()) {
                val current = currentDomainIndex[providerId] ?: 0
                currentDomainIndex[providerId] = (current + 1) % hosts.size
            }
        }
    }

    private suspend fun fetchDomainsIfNeeded() {
        if (System.currentTimeMillis() - lastFetchTime < CACHE_DURATION_MS && domainsCache.isNotEmpty()) {
            return
        }
        try {
            val response = app.get(GITHUB_DOMAINS_URL, timeout = 5).parsedSafe<DomainDocument>()
            val snapshot = response?.let { validatedSnapshot(it) }
            if (snapshot != null) {
                domainsCache.clear()
                domainsCache.putAll(snapshot)
                currentDomainIndex.clear()
                lastFetchTime = System.currentTimeMillis()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Retain the last valid snapshot, or use the caller's default domain.
        }
    }

    internal fun validatedSnapshot(document: DomainDocument): Map<String, List<String>>? {
        val providers = document.providers?.takeIf { it.isNotEmpty() } ?: return null
        val snapshot = providers.mapValues { (_, config) ->
            val allowedHosts = config.allowedHosts.orEmpty().map { it.lowercase(Locale.ROOT) }.toSet()
            (listOfNotNull(config.canonical) + config.mirrors.orEmpty())
                .mapNotNull { normalizeEndpoint(it, allowedHosts) }.distinct()
        }
        return snapshot.takeIf { it.values.all { endpoints -> endpoints.isNotEmpty() } }
    }

    private fun normalizeEndpoint(value: String, allowedHosts: Set<String>): String? {
        val uri = try { URI(value.trim()) } catch (_: Exception) { return null }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || host !in allowedHosts ||
            uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            (uri.port != -1 && uri.port != 443)) return null
        return "https://$host${uri.rawPath.orEmpty().trimEnd('/')}"
    }

    internal fun selectEndpoint(endpoints: List<String>?, index: Int, defaultUrl: String): String {
        if (endpoints.isNullOrEmpty()) return defaultUrl
        return endpoints[Math.floorMod(index, endpoints.size)]
    }
}
