package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubTvNavigationTest {

    private val hub = CloudStreamHub()

    @Test
    fun testTvLowRamBufferCapEnforced() {
        val largeList = (1..400).map { i ->
            hub.newMovieSearchResponse("Movie $i", "https://site.com/movie/$i", TvType.Movie)
        }

        HubCatalogCache.put("Tüm İçerikler", 1, largeList, hasNext = true)

        val cached = HubCatalogCache.get("Tüm İçerikler", 1)
        assertNotNull(cached)
        // Memory cap is 300 items per category (expanded from 150 to prevent data loss while bounding memory)
        assertEquals(300, cached?.first?.size)
    }

    @Test
    fun testTvDpadHighContrastThemes() {
        val lightTheme = HubAppearancePreferences.normalized(HubAppearancePreferences.LIGHT, "BLUE")
        val darkTheme = HubAppearancePreferences.normalized(HubAppearancePreferences.DARK, "ORANGE")

        assertEquals("LIGHT", lightTheme.mode)
        assertEquals("BLUE", lightTheme.accent)
        val blueAccent = HubAppearancePreferences.accents.first { it.id == "BLUE" }
        assertEquals("#2196F3", blueAccent.hex)

        assertEquals("DARK", darkTheme.mode)
        assertEquals("ORANGE", darkTheme.accent)
        val orangeAccent = HubAppearancePreferences.accents.first { it.id == "ORANGE" }
        assertEquals("#F57C00", orangeAccent.hex)
    }

    @Test
    fun testSixteenCategoriesIntegrity() {
        assertEquals(16, UnifiedCategories.ALL.size)
        assertTrue(UnifiedCategories.ALL.contains("Tüm İçerikler"))
        assertTrue(UnifiedCategories.ALL.contains("Filmler"))
        assertTrue(UnifiedCategories.ALL.contains("Diziler"))
        assertTrue(UnifiedCategories.ALL.contains("Türk Dizileri"))
        assertTrue(UnifiedCategories.ALL.contains("Eski / Nostaljik Türk Dizileri"))
        assertTrue(UnifiedCategories.ALL.contains("Çizgi Filmler"))
        assertTrue(UnifiedCategories.ALL.contains("Çizgi Diziler"))
        assertTrue(UnifiedCategories.ALL.contains("Animasyon Filmleri"))
        assertTrue(UnifiedCategories.ALL.contains("Anime"))
        assertTrue(UnifiedCategories.ALL.contains("Belgeseller"))
        assertTrue(UnifiedCategories.ALL.contains("Yeşilçam"))
        assertTrue(UnifiedCategories.ALL.contains("Türkçe Dublaj"))
        assertTrue(UnifiedCategories.ALL.contains("Türkçe Altyazı"))
        assertTrue(UnifiedCategories.ALL.contains("Yeni Eklenenler"))
        assertTrue(UnifiedCategories.ALL.contains("Popüler İçerikler"))
        assertTrue(UnifiedCategories.ALL.contains("İzlemeye Devam Et"))
    }

    @Test
    fun testSpinnerStylePolicyProducesVisibleHighContrastTextInDarkAndLightModes() {
        // v43 Stabilization Regression:
        // Spinner selected value and dropdown items must have high-contrast text and non-blank labels in both Dark and Light themes
        val darkPalette = HubSpinnerStylePolicy.paletteFor(isDark = true, accentHex = "#2196F3")
        val lightPalette = HubSpinnerStylePolicy.paletteFor(isDark = false, accentHex = "#F57C00")

        // Dark mode: white text (#FFFFFF) on dark container (#2C2C2E) and dark dropdown (#252528)
        assertEquals("#FFFFFF", darkPalette.selectedTextHex)
        assertEquals("#2C2C2E", darkPalette.containerBgHex)
        assertEquals("#FFFFFF", darkPalette.dropdownTextHex)
        assertEquals("#252528", darkPalette.dropdownBgHex)
        assertTrue("Selected text color must never equal container background in dark mode", darkPalette.selectedTextHex != darkPalette.containerBgHex)
        assertTrue("Dropdown text color must never equal dropdown background in dark mode", darkPalette.dropdownTextHex != darkPalette.dropdownBgHex)

        // Light mode: near-black text (#111111) on light container (#EFEFF4) and white dropdown (#FFFFFF)
        assertEquals("#111111", lightPalette.selectedTextHex)
        assertEquals("#EFEFF4", lightPalette.containerBgHex)
        assertEquals("#111111", lightPalette.dropdownTextHex)
        assertEquals("#FFFFFF", lightPalette.dropdownBgHex)
        assertTrue("Selected text color must never equal container background in light mode", lightPalette.selectedTextHex != lightPalette.containerBgHex)
        assertTrue("Dropdown text color must never equal dropdown background in light mode", lightPalette.dropdownTextHex != lightPalette.dropdownBgHex)

        // Formatted labels must be non-blank and include visual indicators for TV D-Pad users
        val collapsed = HubSpinnerStylePolicy.formatCollapsedLabel("Dengeli (Varsayılan TV deneyimi)")
        val dropdownSelected = HubSpinnerStylePolicy.formatDropdownLabel("Dengeli (Varsayılan TV deneyimi)", isSelected = true)
        val dropdownUnselected = HubSpinnerStylePolicy.formatDropdownLabel("Hızlı (Düşük RAM / Erken sonuç)", isSelected = false)

        assertTrue(collapsed.isNotBlank() && collapsed.contains("Dengeli"))
        assertTrue(dropdownSelected.isNotBlank() && dropdownSelected.startsWith("✓"))
        assertTrue(dropdownUnselected.isNotBlank() && dropdownUnselected.contains("Hızlı"))
    }

    @Test
    fun testCheckBoxStylePolicyProducesDistinctFocusedAndUnfocusedVisualStatesAndCheckedBadges() {
        // v44 Android TV Focus Fix Regression:
        // CheckBox style/focus policy must produce clearly distinct visual states when focused vs unfocused,
        // and clearly distinguishable checked vs unchecked indicators in both Dark and Light themes.
        val darkCb = HubCheckBoxStylePolicy.paletteFor(isDark = true, accentHex = "#2196F3")
        val lightCb = HubCheckBoxStylePolicy.paletteFor(isDark = false, accentHex = "#F57C00")

        // 1. Focused vs unfocused container background and border must differ in both themes
        assertTrue("Dark mode focused bg must differ from normal bg", darkCb.focusedBgHex != darkCb.normalBgHex)
        assertTrue("Dark mode focused border must differ from normal border", darkCb.focusedBorderHex != darkCb.normalBorderHex)
        assertEquals("#FFFFFF", darkCb.focusedBorderHex)
        assertEquals("#FFFFFF", darkCb.focusedTextHex)
        assertTrue("Dark mode normal text must contrast with normal bg", darkCb.normalTextHex != darkCb.normalBgHex)

        assertTrue("Light mode focused bg must differ from normal bg", lightCb.focusedBgHex != lightCb.normalBgHex)
        assertTrue("Light mode focused border must differ from normal border", lightCb.focusedBorderHex != lightCb.normalBorderHex)
        assertEquals("#111111", lightCb.focusedBorderHex)
        assertEquals("#FFFFFF", lightCb.focusedTextHex)
        assertTrue("Light mode normal text must contrast with normal bg", lightCb.normalTextHex != lightCb.normalBgHex)

        // 2. Checked vs unchecked box tint must differ when unfocused, and focused box tint must contrast with focused bg
        assertTrue("Dark checked tint must differ from unchecked tint", darkCb.checkedBoxTintHex != darkCb.uncheckedBoxTintHex)
        assertTrue("Light checked tint must differ from unchecked tint", lightCb.checkedBoxTintHex != lightCb.uncheckedBoxTintHex)
        assertTrue("Focused box tint must contrast with focused background", darkCb.focusedBoxTintHex != darkCb.focusedBgHex)

        // 3. Formatted labels must distinguish (checked, focused), (checked, unfocused), (unchecked, focused), (unchecked, unfocused)
        val raw = "FilmMakinesi (Movie, TvSeries)"
        val checkedUnfocused = HubCheckBoxStylePolicy.formatStateLabel(raw, isChecked = true, hasFocus = false)
        val checkedFocused = HubCheckBoxStylePolicy.formatStateLabel(raw, isChecked = true, hasFocus = true)
        val uncheckedUnfocused = HubCheckBoxStylePolicy.formatStateLabel(raw, isChecked = false, hasFocus = false)
        val uncheckedFocused = HubCheckBoxStylePolicy.formatStateLabel(raw, isChecked = false, hasFocus = true)

        assertTrue(checkedUnfocused.startsWith("[✓]"))
        assertTrue(checkedFocused.startsWith("▶ [✓]"))
        assertTrue(uncheckedUnfocused.startsWith("[ ]"))
        assertTrue(uncheckedFocused.startsWith("▶ [ ]"))
        assertTrue(checkedFocused != checkedUnfocused)
        assertTrue(uncheckedFocused != uncheckedUnfocused)
        assertTrue(checkedFocused != uncheckedFocused)
        assertTrue(checkedUnfocused != uncheckedUnfocused)

        // Idempotent prefix stripping so repeated focus/check toggles never duplicate prefixes
        assertEquals(
            checkedFocused,
            HubCheckBoxStylePolicy.formatStateLabel(checkedUnfocused, isChecked = true, hasFocus = true)
        )
    }
}

