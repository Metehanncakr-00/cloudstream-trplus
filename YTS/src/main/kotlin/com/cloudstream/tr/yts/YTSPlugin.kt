package com.cloudstream.tr.yts

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class YTSPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(YTS())
    }
}
