# CloudStream TR+ v41 — Kullanım ve Kurulum Kılavuzu (README-v41)

CloudStream TR+ v41, Türkçe içerik öncelikli, Android TV ve mobil cihazlar için optimize edilmiş CloudStream 3/4 eklenti deposudur.

---

## 1. Kurulum Adımları

### Yöntem A: CloudStream Uygulamasından Repository Ekleme (Önerilen)
1. Android cihazınızda **CloudStream** (sürüm 4.8.0 veya üzeri) uygulamasını açın.
2. **Ayarlar (Settings) > Eklentiler (Extensions)** bölümüne gidin.
3. **Depo Ekle (Add Repository)** butonuna tıklayın.
4. Aşağıdaki bilgileri girin:
   - **Depo Adı:** `CloudStream TR+`
   - **Depo URL:** `https://raw.githubusercontent.com/Metehanncakr-00/cloudstream-trplus/builds/repo.json`
5. **İndir / Ekle** butonuna basarak depoyu kaydedin ve listelenen eklentileri (başta `CloudStreamHub`, `HDFilmCehennemi`, `FilmMakinesi`, `DiziYou`, `SetFilmIzle`, `HDFilmDelisi`, `DiziIzle`) yükleyin.

### Yöntem B: Çevrimdışı / Yerel `.cs3` Dosyaları ile Kurulum
1. `cloudstream-trplus-v41-builds.zip` arşivini açın.
2. Cihazınıza yüklemek istediğiniz sağlayıcıların `.cs3` dosyalarını aktarın (örn. ADB, USB bellek veya dosya yöneticisi ile).
3. CloudStream uygulamasında **Eklentiler > Dosyadan Yükle (.cs3)** seçeneğini kullanarak eklentileri yükleyin.

---

## 2. Sağlayıcı Manifest Sürümleri ve Envanter (26 Eklenti)

| Eklenti (Modül) | v40 Sürümü | v41 Sürümü | Türler | Durum |
| :--- | :---: | :---: | :--- | :--- |
| **CloudStreamHub** | 40 | **41** | Movie, TvSeries, Anime, Documentary | Korunan Süper Eklenti / TMDB Aggregator |
| **HDFilmCehennemi** | 110 | **111** | Movie, TvSeries | Korunan Çalışan Sağlayıcı |
| **FilmMakinesi** | 109 | **110** | Movie | Korunan Çalışan Sağlayıcı |
| **DiziYou** | 20 | **21** | TvSeries | Korunan Çalışan Sağlayıcı (*The Office* TR Dublaj) |
| **HDFilmDelisi** | 19 | **20** | Movie | P0 Onarıldı (Vixolity RSC Deobfuscation) |
| **SetFilmIzle** | 20 | **21** | Movie, TvSeries | P0 Onarıldı (FastPlay unquoted token & FNV-1a header) |
| **Dizigecesi** | 20 | **21** | TvSeries | P0 Onarıldı (VidmolyNet/VidmolyMe extractor bridge) |
| **Dizilla** | 20 | **21** | TvSeries | P0 Onarıldı (AES cipher search API & Pichive header) |
| **Animeler** | 20 | **21** | Anime | P0 Onarıldı (Odnoklassniki dynamic redirect extractor) |
| **YTS** | 59 | **60** | Torrent, Movie | P0 Onarıldı (Mirror coroutine resilience & direct torrent payload) |
| **FullHDFilmizlesene** | 19 | **20** | Movie | P0 Onarıldı (RapidVid HTTP 200 .jpg TS segments) |
| **FourKHDHub** | 9 | **10** | Movie, TvSeries | P0 Ayrıştırıcı Onarıldı / Downstream AdMaven Mediator |
| **SezonlukDizi** | 20 | **21** | TvSeries | P0 Turnstile reCAPTCHA challenge (`BLOCKED_WITH_EVIDENCE`) |
| **DiziIzle** | 6 | **7** | TvSeries | P1 Onarıldı (div.resim catalog, non-twimg HLS, clean YT) |
| **AnimeciX** | 109 | **110** | Anime | P1 Onarıldı (Anime movie season fallback & TauVideo null-safety) |
| **YesilCamTv** | 30 | **31** | Movie | P1 Onarıldı (YouTube classical archives enabled) |
| **BelgeselX** | 110 | **111** | Documentary | Güncellendi / Core bytecode uyumlu |
| **BlenderOpenMovies** | 4 | **5** | Movie | Güncellendi / Core bytecode uyumlu |
| **DiziKorea** | 20 | **21** | AsianDrama | Güncellendi / Core bytecode uyumlu |
| **DramaDizilerim** | 19 | **20** | TvSeries, AsianDrama | Korunan Çalışan Sağlayıcı |
| **JetFilmIzle** | 20 | **21** | Movie | Güncellendi / PlayerX Referer & HLS |
| **KultFilmler** | 52 | **53** | Movie | Korunan Çalışan Sağlayıcı |
| **RareFilmm** | 20 | **21** | Movie | Güncellendi / OkRu data-options MP4 & HLS |
| **SinemaCX** | 19 | **20** | Movie | Korunan Çalışan Sağlayıcı |
| **StarTV** | 5 | **6** | Live, TvSeries | Korunan Çalışan Sağlayıcı |
| **WebDramaTurkey** | 20 | **21** | AsianDrama | Güncellendi / Dtpasn M3U8 |

---

## 3. Kod Seviyesinde Uygulanan v41 Düzeltmeleri

> **Doğrulama ve Test Beyanı:** Aşağıdaki hata düzeltmeleri kaynak kodda ve Gradle birim testlerinde (AnimeciX/Core 61 test %100 başarı) ve canlı HTTP/HLS problarında doğrulanmıştır. Fiziksel Android TV/mobil cihaz oynatımı bu oturumda **TEST EDİLMEDİ** olarak sınıflandırılmıştır; kullanıcı tarafından gerçek cihazda test edilmelidir.

1. **HDFilmDelisi (Vixolity RSC Deobfuscation):**
   - Eski sürüm `hdfilmdelisi.one/embed/` altındaki tuzak (honeypot) sahte oynatıcıyı çekiyor ve kullanıcıya video içi "web sitemizden izleyin" uyarısı gösteriyordu.
   - v41'de Next.js RSC payload'ından `https://pl.vixolity.com/<slug>` oynatıcı çıkarımı ve Base64/Hex ters yer değiştirme algoritmasıyla (`decryptVixolity()`) gerçek `https://ha.vixolity.com/vs/<imdb>` adresi çözülmüştür. Canlı testte Türkçe ses parçalı master HLS akışına (HTTP 200) ulaşılmıştır.

2. **DiziGecesi (VidmolyNet/VidmolyMe Extractor Köprüsü):**
   - Sitenin iframe köprüsü `vidmoly.net/embed-...html#...` döndürdüğünde, eski `VidmolyTrExtractor` yalnızca `vidmoly.to` alan adını tanıdığı için bağlantı bulunamıyordu.
   - `core/VidmolyTrExtractor.kt` dosyasına `VidmolyNetExtractor` ve `VidmolyMeExtractor` sınıfları eklenmiş, URL fragmanları temizlenmiştir.

3. **Dizilla (JSON Cipher Arama & Pichive Referer):**
   - Arama API'si `{"success":true,"response":"<AES_CIPHER>"}` formatında döndüğünde eski kod çözemiyordu. JSON yanıtı içindeki `response` anahtarı otomatik olarak deşifre edilmektedir.
   - Pichive embedlerinde `four.pichive.online` CDN ve zorunlu Referer başlıkları eklenerek HTTP 2004 (`ERROR_CODE_IO_BAD_HTTP_STATUS`) hataları giderilmiştir.

4. **SetFilmİzle (FastPlay unquoted spT & FNV-1a X-Sp Header):**
   - FastPlay oynatıcısının güncellenen JSON/JS formatında `stream: "/manifests/.../master.txt?verify=..."` ve tırnaksız tamsayı `spT: 1792194637` yapısı regex ile yakalanmakta, FNV-1a algoritmasıyla `X-Sp` doğrulama başlığı üretilmektedir. Canlı probe ile HTTP 200 HLS master manifest ve segment erişimi doğrulanmıştır.

5. **Animeler (Odnoklassniki Yönlendirme Yakalama):**
   - `animeler.pw/embed/...` sayfalarında yer alan dinamik `var u = "https:\/\/odnoklassniki.ru\/videoembed\/..."` yönlendirmesi regex ile çıkarılarak CloudStream yerleşik Odnoklassniki çıkarıcısına yönlendirilmiştir. HTTP 200 HLS akış doğrulanmıştır.

6. **YTS & YtsResolver (Eşzamanlılık & Coroutine Hata Dayanıklılığı):**
   - `firstNonEmptyMirror` fonksiyonunda, ölü bir ayna (`yts.mx` DNS çökmesi) `launch` içinde istisna fırlattığında tüm ebeveyn coroutine iptal ediliyordu. İstisnalar güvenli şekilde yakalanarak diğer sağlıklı aynaların (`yts.lt`, `yts.bz`, `yts.gg`) çalışması sağlanmıştır.
   - `YTS.kt` `load()` aşamasında filmin torrent listesini `loadData` içine JSON olarak paketleyerek `loadLinks()` çağrısında ek ağ gecikmesi olmadan anında magnet bağlantılarını oluşturmaktadır.

7. **FourKHDHub (Modern Kart Seçicileri):**
   - Sitenin değişen temasındaki `a.movie-card, .movie-card` ve `h3.movie-card-title` seçicileri entegre edilmiştir. Downstream indirme bağlantıları AdMaven yönlendirici (`greenmotors.club`) sayfalarına gittiği için `BLOCKED_WITH_EVIDENCE` olarak raporlanmıştır.

8. **Diziİzle (div.resim, Genel HLS CDN ve YouTube Temizleme):**
   - Katalogda değişen `div.resim a[href]` seçicisi eklenmiş; `parseHls` fonksiyonundaki aşırı kısıtlayıcı `video.twimg.com` domain filtresi kaldırılarak tüm HTTPS HLS CDN'leri desteklenmiştir. YouTube parametreleri temizlenmiştir.

9. **AnimeciX (Anime Film Desteği & TauVideo Null-Safety):**
   - Sezonu olmayan tekil anime filmlerinde boş sezon kontrolü yüzünden bölümlerin boş kalması sorunu `anime.videos` fallback ile düzeltilmiştir. TauVideo API'sindeki `tracks` alanı null-safe yapılmıştır.

10. **Yeşilçam TV (YouTube Klasik Arşivi):**
    - Sitedeki resmi YouTube klasik Türk filmi iframe'lerini engelleyen filtre kaldırılmış, CloudStream yerleşik YoutubeExtractor ile doğrudan oynatılabilir hale getirilmiştir.

---

## 4. Paket Bütünlüğü ve SHA-256 Değerleri

Tüm `.cs3` dosyaları `build/plugins.json` içindeki `fileHash` ve `fileSize` değerleriyle %100 uyumludur:
- **`cloudstream-trplus-v41-main.zip`**: Kaynak kod arşivi (SHA256: `EF7DB448B97A9AD62416E9A0FBE271B7E063357382F9CC910602E4A12E02140D`)
- **`cloudstream-trplus-v41-builds.zip`**: 26 adet `.cs3` eklentisi, `plugins.json` ve `repo.json` içeren dağıtım arşivi (SHA256: `77FE838DDF167D9D14DA35CE4A29E41B8504A2782D8E3D643AE9741D86102681`)
