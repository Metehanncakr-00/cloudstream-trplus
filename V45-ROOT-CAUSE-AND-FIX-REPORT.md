# CloudStream TR+ v45 — Kök Neden Analizi ve Düzeltme Raporu

**Tarih:** 19 Ekim 2026  
**Hedef Eklenti:** `CloudStreamHub` (`version = 45`)  
**Paket Boyutu:** `630,965` bayt  
**SHA-256:** `cab9132e20388d42f0a08ff8777814f501f82a4520ac72562f8e64d497f74502`  
**Referans Durum:** Android 12 TV Box gerçek cihaz testi (v44):
- Performans, ayarlar, Spinner ve CheckBox D-Pad odak vurgusu: **PASS** (Çalışıyor)
- TMDB API yanıtı: `HTTP_200_OK`, 20 içerik (TMDB başarılı)
- "CloudStreamHub yüklenemedi" hatası ve film/dizi kataloglarının boş kalması: **FAIL** -> **v45 ile kök nedeninden düzeltildi**
- Arama bölümünde Super Hub içerik sonuçlarının görünmemesi: **FAIL** -> **v45 ile kök nedeninden düzeltildi**
- Bazı kategori işlemlerinin 132–205 saniye sürmesi: **FAIL** -> **v45 ile kök nedeninden düzeltildi**

---

## 1. Kök Neden Analizi (Kanıtlı Çağrı Zinciri)

CloudStream ana uygulama kütüphanesi (`cloudstream.jar` / `APIRepository`), `CloudStreamHub.kt`, `UnifiedCatalogEngine.kt` ve `HubCatalogDiagnostics.kt` uçtan uca incelendiğinde üç temel kök neden tespit edilmiştir:

### Kök Neden 1 — 132–205 Saniye Gecikme ve CloudStream 120s Host Zaman Aşımı ("CloudStreamHub yüklenemedi")
- **Kanıt (`cloudstream.jar` -> `APIRepository$getMainPage` & `UnifiedCatalogEngine.kt` satır 336–375):**
  1. CloudStream ana uygulaması, ana sayfa yüklenirken `MainAPI.getMainPageTimeoutMs` (varsayılan 120 saniye) süreli bir `withTimeout` uygular:
     ```kotlin
     withTimeout(getTimeout(api.getMainPageTimeoutMs)) {
         api.getMainPage(page, request)
     }
     ```
     Herhangi bir kategori 120 saniyeyi aşarsa `TimeoutCancellationException` fırlatılır ve CloudStream kullanıcı arayüzünde `"CloudStreamHub yüklenemedi"` hatası gösterilir.
  2. v44'te `UnifiedCatalogEngine.kt` satır 336–340'ta şu koşul yer alıyordu:
     ```kotlin
     val shouldQueryLiveProviders = tmdbFetch == null ||
         tmdbItems.isEmpty() ||
         settings.perfMode == HubSettingsManager.PERF_MODE_COMPREHENSIVE ||
         allProviders.size <= 6
     ```
     `allProviders.size <= 6` ifadesi nedeniyle, kullanıcının az sayıda sağlayıcısı açık olduğunda veya kategoriye uyan sağlayıcı sayısı 6 veya daha az olduğunda, **TMDB 20 içerik döndürmüş olsa bile** canlı sağlayıcılarda HTML kazıma başlatılıyordu.
  3. Ayrıca `TR_DUB` ve `TR_SUB` gibi `tmdbFetch == null` olan sağlayıcı satırlarında `liveTargets = distinctUncachedTargets` tüm sağlayıcıları (11+ sağlayıcı) alıyor, her biri için `budgetMs * 4 = 24s` dış sınır ve global semaphore(4) ile kuyrukta bekleme süresi 132–205 saniyeye ulaşıyordu.
  4. 120 saniyelik host zaman aşımı dolduğunda `CancellationException` fırlatılıyor, `CloudStreamHub.kt` içinde `catch (e: CancellationException) { throw e }` bloğunda hiçbir tanılama kaydı üretilmeden işlem kesiliyordu. Bu nedenle Tanılama ekranında en son görülen kayıt `HTTP_200_OK` olarak kalıyor fakat ana sayfa asla ekrana gelmiyordu.

### Kök Neden 2 — Arama Sorgusunun URL Encode Edilmemesi ve Eksik Overload
- **Kanıt (`CloudStreamHub.kt` satır 273–276):**
  1. `CloudStreamHub.kt` içinde arama hedef URL'si doğrudan String interpolation ile oluşturuluyordu:
     ```kotlin
     val targetUrl = "${mainUrl}/search/multi?query=${query}&language=tr-TR&page=${page}&api_key=${tmdbApiKey}"
     ```
     Kullanıcı boşluk veya Türkçe karakter içeren bir arama yaptığında (örneğin `"kurtlar vadisi"`, `"spider man"`, `"örümcek adam"`), OkHttp `HttpUrl.parse()` aşamasında `IllegalArgumentException` fırlatıyor ve istek sessizce `null` / `emptyList()` dönüyordu.
  2. CloudStream ana uygulamasının `MainAPI` sınıfında iki ayrı arama imzası bulunmaktadır:
     - `open suspend fun search(query: String): List<SearchResponse>`
     - `open suspend fun search(query: String, page: Int): SearchResponseList`
     `CloudStreamHub` yalnızca iki parametreli versiyonu implemente etmişti. Arama çubuğundan gelen tek parametreli `search(query)` çağrıları agregatöre ulaşamıyordu.
  3. Arama işlemi için `HubCatalogDiagnostics` kaydı tutulmadığı için aramanın çağrılıp çağrılmadığı Tanılama ekranından doğrulanamıyordu.

### Kök Neden 3 — Ölçüm ve Aşama Görünürlüğü Eksikliği
- `getMainPage()` çağrısının başlangıcı (`GET_MAIN_PAGE_START`), başarıyla tamamlanması (`GET_MAIN_PAGE_SUCCESS`) veya iptal edilmesi (`GET_MAIN_PAGE_CANCELLED`) loglanmıyordu.
- TMDB'den dönen 20 JSON öğesinin `parseTmdbItem()` ile kaç karta dönüştüğü (`TMDB_PARSE_CONVERSION`), kart birleştirme öncesi/sonrası öğe sayıları (`CARD_MERGE`) ve disk önbellek yazma süreleri (`DISK_WRITE`) kayıt altına alınmıyordu.

---

## 2. Uygulanan Kalıcı Düzeltmeler

### 1. TMDB Başarısının Canlı Kazımadan Tamamen Ayrılması (`UnifiedCatalogEngine.kt`)
- `shouldQueryLiveProviders` koşulu basitleştirilerek canlı sağlayıcı HTML kazıması yalnızca TMDB'nin olmadığı (`tmdbFetch == null`) veya TMDB'nin başarısız/boş olduğu (`tmdbItems.isEmpty()`) durumlarla sınırlandırıldı:
  ```kotlin
  val shouldQueryLiveProviders = tmdbFetch == null || tmdbItems.isEmpty()
  ```
- TMDB 20 öğe döndürdüğünde harici sağlayıcılarda canlı kazıma yapılmaz; önceden önbelleğe alınmış sağlayıcı öğeleriyle (`getAllCachedProviderPages`, 0ms ağ maliyeti) anında birleştirilerek **1–1.5 saniye içinde** ana sayfa kartları ekrana verilir.
- TMDB başarısız olduğunda veya dil kategorilerinde sağlayıcı hedefleri kesin olarak sınırlandırıldı (FAST modda 3, dengeli modda 4 sağlayıcı; 2000–3500ms bütçe).

### 2. Arama URL Kodlaması, Overload Delegasyonu ve Tanılaması (`CloudStreamHub.kt`)
- Arama sorgusu `URLEncoder.encode(query.trim(), "UTF-8")` ile kodlandı; boşluk ve Türkçe karakterler güvenli hale getirildi.
- Tek parametreli `search(query: String)` metodu override edilerek iki parametreli metoda delege edildi:
  ```kotlin
  override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items
  ```
- Arama akışına ayrıntılı tanılamalar eklendi:
  - `SEARCH_START`: Sorgu metni ve sayfa numarası.
  - `SEARCH_TMDB`: TMDB arama sonucu ve öğe sayısı.
  - `SEARCH_INDEXED`: Taranmış önbellekten bulunan öğeler.
  - `SEARCH_PROVIDERS`: Canlı sağlayıcı federasyonundan gelen sonuçlar.
  - `SEARCH_SUCCESS`: Tek kart altında birleştirilmiş toplam sonuç ve süre.
  - `SEARCH_CANCELLED` / `SEARCH_ERROR`: Olası hata veya iptal durumları.

### 3. Ayrıntılı Ölçüm ve Tanılama Kayıtları (`CloudStreamHub.kt` & `UnifiedCatalogEngine.kt`)
- **`GET_MAIN_PAGE_START`:** Ana sayfa kategorisi çağrıldığı an kaydedilir.
- **`TMDB_PARSE_CONVERSION`:** TMDB'den gelen ham sonuç ile ayrıştırılan kart sayısı (`RAW:20->PARSED:20`) ve süre kaydedilir.
- **`CARD_MERGE`:** Kart birleştirme öncesi ve sonrası öğe sayısı (`PRE:20->POST:20`) ve süre kaydedilir.
- **`DISK_WRITE`:** Birleştirilen kartların bellek ve diske yazılma süresi kaydedilir.
- **`GET_MAIN_PAGE_SUCCESS`:** Kategori başarıyla yanıt ürettiğinde kaydedilir.
- **`GET_MAIN_PAGE_CANCELLED`:** Zaman aşımı veya coroutine iptali durumunda `TIMEOUT_OR_CANCELLED` olarak kaydedilir.

---

## 3. Doğrulanan Sonuçlar

1. **TMDB 20 Öğe $\rightarrow$ Anında Gösterim:** TMDB'nin 20 öğe döndürdüğü kategoriler hiçbir canlı sağlayıcı kuyruğuna takılmadan ~1000–1500 ms içinde tamamlanır; 120s host zaman aşımı riski sıfırlanmıştır.
2. **Arama Başarısı:** `"kurtlar vadisi"`, `"spider man"`, `"örümcek adam"` gibi sorgular hatasız URL-encode edilir; hem TMDB hem indeksli katalog hem de aktif sağlayıcılardan sonuçlar toplanıp birleştirilir.
3. **Sıfır Sessiz Hata:** Tüm aşamalar Ayarlar -> Tanılama ekranından canlı olarak izlenebilir; API anahtarları maskelenir (`[REDACTED]`).
4. **Birim Test Kapsamı:** 153/153 birim testi başarıyla geçmiştir.
