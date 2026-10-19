package com.cloudstream.tr.one

import org.junit.Assert.*
import org.junit.Test

class OneProvidersGroup1Test {

    @Test
    fun testNineAdaptersRegisteredByDefault() {
        val expected = listOf(
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

        val registered = OneProviders.all().map { it.providerId }
        for (prov in expected) {
            assertTrue("Provider $prov must be registered in Group 1", registered.contains(prov))
            val adapter = OneProviders.byId(prov)
            assertNotNull("Adapter for $prov must be non-null", adapter)
            assertEquals(prov, adapter!!.providerId)
        }
        assertEquals("Group 1 must contain exactly 9 default providers", 9, OneProviders.all().size)
    }

    @Test
    fun testSupportedTypesForGroup1() {
        // Movies
        assertTrue(OneProviders.byId("HDFilmCehennemi")!!.supportedTypes.contains(OneKind.MOVIE))
        assertTrue(OneProviders.byId("FilmMakinesi")!!.supportedTypes.contains(OneKind.MOVIE))
        assertTrue(OneProviders.byId("SinemaCX")!!.supportedTypes.contains(OneKind.MOVIE))
        assertTrue(OneProviders.byId("FullHDFilmizlesene")!!.supportedTypes.contains(OneKind.MOVIE))

        // Series
        assertTrue(OneProviders.byId("SezonlukDizi")!!.supportedTypes.contains(OneKind.SERIES))
        assertTrue(OneProviders.byId("Dizilla")!!.supportedTypes.contains(OneKind.SERIES))
        assertTrue(OneProviders.byId("DiziYou")!!.supportedTypes.contains(OneKind.SERIES))
        assertTrue(OneProviders.byId("HDFilmCehennemi")!!.supportedTypes.contains(OneKind.SERIES))
        assertTrue(OneProviders.byId("FilmMakinesi")!!.supportedTypes.contains(OneKind.SERIES))

        // Anime
        assertTrue(OneProviders.byId("AnimeciX")!!.supportedTypes.contains(OneKind.ANIME_SERIES))
        assertTrue(OneProviders.byId("AnimeciX")!!.supportedTypes.contains(OneKind.ANIME_MOVIE))
        assertTrue(OneProviders.byId("Animeler")!!.supportedTypes.contains(OneKind.ANIME_SERIES))
    }

    @Test
    fun testCustomAdapterRegistration() {
        val testAdapter = object : TRProviderAdapter {
            override val providerId: String = "MockProvider"
            override val displayName: String = "Mock Provider"
            override val supportedTypes: Set<OneKind> = setOf(OneKind.MOVIE)

            override suspend fun fetchCatalog(category: String, page: Int): List<ProviderListing> = emptyList()
            override suspend fun search(query: String): List<ProviderListing> = emptyList()
            override suspend fun loadDetail(url: String) = null
            override suspend fun loadLinks(
                data: String,
                isCasting: Boolean,
                subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
                callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
            ): Boolean = false
        }

        OneProviders.register(testAdapter)
        assertNotNull(OneProviders.byId("MockProvider"))
        assertEquals("MockProvider", OneProviders.byId("MockProvider")!!.providerId)

        // Reset back to defaults
        OneProviders.resetForTests()
        assertNull(OneProviders.byId("MockProvider"))
        assertEquals(9, OneProviders.all().size)
    }
}
