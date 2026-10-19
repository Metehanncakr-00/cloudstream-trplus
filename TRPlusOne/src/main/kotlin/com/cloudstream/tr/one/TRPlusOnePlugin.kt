package com.cloudstream.tr.one

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TRPlusOnePlugin : Plugin() {
    override fun load(context: Context) {
        OneIndex.init(context.applicationContext)
        OneEpisodeRegistry.init(context.applicationContext)
        OneSettings.init(context.applicationContext)
        super.load(context)
        openSettings = { showSettings(it) }
    }

    override fun load() {
        registerMainAPI(TRPlusOne())
        // Standalone plugin: register all custom extractors used across the 9 integrated providers.
        registerExtractorAPI(com.cloudstream.tr.animecix.TauVideo())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.VidmolyTrExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.VidmolyNetExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.VidmolyMeExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.RapidVidExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.CloseLoadExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.FilmizleInExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.PichiveExtractor())
    }

    private fun showSettings(context: Context) {
        val density = context.resources.displayMetrics.density
        fun dp(px: Int) = (density * px).toInt()
        val group = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }

        val providerList = listOf(
            "HDFilmCehennemi",
            "SezonlukDizi",
            "AnimeciX",
            "FilmMakinesi",
            "SinemaCX",
            "FullHDFilmizlesene",
            "Dizilla",
            "DiziYou",
            "Animeler"
        )

        val checkboxes = providerList.map { provider ->
            CheckBox(context).apply {
                text = provider
                textSize = 16f
                isFocusable = true
                isFocusableInTouchMode = true
                isChecked = OneSettings.isEnabled(provider)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnFocusChangeListener { view, focused ->
                    view.setBackgroundColor(if (focused) Color.argb(80, 110, 70, 190) else Color.TRANSPARENT)
                }
            }
        }
        checkboxes.forEach(group::addView)
        val scroll = ScrollView(context).apply { addView(group) }
        AlertDialog.Builder(context)
            .setTitle("TR+ ONE — Kaynaklar (Aşama 3 - Grup 1)")
            .setMessage("Aktif 9 sağlayıcı arasından istediğinizi açıp kapatabilirsiniz.")
            .setView(scroll)
            .setPositiveButton("Kaydet") { _, _ ->
                checkboxes.forEachIndexed { index, checkbox ->
                    OneSettings.setEnabled(providerList[index], checkbox.isChecked)
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }
}
