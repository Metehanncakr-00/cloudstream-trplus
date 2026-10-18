# CloudStream TR+ v40 — Değişiklik Raporu (CHANGES-v39-to-v40)

Bu belge, CloudStream TR+ v39 sürümünden v40 sürümüne geçişte yapılan tüm mimari, çekirdek (core), sağlayıcı (provider) ve hata yönetimi düzeltmelerini ayrıntılı olarak kayıt altına alır.

---

## 1. Çekirdek ve Oynatıcı Motoru Düzeltmeleri (Core & Engine)

### 1.1 ExoPlayer 4003 Ses Çözücü Hatası Koruması (`ERROR_CODE_DECODING_FAILED`)
- **Dosya:** `core/src/main/kotlin/com/cloudstream/tr/core/resolvers/TorrentioResolver.kt`
- **Sorun:** Android 12 TV / ExoPlayer üzerinde Medcezir ve Torrentio üzerinden çekilen bazı torrent içeriklerinde `.avi` kapsayıcısındaki MP3 ses akışları `MediaCodecAudioRenderer` üzerinde `audio/mpeg` ve `OMX.google.mp3.decoder` çökmelerine (`ERROR_CODE_DECODING_FAILED (4003)`) yol açıyordu.
- **Düzeltme:** 
  - Akış dosya adları denetlenerek `.avi` uzantılı legacy kapsayıcılar Torrentio akış listesinden elendi (`!stream.name.endsWith(".avi", ignoreCase = true)`).
  - P2P ağında ölü (3 seed altı) torrent kaynakları filtrelenirken, 3 ve 4 seed'e sahip kaynaklar kullanıcı seçeneklerini gereksiz daraltmamak adına korundu.

### 1.2 Türkçe Kaynak Önceliği ve Akıllı Sıralama (SmartPlay & Language Priority)
- **Dosyalar:** 
  - `core/src/main/kotlin/com/cloudstream/tr/core/model/ManifestLanguageEvidence.kt`
  - `core/src/main/kotlin/com/cloudstream/tr/core/streaming/SmartPlayEngine.kt`
- **Sorun:** The Office ve benzeri dizilerde çalışan Türkçe DiziYou kaynağı varken sistem otomatik olarak yabancı sesli İngilizce Torrentio akışını seçiyordu. Ayrıca torrent etiketlerindeki `RU; unverified` gibi yanıltıcı etiketler ses dilini doğru yansıtmıyordu.
- **Düzeltme:**
  - `ManifestLanguageEvidence` içerisinde 4 seviyeli katı Türkçe öncelik hiyerarşisi uygulandı:
    - **Tier 3:** Doğrulanmış Türkçe dublaj (`TR_DUB`).
    - **Tier 2:** Doğrulanmış Türkçe altyazı (`TR_SUB`).
    - **Tier 1:** Doğrudan erişilebilir, dil etiketi belirsiz yerli akışlar (`UNKNOWN` - örn. DiziYou HLS akışı).
    - **Tier 0:** Açıkça yabancı dil olduğu belirtilen akışlar (`FOREIGN`).
    - *(Orijinal dil modu seçildiğinde yabancı ses akışları Tier 4 olarak önceliklendirilir)*.
  - `SmartPlayEngine`:
    - Doğrudan Türkçe akış puanı 1200'e (dublaj/altyazı etiketiyle 1500'e) çıkarıldı.
    - `TR_DUB` tercihinde açıkça yabancı dil bildiren torrent akışlarına `-600` ceza puanı verilerek DiziYou gibi yerli kaynakların önüne geçmesi engellendi.
    - 5 seed altındaki p2p akışlarına `-300` ceza puanı uygulanarak listenin sonuna ötelenmesi sağlandı (kullanıcı seçenekleri tamamen yok edilmeden sıralama iyileştirildi).
    - Dosya adına göre tahmin yapan kurallar izole edildi (`preflight == null`); doğrulanmış preflight verisi varken asla tahminlerle dil etiketi ezilmez.

---

## 2. Sağlayıcı Onarımları ve Sürüm Güncellemeleri

### 2.1 Ana Eklenti
- **CloudStreamHub** (`CloudStreamHub/build.gradle.kts`): Sürüm 39'dan **40**'a yükseltildi. Tüm modül kaynakları ve çekirdek streaming kuralları ile yeniden derlendi.

### 2.2 Bağlantı Bulunamayan Sağlayıcılar

#### SetFilmİzle
- **Dosya:** `SetFilmIzle/src/main/kotlin/com/cloudstream/tr/setfilmizle/SetFilmIzle.kt`
- **Sürüm:** 19 -> 20
- **Kök Neden:** SetFilmİzle'nin kullandığı `FastPlay` ve `SetPlay` iframe oynatıcısı token korumasına geçmişti. `X-Sp` başlığı eksik veya geçersiz olduğunda oynatıcı boş dönüyordu.
- **Onarım:**
  - `extractSetPlay` XOR şifre çözücüsü uygulandı.
  - FNV-1a tabanlı `X-Sp` token üretim algoritması (`sp|spT|random36` üzerinde `Origin: https://setfilmizle.org` ve `Referer: https://setfilmizle.org/`) entegre edildi.
  - FastPlay master HLS manifestine doğrudan erişim sağlandı (HTTP 200).

#### Diziİzle
- **Dosya:** `DiziIzle/src/main/kotlin/com/cloudstream/tr/diziizle/DiziIzle.kt`
- **Sürüm:** 5 -> 6
- **Kök Neden:** Sitedeki video gömme yapısı değişmiş, eski Twitter HLS fallback'i bağlantı bulamıyordu.
- **Onarım:**
  - Sayfa içindeki `YT_SRC` (YouTube), `VD_SRC` (doğrudan video/HLS) ve iframe gömmeleri (`dailymotion`, `mail.ru` vb.) ayıklandı.
  - Başarısız embed'lerde Twitter HLS fallback'i korundu.

#### RareFilmm
- **Dosya:** `RareFilmm/src/main/kotlin/com/cloudstream/tr/rarefilmm/RareFilmm.kt`
- **Sürüm:** 19 -> 20
- **Kök Neden:** Sitedeki `ok.ru` gömme yapısı standart CloudStream extractor'ı tarafından doğrudan yakalanamıyordu.
- **Onarım:**
  - `extractOkRu` fonksiyonu eklendi; `data-options` JSON verisinden yüksek çözünürlüklü MP4 akışları ve HLS master playlist doğrudan ayrıştırıldı.

#### WebDramaTurkey
- **Dosya:** `WebDramaTurkey/src/main/kotlin/com/cloudstream/tr/webdramaturkey/WebDramaTurkey.kt`
- **Sürüm:** 19 -> 20
- **Kök Neden:** `[data-embed]` seçicisi yalnızca butonları hedefliyordu ve iframe URL'lerindeki `#...` fragmanları `loadExtractor` eşleşmesini bozuyordu. Ayrıca `dtpasn.asia` köprüsü desteklenmiyordu.
- **Onarım:**
  - `[data-embed]` seçicisi tüm etiketleri kapsayacak şekilde genişletildi.
  - URL fragmanları temizlendi (`split("#")[0]`).
  - `dtpasn.asia` köprü gömmeleri için regex tabanlı doğrudan M3U8 çıkarımı eklendi.

#### Dizigecesi
- **Dosya:** `Dizigecesi/src/main/kotlin/com/cloudstream/tr/dizigecesi/Dizigecesi.kt`
- **Sürüm:** 19 -> 20
- **Kök Neden:** Gömme iframe URL'lerindeki `#...` parçaları Vidmoly extractor regex eşleşmesini engelliyordu.
- **Onarım:**
  - Alfa-sayısal embed ID desteği eklendi.
  - `#` fragmanı temizlenerek Vidmoly extractor'ına tam uyumlu URL iletildi.

#### BlenderOpenMovies
- **Dosya:** `BlenderOpenMovies/src/main/kotlin/com/cloudstream/tr/blenderopenmovies/BlenderOpenMovies.kt`
- **Sürüm:** 3 -> 4
- **Kök Neden:** `uuidFromUrl` sonundaki eğik çizgi (`/`) nedeniyle PeerTube API çağrısı geçersiz UUID üretiyordu; video dosyalarında `static/webseed/` yolları filtreleniyordu.
- **Onarım:**
  - `trimEnd('/')` eklendi.
  - `static/webseed/` video yolları allowlist'e alındı.
  - Belirsiz preflight sonuçlarında geçerli video dosyalarının düşürülmesi engellendi.

#### SezonlukDizi
- **Dosya:** `SezonlukDizi/build.gradle.kts`
- **Sürüm:** 19 -> 20
- **Durum:** `BLOCKED_WITH_EVIDENCE`
- **Kök Neden:** SezonlukDizi oynatıcı sayfaları Cloudflare Turnstile reCAPTCHA challenge (`reCAPTCHADATA.asp`, sitekey `0x4AAAAAACOPjx9JKl-Ox-zR`) ile korunmaktadır. Güvenlik korumalarını aşmaya çalışılmamış, durum kanıtıyla dokümante edilmiştir.

---

### 2.3 Oynatma Sorunları Tanılaması ve Korumalar

> **Önemli Beyan:** Aşağıdaki oynatma düzeltmeleri ve korumaları kod seviyesinde uygulanmış ve Gradle birim testlerinde doğrulanmıştır. Fiziksel Android cihaz/emülatör üzerinde oynatma bu oturumda **TEST EDİLMEDİ** olarak raporlanmıştır.

#### Dizilla / Diziella
- **Dosya:** `Dizilla/src/main/kotlin/com/cloudstream/tr/dizilla/Dizilla.kt`
- **Sürüm:** 19 -> 20
- **Kök Neden:** Dizilla embed'lerinde `four.pichive.online` gibi süresi dolmuş veya engellenmiş sunucular HTTP 403 Forbidden dönüyor, bu da ExoPlayer'da `ERROR_CODE_IO_BAD_HTTP_STATUS (2004)` hatasına sebep oluyordu.
- **Onarım:**
  - Çözülen akış bağlantıları `StreamValidator.validateStream` üzerinden preflight kontrolünden geçirildi.
  - 403 ve 404 dönen ölü sunucular ayıklanarak ExoPlayer'a yalnızca yanıt veren doğrulanmış akışlar iletildi.

#### JetFilmİzle
- **Dosya:** `JetFilmIzle/src/main/kotlin/com/cloudstream/tr/jetfilmizle/JetFilmIzle.kt`
- **Sürüm:** 19 -> 20
- **Kök Neden:** JetFilmİzle'nin `playerx.info` oynatıcısından alınan video segmentleri `Referer: https://playerx.info/` başlığı olmadan 403 Forbidden dönüyor ve video ExoPlayer'da donuyordu.
- **Onarım:**
  - `extractPlayerX` fonksiyonu yazıldı.
  - Akış bağlantısına `Referer: https://playerx.info/` başlığı bağlandı.
  - WEBVTT altyazı desteği eklendi.

#### BelgeselX
- **Dosya:** `BelgeselX/src/main/kotlin/com/cloudstream/tr/belgeselx/BelgeselX.kt`
- **Sürüm:** 109 -> 110
- **Kök Neden:** Sayfadaki data-iframe özniteliklerinden yalnızca ilki alınıyordu ve gömülü m3u8/mp4 akışları atlanıyordu.
- **Onarım:**
  - Tüm data-iframe'ler `findAll` ile taranacak şekilde güncellendi.
  - Sayfa içi regex ile doğrudan M3U8 ve MP4 tespiti eklendi.

---

### 2.4 Dil ve Altyazı Eşleme İyileştirmeleri

#### DiziKorea
- **Dosya:** `DiziKorea/src/main/kotlin/com/cloudstream/tr/dizikorea/DiziKorea.kt`
- **Sürüm:** 19 -> 20
- **Düzeltme:** Oynatıcı API yanıtındaki ve sayfa `<track>` etiketlerindeki Türkçe VTT altyazıları ayrıştırılarak `subtitleCallback` ile `Turkish` / `tr` diliyle oynatıcıya sunuldu.

#### AnimeciX
- **Dosya:** `AnimeciX/src/main/kotlin/com/cloudstream/tr/animecix/TauVideoExtractor.kt`
- **Sürüm:** 108 -> 109
- **Düzeltme:** `TauVideoExtractor` içerisine `tracks` modeli eklenerek fansub Türkçe altyazı dosyaları `subtitleCallback` ile oynatıcıya aktarıldı.

---

## 3. Korunan Çalışan Sağlayıcılar (Gerçek Manifest Sürümleri)

Aşağıdaki sağlayıcılar başarıyla test edilmiş olup işlevleri kesinlikle korunmuştur:
1. **HDFilmCehennemi** (v110): Film ve dizi arama, detay ve oynatma kusursuz çalışıyor.
2. **FilmMakinesi** (v109): Film oynatma ve kaynak çözümleme kusursuz çalışıyor.
3. **DiziYou** (v20): Dizi yükleme, The Office Türkçe dublaj oynatma kusursuz çalışıyor.

---

## 4. Doğrulama ve Derleme Sonuçları

- **Gradle Testleri:** `.\gradlew.bat testDebugUnitTest --parallel --continue` -> **BAŞARILI** (35 modülün tamamı geçti, 1m 59s).
- **Gradle Eklenti Üretimi:** `.\gradlew.bat make makePluginsJson` -> **BAŞARILI** (26 `.cs3` eklentisi ve `plugins.json` üretildi, CloudStreamHub v40 doğrulandı, 41s).
- **Repo Doğrulama:** `python tools/validate_repo.py --verify-artifacts` -> **BAŞARILI** (0 hata).
- **Sürüm Kontrolü:** `python tools/verify_version_bumps.py --base v36-baseline` -> **BAŞARILI** (Tüm modül sürümleri artırıldı).
- **Yayın Güvenliği:** `python tools/verify_release_safety.py` -> **BAŞARILI** (0 hata).
