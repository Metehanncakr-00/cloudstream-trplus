# TR+ ONE v2 — Birim ve Entegrasyon Test Raporu (Aşama 2)

**Tarih:** 19 Ekim 2026  
**Eklenti:** TR+ ONE (`TRPlusOne`)  
**Sürüm:** 2  
**Ortam:** Android Gradle Plugin 8.7.3, Kotlin 2.3.0, CloudStream SDK v4.8.0  

---

## 1. Test Matrisi ve Sonuçları

| Test Paketi | Test Sayısı | Başarılı | Başarısız | Kapsanan Alanlar |
| :--- | :---: | :---: | :---: | :--- |
| **`CanonicalEngineTest`** | 7 | 7 | 0 | Başlık normalizasyonu, tek kart birleştirme, yıl ayrımı, anime/film/dizi izolasyonu |
| **`OneRouteCodecTest`** | 12 | 12 | 0 | Kanonik `/media/`, TV'nin `one://` prepended adresi, eski `one://`, `/movie/`, `/episode/`, geçersiz adres reddi |
| **`TRPlusOneTest`** | 13 | 13 | 0 | Ürün kimliği, kart URL üretimi, `load()` TV prepended adres çözümü, `loadLinks()` rota yönlendirmesi, olay tanılamaları |
| **TOPLAM** | **32** | **32** | **0** | **100% PASS** |

---

## 2. Ayrıntılı Test Dökümü

### A. Rota Çözümleme (`OneRouteCodecTest`)
- [x] `testCanonicalMediaRoute`: `https://trplus.one.invalid/media/{id}` -> `OneRoute.Media(id)`
- [x] `testTvPrependedMediaRoute`: `https://trplus.one.invalid/one://media/{id}` -> `OneRoute.Media(id)`
- [x] `testLegacyMediaRoute`: `one://media/{id}` -> `OneRoute.Media(id)`
- [x] `testRelativeMediaRoute`: `/media/{id}` -> `OneRoute.Media(id)`
- [x] `testCanonicalMovieRoute`: `https://trplus.one.invalid/movie/{id}` -> `OneRoute.Movie(id)`
- [x] `testTvPrependedMovieRoute`: `https://trplus.one.invalid/one://movie/{id}` -> `OneRoute.Movie(id)`
- [x] `testLegacyMovieRoute`: `one://movie/{id}` -> `OneRoute.Movie(id)`
- [x] `testCanonicalEpisodeRoute`: `https://trplus.one.invalid/episode/{id}/2/14` -> `OneRoute.Episode(id, 2, 14)`
- [x] `testTvPrependedEpisodeRoute`: `https://trplus.one.invalid/one://episode/{id}/1/5` -> `OneRoute.Episode(id, 1, 5)`
- [x] `testLegacyEpisodeRoute`: `one://episode/{id}/0/1` -> `OneRoute.Episode(id, 0, 1)`
- [x] `testRoundTripGenerators`: URL üreticilerinin ürettiği adreslerin parse edildiğinde aynı nesneyi vermesi
- [x] `testSafeRejectionOfInvalidRoutes`: Yabancı alan adları, boş kimlikler, negatif bölüm numaraları, geçersiz karakterlerin güvenle `null` dönmesi

### B. Sağlayıcı Giriş Noktaları (`TRPlusOneTest`)
- [x] `testProductIdentity`: `name="TR+ ONE"`, `mainUrl="https://trplus.one.invalid"`, `lang="tr"`, TV türleri
- [x] `testCardGeneratesCanonicalCloudStreamUrl`: Kart URL'sinin `https://trplus.one.invalid/media/{id}` olması
- [x] `testCardTypesMatchCanonicalKind`: Movie, Series, Anime, AnimeMovie eşleşmesi
- [x] `testLoadWithTvPrependedUrlRoutesCorrectly`: TV kutusunda çöken `https://trplus.one.invalid/one://media/...` adresinin başarıyla çözülüp `OneIndex` üzerinden bulunması
- [x] `testLoadWithLegacyAndCanonicalUrls`: Hem eski hem yeni adreslerle `OneRouteCodec` doğrulaması
- [x] `testMovieDetailPlayRouteFormat`: Film detayında oynatma adresinin `/movie/{id}` olması
- [x] `testEpisodeDetailPlayRouteFormat`: Dizi detayında bölüm oynatma adresinin `/episode/{id}/{s}/{e}` olması
- [x] `testLoadLinksRejectsMalformedRoutes`: Bozuk veya yabancı oynatma verilerinin `false` dönerek reddedilmesi
- [x] `testLoadRejectsInvalidRoutesSafely`: Bozuk veya yabancı detay URL'lerinin `null` dönerek reddedilmesi
- [x] `testDiagnosticsLogsEvents`: `LOAD_START`, `ROUTE_PARSED`, `MEDIA_NOT_FOUND` olaylarının loglanması
- [x] `testLoadLinksDiagnosticsLogsEvents`: `LOAD_LINKS_START`, `LOAD_LINKS_RESULT` olaylarının loglanması
- [x] `testRedactionInDiagnostics`: Hassas `apiKey` ve `token` verilerinin `[REDACTED]` ile sansürlenmesi
- [x] `testMainPageSections`: 4 ana sayfa kategorisinin (Filmler, Diziler, Anime, Son Eklenenler) doğrulanması

---

## 3. Paket Derleme Bilgileri

* **Komut:** `.\gradlew.bat :TRPlusOne:make --console=plain`
* **Çıktı Dosyası:** `TRPlusOne/build/TRPlusOne.cs3`
* **Boyut:** 237,954 bytes
* **SHA-256:** `c2874048f3d371c3e1a5fa1b6a0c604ed0eb5015e556a8ffd45a48b04d3838d8`
* **Manifest Versiyonu:** 2

---

## 4. Donanım Test Beyanı

> **Fiziksel Android 12 TV Box Testi: `NOT_TESTED`**  
> Kodlama, birim testleri ve paket derlemesi tamamlanmıştır. Kullanıcının GitHub test yayını onayının ardından cihaz üzerinde test edilecektir.
