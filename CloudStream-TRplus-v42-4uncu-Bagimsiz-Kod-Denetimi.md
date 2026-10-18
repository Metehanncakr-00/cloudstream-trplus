# CloudStream TR+ v42 — 4. Bağımsız Kaynak Kodu Denetimi

**Tarih:** 18 Ekim 2026  
**İncelenen dosyalar:** `cloudstream-trplus-v42-main(3).zip`, `cloudstream-trplus-v42-builds(3).zip`  
**Yöntem:** ZIP/manifest/hash doğrulaması ve Kotlin kaynak kodu statik akış incelemesi. **Gradle birim testleri bu ortamda yeniden çalıştırılmadı; Android TV üzerinde gerçek oynatma yapılmadı.**

## 1. Paket doğrulaması

| Paket | Bayt | Gerçek SHA-256 | ZIP testi |
|---|---:|---|---|
| `cloudstream-trplus-v42-main(3).zip` | 2.150.473 | `e1ea3fcf895475137c1e386c56419b596f2bb0f26afe1a5eccc159cdda537e37` | PASS (585 girdi) |
| `cloudstream-trplus-v42-builds(3).zip` | 4.508.221 | `93afebb7958c69a00446b814722493d512b893460ed840f802143bfec3ee3ea3` | PASS (29 girdi) |

26 `.cs3` doğrulandı: her eklentide `classes.dex`, `manifest.json` var; `plugins.json` içindeki `fileSize`, `fileHash`, `version` alanları birebir eşleşiyor. `SHA256SUMS` içindeki 28 kayıt eşleşiyor. `CloudStreamHub` manifest sürümü 42.

**Belge tutarsızlığı:** `README-v42.md:105` ve `TEST-REPORT-v42.md:97`, main ZIP'i `2.150.478` bayt / `5365dc...` diye bildiriyor; gerçek dosya 2.150.473 bayt / `e1ea3f...`. Builds ZIP hash'i doğru. ZIP dosyası bozuk değil, metaveri eskimiş.

## 2. Hâlâ açık kritik sorunlar

### P0-1 — Birleşik sayfada 301+ içeriğin kaybı

**Kanıt:** `CloudStreamHub/src/main/kotlin/com/cloudstream/tr/hub/HubCatalogCache.kt:103-121` ve `:177-196` içinde `items.take(MAX_ITEMS_PER_PAGE)` uygulanıyor; sınır `:30` satırında `300`. `UnifiedCatalogEngine.kt:117-123` tüm birleştirilmiş sonuçları tek sayfa olarak cache'e yazıyor. Artan sonuçları sonraki sayfalara taşıyan kod yok. İlk canlı istek 300'den çok öğe döndürse bile yeniden açılan aynı sayfa önbellekten 300 öğeyle gelir. `UnifiedCatalogEngineTest.kt:315-327` yalnız **200** öğeyi sınamış; 301/500 senaryosunu sınamıyor.

**Gerekli:** 301 ve 500+ farklı kayıt için kalıcı sayfa taşması (overflow) veya doğru devam token'ı; ilk erişim, cache hit, uygulama yeniden başlatma ve sayfa 2/3 durumlarında aynı bütünleşik kümenin kayıpsız gezildiğini test et. Sadece limiti 500'e yükseltme.

### P0-2 — Arka plan kataloğu ana sayfanın indeks kaynağı değil

**Kanıt:** `UnifiedCatalogEngine.kt:199` tarayıcı `HubCatalogCache.putProviderCatalog()` çağırıyor. Ancak çalışma kodunda `HubCatalogCache.getProviderCatalog()` **hiç çağrılmıyor**; tek kullanımı testlerde. `getCategoryPage()` yalnız `getUnified()` kontrol ediyor, cache miss'te her sağlayıcıya canlı istek atıyor. Tarayıcı ürettiği indeksin ana sayfa/arama için tüketicisi yok. Bu nedenle "bütün katalogların indekslenmesi" ve "arka planda eksik içeriklerin giderek tamamlanması" şu an gerçekleşmiyor.

**Gerekli:** provider bazlı artımlı indeksin birleşik ana sayfa ve aramada okunması; taze canlı sonuçlarla tutarlı biçimde birleştirilmesi; tekrar/farklı kategori kaynaklı yinelenmelerin giderilmesi; TTL ve devam cursor'larının indeksle eşlenmesi. Bütün sağlayıcıların tüm kataloğu ancak listeleme API'lerinin izin verdiği ölçüde mümkün.

### P0-3 — Bağımsız taramalarda tek kart kaynaklarının üzerine yazılması

**Kanıt:** `UnifiedCatalogEngine.kt:201` her sağlayıcıdan gelen sayfaya ayrı `HubSingleCardMerger.mergeCandidates(fetchedItems, provider.name)` çağırıyor. `HubSingleCardMerger.kt:174-199` yalnız verilen sayfa içindeki öğeleri birleştiriyor; `:220-239` aynı başlık/yıl için aynı `canonicalId` üretebiliyor. `HubMediaRegistry.kt:41-67` içindeki `register()` mevcut ID'nin referanslarını birleştirmiyor: `byId[media.id] = media` ile eskisini değiştiriyor. Sonuç: P1 kaynaklı X önce, P2 kaynaklı X sonra tarandığında kayıt en sonunda yalnız P2 referansına sahip olabilir. Birleşik ana sayfanın tek çağrıda yaptığı kaynak toplamayı bu hata zorunlu olarak etkilemez; **arka plan taraması ve kalıcı indeksin toplu birikimi** etkilenir.

**Gerekli:** Aynı canonical ID altında farklı sağlayıcı referanslarını `providerName + url` bazında birleştir, duplicate'leri filtrele, eski kaynakları koru; testte P1/P2/P3 ayrı taramalar, yeniden başlatma ve favoriden direkt açılış sonunda 3 kaynağı doğrula.

## 3. Ek kalite riskleri

- `HubMediaRegistry.kt:35-37` kalıcı kaynak kaydı `HubCatalogCache.getDiskCacheDir()` üzerinden Android `cacheDir` altına yazılıyor; sistem bu dosyayı silebilir. Ayrıca `HubCatalogCache.kt:227-232` önbellek dizinindeki eski **tüm** dosyaları ayıklıyor; özel `canonical_media_registry_v4.json` dosyasını bu işlemden korumuyor. Kalıcı favori ve geçmiş kaynakları için `filesDir` veya ayrı güvenilir dosya konumu kullan.
- `HubMediaRegistry.kt:66-68` her kart kaydında bütün kayıt haritasını yeniden JSON dosyasına yazıyor. Birleşik 300+ öğede yoğun senkron disk I/O üretebilir. Batch/debounce ve atomik dosya değişimini düşün; düşük RAM TV senaryosunda ölç.
- `UnifiedCatalogEngine.kt:109-114`: TMDB fetch tanımlıysa ve herhangi içerik geldiyse `hasMorePages=true` yapıyor; gerçek TMDB son sayfada bile sonrasına geçilebilir gösterimi olabilir.
- `UnifiedCatalogEngine.kt:233-240`: Arka plan işinin ömrü `CoroutineScope(Dispatchers.IO)` ile başlıyor, belirgin lifecycle iptal yolu yok; iptal ve yeniden başlatma güvenliğini doğrula.
- `CloudStreamProviderRegistryAdapter.kt`: 23 bilinen katalog sağlayıcısı listeleniyor; YTS ve FourKHDHub ayrı resolver'lar, Hub ise kendisi. **26 paket**, **26 katalog sağlayıcısı** demek değildir.
- Cache'in başarısız sağlayıcılardan gelen eksik birleşik sonuçları uzun TTL boyunca saklaması riski için kısmi sonuç/başarısız kaynak politikası gerekir.

## 4. Kabul kapıları

1. **301 / 500 / 1000 örnek içerik:** ilk istek, cache hit, devam sayfaları, yeniden başlatma = kayıp 0, tekrar 0.
2. **Çok sağlayıcılı artımlı tarama:** provider ham indeksini sonraki birleşik katalog/arama isteği gerçekten okuyor (spy test ve gerçek çağrı zinciri).
3. **Ayrı gelen P1/P2/P3:** tek canonical kartta üç referans; yeniden başlatmada üçü korunuyor.
4. **Kalıcı registry:** Android `cacheDir` evrimi ve önbellek tahliyesi kaynak referanslarını yok etmiyor.
5. **Android emülatörü:** Kaydet görünür ve çalışır; bir film tek kartta çoklu kaynak açar; en az bir gerçek video oynar. **Bu testler yapılmadan gerçek oynatma PASS değildir.**
6. **Teslim:** Belgeler gerçek nihai ZIP hash'lerini ve boyutlarını yansıtır; tamamlandıktan sonra dosyalar tekrar değişirse checksum belgesi yeniden üretilir.

**Yayın kararı:** `HOLD`. GitHub'daki v41 sürümünü koru. Kod-P0 düzeltmeleri ve Android testi geçildikten sonra v42'yi yayınla.
