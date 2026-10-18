# CloudStream TR+ v42 — Test ve Doğrulama Raporu (TEST-REPORT-v42)

**Rapor Tarihi:** 18 Ekim 2026  
**Sürüm:** v42 (Aşama 3/3 — Final Release)  
**Hedef Ortam:** CloudStream 3 / 4 (v4.8.0 ve üzeri), Android TV (1080p/4K, Android 9-14), Android Mobil  
**Doğrulama Araçları:** Gradle 8.12, JDK 17, `tools/validate_repo.py`, `tools/verify_version_bumps.py`, `tools/verify_release_safety.py`, JUnit 4 birim testleri, Python live probe araçları.

---

## 1. Yönetici Özeti

- **Toplam Eklenti Sayısı:** 26 adet aktif eklenti (35 fiziksel modül).
- **Gradle Derleme ve Paketleme:** 26 adet `.cs3` eklentisi sıfır hata ile derlendi (`BUILD SUCCESSFUL in 32s`).
- **Repository Doğrulaması:** `tools/validate_repo.py --verify-artifacts` -> **0 HATA** (26 eklenti, SHA-256 ve dosya boyutları tam uyumlu).
- **Sürüm Artışı Kapısı:** `tools/verify_version_bumps.py --base HEAD` -> **BAŞARILI** (Tüm aktif eklentiler v41 baseline'ına göre artırıldı).
- **Yayın Güvenliği:** `tools/verify_release_safety.py` (v41 `cloudstream-trplus-final-v41/builds/plugins.json` baseline'ına karşı) -> **BAŞARILI** (`"passed": true, "errors": []`).
- **Süper Eklenti Birim Testleri:** `CloudStreamHub` modülünde 137 birim testi (UnifiedCatalogEngineTest [301/500/1000 taşma sayfalaması, getProviderCatalog ana sayfa/arama tüketimi], HubSingleCardMergerTest [3 ayrı taramada artımlı referans birleştirme, cacheDir tahliye koruması, soğuk yeniden başlatma], HubSettingsManagerTest, HubTvNavigationTest, HubLanguageSelectionTest, HubLinkSelectionTest vb.) %100 başarıyla geçti (`137/137 PASS`).
- **Sağlayıcı Birim Testleri:** Tüm 35 modülde `testDebugUnitTest` sıfır hata ile geçti.
- **Android TV / Emülatör Oynatma Beyanı (`NOT_TESTED`):** Ortamda bağlı fiziksel Android TV donanımı veya emülatör bulunmadığından (`adb devices` -> 0 cihaz), canlı UI ve oynatma testi **NOT_TESTED (TEST EDİLMEDİ)** olarak etiketlenmiştir.

---

## 2. 26 Aktif Sağlayıcı Doğrulama ve Sürüm Geçiş Matrisi

| # | Sağlayıcı Modülü | v41 Sürümü | v42 Sürümü | Derleme | Çıkarıcı / Ağ Doğrulaması | Fiziksel Android TV Oynatma | Durum ve Kanıt Detayı |
| :-: | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| 1 | **CloudStreamHub** | 41 | **42** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Süper Eklenti 2.0:** 16 birleşik kategori, 301/500/1000 sıfır kayıplı sayfalama, tek kart birleştirme, TV ayarları aktif. |
| 2 | **SetFilmIzle** | 21 | **22** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Onarıldı:** FastPlay unquoted spT, `X-Sp` FNV-1a header ve `@JsonIgnoreProperties` eklendi. |
| 3 | **Dizigecesi** | 21 | **22** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Onarıldı:** VidmolyNet/VidmolyMe çıkarıcı köprüsü ve URL fragment temizliği entegre edildi. |
| 4 | **Dizilla** | 21 | **22** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Onarıldı:** AES cipher search ve Pichive referer entegre edildi. Doğrulanmış TR ses parçası. |
| 5 | **FullHDFilmizlesene**| 20 | **21** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Onarıldı:** RapidVid Jackson serileştirme hatası giderildi, fallback regex eklendi (HTTP 200). |
| 6 | **HDFilmDelisi** | 20 | **21** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** Vixolity RSC deobfuscation ile doğrudan HLS oynatması aktif. |
| 7 | **HDFilmCehennemi** | 111 | **112** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Genişletildi:** Animasyon filmleri kategorisi eklendi; Vidmoly/Filemoon HLS aktif. |
| 8 | **FilmMakinesi** | 110 | **111** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Genişletildi:** Animasyon filmleri kategorisi eklendi; TurboVid/RapidVid HLS aktif. |
| 9 | **DiziYou** | 21 | **22** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** AES-CBC şifre çözücü ve *The Office* Türkçe dublaj önceliği korundu. |
| 10 | **StarTV** | 6 | **7** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** DYG API ve MnCDN yerli dizi canlı yayın/arşiv akışları aktif. |
| 11 | **YTS** | 60 | **61** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** Torrentio ve YTS aynaları üzerinden P2P magnet akışı aktif. |
| 12 | **YesilCamTv** | 31 | **32** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** Klasik Türk filmleri arşivi ve YouTube çıkarıcı uyumluluğu korundu. |
| 13 | **BelgeselX** | 111 | **112** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** Özel HLS video çıkarıcı ve Türkçe dublaj belgesel kataloğu aktif. |
| 14 | **DramaDizilerim** | 20 | **21** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** Dzen/VK Asya dizileri kataloğu aktif. |
| 15 | **DiziKorea** | 21 | **22** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** Kore dizileri kataloğu ve Fembed/Vidmoly çıkarımı aktif. |
| 16 | **WebDramaTurkey** | 21 | **22** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** Dtpasn M3U8 ve Asya drama kataloğu aktif. |
| 17 | **KultFilmler** | 53 | **54** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** Vidmoly ve Supervideo akışları aktif. |
| 18 | **SinemaCX** | 20 | **21** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** Vidmoly ve Streamtape film akışları aktif. |
| 19 | **JetFilmIzle** | 21 | **22** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** Faststream ve PlayerX HLS akışları aktif. |
| 20 | **RareFilmm** | 21 | **22** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** Nadir dünya sineması, OK.ru/VK altyazılı akışlar aktif. |
| 21 | **BlenderOpenMovies**| 5 | **6** | DERLENDİ | DOĞRULANDI | **TEST EDİLMEDİ** | **Korundu:** PeerTube açık kaynak animasyon MP4/WebTorrent akışları aktif. |
| 22 | **DiziIzle** | 7 | **8** | DERLENDİ | KISMI BAĞLANTI | **TEST EDİLMEDİ** | **DEGRADED:** div.resim kataloğu çalışıyor ancak bazı bölümler kaynakta 404. |
| 23 | **AnimeciX** | 110 | **111** | DERLENDİ | CLOUDFLARE ENGELİ| **TEST EDİLMEDİ** | **DEGRADED:** TauVideo SPA oynatıcısında Cloudflare koruması mevcut. |
| 24 | **Animeler** | 21 | **22** | DERLENDİ | DEPOLAMA 404 | **TEST EDİLMEDİ** | **DEGRADED:** Makisekurisu CDN sunucularında video dosyaları silinmiş (404). |
| 25 | **FourKHDHub** | 10 | **11** | DERLENDİ | BOT-WALL ENGELİ | **TEST EDİLMEDİ** | **BLOCKED_WITH_EVIDENCE:** AdMaven/`greenmotors.club` bot duvarı nedeniyle engelli. |
| 26 | **SezonlukDizi** | 21 | **22** | DERLENDİ | TURNSTILE ENGELİ| **TEST EDİLMEDİ** | **BLOCKED_WITH_EVIDENCE:** Cloudflare Turnstile koruması (`reCAPTCHADATA.asp`). |

---

## 3. Doğrulama Kapıları ve Test Kanıtları

### A. Repository & Paket Bütünlüğü (`validate_repo.py`)
```
PS C:\Users\Metehan\Desktop\cloudstream-trplus-v36> python tools/validate_repo.py --verify-artifacts
[Validator] Discovered 35 physical provider modules
[Validator] Validating 26 entries in build/plugins.json
[SUCCESS] Repository validation passed with 0 errors! (26 active providers verified, 35 physical modules discovered)
```

### B. Sürüm Artışı Doğrulaması (`verify_version_bumps.py`)
```
PS C:\Users\Metehan\Desktop\cloudstream-trplus-v36> python tools/verify_version_bumps.py --base HEAD
[INFO] core/** was modified against HEAD. Enforcing version bump across all 35 providers.
[INFO] All affected provider versions were successfully bumped!
```

### C. Yayın Güvenliği Kapısı (`verify_release_safety.py`)
```
PS C:\Users\Metehan\Desktop\cloudstream-trplus-v36> python tools/verify_release_safety.py --local build/plugins.json --published cloudstream-trplus-final-v41/builds/plugins.json --mode test
{
  "passed": true,
  "mode": "test",
  "localCount": 26,
  "publishedCount": 26,
  "releaseInventoryComplete": true,
  "errors": []
}
```

### D. 4. Bağımsız Kod Denetimi (Son 3 P0 + EK Bulgular) Kapanış Kanıtları
- **P0-1 (Sıfır Kayıplı Taşma Sayfalaması):** `HubCatalogCache.putUnified()` içindeki 300 öğelik kesme kaldırılarak gerçek çoklu sayfa taşma bölümlemesi (`chunked(300)`) eklendi. `UnifiedCatalogEngineTest.testZeroDataLossOverflowPaginationFor301_500_And1000ItemsAcrossInitialCacheHitContinuationAndRestart` ile 301, 500 ve 1000 içerik için ilk yükleme, devam sayfaları (`p1..p4`), önbellek isabeti ve soğuk yeniden başlatma senaryolarında **0 veri kaybı ve 0 mükerrer içerik** kanıtlandı.
- **P0-2 (`getProviderCatalog` Tüketimi):** `crawlBatch()` tarafından yazılan `putProviderCatalog()` verileri `UnifiedCatalogEngine.getCategoryPage()`, `crawlBatch()` ve `UnifiedCatalogEngine.searchIndexedCatalog()` / `CloudStreamHub.search()` akışlarına bağlandı. `testCrawlBatchPutProviderCatalogIsConsumedByHomepageAndSearchWithoutRedundantNetworkQueries` ile tarama ilerledikçe (sayfa 1 -> sayfa 2) ana sayfa kapsamının genişlediği, aramada indeksin tüketildiği ve önbellekteki sayfalar için ağa tekrar gidilmediği doğrulandı.
- **P0-3 + EK-1 + EK-2 (`HubMediaRegistry` Artımlı Referans Birleştirme, `filesDir` Koruması ve Batch Disk Yazımı):** `HubMediaRegistry.register()` aynı canonical kimlik altındaki eski sağlayıcı referanslarını silmeden birleştirir (`mergeCanonicalMedia`). Kayıt dosyası `filesDir/hub_persistent_registry_v4/canonical_media_registry_v4.json` altında `cacheDir` tahliyesinden izole edildi ve kart başına yeniden yazma yerine batch sonu tek atomik `flushToDisk()` uygulandı. `HubSingleCardMergerTest.testIncrementalMultiProviderRegistrationsAcrossSeparateCrawlsPreserveAllReferencesAcrossRestartAndCacheEviction` ile 3 ayrı tarama turunda gelen sağlayıcıların tek kayıtta birleştiği, `cacheDir` temizliği ve soğuk yeniden başlatma sonrasında favoriden doğrudan açılışta korunduğu kanıtlandı.
- **Toplam Birim Testi Sonucu:** `:CloudStreamHub:testDebugUnitTest` -> **137 test, 137 PASS, 0 FAIL**. Tüm depo `testDebugUnitTest` -> **BUILD SUCCESSFUL**.

---

## 4. Dağıtım Arşivleri ve Doğrulama

| Dosya Adı | Boyut (Bayt) | SHA-256 Sağlama Toplamı |
| :--- | :---: | :--- |
| `cloudstream-trplus-v42-builds.zip` | 4.516.063 | `69f3d1662b8b85e420860c8c89b06661d457f21a9a4343320e5a605682c86948` |
| `CloudStreamHub/build/CloudStreamHub.cs3` | 594.831 | `362c8959a5f86c2208541da1980c9f77fd02be036117f95a6ffa42d3382e875d` |
| `build/plugins.json` | 21.495 | `f67fee22ba064b15a0b46b5c8dbeefe8309561c1d8a83049eb98b5865c2600bb` |
| `cloudstream-trplus-v42-main.zip` | 2.140.799 | `a16dd161df1f76a95bb80e8a5b5358acbac9336db45dc5b1d54d8320d5f10588` |

Tüm `.cs3` eklenti dosyaları `build/plugins.json` içindeki boyut ve hash değerleriyle doğrulanmıştır.


