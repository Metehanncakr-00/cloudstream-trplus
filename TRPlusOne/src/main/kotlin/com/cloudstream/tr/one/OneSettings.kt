package com.cloudstream.tr.one
import java.util.ArrayDeque

import android.content.Context

object OneSettings {
    private const val FILE = "TRPlusOneSettings_v1"
    private val disabled = mutableSetOf<String>()
    private var appContext: Context? = null
    @Synchronized fun init(context: Context) {
        appContext = context.applicationContext
        val pref = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        disabled.clear()
        disabled.addAll(pref.getStringSet("disabled", emptySet()) ?: emptySet())
    }
    @Synchronized fun isEnabled(provider: String): Boolean = provider !in disabled
    @Synchronized fun setEnabled(provider: String, enabled: Boolean) {
        if (enabled) disabled.remove(provider) else disabled.add(provider)
        appContext?.getSharedPreferences(FILE, Context.MODE_PRIVATE)?.edit()
            ?.putStringSet("disabled", disabled.toSet())?.apply()
    }
}

object OneDiagnostics {
    private val logs = ArrayDeque<String>()
    @Synchronized fun record(source: String, detail: String) {
        val clean = detail.replace(Regex("(?i)(api[_-]?key|token|authorization)[=:][^\\s&]+"), "$1=[REDACTED]")
        logs.addLast("[$source] $clean")
        while (logs.size > 100) logs.removeFirst()
    }
    @Synchronized fun report(): String = if (logs.isEmpty()) "Henüz ölçüm yok." else logs.joinToString("\n")
}
