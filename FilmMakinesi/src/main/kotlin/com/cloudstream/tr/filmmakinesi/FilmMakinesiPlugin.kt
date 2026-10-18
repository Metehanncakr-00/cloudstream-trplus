package com.cloudstream.tr.filmmakinesi

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class FilmMakinesiPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(FilmMakinesi())
    }
}
