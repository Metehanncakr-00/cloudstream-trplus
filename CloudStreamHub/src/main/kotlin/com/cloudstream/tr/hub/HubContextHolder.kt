package com.cloudstream.tr.hub

import android.content.Context

/**
 * Isolated Context container to allow safe access to Android application context
 * from catalog, merger, and registry layers without triggering class-loading of
 * host-only Plugin classes during JVM unit testing.
 */
object HubContextHolder {
    @Volatile
    var pluginContext: Context? = null
}
