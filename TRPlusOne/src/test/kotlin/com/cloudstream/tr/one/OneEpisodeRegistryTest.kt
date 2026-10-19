package com.cloudstream.tr.one

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OneEpisodeRegistryTest {

    @Before
    fun setup() {
        OneEpisodeRegistry.clear()
        OneDiagnostics.clear()
    }

    @Test
    fun testRegisterAndRetrieveSingleProviderEpisode() {
        val mediaId = "showTestMedia123"
        val epUrl = "https://sezonlukdizi.cc/regular-show-the-lost-tapes/1-sezon-1-bolum.html"

        OneEpisodeRegistry.register(mediaId, season = 1, episode = 1, providerId = "SezonlukDizi", episodeUrl = epUrl)

        assertTrue(OneEpisodeRegistry.hasEpisode(mediaId, 1, 1))
        val targets = OneEpisodeRegistry.get(mediaId, 1, 1)
        assertEquals(1, targets.size)
        assertEquals(epUrl, targets["SezonlukDizi"])

        // Check diagnostic event
        val history = OneDiagnostics.history()
        assertTrue("Must log EPISODE_MATCH", history.any { it.contains("[EPISODE_MATCH]") && it.contains("SezonlukDizi") })
    }

    @Test
    fun testRegisterMultipleProvidersForSameEpisode() {
        val mediaId = "multiShow456"
        val sezonlukUrl = "https://sezonlukdizi.cc/shows/breaking-bad/1-sezon-1-bolum.html"
        val dizillaUrl = "https://dizilla.now/dizi/breaking-bad-izle/sezon-1/bolum-1"
        val diziYouUrl = "https://www.diziyou.one/breaking-bad-1-sezon-1-bolum/"

        OneEpisodeRegistry.register(mediaId, 1, 1, "SezonlukDizi", sezonlukUrl)
        OneEpisodeRegistry.register(mediaId, 1, 1, "Dizilla", dizillaUrl)
        OneEpisodeRegistry.register(mediaId, 1, 1, "DiziYou", diziYouUrl)

        val targets = OneEpisodeRegistry.get(mediaId, 1, 1)
        assertEquals(3, targets.size)
        assertEquals(sezonlukUrl, targets["SezonlukDizi"])
        assertEquals(dizillaUrl, targets["Dizilla"])
        assertEquals(diziYouUrl, targets["DiziYou"])
    }

    @Test
    fun testNonExistentEpisodeReturnsEmptyMapSafely() {
        val targets = OneEpisodeRegistry.get("nonExistentMedia", 99, 99)
        assertNotNull(targets)
        assertTrue(targets.isEmpty())
        assertFalse(OneEpisodeRegistry.hasEpisode("nonExistentMedia", 99, 99))
    }

    @Test
    fun testEpKeyFormat() {
        assertEquals("1:1", OneEpisodeRegistry.epKey(1, 1))
        assertEquals("0:1", OneEpisodeRegistry.epKey(0, 1))
        assertEquals("3:12", OneEpisodeRegistry.epKey(3, 12))
    }
}
