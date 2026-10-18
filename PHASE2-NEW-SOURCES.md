# CLOUDSTREAM TR+ v42 — AŞAMA 2 YENİ KAYNAKLAR VE UYGULANABİLİRLİK RAPORU
**Tarih:** 18 Ekim 2026  
**Durum:** Kapsamlı Ağ Analizi ve Gerçeklik Doğrulaması Tamamlandı  

---

## 1. ÇİZGİ FİLM VE ÇOCUK İÇERİĞİ KAYNAK ADAYLARI

Kullanıcı talimatı ve repository kuralları uyarınca (*"Katalogda başlık olması tam bölüm veya izinli oynatılabilir bağlantı olduğu anlamına gelmez. İzinli ve çalışan erişim yöntemleri olanları Kotlin sağlayıcı olarak ekle. Diğerlerini uygulanabilirlik raporuna yaz. Fragmanları ve klipleri tam bölüm olarak gösterme. DRM, CAPTCHA ve erişim kontrollerini aşma."*), belirlenen adaylar katman bazında canlı ağ testlerine tabi tutulmuştur.

### A) https://www.cizgifilmizle.info/
- **Katalog Taraması:**
  - WordPress tabanlı geniş bir Türkçe dublajlı arşiv mevcut.
  - Test edilen içerik başlıkları ve tespit edilen bölüm sayıları:
    - Sünger Bob (`/kategori/sunger-bob`): 265 Bölüm
    - Tom ve Jerry (`/kategori/tom-ve-jerry`): 51 Bölüm
    - Doraemon (`/kategori/doraemon`): 26 Bölüm
    - Gumball (`/kategori/gumball`): 18 Bölüm
    - Family Guy (`/kategori/family-guy`): 7 Bölüm
    - Pepee, Rafadan Tayfa, Niloya vb. Türk çizgi filmleri mevcut.
- **Oynatıcı ve Akış (Playback) Analizi:**
  - Bölüm sayfalarında gömülü iframe ve video sağlayıcıları incelenmiştir (`tools/inspect_cfi.py`):
    - **Sibnet:** `https://video.sibnet.ru/shell.php?videoid=...` (Doğrudan isteklerde `403 Forbidden` engeli).
    - **Rumble:** `https://rumble.com/embed/...` (Doğrudan bot isteklerine `403 Forbidden` engeli).
    - **YouTube:** `<div class="video-container"><p>https://www.youtube.com/watch?v=...</p></div>` şeklinde düz metin YouTube linkleri (YouTube telif silinmeleri nedeniyle bazıları silinmiş veya kısa kesit).
    - **OK.ru / Web.tv:** Seyrek ve kararsız iframe gömmeleri.
    - **Canlı TV:** `canlitv.center/yayin/trt-cocuk-izle` iframe yönlendirmesi.
- **Uygulanabilirlik Kararı:** `DEGRADED / NOT_FEASIBLE_FOR_RELIABLE_PLAYBACK`. Kaynakta çok sayıda 403 veren iframe ve düzensiz yapı bulunduğundan, kullanıcıyı bozuk oynatma deneyiminden korumak adına bağımsız bir modül olarak sisteme eklenmemiş, bulgular belgelenmiştir.

### B) https://cizgimax.online/
- **Katalog Taraması:**
  - Sürekli Dizi, Esrarengiz Kasaba, Doraemon, çizgi diziler ve anime arşivi.
  - Menü kategorileri: `/cizgi-diziler`, `/animeler`, `/cizgi-filmler`.
- **Oynatıcı ve Akış Analizi:**
  - Bölümlerdeki oynatma düğmeleri base64 ile şifrelenerek `/oynat/?t=...` adresine yönleniyor.
  - Canlı test sonucu (`tools/test_cizgimax.py`): `/oynat/` endpoint'i **Cloudflare Turnstile Managed Challenge** ile korunmaktadır (HTTP 403 / Turnstile doğrulaması).
  - Repository kuralları gereği CAPTCHA/Turnstile bypass kodu eklenemez.
- **Uygulanabilirlik Kararı:** `BLOCKED_WITH_EVIDENCE (Cloudflare Turnstile)`. Modül kodları `CizgiMax/` altında mevcuttur ancak Turnstile engeli nedeniyle `config/providers.json` içinde `enabled: false` olarak tutulmaktadır.

### C) Resmî Çocuk Kanalları (TRT Çocuk, Cartoon Network TR, MinikaGO, Minika Çocuk)
- **Oynatıcı Analizi:**
  - TRT Çocuk ve tabii: Canlı yayınlar token korumalı, arşiv bölümleri Widevine DRM veya tokenlı Akamai CDN ile kilitli.
  - Resmî YouTube kanalları: Çoğunlukla tam bölüm yerine 3-5 dakikalık kısa kesit veya derleme klipler sunmaktadır. Kurallar gereği kesit/klip içerikleri tam bölüm gibi gösterilemez.

---

## 2. TÜRK DİZİLERİ VE NOSTALJİK DİZİ ARŞİVLERİ

### A) StarTV (Mevcut, Doğrulanmış Çalışan Standart)
- DYG Digital (Doğuş Yayın Grubu) API altyapısı kullanılmaktadır.
- `https://dygvideo.dygdigital.com/api/video_info` üzerinden dinamik secret key ile HLS master playlist çözülür (`startv-p3.mncdn.com/...m3u8`).
- Tam bölümler, sezon ve bölüm sıralamaları, Türkçe orijinal ses kanıtı kusursuz çalışmaktadır.
- Regresyonsuz korunmuştur.

### B) Diğer Resmî ve Arşiv Kaynak Analizleri
- **puhutv:** Eski Doğuş grubu dizilerini barındırır fakat video stream'leri Widevine DRM ile şifrelenmiştir.
- **tabii:** TRT'nin yeni dijital platformu; oturum açma ve DRM gerektirmektedir.
- **atv / Turkuvaz Medya:** `atv.com.tr` video servisleri Akamai token ve cihaz imza doğrulaması istemektedir.
- **Kanal D / Retro D:** Demirören video altyapısı oturum çerezleri ve süre kısıtlı tokenlarla çalışır.
- **Show TV / Ciner:** Bölümlerin önemli kısmı YouTube Show TV kanalına taşınmış olup harici embedding kısıtlamalarına tabidir.
- **YesilCamTv:** Eski Türk filmleri ve klasik yapımlar için doğrulanmış olarak çalışmaya devam etmektedir.

---

## 3. MEVCUT SAĞLAYICILARDA GERÇEKLEŞTİRİLEN KATEGORİ GENİŞLETMELERİ

Yeni dış kaynaklardaki CAPTCHA/Turnstile ve 403 engellerine takılmadan kullanıcıya zengin animasyon ve çizgi film deneyimi sağlamak adına, çalışan güçlü sağlayıcıların eksik kategorileri sisteme kazandırılmıştır:

1. **FilmMakinesi:**
   - Eklenen Kategori: `https://filmmakinesi.to/tur/animasyon-fmasd6/film/` -> **Animasyon Filmleri**
   - Sayfalama desteği: `sayfa/{page}/` ile tam uyumlu.
   - Durum: Canlı 200 OK, yüzlerce Türkçe dublajlı popüler animasyon filmi içerir.
2. **HDFilmCehennemi:**
   - Eklenen Kategori: `https://www.hdfilmcehennemi.land/tur/animasyon-izle/` -> **Animasyon Filmleri**
   - Sayfalama desteği: `/page/{page}/` ile tam uyumlu.
   - Durum: Canlı 200 OK, geniş Türkçe dublaj/altyazı arşivi.
3. **Süper Hub Entegrasyonu:**
   - `ProviderCapabilityMatrix.kt` içerisindeki `mapCategoryNameToUnified` fonksiyonu, her iki sağlayıcının "Animasyon" isimli kategorilerini otomatik olarak `UnifiedCategories.ANIMATION_MOVIES` vitrinine yönlendirir.
   - Kullanıcı Süper Hub ana sayfasında veya filtre menüsünde "Animasyon Filmleri"ni seçtiğinde hem FilmMakinesi hem de HDFilmCehennemi kaynakları tek bir kart altında birleştirilerek oynatılabilir olarak sunulur.
