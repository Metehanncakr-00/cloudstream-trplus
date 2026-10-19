# TR+ ONE v3 — Birim, Entegrasyon ve Derleme Test Raporu (Aşama 3)

**Tarih:** 19 Ekim 2026  
**Eklenti:** TR+ ONE (`TRPlusOne`)  
**Sürüm:** 3  
**Ortam:** Android Gradle Plugin 8.7.3, Kotlin 2.3.0, JVM 11, CloudStream SDK v4.8.0  

---

## 1. Test Matrisi ve Sonuçları

| Test Paketi | Test Sayısı | Başarılı | Başarısız | Kapsanan Alanlar |
| :--- | :---: | :---: | :---: | :--- |
| **`CanonicalEngineTest`** | 7 | 7 | 0 | Başlık normalizasyonu, tek kart birleştirme, yıl ayrımı, anime/film/dizi izolasyonu |
| **`OneRouteCodecTest`** | 12 | 12 | 0 | Kanonik `/media/`, TV'nin `one://` prepended adresi, eski `one://`, `/movie/`, `/episode/`, geçersiz adres reddi |
| **`TRPlusOneTest`** | 13 | 13 | 0 | Ürün kimliği, kart URL üretimi, `load()` TV prepended adres çözümü, `loadLinks()` rota yönlendirmesi, olay tanılamaları |
| **`OneEpisodeRegistryTest`** | 4 | 4 | 0 | Bölüm hedef URL'si kaydı, `mediaId:s:e` çözümü, çoklu sağlayıcı bölüm haritası, kayıt temizleme |
| **`OneProvidersGroup1Test`** | 3 | 3 | 0 | 9 sağlayıcı adaptörünün kaydı, tür filtreleme (film, dizi, anime), semafor ile eşzamanlılık kısıtı |
| **`MultiSourceAndTaggingTest`** | 4 | 4 | 0 | Çoklu sağlayıcı kaynak birleştirme, `[Türkçe Dublaj]` ve `[Türkçe Altyazı]` etiketleme, sahte link izolasyonu |
| **TOPLAM** | **43** | **43** | **0** | **100% PASS** |

---

## 2. Ayrıntılı Test Dökümü

### A. Bölüm Kaydı ve Doğrudan Çözümleme (`OneEpisodeRegistryTest`)
- [x] `testRegisterAndResolveEpisodeUrl`: Bölüm hedef URL'sinin (`ep.data`) kaydedilip `(mediaId, season, episode)` ile doğrudan ve anında döndürülmesi.
- [x] `testMultiProviderEpisodeMapping`: Aynı bölüm için birden fazla sağlayıcıdan gelen hedef URL'lerin tek anahtarda haritalanması.
- [x] `testResolveMissingEpisodeReturnsEmpty`: Kaydı bulunmayan sezon/bölüm sorgularında çökme yaşanmadan güvenle boş liste dönülmesi.
- [x] `testClearAndEviction`: Önbellek temizleme çağrısında tüm kayıtların güvenle silinmesi.

### B. Grup 1 Sağlayıcıları ve Eşzamanlılık (`OneProvidersGroup1Test`)
- [x] `testGroup1ProvidersRegistered`: 9 sağlayıcının (`hdfilmcehennemi`, `sezonlukdizi`, `animecix`, `filmmakinesi`, `sinemacx`, `fullhdfilmizlesene`, `dizilla`, `diziyou`, `animeler`) eksiksiz kayıtlı olması.
- [x] `testProviderTypeFiltering`: Film, dizi ve anime türlerine göre adaptör filtrelemesinin doğrulanması.
- [x] `testCatalogConcurrencyThrottle`: `Semaphore(4)` kısıtlamasının TV Box üzerinde CPU/ağ aşırı yüklemesini engellediğinin kanıtı.

### C. Çoklu Kaynak ve Dil Etiketleme (`MultiSourceAndTaggingTest`)
- [x] `testMultiSourceDeduplication`: Farklı sağlayıcılardan gelen aynı yapımların `CanonicalEngine` ile tek kartta toplanması.
- [x] `testDublajTagDecoration`: Kaynakta Türkçe dublaj kanıtı olan linklere `[Türkçe Dublaj]` etiketi eklenmesi.
- [x] `testAltyaziTagDecoration`: Kaynakta Türkçe altyazı kanıtı olan linklere `[Türkçe Altyazı]` etiketi eklenmesi.
- [x] `testNoHallucinatedLinks`: Kaynakta olmayan sahte akışların asla üretilmediğinin ve yalnızca gerçek sağlayıcı linklerinin etiketlendiğinin doğrulanması.

### D. Rota ve Geriye Dönük Uyumluluk (`OneRouteCodecTest` & `CanonicalEngineTest`)
- [x] 12 rota testi: Kanonik URL'ler, TV Box prepended adresleri, eski URL'ler, güvenli reddetme.
- [x] 7 kanonik motor testi: Normalizasyon, yıl eşleşmesi, anime/film kategorizasyonu.
- [x] 13 TRPlusOne motor testi: Sağlayıcı kimliği, kart oluşturma, detay yükleme, tanılama logları ve hassas veri sansürü (`[REDACTED]`).

---

## 3. Dokuz Sağlayıcının Doğrulama Durumu

| Sağlayıcı | Modül Kodu Pakette? | Adaptör Kaydı? | Extractor Kayıtları | Doğrulama Seviyesi |
| :--- | :---: | :---: | :--- | :--- |
| **HDFilmCehennemi** | Evet | Evet | RapidVid, VidMoly | **ACTIVE** (TV Box Oynatma Kanıtlandı) |
| **SezonlukDizi** | Evet | Evet | Byse, Filemoon | **DEGRADED** (v3 URL Onarıldı, TV Oynatma: `NOT_TESTED`) |
| **AnimeciX** | Evet | Evet | MakiseKurisu, FirePlayer, TauVideo | **DEGRADED** (TauVideo/CF kısıtı, TV Oynatma: `NOT_TESTED`) |
| **FilmMakinesi** | Evet | Evet | CloseLoad, VidMoly | **NOT_TESTED** (Birim Testleri PASS, TV Oynatma Bekliyor) |
| **SinemaCX** | Evet | Evet | TauVideo, Filemoon, FilmizleIn | **NOT_TESTED** (Birim Testleri PASS, TV Oynatma Bekliyor) |
| **FullHDFilmizlesene** | Evet | Evet | RapidVid, VidMoly | **NOT_TESTED** (Birim Testleri PASS, TV Oynatma Bekliyor) |
| **Dizilla** | Evet | Evet | Byse, Filemoon, Pichive | **NOT_TESTED** (Birim Testleri PASS, TV Oynatma Bekliyor) |
| **DiziYou** | Evet | Evet | VidMoly, Filemoon | **NOT_TESTED** (Birim Testleri PASS, TV Oynatma Bekliyor) |
| **Animeler** | Evet | Evet | TauVideo, RapidVid | **NOT_TESTED** (Birim Testleri PASS, TV Oynatma Bekliyor) |

---

## 4. Paket Derleme ve CS3 İncelemesi

* **Derleme Komutu:** `.\gradlew.bat :TRPlusOne:make --console=plain`
* **Sonuç:** `BUILD SUCCESSFUL in 4s`
* **Çıktı Dosyası:** `TRPlusOne/build/TRPlusOne.cs3`
* **Dosya Boyutu:** 313,914 bytes (~306 KB)
* **SHA-256 Özeti:** `CCA5D471502FFB4EF3FB54CD00FA6E9FF9275507680CF2053C7778E84F377397`
* **Manifest İçeriği:**
  ```json
  {
    "requiresResources": false,
    "version": 3,
    "pluginClassName": "com.cloudstream.tr.one.TRPlusOnePlugin",
    "name": "TRPlusOne"
  }
  ```
* **DEX İçerik Doğrulaması:**
  - `HDFilmCehennemi`: **MEVCUT**
  - `SezonlukDizi`: **MEVCUT**
  - `AnimeciX`: **MEVCUT**
  - `FilmMakinesi`: **MEVCUT**
  - `SinemaCX`: **MEVCUT**
  - `FullHDFilmizlesene`: **MEVCUT**
  - `Dizilla`: **MEVCUT**
  - `DiziYou`: **MEVCUT**
  - `Animeler`: **MEVCUT**
  - `OneEpisodeRegistry`: **MEVCUT**
  - `OneProviders`: **MEVCUT**
  - `TRProviderAdapter`: **MEVCUT**

---

## 5. Donanım Test Beyanı

> **Fiziksel Android 12 TV Box Testi: `NOT_TESTED`**  
> Kodlama, adaptör entegrasyonu, birim testleri (43/43 PASS) ve CS3 paket derlemesi yerel ortamda eksiksiz tamamlanmıştır. HDFilmCehennemi dışındaki Grup 1 sağlayıcılarının TR+ ONE üzerinden canlı oynatma başarısı henüz fiziksel TV kutusu üzerinde test edilmediğinden resmi olarak `NOT_TESTED` statüsündedir.
