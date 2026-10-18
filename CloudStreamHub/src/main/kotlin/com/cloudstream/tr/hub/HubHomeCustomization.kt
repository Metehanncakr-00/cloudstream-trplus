package com.cloudstream.tr.hub

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.widget.CompoundButton
import android.widget.TextView
import com.lagradost.cloudstream3.MainPageData

internal data class HubHomeSection(val id: String, val label: String)

/**
 * The host owns its global home and color theme. These preferences customize
 * only the CloudStreamHub provider and its own plugin settings views.
 */
internal object HubHomePreferences {
    private const val PREFS = "CloudStreamHub_Home_v1"
    private const val KEY_ORDER = "order"
    private const val KEY_HIDDEN = "hidden"

    val sections = listOf(
        HubHomeSection("all_content", UnifiedCategories.ALL_CONTENT),
        HubHomeSection("movies", UnifiedCategories.MOVIES),
        HubHomeSection("series", UnifiedCategories.SERIES),
        HubHomeSection("turkish_series", UnifiedCategories.TURKISH_SERIES),
        HubHomeSection("nostalgic_turkish_series", UnifiedCategories.NOSTALGIC_TURKISH_SERIES),
        HubHomeSection("cartoons", UnifiedCategories.CARTOONS),
        HubHomeSection("cartoon_series", UnifiedCategories.CARTOON_SERIES),
        HubHomeSection("animation_movies", UnifiedCategories.ANIMATION_MOVIES),
        HubHomeSection("anime", UnifiedCategories.ANIME),
        HubHomeSection("documentaries", UnifiedCategories.DOCUMENTARIES),
        HubHomeSection("yesilcam", UnifiedCategories.YESILCAM),
        HubHomeSection("tr_dub", UnifiedCategories.TR_DUB),
        HubHomeSection("tr_sub", UnifiedCategories.TR_SUB),
        HubHomeSection("new_releases", UnifiedCategories.NEW_RELEASES),
        HubHomeSection("popular", UnifiedCategories.POPULAR),
        HubHomeSection("continue_watching", UnifiedCategories.CONTINUE_WATCHING)
    )

    private val legacyAliases = mapOf(
        "trending" to "all_content",
        "popular_movies" to "movies",
        "popular_series" to "series",
        "rated_movies" to "popular",
        "theaters" to "new_releases",
        "documentary_movies" to "documentaries",
        "documentary_series" to "documentaries"
    )

    private val legacyLabelToSectionId = mapOf(
        "Günün Trendleri" to "all_content",
        "Popüler Filmler" to "movies",
        "Popüler Diziler" to "series",
        "En Çok Oy Alan Filmler" to "popular",
        "Vizyondaki Filmler" to "new_releases",
        "Belgesel Filmler" to "documentaries",
        "Belgesel Dizileri" to "documentaries",
        "Anime ve Animasyon" to "anime"
    )

    private val identifiers = sections.map { it.id }.toSet()

    data class Config(val order: List<String>, val hidden: Set<String>)
    @Volatile private var config = normalized(emptyList(), emptySet())

    /** Preserve every known section exactly once; do not permit an empty homepage. */
    fun normalized(rawOrder: List<String>, rawHidden: Set<String>): Config {
        val mappedOrder = rawOrder.map { legacyAliases[it] ?: it }
        val mappedHidden = rawHidden.map { legacyAliases[it] ?: it }.toSet()

        val order = (mappedOrder.filter { it in identifiers } + sections.map { it.id }).distinct()
        val hidden = mappedHidden.intersect(identifiers).let { valid ->
            if (valid.size == identifiers.size) valid - order.first() else valid
        }
        return Config(order, hidden)
    }

    @Synchronized fun snapshot(): Config = config.copy(order = config.order.toList(), hidden = config.hidden.toSet())

    @Synchronized fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        config = normalized(
            prefs.getString(KEY_ORDER, "").orEmpty().split(",").filter(String::isNotBlank),
            prefs.getString(KEY_HIDDEN, "").orEmpty().split(",").filter(String::isNotBlank).toSet()
        )
    }

    @Synchronized fun save(context: Context, value: Config) {
        val next = normalized(value.order, value.hidden)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, next.order.joinToString(","))
            .putString(KEY_HIDDEN, next.hidden.joinToString(","))
            .apply()
        config = next
    }

    fun canonicalCategoryName(label: String): String {
        val trimmed = label.trim()
        val legacyId = legacyLabelToSectionId[trimmed] ?: legacyAliases[trimmed]
        if (legacyId != null) {
            sections.firstOrNull { it.id == legacyId }?.let { return it.label }
        }
        return trimmed
    }

    @Synchronized fun isVisible(label: String): Boolean {
        val canonical = canonicalCategoryName(label)
        val sectionId = sections.firstOrNull { it.label == canonical || it.id == canonical }?.id
        return sectionId != null && sectionId !in config.hidden
    }

    fun filtered(pages: List<MainPageData>, value: Config = snapshot()): List<MainPageData> {
        val selected = normalized(value.order, value.hidden)
        val byLabel = pages.associateBy { it.name }
        return selected.order.mapNotNull { id ->
            if (id in selected.hidden) null else sections.firstOrNull { it.id == id }
                ?.let { byLabel[it.label] }
        }
    }
}

internal object HubAppearancePreferences {
    private const val PREFS = "CloudStreamHub_Appearance_v1"
    private const val KEY_MODE = "dialog_mode"
    private const val KEY_ACCENT = "accent"

    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"

    data class Accent(val id: String, val label: String, val hex: String)
    val accents = listOf(
        Accent("BLUE", "Mavi", "#2196F3"),
        Accent("PURPLE", "Mor", "#9C27B0"),
        Accent("TEAL", "Turkuaz", "#009688"),
        Accent("GREEN", "Yeşil", "#43A047"),
        Accent("ORANGE", "Turuncu", "#F57C00"),
        Accent("RED", "Kırmızı", "#E53935")
    )
    val modes = listOf(SYSTEM, LIGHT, DARK)

    data class Config(val mode: String = SYSTEM, val accent: String = "BLUE")
    @Volatile private var config = Config()

    fun normalized(mode: String?, accent: String?): Config = Config(
        mode?.takeIf(modes::contains) ?: SYSTEM,
        accent?.takeIf { id -> accents.any { it.id == id } } ?: "BLUE"
    )

    fun snapshot(): Config = config

    @Synchronized fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        config = normalized(prefs.getString(KEY_MODE, SYSTEM), prefs.getString(KEY_ACCENT, "BLUE"))
    }

    @Synchronized fun save(context: Context, value: Config) {
        val next = normalized(value.mode, value.accent)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, next.mode).putString(KEY_ACCENT, next.accent).apply()
        config = next
    }

    fun settingsContext(context: Context, value: Config = snapshot()): Context = when (value.mode) {
        LIGHT -> ContextThemeWrapper(context, android.R.style.Theme_Material_Light_Dialog_Alert)
        DARK -> ContextThemeWrapper(context, android.R.style.Theme_Material_Dialog_Alert)
        else -> context
    }

    fun accentColor(value: Config = snapshot()): Int = Color.parseColor(
        accents.firstOrNull { it.id == value.accent }?.hex ?: accents.first().hex
    )

    fun tint(text: TextView, value: Config = snapshot()) = text.setTextColor(accentColor(value))

    fun tint(check: CompoundButton, value: Config = snapshot()) {
        check.buttonTintList = ColorStateList.valueOf(accentColor(value))
    }
}
