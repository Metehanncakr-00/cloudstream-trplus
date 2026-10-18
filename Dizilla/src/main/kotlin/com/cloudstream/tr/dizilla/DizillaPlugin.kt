package com.cloudstream.tr.dizilla

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DizillaPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Dizilla())
    }
}
