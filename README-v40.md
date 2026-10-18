# CloudStream TR+ v40 — Kullanım ve Kurulum Kılavuzu (README-v40)

CloudStream TR+ v40, Türkçe içerik öncelikli, Android TV ve mobil cihazlar için optimize edilmiş CloudStream 3/4 eklenti deposudur.

---

## 1. Kurulum Adımları

### Yöntem A: CloudStream Uygulamasından Repository Ekleme (Önerilen)
1. Android cihazınızda **CloudStream** (sürüm 4.8.0 veya üzeri) uygulamasını açın.
2. **Ayarlar (Settings) > Eklentiler (Extensions)** bölümüne gidin.
3. **Depo Ekle (Add Repository)** butonuna tıklayın.
4. Aşağıdaki bilgileri girin:
   - **Depo Adı:** `CloudStream TR+`
   - **Depo URL:** `https://raw.githubusercontent.com/Metehanncakr-00/cloudstream-trplus/builds/repo.json`
5. **İndir / Ekle** butonuna basarak depoyu kaydedin ve listelenen eklentileri (başta `CloudStreamHub`, `HDFilmCehennemi`, `FilmMakinesi`, `DiziYou`, `SetFilmIzle`, `DiziIzle`) yükleyin.

### Yöntem B: Çevrimdışı / Yerel `.cs3` Dosyaları ile Kurulum
1. `cloudstream-trplus-v40-builds.zip` arşivini açın.
2. Cihazınıza yüklemek istediğiniz sağlayıcıların `.cs3` dosyalarını aktarın (örn. ADB, USB bellek veya dosya yöneticisi ile).
3. CloudStream uygulamasında **Eklentiler > Dosyadan Yükle (.cs3)** seçeneğini kullanarak eklentileri tek tek yükleyin.

---

## 2. Sağlayıcı Manifest Sürümleri ve Değiştirilen Dosyalar

### Çekirdek ve Sağlayıcı Sürümleri (Manifest Doğrulaması):
- **CloudStreamHub** (v40): TMDB Keşif & Sağlayıcı Birleştirici ana eklenti (v39'dan v40'a yükseltildi).
- **HDFilmCehennemi** (v110): Korunan çalışan sağlayıcı.
- **FilmMakinesi** (v109): Korunan çalışan sağlayıcı.
- **DiziYou** (v20): Korunan çalışan sağlayıcı, The Office Türkçe dublaj testleri başarılı.
- **SetFilmIzle** (v20): FastPlay XOR ve `X-Sp` token çözümü entegre edildi.
- **DiziIzle** (v6): `YT_SRC`, `VD_SRC` ve modern iframe ayrıştırıcıları eklendi.
- **RareFilmm** (v20): OkRu data-options MP4 ve HLS çıkarımı eklendi.
- **WebDramaTurkey** (v20): `[data-embed]` seçici genişletmesi, URL fragman temizliği, `dtpasn.asia` M3U8 desteği eklendi.
- **Dizigecesi** (v20): Alfanümerik embed ID desteği ve Vidmoly URL fragman temizliği yapıldı.
- **BlenderOpenMovies** (v4): UUID slash temizleme, `static/webseed/` video yolu desteği eklendi.
- **Dizilla** (v20): StreamValidator preflight filtreleme (HTTP 2004 / 403 Forbidden önleyici) eklendi.
- **JetFilmIzle** (v20): `playerx.info` Referer başlığı entegrasyonu (donma önleyici) ve WEBVTT altyazı desteği eklendi.
- **BelgeselX** (v110): Çoklu data-iframe ayrıştırması ve doğrudan regex video çıkarımı eklendi.
- **DiziKorea** (v20): Player API ve sayfa içi Türkçe altyazı bağlantıları eklendi.
- **AnimeciX** (v109): TauVideo Türkçe altyazı parçaları ayrıştırması eklendi.
- **SezonlukDizi** (v20): Cloudflare Turnstile reCAPTCHA koruması dokümante edildi (`BLOCKED_WITH_EVIDENCE`).

### Çekirdek (Core) Değişiklikleri:
- `core/src/main/kotlin/com/cloudstream/tr/core/resolvers/TorrentioResolver.kt`: `.avi` kapsayıcı filtreleme (MediaCodec 4003 ses çözücü hatasını önleme), ölü p2p (3 seed altı) filtreleme.
- `core/src/main/kotlin/com/cloudstream/tr/core/model/ManifestLanguageEvidence.kt`: 4 katmanlı Türkçe kaynak öncelik modeli uygulandı.
- `core/src/main/kotlin/com/cloudstream/tr/core/streaming/SmartPlayEngine.kt`: Yerli Türkçe yayınlara +1200 / +1500 puan, dublaj modunda yabancı torrentlere -600 puan cezalandırma, 5 seed altındaki p2p kaynaklara -300 puan cezalandırma uygulandı.

---

## 3. Kod Seviyesinde Uygulanan Hata Düzeltmeleri ve Tanılamalar

> **Doğrulama Notu:** Aşağıdaki hata düzeltmeleri kaynak kodda ve Gradle birim testlerinde doğrulanmıştır. Android cihaz veya emülatör üzerinde bizzat fiziksel oynatma bu oturumda **TEST EDİLMEDİ** olarak sınıflandırılmıştır; gerçek Android doğrulaması kullanıcı tarafından cihaz üzerinde yapılmalıdır.

1. **HTTP 2004 (`ERROR_CODE_IO_BAD_HTTP_STATUS`) Tanılama ve Filtresi:**
   - Dizilla akışlarında `four.pichive.online` gibi süresi dolmuş veya 403 Forbidden dönen sunucular `StreamValidator.validateStream` preflight kontrolü ile oynatıcıya sunulmadan önce filtrelenir.
2. **MediaCodec 4003 (`ERROR_CODE_DECODING_FAILED`) Önleme:**
   - Medcezir ve Torrentio üzerinde `audio/mpeg` ve `OMX.google.mp3.decoder` çökmesine yol açan `.avi` kapsayıcısındaki MP3 ses parçaları Torrentio akış listesinden elenir.
3. **JetFilmİzle Donma / Takılma Önleme:**
   - `playerx.info` oynatıcısından alınan video segmentlerinin gereksinim duyduğu `Referer: https://playerx.info/` başlığı doğrudan `ExtractorLink` üzerine iliştirilir.
4. **The Office ve Dizi İçeriklerinde Türkçe Kaynak Önceliği:**
   - Doğrulanmış Türkçe dublaj veya altyazı akışı mevcutken yabancı torrent akışlarının otomatik seçilmesi engellenmiş; yerli Türkçe akışlara yüksek öncelik puanı atanmıştır.
5. **Torrentio Dil Etiketleri:**
   - `RU; unverified` gibi belirsiz etiketlerin doğrulanmış ses dili olarak algılanması önlenmiştir.

---

## 4. Bilinen Durumlar ve Torrentio Davranışı

- **Torrentio Seed Filtresi ve Sıralama Davranışı:**
  - P2P ağında 3 seed'in altındaki (tamamen ölü/bağlanılamayan) torrent kaynakları `TorrentioResolver` tarafından elenir.
  - Kullanıcı seçeneklerini gereksiz daraltmamak adına 3 ve 4 seed'e sahip kaynaklar listelenmeye devam eder; ancak `SmartPlayEngine` tarafından -300 ceza puanı uygulanarak otomatik olarak listenin sonuna ötelenir.
  - Debrid veya doğrudan HTTP/HTTPS önbellek bağlantısı içeren torrent kaynakları seed kısıtlamasından etkilenmez.
- **SezonlukDizi (`BLOCKED_WITH_EVIDENCE`):**
  - SezonlukDizi oynatıcı sayfaları Cloudflare Turnstile reCAPTCHA challenge (`reCAPTCHADATA.asp`, sitekey `0x4AAAAAACOPjx9JKl-Ox-zR`) ile korunmaktadır. Proje etik ve yasal ilkeleri gereği bot korumalarını delmeye yönelik yasa dışı kod eklenmemiştir.

---

## 5. Uygulanabilir Android Test Listesi (Doğrulama Kılavuzu)

Aşağıdaki içerikler, Android TV / Mobil cihazda CloudStream TR+ v40 üzerinden kullanıcı tarafından test edilebilir:

1. **Dizi: The Office (Türkçe Dublaj Kontrolü)**
   - **Sağlayıcı:** `CloudStreamHub` veya `DiziYou`
   - **Hedef:** 1. Sezon 1. Bölüm oynatıldığında Türkçe dublajlı DiziYou akışının otomatik seçildiğini doğrulayın.
2. **Dizi: Medcezir (Ses Çözücü 4003 Kontrolü)**
   - **Sağlayıcı:** `CloudStreamHub` / `StarTV`
   - **Hedef:** Oynatma başlatıldığında `MediaCodecAudioRenderer` çökmesi (4003) yaşanmadan HLS veya uyumlu MP4 akışının açıldığını doğrulayın.
3. **Film: Güncel Film (JetFilmİzle Donma Kontrolü)**
   - **Sağlayıcı:** `JetFilmIzle`
   - **Hedef:** Film başlatıldığında ilk 30 saniye ve sonraki segmentlerde donma olmadan akışın sürdüğünü doğrulayın.
4. **Dizi: Yabancı Dizi (Dizilla HTTP 2004 Kontrolü)**
   - **Sağlayıcı:** `Dizilla`
   - **Hedef:** Seçilen bölümde `ERROR_CODE_IO_BAD_HTTP_STATUS (2004)` hatası vermeden sağlam alternatif kaynakların çalıştığını doğrulayın.
5. **Film: Yerli / Yabancı Film (SetFilmİzle FastPlay Kontrolü)**
   - **Sağlayıcı:** `SetFilmIzle`
   - **Hedef:** "Bağlantı bulunamadı" uyarısı yerine FastPlay master HLS akışının listelendiğini doğrulayın.
6. **Kısa Film: Big Buck Bunny / Sintel (BlenderOpenMovies Kontrolü)**
   - **Sağlayıcı:** `BlenderOpenMovies`
   - **Hedef:** PeerTube video listesinden MP4 / WebSeed akışının doğrudan açıldığını doğrulayın.
