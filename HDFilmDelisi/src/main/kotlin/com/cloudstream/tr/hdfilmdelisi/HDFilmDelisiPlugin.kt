package com.cloudstream.tr.hdfilmdelisi

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class HDFilmDelisiPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(HDFilmDelisi())
    }
}
