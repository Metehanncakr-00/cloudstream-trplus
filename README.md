# CloudStream TR+ — Eklenti Deposu (Extensions Repository)

> TR+ için güncel kurulum, yerel paketler, doğrulama sınırları ve geri dönüş: **[README-TR.md](README-TR.md)**. Paket derlemesi gerçek oynatma değildir; Android 12 cihaz kabulü ayrıca yapılmalıdır. Bu çalışma kendiliğinden yayın yapmaz. Aşağıdaki upstream rozetleri TR+ doğrulama sonucu olarak yorumlanmamalıdır.

[![Build & Publish](https://github.com/Emre-Kahveci/CloudStreamHub/actions/workflows/build.yml/badge.svg)](https://github.com/Emre-Kahveci/CloudStreamHub/actions/workflows/build.yml)
[![Validate](https://github.com/Emre-Kahveci/CloudStreamHub/actions/workflows/validate.yml/badge.svg)](https://github.com/Emre-Kahveci/CloudStreamHub/actions/workflows/validate.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Eski `Kraptor123/cs-kraptor` Türkçe eklenti ekosisteminin adli analiz (forensics) ve modernizasyon süreci sonucunda, güncel resmî **CloudStream 3** mimarisine tam uyumlu, güvenli, test edilebilir ve otomatik derlenen yeni nesil Türkçe eklenti deposudur.

---

## ⚡ Hızlı Kurulum (Tek Tıkla Kurulum)

CloudStream uygulaması cihazınızda yüklüyse:

👉 **[CloudStream'e Doğrudan Ekle (Tek Tıkla Kurulum)](cloudstreamrepo://raw.githubusercontent.com/Metehanncakr-00/cloudstream-trplus/builds/repo.json)**

---

## 📱 Manuel Kurulum Adımları

1. Cihazınızda **CloudStream 3** uygulamasını açın.
2. **Ayarlar (Settings)** &rarr; **Eklentiler (Extensions)** &rarr; **Depo Ekle (Add Repository)** sekmesine gidin.
3. Aşağıdaki Depo URL'sini kopyalayıp ilgili alana yapıştırın:

```text
https://raw.githubusercontent.com/Metehanncakr-00/cloudstream-trplus/builds/repo.json
```

4. Depo eklendikten sonra listeden dilediğiniz eklentiyi seçip **Yükle (Install)** butonuna basarak anında kullanmaya başlayabilirsiniz!

---

## 📺 Android TV Box Gerçek Cihaz Test Kontrol Listesi (v42)

> **Durum:** `manualPlayback = NOT_TESTED`, `deviceVerification = PENDING`.
> Kısa depo bağlantısı (`https://cutt.ly/7yEDkjvg`) ve doğrudan depo adresi (`https://raw.githubusercontent.com/Metehanncakr-00/cloudstream-trplus/builds/repo.json`) korunmuştur.

TV Box cihazınızda v42 güncellemesini aldıktan sonra aşağıdaki maddeleri doğrulayın:
- **CloudStreamHub v42 Güncellemesi:** Eklenti listesinde `CloudStreamHub` sürümünün **42** olarak göründüğünü ve 26 aktif eklentinin tamamının yüklendiğini kontrol edin.
- **Süper Eklenti Birleşik Katalog:** Ana sayfada CloudStreamHub seçiliyken birleşik kataloğun yüklendiğini ve aşağı kaydırmada sayfalamanın (pagination) veri kaybı olmadan devam ettiğini doğrulayın.
- **Aynı Film İçin Tek Kart ve Çoklu Kaynaklar:** Aynı içeriğin tek kartta birleştiğini, oynatma aşamasında birden fazla sağlayıcının bağlantılarının birlikte listelendiğini ve favorilerden soğuk yeniden başlatma sonrası açılışta korunduğunu doğrulayın.
- **Türkçe Dublaj / Altyazı Seçenekleri:** Kaynak listesinde Türkçe Dublaj ve Türkçe Altyazı akışlarının önceliklendirildiğini kontrol edin.
- **Kaydet / İptal Ayarları:** CloudStreamHub TV ayar ekranında kumanda (D-Pad) ile **Kaydet**, **İptal** ve **Sıfırla** butonlarının çalıştığını ve kaydedilen ayarların yeniden başlatmada korunduğunu doğrulayın.
- **Film / Dizi / Animasyon Kategorileri:** *Filmler, Diziler, Türk Dizileri, Nostaljik Türk Dizileri, Çizgi Filmler, Çizgi Diziler, Animasyon Filmleri, Anime, Belgeseller, Yeşilçam* kategorilerinin içerik getirdiğini kontrol edin.

---

## 📦 Sağlayıcı Modülleri

Etkin eklentilerin güncel listesi [config/providers.json](config/providers.json) dosyasındadır. Aşağıdaki modül tablosu devre dışı sağlayıcıları da içerir; listede bulunmak doğrulanmış oynatma anlamına gelmez. CloudStreamHub'ın kalite ve kaynak seçimi [bağlantı çözümleme dokümanında](docs/LINK_RESOLUTION.md) açıklanmıştır.

| Eklenti Adı | Modül Adı | Türler | Canonical Domain | Açıklama |
|---|---|---|---|---|
| 🌟 **CloudStreamHub Aggregator** | `CloudStreamHub` | Film, Dizi, Anime, Çizgi Dizi, Belgesel | [api.themoviedb.org](https://api.themoviedb.org) | **Süper Eklenti**: TMDB Keşif & Tüm Türkçe Sağlayıcıları Birleştiren Federated Motor |
| **HDFilmCehennemi** | `HDFilmCehennemi` | Film, Dizi | [hdfilmcehennemi.nl](https://www.hdfilmcehennemi.nl) | Güncel yabancı ve yerli filmler, hızlı CDN yayınları |
| **FullHDFilmizlesene** | `FullHDFilmizlesene` | Film | [fullhdfilmizlesene.now](https://www.fullhdfilmizlesene.now) | RapidVid & Turbovid yerli/yabancı film arşivi |
| **Film Modu** | `FilmModu` | Film | [filmmodu15.com](https://www.filmmodu15.com) | Yüksek kaliteli 1080p yerli ve yabancı film arşivi |
| **Film Makinesi** | `FilmMakinesi` | Film, Dizi | [filmmakinesi.to](https://filmmakinesi.to) | CloseLoad & Vidmoly entegrasyonlu geniş film arşivi |
| **JetFilmİzle** | `JetFilmIzle` | Film | [jetfilmizle.now](https://jetfilmizle.now) | Güncel sinema ve film arşivi |
| **SinemaCX** | `SinemaCX` | Film | [sinemacc.com](https://sinemacc.com) | FilmizleIn & Vidmoly alternatifli film arşivi |
| **Kült Filmler** | `KultFilmler` | Film, Dizi | [kultfilmler.net](https://kultfilmler.net) | Klasik ve kült filmler arşivi |
| **Yeşilçam TV** | `YesilCamTv` | Film | [yesilcamtv.com.tr](https://yesilcamtv.com.tr) | Nostaljik Yeşilçam klasik Türk sineması |
| **Dizilla** | `Dizilla` | Dizi | [dizilla.now](https://dizilla.now) | Pichive & FourPichive güncel yabancı dizi arşivi |
| **Sezonluk Dizi** | `SezonlukDizi` | Dizi | [sezonlukdizi.cc](https://sezonlukdizi.cc) | Kapsamlı yabancı dizi ve sezon takibi |
| **DiziMom** | `DiziMom` | Dizi | [dizimom.diy](https://www.dizimom.diy) | Peacemaker & Vidmoly yabancı dizi arşivi |
| **DiziYou** | `DiziYou` | Dizi | [diziyou.one](https://www.diziyou.one) | Popüler yabancı diziler ve hızlı oynatıcılar |
| **DDizi** | `DDizi` | Dizi | [ddizi.pro](https://www.ddizi.pro) | Yerli ve yabancı dizi arşivi, güncel bölüm takibi |
| **DiziPal** | `DiziPal` | Dizi, Film | [dizipal1430.com](https://dizipal1430.com) | Popüler dijital platform dizileri ve filmleri |
| **Sinewix** | `Sinewix` | Film, Dizi | [sinewix.net](https://sinewix.net) | Geniş film ve dizi arşivi, dublaj & altyazı seçenekleri |
| **ÇizgiMax** | `CizgiMax` | Çizgi Dizi, Anime, Çizgi Film | [cizgimax.online](https://cizgimax.online) | Nostaljik ve güncel çizgi dizi & animasyon arşivi |
| **Türk Anime TV** | `TurkAnime` | Anime, Anime Film | — | Devre dışı: kaynak site kalıcı olarak kapandı ve alan adlarını satışa çıkardı |
| **AnimeciX** | `AnimeciX` | Anime | [animecix.tv](https://animecix.tv) | Modern anime arşivi ve hızlı API entegrasyonu |
| **BelgeselX** | `BelgeselX` | Belgesel | [belgeselx.com](https://belgeselx.com) | Türkçe dublajlı doğa, tarih, bilim belgeselleri |
| **DiziKorea** | `DiziKorea` | Asya Dizisi, Kore Dizisi | [dizikorea3.com](https://dizikorea3.com) | Güncel Asya ve Kore dizileri, hızlı oynatıcılar |
| **DramaDizilerim** | `DramaDizilerim` | Asya Draması | [dramadizilerim.com](https://dramadizilerim.com) | Özel Asya dizileri, doğrudan CDN ve Türkçe altyazı |
| **WebDramaTurkey** | `WebDramaTurkey` | Asya Draması | [webdramaturkey2.com](https://webdramaturkey2.com) | Web Drama dizileri ve bridge player video akışı |
| **Animeler** | `Animeler` | Anime, Anime Film | [animeler.pw](https://animeler.pw) | Geniş anime dizileri ve AnizmPlayer video akışı |
| **SetFilmİzle** | `SetFilmIzle` | Film, Dizi | [setfilmizle.ltd](https://www.setfilmizle.ltd) | Dev film ve dizi arşivi, SetPlay (FastPlay) akışları |
| **DiziLife** | `DiziLife` | Dizi, Film | [dizi74.life](https://dizi74.life) | Yerli ve yabancı dizi arşivi, alternatif playerlar |
| **HDFilmDelisi** | `HDFilmDelisi` | Film | [hdfilmdelisi.one](https://hdfilmdelisi.one) | Geniş film arşivi, doğrudan embed & iframe oynatıcılar |
| **Dizigecesi** | `Dizigecesi` | Dizi, Film | [dizigecesi.com](https://dizigecesi.com) | Güncel yabancı dizi ve film arşivi, Vidmoly & AJAX player |
| **RareFilmm** | `RareFilmm` | Film | [rarefilmm.com](https://rarefilmm.com) | Nadir ve klasik dünya sineması filmleri, OkRu akışları |
| **FilmHane** | `FilmHane` | Film, Dizi | [filmhane.shop](https://www.filmhane.shop) | Full HD film ve dizi arşivi, hızlı Vidmoly CDN akışları |

---

## 🛡️ Temel Mimari ve Güvenlik İlkeleri

1. **Çalışma Zamanı (Runtime) Link Çözümleme**: Bu depoda hiçbir kalıcı `.m3u8` veya geçici CDN yayın URL'si commit edilmez. Yayın bağlantıları kullanıcı oynat tuşuna bastığında `loadLinks()` üzerinden taze ve yetkili olarak çözümlenir. Detaylar için [docs/LINK_RESOLUTION.md](docs/LINK_RESOLUTION.md) belgesini inceleyin.
2. **Domain Hijack Koruması**: Eski domainlerin el değiştirmesi riskine karşı kör yönlendirme takip edilmez. Domainler `config/domains.json` allowlist'i ve içerik işaretçileri (fingerprint) ile doğrulanır. Detaylar için [docs/DOMAIN_MANAGEMENT.md](docs/DOMAIN_MANAGEMENT.md) belgesini inceleyin.
3. **DRM / Widevine / Kimlik Bilgisi Yasağı**: Hiçbir eklenti DRM koruması aşmaz, ücretli abonelik atlatmaz veya kullanıcı hesabı çerezi kullanmaz. Yalnızca kamuya açık, serbest kaynaklar desteklenir.
4. **Atomik Yayınlama**: Derleme ve test süreçleri başarıyla tamamlanmadan `builds` branch'ine hiçbir dosya yüklenmez; mevcut kullanıcıların çalışan sürümleri korunur.

---

## 🛠️ Geliştiriciler İçin Yerel Derleme (Local Development)

### Gereksinimler
- **JDK 17 veya JDK 21**
- **Android SDK (API 35)**
- **Git**

### Komutlar

```powershell
# Eklentileri derlemek ve .cs3 oluşturmak için:
./gradlew make

# plugins.json dosyasını üretmek için:
./gradlew makePluginsJson

# Statik doğrulama ve bütünlük testi:
powershell -ExecutionPolicy Bypass -File tools/validate_repo.ps1
# veya Python ile:
python tools/validate_repo.py --verify-artifacts

# Domain sağlık testi:
powershell -ExecutionPolicy Bypass -File tools/domain_health.ps1
```

---

## 🤝 Katkıda Bulunma ve Hata Bildirimi

- Yeni bir eklenti geliştirmek istiyorsanız [docs/PROVIDER_DEVELOPMENT.md](docs/PROVIDER_DEVELOPMENT.md) ve [CONTRIBUTING.md](CONTRIBUTING.md) belgelerini okuyunuz.
- Çalışmayan bir eklenti bildirmek için [Bozuk Eklenti Formu](.github/ISSUE_TEMPLATE/provider-broken.yml) kullanabilirsiniz.
- Güvenlik bildirimleri için [SECURITY.md](SECURITY.md) belgesini inceleyiniz.

## 📄 Lisans ve Yasal Uyarı

Bu proje [GNU General Public License v3.0](LICENSE) altında lisanslanmıştır. Eklentiler yalnızca kamuya açık web sayfalarının kullanıcı adına standart HTTP protokolüyle ayrıştırılması prensibiyle çalışır; repository sunucularında hiçbir video, ses veya medya içeriği barındırılmaz.
