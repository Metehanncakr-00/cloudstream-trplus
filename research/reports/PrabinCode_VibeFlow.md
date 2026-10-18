# PrabinCode/VibeFlow — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** müzik streaming uygulaması / SimpMusic fork  
**Bizim açımızdan önem seviyesi:** Yüksek  
**Teknoloji stack:** Kotlin Multiplatform/Compose, multi-module

SimpMusic tabanlı geniş müzik client; Android/Desktop, core modülü, cast/crashlytics varyantları, YouTube internal API.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [PrabinCode/VibeFlow](https://github.com/PrabinCode/VibeFlow)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** core/ modülü.
- **[DOĞRULANDI/README]** feature/service modülerliği.
- **[DOĞRULANDI/README]** FOSS telemetry ayrımı.
- **[DOĞRULANDI/README]** desktop/mobile adapters.
- **[DOĞRULANDI/README]** SponsorBlock/RYD integrations.

**[ÇIKARIM]** Mimari sonuç: Bu tam bir client olduğundan networking/cache/playback fikirleri alınabilir; CloudStream provider contractına birebir taşınmamalıdır.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| YouTube Music | Müzik/servis | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Spotify API | Müzik/servis | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| LRCLIB | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| SimpMusic Lyrics | Müzik/servis | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: core/ modülü, feature/service modülerliği, FOSS telemetry ayrımı, desktop/mobile adapters, SponsorBlock/RYD integrations.
- **Header/referer/cookie/token** davranışları yalnızca source ile doğrulandığı ölçüde güvenilir kabul edilmelidir.
- **[DOĞRULANAMADI]** Anti-bot, encrypted payload veya WebView kullanımına dair açık kanıt yoksa varmış gibi değerlendirilmedi.

## 5. Bizim Repo ile Karşılaştırma

| Konu | Bu Repo | Bizim Repo | Hangisi Daha İyi? | Neden? |
|---|---|---|---|---|
| architecture | repo-spesifik; aşağıdaki patternler | provider-per-module + merkezi config | Duruma göre | Kanıt kapsamına göre |
| maintainability | modülerlik derecesi repo tipine bağlı | iyi provider izolasyonu | Duruma göre | Kanıt kapsamına göre |
| provider architecture | çoklu/tek provider veya client adapter | 8 aktif provider | Duruma göre | Kanıt kapsamına göre |
| networking | repo stackine özgü | NiceHttp + provider headers | Yeterli veri yok | Kanıt kapsamına göre |
| caching | client repolarda daha görünür | merkezi cache doğrulanmadı | Bu repo | Kanıt kapsamına göre |
| observability | bazı repolarda in-app/source health | CI/health metadata | Duruma göre | Kanıt kapsamına göre |
| testability | source görünürlüğüne bağlı | provider test dirs + smoke metadata | Duruma göre | Kanıt kapsamına göre |

Temel farkın teknik sonucu: bu repo geniş coverage veya client-level abstraction sağlıyorsa Bizim Repo'nun güvenli domain/payload tasarımı korunarak yalnızca ilgili pattern incremental adapte edilmelidir. Büyük rewrite otomatik olarak önerilmez.

## 6. Bizde Olmayan Özellikler

| Özellik | Nasıl Çalışıyor? | Bizde Var mı? | Bize Faydası | Uygulama Zorluğu |
|---|---|---|---|---|
| core/ modülü | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| feature/service modülerliği | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| FOSS telemetry ayrımı | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| desktop/mobile adapters | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |
| SponsorBlock/RYD integrations | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |

## 7. Daha Performanslı Yaklaşımlar

- Büyük client mimarisinde feature isolation ve optional integration patternleri için güçlü.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** core/ modülü.
- **[DOĞRULANDI/README]** feature/service modülerliği.
- **[DOĞRULANDI/README]** FOSS telemetry ayrımı.
- **[DOĞRULANDI/README]** desktop/mobile adapters.
- **[DOĞRULANDI/README]** SponsorBlock/RYD integrations.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- Büyük client mimarisinde feature isolation ve optional integration patternleri için güçlü.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- Upstream SimpMusic etkisi ayrıştırılmalı; özgün katkı ile upstream karıştırılmamalı.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P1 | Büyük client mimarisinde feature isolation ve optional integration patternleri için güçlü | Teknik öğrenim/coverage | Orta | Orta | PrabinCode/VibeFlow |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [README.md](https://github.com/PrabinCode/VibeFlow/blob/main/README.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [core/](https://github.com/PrabinCode/VibeFlow/tree/main/core) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [composeApp/](https://github.com/PrabinCode/VibeFlow/tree/main/composeApp) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [androidApp/](https://github.com/PrabinCode/VibeFlow/tree/main/androidApp) | Repo için yüksek sinyalli dosya/dizin | P1 |
| [desktopApp/](https://github.com/PrabinCode/VibeFlow/tree/main/desktopApp) | Repo için yüksek sinyalli dosya/dizin | P1 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. core/ modülü
2. feature/service modülerliği
3. Büyük client mimarisinde feature isolation ve optional integration patternleri için güçlü

**Bizim için genel AR-GE değeri:** `8.8/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
