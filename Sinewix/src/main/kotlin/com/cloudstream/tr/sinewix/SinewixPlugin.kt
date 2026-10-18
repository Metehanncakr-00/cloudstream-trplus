package com.cloudstream.tr.sinewix

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class SinewixPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Sinewix())
    }
}
