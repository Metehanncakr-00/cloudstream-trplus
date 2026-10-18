# CLOUDSTREAM TR+ v42 — AŞAMA 1 GÜVENLİK VE UYUMLULUK KONTROL LİSTESİ (SAFETY CHECKLIST)

**Tarih:** 18 Ekim 2026  
**Aşama:** 1/3 (Unified Super Hub, Tam Katalog Altyapısı, Android TV Ayarları Kalıcı Çözümü)  
**Çalışma Alanı:** `cloudstream-trplus-v36` (v41 tabanı üzerinde geliştirildi)

---

## 1. Korunan Temel Sistem ve Geriye Dönük Uyumluluk (Preserved Systems)

- [x] **26 Sağlayıcı Bütünlüğü:** Tüm 26 eklenti (`AnimeciX`, `Animeler`, `BelgeselX`, `CizgiMax`, `DDizi`, `DiziIzle`, `DiziKorea`, `DiziLife`, `DiziMom`, `DiziPal`, `DiziYou`, `Dizigecesi`, `Dizilla`, `DramaDizilerim`, `FilmHane`, `FilmMakinesi`, `FilmModu`, `FourKHDHub`, `FullHDFilmizlesene`, `HDFilmCehennemi`, `HDFilmDelisi`, `JetFilmIzle`, `KultFilmler`, `RareFilmm`, `SetFilmIzle`, `SezonlukDizi`, `SinemaCX`, `Sinewix`, `StarTV`, `TurkAnime`, `WebDramaTurkey`, `YesilCamTv`, `YTS`) `tools/validate_repo.py` ile doğrulanmış ve aktiftir.
- [x] **TMDB Federasyonu ve Keşif:** Mevcut TMDB API entegrasyonu korunmuş, sağlayıcı kataloglarıyla `UnifiedCatalogEngine` üzerinden typed katmanda harmanlanmıştır.
- [x] **Türkçe Ses Önceliği (Turkish Audio First):** `StreamPrioritySorter` ve `ManifestLanguageEvidence` entegrasyonu korunmuş; doğrulanmış TR Dublaj / Altyazı sinyalleri önceliklendirilmektedir.
- [x] **Torrent & Debrid Entegrasyonu:** Real-Debrid, Torbox, YTS, FourKHDHub ve Torrentio entegrasyonları korunmuş; mükerrer sorgular engellenmiştir.
- [x] **Manuel Kaynak Seçimi:** Kullanıcının loadLinks sonucundaki akışlardan dilediğini seçebilme kabiliyeti korunmuştur.

---

## 2. Android TV & Düşük RAM Donanım Güvenliği (Hardware & Memory Safety)

- [x] **Sınırlı Hafıza Tamponu (Bounded Memory Buffer):** Düşük RAM'li Android TV cihazlarında (1GB - 2GB RAM) OOM (Out-Of-Memory) çökmesini engellemek için kategori başına hafıza önbelleği en fazla 150 öğe ile sınırlandırılmıştır (`HubCatalogCache.MAX_CACHE_ITEMS_PER_CATEGORY = 150`).
- [x] **Arka Plan Yürütme (No UI Thread Blocking):** Tüm katalog derleme, arama ve sağlayıcı sorguları Kotlin Coroutines (`Dispatchers.IO`) üzerinde sınırlandırılmış eşzamanlılıkla (`Semaphore(3)`, strict 8s timeout) koşturulmaktadır.
- [x] **İptal ve Yaşam Döngüsü (Cancellation):** Kullanıcı kategori değiştirdiğinde veya aramadan çıktığında etkin iş parçacıkları `currentCoroutineContext().ensureActive()` denetimiyle güvenle iptal edilir.
- [x] **Sayfalama ve Kademeli Yükleme (Pagination):** Tek seferde tüm sayfalar çekilmez; her sayfada 20 öğelik artımlı partiler getirilir.

---

## 3. Android TV Arayüz ve Kumanda Uyumluluğu (TV UX & Navigation)

- [x] **"Kaydet" Düğmesi Kalıcı Görünürlüğü:** Ayarlar diyaloğu; kaydırılabilir içerik alanı (`ScrollView weight = 1.0f`) ve bağımsız sabit alt kontrol çubuğu (`LinearLayout weight = 0`) olarak ikiye ayrılmıştır. Kaydet, İptal ve Varsayılan düğmeleri ekranın altına sabitlenmiş olup içerik ne kadar uzun olursa olsun ASLA ekran dışına taşmaz veya kırpılmaz.
- [x] **D-Pad Kumanda Odaklanması:** Tüm sekme başlıkları, ayar kontrolleri ve alt işlem butonları `isFocusable = true` ve `isFocusableInTouchMode = true` yapılandırmasına sahiptir. TV uzaktan kumandası yön tuşlarıyla kesintisiz gezilebilir.
- [x] **Görünür Odak Çerçevesi (High-Contrast Focus State):** TV kumandasıyla odaklanılan buton ve sekmelerde arka plan ve metin renkleri belirgin kontrast farkıyla (`#2196F3` / `#FFC107` / `#333333`) anında ayırt edilir.
- [x] **Çözünürlük Bağımsızlığı:** 720p, 1080p, 4K ekran ölçeklerinde sabit dip bar kırpılmadan ve düğmeler üst üste binmeden render edilir.
- [x] **Kalıcılık (Persistence):** "Kaydet" tıklandığında ayarlar anında `SharedPreferences`'a yazılır; diyalog yeniden açıldığında kaydedilen değerler yüklenir. "İptal" seçildiğinde geçici değişiklikler terk edilir.

---

## 4. Birleşik Katalog & Tek Kart Eşleştirme Güvenliği (Catalog & Merger Safety)

- [x] **Film ve Dizi Ayrımı:** Bir film ile dizi aynı ada sahip olsa dahi asla aynı karta birleştirilmez (`isMovie != candidate.isMovie`).
- [x] **Anime Film ve Anime Dizi Ayrımı:** Anime filmleri ile anime serileri kesinlikle birbirinden ayrı tutulur.
- [x] **Yıl ve Yeniden Çekim (Remake) Ayrımı:** Farklı çıkış yıllarına sahip yapımlar asla tek karta indirgenmez; ayrı kartlar olarak sunulur.
- [x] **Dizi Sezon ve Bölüm Bütünlüğü:** Dizilerde sezon ve bölüm numaraları kesin eşleştirilir; Sezon 0 (özel bölümler / specials) normal sezon bölümlerine bağlanamaz.
- [x] **Şeffaf Kapsama Bildirimi (No Fake Catalogs):** Listeleme sunmayan veya dinamik ana sayfası bulunmayan sağlayıcılar için yapay/uydurma katalog üretilmez; yetenek matrisinde `NO_LISTING` veya `SEARCH_ONLY` olarak şeffaf biçimde raporlanır.
- [x] **İzolasyon (Provider Fault Isolation):** Bir sağlayıcı çökerse veya 8 saniyelik zaman aşımına uğrarsa diğer sağlayıcıların sonuçları etkilenmez ve kullanıcıya sunulur.

---

## 5. Güvenlik, Gizlilik ve Kimlik Koruması (Security & Privacy)

- [x] **Hassas Token Koruması:** Real-Debrid ve Torbox API anahtarları loglara veya dışa aktarılan tanılama raporlarına yazılmaz; `[REDACTED_RD_TOKEN]` ve `[REDACTED_TB_TOKEN]` olarak maskelenir.
- [x] **Hardcoded Kimlik / DRM Bulunmaması:** Kod tabanında hiçbir sabit kullanıcı kimliği, şifresi, kısa ömürlü oturum çerezi veya yetkisiz erişim mekanizması bulunmaz.
- [x] **Ağ Güvenliği & Allowlist:** Yalnızca tanımlı alan adı çözümleyicisi (`config/domains.json`) ve güvenli HTTPS protokolleri kullanılır.

---

## 6. Doğrulama ve Kabul Kriterleri Matrisi (Verification Matrix)

| Test / Kapı (Gate) | Hedef / Kapsam | Durum | Kanıt |
| :--- | :--- | :---: | :--- |
| `:CloudStreamHub:testDebugUnitTest` | 121 Birim Testi (Merger, Catalog, TV, Settings, Home) | **BAŞARILI** | `BUILD SUCCESSFUL in 23s`, 121 passed, 0 failed |
| `tools/validate_repo.py` | Depo ve sağlayıcı bütünlük doğrulaması | **BAŞARILI** | `0 errors! (26 active providers verified, 35 physical modules discovered)` |
| `Single Card Merger` | Aynı film 3 sağlayıcıda tek kart, farklı yıl ayrı kart | **BAŞARILI** | `HubSingleCardMergerTest` (5 test geçti) |
| `TV RAM Safety` | Düşük RAM 150 limit, TTL tampon boşaltımı | **BAŞARILI** | `HubTvNavigationTest` (3 test geçti) |
| `TV D-Pad & Settings` | 7 sekme, sabit alt bar, Kaydet/İptal kalıcılığı | **BAŞARILI** | `HubSettingsManagerTest` & `CloudStreamHubPlugin` |
| `16 Canonical Categories` | 16 kategori eksiksiz haritalama ve filtreleme | **BAŞARILI** | `HubHomeCustomizationTest` & `UnifiedCategories` |

---

## 7. Sonuç ve Aşama 2 İzni

- **Aşama 1 Durumu:** EKSİKSİZ TAMAMLANDI.
- **Dağıtım Kısıtı:** Kullanıcı yönergesi doğrultusunda final v42 ZIP üretimi ve GitHub yayını Aşama 3 sonrasına ertelenmiştir; çalışma ağacı yerel ve geri alınabilir durumdadır.
- **Aşama 2 Geçişi:** Kod tabanı, sonraki aşamada ek sağlayıcıların eklenmesi veya mevcut sağlayıcıların genişletilmesine tamamen hazır ve modülerdir.
