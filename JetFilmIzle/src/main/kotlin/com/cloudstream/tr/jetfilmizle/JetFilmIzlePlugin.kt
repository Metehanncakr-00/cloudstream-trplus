package com.cloudstream.tr.jetfilmizle

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class JetFilmIzlePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(JetFilmIzle())
    }
}
