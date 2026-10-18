# CloudStream TR+ v42 — Kullanım ve Kurulum Kılavuzu (README-v42)

**Sürüm:** v42 (Unified Super Hub + Complete Catalog + TV Settings)  
**Yayın Tarihi:** 18 Ekim 2026  
**Uyumlu Uygulama:** CloudStream 3 / 4 (v4.8.0 ve üzeri)  
**Hedef Platformlar:** Android TV (1080p / 4K, Android 9-14), Google TV, Android TV Box, Android Telefon ve Tablet  

---

## 1. CloudStream TR+ v42 Nedir?

CloudStream TR+ v42, Türkçe içerik öncelikli, 26 aktif sağlayıcıyı tek bir akıllı merkezde toplayan ve Android TV kullanıcı deneyimine özel olarak optimize edilmiş açık kaynaklı bir CloudStream eklenti deposudur.

### v42 ile Gelen Başlıca Yenilikler:
1. **Süper Eklenti 2.0 (Unified Super Hub):**
   - Tüm aktif sağlayıcıların içerikleri tek bir typed `UnifiedCatalog` katmanında birleştirildi.
   - Sayfalama (pagination) ve sonsuz kaydırma (infinite scroll) desteği eklendi.
2. **16 Birleşik Kategori:**
   - Dağınık sağlayıcı menüleri yerine: *Tüm İçerikler, Filmler, Diziler, Türk Dizileri, Nostaljik Türk Dizileri, Çizgi Filmler, Çizgi Diziler, Animasyon Filmleri, Anime, Belgeseller, Yeşilçam, Türkçe Dublaj, Türkçe Altyazı, Yeni Eklenenler, Popüler İçerikler, İzlemeye Devam Et*.
3. **Tek Kart, Bütün Sağlayıcılar (Single Card Merger):**
   - Aynı film veya dizi farklı sağlayıcılarda varsa tek bir kart gösterilir; mükerrer kart kirliliği önlenir.
   - Detay sayfasında TMDB yüksek kaliteli afişi, konusu ve oyuncuları sunulurken, oynatma aşamasında eşleşen tüm sağlayıcıların akışları (`FastPlay`, `Vidmoly`, `TurboVid`, `RapidVid`, `Torrentio`) listelenir.
4. **Android TV ve Kumanda Uyumluluğu:**
   - D-Pad odaklanma sorunları giderildi.
   - Ayarlar ekranındaki **Kaydet**, **İptal** ve **Sıfırla** butonları tamamen işlevsel hale getirildi; ayarlar cihaz yeniden başlatıldığında korunur.
5. **Akıllı Önbellek, Sıfır Kayıplı Sayfalama ve Ağ Optimizasyonu:**
   - **Sıfır Kayıplı Çoklu Sayfa Taşma (Overflow Pagination):** `HubCatalogCache.putUnified()` içinde 300 öğeyi aşan birleşik katalog sonuçları (301, 500, 1000+ içerik) kesilmez; otomatik olarak ardışık taşma sayfalarına (`p1`, `p2`, `p3`, `p4`...) bölünerek hem belleğe hem diske sıfır veri kaybı ve sıfır mükerrerlikle yazılır.
   - **Tarayıcı Önbelleğinin (`getProviderCatalog`) Gerçek Tüketimi:** `crawlBatch()` tarafından yazılan sağlayıcı katalog sayfaları hem `UnifiedCatalogEngine.getCategoryPage()` (birleşik ana sayfa) hem de `UnifiedCatalogEngine.searchIndexedCatalog()` / `CloudStreamHub.search()` tarafından doğrudan tüketilir; tarama ilerledikçe katalog kapsamı genişler ve önbellekteki sayfalar için tekrar ağ isteği atılmaz.
   - **Artımlı Çoklu Kaynak Birleştirme ve Tahliye Koruması (`HubMediaRegistry`):** Farklı zamanlarda veya ayrı tarama turlarında (`crawlBatch`) aynı içeriği getiren sağlayıcıların referansları (`providerReferences`) üzerine yazılmadan tek canonical kayıtta birleştirilir. Kayıtlar `filesDir/hub_persistent_registry_v4` altında Android `cacheDir` tahliyesinden korunur ve toplu (batch) atomik `.tmp` yazımıyla disk maliyeti minimize edilir.
   - Ağ istekleri `Semaphore` ile sınırlandırılarak zayıf donanımlı Android TV kutularında donma ve kilitlenmeler engellendi.
6. **Sağlayıcı Onarımları:**
   - `SetFilmIzle`: FastPlay unquoted token & FNV-1a `X-Sp` doğrulama başlığı onarıldı (HTTP 200).
   - `Dizigecesi`: VidmolyNet / VidmolyMe çıkarıcı köprüsü ve URL fragment temizliği eklendi.
   - `Dizilla`: AES cipher search API yanıtı ve Pichive oynatıcı Referer başlığı onarıldı (Doğrulanmış TR ses).
   - `FullHDFilmizlesene`: RapidVid serileştirme hatası ve CDN akış tespiti onarıldı.
   - `FilmMakinesi` & `HDFilmCehennemi`: Animasyon filmleri kategorileri entegre edildi.

---

## 2. Kurulum Kılavuzu

### Yöntem A: CloudStream İçinden Depo Ekleme (Önerilen)
1. Android TV veya telefonunuzda **CloudStream** uygulamasını açın.
2. **Ayarlar (Settings) > Eklentiler (Extensions)** bölümüne gidin.
3. **Depo Ekle (Add Repository)** seçeneğini seçin.
4. Aşağıdaki bilgileri eksiksiz girin:
   - **Depo Adı:** `CloudStream TR+`
   - **Depo URL:** `https://raw.githubusercontent.com/Metehanncakr-00/cloudstream-trplus/builds/repo.json`
5. **İndir / Ekle** butonuna basın. Depo eklendikten sonra listeden dilediğiniz sağlayıcıları (başta `CloudStreamHub`, `HDFilmCehennemi`, `FilmMakinesi`, `DiziYou`, `SetFilmIzle`, `HDFilmDelisi`) tek tıkla yükleyin.

### Yöntem B: Çevrimdışı / Manuel `.cs3` Kurulumu
1. `cloudstream-trplus-v42-builds.zip` arşivini bilgisayarınızda veya telefonunuzda açın.
2. İçindeki `.cs3` dosyalarını cihazınıza aktarın (USB bellek, ADB veya Send Files to TV ile).
3. CloudStream uygulamasında **Eklentiler > Dosyadan Yükle (.cs3)** yolunu izleyerek istediğiniz modülleri yükleyin.

---

## 3. Sağlayıcı Manifest Sürümleri ve Durum Tablosu (26 Eklenti)

| Eklenti (Modül) | v41 Sürümü | v42 Sürümü | Türler | Durum |
| :--- | :---: | :---: | :--- | :--- |
| **CloudStreamHub** | 41 | **42** | Movie, TvSeries, Anime, Documentary | **Süper Eklenti 2.0 / Birleşik Merkez** |
| **SetFilmIzle** | 21 | **22** | Movie, TvSeries | **Onarıldı (FastPlay unquoted token & FNV-1a)** |
| **Dizigecesi** | 21 | **22** | TvSeries | **Onarıldı (VidmolyNet/VidmolyMe extractor köprüsü)** |
| **Dizilla** | 21 | **22** | TvSeries | **Onarıldı (AES cipher search & Pichive Referer)** |
| **FullHDFilmizlesene** | 20 | **21** | Movie | **Onarıldı (RapidVid Jackson fix & TS stream)** |
| **HDFilmDelisi** | 20 | **21** | Movie | **Korundu (Vixolity RSC Doğrudan HLS)** |
| **HDFilmCehennemi** | 111 | **112** | Movie, TvSeries, Animasyon | **Genişletildi (Animasyon Kategorisi Eklendi)** |
| **FilmMakinesi** | 110 | **111** | Movie, Animasyon | **Genişletildi (Animasyon Kategorisi Eklendi)** |
| **DiziYou** | 21 | **22** | TvSeries | **Korundu (AES-CBC & Türkçe Dublaj Önceliği)** |
| **StarTV** | 6 | **7** | Live, TvSeries | **Korundu (MnCDN Canlı Yayın & Yerli Arşiv)** |
| **YTS** | 60 | **61** | Torrent, Movie | **Korundu (Torrentio & Çoklu Ayna Magnet)** |
| **YesilCamTv** | 31 | **32** | Movie | **Korundu (YouTube Klasik Türk Sineması Arşivi)** |
| **BelgeselX** | 111 | **112** | Documentary | **Korundu (Özel HLS Video Çıkarıcı)** |
| **DramaDizilerim** | 20 | **21** | TvSeries, AsianDrama | **Korundu (Dzen/VK Asya Dizileri)** |
| **DiziKorea** | 21 | **22** | AsianDrama | **Korundu (Fembed/Vidmoly Kore Dizileri)** |
| **WebDramaTurkey** | 21 | **22** | AsianDrama | **Korundu (Dtpasn Asya Drama Akışı)** |
| **KultFilmler** | 53 | **54** | Movie | **Korundu (Vidmoly & Supervideo)** |
| **SinemaCX** | 20 | **21** | Movie | **Korundu (Vidmoly & Streamtape)** |
| **JetFilmIzle** | 21 | **22** | Movie | **Korundu (PlayerX & Faststream HLS)** |
| **RareFilmm** | 21 | **22** | Movie | **Korundu (Nadir Dünya Sineması & OK.ru)** |
| **BlenderOpenMovies**| 5 | **6** | Movie | **Korundu (PeerTube WebTorrent/MP4)** |
| **DiziIzle** | 7 | **8** | TvSeries | **DEGRADED (Kısmi Oynatma / Kaynak 404)** |
| **AnimeciX** | 110 | **111** | Anime | **DEGRADED (TauVideo Cloudflare Koruması)** |
| **Animeler** | 21 | **22** | Anime | **DEGRADED (Makisekurisu CDN 404)** |
| **FourKHDHub** | 10 | **11** | Movie, TvSeries | **BLOCKED_WITH_EVIDENCE (AdMaven/Bot Duvarı)** |
| **SezonlukDizi** | 21 | **22** | TvSeries | **BLOCKED_WITH_EVIDENCE (Cloudflare Turnstile)** |

---

## 4. Dürüstlük Beyanı ve Bilinen Durumlar

Proje ilkeleri gereğince, çalışmayan hiçbir özellik "çalışıyor" olarak sunulmaz:
- **Fiziksel Android TV / Emülatör Test Durumu (`NOT_TESTED`):** Tüm eklentiler derlenmiş, paketlenmiş, birim testlerinden ve canlı ağ/çıkarıcı problarından geçirilmiştir. Ortamda bağlı bir Android TV cihazı veya emülatör bulunmadığından (`adb devices` -> 0 cihaz), fiziksel/emülatör UI ve oynatma testi **NOT_TESTED (TEST EDİLMEDİ)** olarak raporlanmıştır.
- **Engelli Sağlayıcılar:** `FourKHDHub` (AdMaven yönlendirici bot duvarı) ve `SezonlukDizi` (Cloudflare Turnstile CAPTCHA) harici bot korumaları nedeniyle doğrudan akış sunamamaktadır; kodları ayrıştırıcı seviyesinde güncel tutulmakla birlikte engelli durumları şeffafça bildirilmiştir.
- **Yeni Sağlayıcı Adayları:** Yapılan fizibilite araştırmasında `cizgimax.online` (Turnstile), `cizgifilmizle.info` (404 ölü endpointler) ve `dizipub` (Cloudflare challenge) bot duvarları nedeniyle bu sürümde sisteme eklenmemiştir. Ayrıntılar `NEW-PROVIDER-FEASIBILITY-v42.md` belgesindedir.
- **9 Devre Dışı Modül:** Kapanan veya erişilemeyen 9 modül (`CizgiMax`, `DDizi`, `DiziLife`, `DiziMom`, `DiziPal`, `FilmHane`, `FilmModu`, `Sinewix`, `TurkAnime`) `settings.gradle.kts` ve `config/providers.json` içinde devre dışı tutulmaktadır.

---

## 5. Paket Bütünlüğü ve Dağıtım Arşivleri

Tüm `.cs3` dosyaları `build/plugins.json` içindeki `fileHash` ve `fileSize` değerleriyle %100 uyumludur.

| Dosya Adı | Boyut (Bayt) | SHA-256 Sağlama Toplamı |
| :--- | :---: | :--- |
| `cloudstream-trplus-v42-builds.zip` | 4.516.063 | `69f3d1662b8b85e420860c8c89b06661d457f21a9a4343320e5a605682c86948` |
| `CloudStreamHub/build/CloudStreamHub.cs3` | 594.831 | `362c8959a5f86c2208541da1980c9f77fd02be036117f95a6ffa42d3382e875d` |
| `build/plugins.json` | 21.495 | `f67fee22ba064b15a0b46b5c8dbeefe8309561c1d8a83049eb98b5865c2600bb` |
| `cloudstream-trplus-v42-main.zip` | 2.140.799 | `a16dd161df1f76a95bb80e8a5b5358acbac9336db45dc5b1d54d8320d5f10588` |

---

## 6. Android TV Box Gerçek Cihaz Test Kontrol Listesi (`manualPlayback=NOT_TESTED`, `deviceVerification=PENDING`)

GitHub üzerinden v42 yayınlandıktan sonra Android TV Box cihazınızda aşağıdaki adımları sırasıyla test edin:

1. **CloudStreamHub v42 Güncellemesi:**
   - CloudStream > **Ayarlar > Eklentiler > CloudStream TR+** deposuna girin (Depo URL: `https://raw.githubusercontent.com/Metehanncakr-00/cloudstream-trplus/builds/repo.json` veya kısa bağlantı: `https://cutt.ly/7yEDkjvg`).
   - `CloudStreamHub` eklentisinin **sürüm 42** olarak göründüğünü ve 26 eklentinin tamamının güncellenebildiğini doğrulayın.
2. **Süper Eklenti Birleşik Katalog:**
   - Ana sayfa kaynağını **CloudStreamHub** olarak seçin.
   - Ana sayfanın TMDB ve aktif Türkçe sağlayıcılarla birleşik olarak yüklendiğini ve aşağı kaydırdıkça sayfalamanın (pagination) kesintisiz devam ettiğini kontrol edin.
3. **Aynı Film İçin Tek Kart ve Çoklu Kaynaklar:**
   - Birden fazla sağlayıcıda bulunan popüler bir filmi veya diziyi açın; katalogda ve aramada **tek kart** olarak birleştiğini, oynat tuşuna basıldığında ise birden fazla sağlayıcının akışlarının (`SetFilmIzle`, `HDFilmCehennemi`, `FilmMakinesi`, `FullHDFilmizlesene`, `DiziYou` vb.) birlikte listelendiğini doğrulayın.
   - İçeriği **Favorilere** ekleyip uygulamayı tamamen kapatıp yeniden açtıktan sonra favorilerden doğrudan açtığınızda çoklu sağlayıcı kaynaklarının korunduğunu kontrol edin.
4. **Türkçe Dublaj / Altyazı Seçenekleri:**
   - Oynatıcı kaynak listesinde **Türkçe Dublaj** ve **Türkçe Altyazı** etiketlerinin doğru sıralandığını ve seçilen akışın sorunsuz oynatıldığını doğrulayın.
5. **Kaydet / İptal / Sıfırla Ayarları:**
   - Eklenti ayarlarından **CloudStreamHub Ayarları** ekranını kumanda (D-Pad) ile açın.
   - Bir ayarı değiştirip **İptal** dediğinizde eski değerin korunduğunu, **Kaydet** dediğinizde ve uygulamayı yeniden başlattığınızda yeni ayarın kalıcı olduğunu kontrol edin.
6. **Film / Dizi / Animasyon Kategorileri:**
   - *Filmler, Diziler, Türk Dizileri, Nostaljik Türk Dizileri, Çizgi Filmler, Çizgi Diziler, Animasyon Filmleri, Anime, Belgeseller, Yeşilçam, Türkçe Dublaj, Türkçe Altyazı* kategorilerinin her birinin içerik listelediğini doğrulayın.




