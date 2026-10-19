# TR+ ONE v4 — DOĞRULAMA VE TEST RAPORU

**Tarih:** 2026-10-19  
**Ürün Adı:** TR+ ONE  
**Eklenti Sürümü:** 4  
**Paket Adı:** `com.cloudstream.tr.one`  
**Derleme Hedefi:** `:TRPlusOne:make`  
**Çıkış Dosyası:** `TRPlusOne/build/TRPlusOne.cs3`  
**Fiziksel TV Oynatması:** `NOT_TESTED` (Kullanıcı cihaz doğrulaması bekleniyor)  

---

## 1. PAKET VE DERLEME BİLGİLERİ

- **CS3 Dosyası:** `TRPlusOne/build/TRPlusOne.cs3`
- **Dosya Boyutu:** 320,703 bayt (~320 KB)
- **SHA-256 Checksum:** `F8C4FBB3F159BC420D4FE264D4592F87C66B4A4B2C38B12C76A9AADCB9F2ADDB`
- **Manifest Doğrulaması:**
  ```json
  {"requiresResources":false,"version":4,"pluginClassName":"com.cloudstream.tr.one.TRPlusOnePlugin","name":"TRPlusOne"}
  ```
- **DEX Doğrulaması:** Dalvik Executable (DEX) başarıyla derlendi ve CS3 içine paketlendi.

---

## 2. BİRİM TESTİ SONUÇLARI

Çalıştırılan komut: `.\gradlew.bat :TRPlusOne:testDebugUnitTest --console=plain`  
**Sonuç:** `BUILD SUCCESSFUL in 23s` — **47/47 Test PASS (0 Hata, 0 İhmal)**

### Test Dağılımı ve Doğrulanan Davranışlar

#### A. DiziYou Regresyon ve Birleştirme Testleri (`DiziYouRegressionAndUnificationTest`)
- `testTurkishConjunctionNormalization`: `"Rick ve Morty"`, `"Rick and Morty"` ve `"Rick & Morty"` başlıklarının aynı kanonik forma (`"rick and morty"`) dönüştüğünü doğrular. — **PASS**
- `testExistingSezonlukDiziMergesWithRemoteDiziYou`: Yerel indekste SezonlukDizi (yıl=2013) bulunan bir kartın, uzaktan gelen yılsız DiziYou sonucuyla tek kart altında birleştiğini, kart kimliğinin korunduğunu ve takma adın (`alias -> canonicalId`) kaydedildiğini kanıtlar. — **PASS**
- `testDiziYouEpisodePlaybackAndFallbackDelivery`: 1x01 bölümünde SezonlukDizi bot koruması/hata verdiğinde, sistemin otomatik olarak DiziYou'ya geçip çalışan HLS (`.m3u8`) akışını ve Türkçe altyazıyı CloudStream'e başarıyla teslim ettiğini doğrular. — **PASS**
- `testDifferentProductionsDoNotMerge`: Farklı yıllardaki yapımların (*Doctor Who 1963* vs *2005*), farklı türlerin (Film vs Dizi) ve her ikisinde de yıl bulunmayan bağımsız yapımların yanlışlıkla birleşmesini engeller. — **PASS**

#### B. Çoklu Kaynak ve Etiketleme Testleri (`MultiSourceAndTaggingTest`)
- `testAudioAndSubtitleTagging`: Akış linklerinin `[DiziYou] ... [Türkçe Dublaj]` veya `[Türkçe Altyazı]` şeklinde doğru etiketlendiğini doğrular. — **PASS**
- `testEpisodeDirectLinkPlaybackAndDiagnostics`: Doğrudan bölüm linki oynatımı ve tanılama günlüğü üretimini doğrular. — **PASS**
- `testEpisodeMissingFromRegistryTriggersFallbackAndRegisters`: Kayıt defterinde eksik olan bölümlerin sağlayıcı detayından dinamik olarak çekilip kaydedildiğini doğrular. — **PASS**
- `testMultiSourceSingleCardDeduplication`: Farklı sağlayıcılardan gelen aynı içeriklerin tek kartta toplandığını doğrular. — **PASS**

#### C. Kanonik Motor ve Rota Testleri (`CanonicalEngineTest`, `OneRouteCodecTest`, `TRPlusOneTest`)
- Tüm rota biçimleri (`canonical`, `legacy`, TV tarafından dönüştürülmüş `tvPrepended`) test edildi. — **PASS**
- Geçersiz ve yabancı rotaların güvenle reddedildiği doğrulandı. — **PASS**
- Tanılama olaylarının (`LOAD_START`, `ROUTE_PARSED`, `LOAD_LINKS_START`, `LOAD_LINKS_RESULT`) ve hassas veri maskelemesinin (`[REDACTED]`) çalıştığı doğrulandı. — **PASS**

---

## 3. TANILAMA OLAYLARI KONTROLÜ

TR+ ONE v4, TV Box üzerinde oluşan durumları izlemek için aşağıdaki 8 olayı eksiksiz üretmektedir:

| Olay Adı | Tetiklenme Anı | Test Doğrulaması |
|---|---|---|
| `SEARCH_PROVIDER_RESULTS` | Sağlayıcı arama yanıtı döndüğünde | Kod ve mantık doğrulandı |
| `CANONICAL_MATCH` | İki farklı sağlayıcı listelemesi eşleştiğinde | `testExistingSezonlukDiziMergesWithRemoteDiziYou` ile doğrulandı |
| `MEDIA_PROVIDER_LIST` | İçerik detay sayfasında sağlayıcılar sıralandığında | `load()` akışında doğrulandı |
| `EPISODE_MATCH` | Bölüm numarası ve veri bağlantısı eşleştirildiğinde | `testDiziYouEpisodePlaybackAndFallbackDelivery` ile doğrulandı |
| `EPISODE_DATA_FOUND` | `OneEpisodeRegistry` tablosundan doğrudan alındığında | Test edildi ve doğrulandı |
| `PROVIDER_LINKS_START` | Sağlayıcıda `loadLinks` başladığında | Test edildi ve doğrulandı |
| `PROVIDER_LINKS_RESULT` | Sağlayıcı link sonucu verdiğinde | Test edildi ve doğrulandı |
| `PLAYABLE_LINKS_TOTAL` | Oynatıcıya nihai linkler iletildiğinde | Test edildi ve doğrulandı |

---

## 4. GÜVENLİK VE DEĞİŞİKLİK BEYANI

- **GitHub Push Durumu:** Yapılmadı (Kullanıcı talimatına tam riayet).
- **Mevcut 25 Eklenti ve CloudStreamHub:** Hiçbir dosyası bozulmadı veya değiştirilmedi.
- **Fiziksel TV Box Oynatması:** `NOT_TESTED` (Kullanıcı onayı ve test yayını sonrasında cihaz üzerinde denenecektir).
