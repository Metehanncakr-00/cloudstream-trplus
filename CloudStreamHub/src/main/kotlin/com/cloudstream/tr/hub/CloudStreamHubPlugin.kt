package com.cloudstream.tr.hub

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodecList
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.cloudstream.tr.core.diagnostics.DiagnosticLogger
import com.cloudstream.tr.core.resolvers.DebridConfig
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

internal object HubSpinnerStylePolicy {
    data class Palette(
        val containerBgHex: String,
        val containerBorderHex: String,
        val selectedTextHex: String,
        val dropdownBgHex: String,
        val dropdownTextHex: String,
        val focusedBgHex: String,
        val focusedTextHex: String
    )

    fun paletteFor(isDark: Boolean, accentHex: String = "#2196F3"): Palette {
        return if (isDark) {
            Palette(
                containerBgHex = "#2C2C2E",
                containerBorderHex = "#5A5A5E",
                selectedTextHex = "#FFFFFF",
                dropdownBgHex = "#252528",
                dropdownTextHex = "#FFFFFF",
                focusedBgHex = accentHex,
                focusedTextHex = "#FFFFFF"
            )
        } else {
            Palette(
                containerBgHex = "#EFEFF4",
                containerBorderHex = "#B5B5BD",
                selectedTextHex = "#111111",
                dropdownBgHex = "#FFFFFF",
                dropdownTextHex = "#111111",
                focusedBgHex = accentHex,
                focusedTextHex = "#FFFFFF"
            )
        }
    }

    fun formatCollapsedLabel(rawLabel: String): String {
        return "▾  ${rawLabel.trim()}"
    }

    fun formatDropdownLabel(rawLabel: String, isSelected: Boolean): String {
        return if (isSelected) "✓  ${rawLabel.trim()}" else "    ${rawLabel.trim()}"
    }
}

internal object HubCheckBoxStylePolicy {
    data class Palette(
        val normalBgHex: String,
        val normalBorderHex: String,
        val normalTextHex: String,
        val focusedBgHex: String,
        val focusedBorderHex: String,
        val focusedTextHex: String,
        val checkedBoxTintHex: String,
        val uncheckedBoxTintHex: String,
        val focusedBoxTintHex: String
    )

    fun paletteFor(isDark: Boolean, accentHex: String = "#2196F3"): Palette {
        return if (isDark) {
            Palette(
                normalBgHex = "#252528",
                normalBorderHex = "#48484E",
                normalTextHex = "#EEEEEE",
                focusedBgHex = accentHex,
                focusedBorderHex = "#FFFFFF",
                focusedTextHex = "#FFFFFF",
                checkedBoxTintHex = accentHex,
                uncheckedBoxTintHex = "#B0B0B8",
                focusedBoxTintHex = "#FFFFFF"
            )
        } else {
            Palette(
                normalBgHex = "#F2F2F7",
                normalBorderHex = "#C5C5CE",
                normalTextHex = "#111111",
                focusedBgHex = accentHex,
                focusedBorderHex = "#111111",
                focusedTextHex = "#FFFFFF",
                checkedBoxTintHex = accentHex,
                uncheckedBoxTintHex = "#55555E",
                focusedBoxTintHex = "#FFFFFF"
            )
        }
    }

    fun stripStatePrefix(label: String): String {
        return label
            .removePrefix("▶ ")
            .removePrefix("[✓] ")
            .removePrefix("[ ] ")
            .trim()
    }

    fun formatStateLabel(rawLabel: String, isChecked: Boolean, hasFocus: Boolean = false): String {
        val clean = stripStatePrefix(rawLabel)
        val badge = if (isChecked) "[✓]" else "[ ]"
        return if (hasFocus) "▶ $badge  $clean" else "$badge  $clean"
    }
}


@CloudstreamPlugin
class CloudStreamHubPlugin : Plugin() {
    companion object {
        var pluginContext: Context?
            get() = HubContextHolder.pluginContext
            internal set(value) {
                HubContextHolder.pluginContext = value
            }
    }

    override fun load(context: Context) {
        HubContextHolder.pluginContext = context.applicationContext
        // Preferences must load before super.load registers the Hub provider.
        DebridConfig.initFromPreferences(context.applicationContext)
        HubPerformanceSettings.load(context.applicationContext)
        HubHomePreferences.load(context.applicationContext)
        HubAppearancePreferences.load(context.applicationContext)
        HubSettingsManager.load(context.applicationContext)
        super.load(context)
        openSettings = { settingsContext -> showHubSettings(settingsContext) }
        registerVideoClickAction(HubTorrentVlcAction())
        registerVideoClickAction(HubTorrentMxAction())
    }

    override fun load() {
        HubFederationLogcatBridge.install()
        registerMainAPI(CloudStreamHub())
        registerExtractorAPI(com.cloudstream.tr.animecix.TauVideo())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.VidmolyTrExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.VidmolyNetExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.VidmolyMeExtractor())
        registerExtractorAPI(com.cloudstream.tr.core.extractors.RapidVidExtractor())
    }


    private fun showHubSettings(context: Context) {
        DebridConfig.initFromPreferences(context)
        HubHomePreferences.load(context)
        HubAppearancePreferences.load(context)
        val settingsState = HubSettingsManager.load(context)
        val appearance = HubAppearancePreferences.snapshot()
        val themedContext = HubAppearancePreferences.settingsContext(context, appearance)
        val accentColor = HubAppearancePreferences.accentColor(appearance)
        val accentHex = HubAppearancePreferences.accents.firstOrNull { it.id == appearance.accent }?.hex ?: "#2196F3"

        fun dp(value: Int): Int = (value * themedContext.resources.displayMetrics.density).toInt()

        val isDark = appearance.mode == HubAppearancePreferences.DARK ||
            (appearance.mode == HubAppearancePreferences.SYSTEM &&
                (themedContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES)

        val primaryTextColor = if (isDark) Color.parseColor("#EEEEEE") else Color.parseColor("#212121")
        val secondaryTextColor = if (isDark) Color.parseColor("#AAAAAA") else Color.parseColor("#666666")
        val dialogBgColor = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF")
        val tabBgColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        val spinnerPalette = HubSpinnerStylePolicy.paletteFor(isDark, accentHex)
        val checkBoxPalette = HubCheckBoxStylePolicy.paletteFor(isDark, accentHex)

        // Dialog container layout
        val rootLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(dialogBgColor)
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }

        // Header Title
        val headerLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val titleText = TextView(themedContext).apply {
            text = "CloudStream TR+ Super Hub"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
        }
        val subtitleText = TextView(themedContext).apply {
            text = "Birleşik Katalog & Android TV Kaynak Merkezi"
            textSize = 12f
            setTextColor(secondaryTextColor)
        }
        headerLayout.addView(titleText)
        headerLayout.addView(subtitleText)
        rootLayout.addView(headerLayout)

        // Tab definitions
        val tabs = listOf(
            "Genel",
            "Katalog",
            "Dil",
            "Oynatma",
            "Performans",
            "Tanılama",
            "Görünüm"
        )
        val tabButtons = mutableListOf<Button>()
        val tabPanels = mutableListOf<LinearLayout>()
        var onTanilamaSelected: (() -> Unit)? = null

        val tabScroll = HorizontalScrollView(themedContext).apply {
            isFillViewport = true
            setPadding(0, 0, 0, dp(8))
        }
        val tabBar = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val contentContainer = FrameLayout(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun selectTab(selectedIndex: Int) {
            for (i in tabs.indices) {
                val isSelected = i == selectedIndex
                tabButtons[i].apply {
                    setBackgroundColor(if (isSelected) accentColor else tabBgColor)
                    setTextColor(if (isSelected) Color.WHITE else primaryTextColor)
                    setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                }
                tabPanels[i].visibility = if (isSelected) View.VISIBLE else View.GONE
            }
            if (tabs.getOrNull(selectedIndex) == "Tanılama") {
                onTanilamaSelected?.invoke()
            }
        }

        // Helper for TV D-Pad visible focus highlight
        fun applyTvFocus(view: View, isPrimaryButton: Boolean = false) {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.setBackgroundColor(if (isPrimaryButton) Color.WHITE else accentColor)
                    if (v is TextView) v.setTextColor(if (isPrimaryButton) Color.BLACK else Color.WHITE)
                } else {
                    v.setBackgroundColor(if (isPrimaryButton) accentColor else Color.TRANSPARENT)
                    if (v is TextView) v.setTextColor(if (isPrimaryButton) Color.WHITE else primaryTextColor)
                }
            }
        }

        // High-contrast, D-Pad focusable CheckBox factory for Android 12 TV
        val unfocusedBoxTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                Color.parseColor(checkBoxPalette.checkedBoxTintHex),
                Color.parseColor(checkBoxPalette.uncheckedBoxTintHex)
            )
        )
        val focusedBoxTintList = ColorStateList.valueOf(Color.parseColor(checkBoxPalette.focusedBoxTintHex))

        fun createTvCheckBox(
            rawLabel: String,
            initialChecked: Boolean,
            fontSizeSp: Float = 13.5f,
            rowContainer: View? = null,
            onToggle: (( CheckBox, Boolean) -> Unit)? = null
        ): CheckBox {
            val baseLabel = HubCheckBoxStylePolicy.stripStatePrefix(rawLabel)
            val normalDrawable = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor(checkBoxPalette.normalBgHex))
                setStroke(dp(1), Color.parseColor(checkBoxPalette.normalBorderHex))
            }
            val focusedDrawable = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor(checkBoxPalette.focusedBgHex))
                setStroke(dp(2), Color.parseColor(checkBoxPalette.focusedBorderHex))
            }

            return CheckBox(themedContext).apply {
                isChecked = initialChecked
                text = HubCheckBoxStylePolicy.formatStateLabel(baseLabel, initialChecked, hasFocus = false)
                textSize = fontSizeSp
                setTextColor(Color.parseColor(checkBoxPalette.normalTextHex))
                setTypeface(null, if (initialChecked) Typeface.BOLD else Typeface.NORMAL)
                buttonTintList = unfocusedBoxTintList
                background = normalDrawable
                isFocusable = true
                isFocusableInTouchMode = true
                setPadding(dp(10), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(3), 0, dp(3))
                }

                fun refreshVisualState(focused: Boolean, checked: Boolean) {
                    background = if (focused) focusedDrawable else normalDrawable
                    setTextColor(
                        if (focused) Color.parseColor(checkBoxPalette.focusedTextHex)
                        else Color.parseColor(checkBoxPalette.normalTextHex)
                    )
                    setTypeface(null, if (focused || checked) Typeface.BOLD else Typeface.NORMAL)
                    buttonTintList = if (focused) focusedBoxTintList else unfocusedBoxTintList
                    val formatted = HubCheckBoxStylePolicy.formatStateLabel(baseLabel, checked, focused)
                    if (text.toString() != formatted) {
                        text = formatted
                    }
                    rowContainer?.alpha = 1.0f
                }

                setOnFocusChangeListener { _, hasFocus ->
                    refreshVisualState(hasFocus, isChecked)
                }

                setOnCheckedChangeListener { buttonView, checked ->
                    onToggle?.invoke(this, checked)
                    refreshVisualState(buttonView.hasFocus(), isChecked)
                }
            }
        }

        // High-contrast, D-Pad accessible Spinner factory for Android 12 TV
        fun createTvSpinner(options: List<String>, initialSelection: Int): Spinner {
            val safeIndex = initialSelection.coerceIn(0, (options.size - 1).coerceAtLeast(0))
            val normalDrawable = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor(spinnerPalette.containerBgHex))
                setStroke(dp(1), Color.parseColor(spinnerPalette.containerBorderHex))
            }
            val focusedDrawable = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor(spinnerPalette.focusedBgHex))
                setStroke(dp(2), Color.WHITE)
            }

            return Spinner(themedContext, Spinner.MODE_DIALOG).apply {
                background = normalDrawable
                isFocusable = true
                isFocusableInTouchMode = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(2), 0, dp(4))
                }

                val customAdapter = object : ArrayAdapter<String>(
                    themedContext,
                    android.R.layout.simple_spinner_item,
                    options
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val tv = (convertView as? TextView) ?: TextView(themedContext)
                        val label = options.getOrElse(position) { "" }
                        tv.text = HubSpinnerStylePolicy.formatCollapsedLabel(label)
                        tv.textSize = 14f
                        tv.setTypeface(null, Typeface.BOLD)
                        tv.setSingleLine(true)
                        tv.ellipsize = TextUtils.TruncateAt.END
                        tv.setTextColor(
                            if (this@apply.hasFocus()) Color.parseColor(spinnerPalette.focusedTextHex)
                            else Color.parseColor(spinnerPalette.selectedTextHex)
                        )
                        tv.setBackgroundColor(Color.TRANSPARENT)
                        tv.setPadding(dp(12), dp(10), dp(12), dp(10))
                        return tv
                    }

                    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val tv = (convertView as? TextView) ?: TextView(themedContext)
                        val label = options.getOrElse(position) { "" }
                        val isSel = (position == this@apply.selectedItemPosition)
                        tv.text = HubSpinnerStylePolicy.formatDropdownLabel(label, isSel)
                        tv.textSize = 14f
                        tv.setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                        tv.setTextColor(
                            if (isSel) accentColor
                            else Color.parseColor(spinnerPalette.dropdownTextHex)
                        )
                        tv.setBackgroundColor(Color.parseColor(spinnerPalette.dropdownBgHex))
                        tv.setPadding(dp(16), dp(12), dp(16), dp(12))
                        return tv
                    }
                }

                adapter = customAdapter
                setSelection(safeIndex)

                setOnFocusChangeListener { _, hasFocus ->
                    background = if (hasFocus) focusedDrawable else normalDrawable
                    (selectedView as? TextView)?.setTextColor(
                        if (hasFocus) Color.parseColor(spinnerPalette.focusedTextHex)
                        else Color.parseColor(spinnerPalette.selectedTextHex)
                    )
                }
            }
        }

        // 1. Genel Tab Content
        val panelGenel = LinearLayout(themedContext).apply { orientation = LinearLayout.VERTICAL }
        val providerTitle = TextView(themedContext).apply {
            text = "Sağlayıcı Yönetimi"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            setPadding(0, dp(4), 0, dp(6))
        }
        panelGenel.addView(providerTitle)

        val discoverableProviders = CloudStreamProviderRegistryAdapter.getAllDiscoverableProviders()
        val disabledProvidersMutable = settingsState.disabledProviders.toMutableSet()

        val providerBtnRow = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }
        val btnEnableAll = Button(themedContext).apply {
            text = "Tümünü Aç"
            textSize = 12f
            applyTvFocus(this)
        }
        val btnDisableAll = Button(themedContext).apply {
            text = "Tümünü Kapat"
            textSize = 12f
            applyTvFocus(this)
        }
        providerBtnRow.addView(btnEnableAll)
        providerBtnRow.addView(btnDisableAll)
        panelGenel.addView(providerBtnRow)

        val providerChecks = mutableMapOf<String, CheckBox>()
        for (p in discoverableProviders) {
            val cb = createTvCheckBox(
                rawLabel = "${p.name} (${p.supportedTypes.joinToString { it.name }})",
                initialChecked = p.name !in disabledProvidersMutable
            ) { _, isChecked ->
                if (isChecked) disabledProvidersMutable.remove(p.name)
                else disabledProvidersMutable.add(p.name)
            }
            providerChecks[p.name] = cb
            panelGenel.addView(cb)
        }

        btnEnableAll.setOnClickListener {
            disabledProvidersMutable.clear()
            providerChecks.values.forEach { it.isChecked = true }
        }
        btnDisableAll.setOnClickListener {
            disabledProvidersMutable.addAll(discoverableProviders.map { it.name })
            providerChecks.values.forEach { it.isChecked = false }
        }

        val reportFailuresCheck = createTvCheckBox(
            rawLabel = "Başarısız sağlayıcıları tanılama kaydına ekle",
            initialChecked = settingsState.reportFailures
        )
        panelGenel.addView(reportFailuresCheck)

        val rdLabel = TextView(themedContext).apply {
            text = "Real-Debrid API Token (Opsiyonel):"
            textSize = 13f
            setTextColor(primaryTextColor)
            setPadding(0, dp(12), 0, dp(4))
        }
        val rdInput = EditText(themedContext).apply {
            hint = "Real-Debrid API Key giriniz..."
            setText(DebridConfig.realDebridToken ?: "")
            setTextColor(primaryTextColor)
            setHintTextColor(secondaryTextColor)
        }
        panelGenel.addView(rdLabel)
        panelGenel.addView(rdInput)

        val torboxLabel = TextView(themedContext).apply {
            text = "Torbox API Token (Opsiyonel):"
            textSize = 13f
            setTextColor(primaryTextColor)
            setPadding(0, dp(8), 0, dp(4))
        }
        val torboxInput = EditText(themedContext).apply {
            hint = "Torbox API Key giriniz..."
            setText(DebridConfig.torboxToken ?: "")
            setTextColor(primaryTextColor)
            setHintTextColor(secondaryTextColor)
        }
        panelGenel.addView(torboxLabel)
        panelGenel.addView(torboxInput)

        // 2. Katalog Tab Content
        val panelKatalog = LinearLayout(themedContext).apply { orientation = LinearLayout.VERTICAL }
        val catalogTitle = TextView(themedContext).apply {
            text = "Kategori Görünürlüğü ve Sıralama"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            setPadding(0, dp(4), 0, dp(6))
        }
        panelKatalog.addView(catalogTitle)

        val originalHome = HubHomePreferences.snapshot()
        val homeOrder = originalHome.order.toMutableList()
        val homeHidden = originalHome.hidden.toMutableSet()
        val homeRows = LinearLayout(themedContext).apply { orientation = LinearLayout.VERTICAL }
        val arrowWidth = dp(48)

        fun renderHomeRows(focusSectionId: String? = null, focusAction: String? = null) {
            homeRows.removeAllViews()
            var targetViewToFocus: View? = null
            for ((index, sectionId) in homeOrder.withIndex()) {
                val title = HubHomePreferences.sections.firstOrNull { it.id == sectionId }?.label ?: sectionId
                val row = LinearLayout(themedContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, dp(2), 0, dp(2))
                    }
                }
                val check = createTvCheckBox(
                    rawLabel = title,
                    initialChecked = sectionId !in homeHidden,
                    fontSizeSp = 13f,
                    rowContainer = row
                ) { cb, enabled ->
                    if (!enabled && homeHidden.size == homeOrder.size - 1) {
                        cb.isChecked = true
                        Toast.makeText(context, "En az bir kategori açık kalmalıdır.", Toast.LENGTH_SHORT).show()
                    } else if (enabled) {
                        homeHidden.remove(sectionId)
                    } else {
                        homeHidden.add(sectionId)
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(0, 0, dp(6), 0)
                    }
                }
                val up = Button(themedContext).apply {
                    text = "↑"
                    contentDescription = "$title yukarı"
                    isEnabled = index > 0
                    applyTvFocus(this)
                    setOnClickListener {
                        homeOrder.removeAt(index)
                        homeOrder.add(index - 1, sectionId)
                        renderHomeRows(focusSectionId = sectionId, focusAction = "up")
                    }
                }
                val down = Button(themedContext).apply {
                    text = "↓"
                    contentDescription = "$title aşağı"
                    isEnabled = index < homeOrder.lastIndex
                    applyTvFocus(this)
                    setOnClickListener {
                        homeOrder.removeAt(index)
                        homeOrder.add(index + 1, sectionId)
                        renderHomeRows(focusSectionId = sectionId, focusAction = "down")
                    }
                }
                row.addView(check)
                row.addView(up, LinearLayout.LayoutParams(arrowWidth, dp(44)))
                row.addView(down, LinearLayout.LayoutParams(arrowWidth, dp(44)))
                homeRows.addView(row)
                if (sectionId == focusSectionId) {
                    targetViewToFocus = when (focusAction) {
                        "up" -> if (up.isEnabled) up else down
                        "down" -> if (down.isEnabled) down else up
                        else -> check
                    }
                }
            }
            targetViewToFocus?.post { targetViewToFocus.requestFocus() }
        }
        renderHomeRows()
        panelKatalog.addView(homeRows)

        val catalogActionRow = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        val btnClearCache = Button(themedContext).apply {
            text = "Önbelleği Temizle"
            applyTvFocus(this)
            setOnClickListener {
                HubCatalogCache.clearAll(context)
                Toast.makeText(context, "Katalog önbelleği temizlendi.", Toast.LENGTH_SHORT).show()
            }
        }
        val btnResetCrawler = Button(themedContext).apply {
            text = "İndeks Cursor Sıfırla"
            applyTvFocus(this)
            setOnClickListener {
                HubCatalogCache.resetCrawlerCursors(context)
                Toast.makeText(context, "Tarama imleçleri sıfırlandı.", Toast.LENGTH_SHORT).show()
            }
        }
        catalogActionRow.addView(btnClearCache)
        catalogActionRow.addView(btnResetCrawler)
        panelKatalog.addView(catalogActionRow)

        // 3. Dil Tab Content
        val panelDil = LinearLayout(themedContext).apply { orientation = LinearLayout.VERTICAL }
        val audioTitle = TextView(themedContext).apply {
            text = "Ses ve Dil Tercihleri"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            setPadding(0, dp(4), 0, dp(6))
        }
        panelDil.addView(audioTitle)

        val audioValues = listOf("ALL", "TR_DUB", "ORIGINAL")
        val audioSpinner = createTvSpinner(
            options = listOf(
                "Otomatik (Türkçe öncelikli)",
                "Türkçe dublaj tercih et",
                "Bilinen Türkçe dışı ses (Orijinal) tercih et"
            ),
            initialSelection = audioValues.indexOf(DebridConfig.audioPreference).coerceAtLeast(0)
        )
        val audioHint = TextView(themedContext).apply {
            text = "Yalnızca doğrulanabilir ses sinyalleri Türkçe olarak kabul edilir. Bilinmeyen sesler hiçbir zaman Türkçe olarak varsayılmaz."
            textSize = 12f
            setTextColor(secondaryTextColor)
            setPadding(0, dp(4), 0, dp(12))
        }
        panelDil.addView(audioSpinner)
        panelDil.addView(audioHint)

        val unknownAudioValues = listOf("DEPRIORITIZE", "NEUTRAL")
        val unknownAudioLabel = TextView(themedContext).apply {
            text = "Bilinmeyen ses kaynaklarına yaklaşım:"
            textSize = 13f
            setTextColor(primaryTextColor)
        }
        val unknownAudioSpinner = createTvSpinner(
            options = listOf("Düşük öncelik ver (Önerilen)", "Normal değerlendir"),
            initialSelection = unknownAudioValues.indexOf(settingsState.unknownAudioPolicy).coerceAtLeast(0)
        )
        panelDil.addView(unknownAudioLabel)
        panelDil.addView(unknownAudioSpinner)

        // 4. Oynatma Tab Content
        val panelOynatma = LinearLayout(themedContext).apply { orientation = LinearLayout.VERTICAL }
        val playTitle = TextView(themedContext).apply {
            text = "Oynatma ve Kaynak Sıralaması"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            setPadding(0, dp(4), 0, dp(6))
        }
        panelOynatma.addView(playTitle)

        val profileValues = listOf(
            HubPerformanceSettings.AUTO,
            HubPerformanceSettings.COMPATIBLE,
            HubPerformanceSettings.FEATURE
        )
        val profileSpinner = createTvSpinner(
            options = listOf("Otomatik", "Uyumluluk öncelikli (Düşük RAM/TV)", "4K / REMUX öncelikli"),
            initialSelection = profileValues.indexOf(HubPerformanceSettings.profile).coerceAtLeast(0)
        )
        panelOynatma.addView(profileSpinner)

        val hardwareInfo = TextView(themedContext).apply {
            textSize = 12f
            setTextColor(secondaryTextColor)
            text = "Donanımsal HEVC: " + try {
                if (MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                    !info.isEncoder && info.supportedTypes.any { type -> type.equals("video/hevc", true) }
                }) "Destekleniyor" else "Bildirilmiyor"
            } catch (_: Exception) { "Sorgulanamadı" }
            setPadding(0, dp(4), 0, dp(8))
        }
        panelOynatma.addView(hardwareInfo)

        val torrentCheck = createTvCheckBox(
            rawLabel = "P2P Torrent Kaynaklarını Göster (Torrentio/YTS)",
            initialChecked = DebridConfig.enableTorrentSources
        )
        val cdnCheck = createTvCheckBox(
            rawLabel = "4KHDHub Doğrudan CDN Akışlarını Önceliklendir",
            initialChecked = DebridConfig.prioritizeDirectCdn
        )
        val torrentAtBottomCheck = createTvCheckBox(
            rawLabel = "Torrent kaynaklarını listenin sonuna yerleştir",
            initialChecked = settingsState.torrentsAtBottom
        )
        panelOynatma.addView(torrentCheck)
        panelOynatma.addView(cdnCheck)
        panelOynatma.addView(torrentAtBottomCheck)

        // 5. Performans Tab Content
        val panelPerformans = LinearLayout(themedContext).apply { orientation = LinearLayout.VERTICAL }
        val perfTitle = TextView(themedContext).apply {
            text = "Performans ve Zaman Aşımı Profili"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            setPadding(0, dp(4), 0, dp(6))
        }
        panelPerformans.addView(perfTitle)

        val perfModeValues = listOf(
            HubSettingsManager.PERF_MODE_FAST,
            HubSettingsManager.PERF_MODE_BALANCED,
            HubSettingsManager.PERF_MODE_COMPREHENSIVE
        )
        val perfModeSpinner = createTvSpinner(
            options = listOf(
                "Hızlı (Düşük RAM / Erken sonuç)",
                "Dengeli (Varsayılan TV deneyimi)",
                "Kapsamlı (Bütün kaynakları derin tara)"
            ),
            initialSelection = perfModeValues.indexOf(settingsState.perfMode).coerceAtLeast(0)
        )
        panelPerformans.addView(perfModeSpinner)

        val concurrencyValues = listOf(2, 4, 6)
        val concurrencyLabel = TextView(themedContext).apply {
            text = "Eşzamanlı sağlayıcı sorgu limiti:"
            textSize = 13f
            setTextColor(primaryTextColor)
            setPadding(0, dp(8), 0, dp(2))
        }
        val concurrencySpinner = createTvSpinner(
            options = listOf("2 eşzamanlı", "4 eşzamanlı (Önerilen)", "6 eşzamanlı"),
            initialSelection = concurrencyValues.indexOf(settingsState.maxConcurrency).coerceAtLeast(1)
        )
        panelPerformans.addView(concurrencyLabel)
        panelPerformans.addView(concurrencySpinner)

        val timeoutValues = listOf(15, 30, 45)
        val timeoutLabel = TextView(themedContext).apply {
            text = "Sağlayıcı arama zaman aşımı:"
            textSize = 13f
            setTextColor(primaryTextColor)
            setPadding(0, dp(8), 0, dp(2))
        }
        val timeoutSpinner = createTvSpinner(
            options = listOf("15 saniye", "30 saniye (Varsayılan)", "45 saniye"),
            initialSelection = timeoutValues.indexOf(settingsState.searchTimeoutSec).coerceAtLeast(1)
        )
        panelPerformans.addView(timeoutLabel)
        panelPerformans.addView(timeoutSpinner)

        val ttlValues = listOf(15, 60, 360, 1440)
        val ttlLabel = TextView(themedContext).apply {
            text = "Katalog önbellek süresi (TTL):"
            textSize = 13f
            setTextColor(primaryTextColor)
            setPadding(0, dp(8), 0, dp(2))
        }
        val ttlSpinner = createTvSpinner(
            options = listOf("15 dakika", "1 saat (Önerilen)", "6 saat", "24 saat"),
            initialSelection = ttlValues.indexOf(settingsState.cacheTtlMin).coerceAtLeast(1)
        )
        panelPerformans.addView(ttlLabel)
        panelPerformans.addView(ttlSpinner)

        val enableBackgroundCrawlerCheck = createTvCheckBox(
            rawLabel = "Arka planda otomatik katalog indeksleme (TV'de akıcılık için kapalı önerilir)",
            initialChecked = settingsState.enableBackgroundCrawler
        )
        panelPerformans.addView(enableBackgroundCrawlerCheck)

        // 6. Tanılama Tab Content
        val panelTanilama = LinearLayout(themedContext).apply { orientation = LinearLayout.VERTICAL }
        val diagTitle = TextView(themedContext).apply {
            text = "Tanılama ve Kayıt Güvenliği"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            setPadding(0, dp(4), 0, dp(6))
        }
        panelTanilama.addView(diagTitle)

        val diagSecurityNote = TextView(themedContext).apply {
            text = "API anahtarları, şifreler ve özel kimlik bilgileri tanılama raporlarına veya günlüklere asla yazılmaz; dışa aktarılırken otomatik olarak maskelenir."
            textSize = 12f
            setTextColor(secondaryTextColor)
            setPadding(0, dp(2), 0, dp(8))
        }
        panelTanilama.addView(diagSecurityNote)

        val diagLiveBox = TextView(themedContext).apply {
            text = HubSettingsManager.getSanitizedDiagnosticsReport()
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(primaryTextColor)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor(checkBoxPalette.normalBgHex))
                setStroke(dp(1), Color.parseColor(checkBoxPalette.normalBorderHex))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(4), 0, dp(8))
            }
        }
        onTanilamaSelected = {
            diagLiveBox.text = HubSettingsManager.getSanitizedDiagnosticsReport()
        }

        val btnShowDiag = Button(themedContext).apply {
            text = "Performans ve Kaynak Ölçümlerini Göster"
            applyTvFocus(this)
            setOnClickListener {
                diagLiveBox.text = HubSettingsManager.getSanitizedDiagnosticsReport()
                AlertDialog.Builder(themedContext)
                    .setTitle("CloudStream TR+ Performans & Katalog Raporu")
                    .setMessage(HubSettingsManager.getSanitizedDiagnosticsReport())
                    .setPositiveButton("Kapat", null)
                    .setNeutralButton("Kayıtları Temizle") { _, _ ->
                        HubPerformanceTelemetry.clear()
                        HubCatalogDiagnostics.clear()
                        diagLiveBox.text = HubSettingsManager.getSanitizedDiagnosticsReport()
                        Toast.makeText(context, "Kayıtlar temizlendi.", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }
        val btnClearDiagInline = Button(themedContext).apply {
            text = "Tanılama Kayıtlarını Temizle"
            applyTvFocus(this)
            setOnClickListener {
                HubPerformanceTelemetry.clear()
                HubCatalogDiagnostics.clear()
                diagLiveBox.text = HubSettingsManager.getSanitizedDiagnosticsReport()
                Toast.makeText(context, "Tanılama kayıtları temizlendi.", Toast.LENGTH_SHORT).show()
            }
        }
        panelTanilama.addView(btnShowDiag)
        panelTanilama.addView(btnClearDiagInline)
        panelTanilama.addView(diagLiveBox)

        // 7. Görünüm Tab Content
        val panelGorunum = LinearLayout(themedContext).apply { orientation = LinearLayout.VERTICAL }
        val appearanceTitle = TextView(themedContext).apply {
            text = "Görünüm ve Renk Teması"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            setPadding(0, dp(4), 0, dp(6))
        }
        panelGorunum.addView(appearanceTitle)

        val dialogThemeLabel = TextView(themedContext).apply {
            text = "Pencere teması:"
            textSize = 13f
            setTextColor(primaryTextColor)
        }
        val themeModes = HubAppearancePreferences.modes
        val modeSpinner = createTvSpinner(
            options = listOf("Sistem varsayılanı", "Açık", "Koyu"),
            initialSelection = themeModes.indexOf(appearance.mode).coerceAtLeast(0)
        )
        val accentLabel = TextView(themedContext).apply {
            text = "Vurgu rengi:"
            textSize = 13f
            setTextColor(primaryTextColor)
            setPadding(0, dp(8), 0, dp(2))
        }
        val accentSpinner = createTvSpinner(
            options = HubAppearancePreferences.accents.map { it.label },
            initialSelection = HubAppearancePreferences.accents.indexOfFirst { it.id == appearance.accent }.coerceAtLeast(0)
        )
        val appearanceHint = TextView(themedContext).apply {
            text = "Tema yalnız CloudStreamHub penceresini etkiler. Değişiklikler için ayar penceresini yeniden açın."
            textSize = 12f
            setTextColor(secondaryTextColor)
            setPadding(0, dp(4), 0, 0)
        }
        panelGorunum.addView(dialogThemeLabel)
        panelGorunum.addView(modeSpinner)
        panelGorunum.addView(accentLabel)
        panelGorunum.addView(accentSpinner)
        panelGorunum.addView(appearanceHint)

        // Assemble all tab panels
        tabPanels.addAll(
            listOf(
                panelGenel,
                panelKatalog,
                panelDil,
                panelOynatma,
                panelPerformans,
                panelTanilama,
                panelGorunum
            )
        )
        for (panel in tabPanels) {
            contentContainer.addView(panel)
        }

        // Build Tab Bar Buttons
        for (i in tabs.indices) {
            val tabName = tabs[i]
            val btn = Button(themedContext).apply {
                text = tabName
                textSize = 13f
                applyTvFocus(this)
                setOnClickListener { selectTab(i) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(44)
                ).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
            }
            tabButtons.add(btn)
            tabBar.addView(btn)
        }
        tabScroll.addView(tabBar)
        rootLayout.addView(tabScroll)

        // Content ScrollView with layout weight 1.0f (guarantees middle content scrolls independently)
        val contentScroll = ScrollView(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            isFocusable = false
            addView(contentContainer)
        }
        rootLayout.addView(contentScroll)

        // FIXED BOTTOM ACTION BAR (PERMANENT AND VISIBLE ON ANDROID TV, NEVER CLIPPED)
        val bottomBar = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        var dialogRef: AlertDialog? = null

        val btnResetDefaults = Button(themedContext).apply {
            text = "Varsayılana Dön"
            textSize = 13f
            applyTvFocus(this)
            setOnClickListener {
                HubSettingsManager.resetToDefaults(context)
                Toast.makeText(context, "Varsayılan ayarlar yüklendi. Pencereyi yeniden açın.", Toast.LENGTH_SHORT).show()
                dialogRef?.dismiss()
            }
        }

        val btnCancel = Button(themedContext).apply {
            text = "İptal"
            textSize = 14f
            applyTvFocus(this)
            setOnClickListener {
                dialogRef?.dismiss()
            }
        }

        val btnSave = Button(themedContext).apply {
            text = "Kaydet"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(accentColor)
            setTextColor(Color.WHITE)
            applyTvFocus(this, isPrimaryButton = true)
            setOnClickListener {
                // 1. Debrid Config
                DebridConfig.saveToPreferences(
                    context = context,
                    rdToken = rdInput.text.toString(),
                    torbox = torboxInput.text.toString(),
                    enableTorrents = torrentCheck.isChecked,
                    prioritizeCdn = cdnCheck.isChecked,
                    audioPref = audioValues[audioSpinner.selectedItemPosition]
                )

                // 2. Playback Profile
                HubPerformanceSettings.save(context, profileValues[profileSpinner.selectedItemPosition])

                // 3. Home Category Order & Visibility
                HubHomePreferences.save(context, HubHomePreferences.Config(homeOrder, homeHidden))

                // 4. Appearance
                HubAppearancePreferences.save(
                    context,
                    HubAppearancePreferences.Config(
                        themeModes[modeSpinner.selectedItemPosition],
                        HubAppearancePreferences.accents[accentSpinner.selectedItemPosition].id
                    )
                )

                // 5. Unified Settings State
                val nextState = HubSettingsManager.State(
                    disabledProviders = disabledProvidersMutable.toSet(),
                    reportFailures = reportFailuresCheck.isChecked,
                    unknownAudioPolicy = unknownAudioValues[unknownAudioSpinner.selectedItemPosition],
                    torrentsAtBottom = torrentAtBottomCheck.isChecked,
                    perfMode = perfModeValues[perfModeSpinner.selectedItemPosition],
                    searchTimeoutSec = timeoutValues[timeoutSpinner.selectedItemPosition],
                    maxConcurrency = concurrencyValues[concurrencySpinner.selectedItemPosition],
                    cacheTtlMin = ttlValues[ttlSpinner.selectedItemPosition],
                    enableBackgroundCrawler = enableBackgroundCrawlerCheck.isChecked
                )
                HubSettingsManager.save(context, nextState)

                Toast.makeText(context, "Ayarlar kaydedildi.", Toast.LENGTH_LONG).show()
                dialogRef?.dismiss()
            }
        }

        bottomBar.addView(btnResetDefaults, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)))
        bottomBar.addView(btnCancel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)).apply {
            setMargins(dp(6), 0, dp(6), 0)
        })
        bottomBar.addView(btnSave, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)))

        rootLayout.addView(bottomBar)

        // Select initial tab
        selectTab(0)

        // Create Dialog and constrain window bounds for Android TV
        val dialog = AlertDialog.Builder(themedContext)
            .setView(rootLayout)
            .create()

        dialogRef = dialog
        dialog.show()

        dialog.window?.let { window ->
            val dm = themedContext.resources.displayMetrics
            val width = (dm.widthPixels * 0.92).toInt().coerceAtLeast(dp(360))
            val height = (dm.heightPixels * 0.88).toInt().coerceAtLeast(dp(440))
            window.setLayout(width, height)
            window.setGravity(Gravity.CENTER)
        }
    }
}

private object HubFederationLogcatBridge {
    private var installed = false

    fun install() {
        synchronized(this) {
            if (installed) return

            DiagnosticLogger.addListener { event ->
                if (event.message.startsWith("Federation result:")) {
                    val message = event.message
                    val result = when {
                        "no confident match" in message -> "no-confident-match"
                        "provider.load returned no response" in message -> "load-no-response"
                        "no movie or requested episode link data" in message -> "no-link-data"
                        "confident " in message -> "confident-match"
                        else -> Regex(
                            "raw=(\\d+), providerEmitted=(\\d+).*valid=(\\d+), indeterminate=(\\d+), invalid=(\\d+), duplicates=(\\d+), dropped=(\\d+)"
                        ).find(message)?.let { match ->
                            val (raw, emitted, valid, indeterminate, invalid, duplicates, dropped) = match.destructured
                            "streams raw=$raw emitted=$emitted valid=$valid indeterminate=$indeterminate invalid=$invalid duplicates=$duplicates dropped=$dropped"
                        } ?: "federation-result"
                    }
                    Log.i("CloudStreamHub", "${event.provider} [${event.stage}] $result")
                }
            }
            installed = true
        }
    }
}
