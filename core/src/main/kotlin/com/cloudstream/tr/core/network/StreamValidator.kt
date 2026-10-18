package com.cloudstream.tr.core.network

import com.cloudstream.tr.core.diagnostics.DiagnosticCategory
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.diagnostics.DiagnosticStage
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream

enum class ValidationStatus {
    VALID,
    INVALID,
    INDETERMINATE
}

data class PreflightResult(
    val status: ValidationStatus,
    val streamType: ExtractorLinkType,
    val detectedMime: String? = null,
    val statusCode: Int = 0,
    val failureReason: String? = null,
    val media3ErrorCode: Int? = null,
    val latencyMs: Long = 0L,
    val hasMediaEvidence: Boolean = false,
    val languageEvidence: com.cloudstream.tr.core.model.ManifestLanguageEvidence = com.cloudstream.tr.core.model.ManifestLanguageEvidence()
) {
    val isValid: Boolean get() = status == ValidationStatus.VALID
}

data class StreamHttpResponse(
    val code: Int,
    val contentType: String? = null,
    val openStream: () -> InputStream
)

fun interface StreamHttpTransport {
    suspend fun get(url: String, headers: Map<String, String>): StreamHttpResponse
}

object StreamValidator {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val MAX_READ_BYTES = 8192

    private val defaultTransport = StreamHttpTransport { url, headers ->
        val resp = app.get(url, headers = headers)
        StreamHttpResponse(
            code = resp.code,
            contentType = resp.headers["Content-Type"] ?: resp.headers["content-type"],
            openStream = { resp.body.byteStream() }
        )
    }

    var transport: StreamHttpTransport = defaultTransport

    fun resetTransport() {
        transport = defaultTransport
    }

    /**
     * Pure HTTP status classifier:
     * - 200..299 -> VALID (continue media analysis)
     * - 400, 401, 403, 404, 405, 406, 410, 422 -> INVALID
     * - 408, 425, 429, 500..599 -> INDETERMINATE (retryable/transient)
     * - Other 4xx -> INVALID
     */
    fun classifyHttpStatus(code: Int): ValidationStatus {
        return when {
            code in 200..299 -> ValidationStatus.VALID
            code in listOf(400, 401, 403, 404, 405, 406, 410, 422) -> ValidationStatus.INVALID
            code in listOf(408, 425, 429) || code in 500..599 -> ValidationStatus.INDETERMINATE
            code in 400..499 -> ValidationStatus.INVALID
            else -> ValidationStatus.INDETERMINATE
        }
    }

    /**
     * Reads up to maxBytes from an InputStream safely without loading full media files into memory.
     */
    fun readBoundedBytes(inputStream: InputStream, maxBytes: Int = MAX_READ_BYTES): ByteArray {
        val buffer = ByteArray(maxBytes)
        var totalRead = 0
        try {
            while (totalRead < maxBytes) {
                val read = inputStream.read(buffer, totalRead, maxBytes - totalRead)
                if (read == -1) break
                totalRead += read
            }
        } catch (_: Exception) {
            // Return whatever was read before exception
        }
        return if (totalRead == maxBytes) buffer else buffer.copyOf(totalRead)
    }

    /**
     * Checks if the given byte array matches known container/manifest magic headers.
     */
    fun inferTypeFromBytes(bytes: ByteArray): ExtractorLinkType? {
        if (bytes.isEmpty()) return null

        val textHeader = String(bytes.take(256).toByteArray(), Charsets.UTF_8).trimStart()
        if (textHeader.startsWith("#EXTM3U") || textHeader.contains("#EXT-X-STREAM-INF") || textHeader.contains("#EXT-X-TARGETDURATION")) {
            return ExtractorLinkType.M3U8
        }

        // EBML (Matroska / WebM): 0x1A, 0x45, 0xDF, 0xA3
        if (bytes.size >= 4 &&
            bytes[0] == 0x1A.toByte() &&
            bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() &&
            bytes[3] == 0xA3.toByte()
        ) {
            return ExtractorLinkType.VIDEO
        }

        // MP4: 'ftyp' at offset 4..7 or 'moov'
        if (bytes.size >= 8) {
            val ftyp = String(bytes.sliceArray(4..7), Charsets.US_ASCII)
            if (ftyp == "ftyp" || ftyp == "moov") {
                return ExtractorLinkType.VIDEO
            }
        }

        // MPEG-TS sync byte 0x47 (packet size 188 bytes; offset 0, 188, and 376 if available)
        if (bytes.size >= 189 && bytes[0] == 0x47.toByte() && bytes[188] == 0x47.toByte()) {
            if (bytes.size >= 377 && bytes[376] != 0x47.toByte()) {
                return null
            }
            return ExtractorLinkType.VIDEO
        }

        return null
    }

    /**
     * Infers stream type from explicit hint, content-type header, and URL path.
     */
    fun inferTypeFromMetadata(contentType: String?, url: String): ExtractorLinkType {
        val ct = contentType?.lowercase() ?: ""
        val cleanUrl = url.lowercase().substringBefore("?")

        if (ct.contains("application/vnd.apple.mpegurl") ||
            ct.contains("application/x-mpegurl") ||
            cleanUrl.endsWith(".m3u8") ||
            cleanUrl.contains("/hls/")
        ) {
            return ExtractorLinkType.M3U8
        }

        if (ct.contains("application/dash+xml") || cleanUrl.endsWith(".mpd")) {
            return ExtractorLinkType.DASH
        }

        if (ct.contains("video/") ||
            cleanUrl.endsWith(".mp4") ||
            cleanUrl.endsWith(".mkv") ||
            cleanUrl.endsWith(".webm")
        ) {
            return ExtractorLinkType.VIDEO
        }

        return ExtractorLinkType.VIDEO
    }

    private fun hasExplicitMediaMetadata(contentType: String?): Boolean {
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase() ?: ""

        return ct.startsWith("video/") ||
            ct == "application/vnd.apple.mpegurl" ||
            ct == "application/x-mpegurl" ||
            ct == "application/dash+xml"
    }

    /**
     * Determines if a response is an invalid media response (HTML error, bot challenge, JSON error).
     */
    fun isInvalidMediaBody(bodySample: String, contentType: String?): Boolean {
        val trimmed = bodySample.trimStart()
        if (trimmed.startsWith("#EXTM3U") || (trimmed.startsWith("<?xml") && trimmed.contains("<MPD"))) {
            return false
        }

        val lower = bodySample.lowercase().trim()
        val ct = contentType?.lowercase() ?: ""


        if (ct.contains("text/html") ||
            lower.startsWith("<!doctype html") ||
            lower.startsWith("<html") ||
            lower.contains("just a moment...") ||
            lower.contains("cloudflare") ||
            lower.contains("security error") ||
            lower.contains("access denied") ||
            lower.contains("403 forbidden") ||
            lower.contains("404 not found")
        ) {
            return true
        }

        if (ct.contains("application/json") &&
            (lower.contains("\"error\"") || lower.contains("\"message\"") || lower.contains("\"status\":false"))
        ) {
            return true
        }

        return false
    }

    /**
     * Executes a bounded preflight check on a stream URL before emitting ExtractorLink.
     * Drops HTML error pages, Cloudflare challenges, and expired links that would cause 3003.
     */
    suspend fun validateStream(
        url: String,
        headers: Map<String, String> = emptyMap(),
        provider: String = "Generic",
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): PreflightResult {
        if (url.isBlank() || !url.startsWith("http")) {
            return PreflightResult(
                status = ValidationStatus.INVALID,
                streamType = ExtractorLinkType.VIDEO,
                failureReason = "INVALID_URL_FORMAT",
                media3ErrorCode = 2004
            )
        }

        return try {
            withTimeoutOrNull(timeoutMs) {
                val reqHeaders = headers.toMutableMap().apply {
                    putIfAbsent("User-Agent", SafeHttpClient.DEFAULT_USER_AGENT)
                    putIfAbsent("Range", "bytes=0-1024")
                }

                val startTime = System.currentTimeMillis()
                var response = transport.get(url, reqHeaders)
                var code = response.code

                // Dedicated 416 branch: retry ONCE without Range header in case server rejects ranges
                if (code == 416) {
                    val retryHeaders = reqHeaders.toMutableMap().apply { remove("Range") }
                    response = transport.get(url, retryHeaders)
                    code = response.code
                }

                val latency = System.currentTimeMillis() - startTime

                val status = classifyHttpStatus(code)
                if (status != ValidationStatus.VALID) {
                    val media3Err = if (status == ValidationStatus.INVALID) 2004 else 2001
                    DiagnosticLogger.log(
                        provider = provider,
                        stage = DiagnosticStage.STREAM_PREFLIGHT,
                        category = DiagnosticCategory.HTTP,
                        message = "Preflight HTTP $code received (classified as $status) for: ${DiagnosticLogger.redactUrl(url)}",
                        url = url,
                        httpStatus = code,
                        media3ErrorCode = media3Err
                    )
                    return@withTimeoutOrNull PreflightResult(
                        status = status,
                        streamType = ExtractorLinkType.VIDEO,
                        statusCode = code,
                        failureReason = "HTTP_STATUS_$code",
                        media3ErrorCode = media3Err,
                        latencyMs = latency
                    )
                }

                val ct = response.contentType
                // Bounded body read: read at most MAX_READ_BYTES directly from byteStream
                val bytes = response.openStream().use { stream ->
                    readBoundedBytes(stream, MAX_READ_BYTES)
                }
                val bodySample = String(bytes.take(256).toByteArray(), Charsets.UTF_8)

                if (isInvalidMediaBody(bodySample, ct)) {
                    DiagnosticLogger.log(
                        provider = provider,
                        stage = DiagnosticStage.STREAM_PREFLIGHT,
                        category = DiagnosticCategory.CONTAINER,
                        message = "Preflight rejected HTML/challenge response (3003 prevention) for: ${DiagnosticLogger.redactUrl(url)}",
                        url = url,
                        httpStatus = code,
                        media3ErrorCode = 3003
                    )
                    return@withTimeoutOrNull PreflightResult(
                        status = ValidationStatus.INVALID,
                        streamType = ExtractorLinkType.VIDEO,
                        statusCode = code,
                        failureReason = "INVALID_MEDIA_CONTAINER_HTML",
                        media3ErrorCode = 3003
                    )
                }

                val inferredFromBytes = inferTypeFromBytes(bytes)
                val finalType = inferredFromBytes ?: inferTypeFromMetadata(ct, url)
                val hasMediaEvidence = inferredFromBytes != null || hasExplicitMediaMetadata(ct)

                PreflightResult(
                    status = ValidationStatus.VALID,
                    streamType = finalType,
                    detectedMime = ct,
                    statusCode = code,
                    latencyMs = latency,
                    hasMediaEvidence = hasMediaEvidence,
                    languageEvidence = com.cloudstream.tr.core.model.ManifestLanguageParser.parse(
                        bytes, bytes.size >= MAX_READ_BYTES || code == 206
                    )
                )
            } ?: run {
                // Timeout elapsed: Fail-closed with INDETERMINATE status (never emit blindly)
                val fallbackType = inferTypeFromMetadata(null, url)
                PreflightResult(
                    status = ValidationStatus.INDETERMINATE,
                    streamType = fallbackType,
                    failureReason = "PREFLIGHT_TIMEOUT",
                    media3ErrorCode = 2004
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLogger.log(
                provider = provider,
                stage = DiagnosticStage.STREAM_PREFLIGHT,
                category = DiagnosticCategory.NETWORK,
                message = "Preflight exception: ${e.message}",
                url = url,
                throwable = e,
                media3ErrorCode = 2001
            )
            // Exception: Fail-closed with INDETERMINATE status (never emit blindly)
            PreflightResult(
                status = ValidationStatus.INDETERMINATE,
                streamType = inferTypeFromMetadata(null, url),
                failureReason = "PREFLIGHT_EXCEPTION",
                media3ErrorCode = 2001
            )
        }
    }
}
