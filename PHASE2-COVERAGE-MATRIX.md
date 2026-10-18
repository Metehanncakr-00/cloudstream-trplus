# CLOUDSTREAM TR+ v42 — AŞAMA 2 SAĞLAYICI KAPSAMA VE OYNATMA MATRİSİ
**Tarih:** 18 Ekim 2026  
**Durum:** Tam Kapsama ve Oynatma Durum Raporu  

---

## 1. 26 AKTİF SAĞLAYICININ DETAYLI KATMAN MATRİSİ

| # | Sağlayıcı Modülü | Türler | Durum | Domain / Canonical | Oynatma / Çözümleyici Altyapısı | Ses / Altyazı Dili |
| :- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **CloudStreamHub** | Tüm Türler | **IMPLEMENTED_WORKING** | Hub Sanal Sağlayıcı | Birleşik Süper Hub Motoru (26 sağlayıcı federasyonu) | TR Dub, TR Sub, Çoklu Dil |
| 2 | **SetFilmIzle** | Film, Dizi | **IMPLEMENTED_WORKING** (Onarıldı) | `setfilmizle.ltd` | FastPlay HLS (`X-Sp` dinamik tokenlı m3u8) | Türkçe Dublaj & Altyazılı |
| 3 | **Dizigecesi** | Dizi, Film | **IMPLEMENTED_WORKING** (Onarıldı) | `dizigecesi.com` | Vidmoly HLS (`vidmoly.net`, `vidmoly.me`, `vidmoly.to`) | Türkçe Altyazılı & Dublaj |
| 4 | **Dizilla** | Dizi | **IMPLEMENTED_WORKING** (Onarıldı) | `dizilla.club` | Pichive HLS (`m.php` HLS playlist + `ld.php` Türkçe ses) | Türkçe Dublaj (Doğrulanmış TR ses) |
| 5 | **FullHDFilmizlesene**| Film | **IMPLEMENTED_WORKING** (Onarıldı) | `fullhdfilmizlesene.now` | RapidVid HLS (`window._p8` şifresi çözülmüş CDN stream) | Türkçe Dublaj & Altyazılı |
| 6 | **HDFilmDelisi** | Film | **IMPLEMENTED_WORKING** (Korundu) | `hdfilmdelisi.top` | Doğrudan CDN HLS m3u8 oynatma | Türkçe Dublaj & Altyazılı |
| 7 | **HDFilmCehennemi** | Film, Dizi, Animasyon | **IMPLEMENTED_WORKING** (Genişletildi) | `hdfilmcehennemi.nl` | VidMoly, CloseLoad, Filemoon, HLS | Türkçe Dublaj & Altyazılı |
| 8 | **FilmMakinesi** | Film, Dizi, Animasyon | **IMPLEMENTED_WORKING** (Genişletildi) | `filmmakinesi.to` | TurboVid, RapidVid, HLS | Türkçe Dublaj & Altyazılı |
| 9 | **DiziYou** | Dizi | **IMPLEMENTED_WORKING** (Korundu) | `diziyou.vip` | AES-CBC şifre çözücü, Vidmoly, Filemoon | Türkçe Altyazılı & Dublaj |
| 10 | **StarTV** | Türk Dizileri | **IMPLEMENTED_WORKING** (Korundu) | `startv.com.tr` | DYG Digital API, MnCDN HLS m3u8 | Türkçe Orijinal |
| 11 | **YTS** | Torrent Film | **IMPLEMENTED_WORKING** (Korundu) | `yts.mx` | YTS API, Torrentio, WebTorrent / Harici Player | Orijinal + TR Altyazı |
| 12 | **YesilCamTv** | Klasik Türk Filmleri | **IMPLEMENTED_WORKING** (Korundu) | `yesilcamtv.net` | OkRu, VK, doğrudan video | Türkçe Orijinal |
| 13 | **BelgeselX** | Belgesel | **IMPLEMENTED_WORKING** (Korundu) | `belgeselx.com` | BelgeselX özel HLS oynatıcısı | Türkçe Dublaj |
| 14 | **DramaDizilerim** | Asya Dizileri | **IMPLEMENTED_WORKING** (Korundu) | `dramadizilerim.org` | Dzen, VK, MailRu, Sibnet | Türkçe Altyazılı |
| 15 | **DiziKorea** | Kore / Asya Dizileri | **IMPLEMENTED_WORKING** (Korundu) | `dizikorea.art` | Fembed, Vidmoly, MailRu | Türkçe Altyazılı |
| 16 | **WebDramaTurkey** | Asya Dizileri | **IMPLEMENTED_WORKING** (Korundu) | `webdramaturkey.com` | GDrive, Dood, Vidmoly | Türkçe Altyazılı |
| 17 | **KultFilmler** | Film, Dizi | **IMPLEMENTED_WORKING** (Korundu) | `kultfilmler.top` | Vidmoly, Supervideo | Türkçe Altyazılı & Dublaj |
| 18 | **SinemaCX** | Film | **IMPLEMENTED_WORKING** (Korundu) | `sinemacx.net` | Vidmoly, Streamtape, HLS | Türkçe Dublaj & Altyazılı |
| 19 | **JetFilmIzle** | Film | **IMPLEMENTED_WORKING** (Korundu) | `jetfilmizle.mobi` | Faststream, Vidmoly | Türkçe Dublaj & Altyazılı |
| 20 | **RareFilmm** | Nadir Dünya Sineması | **IMPLEMENTED_WORKING** (Korundu) | `rarefilmm.com` | VK, OK.ru, Archive.org | Orijinal / Altyazılı |
| 21 | **BlenderOpenMovies**| Açık Kaynak Film | **IMPLEMENTED_WORKING** (Korundu) | `video.blender.org` | PeerTube doğrudan WebTorrent/MP4 | İngilizce / Çoklu Dil |
| 22 | **DiziIzle** | Dizi | **DEGRADED** (Kısmi Oynatma) | `diziizle.net` | Bazı bölümlerde sunucu kaynaklı 404 | Türkçe Dublaj & Altyazılı |
| 23 | **AnimeciX** | Anime | **DEGRADED** (Cloudflare Engeli) | `animecix.tv` | TauVideo SPA player Cloudflare engeli | Türkçe Altyazılı |
| 24 | **Animeler** | Anime, Anime Film | **DEGRADED** (Depolama 404) | `animeler.pw` | Makisekurisu CDN sunucuları (`steinsga.site` vb.) 404 | Türkçe Altyazılı |
| 25 | **FourKHDHub** | 4K Film | **BLOCKED_WITH_EVIDENCE** (Bot-Wall) | `4khdhub.one` | `greenmotors.club` JS ad/bot yönlendirmesi | İngilizce & Çoklu Dil |
| 26 | **SezonlukDizi** | Dizi | **BLOCKED_WITH_EVIDENCE** (Turnstile) | `sezonlukdizi.vip` | Cloudflare Turnstile CAPTCHA koruması | Türkçe Dublaj & Altyazılı |

---

## 2. DEVRE DIŞI BIRAKILAN / DESTEKLENMEYEN 9 MODÜL (`disabled`)

Repository politikası ve CloudStream kuralları uyarınca tamamen kapanmış veya aşırı bot engeli bulunan şu 9 modül `settings.gradle.kts` ve `config/providers.json` içinde devre dışı tutulmaktadır:
1. `CizgiMax` (`cizgimax.online` - Cloudflare Turnstile engeli)
2. `DDizi` (Kapanmış / alan adı yanıt vermiyor)
3. `DiziLife` (Kapanmış / içerik kaldırılmış)
4. `DiziMom` (Kapanmış)
5. `DiziPal` (Haftalık alan adı değişikliği ve Cloudflare engel duvarı)
6. `FilmHane` (Kapanmış)
7. `FilmModu` (Kapanmış / telif erişim engeli)
8. `Sinewix` (Özel Android native kütüphane / API gereksinimi)
9. `TurkAnime` (Cloudflare Turnstile + Google reCAPTCHA)

---

## 3. SÜPER HUB 16 BİRLEŞİK KATEGORİSİNİN GÜNCEL KAPSAMA DURUMU

| Birleşik Kategori Adı | Kapsayan Aktif Sağlayıcılar | Kaynak Zenginliği |
| :--- | :--- | :--- |
| **Tüm İçerikler** | Tüm aktif 26 sağlayıcı | Tam Kapsama |
| **Filmler** | FilmMakinesi, HDFilmCehennemi, FullHDFilmizlesene, SetFilmIzle, HDFilmDelisi, KultFilmler, SinemaCX, JetFilmIzle, YTS | Yüksek |
| **Diziler** | Dizilla, Dizigecesi, DiziYou, SetFilmIzle, HDFilmCehennemi, FilmMakinesi, DiziIzle | Yüksek |
| **Türk Dizileri** | StarTV, Dizigecesi, DiziYou, SetFilmIzle | Yüksek |
| **Eski / Nostaljik Türk Dizileri** | StarTV, YesilCamTv | Orta / Yüksek |
| **Çizgi Filmler** | FilmMakinesi, HDFilmCehennemi, CloudStreamHub | Orta |
| **Çizgi Diziler** | CloudStreamHub, DiziYou, Dizilla | Orta |
| **Animasyon Filmleri** | FilmMakinesi (Genişletildi), HDFilmCehennemi (Genişletildi), SetFilmIzle | Yüksek |
| **Anime** | AnimeciX (Kısmi), Animeler (Kısmi), CloudStreamHub (TMDB Federasyonu) | Orta |
| **Belgeseller** | BelgeselX, RareFilmm | Yüksek |
| **Yeşilçam** | YesilCamTv, SinemaCX | Yüksek |
| **Türkçe Dublaj** | HDFilmDelisi, SetFilmIzle, FullHDFilmizlesene, Dizilla, HDFilmCehennemi, FilmMakinesi | Çok Yüksek (Doğrulanmış TR Ses) |
| **Türkçe Altyazı** | Dizigecesi, DiziYou, KultFilmler, FilmMakinesi, HDFilmCehennemi, SinemaCX | Çok Yüksek |
| **Yeni Eklenenler** | FilmMakinesi, HDFilmCehennemi, SetFilmIzle, Dizigecesi, DiziYou | Yüksek |
| **Popüler İçerikler** | Dizilla, HDFilmDelisi, FilmMakinesi, HDFilmCehennemi | Yüksek |
| **İzlemeye Devam Et** | Host CloudStream Uygulama Katmanı | Uygulama Desteğiyle Tam |
