# Sorun Giderme Rehberi (Troubleshooting)

## 1. CloudStream Eklenti Yüklenemiyor ("Failed to download plugin")
- **Neden**: `builds` branch'indeki `.cs3` dosyası indirilemiyor veya SHA-256 hash'i `plugins.json` içindekiyle uyuşmuyor.
- **Çözüm**: `python tools/validate_repo.py --verify-artifacts` çalıştırarak hash eşleşmesini doğrulayın.

## 2. Repo Eklenirken "Invalid Repository" Hatası
- **Neden**: `repo.json` dosyasındaki `pluginLists` URL'si raw github formatında değil veya `manifestVersion` 1 değil.
- **Çözüm**: `repo.json` dosyasındaki URL'nin doğrudan `raw.githubusercontent.com/.../builds/plugins.json` adresine işaret ettiğini kontrol edin.

## 3. Ana Sayfa Boş Geliyor (No items on home page)
- **Neden**: Hedef sitenin HTML yapısı veya CSS class isimleri değişmiş olabilir.
- **Çözüm**: Sitenin güncel DOM yapısını tarayıcıda inceleyin. Parser fonksiyonunu güncelleyip unit test ekleyin.

## 4. Oynatmaya Basıldığında "No links found"
- **Neden**: Embed iframe adresi değişmiş, video sağlayıcı yeni bir koruma eklemiş veya extractor güncelliğini yitirmiş olabilir.
- **Çözüm**: `loadLinks()` içindeki iframe çekme mantığını ve CloudStream'in upstream extractor listesini kontrol edin.

## 5. ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3003) Hatası
- **Yanlış Teşhis**: 3003 hatası doğrudan "cihaz codec'i desteklemiyor" veya "cihaz yetersiz" anlamına GELMEZ.
- **Gerçek Kök Nedenler**:
  1. **Player'a HTML / JSON Gitmesi**: CDN veya bot koruması (Cloudflare challenge, 403 Forbidden, `security error`) HTTP 200 ile HTML sayfası döner. Media3 oynatıcısı bu metni video container'ı olarak parse etmeye çalışıp 3003 fırlatır.
  2. **HLS Akışının Progressive (VIDEO) Olarak İşaretlenmesi**: URL sonunda `.m3u8` bulunmayan (token veya query parametreli, ya da `.txt` endpoint) akışlar `url.contains(".m3u8")` kontrolü başarısız olunca `VIDEO` yapılır. Progressive `Mp4Extractor` `#EXTM3U` metnini parse edemez ve 3003 fırlatır.
  3. **Eksik Referer / Origin Header**: Hedef CDN isteği reddeder ve hata sayfası döner.
- **Çözüm**:
  - `StreamValidator.validateStream()` kullanarak linki player'a vermeden önce ön kontrolden geçirin.
  - HLS manifestinin ilk byte'larında `#EXTM3U` bulunduğunu doğrulayıp `ExtractorLinkType.M3U8` olarak işaretleyin.
  - Gerekli `Referer` başlığını `ExtractorLink.headers` içine eksiksiz ekleyin.

## 6. Media3 / ExoPlayer Hata Kodları Referansı
| Hata Kodu | Media3 Tanımı | Olası Neden |
|---|---|---|
| **2001** | `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` | DNS çözülemedi veya bağlantı koptu |
| **2002** | `ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT` | Sunucu zaman aşımına uğradı |
| **2003** | `ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE` | Beklenmeyen Content-Type |
| **2004** | `ERROR_CODE_IO_BAD_HTTP_STATUS` | HTTP 403 / 404 / 410 / 5xx |
| **3001** | `ERROR_CODE_PARSING_CONTAINER_MALFORMED` | Bozuk MP4/MKV container başlığı |
| **3002** | `ERROR_CODE_PARSING_MANIFEST_MALFORMED` | HLS `#EXTM3U` veya DASH MPD manifesti bozuk |
| **3003** | `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` | HTML/hata sayfasının medya sanılması veya HLS/VIDEO tip uyuşmazlığı |
| **4001** | `ERROR_CODE_DECODER_INIT_FAILED` | Donanımsal/yazılımsal decoder başlatılamadı |
| **4005** | `ERROR_CODE_DECODING_FORMAT_UNSUPPORTED` | Cihaz belirtilen video codec'ini (örn. AV1 / HEVC 10-bit) desteklemiyor |
