package com.cloudstream.tr.cizgimax

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class CizgiMaxPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(CizgiMax())
    }
}
