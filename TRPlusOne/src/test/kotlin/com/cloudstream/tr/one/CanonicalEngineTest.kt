package com.cloudstream.tr.one

import org.junit.Assert.*
import org.junit.Test

class CanonicalEngineTest {
    private fun item(provider: String, title: String, kind: OneKind = OneKind.MOVIE, year: Int? = 2014) =
        ProviderListing(provider, "https://$provider.example/$title", title, year, kind)

    @Test fun sameMovieTwoSourcesOneCard() {
        val merged = CanonicalEngine.merge(emptyList(), listOf(item("A", "Interstellar"), item("B", "Interstellar")))
        assertEquals(1, merged.size)
        assertEquals(setOf("A", "B"), merged.single().listings.map { it.providerId }.toSet())
    }

    @Test fun differentYearsRemainSeparate() {
        val merged = CanonicalEngine.merge(emptyList(), listOf(item("A", "Dune", year = 1984), item("B", "Dune", year = 2021)))
        assertEquals(2, merged.size)
    }

    @Test fun filmAndSeriesRemainSeparate() {
        val merged = CanonicalEngine.merge(emptyList(), listOf(item("A", "The Last of Us"), item("B", "The Last of Us", OneKind.SERIES)))
        assertEquals(2, merged.size)
    }

    @Test fun unknownYearDoesNotMergeAcrossProviders() {
        val merged = CanonicalEngine.merge(emptyList(), listOf(item("A", "Dune", year = null), item("B", "Dune", year = null)))
        assertEquals(2, merged.size)
    }

    @Test fun turkishAndEnglishICompareCorrectly() {
        val merged = CanonicalEngine.merge(emptyList(), listOf(item("A", "Interstellar"), item("B", "interstellar")))
        assertEquals(1, merged.size)
        assertEquals("istanbul", OneIdentity.normalizedTitle("İstanbul", null))
        assertEquals("igdir", OneIdentity.normalizedTitle("Iğdır", null))
    }

    @Test fun sameProviderUrlNotDuplicated() {
        val original = item("A", "Interstellar")
        val twice = CanonicalEngine.merge(emptyList(), listOf(original, original))
        assertEquals(1, twice.single().listings.size)
    }

    @Test fun animeFilmAndAnimeSeriesRemainSeparate() {
        val merged = CanonicalEngine.merge(emptyList(), listOf(
            item("A", "Your Name", OneKind.ANIME_MOVIE),
            item("B", "Your Name", OneKind.ANIME_SERIES)
        ))
        assertEquals(2, merged.size)
    }
}
