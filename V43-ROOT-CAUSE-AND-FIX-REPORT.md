# CLOUDSTREAM TR+ v43 — KÖK NEDEN ANALİZİ VE ONARIM RAPORU

**Hedef Sürüm:** `CloudStreamHub` v43 (`version = 43`)  
**Çalışma Dalı:** `trplus/v43-stabilization` (Yerel izole dal — GitHub'a push/yayın yapılmadı)  
**Yedek Konumu:** `backups/v42-pre-v43-stabilization/` (`CloudStreamHub-v42.cs3` ve v42 kaynak dosyaları)

---

## 1. Gerçek Cihaz (Android 12 TV Box) Belirtileri ve Kanıtlanmış Kök Nedenler

Android 12 TV Box üzerinde CloudStreamHub v42 yüklüyken gözlemlenen dört kritik sorunun kod düzeyindeki kanıtlanmış kök nedenleri aşağıda sınıflandırılmıştır:

### Belirti 1: Uygulamanın Ciddi Şekilde Kasması (Super Hub Kaldırıldığında Anında Hızlanması)

1. **Kanıtlanmış Kök Neden 1A — Kart Birleştiricide Tekrarlanan Reflection ve Sınıf Örnekleme Fırtınası (`CloudStreamProviderRegistryAdapter.kt` & `HubSingleCardMerger.kt`):**
   - `HubSingleCardMerger.toCandidate(item)` fonksiyonu, katalogdaki **her bir içerik kartı** için `CloudStreamProviderRegistryAdapter.getAllDiscoverableProviders()` metodunu çağırıyordu (`HubSingleCardMerger.kt:89`).
   - `getAllDiscoverableProviders()` ise hiçbir önbellekleme (cache) yapmadan **her çağrıda** `APIHolder` üzerinde Java Reflection çalıştırıyor ve `knownProviderClasses` listesindeki 23 sağlayıcı sınıfı için `Class.forName(className).getDeclaredConstructor().newInstance()` çağırıyordu (`CloudStreamProviderRegistryAdapter.kt:99-107`).
   - Ana sayfada 16 kategori satırı × ~200 kart işlendiğinde **tek bir ana sayfa açılışında ~70.000+ kez Reflection ve `MainAPI` nesne örneklemesi** tetikleniyor, Android TV Box'ın düşük RAM ve 4 çekirdekli işlemcisinde yoğun Garbage Collection (GC) duraksamalarına yol açıyordu.

2. **Kanıtlanmış Kök Neden 1B — Çağrı Başına Yerel Semaphore ve Eşzamanlı Ağ Fırtınası (`UnifiedCatalogEngine.kt` & `CloudStreamHub.kt`):**
   - CloudStream ana sayfası açıldığında 16 kategori satırı için paralel olarak `getMainPage(page = 1, request)` çağrılır.
   - `UnifiedCatalogEngine.getCategoryPage()` içindeki `Semaphore(maxConcurrency)` **her kategori çağrısı için yerel (local)** oluşturuluyordu (`UnifiedCatalogEngine.kt:121`). Bu nedenle 16 satır aynı anda çalıştığında toplam eşzamanlılık 4 ile sınırlanmak yerine `16 × 4 = 64` eşzamanlı HTML kazıma (Jsoup parse + HTTP) isteğine ulaşıyordu.
   - Buna ek olarak `CloudStreamHub.getMainPage()` her `page == 1` çağrısında `UnifiedCatalogEngine.triggerBackgroundPrewarm()` başlatarak arka planda 23 sağlayıcının tüm kategorilerini (`crawlBatch`) ön plandaki ana sayfa yüklemesiyle aynı anda tarıyordu (`CloudStreamHub.kt:103`).

3. **Kanıtlanmış Kök Neden 1C — İkincil Kategori Eşleşmesinde Tüm Alt Kategorilerin Sorgulanması (`ProviderCapabilityMatrix.kt`):**
   - `ProviderCapabilityMatrix.getProvidersForCategory()` içinde doğrudan kategori adı eşleşmediğinde devreye giren `typeMatch` (tür uyumu) dalı (`ProviderCapabilityMatrix.kt:157-160`), sağlayıcının yalnızca birincil kategorisini eklemek yerine `for (cat in cap.categories)` döngüsüyle **tüm alt kategorilerini** hedef listeye ekliyordu.

4. **Kanıtlanmış Kök Neden 1D — Disk Önbelleği Okumasında Gereksiz Tam Kayıt Yazımı (`HubCatalogCache.kt`):**
   - `HubCatalogCache.loadFromFile()` diskten her kategori sayfası okuduğunda `HubMediaRegistry.flushToDisk(context)` çağırarak (`HubCatalogCache.kt:480`) binlerce medya kaydını içeren `canonical_media_registry_v4.json` dosyasını senkron olarak diske yeniden yazıyordu.

---

### Belirti 2: Film ve Dizi Kataloglarının Kaybolması

1. **Kanıtlanmış Kök Neden 2A — Boş Yanıtların Önbelleği Zehirlemesi (Empty Result Cache Poisoning — `HubCatalogCache.kt` & `UnifiedCatalogEngine.kt`):**
   - Ön plandaki 64 eşzamanlı sağlayıcı isteği + arka plan taraması OkHttp bağlantı havuzunu (`maxRequests = 64`) doldurduğunda TMDB ve sağlayıcı istekleri zaman aşımına (`withTimeoutOrNull`) düşüp `emptyList()` döndürüyordu.
   - `UnifiedCatalogEngine.getCategoryPage()` ise sonuç `emptyList()` olsa dahi `HubCatalogCache.putUnified(categoryName, page, emptyList(), false, context)` çağırarak **bellek ve disk önbelleğindeki geçerli film/dizi listesinin üzerine boş liste yazıyordu** (`HubCatalogCache.kt:153-174`).
   - Ayrıca `HubCatalogCache.loadFromFile()` süresi dolan (TTL aşımı) disk önbellek dosyasını ağdan yeni veri gelmeden önce `file.delete()` ile siliyordu (`HubCatalogCache.kt:399`).

---

### Belirti 3: Bazı Kategorilerin ve Menülerin Açılmaması

1. **Kanıtlanmış Kök Neden 3A — `TR_DUB`, `TR_SUB` ve `CONTINUE_WATCHING` Kategorilerinde Kaynak Eksikliği (`CloudStreamHub.kt`):**
   - `TR_DUB` (`hub://catalog/tr_dub`), `TR_SUB` (`hub://catalog/tr_sub`) ve `CONTINUE_WATCHING` (`hub://catalog/continue_watching`) satırları `mainUrl` (`https://api.themoviedb.org/3`) ile başlamadığı için `tmdbFetch = null` kalıyordu (`CloudStreamHub.kt:71-75, 93`).
   - `CONTINUE_WATCHING` ("İzlemeye Devam Et") için `ProviderCapabilityMatrix.getProvidersForCategory()` hiçbir sağlayıcı döndürmediğinden bu kategori **her zaman boş** dönüyordu.

2. **Kanıtlanmış Kök Neden 3B — `CloudStreamHub.load(url)` ve `loadLinks(data)` İçinde Sağlayıcı/Anime Eşleşme Hatası (`CloudStreamHub.kt`):**
   - `CloudStreamHub.load(url)` içinde sağlayıcı kartları yalnızca `url.contains(p.mainUrl)` ile aranıyordu (`CloudStreamHub.kt:228`). Sağlayıcı URL'sinde `www.` farkı veya dinamik domain değişimi olduğunda `matchedProvider` `null` dönüyor ve içerik detay menüsü açılmıyordu.
   - `baseLoad` bir `AnimeLoadResponse` olduğunda veya `canonicalMedia == null` olduğunda `CloudStreamHub.load(url)` yanıtı `AggregatorLinkPayload` (`hub://item?...`) ile sarmalamadan ham sağlayıcı verisiyle döndürüyordu (`CloudStreamHub.kt:231-279`). Kullanıcı oynatmaya bastığında `CloudStreamHub.loadLinks(data)` ilk satırda `AggregatorLinkPayload.fromUrlData(data) ?: return false` (`CloudStreamHub.kt:384`) nedeniyle doğrudan başarısız oluyordu.

---

### Belirti 4: Ayarlar Ekranındaki Spinner Değerlerinin Görünmemesi

1. **Kanıtlanmış Kök Neden 4 — Özel Koyu/Açık Diyalog İçinde Varsayılan `simple_spinner_dropdown_item` Kullanımı (`CloudStreamHubPlugin.kt`):**
   - `CloudStreamHubPlugin.kt` içindeki 9 `Spinner` bileşeninin tamamı `ArrayAdapter(themedContext, android.R.layout.simple_spinner_dropdown_item, ...)` ile oluşturulmuştu ve `getView()` / `getDropDownView()` metotlarında açık metin/arka plan rengi tanımlanmamıştı (`CloudStreamHubPlugin.kt:372-619`).
   - Android 12 TV'de `#1C1C1E` koyu diyalog zemini (veya açık açılır liste penceresi) üzerinde varsayılan tema metin rengi arka planla aynı tonda kaldığından seçili Spinner değeri ve açılır liste seçenekleri görünmez oluyordu.

---

## 2. Uygulanan Onarımlar (Dosya Bazında)

| Dosya | Uygulanan Onarım |
|---|---|
| `CloudStreamProviderRegistryAdapter.kt` | Yerleşik 23 sağlayıcı örneği `bundledProviderInstances by lazy` ile süreç boyunca **yalnızca 1 kez** oluşturulacak şekilde önbelleklendi. `getAllDiscoverableProviders()` için 30 saniyelik TTL önbelleği ve `www.`/domain farklarını tolere eden hızlı `findProviderForUrl()` / `detectProviderNameForUrl()` eklendi. |
| `HubSingleCardMerger.kt` | `mergeCandidates()` içinde sağlayıcı listesi parti başına tek kez alınıp `toCandidate()` içine aktarıldı; kart başına reflection/instantiation tamamen kaldırıldı. |
| `ProviderCapabilityMatrix.kt` | Doğrudan kategori eşleşmesi olmayan ikincil `typeMatch` durumunda sağlayıcının tüm alt kategorileri yerine yalnızca birincil kategorisi (`cap.categories.first()`) seçilerek gereksiz istek çoğalması önlendi. |
| `HubSettingsManager.kt` | `enableBackgroundCrawler` ayarı eklendi ve varsayılan olarak **`false` (Kapalı)** yapıldı. Kategori zaman aşımı (`effectiveCategoryTimeoutMs`) profil bazlı sınırlandı. |
| `HubCatalogCache.kt` | `putUnified()` içinde boş listelerin (`emptyList()`) mevcut geçerli önbelleği ezmesi engellendi. Ağ hatasında son sağlam kataloğu koruyan `getUnifiedStale()` ve "İzlemeye Devam Et" için `getRecentCatalogItems()` eklendi. `loadFromFile()` içinde gereksiz `flushToDisk()` yazımları kaldırıldı. |
| `UnifiedCatalogEngine.kt` | Çağrı başına yerel semaphore yerine **tüm motor genelinde paylaşılan global `Semaphore`** ve aynı `(categoryName, page)` için oturum içi `Mutex` tekilleştirmesi eklendi. TMDB destekli ana sayfa satırlarında canlı sağlayıcı kazıması satır başına sınırlandı. `triggerBackgroundPrewarm()` varsayılan olarak devre dışı bırakıldı (açılsa bile 15 sn gecikme ve 30 dk bekleme süresi eklendi). |
| `CloudStreamHub.kt` | TMDB istekleri için yedekli `fetchTmdbPage()` / `fetchTmdbDetail()` eklendi. 16 kategorinin tamamına (`TR_DUB`, `TR_SUB`, `CONTINUE_WATCHING` dahil) geçerli veri kaynağı/fallback bağlandı. `load(url)` içinde `AnimeLoadResponse` ve `canonicalMedia == null` durumları `AggregatorLinkPayload` ile sarmalandı; `loadLinks()` içine doğrudan sağlayıcı yedeklemesi eklendi. |
| `CloudStreamHubPlugin.kt` | `HubSpinnerStylePolicy` ve `createTvSpinner()` eklendi. Tüm 9 `Spinner` bileşeninde `getView()` (seçili değer: `▾  ...`) ve `getDropDownView()` (açılır liste: `✓  ...`) için yüksek kontrastlı metin rengi, görünür çerçeve (`GradientDrawable`), `Spinner.MODE_DIALOG` TV uyumu ve D-Pad odak vurgusu sağlandı. Performans sekmesine arka plan tarama anahtarı eklendi. |
| `CloudStreamHub/build.gradle.kts` | Eklenti sürümü `version = 43` olarak güncellendi. |
