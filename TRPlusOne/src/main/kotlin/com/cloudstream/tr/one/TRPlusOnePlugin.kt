package com.cloudstream.tr.one

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TRPlusOnePlugin : Plugin() {
    override fun load(context: Context) {
        OneIndex.init(context.applicationContext)
        OneSettings.init(context.applicationContext)
        super.load(context)
        openSettings = { showSettings(it) }
    }

    override fun load() {
        registerMainAPI(TRPlusOne())
        // A standalone plugin cannot rely on extractor registration by sibling plugins.
        registerExtractorAPI(com.cloudstream.tr.animecix.TauVideo())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.VidmolyTrExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.VidmolyNetExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.VidmolyMeExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.RapidVidExtractor())
    }

    private fun showSettings(context: Context) {
        val density = context.resources.displayMetrics.density
        fun dp(px: Int) = (density * px).toInt()
        val group = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val checkboxes = listOf("HDFilmCehennemi", "SezonlukDizi", "AnimeciX").map { provider ->
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
            .setTitle("TR+ ONE — Kaynaklar (Aşama 1)")
            .setMessage("Yalnızca doğrulanmış üç prototip kaynağı. Kaynakları açıp kapatabilirsiniz.")
            .setView(scroll)
            .setPositiveButton("Kaydet") { _, _ ->
                checkboxes.forEachIndexed { index, checkbox ->
                    OneSettings.setEnabled(listOf("HDFilmCehennemi", "SezonlukDizi", "AnimeciX")[index], checkbox.isChecked)
                }
            }
            .setNegativeButton("İptal", null)
            .setNeutralButton("Tanılama") { _, _ ->
                AlertDialog.Builder(context)
                    .setTitle("TR+ ONE — Çalışma Kayıtları")
                    .setMessage((OneIndex.stats() + "\n\n" + OneDiagnostics.report()).takeLast(8000))
                    .setPositiveButton("Kapat", null)
                    .show()
            }
            .show()
    }
}
