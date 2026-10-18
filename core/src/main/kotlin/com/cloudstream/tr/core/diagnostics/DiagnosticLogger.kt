package com.cloudstream.tr.core.diagnostics

import java.net.URI

enum class DiagnosticStage {
    DOMAIN,
    HOMEPAGE,
    SEARCH,
    LOAD,
    EPISODE_DISCOVERY,
    EMBED_DISCOVERY,
    EXTRACTOR,
    STREAM_PREFLIGHT,
    MANIFEST,
    SEGMENT,
    PLAYBACK
}

enum class DiagnosticCategory {
    NETWORK,
    HTTP,
    AUTH,
    REDIRECT,
    ANTI_BOT,
    SOURCE_DISCOVERY,
    EXTRACTOR,
    MANIFEST,
    CONTAINER,
    DECODER,
    DRM,
    SUBTITLE,
    UNKNOWN
}

data class DiagnosticEvent(
    val provider: String,
    val stage: DiagnosticStage,
    val category: DiagnosticCategory,
    val host: String?,
    val httpStatus: Int? = null,
    val media3ErrorCode: Int? = null,
    val errorClass: String? = null,
    val message: String,
    val durationMs: Long? = null,
    val retryCount: Int = 0
)

object DiagnosticLogger {
    private val listeners = mutableListOf<(DiagnosticEvent) -> Unit>()

    fun addListener(listener: (DiagnosticEvent) -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: (DiagnosticEvent) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun log(event: DiagnosticEvent) {
        synchronized(listeners) {
            for (listener in listeners) listener(event)
        }
    }

    fun log(
        provider: String,
        stage: DiagnosticStage,
        category: DiagnosticCategory,
        message: String,
        url: String? = null,
        httpStatus: Int? = null,
        media3ErrorCode: Int? = null,
        throwable: Throwable? = null,
        durationMs: Long? = null,
        retryCount: Int = 0
    ) {
        val host = url?.let { extractHost(it) }
        val event = DiagnosticEvent(
            provider = provider,
            stage = stage,
            category = category,
            host = host,
            httpStatus = httpStatus,
            media3ErrorCode = media3ErrorCode,
            errorClass = throwable?.javaClass?.simpleName,
            message = redactSensitiveInfo(message),
            durationMs = durationMs,
            retryCount = retryCount
        )
        log(event)
    }

    fun extractHost(url: String): String {
        return try {
            URI(url).host ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    fun redactUrl(url: String): String {
        return try {
            val uri = URI(url)
            val cleanQuery = if (uri.query != null) "?[REDACTED]" else ""
            "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}${uri.path}$cleanQuery"
        } catch (_: Exception) {
            "[REDACTED_URL]"
        }
    }

    fun redactSensitiveInfo(input: String): String {
        return input
            .replace(Regex("""(?i)(api[_-]?key|token|password|auth|secret|hash)=([a-zA-Z0-9_\-\.]{6,})"""), "$1=[REDACTED]")
            .replace(Regex("""Bearer\s+[a-zA-Z0-9_\-\.]{15,}"""), "Bearer [REDACTED]")
    }
}
