# CloudStream TR+ v41 — Test ve Doğrulama Raporu (TEST-REPORT-v41)

**Rapor Tarihi:** 17 Ekim 2026  
**Hedef Ortam:** CloudStream 3 / 4 (v4.8.0 ve üzeri)  
**Doğrulama Araçları:** Gradle 8.12, JDK 17, `tools/validate_repo.py`, `tools/verify_release_safety.py`, JUnit 4 birim testleri, Python live probe araçları.

---

## 1. Yönetici Özeti

- **Toplam Eklenti Sayısı:** 26 adet aktif eklenti.
- **Gradle Derleme ve Paketleme:** 26 adet `.cs3` eklentisi sıfır hata ile üretildi (`BUILD SUCCESSFUL`).
- **Repository Doğrulaması:** `tools/validate_repo.py --verify-artifacts` -> **0 Hata** (26 eklenti, SHA-256 ve dosya boyutları tam uyumlu).
- **Yayın Güvenliği:** `tools/verify_release_safety.py` (v40 baseline'ına karşı) -> **BAŞARILI** (`"passed": true, "errors": []`).
- **Birim Testleri:** `core/src/test/` ve `AnimeciX` paketlerinde 61 birim testinin tamamı başarıyla geçti (%100).
- **Android Oynatma Beyanı:** Bu oturumda fiziksel Android cihaz veya emülatör üzerinde ADB canlı oynatma testi yapılmamıştır; bu nedenle cihaz seviyesindeki oynatma durumları dürüst ve şeffaf biçimde **TEST EDİLMEDİ** olarak etiketlenmiştir.

---

## 2. 26 Sağlayıcı İçin Kapsamlı Doğrulama Matrisi

| # | Sağlayıcı (Modül) | v40 -> v41 | Derleme | Çıkarıcı / Ağ Doğrulaması | Fiziksel Android Oynatma | Katman ve Kanıt Detayı |
| :-: | :--- | :---: | :---: | :---: | :---: | :--- |
| 1 | **CloudStreamHub** | 40 -> **41** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | TMDB Keşif & Aggregator, 4 katmanlı Türkçe dil öncelik sıralaması aktif. |
| 2 | **HDFilmCehennemi** | 110 -> **111** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | Korunan çalışan sağlayıcı. Bytecode v41 senkronizasyonu sağlandı. |
| 3 | **FilmMakinesi** | 109 -> **110** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | Korunan çalışan sağlayıcı. Turbo/Vidmoly HLS akışları aktif. |
| 4 | **DiziYou** | 20 -> **21** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | Korunan çalışan sağlayıcı. *The Office* Türkçe dublaj önceliği korundu. |
| 5 | **HDFilmDelisi** | 19 -> **20** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | P0 Onarıldı. Next.js RSC `decryptVixolity()` ile gerçek HLS (HTTP 200, Türkçe ses) doğrulandı. Sahte honeypot elendi. |
| 6 | **SetFilmIzle** | 20 -> **21** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | P0 Onarıldı. FastPlay unquoted `spT` ve `X-Sp` FNV-1a doğrulama başlığı eklendi. HTTP 200 M3U8 ve TS segmentleri doğrulandı. |
| 7 | **Dizigecesi** | 20 -> **21** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | P0 Onarıldı. VidmolyNet ve VidmolyMe çıkarıcı köprüsü eklendi, URL fragmanları temizlendi. |
| 8 | **Dizilla** | 20 -> **21** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | P0 Onarıldı. JSON AES arama API yanıtı deşifre edildi; Pichive oynatıcısına Referer başlığı bağlanarak HTTP 2004 önlendi. |
| 9 | **Animeler** | 20 -> **21** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | P0 Onarıldı. Embed JS içindeki `var u` Odnoklassniki yönlendirmesi çıkarıldı; HTTP 200 HLS doğrulandı. |
| 10 | **YTS** | 59 -> **60** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | P0 Onarıldı. Coroutine ayna istisna iptali düzeltildi; `load()` aşamasında torrent verisi paketlendi; `yts.lt`/`yts.bz` aktif. |
| 11 | **FullHDFilmizlesene** | 19 -> **20** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | P0 Onarıldı. RapidVid disguising TS segmentleri (HTTP 200) doğrulandı. |
| 12 | **FourKHDHub** | 9 -> **10** | DERLENDİ | BLOCKED_WITH_EVIDENCE | TEST EDİLMEDİ | P0 Ayrıştırıcı Onarıldı. Modern `a.movie-card` seçicileri entegre edildi. Downstream AdMaven yönlendirici (`greenmotors.club`) bot engeli nedeniyle oynatılamaz. |
| 13 | **SezonlukDizi** | 20 -> **21** | DERLENDİ | BLOCKED_WITH_EVIDENCE | TEST EDİLMEDİ | P0 İncelendi. AJAX endpoint'i Cloudflare Turnstile reCAPTCHA challenge (`reCAPTCHADATA.asp`) ile korunmaktadır. Bot delme kodundan kaçınılmıştır. |
| 14 | **DiziIzle** | 6 -> **7** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | P1 Onarıldı. `div.resim a[href]` katalog seçicisi, genel HLS CDN desteği ve YouTube URL temizliği uygulandı. |
| 15 | **AnimeciX** | 109 -> **110** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | P1 Onarıldı. Sezonsuz anime filmlerinde `anime.videos` fallback eklendi. TauVideo `tracks` null-safety sağlandı. |
| 16 | **YesilCamTv** | 30 -> **31** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | P1 Onarıldı. YouTube klasik film filtre kısıtlaması kaldırılarak resmi arşiv içeriklerinin açılması sağlandı. |
| 17 | **BelgeselX** | 110 -> **111** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | Çoklu data-iframe ve regex video çıkarıcıları aktif. Bytecode sürüm artışı yapıldı. |
| 18 | **BlenderOpenMovies** | 4 -> **5** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | Resmi video.blender.org PeerTube MP4 akışları aktif. Bytecode sürüm artışı yapıldı. |
| 19 | **DiziKorea** | 20 -> **21** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | Player API ve sayfa içi Türkçe altyazı desteği. Bytecode sürüm artışı yapıldı. |
| 20 | **DramaDizilerim** | 19 -> **20** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | Korunan çalışan sağlayıcı. Bytecode sürüm artışı yapıldı. |
| 21 | **JetFilmIzle** | 20 -> **21** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | PlayerX Referer başlığı entegrasyonu ve HLS akışları aktif. Bytecode sürüm artışı yapıldı. |
| 22 | **KultFilmler** | 52 -> **53** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | Korunan çalışan sağlayıcı. Bytecode sürüm artışı yapıldı. |
| 23 | **RareFilmm** | 20 -> **21** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | OkRu data-options MP4 ve HLS çıkarımı aktif. Bytecode sürüm artışı yapıldı. |
| 24 | **SinemaCX** | 19 -> **20** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | Korunan çalışan sağlayıcı. Bytecode sürüm artışı yapıldı. |
| 25 | **StarTV** | 5 -> **6** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | Korunan çalışan canlı yayın ve dizi sağlayıcısı. Bytecode sürüm artışı yapıldı. |
| 26 | **WebDramaTurkey** | 20 -> **21** | DERLENDİ | BAĞLANTI DOĞRULANDI | TEST EDİLMEDİ | Dtpasn M3U8 çıkarımı ve [data-embed] seçicisi aktif. Bytecode sürüm artışı yapıldı. |

---

## 3. Doğrulama Kapıları ve Kanıt Detayları

### A. Repository & Paket Bütünlüğü
```
[Validator] Discovered 35 physical provider modules
[Validator] Validating 26 entries in build/plugins.json
[SUCCESS] Repository validation passed with 0 errors! (26 active providers verified, 35 physical modules discovered)
```

### B. Yayın Güvenliği & Sürüm Artışı (Release Safety)
```
{
  "passed": true,
  "mode": "test",
  "localCount": 26,
  "publishedCount": 26,
  "releaseInventoryComplete": true,
  "errors": []
}
```

### C. Dağıtım Arşivleri ve Testzip
```
Root SHA256SUMS: 505 files verified successfully.
cloudstream-trplus-main.zip: valid ZIP, 446 entries
cloudstream-trplus-builds.zip: valid ZIP, 29 entries (26 .cs3 + plugins.json + repo.json + SHA256SUMS)
CS3 count in builds/: 26
plugins.json entries: 26
ALL PACKAGING CHECKS PASSED PERFECTLY!
```

---

## 4. Dağıtım Hash Özeti

| Dosya Adı | Boyut (Bayt) | SHA-256 Özeti |
| :--- | :---: | :--- |
| `cloudstream-trplus-v41-main.zip` | 1.823.003 | `EF7DB448B97A9AD62416E9A0FBE271B7E063357382F9CC910602E4A12E02140D` |
| `cloudstream-trplus-v41-builds.zip` | 4.429.176 | `77FE838DDF167D9D14DA35CE4A29E41B8504A2782D8E3D643AE9741D86102681` |
