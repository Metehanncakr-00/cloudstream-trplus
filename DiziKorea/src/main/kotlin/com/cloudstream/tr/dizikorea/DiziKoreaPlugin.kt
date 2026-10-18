package com.cloudstream.tr.dizikorea

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DiziKoreaPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(DiziKorea())
    }
}
