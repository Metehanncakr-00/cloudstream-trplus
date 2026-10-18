package com.cloudstream.tr.dizimom

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DiziMomPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(DiziMom())
    }
}
