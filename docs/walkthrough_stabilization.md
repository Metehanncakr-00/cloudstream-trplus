# CloudStreamHub Stabilization — Uçtan Uca Doğrulama ve İnceleme Raporu

## Yapılan Temel Değişiklikler

1. **Stream URL Doğrulama ve 3003 Önleme Katmanı (`StreamValidator.kt`):**
   - HLS `#EXTM3U`, MP4 `ftyp`/`moov`, MKV/WebM `EBML` magic byte doğrulaması eklendi.
   - HTML hata sayfaları (Cloudflare challenge, 403 Forbidden, `security error`, vb.) ve JSON hata gövdeleri player'a gitmeden ön kontrolle elendi.
   - Bounded timeout (maksimum 2.0s) ve byte-range GET (`Range: bytes=0-1024`) ile Android TV/düşük donanımlı cihazlarda takılma önlendi.

2. **Yapılandırılmış Gözlemlenebilirlik (`DiagnosticLogger.kt`):**
   - Aşama bazlı (DOMAIN, SEARCH, LOAD, EXTRACTOR, STREAM_PREFLIGHT, vb.) telemetri kuruldu.
   - Hatalar Media3 hata kodlarına (2001, 2004, 3001, 3002, 3003, 4001, 4005) map edildi.
   - Hassas veriler (API key, token, auth header) otomatik maskelendi ([REDACTED]).

3. **Özel Extractor'ların Güçlendirilmesi (Hardening):**
   - `RapidVidExtractor`, `PichiveExtractor`, `PeacemakerExtractor`, `FilmizleInExtractor`, `VidmolyTrExtractor`, `CloseLoadExtractor` ve `TauVideo` güncellendi.
   - Hatalı `INFER_TYPE` ve hardcoded `VIDEO`/`P1080` atamaları yerine dinamik `StreamValidator` çıkarımı uygulandı.
   - Sessizce yutulan `catch (_: Exception) {}` blokları `DiagnosticLogger` ile görünür kılındı.

4. **CloudStreamHub Federasyon ve Eşleştirme Reformu:**
   - `CloudStreamProviderRegistryAdapter`: `APIHolder` üzerindeki kayıtlı sağlayıcıları dinamik sorgular; hardcoded 7 sağlayıcı listesi ve riskli `Class.forName` araması kaldırıldı.
   - `HubMatchingEngine`: Başlık normalizasyonu, yıl toleransı ve benzerlik skoru ($\ge 0.80$) ile katı eşleştirme yapıldı. Eşleşme yoksa `searchList.first()` seçilme hatası engellendi.
   - Dizi bölümlerinde kesin sezon/bölüm eşleşmesi zorunlu kılındı; başka sezona fallback engellendi.

5. **Domain Drift Düzeltmeleri:**
   - `SinemaCX` canonical domain `https://sinemacc.com` ile eşitlendi, Base64 decode edilmiş iframe desteği eklendi.
   - `DDizi` canonical domain `https://www.ddizi.im` ile eşitlendi.
   - `JetFilmIzle` canonical domain `https://jetfilmizle.now` ile eşitlendi.

6. **Sağlık İzleme ve Playback Matrix (L0-L8):**
   - `tools/playback_verifier.py`: L6 Extractor Resolution, L7 Media Preflight, L8 First Segment doğrulama motoru eklendi.
   - `reports/playback_matrix.json`: 30 aktif sağlayıcı için anti-staleness metaverileriyle (commit SHA, config hash, provider count) machine-readable matrix üretildi.
   - `tools/provider_health.py`: İmkansız sağlık skoru kombinasyonları (failed iken score=100) engellendi ve invariant testleri yazıldı.

---

## Doğrulama ve Test Sonuçları

### 1. Kotlin & Android Unit Testleri
- `./gradlew :AnimeciX:test`:
  - `CoreTest`: StreamValidator byte çıkarımı, HTML/hata eleme, metadata çıkarımı, redaction ve link deduplication testleri: **BAŞARILI**
- `./gradlew :CloudStreamHub:test`:
  - `CloudStreamHubTest`: TMDB sayfa parse, HubMatchingEngine kesin eşleştirme, alakasız başlığı ilk sonuca fallback yapmama ve yıl toleransı testleri: **BAŞARILI**

### 2. Python Araçları ve Sağlık Testleri
- `pytest tools/tests/`:
  - `test_playback_verifier.py`: L6, L7 preflight, L8 segment ve matrix testleri (4 test): **BAŞARILI**
  - `test_health.py`: Invariant testleri, imkansız skor kontrolü (5 test): **BAŞARILI**
  - Tüm test suite (46 test): **BAŞARILI (46 passed)**

### 3. Repository Bütünlüğü
- `python tools/validate_repo.py`:
  - 30 aktif sağlayıcı, build.gradle, plugin manifest ve domain kuralları: **0 Hata (BAŞARILI)**
