package com.cloudstream.tr.filmhane

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class FilmHanePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(FilmHane())
    }
}
