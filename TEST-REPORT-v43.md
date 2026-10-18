# CloudStream TR+ v43 — Test ve Doğrulama Raporu (`TEST-REPORT-v43.md`)

**Tarih:** 2026-10-18  
**Modül:** `:CloudStreamHub` (`version = 43`)  
**Yerel Dal:** `trplus/v43-stabilization`  
**Çalıştırılan Komut:** `.\gradlew.bat :CloudStreamHub:testDebugUnitTest :CloudStreamHub:make --console=plain`

---

## 1. Genel Test Özeti

| Doğrulama Katmanı | Kapsam | Sonuç | Kanıt |
|---|---|---|---|
| **Birim & Regresyon Testleri (`testDebugUnitTest`)** | 16 test sınıfı, **142** test senaryosu | **PASS (142 / 142)** | `CloudStreamHub/build/test-results/testDebugUnitTest/*.xml` |
| **Yeni v43 Stabilizasyon Regresyon Testleri** | 5 yeni hedefli regresyon testi | **PASS (5 / 5)** | `UnifiedCatalogEngineTest`, `HubTvNavigationTest` |
| **DEX ve CS3 Paket Üretimi (`:CloudStreamHub:make`)** | `CloudStreamHub.cs3` (version = 43) | **PASS** | `610,839` bayt, `manifest.json` (`version: 43`) + `classes.dex` |
| **Fiziksel Android 12 TV Box Cihaz Testi** | Gerçek TV Box üzerinde UI/kumanda/canlı ağ | **NOT_TESTED** | Bu ortamda fiziksel Android 12 TV Box bağlı olmadığı için manuel kontrol listesi hazırlanmıştır |

---

## 2. Test Sınıfları Bazında Sonuçlar (`142 PASS / 0 FAIL / 0 ERROR`)

| Test Sınıfı | Test Sayısı | Başarısız | Hata | Süre | Durum |
|---|---:|---:|---:|---:|---|
| `com.cloudstream.tr.hub.CloudStreamHubTest` | 20 | 0 | 0 | 0.860s | **PASS** |
| `com.cloudstream.tr.hub.HubCachePolicyTest` | 2 | 0 | 0 | 0.004s | **PASS** |
| `com.cloudstream.tr.hub.HubFeatureTopKTest` | 2 | 0 | 0 | 0.011s | **PASS** |
| `com.cloudstream.tr.hub.HubHomeCustomizationTest` | 5 | 0 | 0 | 0.003s | **PASS** |
| `com.cloudstream.tr.hub.HubLanguageSelectionTest` | 9 | 0 | 0 | 0.100s | **PASS** |
| `com.cloudstream.tr.hub.HubLinkSelectionTest` | 22 | 0 | 0 | 0.082s | **PASS** |
| `com.cloudstream.tr.hub.HubPerformanceSupportTest` | 6 | 0 | 0 | 0.259s | **PASS** |
| `com.cloudstream.tr.hub.HubSettingsManagerTest` | 6 | 0 | 0 | 0.094s | **PASS** |
| `com.cloudstream.tr.hub.HubSingleCardMergerTest` | 11 | 0 | 0 | 0.110s | **PASS** |
| `com.cloudstream.tr.hub.HubSourceMemoTest` | 3 | 0 | 0 | 0.015s | **PASS** |
| `com.cloudstream.tr.hub.HubSourceResolutionTest` | 3 | 0 | 0 | 0.011s | **PASS** |
| `com.cloudstream.tr.hub.HubTorrentReadinessTest` | 24 | 0 | 0 | 0.921s | **PASS** |
| `com.cloudstream.tr.hub.HubTorrentTransportTest` | 3 | 0 | 0 | 0.576s | **PASS** |
| `com.cloudstream.tr.hub.HubTorrentVlcActionTest` | 7 | 0 | 0 | 0.007s | **PASS** |
| `com.cloudstream.tr.hub.HubTvNavigationTest` | 4 | 0 | 0 | 0.002s | **PASS** |
| `com.cloudstream.tr.hub.UnifiedCatalogEngineTest` | 15 | 0 | 0 | 4.522s | **PASS** |
| **TOPLAM** | **142** | **0** | **0** | **7.577s** | **PASS** |

---

## 3. v43 İçin Eklenen Yeni Regresyon Testleri

1. **`UnifiedCatalogEngineTest.testEmptyOrFailedNetworkResponseDoesNotWipeValidCachedCatalog`** (`PASS`)
   - **Doğrulanan Davranış:** Önbellekte geçerli katalog öğeleri varken (`HubCatalogCache.putUnified`), sonraki bir ağ hatası veya boş yanıt (`emptyList()`) geldiğinde önbelleğin silinmediğini ve `getCategoryPage` çağrısının son geçerli katalog öğelerini (`getUnifiedStale`) döndürdüğünü kanıtlar.
2. **`UnifiedCatalogEngineTest.testRepeatedTriggerBackgroundPrewarmDoesNotStartHeavyCrawlByDefault`** (`PASS`)
   - **Doğrulanan Davranış:** Varsayılan ayarlarda (`enableBackgroundCrawler == false`) `triggerBackgroundPrewarm()` art arda çağrılsa bile hiçbir arka plan taraması (`crawlBatch`) başlatılmadığını ve sağlayıcılara sıfır ağ isteği yapıldığını kanıtlar.
3. **`UnifiedCatalogEngineTest.testSameSessionDuplicateGetCategoryPageDoesNotTriggerRedundantNetworkRequests`** (`PASS`)
   - **Doğrulanan Davranış:** Aynı oturumda aynı `(category, page)` için eşzamanlı veya ardışık `getCategoryPage()` çağrılarının `inFlightCategoryMutexes` ve birleşik önbellek sayesinde sağlayıcıyı yalnızca **1 kez** sorguladığını kanıtlar.
4. **`UnifiedCatalogEngineTest.testRegistryAdapterCachesBundledInstancesWithoutReflectionStorm`** (`PASS`)
   - **Doğrulanan Davranış:** `CloudStreamProviderRegistryAdapter.getBundledProviders()` tekrar tekrar çağrıldığında `Class.forName().newInstance()` ile yeni nesne üretmediğini, aynı singleton `MainAPI` referanslarını (`===`) döndürdüğünü ve `findProviderForUrl()` metodunun yönlendirilmiş alan adlarını doğru eşleştirdiğini kanıtlar.
5. **`HubTvNavigationTest.testSpinnerStylePolicyProducesVisibleHighContrastTextInDarkAndLightModes`** (`PASS`)
   - **Doğrulanan Davranış:** `HubSpinnerStylePolicy.resolve(isDarkMode = true/false)` tarafından üretilen metin, açılır liste, arka plan ve odak kenarlığı renklerinin şeffaf olmadığını, metin ve arka plan renklerinin birbirinden farklı ve yüksek kontrastlı olduğunu, seçili/açılır etiket biçimlendiricilerinin (`▾  ...` ve `✓  ...`) boş olmayan okunaklı metin ürettiğini kanıtlar.

---

## 4. Üretilen `CloudStreamHub.cs3` (v43) Paket Doğrulaması

- **Dosya Yolu 1:** `CloudStreamHub/build/CloudStreamHub.cs3`
- **Dosya Yolu 2:** `build/CloudStreamHub.cs3`
- **Boyut:** `610,839` bayt
- **SHA-256:** `0243b7f720fe06acc6d13480b910832bf2dda8949a84aa784c8367e0a3101a10`
- **İçerik (`manifest.json`):**
  ```json
  {
    "requiresResources": false,
    "version": 43,
    "pluginClassName": "com.cloudstream.tr.hub.CloudStreamHubPlugin",
    "name": "CloudStreamHub"
  }
  ```
- **Arşiv Girdileri:** `manifest.json`, `classes.dex`
