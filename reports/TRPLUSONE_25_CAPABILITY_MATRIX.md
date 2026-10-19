# TR+ ONE — 25 Sağlayıcı Envanteri ve Kabiliyet Matrisi

Bu rapor, `builds/plugins.json` ve güncel `main` dalı esas alınarak, **CloudStreamHub HARİÇ 25 bağımsız sağlayıcının** gerçek envanter, mimari, katalog, arama, bölüm, oynatma ve dil kabiliyetlerini belgeler.

---

## 1. Dağıtım Envanteri ve Durum Ayrımı

Canlı dağıtım (`origin/builds:plugins.json`) toplam **27 eklenti** içermektedir:
- **25 Bağımsız İçerik Sağlayıcısı**
- **1 Bağımsız Birleşik Eklenti:** `TRPlusOne` (v3)
- **1 Eski Agregator Eklentisi:** `CloudStreamHub` (v45 - TR+ ONE bundan tamamen bağımsızdır)

### `config/providers.json` ile `builds/plugins.json` Arasındaki Farklar

`config/providers.json` dosyasında toplam 38 kayıt bulunmaktadır. Bunlardan **11 tanesi** kalıcı olarak devre dışı bırakılmış (`enabled: false`) olup `builds/plugins.json` dağıtımında **yer almamaktadır**:
- `DiziPal` (Domain zaman aşımı / kapalı)
- `TurkAnime` (Kalıcı kapanma duyurusu - 2010-2026)
- `TrtDizi` (Devre dışı / kaldırıldı)
- `DiziMom` (SSL sertifika uyuşmazlığı)
- `DiziBox` (Devre dışı / kaldırıldı)
- `FilmModu` (Cloudflare Error 1010 WAF)
- `CizgiMax` (HTTP 404)
- `DDizi` (Arama ve bölüm sayfaları 404)
- `Sinewix` (DNS çözümleme hatası)
- `DiziLife` (SSL sertifika uyuşmazlığı)
- `FilmHane` (Domain bağlantı zaman aşımı)

---

## 2. 25 Sağlayıcı Sınıflandırma ve Doğrulama Matrisi

Sağlayıcı durumları kurallara uygun olarak 4 seviyede sınıflandırılmıştır:
- **`ACTIVE`**: Gerçek donanım üzerinde oynatması kanıtlanmış ve çalışan kaynaklar.
- **`DEGRADED`**: Katalog/detay çalışan, ancak bölüm yönlendirmesi veya extractor kısıtları bulunan kaynaklar.
- **`NOT_TESTED`**: Modül kodu ve derlemesi tam olan, ancak fiziksel Android 12 TV Box üzerinde oynatma kanıtı henüz bulunmayan kaynaklar.
- **`UNAVAILABLE`**: Sağlayıcı tarafında soft 404, WAF engeli veya çözülemeyen tescilli oynatıcı bulunan kaynaklar.

| # | Sağlayıcı / ID | Türler | Güncel Kanonik Domain | Ana Sayfa / Arama | Türkçe Dublaj | Türkçe Altyazı | Extractor / Çözücü | Gerçek Durum |
| :-: | :--- | :--- | :--- | :-: | :-: | :-: | :--- | :---: |
| 1 | **HDFilmCehennemi** (`HDFilmCehennemi`) | Film, Dizi | `https://www.hdfilmcehennemi.nl` | Var / Var | Var (Meta) | Var (Meta) | RapidVid, VidMoly | **ACTIVE** (TV Box Oynatma Kanıtlandı) |
| 2 | **BlenderOpenMovies** (`BlenderOpenMovies`) | Film | `https://video.blender.org` | Var / Var | Yok (İng/Sessiz) | Var | Doğrudan MP4 / WebM | **ACTIVE** (Açık Kaynak Akışlar) |
| 3 | **SezonlukDizi** (`SezonlukDizi`) | Dizi, Çizgi Dizi | `https://sezonlukdizi.cc` | Var / Var | Var (Meta) | Var (Meta) | Byse, Filemoon | **DEGRADED** (v3'te URL onarıldı, TV Oynatma: NOT_TESTED) |
| 4 | **AnimeciX** (`AnimeciX`) | Anime, Film | `https://animecix.tv` | Var / Var | Yok | Var (Meta) | MakiseKurisu, FirePlayer | **DEGRADED** (TauVideo/CF engeli, TV Oynatma: NOT_TESTED) |
| 5 | **FilmMakinesi** (`FilmMakinesi`) | Film | `https://filmmakinesi.to` | Var / Var | Var (Meta) | Var (Meta) | CloseLoad, VidMoly | **NOT_TESTED** (Grup 1 - TV Oynatma Bekliyor) |
| 6 | **SinemaCX** (`SinemaCX`) | Film | `https://sinemacc.com` | Var / Var | Var (Meta) | Var (Meta) | TauVideo, Filemoon | **NOT_TESTED** (Grup 1 - TV Oynatma Bekliyor) |
| 7 | **FullHDFilmizlesene** (`FullHDFilmizlesene`) | Film | `https://www.fullhdfilmizlesene.now` | Var / Var | Var (Meta) | Var (Meta) | RapidVid, VidMoly | **NOT_TESTED** (Grup 1 - TV Oynatma Bekliyor) |
| 8 | **Dizilla** (`Dizilla`) | Dizi | `https://dizilla.now` | Var / Var | Yok | Var (Meta) | Byse, Filemoon, Pichive | **NOT_TESTED** (Grup 1 - TV Oynatma Bekliyor) |
| 9 | **DiziYou** (`DiziYou`) | Dizi, Asya | `https://www.diziyou.one` | Var / Var | Yok | Var (Meta) | VidMoly, Filemoon | **NOT_TESTED** (Grup 1 - TV Oynatma Bekliyor) |
| 10 | **Animeler** (`Animeler`) | Anime | `https://animeler.pw` | Var / Var | Yok | Var (Meta) | TauVideo, RapidVid | **NOT_TESTED** (Grup 1 - TV Oynatma Bekliyor) |
| 11 | **StarTV** (`StarTV`) | Dizi | `https://www.startv.com.tr` | Var / Var | Orijinal TR | Yok | Doğrudan M3U8 | **NOT_TESTED** |
| 12 | **DiziIzle** (`DiziIzle`) | Dizi | `https://www.diziizle.you` | Var / Var | Orijinal TR | Yok | Dahili / Yerel | **NOT_TESTED** |
| 13 | **FourKHDHub** (`FourKHDHub`) | 4K Film/Dizi | API / P2P | Var / Var | Yok | Var | Torrentio / Debrid | **NOT_TESTED** |
| 14 | **YTS** (`YTS`) | Film | `https://yts.gg` | Var / Var | Yok (İng) | Harici Altyazı | Dahili Torrent | **NOT_TESTED** |
| 15 | **YesilCamTv** (`YesilCamTv`) | Klasik Film | `https://yesilcamtv.com.tr` | Var / Var | Orijinal TR | Yok | Rumble | **NOT_TESTED** |
| 16 | **KultFilmler** (`KultFilmler`) | Klasik Film | `https://kultfilmler.net` | Var / Var | Belirtilmemiş | Var (Meta) | Dahili / TauVideo | **NOT_TESTED** |
| 17 | **HDFilmDelisi** (`HDFilmDelisi`) | Film | `https://hdfilmdelisi.one` | Var / Var | Var (Meta) | Var (Meta) | VidMoly | **NOT_TESTED** |
| 18 | **DiziKorea** (`DiziKorea`) | Kore Dizisi | `https://dizikorea3.com` | Var / Var | Yok | Var (Meta) | VidMoly, Filemoon | **NOT_TESTED** |
| 19 | **BelgeselX** (`BelgeselX`) | Belgesel | `https://belgeselx.com` | Kısmi | Yok | Yok | - | **UNAVAILABLE** (Katalog soft 404) |
| 20 | **Dizigecesi** (`Dizigecesi`) | Dizi | `https://dizigecesi.com` | Kısmi | Yok | Yok | - | **UNAVAILABLE** (Bölüm sayfaları soft 404) |
| 21 | **DramaDizilerim** (`DramaDizilerim`) | Asya Dizisi | `https://dramadizilerim.com` | Kısmi | Yok | Yok | - | **UNAVAILABLE** (DramaPlaybackGuard JS engeli) |
| 22 | **WebDramaTurkey** (`WebDramaTurkey`) | Asya Dizisi | `https://webdramaturkey2.com` | Kısmi | Yok | Yok | - | **UNAVAILABLE** (dtpasn.asia obfuscated player) |
| 23 | **RareFilmm** (`RareFilmm`) | Sanat Filmi | `https://rarefilmm.com` | Kısmi | Yok | Yok | OK.ru (SSL Hata) | **UNAVAILABLE** (Russian CA SSL uyuşmazlığı) |
| 24 | **SetFilmIzle** (`SetFilmIzle`) | Film | `https://www.setfilmizle.ltd` | Kısmi | Yok | Yok | - | **UNAVAILABLE** (Arama 404 ve player desteği yok) |
| 25 | **JetFilmIzle** (`JetFilmIzle`) | Film | `https://jetfilmizle.now` | Kısmi | Yok | Yok | - | **UNAVAILABLE** (Site DOM değişti, player tanımsız) |

---

## 3. Dizi ve Anime Bölüm Oynatma Analizi

### A. SezonlukDizi İncelemesi
* **Fiziksel TV Box Hatası:** *Regular Show: The Lost Tapes* dizisinde bölüm seçildiğinde `LOAD_LINKS_RESULT yielded=0 links totalMs=987` hatası kaydedildi.
* **Kök Neden:**
  1. `loadLinks()`'e yalnızca soyut rota (`https://trplus.one.invalid/episode/{id}/1/1`) teslim edildiğinden, eklenti tüm dizi ana sayfasını yeniden indirmeye çalışıyordu.
  2. Ayrıca SezonlukDizi sunucularında bazı player'lar (`Pixel`, `Streamruby`, `Vidmoly`) doğrudan bot korumasına (`reCAPTCHADATA.asp`) takılırken, `Filemoon` (`bysejikuar.com`) embed linkleri doğrudan çözülebilmektedir.
* **v3 Çözümü:** `OneEpisodeRegistry` ile `load()` anında toplanan doğrudan bölüm adresi (`https://sezonlukdizi.cc/regular-show-the-lost-tapes/1-sezon-1-bolum.html`) saklanır ve `loadLinks()` anında doğrudan taranır.

### B. AnimeciX İncelemesi
* **Fiziksel TV Box Hatası:** Anime detayları çekilebilmesine rağmen bazı videolarda link bulunamadı.
* **Kök Neden:** AnimeciX video kaynakları MakiseKurisu API ve FirePlayer koruması altındadır. TauVideo iframe'leri Cloudflare bot koruması altındadır.
* **v3 Çözümü:** `TRPlusOnePlugin` içinde `TauVideo` ve ilgili extractor'lar kaydedilmiş, ayrıca animeler için kararlı birincil/ikincil kaynak olarak **`Animeler`** sağlayıcısı Grup 1'e dahil edilmiştir.

### C. Çoklu Aşama Tanılama (`OneDiagnostics`)
`0 links` sonucunun yalnızca rota eşleme hatası olmadığı, extractor başarısızlıklarının da loglanması gerektiği dikkate alınarak aşağıdaki aşama logları ayrıştırılmıştır:
- `EPISODE_DATA_FOUND` / `EPISODE_DATA_MISSING`: Bölüm hedef adresi bulunabildi mi?
- `PROVIDER_LINKS_START`: Sağlayıcının link çıkarma fonksiyonu çağrıldı mı?
- `PROVIDER_LINKS_RESULT`: Kaç link çıkarıldı, ne kadar sürdü?
- `EXTRACTOR_FAILED`: Extractor düzeyinde hata veya koruma engeli oluştu mu?

---

## 4. Grup 1 (9 Sağlayıcı) Entegrasyon Güvencesi

1. **Bağımsız Modül Kaynakları:** 9 sağlayıcının kaynak kodları `copyOneProviderSources` görevi ile doğrudan `TRPlusOne.cs3` paketinin kendi DEX'i içerisine derlenir.
2. **Hata İzolasyonu:** Bir sağlayıcı ağ hatası verir, zaman aşımına uğrar veya çökerse, `OneProviders` içindeki `try-catch` koruması sayesinde diğer 8 sağlayıcı etkilenmeden çalışmasını sürdürür.
3. **Eşzamanlılık Kısıtı (`Semaphore(4)`):** 9 sağlayıcının aynı anda ağ isteği yaparak Android 12 TV Box CPU/RAM kaynaklarını tüketmesi engellenmiştir.
4. **Dil Etiketleri Güvencesi:** Yalnızca sağlayıcının gerçek metadata veya link başlığında tespit edilen doğrulanmış durumlarda `[Türkçe Dublaj]` ve `[Türkçe Altyazı]` etiketleri eklenir; asla sahte bağlantı üretilmez.

> [!NOTE]
> **Fiziksel Android 12 TV Box Testi Beyanı:**  
> Grup 1 içerisindeki 9 sağlayıcının birim testleri (43/43 PASS) ve derlemesi yerel olarak tamamlanmıştır. Ancak `HDFilmCehennemi` dışındaki 8 sağlayıcının TR+ ONE üzerinden canlı oynatma başarısı henüz fiziksel TV kutusu üzerinde test edilmediğinden resmi olarak **`NOT_TESTED`** statüsündedir.
