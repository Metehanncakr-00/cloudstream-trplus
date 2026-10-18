package com.cloudstream.tr.hub

import com.lagradost.cloudstream3.MainPageData
import org.junit.Assert.*
import org.junit.Test

class HubHomeCustomizationTest {
    @Test fun defaultContainsAllSixteenHomeSectionsInExistingOrder() {
        val pages = HubHomePreferences.sections.map { MainPageData(it.label, "/fixture/" + it.id) }
        val actual = HubHomePreferences.filtered(pages, HubHomePreferences.normalized(emptyList(), emptySet()))
        assertEquals(pages, actual)
        assertEquals(16, actual.size)
    }

    @Test fun moviesCanBeHiddenWithoutTouchingOtherSections() {
        val pages = HubHomePreferences.sections.map { MainPageData(it.label, "/fixture/" + it.id) }
        val actual = HubHomePreferences.filtered(pages, HubHomePreferences.normalized(
            emptyList(), setOf("movies")
        ))
        assertEquals(15, actual.size)
        assertFalse(actual.any { it.name == UnifiedCategories.MOVIES })
        assertTrue(actual.any { it.name == UnifiedCategories.SERIES })
    }

    @Test fun reorderIsStableEvenWithUnknownOrRepeatedIdentifiers() {
        val config = HubHomePreferences.normalized(
            listOf("anime", "movies", "anime", "UNRECOGNIZED"),
            setOf("series", "MISSING")
        )
        assertEquals("anime", config.order.first())
        assertEquals("movies", config.order[1])
        assertEquals(16, config.order.distinct().size)
        assertEquals(setOf("series"), config.hidden)
        val pages = HubHomePreferences.sections.map { MainPageData(it.label, "/fixture/" + it.id) }
        val ordered = HubHomePreferences.filtered(pages, config)
        assertEquals(UnifiedCategories.ANIME, ordered.first().name)
        assertEquals(UnifiedCategories.MOVIES, ordered[1].name)
        assertEquals(15, ordered.size)
    }

    @Test fun hidingAllAutomaticallyKeepsOneCategoryAvailable() {
        val all = HubHomePreferences.sections.map { it.id }.toSet()
        val config = HubHomePreferences.normalized(emptyList(), all)
        assertEquals(15, config.hidden.size)
        assertTrue(config.order.first() !in config.hidden)
    }

    @Test fun themeChoicesAreRestrictedToKnownModeAndAccent() {
        assertEquals(HubAppearancePreferences.Config(), HubAppearancePreferences.normalized("unknown", "bad"))
        assertEquals(HubAppearancePreferences.Config("DARK", "TEAL"),
            HubAppearancePreferences.normalized("DARK", "TEAL"))
        assertEquals(6, HubAppearancePreferences.accents.size)
    }
}
