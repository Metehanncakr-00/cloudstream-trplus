package com.cloudstream.tr.dizigecesi

import com.cloudstream.tr.core.extractors.VidmolyMeExtractor
import com.cloudstream.tr.core.extractors.VidmolyNetExtractor
import com.cloudstream.tr.core.extractors.VidmolyTrExtractor
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DizigecesiPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Dizigecesi())
        registerExtractorAPI(VidmolyTrExtractor())
        registerExtractorAPI(VidmolyNetExtractor())
        registerExtractorAPI(VidmolyMeExtractor())
    }
}

