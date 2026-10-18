package com.cloudstream.tr.webdramaturkey

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class WebDramaTurkeyPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(WebDramaTurkey())
    }
}
