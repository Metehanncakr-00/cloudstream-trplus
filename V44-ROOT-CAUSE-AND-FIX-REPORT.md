# CloudStream TR+ v44 — Kök Neden Analizi ve Düzeltme Raporu

**Tarih:** 19 Ekim 2026  
**Hedef Eklenti:** `CloudStreamHub` (`version = 44`)  
**Referans Durum:** Android 12 TV Box gerçek cihaz testi (v43):
- Kasma / donma: **PASS** (Çözüldü)
- Ayarlar Spinner seçili değer görünürlüğü: **PASS** (Çözüldü)
- Ana sayfa film / dizi kataloglarının boş gelmesi: **FAIL** -> **v44 ile kök nedeninden düzeltildi**
- Kumanda (D-Pad) ile CheckBox'lar arasında gezinirken odak vurgusunun olmaması: **FAIL** -> **v44 ile kök nedeninden düzeltildi**

---

## 1. Boş Katalog Hatasının Kök Neden Analizi (Kanıtlı Çağrı Zinciri)

`CloudStreamHub.getMainPage()` -> `fetchTmdbPage()` -> `parseTmdbItem()` -> `UnifiedCatalogEngine.getCategoryPage()` -> `ProviderCapabilityMatrix` -> `HubCatalogCache` -> `HubHomePreferences` zinciri ve CloudStream ana uygulama (`cloudstream.jar` / `APIRepository.getMainPage`) bytecode'u uçtan uca incelendiğinde, ana sayfada katalogların boş dönmesine yol açan **4 somut kök neden** tespit edilmiştir:

### Kök Neden 1 — 16 Paralel Ana Sayfa Satırında Semaphore Kuyruk Açlığı (`APIRepository.getMainPage` Timeout)
- **Kanıt (`cloudstream.jar` -> `APIRepository$getMainPage$2$1` & `UnifiedCatalogEngine.kt` satır 183–259):**
  CloudStream ana uygulaması (`sequentialMainPage == false`), ana sayfa açıldığında `CloudStreamHub.mainPage` içindeki **16 kategorinin tamamı** için aynı anda `async { api.getMainPage(1, request) }` başlatmakta ve `deferreds.map { it.await() }` ile **16 satırın tamamı bitene kadar** beklemektedir.
- v43'te `UnifiedCatalogEngine.getCategoryPageInternal`:
  1. TMDB yanıtı ~300 ms içinde gelmiş olsa bile, her satır için ayrıca `5` canlı sağlayıcıda (`16 satır × 5 = 80` eşzamanlı HTML kazıma isteği!) `provider.getMainPage()` başlatıp `providerDeferreds.awaitAll()` çağrısında bekliyordu.
  2. `fetchProviderTarget` içinde `withTimeoutOrNull(fetchTimeoutMs)` (`6000ms`), `semaphore.withPermit` (`Semaphore(4)`) bloğunun **içine** konulmuştu:
     ```kotlin
     return semaphore.withPermit {
         withTimeoutOrNull(fetchTimeoutMs) { ... } // Kuyruk bekleme süresi timeout'a dahil değildi!
     }
     ```
  3. Ayrıca her sağlayıcının `getMainPage()` çağrısı içinde `DynamicDomainResolver.resolve()` global bir `Mutex` kilitliyordu.
- **Sonuç:** 80 canlı sağlayıcı kazıma isteği 4'erli 20 dalga halinde `globalProviderSemaphore(4)` kuyruğuna giriyor; son dalgaların izin (permit) alması 60–120 saniye sürdüğü için CloudStream `APIRepository.getMainPage()` zaman aşımına uğrayarak tüm ana sayfayı boş (`Resource.Failure` / boş liste) düşürüyordu.

### Kök Neden 2 — TMDB JSON Ayrıştırma Kırılganlığı (D8 Kotlin 2.3 Metadata Uyarısı) ve Sessiz Hata Yutma
- **Kanıt (`CloudStreamHub.kt` satır 63–76 & D8 derleme çıktısı):**
  `.cs3` paketi derlenirken D8:
  `WARNING: D8: Unexpected error during rewriting of Kotlin metadata for class 'com.cloudstream.tr.hub.TmdbPageResponse'` uyarısı üretmektedir.
  `fetchTmdbPage()` yalnızca `app.get(...).parsedSafe<TmdbPageResponse>()` (Jackson `KotlinModule` yansıma/reflection tabanlı metadata okuma) kullanıyor, ilk isteği `authHeaders` olmadan gönderiyor ve olası tüm HTTP/JSON hatalarını `catch (_: Throwable) { null }` ile hiçbir tanılama kaydı üretmeden sessizce `emptyList()` olarak yutuyordu.

### Kök Neden 3 — `ProviderCapabilityMatrix` Çoklu Tür (`TvType`) Yanlış Sınıflandırması ve Sahte `TR_DUB` / `TR_SUB` TMDB URL'leri
- **Kanıt (`ProviderCapabilityMatrix.kt` satır 25–35 & `CloudStreamHub.kt` satır 105–106):**
  1. `ProviderCapabilityMatrix.mapCategoryNameToUnified()` içinde `TvType.Documentary in providerTypes`, `TvType.Anime in providerTypes` ve `TvType.Cartoon in providerTypes` kontrolleri, kategori başlığındaki `"dublaj"`, `"altyazı"`, `"dizi"` ve `"film"` anahtar kelimelerinden **önce** çalışıyordu. Bu nedenle `FullHDFilmizlesene`, `HDFilmCehennemi`, `JetFilmIzle`, `KultFilmler`, `SinemaCX` gibi `supportedTypes` kümesinde `Documentary` veya `Anime` bulunduran çoklu-tür sağlayıcıların `"Türkçe Dublaj Filmler"`, `"Türkçe Altyazılı Filmler"`, `"Diziler"` ve `"Filmler"` kategorileri yanlışlıkla `Belgeseller` veya `Anime` olarak sınıflandırılıyordu.
  2. `CloudStreamHub.availableHomePages` içinde `Türkçe Dublaj` ve `Türkçe Altyazı` satırları, dil doğrulaması olmayan genel İngilizce TMDB discover URL'lerine (`with_original_language=en` ve `discover/tv`) bağlanmıştı.

### Kök Neden 4 — Eski Ana Sayfa Etiketlerinin (`HubHomePreferences.isVisible`) Reddedilmesi
- **Kanıt (`HubHomeCustomization.kt` satır 87–88):**
  Cihazda daha önceki bir sürümden (`v41`) kalan kategori başlığı (`"Günün Trendleri"`, `"Popüler Filmler"`, `"Popüler Diziler"` vb.) önbellekte tutulduğunda `HubHomePreferences.isVisible(request.name)` doğrudan `false` döndürerek `getMainPage()` çağrısını `emptyList()` ile sonlandırıyordu.

---

## 2. Boş Katalog İçin Uygulanan Kalıcı Düzeltmeler

1. **Yansımasız (Reflection-Free) Deterministik TMDB JSON Ayrıştırıcı (`TmdbJsonParser` — `TmdbApi.kt`):**
   - Tüm TMDB veri sınıflarına `@JsonIgnoreProperties(ignoreUnknown = true)` eklendi.
   - D8 Kotlin 2.3 metadata yeniden yazımından hiç etkilenmeyen, doğrudan Jackson AST (`ObjectMapper.readTree` / `JsonNode`) üzerinden çalışan `TmdbJsonParser.parsePage()` ve `TmdbJsonParser.parseDetail()` eklendi.
   - `fetchTmdbPage()` ve `fetchTmdbDetail()` önce `authHeaders` (`Bearer` + `Accept: application/json`) ile istek atacak, gerekirse `jsonAcceptHeaders` ile yedeklenecek şekilde güncellendi.

2. **Ana Sayfa Semaphore Kuyruk Açlığının Giderilmesi ve Sağlayıcı Fallback Mimarisi (`UnifiedCatalogEngine.kt`):**
   - TMDB destekli 14 ana sayfa satırında TMDB öğeleri başarıyla geldiğinde (`tmdbItems.isNotEmpty()`), önbellekteki sağlayıcı katalog öğeleri (`getAllCachedProviderPages`, 0 ms ağ maliyeti) ile anında tek kart altında birleştirilir; 25 sağlayıcıda 80 canlı HTML kazıma isteği kuyruğa sokularak ana sayfa bloke edilmez.
   - **TMDB Başarısız Olursa Otomatik Sağlayıcı Fallback'i:** TMDB boş dönerse, zaman aşımına uğrarsa veya hata verirse (`tmdbItems.isEmpty()`), `UnifiedCatalogEngine` otomatik olarak canlı sağlayıcı kataloglarını (`distinctUncachedTargets`) devreye sokar (`FALLBACK_TO_PROVIDERS`).
   - **İki Katmanlı Timeout:** `fetchProviderTarget` içinde hem `semaphore.withPermit` dışında kuyruk güvenliği sınırı hem de `semaphore.withPermit` içinde sağlayıcı çalışma sınırı (`withTimeoutOrNull(budgetMs)`) uygulanarak hiçbir sağlayıcının diğerlerini kilitlememesi garanti altına alındı.
   - Hiçbir kaynak yanıt vermezse `HubCatalogCache.getUnifiedStale()` ile mevcut geçerli önbellek korunur (`STALE_FALLBACK`).

3. **Gerçekçi `Türkçe Dublaj` / `Türkçe Altyazı` Kategorileri ve Doğru Kategori Haritalama (`ProviderCapabilityMatrix.kt` & `CloudStreamHub.kt`):**
   - `CloudStreamHub.availableHomePages` içindeki `TR_DUB` ve `TR_SUB` satırlarından sahte TMDB URL'leri kaldırılarak `"hub://category/tr_dub"` ve `"hub://category/tr_sub"` atandı.
   - `ProviderCapabilityMatrix.mapCategoryNameToUnified()` içinde açık kategori adı anahtar kelimeleri (`"dublaj"`, `"altyazı"`, `"yeşilçam"`, `"nostalji"`, `"türk dizi"`, `"belgesel"`, `"anime"`, `"çizgi"`, `"animasyon"`, `"yeni"`, `"popüler"`, `"dizi"`, `"film"`) `providerTypes` fallback'inden **önce** değerlendirilecek şekilde düzeltildi.
   - `getProvidersForCategory()` içinde `TR_DUB` ve `TR_SUB` için genel `TvType.Movie` fallback'i kaldırıldı; yalnızca gerçek dublaj/altyazı kategorisine sahip sağlayıcılar eşleştirilir.

4. **Görünür ve Güvenli Katalog Tanılama (`HubCatalogDiagnostics` — `HubPerformanceSupport.kt`, `HubSettingsManager.kt`, `CloudStreamHubPlugin.kt`):**
   - Her kategori isteğinin kaynağı (`TMDB`, sağlayıcı adı, `HubCatalogCache`), aşaması (`HTTP_JSON`, `PROVIDER_FETCH`, `FALLBACK_TO_PROVIDERS`, `MERGE_COMPLETE`, `CACHE_HIT`, `STALE_FALLBACK`, `VISIBILITY_CHECK`), durumu (`HTTP_200_OK`, `TIMEOUT`, `ERROR:...`), öğe sayısı ve süresi (ms) `HubCatalogDiagnostics` içine kaydedilir.
   - `HubCatalogDiagnostics.sanitize()` tüm `api_key=...`, `Bearer ...`, JWT ve Debrid tokenlarını otomatik olarak `[REDACTED]` ile maskeler; hiçbir gizli anahtar loglanmaz.
   - Ayarlar -> **Tanılama** sekmesine canlı tanılama kutusu ve temizleme/yenileme kontrolleri eklendi.

---

## 3. Android TV CheckBox D-Pad Focus Kök Neden Analizi ve Düzeltmesi

### Kök Neden
- `CloudStreamHubPlugin.kt` içinde `Button` kontrolleri için `applyTvFocus()` ve `Spinner` kontrolleri için `HubSpinnerStylePolicy` tanımlanmışken, `Genel` (sağlayıcı listesi ve hata raporlama), `Katalog` (16 kategori görünürlük satırı), `Oynatma` (3 oynatma tercihi) ve `Performans` (arka plan tarayıcı tercihi) sekmelerindeki **hiçbir `CheckBox` kontrolünde `OnFocusChangeListener`, odak kenarlığı veya odak arka plan vurgusu bulunmuyordu.**
- Android 12 TV Box üzerinde kumanda ile `CheckBox` satırları arasında gezinirken hangi satırın seçili olduğu görülemiyordu.

### Uygulanan Düzeltme (`HubCheckBoxStylePolicy` & `createTvCheckBox` — `CloudStreamHubPlugin.kt`)
- `HubCheckBoxStylePolicy` eklendi (`paletteFor(isDark, accentHex)` ve `formatStateLabel(rawLabel, isChecked, hasFocus)`).
- Tüm sekmelerdeki `CheckBox` kontrolleri `createTvCheckBox(...)` fabrikası ile oluşturuldu:
  - **Odaklandığında (Focused):** Yüksek kontrastlı vurgu arka planı (`accentHex`), **2dp parlak kenarlık** (Koyu temada `#FFFFFF` beyaz, Açık temada `#111111` siyah), kalın (`BOLD`) beyaz metin (`#FFFFFF`), beyaz kutu ikonu (`#FFFFFF`) ve satır başında net odak/durum göstergesi (`▶ [✓]  ...` veya `▶ [ ]  ...`).
  - **Odaktan Çıktığında (Unfocused):** Tema uyumlu normal kart arka planı (Koyu `#252528` / Açık `#F2F2F7`), 1dp ince kenarlık (`#48484E` / `#C5C5CE`), okunaklı metin (`#EEEEEE` / `#111111`), seçili/seçili değil durumuna göre farklılaşan `ColorStateList` kutu rengi ve `[✓]  ...` / `[ ]  ...` durum rozeti.
  - **Katalog Sıralama Satırları (`renderHomeRows`):** `↑` ve `↓` butonlarıyla kategori sırası değiştirildiğinde kumanda odağının kaybolmaması için taşınan satırdaki butona otomatik `requestFocus()` geri yüklemesi eklendi.
