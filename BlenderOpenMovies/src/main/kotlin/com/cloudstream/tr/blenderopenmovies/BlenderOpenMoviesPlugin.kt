package com.cloudstream.tr.blenderopenmovies

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class BlenderOpenMoviesPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(BlenderOpenMovies())
    }
}
