package com.cloudstream.tr.startv

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class StarTVPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(StarTV())
    }
}
