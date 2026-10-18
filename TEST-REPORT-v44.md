# CloudStream TR+ v44 — Birim Test, Derleme ve Doğrulama Raporu

**Tarih:** 19 Ekim 2026  
**Hedef Eklenti:** `CloudStreamHub` (`version = 44`)  
**Üretilen Paket:** `build/CloudStreamHub.cs3` (`CloudStreamHub/build/CloudStreamHub.cs3`)  
**Paket Boyutu:** `628,686` byte  
**SHA-256:** `befa3befbd8b2033b79237f5a3481ff3f88fdbaa3a339b13dbd85c1b31e63153`  
**Manifest Doğrulaması:** `{"requiresResources":false,"version":44,"pluginClassName":"com.cloudstream.tr.hub.CloudStreamHubPlugin","name":"CloudStreamHub"}`  
**Diğer 25 Eklenti:** Değiştirilmedi (Korundu)  
**GitHub Yayını:** Kullanıcı onayıyla `main` ve `builds` dallarına güvenli yayın (v43 geri dönüş yedekleriyle birlikte)

---

## 1. Otomatik Doğrulama Kapıları (`AUTO_REQUIRED`)

| Doğrulama Kapısı | Komut | Sonuç | Kanıt / Detay |
| :--- | :--- | :--- | :--- |
| **Depo Yapısal Doğrulama** | `python tools/validate_repo.py` | **PASS** | 26 aktif eklenti ve `config/providers.json` tutarlılığı doğrulandı |
| **CloudStreamHub Birim Testleri** | `.\gradlew.bat :CloudStreamHub:testDebugUnitTest --console=plain` | **PASS (149/149)** | 16 test sınıfı, **149 test**, **0 hata**, **0 başarısızlık** |
| **CloudStreamHub v44 `.cs3` Derlemesi** | `.\gradlew.bat :CloudStreamHub:make --console=plain` | **PASS** | `CloudStreamHub.cs3` (`version = 44`, `628,686` byte) üretildi |

---

## 2. v44 Regresyon Test Kapsamı ve Sonuçları

| Test Adı | Test Sınıfı | Doğrulanan v44 Gereksinimi | Durum |
| :--- | :--- | :--- | :--- |
| `testTmdbJsonParserDeterministicallyParsesPageAndDetailWithoutReflection` | `CloudStreamHubTest` | TMDB sayfa ve detay JSON yanıtları yansıma (`KotlinModule`) kullanmadan `TmdbJsonParser` ile ayrıştırılır ve gerçek `SearchResponse` / `LoadResponse` üretir | **PASS** |
| `testTrDubAndTrSubHomePagesUseProviderCategoryUrisInsteadOfFakeTmdbUrls` | `CloudStreamHubTest` | `Türkçe Dublaj` ve `Türkçe Altyazı` satırları sahte İngilizce TMDB listeleri çekmez (`hub://category/tr_dub` ve `hub://category/tr_sub` kullanır) | **PASS** |
| `testGetMainPageReturnsNonEmptyForVisibleCategoryAndEmptyForHiddenCategory` | `CloudStreamHubTest` | `CloudStreamHub.getMainPage()` görünür kategoriler (ve eski v41 etiketleri) için dolu sonuç, gizli kategoriler için boş liste döndürür ve `HIDDEN_BY_USER` tanılaması kaydeder | **PASS** |
| `testMultiTypeProviderDoesNotMisclassifyDubSubOrMovieCategoriesAsDocumentary` | `UnifiedCatalogEngineTest` | `Documentary`/`Anime` destekleyen çoklu-tür sağlayıcıların `"Türkçe Dublaj"`, `"Türkçe Altyazı"`, `"Diziler"` ve `"Filmler"` kategorileri yanlışlıkla `Belgeseller` olarak sınıflandırılmaz; `TR_DUB` yalnızca gerçek dublaj kategorisi olan sağlayıcıları eşleştirir | **PASS** |
| `testTmdbSuccessMergesWithCachedProviderItemsWithoutTriggering25LiveHtmlScrapes` | `UnifiedCatalogEngineTest` | TMDB başarılı olduğunda TMDB öğeleri + önbellekteki sağlayıcı öğeleri 25 canlı HTML kazıma kuyruğuna takılmadan anında tek kart altında birleşir | **PASS** |
| `testTmdbFailureAutomaticallyFallsBackToLiveProviderCatalogsAndRecordsDiagnostics` | `UnifiedCatalogEngineTest` | TMDB başarısız olduğunda sağlayıcı katalog fallback'i (`FALLBACK_TO_PROVIDERS`) devreye girer, sonuç döndürür ve API anahtarlarını sızdırmadan `HubCatalogDiagnostics` kaydı üretir | **PASS** |
| `testProviderTimeoutPreservesOtherResults` | `UnifiedCatalogEngineTest` | Sağlayıcılardan biri çöktüğünde veya zaman aşımına uğradığında diğer sağlayıcıların sonuçları korunur | **PASS** |
| `testEmptyOrFailedNetworkResponseDoesNotWipeValidCachedCatalog` | `UnifiedCatalogEngineTest` | `HubCatalogCache` boş/hatalı yanıtla mevcut geçerli önbelleği silmez | **PASS** |
| `testCheckBoxStylePolicyProducesDistinctFocusedAndUnfocusedVisualStatesAndCheckedBadges` | `HubTvNavigationTest` | `HubCheckBoxStylePolicy` odaklandığında (`▶ [✓]` / `▶ [ ]`, vurgu arka planı, 2dp yüksek kontrast kenarlık) ve odaktan çıktığında (`[✓]` / `[ ]`, normal arka plan) farklı görsel durum üretir | **PASS** |
| `testSpinnerStylePolicyProducesVisibleHighContrastTextInDarkAndLightModes` | `HubTvNavigationTest` | v43 Spinner görünürlük düzeltmesi aynen korunmuştur | **PASS** |
| `testRepeatedTriggerBackgroundPrewarmDoesNotStartHeavyCrawlByDefault` | `UnifiedCatalogEngineTest` | v43 performans düzeltmesi (`enableBackgroundCrawler = false`) aynen korunmuştur | **PASS** |

---

## 3. Fiziksel Cihaz Doğrulaması (`MANUAL_REQUIRED`)

| Kontrol | Durum | Açıklama |
| :--- | :--- | :--- |
| **Android 12 TV Box Gerçek Cihaz Testi (16 Kategori Yükleme + Kumanda CheckBox D-Pad Gezinmesi)** | **`NOT_TESTED`** | Fiziksel Android 12 TV Box donanımı bu derleme ortamında bağlı olmadığı için gerçek TV ekranı ve kumanda doğrulaması kullanıcı cihazında yapılmalıdır. |
