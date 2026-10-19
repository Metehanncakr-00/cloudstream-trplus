# TR+ ONE v3 — Çoklu Sağlayıcı Entegrasyonu ve Dizi/Anime Oynatma Onarımı (Aşama 3)

Bu sürüm, **TR+ ONE** bağımsız eklentisini 25 kaynaklık sağlayıcı envanterine hazırlayan modüler mimariye (`TRProviderAdapter` + `OneProviders`) geçiren, fiziksel **Android 12 TV Box** üzerinde tespit edilen dizi/anime bölüm oynatma hatasını (`OneEpisodeRegistry`) kökten çözen ve **Grup 1 (9 Sağlayıcı)** entegrasyonunu tamamlayan ana sürümdür.

> [!IMPORTANT]
> **Fiziksel Android 12 TV Box v3 Oynatma Testi: `NOT_TESTED`**  
> Kodlama, birim testleri (43/43 PASS) ve CS3 paket derlemesi yerel ortamda eksiksiz tamamlanmıştır. Kullanıcının GitHub test yayını onayı ile birlikte cihaz üzerinde canlı test gerçekleştirilecektir.

---

## 1. Dağıtım Envanteri ve Sağlayıcı Sınıflandırması

Canlı dağıtım (`origin/builds:plugins.json`) toplam **27 eklenti** içermektedir:
* **25 Bağımsız İçerik Sağlayıcısı**
* **1 Bağımsız Birleşik Eklenti:** `TRPlusOne` (v3)
* **1 Eski Agregator Eklentisi:** `CloudStreamHub` (v45 - TR+ ONE bundan tamamen bağımsızdır)

`config/providers.json` dosyasındaki 38 kayıttan 11 tanesi kalıcı olarak kapalı/devre dışı (`enabled: false`) olup yayındaki `builds/plugins.json` içinde yer almamaktadır.

### Sağlayıcı Doğrulama Durumları
* **`ACTIVE`**: Fiziksel TV Box üzerinde oynatması kanıtlanmış kaynaklar: `HDFilmCehennemi` (ve açık kaynak video akışı sunan `BlenderOpenMovies`).
* **`DEGRADED`**: Katalog/detay çalışan, ancak bölüm yönlendirmesi veya extractor korumaları bulunan kaynaklar: `SezonlukDizi` (v3'te URL eşleşmesi düzeltildi, canlı TV testi: `NOT_TESTED`), `AnimeciX` (TauVideo/CF korumaları, canlı TV testi: `NOT_TESTED`).
* **`NOT_TESTED`**: Modül kodu ve derlemesi tam olan, ancak fiziksel Android 12 TV Box üzerinde oynatma kanıtı henüz bulunmayan kaynaklar: `FilmMakinesi`, `SinemaCX`, `FullHDFilmizlesene`, `Dizilla`, `DiziYou`, `Animeler` ve diğerleri.
* **`UNAVAILABLE`**: Sağlayıcı tarafında soft 404, WAF engeli veya çözülemeyen oynatıcı bulunan kaynaklar: `BelgeselX`, `Dizigecesi`, `DramaDizilerim`, `WebDramaTurkey`, `RareFilmm`, `SetFilmIzle`, `JetFilmIzle`.

---

## 2. Fiziksel TV Box Hatası ve Kök Neden Analizi

### A. TV Box Canlı Kanıtı
Fiziksel Android 12 TV kutusunda yapılan canlı testlerde:
- **Katalog ve Arama:** Başarılı (2-4 saniyede 137+ kart yüklendi).
- **Film Detayı ve Film Oynatma:** Başarılı (`HDFilmCehennemi` üzerinden film stream'leri akıcı şekilde başladı).
- **Dizi/Anime Detay Sayfası:** Başarılı (Sezon ve bölüm listeleri başarıyla ekranda listelendi).
- **Dizi/Anime Bölüm Oynatma:** **BAŞARISIZ**.
  * Örnek: *Regular Show: The Lost Tapes* (SezonlukDizi), Sezon 1 Bölüm 1.
  * Canlı Log Kaydı: `LOAD_LINKS_RESULT yielded=0 links totalMs=987`.

### B. Kök Neden Tespiti
1. **Gereksiz Ağ Turu (Redundant HTTP Round-trip):**
   v1 ve v2 mimarisinde `TRPlusOne.load()` çalışırken sağlayıcıdan dönen bölüm listesi CloudStream'e teslim ediliyordu; ancak bölümün `data` URL'si yerine kart bazlı soyut rota `https://trplus.one.invalid/episode/{id}/{s}/{e}` atanıyordu.
2. `loadLinks()` çağrıldığında eklenti elinde gerçek bölüm URL'si olmadığı için dizinin ana sayfasını (`api.load(listing.url)`) sıfırdan tekrar indiriyor ve regex/filtre ile `s` ve `e` numaralı bölümü bulmaya çalışıyordu.
3. SezonlukDizi gibi sağlayıcılarda `api.load()` sayfayı parse ederken AJAX veya dinamik betik yapıları nedeniyle tek bölüm araması başarısız oluyor ya da 987 ms içinde boş dönüyordu.
4. Ayrıca SezonlukDizi'deki bazı player'lar (`Pixel`, `Streamruby`, `Vidmoly`) bot korumasına (`reCAPTCHADATA.asp`) takılırken, `Filemoon` (`bysejikuar.com`) embed linkleri doğrudan çözülebilmektedir.
5. Bu nedenle bölüm oynatıcıya hiç stream URL'si iletilemiyordu (`0 links`).

---

## 3. Mimari Çözüm ve Yenilikler

### A. Doğrudan Bölüm Veri Kaydı (`OneEpisodeRegistry`)
* `OneEpisodeRegistry`, `load()` aşamasında sağlayıcılardan toplanan bölümlerin gerçek hedef URL'lerini (`ep.data`) `mediaId:season:episode` anahtarı altında iş parçacığı güvenli (thread-safe) biçimde kaydeder.
* `loadLinks()` tetiklendiğinde soyut URL'den `(id, s, e)` çözülür ve `OneEpisodeRegistry.resolveEpisodeUrls(mediaId, s, e)` ile **O(1) sürede ve ağ çağrısı yapmadan** doğrudan bölümün gerçek linkine ulaşılır:
  ```kotlin
  val targetUrls = OneEpisodeRegistry.resolveEpisodeUrls(route.mediaId, route.season, route.episode)
  for ((providerId, epTargetUrl) in targetUrls) {
      provider.loadLinks(epTargetUrl, ...)
  }
  ```
* Bu sayede 987 ms'lik boş dönen dizi ana sayfa döngüsü tamamen ortadan kaldırılmıştır.

### B. Modüler Sağlayıcı Adaptör Mimarisi (`TRProviderAdapter` & `OneProviders`)
* Tüm sağlayıcılar temiz bir adaptör sözleşmesine bağlandı.
* TV kutusundaki bellek ve CPU kısıtları gözetilerek katalog ve arama sorguları `Semaphore(4)` ile sınırlandırılmıştır; 9 sağlayıcı aynı anda sorgulanırken donanımın kilitlenmesi veya bellek aşımı (OOM) önlenir.
* Bir sağlayıcı hata verirse (`try-catch`), diğer 8 sağlayıcı akışını kesintisiz sürdürür.

### C. Grup 1 (9 Sağlayıcı) Entegrasyonu
1. **HDFilmCehennemi** (`hdfilmcehennemi`): Film ve yabancı dizi, RapidVid / VidMoly.
2. **SezonlukDizi** (`sezonlukdizi`): Yabancı dizi ve çizgi diziler, Byse / Filemoon.
3. **AnimeciX** (`animecix`): Anime dizileri ve filmleri, MakiseKurisu / FirePlayer.
4. **FilmMakinesi** (`filmmakinesi`): Yerli/yabancı film arşivi, CloseLoad / VidMoly.
5. **SinemaCX** (`sinemacx`): Popüler ve klasik sinema filmleri, TauVideo / Filemoon.
6. **FullHDFilmizlesene** (`fullhdfilmizlesene`): Yüksek çözünürlüklü film kataloğu, RapidVid.
7. **Dizilla** (`dizilla`): Popüler yabancı diziler, Byse / Filemoon / Pichive.
8. **DiziYou** (`diziyou`): Asya dizileri, Kore dizileri ve anime içerikleri.
9. **Animeler** (`animeler`): Güncel Türkçe altyazılı anime serileri.

### D. Çoklu Kaynak Tek Kart Birleştirme ve Dil Etiketleri
* Aynı yapım birden fazla sağlayıcıda bulunduğunda `CanonicalEngine` bunları tek bir kart altında toplar.
* `loadLinks()` sırasında yalnızca doğrulanmış dil bilgisi tespit edildiğinde `[Türkçe Dublaj]` ve `[Türkçe Altyazı]` etiketleri eklenir; asla sahte (hallucinated) bağlantı üretilmez.

### E. Gelişmiş Tanılama ve Android Logcat Entegrasyonu (`OneDiagnostics`)
TV Box üzerinde canlı hata ayıklama için yapılandırılmış olaylar hem bellek tamponuna hem de Android Logcat'e (`Log.d("TR+ONE", ...)`) yazılır:
- `EPISODE_DATA_FOUND` / `EPISODE_DATA_MISSING`: Bölüm hedef adresi bulunabildi mi?
- `PROVIDER_LINKS_START`: Sağlayıcı link çıkarma fonksiyonu başladı mı?
- `PROVIDER_LINKS_RESULT`: Bulunan link adedi ve harcanan süre
- `EXTRACTOR_FAILED`: Çözülemeyen embed sağlayıcıları
- `PLAYABLE_LINKS_TOTAL`: Oynatıcıya iletilen nihai link sayısı

---

## 4. Derleme ve Paket Bilgileri

* **Modül:** `TRPlusOne`
* **Sürüm:** `3` (v3)
* **JVM Hedefi:** Java 11 / Kotlin JVM 11
* **Paket Dosyası:** `TRPlusOne/build/TRPlusOne.cs3`
* **Boyut:** 313,914 bytes (~306 KB)
* **SHA-256:** `CCA5D471502FFB4EF3FB54CD00FA6E9FF9275507680CF2053C7778E84F377397`
* **Birim Testleri:** 43 / 43 test **PASS** (%100 başarı)
* **CloudStreamHub Bağımlılığı:** **SIFIR** (Tamamen bağımsız)
