package com.cloudstream.tr.animecix

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimeciXPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(AnimeciX())
        registerExtractorAPI(TauVideo())
    }
}
