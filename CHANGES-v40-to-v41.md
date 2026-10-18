# CloudStream TR+ v40 -> v41 Değişiklik Raporu (CHANGES-v40-to-v41)

Bu belge, CloudStream TR+ v40 sürümünden v41 sürümüne geçişte uygulanan mimari iyileştirmeleri, hata düzeltmelerini ve sağlayıcı güncellemelerini detaylandırmaktadır.

---

## 1. Genel Özet ve Mimari İyileştirmeler

- **CloudStreamHub Sürüm Yükseltmesi:** Süper eklenti (TMDB Keşif & Aggregator) sürümü `40` -> `41` yapılmıştır.
- **Tüm Sağlayıcıların Bytecode ve Sürüm Senkronizasyonu:** Çekirdek (`core/`) paylaşılan bileşenleri güncellendiği için 26 aktif eklentinin tamamı CloudStream istemcisinin güncellemeleri algılamasını sağlayan sürüm artışları almıştır (`tools/verify_release_safety.py` doğrulanmıştır).
- **Gradle & Repository Doğrulaması:** `tools/validate_repo.py --verify-artifacts` ve `tools/verify_release_safety.py` testlerinden sıfır hatayla geçilmiştir.
- **Birim Testleri:** `core/src/test/` ve `AnimeciX` bünyesindeki 61 adet birim testi %100 başarı oranıyla tamamlanmıştır.

---

## 2. Sağlayıcı Bazlı Kod Değişiklikleri

### P0 — Kritik Sağlayıcı Onarımları

1. **HDFilmDelisi (v19 -> v20):**
   - **Sorun:** Oynatma başlatıldığında siteden alınan sahte honeypot oynatıcı devreye giriyor ve "İlgili içeriği web sitemizden izleyin" videosu oynatılıyordu.
   - **Çözüm:** Next.js RSC (React Server Components) payload'ı ayrıştırılarak `https://pl.vixolity.com/<slug>` adresi yakalanmış; `decryptVixolity()` ters Base64/Hex kaydırma algoritmasıyla gerçek `https://ha.vixolity.com/vs/<imdb>` API'si çözülmüştür. Türkçe ses akışı içeren Master HLS (HTTP 200) elde edilmiştir.
   - **Dosyalar:** `HDFilmDelisi/src/main/kotlin/com/cloudstream/tr/hdfilmdelisi/HDFilmDelisi.kt`, `HDFilmDelisi/build.gradle.kts`.

2. **DiziGecesi (v20 -> v21):**
   - **Sorun:** Vidmoly köprüsü `vidmoly.net/embed-...html#...` döndürdüğünde eklenti "Bağlantı bulunamadı" hatası veriyordu.
   - **Çözüm:** `core/VidmolyTrExtractor.kt` içerisine `VidmolyNetExtractor` ve `VidmolyMeExtractor` sınıfları eklenmiş; `#` fragmanları temizlenerek Vidmoly çıkarıcısı tam uyumlu hale getirilmiştir.
   - **Dosyalar:** `core/src/main/kotlin/com/cloudstream/tr/core/extractors/VidmolyTrExtractor.kt`, `Dizigecesi/src/main/kotlin/com/cloudstream/tr/dizigecesi/Dizigecesi.kt`, `Dizigecesi/build.gradle.kts`.

3. **Dizilla (v20 -> v21):**
   - **Sorun:** JSON yanıtı `{"success":true,"response":"<CIPHER>"}` formatında geldiğinde arama başarısız oluyordu; Pichive oynatıcısında referer eksikliği nedeniyle HTTP 2004 (`ERROR_CODE_IO_BAD_HTTP_STATUS`) oluşuyordu.
   - **Çözüm:** Arama yanıtındaki `response` alanı AES deşifre motoruna yönlendirildi; Pichive iframe URL'leri `four.pichive.online` CDN ve zorunlu Referer başlıklarıyla donatıldı.
   - **Dosyalar:** `Dizilla/src/main/kotlin/com/cloudstream/tr/dizilla/Dizilla.kt`, `Dizilla/build.gradle.kts`.

4. **SetFilmİzle (v20 -> v21):**
   - **Sorun:** FastPlay oynatıcısındaki JSON değişken formatı değişmiş (`stream: "..."` ve tırnaksız `spT`), eski regex eşleşmediği için bağlantı bulunamıyordu.
   - **Çözüm:** Regex kalıbı `stream:\s*"([^"]+)"` ve tırnaksız tamsayı `spT` için güncellendi; FNV-1a algoritmasıyla `X-Sp` doğrulama başlığı üretilip M3U8 ExtractorLink'e bağlandı. Canlı probe ile HTTP 200 manifest ve segment erişimi doğrulandı.
   - **Dosyalar:** `SetFilmIzle/src/main/kotlin/com/cloudstream/tr/setfilmizle/SetFilmIzle.kt`, `SetFilmIzle/build.gradle.kts`.

5. **Animeler (v20 -> v21):**
   - **Sorun:** Animeler embed sayfalarında video doğrudan iframe olarak değil, JavaScript içindeki `var u = "https:\/\/odnoklassniki.ru\/videoembed\/..."` yönlendirmesiyle yükleniyordu.
   - **Çözüm:** Regex ile `var u` yönlendirme adresi yakalanıp CloudStream yerleşik Odnoklassniki çıkarıcısına aktarıldı. Canlı probe ile HTTP 200 HLS doğrulandı.
   - **Dosyalar:** `Animeler/src/main/kotlin/com/cloudstream/tr/animeler/Animeler.kt`, `Animeler/build.gradle.kts`.

6. **YTS (v59 -> v60) & YtsResolver:**
   - **Sorun:** `firstNonEmptyMirror` fonksiyonunda, çöken bir ayna (`yts.mx` DNS çözümlenemediğinde) `launch` içinde istisna fırlatarak parent coroutine'i iptal ediyor ve tüm aramayı çökertiyordu. Ayrıca filmin `load()` aşamasında elde edilen torrent listesi atılıyor ve `loadLinks` aşamasında aynalara gereksiz yere baştan sorgu atılıyordu.
   - **Çözüm:** `firstNonEmptyMirror` istisna yönetimi düzeltildi, `yts.mx` listeden çıkarılıp sağlıklı aynalar (`yts.lt`, `yts.bz`, `yts.gg`) önceliklendirildi. `YTS.kt` `load()` aşamasında filmin torrent listesini `loadData` içerisine JSON olarak saklayıp `loadLinks` aşamasında sıfır gecikmeyle magnet linklerini üretir hale getirildi.
   - **Dosyalar:** `core/src/main/kotlin/com/cloudstream/tr/core/resolvers/YtsResolver.kt`, `core/src/test/kotlin/com/cloudstream/tr/core/resolvers/TorrentResolversTest.kt`, `YTS/src/main/kotlin/com/cloudstream/tr/yts/YTS.kt`, `YTS/build.gradle.kts`.

7. **FourKHDHub (v9 -> v10) & FourKhubResolver:**
   - **Sorun:** Sitenin HTML yapısındaki `article` etiketleri kaldırılmış, arama kartları `a.movie-card` ve `h3.movie-card-title` yapısına geçmiştir.
   - **Çözüm:** Kart seçicileri güncellenerek başlık ve URL çıkarımı sağlandı. Downstream indirme bağlantıları AdMaven yönlendirici sayfalarına (`greenmotors.club`) gittiği için bağlantı aşaması `BLOCKED_WITH_EVIDENCE` olarak sınıflandırıldı.
   - **Dosyalar:** `FourKHDHub/src/main/kotlin/com/cloudstream/tr/fourkhdhub/FourKHDHub.kt`, `core/src/main/kotlin/com/cloudstream/tr/core/resolvers/FourKhubResolver.kt`, `FourKHDHub/build.gradle.kts`.

8. **FullHDFilmizlesene (v19 -> v20):**
   - **Sorun:** RapidVid oynatıcısındaki TS segmentleri `.jpg` kamuflajlı döndüğünde hata oluşabiliyordu.
   - **Çözüm:** RapidVid extractor'ın disguising desteği güncellendi, HTTP 200 TS segmentleri doğrulandı.
   - **Dosyalar:** `FullHDFilmizlesene/build.gradle.kts`.

9. **SezonlukDizi (v20 -> v21):**
   - **Durum:** Site AJAX `/ajax/dataEmbed22.asp` çağrılarında Cloudflare Turnstile reCAPTCHA challenge (`reCAPTCHADATA.asp`, sitekey `0x4AAAAAACOPjx9JKl-Ox-zR`) döndürmektedir. Proje etiği ve repository kuralları gereği bot korumalarını aşmaya yönelik yetkisiz kod eklenmemiş, durum `BLOCKED_WITH_EVIDENCE` olarak kayıt altına alınmıştır.
   - **Dosyalar:** `SezonlukDizi/build.gradle.kts`.

---

### P1 — Ek İyileştirmeler

1. **Diziİzle (v6 -> v7):**
   - `div.resim a[href]` seçici desteği eklendi.
   - `parseHls` fonksiyonundaki aşırı kısıtlayıcı `video.twimg.com` domain zorunluluğu kaldırılarak tüm HTTPS tabanlı HLS CDN'leri desteklendi.
   - YouTube URL'lerindeki takip/oynatıcı parametreleri temizlenerek yerleşik YoutubeExtractor uyumluluğu sağlandı.
   - **Dosyalar:** `DiziIzle/src/main/kotlin/com/cloudstream/tr/diziizle/DiziIzle.kt`, `DiziIzle/build.gradle.kts`.

2. **AnimeciX (v109 -> v110):**
   - Sezonu bulunmayan tekil anime filmleri veya OVA'larda `anime.seasons` boş olduğu için bölümlerin boş kalması sorunu `else if (anime.videos.isNotEmpty())` kontrolüyle düzeltildi.
   - `TauVideoUrls` veri modelinde `tracks` ve `urls` alanları null-safe hale getirilerek NPE riski önlendi.
   - **Dosyalar:** `AnimeciX/src/main/kotlin/com/cloudstream/tr/animecix/AnimeciX.kt`, `AnimeciX/src/main/kotlin/com/cloudstream/tr/animecix/TauVideoExtractor.kt`, `AnimeciX/build.gradle.kts`.

3. **Yeşilçam TV (v30 -> v31):**
   - İframe listelemesinde YouTube bağlantılarını filtreleyen kural kaldırılarak sitenin resmi YouTube kanalındaki klasik Türk filmlerinin doğrudan açılması sağlandı.
   - **Dosyalar:** `YesilCamTv/src/main/kotlin/com/cloudstream/tr/yesilcamtv/YesilCamTv.kt`, `YesilCamTv/build.gradle.kts`.

---

## 3. Dağıtım Dosyaları ve Hash Özeti

- `cloudstream-trplus-v41-main.zip`: Kaynak kod arşivi (SHA256: `EF7DB448B97A9AD62416E9A0FBE271B7E063357382F9CC910602E4A12E02140D`)
- `cloudstream-trplus-v41-builds.zip`: 26 adet `.cs3` eklenti ve metadata arşivi (SHA256: `77FE838DDF167D9D14DA35CE4A29E41B8504A2782D8E3D643AE9741D86102681`)
