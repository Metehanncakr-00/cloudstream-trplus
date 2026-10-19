package com.cloudstream.tr.one

import android.content.Context
import java.util.ArrayDeque

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
    private const val MAX_LOGS = 300

    @Synchronized fun record(source: String, detail: String) {
        val clean = detail.replace(Regex("(?i)(api[_-]?key|token|authorization|pass|password)[=:][^\\s&]+"), "$1=[REDACTED]")
        logs.addLast("[$source] $clean")
        while (logs.size > MAX_LOGS) logs.removeFirst()
    }

    @Synchronized fun log(event: String, detail: String) {
        record(event, detail)
        runCatching {
            android.util.Log.d("TR+ONE", "[$event] $detail")
        }
    }

    @Synchronized fun report(): String = if (logs.isEmpty()) "Henüz ölçüm yok." else logs.joinToString("\n")

    @Synchronized fun clear() {
        logs.clear()
    }

    @Synchronized fun history(): List<String> = logs.toList()
}
