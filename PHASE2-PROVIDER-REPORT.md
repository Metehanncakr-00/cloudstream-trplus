# CLOUDSTREAM TR+ v42 — AŞAMA 2 SAĞLAYICI ONARIM RAPORU
**Tarih:** 18 Ekim 2026  
**Durum:** Tamamlandı (Kanıt Temelli Onarımlar ve Kategori Genişletmeleri)  
**Mevcut Test Durumu:** 
- `tools/validate_repo.py`: **0 Hata** (26 Aktif Sağlayıcı, 35 Fiziksel Modül)
- Hedefli Gradle Testleri: `:CloudStreamHub`, `:SetFilmIzle`, `:Dizilla`, `:FullHDFilmizlesene`, `:Dizigecesi`, `:FilmMakinesi`, `:HDFilmCehennemi` **BAŞARILI** (BUILD SUCCESSFUL)

---

## 1. YÖNETİCİ ÖZETİ

v41 sürümünün gerçek Android cihaz testlerinde başarısızlık yaşayan veya "Bağlantı bulunamadı" hatası veren öncelikli sağlayıcılar katman bazında (SEARCH -> LOAD -> EPISODES -> LINKS -> MEDIA -> LANGUAGE) incelenmiş ve somut kod onarımları uygulanmıştır.

Uygulanan temel düzeltmeler:
1. **SetFilmIzle:** `wp-admin/admin-ajax.php` üzerinden dönen JSON yanıtında `subtitles` alanı eklendiği için Jackson'ın `UnrecognizedPropertyException` fırlatıp sessizce yutulması giderildi. `@JsonIgnoreProperties(ignoreUnknown = true)` ve regex fallback eklendi. FastPlay `X-Sp` HLS akışı (HTTP 200, segment 200) aktif çalışır duruma getirildi.
2. **Dizigecesi:** Vidmoly iframe köprüsü (`https://vidmoly.net/embed-...`) CloudStream'in varsayılan extractor motorunda kayıtlı olmadığı için link üretemiyordu. `VidmolyTrExtractor`, `VidmolyNetExtractor` ve `VidmolyMeExtractor` hem `DizigecesiPlugin` hem de `CloudStreamHubPlugin` içine tescil edildi. `Dizigecesi.kt` doğrudan `VidmolyTrExtractor` çağrısıyla güçlendirildi.
3. **Dizilla:** Pichive HLS ana oynatma listesi (`https://four.pichive.online/m.php?v=...`) sunucu tarafından `Content-Type: text/html` olarak döndürüldüğünden `StreamValidator.kt` tarafından hatalı şekilde "HTML error body" (Media3 Hata 2004) sayılarak eleniyordu. `StreamValidator.kt` içine `#EXTM3U` ve `<?xml <MPD` body prefix muafiyeti getirilerek akış kurtarıldı.
4. **FullHDFilmizlesene:** RapidVid `_p8` token çözümü sonrasında JSON verisine eklenen `"q"` nesnesi nedeniyle Jackson deserialization çöküyordu. `RapidVidExtractor.kt` modeline `@JsonIgnoreProperties(ignoreUnknown = true)` eklendi, `FAIL_ON_UNKNOWN_PROPERTIES = false` yapıldı ve doğrudan regex fallback ile `cm` (altyazılı) ve `tm` (Türkçe dublaj) akışları güvenceye alındı.
5. **FilmMakinesi & HDFilmCehennemi:** Talep edilen eksik `Animasyon Filmleri` kategorileri (`/tur/animasyon-fmasd6/film/` ve `/tur/animasyon-izle/`) `mainPage` listelerine eklendi. `ProviderCapabilityMatrix` üzerinden Süper Hub'ın `ANIMATION_MOVIES` vitrinine bağlandı.

---

## 2. SAĞLAYICI BAZINDA DETAYLI ONARIM VE TEŞHİS MATRİSİ

| Sağlayıcı | v41 Hata Belirtisi | Kök Neden (Root Cause) | Uygulanan Düzeltme | Sonuç & Oynatma Durumu |
| :--- | :--- | :--- | :--- | :--- |
| **SetFilmIzle** | Bağlantı bulunamadı | Ajax yanıtındaki `subtitles: []` alanı Jackson modelinde tanımlı olmadığı için çöküyor ve `catch` içinde yutuluyordu. | `SetFilmIzle.kt` içindeki `StreamData`, `VideoData`, `VideoResponse` modellerine `@JsonIgnoreProperties` eklendi; raw text regex fallback kondu. | **IMPLEMENTED_WORKING** (FastPlay HLS tokenlı HTTP 200 doğrulanmış) |
| **Dizigecesi** | Bağlantı bulunamadı | `DizigecesiPlugin` içinde hiçbir extractor tescil edilmemişti; `loadExtractor` `vidmoly.net` adresini tanıyamıyordu. | `DizigecesiPlugin` ve `CloudStreamHubPlugin`'e Vidmoly extractor'ları tescil edildi. `Dizigecesi.kt` doğrudan `VidmolyTrExtractor` çağıracak şekilde güncellendi. | **IMPLEMENTED_WORKING** (Vidmoly HLS HTTP 200 doğrulanmış) |
| **Dizilla** | Oynatma Hatası (2004) | Pichive CDN `Content-Type: text/html` başlığıyla `#EXTM3U` gönderiyordu. `StreamValidator.kt` bunu geçersiz sayıp akışı düşürüyordu. | `StreamValidator.isInvalidMediaBody()` metoduna `#EXTM3U` gövde kontrolü eklendi. Başlık ne olursa olsun geçerli HLS korunuyor. | **IMPLEMENTED_WORKING** (Pichive HLS Türkçe ses akışı doğrulanmış) |
| **FullHDFilmizlesene** | Bağlantı bulunamadı | RapidVid'in `_p8` JSON çıktısında `"q"` alanı Jackson deserialization hatası veriyordu. | `RapidVidExtractor.kt` mapper'ı `FAIL_ON_UNKNOWN_PROPERTIES=false` yapıldı, `@JsonIgnoreProperties` ve regex fallback eklendi. | **IMPLEMENTED_WORKING** (RapidVid CDN HLS Türkçe dublaj ve altyazı doğrulanmış) |
| **FourKHDHub** | Bağlantı bulunamadı | Arama ve kart yapısı değişmişti; ayrıca indirme butonları `greenmotors.club` bot korumalı kısa linke (`s-b0t`) yönleniyor. | Kart seçiciler güncellendi (`-movie-`, `-series-`). Ancak `greenmotors` JS bot duvarı aşılması kurallar gereği engellendi. | **BLOCKED_WITH_EVIDENCE** (Shortener Bot-Wall / greenmotors.club redirection) |
| **Animeler** | Bağlantı bulunamadı | Makisekurisu SPA oynatıcı API'si çözüldü ancak arkasındaki tüm CDN depolama alanları (`steinsga.site`, `miuraberserk.site`, `makise-kurisu.site`) 404 dönüyor. | Oynatıcı mantığı analiz edildi, CDN sunucularının kaynak dosyalarını sildiği (`404 Not Found`) kanıtlandı. | **DEGRADED / BLOCKED_WITH_EVIDENCE** (Upstream Storage 404) |

---

## 3. MEVCUT KORUNAN SAĞLAYICILAR (REGRESYONSUZ)

Aşağıdaki sağlayıcıların v41'de çalışan durumları ve yapılandırmaları korunmuştur:
- **HDFilmDelisi:** Doğrulanmış doğrudan oynatma korundu.
- **YTS:** Torrentio ve YTS resolver entegrasyonu korundu.
- **HDFilmCehennemi:** Korundu + `Animasyon Filmleri` kategorisi eklendi.
- **FilmMakinesi:** Korundu + `Animasyon Filmleri` kategorisi eklendi.
- **DiziYou:** Korundu (AES şifreli API ve iframe akışları).
- **StarTV:** Doğrulanmış DYG Digital m3u8 HLS oynatımı korundu.
- **DramaDizilerim, DiziKorea, WebDramaTurkey:** Korundu.
- **KultFilmler, SinemaCX, JetFilmIzle, YesilCamTv:** Korundu.
- **SezonlukDizi:** Cloudflare Turnstile koruması raporlandı, mevcut kod korundu.

---

## 4. KOD DEĞİŞİKLİKLERİ VE KANIT DOSYALARI

Değiştirilen kritik dosyalar:
- `core/src/main/kotlin/com/cloudstream/tr/core/network/StreamValidator.kt`: `#EXTM3U` içerik-türü bağımsız doğrulama.
- `core/src/main/kotlin/com/cloudstream/tr/core/extractors/RapidVidExtractor.kt`: Jackson hata toleransı, regex fallback.
- `SetFilmIzle/src/main/kotlin/com/cloudstream/tr/setfilmizle/SetFilmIzle.kt`: `StreamData` ve `VideoResponse` güvenliği.
- `Dizigecesi/src/main/kotlin/com/cloudstream/tr/dizigecesi/Dizigecesi.kt`: Vidmoly direkt çözücü.
- `Dizigecesi/src/main/kotlin/com/cloudstream/tr/dizigecesi/DizigecesiPlugin.kt`: Extractor kayıtları.
- `FourKHDHub/src/main/kotlin/com/cloudstream/tr/fourkhdhub/FourKHDHub.kt`: Modern kart seçicileri.
- `FilmMakinesi/src/main/kotlin/com/cloudstream/tr/filmmakinesi/FilmMakinesi.kt`: Animasyon kategorisi.
- `HDFilmCehennemi/src/main/kotlin/com/cloudstream/tr/hdfilmcehennemi/HDFilmCehennemi.kt`: Animasyon kategorisi.
- `CloudStreamHub/src/main/kotlin/com/cloudstream/tr/hub/CloudStreamHubPlugin.kt`: Vidmoly ve RapidVid extractor tescili.

Doğrulama ve test scriptleri:
- `tools/test_setplay_flow.py`: SetFilmIzle FastPlay akış doğrulaması (200 OK).
- `tools/test_dizigecesi.py`: Dizigecesi AJAX ve iframe köprü doğrulaması (200 OK).
- `tools/test_fhf_flow.py`: FullHDFilmizlesene RapidVid şifre çözümü ve HLS doğrulaması (200 OK).
- `tools/probe_v41.py`: Dizilla Pichive akış ve Türkçe ses doğrulaması (200 OK).
- `tools/test_categories.py`: FilmMakinesi ve HDFilmCehennemi animasyon kategori doğrulaması.
