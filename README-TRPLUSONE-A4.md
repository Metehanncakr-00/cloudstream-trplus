# TR+ ONE v4 — ÇALIŞAN DİZİYOU KAYNAĞINI BİRLEŞTİRME VE ÇOKLU KAYNAK ONARIMI

**Modül:** `TRPlusOne`  
**Sürüm:** 4  
**Paket:** `com.cloudstream.tr.one`  
**Ürün Adı:** TR+ ONE  
**Çıkış Dosyası:** `TRPlusOne/build/TRPlusOne.cs3`  
**SHA-256:** `F8C4FBB3F159BC420D4FE264D4592F87C66B4A4B2C38B12C76A9AADCB9F2ADDB`  
**Boyut:** 320,703 bayt  
**Fiziksel TV Oynatması:** `NOT_TESTED` (Kullanıcı cihaz doğrulaması bekleniyor)  

---

## 1. GERÇEK TV BOX BULGUSU VE KÖK NEDEN ANALİZİ

### Belirti (Android 12 TV Box)
- Kullanıcı TR+ ONE üzerinde *"Rick ve Morty"* aradı.
- Dizi kartı bulundu, ancak 1. Sezon 1. Bölüm oynatılamadı (0 link).
- Aynı TV Box üzerinde bağımsız **DiziYou** eklentisi açıldığında 1x01 bölümü doğrudan sorunsuz oynatıldı (`storage.diziyou.one/episodes/11501/play.m3u8` HLS akışı ve Türkçe VTT altyazısı ile).
- Diğer sağlayıcılar (SezonlukDizi vb.) ise CloudStream koruması/bot engeli veya yapısal değişiklik nedeniyle link veremedi.

### Kök Neden Denetimi
1. **Erken Yerel Arama Dönüşü (`TRPlusOne.search`)**:
   Eski kodda `local.isNotEmpty()` olduğunda yerel sonuçlar anında döndürülüyor ve uzak sağlayıcı araması `background.launch` ile asenkron arka plana atılıyordu. Kullanıcı karta hemen tıkladığında, arka plandaki DiziYou araması henüz tamamlanmamış ve karta eklenmemiş oluyordu.

2. **Yılsız Dizi Kimlik Uyuşmazlığı (`OneIdentity.key`)**:
   DiziYou arama sonuçlarında yıl bilgisi `null` gelmektedir. Eski `OneIdentity.key()` algoritması, yıl olmadığında `sha("${kind}:${providerId}:${url}")` şeklinde sağlayıcıya özel tekil bir anahtar üretiyordu. SezonlukDizi ise `2013` yılını taşıdığından `sha("SERIES:rick and morty:2013")` anahtarını üretiyordu. İki anahtar farklı olduğundan `CanonicalEngine.merge()` bu iki kaynağı aynı kart altında asla birleştiremiyordu.

3. **Türkçe Bağlaç Farklılığı ("ve" vs "and" vs "&")**:
   DiziYou üzerinde başlık `"Rick ve Morty"`, SezonlukDizi üzerinde ise `"Rick and Morty"` olarak kayıtlıydı. Başlık normalizasyonu yalnızca Türkçe karakterleri (ı->i, vb.) ve sesli harfleri düzeltiyor, fakat `" ve "` ile `" and "` bağlaçlarını eşitlemiyordu.

4. **Yetersiz `load()` Zenginleştirmesi**:
   Kullanıcı kartı açtığında `TRPlusOne.load()` yalnızca halihazırda kartın `listings` listesinde bulunan sağlayıcılara detay isteği gönderiyordu. Kartta sadece SezonlukDizi varsa, DiziYou hiçbir zaman sorgulanmıyor, `OneEpisodeRegistry` tablosuna DiziYou'nun gerçek bölüm adresi (`https://www.diziyou.one/rick-and-morty-1-sezon-1-bolum/`) hiç kaydedilmiyordu.

5. **`loadLinks()` Akışında Alternatif Kaynak Eksikliği**:
   Kayıtlı tek kaynak (SezonlukDizi) TV Box üzerinde Cloudflare/bot korumasına takılıp `yielded=0 links` döndürdüğünde, kartta alternatif çalışan DiziYou kaynağı bulunmadığından oynatma doğrudan başarısız oluyordu.

---

## 2. MİMARİ DEĞİŞİKLİKLER VE DÜZELTMELER

### A. Akıllı Başlık Normalizasyonu (`OneIdentity.kt`)
- Türkçe `" ve "`, İngilizce `" and "` ve sembolik `"&"` bağlaçları tek tip kanonik `" and "` formuna dönüştürüldü:
  ```kotlin
  private val conjunctions = Regex("(?:\\b(?:ve|and)\\b|&)", RegexOption.IGNORE_CASE)
  ```
- Bu sayede `"Rick ve Morty"`, `"Rick and Morty"` ve `"Rick & Morty"` aynı kanonik başlıkta (`"rick and morty"`) buluşur.

### B. Esnek ve Güvenli Çapraz Sağlayıcı Eşleştirme (`OneIdentity.isMatch`)
- Farklı sağlayıcılardan gelen listelemelerin aynı karta ait olup olmadığını belirleyen katmanlı kural seti:
  1. **Tür Uyumu:** Film, Dizi, Anime Film ve Anime Dizi türleri kesinlikle birbirinden ayrı tutulur.
  2. **Dış Kimlik (IMDb / TMDb):** Eşleşen `tt...` IMDb veya TMDb ID varsa doğrudan eşleşir.
  3. **Başlık Uyumu:** Normalleştirilmiş kanonik başlıklar birebir eşit olmalıdır.
  4. **Yıl Uyumluluğu:**
     - Her iki tarafta da yıl varsa, yıllar eşit olmalıdır (Örn: *Doctor Who (1963)* ile *Doctor Who (2005)* kesinlikle birleşmez).
     - Bir tarafta doğrulanmış yıl varken (SezonlukDizi: 2013), diğer tarafta yıl `null` ise (DiziYou: null) ve başlıklar tam eşleşiyorsa güvenle birleştirilir.
     - Her iki tarafta da yıl yoksa ve dış ID bulunmuyorsa, sahte birleşmeleri önlemek için ayrı tutulurlar.

### C. Takma Ad Haritası ve Geriye Dönük Uyumluluk (`OneIndex.kt`)
- `OneIndex` içine kalıcı `aliases: MutableMap<String, String>` eklendi.
- Bir sağlayıcının özel kimliği (örneğin DiziYou'nun yılsız kimliği) ana karta bağlandığında bir takma ad (`alias -> canonicalId`) olarak kaydedilir.
- Kullanıcı eski bir yer imine, geçmişe veya sağlayıcıya özel bir URL'ye (`/media/{aliasId}`) tıkladığında, `OneIndex.media(id)` çağrısı takma adı çözerek doğrudan ana kanonik karta ulaşır.
- Bu yapı `trplusone-index-v1.json` dosyasına şeffaf ve geriye dönük uyumlu şekilde kaydedilir.

### D. Dinamik Arama ve İsteğe Bağlı Zenginleştirme (`TRPlusOne.kt`)
- **Arama:** `TRPlusOne.search()`, yerel önbellek olsa dahi tüm aktif sağlayıcılarda 4000ms zaman aşımı ile paralel arama yapar ve gelen sonuçları dinamik olarak birleştirir.
- **Detay (`load`):** Kart açıldığında eğer kartta 4'ten az sağlayıcı varsa veya aktif sağlayıcılardan biri eksikse, arka planda eksik sağlayıcılarda başlık araması yapılır. Eşleşen kaynaklar karta eklenir ve bölümleri `OneEpisodeRegistry` içine kaydedilir.

### E. Kesintisiz Bölüm Oynatma (`TRPlusOne.loadLinks`)
- Bir bölüm oynatılmak istendiğinde (`https://trplus.one.invalid/episode/{id}/{s}/{e}`), `OneEpisodeRegistry` üzerindeki tüm sağlayıcı hedefleri taranır.
- Bir sağlayıcı hata verirse veya 0 link üretirse (SezonlukDizi), hemen sıradaki sağlayıcıya (DiziYou) geçilir.
- Oynatılabilir akış linkleri hangi sağlayıcıdan geldiğini ve ses/altyazı durumunu belirtecek şekilde etiketlenir:
  `[DiziYou] DiziYou HLS Türkçe Altyazılı [Türkçe Altyazı]`

### F. Android TV Uyumlu Tanılama Ekranı (`TRPlusOnePlugin.kt`)
- Android TV kumandası (D-pad) ile tam uyumlu tanılama arayüzü geri yüklendi:
  - Kumanda odağı için belirgin çerçeve ve arka plan rengi.
  - Kaydırılabilir `TextView` ile son 300 olay günlüğü.
  - "Kopyala" butonu (Android `ClipboardManager` ile logları panoya aktarır).
  - "Temizle" ve "Kapat" butonları.
  - Eklenti ayarları (`Ayarlar -> Tanılama`) içerisinden doğrudan erişilebilir.

---

## 3. ONAYLANAN TANILAMA OLAYLARI (EVENTS)

Aşağıdaki 8 kritik olay `OneDiagnostics` tarafından standart formatta kaydedilmektedir:
1. `SEARCH_PROVIDER_RESULTS`: Sağlayıcı başına bulunan arama sonuç adedi ve süresi.
2. `CANONICAL_MATCH`: Farklı sağlayıcılardan gelen listelemelerin tek kartta birleşmesi.
3. `MEDIA_PROVIDER_LIST`: Bir içerik için mevcut olan tüm sağlayıcıların listesi.
4. `EPISODE_MATCH`: Sezon/bölüm numarasının sağlayıcı özel bölüm bağlantısıyla eşleştirilmesi.
5. `EPISODE_DATA_FOUND`: Bölümün `OneEpisodeRegistry` tablosundan doğrudan bulunması.
6. `PROVIDER_LINKS_START`: Sağlayıcı adaptöründe link çözümleme başlangıcı.
7. `PROVIDER_LINKS_RESULT`: Sağlayıcıdan kaç link çıktığı ve harcanan milisaniye.
8. `PLAYABLE_LINKS_TOTAL`: CloudStream oynatıcısına iletilen toplam geçerli link sayısı.
