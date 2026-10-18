package com.cloudstream.tr.core.network

import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

/** Shares one in-flight value without tying its lifetime to a waiter's Job. */
internal class InFlightRequestDeduplicator<K, T> {
    private val inFlight = ConcurrentHashMap<K, CompletableDeferred<T>>()

    suspend fun execute(key: K, fetch: suspend () -> T): T {
        val candidate = CompletableDeferred<T>()
        val existing = inFlight.putIfAbsent(key, candidate)
        val result = existing ?: candidate
        val ownsRequest = existing == null

        if (ownsRequest) {
            try {
                result.complete(fetch())
            } catch (error: Throwable) {
                result.completeExceptionally(error)
                throw error
            } finally {
                inFlight.remove(key, result)
            }
        }
        return result.await()
    }
}

internal data class GetRequestKey(val url: String, val headers: Map<String, String>, val cacheTime: Int)

internal suspend fun <T> safeHttpCall(
    request: suspend () -> T,
    onFailure: (Exception) -> Unit
): T? {
    return try {
        request()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onFailure(error)
        null
    }
}

/**
 * SafeHttpClient provides resilient networking for CloudStreamTR providers:
 * - Request deduplication for simultaneous in-flight identical requests (RD-005)
 * - Safe error handling preserving privacy and contract semantics
 * - Standardized headers and browser-like user agent
 */
object SafeHttpClient {
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val requests = InFlightRequestDeduplicator<GetRequestKey, NiceResponse>()

    fun defaultHeaders(referer: String? = null, isAjax: Boolean = false): Map<String, String> {
        val map = mutableMapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
            "Accept" to "*/*"
        )
        if (referer != null) {
            map["Referer"] = referer
        }
        if (isAjax) {
            map["X-Requested-With"] = "XMLHttpRequest"
        }
        return map
    }

    /**
     * Executes an HTTP GET request with in-flight deduplication.
     * If another coroutine is already fetching this URL with the same headers and cache time,
     * awaits the same result.
     */
    suspend fun getDedup(
        url: String,
        headers: Map<String, String> = emptyMap(),
        cacheTime: Int = 0
    ): NiceResponse {
        val mergedHeaders = defaultHeaders().toMutableMap().apply { putAll(headers) }
        val key = GetRequestKey(url, mergedHeaders.toMap(), cacheTime)
        return requests.execute(key) {
            app.get(url, headers = mergedHeaders, cacheTime = cacheTime)
        }
    }

    /**
     * Executes safe GET returning null on 404, DNS failure, or connection drop
     */
    suspend fun safeGet(
        url: String,
        headers: Map<String, String> = emptyMap(),
        provider: String = "Generic"
    ): NiceResponse? {
        return safeHttpCall(request = {
            val mergedHeaders = defaultHeaders().toMutableMap().apply { putAll(headers) }
            app.get(url, headers = mergedHeaders)
        }) { e ->
            com.cloudstream.tr.core.diagnostics.DiagnosticLogger.log(
                provider = provider,
                stage = com.cloudstream.tr.core.diagnostics.DiagnosticStage.DOMAIN,
                category = com.cloudstream.tr.core.diagnostics.DiagnosticCategory.NETWORK,
                message = "safeGet failed: ${e.message}",
                url = url,
                throwable = e
            )
        }
    }

    /**
     * Executes safe POST returning null on network or parsing failure
     */
    suspend fun safePost(
        url: String,
        headers: Map<String, String> = emptyMap(),
        data: Map<String, String> = emptyMap(),
        jsonString: String? = null,
        provider: String = "Generic"
    ): NiceResponse? {
        return safeHttpCall(request = {
            val mergedHeaders = defaultHeaders().toMutableMap().apply { putAll(headers) }
            if (jsonString != null) {
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = jsonString.toRequestBody(mediaType)
                app.post(url, headers = mergedHeaders, requestBody = body)
            } else {
                app.post(url, headers = mergedHeaders, data = data)
            }
        }) { e ->
            com.cloudstream.tr.core.diagnostics.DiagnosticLogger.log(
                provider = provider,
                stage = com.cloudstream.tr.core.diagnostics.DiagnosticStage.DOMAIN,
                category = com.cloudstream.tr.core.diagnostics.DiagnosticCategory.NETWORK,
                message = "safePost failed: ${e.message}",
                url = url,
                throwable = e
            )
        }
    }
}
