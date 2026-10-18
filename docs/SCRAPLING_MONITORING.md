# Scrapling Monitoring & Research Altyapısı Rehberi

Bu belge, CloudStreamHub repository'sindeki Python tabanlı provider izleme, anti-bot analiz, selector drift (seçici kayması) tespiti ve güvenlik redaksiyon altyapısını (`tools/scraping`) belgeler.

---

## 1. Mimari Genel Bakış ve Temel Kurallar

### 1.1 Kesin Ayrım (Zero Android Runtime Impact)
- **Scrapling bir Android bağımlılığı DEĞİLDİR.**
- Scrapling, Chromium ve Playwright motorları **yalnızca** repository izleme (`tools/`), CLI araştırma (`tools/provider_probe.py`) ve GitHub Actions sağlık denetimi (`provider-health.yml`) iş akışlarında çalışır.
- Kotlin provider kaynak kodlarına (`src/main/kotlin`) ve Android derleme betiklerine kesinlikle Scrapling veya tarayıcı bağımlılığı eklenemez.
- `build.yml` iş akışı hafif tutulmuştur; Scrapling motoru sadece `provider-health.yml` üzerinde çalışır.

### 1.2 Semantik Ayrım (Player Discovery vs. Android Playback)
- **`PLAYER_DISCOVERED`**: Scrapling otomasyonunun hedef sayfada iframe, video container veya gömülü oynatıcı scripti keşfettiğini belirtir.
- **`PLAYBACK_PASS`**: YALNIZCA gerçek CloudStream Android istemcisinde video oynatımı ve altyazı stream'inin çalıştığı cihaz testlerinde raporlanabilir.
- Otomasyon araçları `PLAYER_DISCOVERED` raporlar; `runtimePlayback` durumunu `UNVERIFIED_BY_AUTOMATION` olarak işaretler.

---

## 2. Araçlar ve Kullanım

### 2.1 Provider Reverse Engineering & Probe Aracı (`provider_probe.py`)

Canlı web sitelerini tersine mühendislik ve anti-bot analizinden geçirmek için kullanılır:

```bash
# Standart HTTP (curl_cffi ile tarayıcı kimliği taklidi)
python tools/provider_probe.py https://kultfilmler.net/ --mode http

# Otomatik 3-Kademeli Strateji (HTTP -> DYNAMIC -> STEALTH)
python tools/provider_probe.py https://animecix.tv/ --mode auto

# Arka plan XHR isteklerini yakalama ve hassas verileri maskeleme
python tools/provider_probe.py https://kultfilmler.net/ --capture-xhr --output reports/probe_sample.json
```

Çıktı Alanları:
- **Status & Mode**: HTTP kodu, kullanılan mod (HTTP, DYNAMIC, STEALTH), harcanan süre.
- **Anti-Bot & Cloudflare**: WAF veya Cloudflare Turnstile/Challenge tespiti.
- **Content Discovery**: Ana sayfa kartları, detay başlığı, bölüm linkleri.
- **Stream Discovery**: İframe'ler, doğrudan `.m3u8` / `.mpd` / `.mp4` linkleri, altyazı kaynakları.
- **Captured XHRs**: Redaksiyondan geçmiş güvenli ağ trafiği.

### 2.2 3-Kademeli Getirme Motoru (`tools/scraping/fetch.py`)

1. **HTTP Kademesi**: `scrapling.Fetcher` ile en yüksek hızda `curl_cffi` üzerinden tarayıcı TLS parmak iziyle istek yapar.
2. **DYNAMIC Kademesi**: JavaScript hydration ve dinamik DOM rendering gerektiren durumlar için Scrapling `DynamicFetcher` (headless Playwright) kullanır.
3. **STEALTH Kademesi**: Cloudflare Turnstile, Managed Challenge veya gelişmiş WAF tespit edildiğinde anti-detect yetenekli `StealthyFetcher` devreye girer.

### 2.3 Seçici Kayması Tespiti (`AdaptiveManager`)

- İlk aramada selector **STRICT** (katı) olarak çalıştırılır. Eşleşme varsa SQLite tabanına parmak izi kaydedilir (`PASS`).
- Katı sorgu 0 sonuç verirse, Scrapling `adaptive=True` algoritması devreye girerek DOM mutasyonuna uğramış aday öğeleri arar.
- Aday öğe bulunursa durum `DRIFT_DETECTED` olarak işaretlenir.
- **Kural**: Adaptive engine Kotlin kodunu otomatik düzenlemez; GitHub Issue açılması veya güncellenmesi için veri sağlar.

### 2.4 Güvenlik ve Gizlilik Redaksiyonu (`XhrRedactor`)

Tüm yakalanan XHR istekleri, yanıtları ve loglar otomatik olarak maskelenir:
- **Hassas Başlıklar**: `Authorization`, `Cookie`, `Set-Cookie`, `x-auth-token`, `x-api-key`, `cf-access-token` -> `<REDACTED>`
- **Token & JWT**: Bearer token'lar, JWT dizgileri -> `<REDACTED_TOKEN>` / `<REDACTED_JWT>`
- **Medya URL'leri**: `.m3u8`, `.mpd`, `.ts` linklerindeki geçici imza/token parametreleri -> `<REDACTED_QUERY>`
- **Şifre ve Gizli Alanlar**: JSON gövdelerindeki parola/secret anahtarları maskelenir.

---

## 3. Sağlık Denetimi ve Katmanlar (L0 - L5)

| Katman | Tanım | Beklenen Durumlar |
| :--- | :--- | :--- |
| **L0** | Yapılandırma & Gradle Uyumu | `PASS`, `FAIL` |
| **L1** | Domain Erişilebilirliği & Yönlendirme Güvenliği | `PASS`, `CLOUDFLARE`, `UNTRUSTED_REDIRECT`, `FAIL` |
| **L2** | Ana Sayfa DOM Ayrıştırma & Seçici Kayması | `PASS`, `DRIFT_DETECTED`, `FAIL`, `AUTOMATION_BLOCKED` |
| **L3** | Arama (GET / POST / AJAX) | `PASS`, `FAIL`, `AUTOMATION_BLOCKED`, `SKIPPED` |
| **L4** | Detay Sayfası Yükleme | `PASS`, `DRIFT_DETECTED`, `FAIL`, `AUTOMATION_BLOCKED`, `SKIPPED` |
| **L5** | Player / İframe Keşfi | `PLAYER_DISCOVERED`, `PLAYER_NOT_FOUND`, `AUTOMATION_BLOCKED`, `SKIPPED` |

Çalıştırma:
```bash
python tools/provider_health.py
python tools/live_provider_smoke.py
python tools/report_health_summary.py
```
