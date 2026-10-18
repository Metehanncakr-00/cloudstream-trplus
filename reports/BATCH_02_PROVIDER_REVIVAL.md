# CloudStreamHub — Batch 02 Provider Revival Raporu

**Tarih:** 16 Eylul 2026  
**Branch:** `revival/batch-02-core-providers`  
**Kapsam:** Batch 02 (6 Cekirdek Dizi ve Film Provider'i)  
**Hedef:** Tersine muhendislik, Scrapling tabanli canli arastirma, tam Kotlin eklenti implementasyonu, sifir gerileme (zero regressions), deterministik testler ve seffaf dogrulama.

---

## 1. Yonetici Ozeti (Executive Summary)

Batch 01 ile stabilize edilen 8 aktif provider'a (`AnimeciX`, `BelgeselX`, `DiziPal`, `FilmMakinesi`, `HDFilmCehennemi`, `KultFilmler`, `TurkAnime`, `YesilCamTv`) ek olarak, Batch 02 kapsaminda 6 aday saglayici (`SezonlukDizi`, `SinemaCX`, `DiziMom`, `DiziYou`, `Dizilla`, `DiziBox`) canli upstream analizi ve tersine muhendislikten gecirilmistir.

Sonuc olarak:
- **5 Saglayici Basariyla Canlandirildi ve Dogrulandi:** `SezonlukDizi`, `SinemaCX`, `DiziMom`, `DiziYou`, `Dizilla`.
- **1 Saglayici Guvenlik/Firewall Nedeniyle Bloke Edildi:** `DiziBox` (Cloudflare 1000s box ASN kurali ile anonim erisimi engellemekte; harici kodlardaki kisisel `LockUser`/`isTrustedUser` cerezleri kural 14 uyarinca kesin olarak reddedilmistir).
- **Toplam Aktif Saglayici Sayisi:** **13**'e yukseltilmistir.
- **Test ve Derleme:** 13 modulun tamami `./gradlew test` (390 actionable task) ve `./gradlew make makePluginsJson` ile sifir hata ile derlenmis ve paketlenmistir.
- **Repository Dogrulamasi:** `python tools/validate_repo.py --verify-artifacts` 13 modul icin 0 hata ile gecmistir.
- **Altyapi Saglik Denetimi:** `python tools/provider_health.py` 13 saglayicinin tamaminda L0 (Config) ve L1 (Domain) asamalarini eksiksiz PASS ile tamamlamistir.

---

## 2. Canlandirilan Provider'lar ve Tersine Muhendislik Analizi

### 1. SezonlukDizi (`com.cloudstream.tr.sezonlukdizi`)
- **Modul Adi:** `SezonlukDizi` | **Surum:** `1` | **Status:** `1` | **Tur:** `TvSeries`
- **Canonical Domain:** `https://sezonlukdizi.cc`
- **Arama Motoru:** `POST https://sezonlukdizi.cc/ajax/arama.asp` (Payload: `q={query}`, Header: `X-Requested-With: XMLHttpRequest`). Dogrudan JSON formatinda dizi basligi, kapak resmi ve slug degerleri doner.
- **Detay & Bolumler:** Dizi sayfalarindaki `/diziler/{slug}.html` yapisi `/bolumler/{slug}.html` tablosuna cozulerek tum sezon ve bolumler `/dizi-adi/{s}-sezon-{e}-bolum.html` formatinda cekilir.
- **Link & Player Cozumleme:** Bolum sayfasindaki `div#dilsec[data-id]` parametresi okunur. `POST /ajax/dataAlternatif22.asp` ile dil secenekleri (1: Altyazili, 0: Dublaj) ve kaynak ID'leri alinir. `POST /ajax/dataEmbed22.asp` ile iframe elde edilir (Vidmoly, Filemoon, Sibnet vb. CloudStream yerlesik extractor'lari ile oynatilir).
- **Birim Testleri:** `SezonlukDiziParserTest` (DOM ayristirma ve arama karti testi).
- **Uretilen Artifact:** `build/SezonlukDizi.cs3` (22.8 KB).

---

### 2. SinemaCX (`com.cloudstream.tr.sinemacx`)
- **Modul Adi:** `SinemaCX` | **Surum:** `1` | **Status:** `1` | **Tur:** `Movie`
- **Canonical Domain:** `https://sinemacc.com` (canlida `https://www.sinema.gg` 301 yonlendirmesi ile `sinemacc.com` adresine tasinmistir; her iki host da allowlist'e eklenmistir).
- **Arama & Sayfalama:** `GET /?s={query}` ve `GET /page/{page}/` ile film listeleri.
- **Detay & Fragman Fallback:** Yalnizca fragman iceren ilk sayfalar tespit edilerek part 2 (`/2/`) adresindeki asil film sayfasina otomatik gecis saglanir.
- **Link & Player Cozumleme:** Iframe icerisindeki `player.filmizle.in/video/{ID}` ayristirilir. `POST https://player.filmizle.in/player/index.php?data={ID}&do=getVideo` API'sine `hash` ve `r` parametreleri gonderilerek dogrudan yetkili HLS `master.m3u8` manifest URL'si elde edilir (canli HTTP 200 dogrulanmistir).
- **Birim Testleri:** `SinemaCXParserTest` (Film karti ayristirma ve metadata testi).
- **Uretilen Artifact:** `build/SinemaCX.cs3` (21.5 KB).

---

### 3. DiziMom (`com.cloudstream.tr.dizimom`)
- **Modul Adi:** `DiziMom` | **Surum:** `1` | **Status:** `1` | **Tur:** `TvSeries`
- **Canonical Domain:** `https://www.dizimom.diy`
- **Guvenlik Denetimi (Kural 13 Tam Dogrulandi):** Harici depolarda tespit edilen sabit `wp-login.php` kullanici adi (`keyiflerolsun`) ve parolasi (`12345`) kesin olarak reddedilmistir. Canli sistemin oturum acmadan, **%100 anonim** ziyaretcilere tum bolum ve video iceriklerini acikca sundugu kanitlanmis ve eklenti sifir kimlik bilgisiyle uygulanmistir.
- **Arama & Detay:** `GET /?s={query}` ve `/diziler/{slug}-izle/` sayfalari uzerinden tum sezon/bolumler ayristirilir.
- **Link & Player Cozumleme:** `https://peacemakerst.com/tv/video/{ID}` iframe adresi ayristirilarak `POST https://peacemakerst.com/tv/video/{ID}?do=getVideo` cagrisi yapilir. Donen dogrudan GoogleVideo MP4 ve HLS akislari player'a iletilir.
- **Birim Testleri:** `DiziMomParserTest` (Post ayristirma ve dizi arama testi).
- **Uretilen Artifact:** `build/DiziMom.cs3` (22.7 KB).

---

### 4. DiziYou (`com.cloudstream.tr.diziyou`)
- **Modul Adi:** `DiziYou` | **Surum:** `1` | **Status:** `1` | **Tur:** `TvSeries`
- **Canonical Domain:** `https://www.diziyou.one` (3. parti kopya `diziyou.net` elenmis, gercek altyapiya sahip `diziyou.one` secilmistir).
- **Arama & Detay:** `GET /?s={query}` ile arama, dizi sayfasindan tum bolumlerin tespiti.
- **Link & Player Cozumleme:** Bolum sayfasindaki `iframe#diziyouPlayer` ve `itemId` okunur. `https://storage.diziyou.one/episodes/{itemId}/play.m3u8` yetkili HLS akisi, varsa Turkce dublaj (`{itemId}_tr/play.m3u8`) ve cift altyazi dosyalari (`tr.vtt`, `en.vtt`) dogrudan oynaticiya sunulur.
- **Birim Testleri:** `DiziYouParserTest` (Kategori ve dizi karti ayristirma testi).
- **Uretilen Artifact:** `build/DiziYou.cs3` (22.2 KB).

---

### 5. Dizilla (`com.cloudstream.tr.dizilla`)
- **Modul Adi:** `Dizilla` | **Surum:** `1` | **Status:** `1` | **Tur:** `TvSeries`
- **Canonical Domain:** `https://dizilla.now`
- **Tersine Muhendislik & Acik Anahtar Dogrulamasi (Kural 15):** Harici kodda gorulen `9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI` anahtari koru korune kopyalanmamis; Dizilla'nin `_app-62e92aeedbdc35dc.js` Webpack bundle'i (Module 379) decompile edilerek anahtarin istemci tarafinda `crypto.createHash("sha256").update("!!22xx!!90!!").digest("base64").substring(0, 32)` formuluyle ve 16 byte 0 IV ile turetildigi matematiksel olarak kanitlanmistir.
- **Metadata & Arama:** Sitedeki `__NEXT_DATA__` icerisindeki `secureData` blogu yerel Java `Cipher` (AES-256-CBC) ile cozulerek anasayfa, trend diziler ve bolum listeleri eksiksiz okunur. Arama `POST /api/bg/searchContent?searchterm={query}` ile gerceklestirilir.
- **Link & Player Cozumleme:** Cozulen bolum verisindeki `RelatedResults.getEpisodeSources` listesinden `pichive.online` iframe'i (`four.pichive.online/iframe.php?v=...`) yakalanir. Sayfadaki `openPlayer` cagrisindan alinan token ile `source2.php?v=` API'sinden yetkili `master.m3u8` HLS akisi ve VTT altyazilari cekilir (canli HTTP 200 ve Turkce ses kanali dogrulanmistir).
- **Birim Testleri:** `DizillaParserTest` (Acik AES sifre cozme dogrulamasi ve metadata yukleme testi).
- **Uretilen Artifact:** `build/Dizilla.cs3` (23.9 KB).

---

### 6. DiziBox — Guvenlik Nedeniyle Bloke Edildi (`BLOCKED_CLOUDFLARE_FIREWALL`)
- **Domain:** `https://dizibox.live`
- **Durum:** `blocked` / `unsupported`
- **Tespit ve Karar:** `dizibox.live` upstream Cloudflare guvenlik duvari tarafindan HTTP 403 `::CLOUDFLARE_ERROR_1000S_BOX::` ("VPN veya Server ip adresi ile giris yapmayiniz... ASN kontrolu") hatasi vermektedir. Harici depolarda bu engelin gelistiricinin kisisel tarayicisindan kopyalanan `LockUser: true`, `isTrustedUser: true`, `dbxu: 1744054959089` cerezleriyle asildigi gorulmustur. Bu durum Kural 14 ("Kisisel yetkili cerez kullanim yasagi") kapsamina girdiginden DiziBox eklentisi repoya eklenmemis ve engellenmis olarak belgelenmistir.

---

## 3. GPL-3.0 Lisans ve Atif (Provenance & License Compliance)

Bu calismada tersine muhendislik arastirmasi yapilan kamuya acik referans repolar:
- `Kraptor123/cs-kraptor` (GPL-3.0)
- `keyiflerolsun/Kekik-CloudStream` (GPL-3.0)

Tum kodlar CloudStreamTR standartlarina gore modern `BasePlugin` ve `@CloudstreamPlugin` sozdizimi ile yeniden yazilmis, guvenlik aciklari ve sabit sifreler temizlenmis ve projenin GPL-3.0 lisansina uygun olarak arsivlenmistir.

---

## 4. Test ve Dogrulama Matrisi

| Saglayici | Surum | TvTypes | Derleme / Test | plugins.json | L1 Domain | L3 Search | Canli Oynatma Yontemi | Durum |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- | :---: |
| **AnimeciX** | 90 | Anime | PASS | PASS | PASS | PASS | TauVideo Extractor (HLS/MP4) | Aktif |
| **BelgeselX** | 90 | Documentary | PASS | PASS | PASS | PASS | Ok.ru / DiziGetir | Aktif |
| **DiziPal** | 90 | Movie, TvSeries | PASS | PASS | PASS | PASS | Videoplay HLS | Aktif |
| **FilmMakinesi** | 90 | Movie, TvSeries | PASS | PASS | PASS | PASS | CloseLoad / Vidpapi HLS | Aktif |
| **HDFilmCehennemi** | 90 | Movie, TvSeries | PASS | PASS | PASS | PASS | Rapidrame HLS | Aktif |
| **KultFilmler** | 32 | Movie, TvSeries | PASS | PASS | PASS | PASS | Vidpapi HLS | Aktif |
| **TurkAnime** | 95 | Anime | PASS | PASS | PASS | PASS | AES-128-CBC Ajax Extractor | Aktif |
| **YesilCamTv** | 10 | Movie | PASS | PASS | PASS | PASS | Dzen / Mail.ru / HLS | Aktif |
| **SezonlukDizi** | **1** | TvSeries | **PASS** | **PASS** | **PASS** | **PASS** | ASP Alternatif -> VidMoly/Filemoon | **Canlandirildi** |
| **SinemaCX** | **1** | Movie | **PASS** | **PASS** | **PASS** | **PASS** | Filmizle secured HLS master.m3u8 | **Canlandirildi** |
| **DiziMom** | **1** | TvSeries | **PASS** | **PASS** | **PASS** | **PASS** | Anonim Peacemakerst HLS/MP4 | **Canlandirildi** |
| **DiziYou** | **1** | TvSeries | **PASS** | **PASS** | **PASS** | **PASS** | Storage.diziyou.one master.m3u8 | **Canlandirildi** |
| **Dizilla** | **1** | TvSeries | **PASS** | **PASS** | **PASS** | **PASS** | AES Deobfuscation -> Pichive HLS | **Canlandirildi** |
| **DiziBox** | — | TvSeries | — | — | BLOCKED | — | Cloudflare ASN Korumasi (Blocked) | **Devre Disi** |

---

## 5. Dogrulama Komutlari ve Sonuclari

1. `./gradlew test --daemon`: **390 actionable task, BUILD SUCCESSFUL**.
2. `./gradlew make makePluginsJson --daemon`: **131 actionable task, BUILD SUCCESSFUL**, `build/plugins.json` uretildi.
3. `python tools/validate_repo.py --verify-artifacts`: **13 provider dogrulandi, 0 hata**.
4. `python -m pytest tools/tests`: **30 test passed**.
5. `python tools/provider_health.py`: **13 provider L0 ve L1 PASS, 0 critical, 0 failed**.
6. `python tools/live_provider_smoke.py`: **13 provider tamamlandi**.
