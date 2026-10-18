# CloudStream TR+ v42 — Nihai bağımsız ZIP ve kaynak kodu denetimi

**İncelenen kullanıcı yüklemeleri:** `cloudstream-trplus-v42-main(2).zip`, `cloudstream-trplus-v42-builds(2).zip`.

## 1. Paket doğrulaması — PASS

| Kontrol | Bulgular |
|---|---|
| ZIP bütünlüğü | Her iki arşivde de ZIP CRC kontrolü geçti. |
| Builds paketi | 29 giriş: 26 adet `.cs3`, `plugins.json`, `repo.json`, `SHA256SUMS`. |
| CS3 içeriği | 26/26 dosyada `classes.dex` ve `manifest.json` mevcut. |
| Metadata | 26/26 `fileHash`, `fileSize`, sürüm ve iç manifest uyumlu. |
| SHA256SUMS | 28/28 doğrulandı. |
| CloudStreamHub | Sürüm **42**; derlenmiş eklenti ve `plugins.json` birbiriyle tutarlı. |
| Kaynak arşivi | 583 giriş, ~2,14 MB; `V42-INTEGRATION-FINAL-AUDIT.md`, `V42-ANDROID-TEST-PLAN.md`, `V42-COMPLETION-AUDIT.md` mevcut. |
| Yüksek hacimli eski loglar | Eski ~32 MB ZIP şişkinliği yeni arşivde yok. |

**Gerçek dosya SHA-256 değerleri:**

- `main(2).zip`: `9bc36b4258e189ee445fdf348f05c57cde10ce9a2643bb510722e3391ea59c66`
- `builds(2).zip`: `21d1570dd237e58d8bad798348193649453da68e173904534c1be2e489b22e73`

**Belgelerdeki düzeltme:** ZIP içindeki `README-v42.md` ve `TEST-REPORT-v42.md` halen bir önceki paketleme turunun `b55f...` / `c3a...` değerlerini bildiriyor. Bu değerler yukarıdaki dosyalarla eşleşmiyor. Nihai arşivlerin hash'lerini dış dağıtım raporunda düzelt; kaynak ZIP'in kendi hash değerinin kendi içine yazılması döngüsel/reproducibility açısından sorunlu olabilir, ayrı release manifesti tercih et.

## 2. Kod düzeyinde doğrulanan geliştirmeler

- `CloudStreamHubPlugin.kt`: `pluginContext`, TV ayarları için ayrı kaydırılan içerik ile sabit Kaydet/İptal/Varsayılan alt barı oluşturulmuş.
- `CloudStreamHub.kt`: `getMainPage()` yeni `UnifiedCatalogEngine.getCategoryPage(..., context=...)` akışına bağlanmış; `search()` uygun sağlayıcılarda semaphore ile sınırlı federasyon araması yapıyor.
- `ProviderCapabilityMatrix.kt`: kategori fan-out tek kategoriden birden fazla kategoriye genişletilmiş.
- `UnifiedCatalogEngine.kt`: paralellik sınırı toplam sağlayıcı sayısına uygulanmıyor; uygun hedeflerin tamamı sorgu listesine alınıyor.
- `HubSingleCardMerger.kt`, `HubMediaRegistry.kt`, `HubCatalogCache.kt`: birleşik kart, sağlayıcı referansları ve JSON disk serileştirme/geri yükleme kodu mevcut.
- `HubSettingsManager.kt`: timeout, concurrency, cache TTL, dil politikası ve torrent sıralama tercihleri saklanıyor; bazıları gerçek çalışma akışlarında tüketiliyor.

**Sınır:** Kaynak kodunun mevcut olması, Android TV uygulamasında doğru ve akıcı oynatma, tüm katalogların eksiksiz erişilebilirliği veya TV UI odak davranışını kanıtlamaz. Bu denetimde Android/Gradle testleri yeniden çalıştırılmadı; `BUILD SUCCESSFUL` iddiası Gemini raporuna aittir.

## 3. Kritik sorunlar — yayından önce giderilmeli

### P0-A — Arka plan tarama birleşik katalog önbelleğini bozabilir

- `UnifiedCatalogEngine.kt` `getCategoryPage()` birleşik kategori/sayfa sonucunu `HubCatalogCache.put(categoryName,page,merged,...)` ile saklıyor.
- Aynı dosyadaki `crawlBatch()` her **tekil sağlayıcının** ham kategori sonucunu `HubCatalogCache.put(cat.unifiedCategory,p,list,...)` ile saklıyor.
- `HubCatalogCache.kt` cache anahtarını yalnız `category:page` olarak oluşturuyor; sağlayıcı ID'si bulunmuyor.
- Böylece aynı kategori/sayfa için arka plan işinin sonucu birleşik katalog sonucunun üzerine yazabilir veya birleşik sorgu yalnız bir sağlayıcının önbelleğini okuyabilir. Üstelik `crawlBatch()` kullanıcı ana sayfayı açarken tetikleniyor.

**Düzeltme:** Birleşik sonuçlarla sağlayıcıya özel katalog sayfalarını farklı namespace/cache key ve dosyalara ayır; ham tarama yazıları birleşik önbelleği doğrudan değiştirmesin. Birleşik önbelleği yalnız tam merge sonrası güncelle. Eşzamanlı tarama ve ana sayfa çağrısını kapsayan regresyon testi ekle.

### P0-B — Boş/hatalı sayfada ilerleme imleci artırılıyor

- `UnifiedCatalogEngine.crawlBatch()` içinde ağ timeout'u, `null` yanıt, boş liste ve hata durumları ayrıştırılmadan `HubCatalogCache.saveCrawlerCursor(..., p+1)` çağrılıyor.
- Bunun sonucunda bağlantısı geçici kesilen bir sayfa daha sonra atlanabilir; `hasNext=false` gelen son sayfadan sonraki sayfaları da gereksiz tarar.

**Düzeltme:** İmleci yalnızca başarıyla alınmış ve tutarlı sayfa için ilerlet; timeout/hata durumunda mevcut sayfayı koru ve retry/backoff uygula; `hasNext=false` ile sağlayıcı+kategori için bitiş durumunu işle. Durumları ayrı test et.

### P0-C — Yanlış yapımları birleştirme riski

- `HubSingleCardMerger.isConfidentSameMedia()` yılı bilinen iki yapım için **1 yıl farklılığa izin veriyor** (`abs(yearDiff) > 1` ise reddediyor).
- Ayrıca `HubMatchingEngine.tokenSimilarity(...) >= 0.70` tek başına doğru yapım kimliği kanıtı olabiliyor.
- Kullanıcı hedefi farklı yıl/remake ve farklı içeriklerin asla karışmamasıydı; mevcut eşleştirme bu garantiyi sağlamıyor.

**Düzeltme:** Farklı ve doğrulanmış yapım yıllarını otomatik birleştirme. Benzerlik skorunu tekil kimlik gibi kullanma; IMDb/TMDB, kesin normalize başlık + kesin tür + doğrulanmış yıl gibi güçlü sinyaller bekle. Benzer/yeniden çekilmiş yapımlar için negatif testler yaz.

### P1-D — Birleşik katalog gerçek anlamda sonsuz/kademeli sonuç sunmuyor

- `UnifiedCatalogEngine.getCategoryPage()` bütün hedeflerin `awaitAll()` sonuçlarını bekledikten sonra kullanıcıya tek liste döndürüyor; ilk hızlı sağlayıcının sonuçları ayrı erken iletilmiyor.
- Her sağlayıcının aynı `page` parametresi kullanılıyor; ana sayfa `hasMorePages` bayrağı herhangi bir sağlayıcıda true olursa true oluyor. Kalıcı kategori/sağlayıcı bitiş ve cursor bilgisi ana sayfa sorgusuyla entegre görünmüyor.
- `HubCatalogCache.put()` her kategori/sayfa için **en fazla 150 öğe** saklıyor; kalan öğeler aynı sayfadan düşebilir. 80 dosyalık LRU sınırlaması da 'bütün katalog her zaman cihazda mevcut' vaadini garanti etmez.

**Düzeltme:** Sağlayıcı/kategori/sayfa düzeyinde ayrı cursor ve bitiş durumu tut; birleşik paginated sayfaları veri kaybı olmadan oluştur. 150 sınırı eski öğeleri tamamen kaybettirmesin; devam imleci veya alt sayfalama olsun. Yavaş bir sağlayıcı sonuçları için tüm listeyi bekletmeyen host API uyumlu yaklaşımı doğrula.

### P1-E — Kayıt/yeniden başlatma yalnız önbellek okunduğunda geri yükleniyor

- `HubCatalogCache.loadFromDisk()` JSON referanslarını `HubMediaRegistry.register` ile geri alıyor — olumlu.
- Ancak registry uygulama başlangıcında bütünüyle restore edilmiyor; sadece ilgili kategori/sayfa diskten okunduğunda doluyor. Kullanıcı doğrudan geçmişten veya favoriden detay açarsa, kartın diğer sağlayıcı referansları henüz mevcut olmayabilir.

**Düzeltme:** `load`/`loadLinks` sırasında cache miss veya cold-boot durumunda canonical ID üzerinden hedefli disk lookup veya güvenli yeniden keşif gerçekleştir. Favori/geçmişten doğrudan açılış testini ekle.

### P1-F — Ayar ekranı ve yayın doğrulaması

- Sabit alt bar kodu ve `HubSettingsManager` kalıcılığı mevcut; ancak 720p/1080p D-pad fokus, Kaydet görünürlüğü, gerçek cihazda bütün tercihlerin uygulanması bağımsız doğrulanmadı.
- `CloudStreamProviderRegistryAdapter.getRegisteredProviders()` YTS ve 4KHDHub'ı bilerek genel federasyondan çıkarıyor. Bunların ayrı resolver yolları var; bu bilinçli kapsam sınırlamasını 26/26 tam katalog diye raporlamamak gerekir.
- Yeni aktif Türkçe çizgi film veya eski Türk dizi sağlayıcısı eklenmemiş; 26 aktif eklenti var.

**Düzeltme:** Android emülatörde D-pad/Kaydet ve 3 sağlayıcıdan tek kart/çoklu kaynak testlerini gerçekleştir; kapsam raporunda FULL / SEARCH_ONLY / RESOLVER_ONLY / BLOCKED ayrımını doğru göster.

## 4. Önerilen yayın kararı

**HENÜZ V41'İN ÜZERİNE YAYINLAMA.** ZIP'ler teknik olarak geçerli, fakat birleşik katalog cache çakışması ve hatalı cursor ilerlemesi gerçek kullanıcıda eksik içerik gösterebilir. Önce P0-A, P0-B, P0-C'yi hedefli bir düzeltme paketiyle kapat; ardından paketleri yeniden üret ve emülatörde iki kritik senaryoyu dene:

1. Bir içerik aynı anda üç sağlayıcıdan gelsin → tek kart → üç gerçek link. Arka plan katalog taraması devam ederken bir sağlayıcının verisi diğerlerini silmesin.
2. İnternet geçici kesilsin veya sağlayıcı timeout versin → sayfa atlanmasın; bağlantı geri geldiğinde eksik sayfa indekslensin. Cold-boot sonrası favori kartının kaynakları geri yüklenebilsin.

Android gerçek oynatması yapılmadan `26/26 oynatma çalışıyor` iddiası kurulamaz. Yeni kaynak eklenmemesi ayrı ürün kapsamı eksiğidir ve izinli yeni adaylar bulunduğunda ayrıca ele alınmalıdır.
