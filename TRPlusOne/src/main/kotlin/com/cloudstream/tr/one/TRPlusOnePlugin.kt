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
            .setTitle("TR+ ONE v4 — Ayarlar ve Kaynaklar")
            .setMessage("Aktif sağlayıcıları yönetebilir veya Tanılama ekranından canlı olayları inceleyebilirsiniz.")
            .setView(scroll)
            .setPositiveButton("Kaydet") { _, _ ->
                checkboxes.forEachIndexed { index, checkbox ->
                    OneSettings.setEnabled(providerList[index], checkbox.isChecked)
                }
            }
            .setNeutralButton("Tanılama") { _, _ ->
                showDiagnosticsDialog(context)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun showDiagnosticsDialog(context: Context) {
        val density = context.resources.displayMetrics.density
        fun dp(px: Int) = (density * px).toInt()

        val logsText = OneDiagnostics.report()

        val textView = android.widget.TextView(context).apply {
            text = logsText
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val scroll = ScrollView(context).apply {
            addView(textView)
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.argb(220, 20, 20, 30))
        }

        AlertDialog.Builder(context)
            .setTitle("TR+ ONE — Canlı Tanılama Günlüğü")
            .setView(scroll)
            .setPositiveButton("Kopyala") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("TR+ ONE Diagnostics", logsText)
                clipboard?.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Tanılama günlüğü panoya kopyalandı.", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Temizle") { _, _ ->
                OneDiagnostics.clear()
                android.widget.Toast.makeText(context, "Günlükler temizlendi.", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Kapat", null)
            .show()
    }
}
