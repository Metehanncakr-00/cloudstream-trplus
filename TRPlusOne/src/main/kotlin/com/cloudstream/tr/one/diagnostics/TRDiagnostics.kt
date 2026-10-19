package com.cloudstream.tr.one.diagnostics

import java.util.concurrent.ConcurrentHashMap

data class ProviderStat(
    val providerId: String,
    var totalFetched: Int = 0,
    var errorCount: Int = 0,
    var lastElapsedMs: Long = 0L,
    var lastStatus: String = "IDLE",
    var lastUpdatedMs: Long = System.currentTimeMillis()
)

object TRDiagnostics {
    private val stats = ConcurrentHashMap<String, ProviderStat>()

    fun recordSuccess(providerId: String, count: Int, elapsedMs: Long) {
        val stat = stats.computeIfAbsent(providerId) { ProviderStat(providerId) }
        synchronized(stat) {
            stat.totalFetched += count
            stat.lastElapsedMs = elapsedMs
            stat.lastStatus = "OK ($count items)"
            stat.lastUpdatedMs = System.currentTimeMillis()
        }
    }

    fun recordError(providerId: String, error: String, elapsedMs: Long) {
        val stat = stats.computeIfAbsent(providerId) { ProviderStat(providerId) }
        synchronized(stat) {
            stat.errorCount++
            stat.lastElapsedMs = elapsedMs
            stat.lastStatus = "ERROR: $error"
            stat.lastUpdatedMs = System.currentTimeMillis()
        }
    }

    fun getStats(): Map<String, ProviderStat> = stats.toMap()

    fun reset() {
        stats.clear()
    }
}
