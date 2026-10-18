package com.cloudstream.tr.fullhdfilmizlesene

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class FullHDFilmizlesenePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(FullHDFilmizlesene())
    }
}
