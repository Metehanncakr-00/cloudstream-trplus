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
}
