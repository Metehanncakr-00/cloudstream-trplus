package com.cloudstream.tr.one

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DiziYouRegressionAndUnificationTest {

    @Before
    fun setup() {
        OneIndex.clearForTests()
        OneEpisodeRegistry.clear()
        OneDiagnostics.clear()
    }

    @Test
    fun testTurkishConjunctionNormalization() {
        val n1 = OneIdentity.normalizedTitle("Rick ve Morty", null)
        val n2 = OneIdentity.normalizedTitle("Rick and Morty", null)
        val n3 = OneIdentity.normalizedTitle("Rick & Morty", null)
        assertEquals("rick and morty", n1)
        assertEquals("rick and morty", n2)
        assertEquals("rick and morty", n3)
        assertEquals(n1, n2)
        assertEquals(n2, n3)
    }

    @Test
    fun testExistingSezonlukDiziMergesWithRemoteDiziYou() {
        // Step 1: Initial state - local index has Rick and Morty from SezonlukDizi with year 2013
        val sezListing = ProviderListing(
            providerId = "SezonlukDizi",
            url = "https://sezonlukdizi.cc/diziler/rick-and-morty.html",
            name = "Rick and Morty",
            year = 2013,
            kind = OneKind.SERIES
        )
        val initialCards = CanonicalEngine.merge(emptyList(), listOf(sezListing))
        assertEquals(1, initialCards.size)
        val cardId = initialCards.first().id
        assertEquals(2013, initialCards.first().year)
        assertEquals(listOf("SezonlukDizi"), initialCards.first().listings.map { it.providerId })

        // Step 2: Remote search arrives with DiziYou listing lacking a year
        val diziYouListing = ProviderListing(
            providerId = "DiziYou",
            url = "https://www.diziyou.one/rick-and-morty3/",
            name = "Rick ve Morty",
            year = null,
            kind = OneKind.SERIES
        )

        var registeredAlias: Pair<String, String>? = null
        val mergedCards = CanonicalEngine.merge(initialCards, listOf(diziYouListing)) { alias, canonical ->
            registeredAlias = alias to canonical
        }

        // Verification: Exactly one card preserved, card ID unchanged, both providers present
        assertEquals(1, mergedCards.size)
        val unifiedCard = mergedCards.first()
        assertEquals(cardId, unifiedCard.id)
        assertEquals(2013, unifiedCard.year)
        assertEquals(setOf("SezonlukDizi", "DiziYou"), unifiedCard.listings.map { it.providerId }.toSet())

        // Alias registered from DiziYou-specific key to canonical card ID
        assertNotNull(registeredAlias)
        assertEquals(unifiedCard.id, registeredAlias!!.second)
    }

    @Test
    fun testDifferentProductionsDoNotMerge() {
        val series1963 = ProviderListing("A", "https://a.com/dw1", "Doctor Who", 1963, OneKind.SERIES)
        val series2005 = ProviderListing("B", "https://b.com/dw2", "Doctor Who", 2005, OneKind.SERIES)
        val movie = ProviderListing("C", "https://c.com/tlou", "The Last of Us", 2023, OneKind.MOVIE)
        val show = ProviderListing("D", "https://d.com/tlou", "The Last of Us", 2023, OneKind.SERIES)

        val merged1 = CanonicalEngine.merge(emptyList(), listOf(series1963, series2005))
        assertEquals(2, merged1.size)

        val merged2 = CanonicalEngine.merge(emptyList(), listOf(movie, show))
        assertEquals(2, merged2.size)

        // Two items both lacking years and without external ID do not merge
        val unk1 = ProviderListing("A", "https://a.com/d1", "Dune", null, OneKind.MOVIE)
        val unk2 = ProviderListing("B", "https://b.com/d2", "Dune", null, OneKind.MOVIE)
        val merged3 = CanonicalEngine.merge(emptyList(), listOf(unk1, unk2))
        assertEquals(2, merged3.size)
    }

    @Test
    fun testDiziYouEpisodePlaybackAndFallbackDelivery() = runBlocking {
        val sezListing = ProviderListing(
            providerId = "SezonlukDizi",
            url = "https://sezonlukdizi.cc/diziler/rick-and-morty.html",
            name = "Rick and Morty",
            year = 2013,
            kind = OneKind.SERIES
        )
        val diziYouListing = ProviderListing(
            providerId = "DiziYou",
            url = "https://www.diziyou.one/rick-and-morty3/",
            name = "Rick ve Morty",
            year = null,
            kind = OneKind.SERIES
        )

        // Merge both listings into index
        OneIndex.ingest("series", 1, listOf(sezListing, diziYouListing))
        val media = OneIndex.media(OneIdentity.key(sezListing))
        assertNotNull("Merged media must exist in index", media)
        val mediaId = media!!.id

        // Register episode URLs for 1x01 from both providers under canonical card ID
        OneEpisodeRegistry.register(mediaId, 1, 1, "SezonlukDizi", "https://sezonlukdizi.cc/rick-and-morty/1-sezon-1-bolum.html")
        OneEpisodeRegistry.register(mediaId, 1, 1, "DiziYou", "https://www.diziyou.one/rick-and-morty-1-sezon-1-bolum/")

        // Create mock adapters
        val sezAdapter = object : TRProviderAdapter {
            override val providerId = "SezonlukDizi"
            override val displayName = "SezonlukDizi"
            override val supportedTypes = setOf(OneKind.SERIES)
            override suspend fun fetchCatalog(category: String, page: Int) = emptyList<ProviderListing>()
            override suspend fun search(query: String) = emptyList<ProviderListing>()
            override suspend fun loadDetail(url: String): LoadResponse? = null
            // Simulates failed/bot-blocked extractor on SezonlukDizi
            override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
                return false
            }
        }

        val diziYouAdapter = object : TRProviderAdapter {
            override val providerId = "DiziYou"
            override val displayName = "DiziYou"
            override val supportedTypes = setOf(OneKind.SERIES)
            override suspend fun fetchCatalog(category: String, page: Int) = emptyList<ProviderListing>()
            override suspend fun search(query: String) = emptyList<ProviderListing>()
            override suspend fun loadDetail(url: String): LoadResponse? = null
            // Emits working HLS link from DiziYou
            override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
                if (data == "https://www.diziyou.one/rick-and-morty-1-sezon-1-bolum/") {
                    subtitleCallback(SubtitleFile("Türkçe", "https://storage.diziyou.one/subtitles/11501/tr.vtt"))
                    @Suppress("DEPRECATION")
                    callback(
                        ExtractorLink(
                            source = "DiziYou",
                            name = "DiziYou HLS Türkçe Altyazılı",
                            url = "https://storage.diziyou.one/episodes/11501/play.m3u8",
                            referer = "https://www.diziyou.one/",
                            quality = Qualities.Unknown.value,
                            type = ExtractorLinkType.M3U8
                        )
                    )
                    return true
                }
                return false
            }
        }

        OneProviders.resetForTests(listOf(sezAdapter, diziYouAdapter))

        val api = TRPlusOne()
        val emittedLinks = mutableListOf<ExtractorLink>()
        val emittedSubs = mutableListOf<SubtitleFile>()

        val route = OneRouteCodec.episodeUrl(mediaId, 1, 1)
        val success = api.loadLinks(route, isCasting = false, subtitleCallback = { emittedSubs.add(it) }) {
            emittedLinks.add(it)
        }

        assertTrue("Playback should succeed despite SezonlukDizi failure", success)
        assertEquals(1, emittedLinks.size)
        val link = emittedLinks.first()
        assertTrue("Link name should contain [DiziYou]", link.name.contains("[DiziYou]"))
        assertTrue("Link name should contain [Türkçe Altyazı]", link.name.contains("[Türkçe Altyazı]"))
        assertEquals("https://storage.diziyou.one/episodes/11501/play.m3u8", link.url)
        assertEquals(1, emittedSubs.size)
        assertEquals("Türkçe", emittedSubs.first().lang)

        OneProviders.resetForTests(null)
    }
}
