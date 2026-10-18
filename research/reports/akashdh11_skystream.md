# akashdh11/skystream — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** bağımsız streaming uygulaması / CloudStream-inspired client  
**Bizim açımızdan önem seviyesi:** Kritik  
**Teknoloji stack:** Flutter/Dart, Riverpod, Hive, quick_js_ng, JS extensions

CloudStream-benzeri çapraz platform istemci; custom JavaScript extension engine.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [akashdh11/skystream](https://github.com/akashdh11/skystream)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** JS extension sandbox/runtime.
- **[DOĞRULANDI/README]** plugin-wise search.
- **[DOĞRULANDI/README]** domain switching.
- **[DOĞRULANDI/README]** in-app plugin logs.
- **[DOĞRULANDI/README]** DoH.
- **[DOĞRULANDI/README]** multi-tracker sync.
- **[DOĞRULANDI/README]** cross-platform single codebase.

**[ÇIKARIM]** Mimari sonuç: Provider kapsamı ve resolver orkestrasyonu doğrudan karşılaştırılabilir.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| extension-provided sources | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| TMDB | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Trakt | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Simkl | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| MAL | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| AniList | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| OpenSubtitles | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| SubDL | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Subsource | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: JS extension sandbox/runtime, plugin-wise search, domain switching, in-app plugin logs, DoH, multi-tracker sync, cross-platform single codebase.
- **Header/referer/cookie/token** davranışları yalnızca source ile doğrulandığı ölçüde güvenilir kabul edilmelidir.
- **[DOĞRULANAMADI]** Anti-bot, encrypted payload veya WebView kullanımına dair açık kanıt yoksa varmış gibi değerlendirilmedi.

## 5. Bizim Repo ile Karşılaştırma

| Konu | Bu Repo | Bizim Repo | Hangisi Daha İyi? | Neden? |
|---|---|---|---|---|
| architecture | repo-spesifik; aşağıdaki patternler | provider-per-module + merkezi config | Duruma göre | Kanıt kapsamına göre |
| maintainability | modülerlik derecesi repo tipine bağlı | iyi provider izolasyonu | Duruma göre | Kanıt kapsamına göre |
| provider architecture | çoklu/tek provider veya client adapter | 8 aktif provider | Duruma göre | Kanıt kapsamına göre |
| networking | repo stackine özgü | NiceHttp + provider headers | Yeterli veri yok | Kanıt kapsamına göre |
| caching | client repolarda daha görünür | merkezi cache doğrulanmadı | Yeterli veri yok | Kanıt kapsamına göre |
| observability | bazı repolarda in-app/source health | CI/health metadata | Duruma göre | Kanıt kapsamına göre |
| testability | source görünürlüğüne bağlı | provider test dirs + smoke metadata | Duruma göre | Kanıt kapsamına göre |

Temel farkın teknik sonucu: bu repo geniş coverage veya client-level abstraction sağlıyorsa Bizim Repo'nun güvenli domain/payload tasarımı korunarak yalnızca ilgili pattern incremental adapte edilmelidir. Büyük rewrite otomatik olarak önerilmez.

## 6. Bizde Olmayan Özellikler

| Özellik | Nasıl Çalışıyor? | Bizde Var mı? | Bize Faydası | Uygulama Zorluğu |
|---|---|---|---|---|
| JS extension sandbox/runtime | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| plugin-wise search | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| domain switching | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| in-app plugin logs | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |
| DoH | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |
| multi-tracker sync | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |

## 7. Daha Performanslı Yaklaşımlar

- Bizim uzun vadeli repo+client vizyonu için en güçlü mimari referanslardan biri.
- provider debug/observability için in-app log fikri.
- JS extension portability.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** JS extension sandbox/runtime.
- **[DOĞRULANDI/README]** plugin-wise search.
- **[DOĞRULANDI/README]** domain switching.
- **[DOĞRULANDI/README]** in-app plugin logs.
- **[DOĞRULANDI/README]** DoH.
- **[DOĞRULANDI/README]** multi-tracker sync.
- **[DOĞRULANDI/README]** cross-platform single codebase.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- Bizim uzun vadeli repo+client vizyonu için en güçlü mimari referanslardan biri.
- provider debug/observability için in-app log fikri.
- JS extension portability.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- Bizim Repo sadece plugin repository; istemciyi yeniden yazmak gereksiz olabilir. Patternleri incremental alınmalı.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P0 | Bizim uzun vadeli repo+client vizyonu için en güçlü mimari referanslardan biri | Teknik öğrenim/coverage | Orta | Orta | akashdh11/skystream |
| P1 | provider debug/observability için in-app log fikri | Teknik öğrenim/coverage | Orta | Orta | akashdh11/skystream |
| P2 | JS extension portability | Teknik öğrenim/coverage | Düşük-Orta | Orta | akashdh11/skystream |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [README.md](https://github.com/akashdh11/skystream/blob/master/README.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [docs/CONTRIBUTING.md](https://github.com/akashdh11/skystream/blob/master/docs/CONTRIBUTING.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [extension engine sources](https://github.com/akashdh11/skystream/blob/master/extension engine sources) | Repo için yüksek sinyalli dosya/dizin | P0 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. JS extension sandbox/runtime
2. plugin-wise search
3. Bizim uzun vadeli repo+client vizyonu için en güçlü mimari referanslardan biri

**Bizim için genel AR-GE değeri:** `9.6/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
