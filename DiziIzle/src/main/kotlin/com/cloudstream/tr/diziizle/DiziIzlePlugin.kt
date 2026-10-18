package com.cloudstream.tr.diziizle

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DiziIzlePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(DiziIzle())
    }
}
