package com.cloudstream.tr.animeler

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimelerPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Animeler())
    }
}
