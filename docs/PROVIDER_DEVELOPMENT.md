# Eklenti Geliştirme Rehberi (Provider Development)

Bu rehber, güncel CloudStream 3 API'sine tam uyumlu yeni bir eklenti modülünün nasıl oluşturulacağını adım adım açıklar.

## 1. Modül Dizini Oluşturma

Repository kök dizininde eklentiniz için bir klasör oluşturun (örn. `MyProvider`):

```text
MyProvider/
├── build.gradle.kts
└── src/
    ├── main/
    │   └── kotlin/com/cloudstream/tr/myprovider/
    │       ├── MyProvider.kt
    │       ├── MyProviderPlugin.kt
    │       └── model/
    └── test/
        ├── kotlin/...
        └── resources/
            ├── homepage.html
            └── detail.html
```

## 2. Modül build.gradle.kts

```kotlin
version = 1

cloudstream {
    authors     = listOf("YourName")
    language    = "tr"
    description = "Açıklayıcı ve doğru eklenti tanımı."
    status      = 1 // 0: Down, 1: OK, 2: Slow, 3: Beta
    tvTypes     = listOf("Movie", "TvSeries")
    iconUrl     = "https://example.com/favicon.png"
}
```

## 3. Plugin Sınıfı

```kotlin
package com.cloudstream.tr.myprovider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MyProviderPlugin : Plugin() {
    override fun load() {
        registerMainAPI(MyProvider())
    }
}
```

## 4. MainAPI İmplementasyonu

Eklentiniz `MainAPI()` sınıfından türer:

```kotlin
package com.cloudstream.tr.myprovider

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class MyProvider : MainAPI() {
    override var mainUrl = "https://example.org"
    override var name = "MyProvider"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler/" to "Filmler",
        "${mainUrl}/diziler/" to "Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data).document
        val items = doc.select(".movie-card").mapNotNull { card ->
            val title = card.selectFirst(".title")?.text() ?: return@mapNotNull null
            val href = fixUrlNull(card.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(card.selectFirst("img")?.attr("data-src"))
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val doc = app.get("${mainUrl}/arama?q=${query}").document
        val results = doc.select(".search-card").mapNotNull { card ->
            val title = card.selectFirst(".title")?.text() ?: return@mapNotNull null
            val href = fixUrlNull(card.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            newMovieSearchResponse(title, href, TvType.Movie)
        }
        return newSearchResponseList(results, hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content"))
        val desc = doc.selectFirst(".plot")?.text()?.trim()

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = desc
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val iframeSrc = doc.selectFirst("iframe")?.attr("src") ?: return false

        return loadExtractor(iframeSrc, referer = mainUrl, subtitleCallback, callback)
    }
}
```

## 5. Tersine Mühendislik ve Sağlık Kontrolü Araçları (Scrapling)

Yeni bir sağlayıcı araştırması yaparken veya mevcut eklentileri test ederken Scrapling altyapısını kullanabilirsiniz:

```powershell
# Sitenin DOM yapısını, anti-bot korumasını ve stream kaynaklarını probe etmek için:
python tools/provider_probe.py https://hedefsite.com/ --mode auto --capture-xhr

# Seçici kayması (adaptive drift) ve sağlık denetimi için:
python tools/provider_health.py --provider HedefProvider
```

Ayrıntılı bilgi için [SCRAPLING_MONITORING.md](SCRAPLING_MONITORING.md) belgesini inceleyiniz.

