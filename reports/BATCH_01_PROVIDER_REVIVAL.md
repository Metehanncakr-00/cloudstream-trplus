# CloudStreamHub — Batch 01 Provider Revival Raporu

**Tarih:** 13 Eylül 2026  
**Branch:** `revival/batch-01-core-providers`  
**Kapsam:** Batch 01 (6 Çekirdek Türkçe Provider)  
**Hedef:** Tam çalışan, test edilmiş, modern CloudStream 3 (`BasePlugin`, `@CloudstreamPlugin`) mimarisine uygun Kotlin eklenti modüllerinin repository'ye kazandırılması.

---

## 1. Yönetici Özeti (Executive Summary)

Mevcut CloudStreamHub repository'sinde bulunan 2 aktif provider'a (`KultFilmler`, `YesilCamTv`) ek olarak, topluluk tarafından en çok talep edilen ve en yüksek trafik alan 6 Türkçe kaynak analiz edilmiş, canlı API ve player mekanizmaları çözülmüş ve modern CloudStream eklenti standartlarında sıfırdan geliştirilmiştir.

Tüm 8 eklenti modülü başarıyla derlenmiş, birim testleri (unit tests) geçmiş, `.cs3` paketleri üretilmiş, Gradle `makePluginsJson` task'ı ile güncel `plugins.json` manifesti oluşturulmuş ve `tools/validate_repo.py --verify-artifacts` doğrulaması 0 hata ile tamamlanmıştır.

---

## 2. Canlandırılan Provider'lar ve Teknik Detaylar

### 1. DiziPal (`com.cloudstream.tr.dizipal`)
- **Modül Adı:** `DiziPal`
- **Sürüm:** `90` | **Status:** `1` (Aktif) | **Türler:** `Movie`, `TvSeries`
- **Canlı Canonical Domain:** `https://dizipal1430.com`
- **Arama Motoru:** `GET /api/search.php?q={query}` JSON REST API üzerinden doğrudan hızlı ve hafif arama.
- **Link & Player Çözümleme:** Sayfa içi Base64 string decode edilerek `videoplay.vip` iframe'i elde edilir. İframe içindeki `const src = '/play.m3u8?id=...&token=...'` dinamik manifest yolu okunarak doğrudan yetkili HLS stream ve VTT altyazıları çekilir.
- **Birim Testleri:** `DiziPalParserTest` (DOM ayrıştırma, arama kartı ve detay yükleme testi).
- **Üretilen Artifact:** `build/DiziPal.cs3` (24.3 KB).

---

### 2. Türk Anime TV (`com.cloudstream.tr.turkanime`)
- **Modül Adı:** `TurkAnime`
- **Sürüm:** `90` | **Status:** `1` (Aktif) | **Türler:** `Anime`
- **Canlı Canonical Domain:** `https://www.turkanime.tv`
- **Arama Motoru:** `POST https://www.turkanime.tv/arama` endpoint'i üzerinden form-encoded arama.
- **Şifreleme ve Bölüm Çözümleme:** `ajax/bolumler` Ajax isteği ve `meta[name='_token']` CSRF koruması. CloudStream 3 çalışma ortamında crash veren legacy CryptoAES yerine saf Java/Kotlin tabanlı OpenSSL uyumlu EVP KDF (MD5 key-derivation) ve AES-128-CBC şifre çözücü yazılarak video oynatıcı iframe'leri (Fembed, Sibnet, Mail.ru, Doodstream vb.) güvenli şekilde extract edilir.
- **Birim Testleri:** `TurkAnimeParserTest` (Native AES/CBC şifre çözme doğrulaması, anime detay ve bölüm ayrıştırma testi).
- **Üretilen Artifact:** `build/TurkAnime.cs3` (21.3 KB).

---

### 3. AnimeciX (`com.cloudstream.tr.animecix`)
- **Modül Adı:** `AnimeciX`
- **Sürüm:** `90` | **Status:** `1` (Aktif) | **Türler:** `Anime`
- **Canlı Canonical Domain:** `https://animecix.tv`
- **Arama & API Mimarisi:** Doğrudan mobil ve web JSON API'si: `https://animecix.tv/secure/search/{query}?limit=20`. Gerekli `x-e-h` güvenlik belirteci başlığı entegre edilmiştir.
- **Link & Player Çözümleme:** Bölüm video kaynakları `TauVideoExtractor` (`https://tau-video.xyz/api/video/...`) API'si üzerinden ayrıştırılarak MP4 ve adaptive HLS akışları alınır.
- **Birim Testleri:** `AnimeciXParserTest` (JSON DTO modelleri, arama yanıtı ve bölüm ayrıştırma testi).
- **Üretilen Artifact:** `build/AnimeciX.cs3` (24.2 KB).

---

### 4. BelgeselX (`com.cloudstream.tr.belgeselx`)
- **Modül Adı:** `BelgeselX`
- **Sürüm:** `90` | **Status:** `1` (Aktif) | **Türler:** `Documentary`
- **Canlı Canonical Domain:** `https://belgeselx.com`
- **Arama Motoru:** Site içi Google Programmable Search Engine (Google CSE ID: `016376594590146270301:iwmy65ijgrm`) asenkron sonuç ayrıştırıcısı.
- **Link & Player Çözümleme:** Dizi ve belgesel sayfalarındaki `a[onclick*='diziGetir']` parametreleri ayrıştırılarak dinamik video veri URL'si (`/video/data/{file}.php?id=...&sira=1`) çağrılır ve yerleşik video extractor'larına (YouTube, Ok.ru, vb.) iletilir.
- **Birim Testleri:** `BelgeselXParserTest` (DOM ayrıştırma ve çok parçalı belgesel detay testi).
- **Üretilen Artifact:** `build/BelgeselX.cs3` (18.1 KB).

---

### 5. HDFilmCehennemi (`com.cloudstream.tr.hdfilmcehennemi`)
- **Modül Adı:** `HDFilmCehennemi`
- **Sürüm:** `90` | **Status:** `1` (Aktif) | **Türler:** `Movie`, `TvSeries`
- **Canlı Canonical Domain:** `https://www.hdfilmcehennemi.nl`
- **Arama Motoru:** `GET /search?q={query}` (`X-Requested-With: fetch`, `Content-Type: application/json`) header'ları ile çalışan canlı hızlı JSON arama motoru.
- **Dinamik Rapidrame Extractor:** `hdfilmcehennemi.mobi` embed player'ındaki değişken isimli, çok adımlı dinamik JavaScript koruması (anahtar tabanlı permütasyon, Base64 dönüşümü, dizi karıştırma ve XOR akış çözücü) saf Kotlin olarak `RapidrameExtractor.kt` içinde tam tersine mühendislik ile çözülmüştür. CDN üzerinden yetkili `.m3u8` ve WebVTT altyazı akışları başarıyla çekilmektedir.
- **Birim Testleri:** `HDFilmCehennemiParserTest` (Arama kartı ayrıştırma ve Rapidrame akış deşifre testi).
- **Üretilen Artifact:** `build/HDFilmCehennemi.cs3` (19.9 KB).

---

### 6. FilmMakinesi (`com.cloudstream.tr.filmmakinesi`)
- **Modül Adı:** `FilmMakinesi`
- **Sürüm:** `90` | **Status:** `1` (Aktif) | **Türler:** `Movie`, `TvSeries`
- **Canlı Canonical Domain:** `https://filmmakinesi.to`
- **Arama & Ana Sayfa:** `/arama/?s={query}` üzerinden HTML arama sonuçları, popüler ve son filmler/diziler kategorileri.
- **Dinamik CloseLoad Extractor:** `closeload.filmmakinesi.to` embed player'ındaki dinamik JavaScript koruması saf Kotlin olarak `CloseLoadExtractor.kt` içinde çözülmüş olup CDN üzerinden 1080p Full HD master playlist (`master.txt` / `.m3u8`) ve Türkçe altyazılar doğrudan elde edilir.
- **Birim Testleri:** `FilmMakinesiParserTest` (Search DOM ayrıştırma, IMDB skor mapping ve CloseLoad akış deşifre testi).
- **Üretilen Artifact:** `build/FilmMakinesi.cs3` (18.4 KB).

---

## 3. Doğrulama ve Test Sonuçları (Verification Matrix)

| Modül | Birim Test Durumu | `.cs3` Derleme | `plugins.json` Entegrasyonu | SHA-256 Hash Doğrulaması |
|---|---|---|---|---|
| **KultFilmler** | PASSED | PASSED (17.2 KB) | Dahil | PASSED |
| **YesilCamTv** | PASSED | PASSED (14.3 KB) | Dahil | PASSED |
| **DiziPal** | PASSED | PASSED (24.3 KB) | Dahil | PASSED |
| **TurkAnime** | PASSED | PASSED (21.3 KB) | Dahil | PASSED |
| **AnimeciX** | PASSED | PASSED (24.2 KB) | Dahil | PASSED |
| **BelgeselX** | PASSED | PASSED (18.1 KB) | Dahil | PASSED |
| **HDFilmCehennemi** | PASSED | PASSED (19.9 KB) | Dahil | PASSED |
| **FilmMakinesi** | PASSED | PASSED (18.4 KB) | Dahil | PASSED |

### Doğrulama Komut Çıktıları:
1. `.\gradlew.bat testDebugUnitTest` -> **BUILD SUCCESSFUL (120 actionable tasks: 120 up-to-date / passed)**
2. `.\gradlew.bat makePluginsJson` -> **BUILD SUCCESSFUL (`build/plugins.json` 8 eklenti ile oluşturuldu)**
3. `python tools/validate_repo.py --verify-artifacts` ->
   ```text
   [Validator] Discovered 8 provider modules: ['AnimeciX', 'BelgeselX', 'DiziPal', 'FilmMakinesi', 'HDFilmCehennemi', 'KultFilmler', 'TurkAnime', 'YesilCamTv']
   [Validator] Validating 8 entries in build/plugins.json
   [SUCCESS] Repository validation passed with 0 errors!
   ```

---

## 4. Güncellenen Dokümantasyon ve Yapılandırma Dosyaları

1. `config/domains.json`: Canlı canonical domainler ve içerik işaretçileri (fingerprints) eklendi (`dizipal1430.com`, `turkanime.tv`, `animecix.tv`, `belgeselx.com`, `hdfilmcehennemi.nl`, `filmmakinesi.to`).
2. `legacy/migration-matrix.json`: 6 provider için `implementationStatus: "implemented"`, `sourceCodeFound: true`, `healthStatus: "healthy"` olarak güncellendi.
3. `docs/LEGACY_INVENTORY.md`: Envanter tablosunda 6 eklenti `implemented` durumuna ve canlı linklerine çekildi.
4. `README.md`: Aktif eklentiler tablosu 8 sağlayıcıyı kapsayacak şekilde genişletildi.
