package com.cloudstream.tr.ddizi

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DDiziPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(DDizi())
    }
}
