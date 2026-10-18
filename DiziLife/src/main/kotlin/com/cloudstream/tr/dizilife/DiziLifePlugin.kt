package com.cloudstream.tr.dizilife

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DiziLifePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(DiziLife())
    }
}
