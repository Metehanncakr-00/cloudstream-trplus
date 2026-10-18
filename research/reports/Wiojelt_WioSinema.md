# Wiojelt/WioSinema — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** CloudStream plugin repository  
**Bizim açımızdan önem seviyesi:** Kritik  
**Teknoloji stack:** Kotlin / CloudStream unified plugin

TMDB v3 Türkçe katalog + çoklu resolver/aggregator; README 58-66 sağlayıcı ölçeği belirtiyor.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [Wiojelt/WioSinema](https://github.com/Wiojelt/WioSinema)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** tek eklentide provider aggregation.
- **[DOĞRULANDI/README]** TMDB-ID tabanlı direkt resolver.
- **[DOĞRULANDI/README]** TV Box modunda concurrency/bellek kısıtlama.
- **[DOĞRULANDI/README]** provider enable/disable ve önerilenler profili.

**[ÇIKARIM]** Mimari sonuç: Provider kapsamı ve resolver orkestrasyonu doğrudan karşılaştırılabilir.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| ClipBox/TurkSinema | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| CineStream | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| CineSimkl | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| FlixNetwork | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| FilmMakinesi | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| HDFilmCehennemi | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| FilmModu | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| FullHDFilm | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| InatBox | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| JetFilmizle | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| SetFilmIzle | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| WebteIzle | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Sinewix | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Dizilla | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziBox | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziPal | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| SezonlukDizi | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziMom | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziYou | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziKorea | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DDizi | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| YabancıDizi | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| AnimeciX | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| TurkAnime | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| AsyaAnimeleri | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| BelgeselX | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| CizgiMax | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| TRanimaci | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| TRasyalog | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| TLCTR | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Internet Archive | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: tek eklentide provider aggregation, TMDB-ID tabanlı direkt resolver, TV Box modunda concurrency/bellek kısıtlama, provider enable/disable ve önerilenler profili.
- **Header/referer/cookie/token** davranışları yalnızca source ile doğrulandığı ölçüde güvenilir kabul edilmelidir.
- **[DOĞRULANAMADI]** Anti-bot, encrypted payload veya WebView kullanımına dair açık kanıt yoksa varmış gibi değerlendirilmedi.

## 5. Bizim Repo ile Karşılaştırma

| Konu | Bu Repo | Bizim Repo | Hangisi Daha İyi? | Neden? |
|---|---|---|---|---|
| architecture | repo-spesifik; aşağıdaki patternler | provider-per-module + merkezi config | Duruma göre | Kanıt kapsamına göre |
| maintainability | modülerlik derecesi repo tipine bağlı | iyi provider izolasyonu | Duruma göre | Kanıt kapsamına göre |
| provider architecture | çoklu/tek provider veya client adapter | 8 aktif provider | Bu repo | Kanıt kapsamına göre |
| networking | repo stackine özgü | NiceHttp + provider headers | Yeterli veri yok | Kanıt kapsamına göre |
| caching | client repolarda daha görünür | merkezi cache doğrulanmadı | Yeterli veri yok | Kanıt kapsamına göre |
| observability | bazı repolarda in-app/source health | CI/health metadata | Duruma göre | Kanıt kapsamına göre |
| testability | source görünürlüğüne bağlı | provider test dirs + smoke metadata | Duruma göre | Kanıt kapsamına göre |

Temel farkın teknik sonucu: bu repo geniş coverage veya client-level abstraction sağlıyorsa Bizim Repo'nun güvenli domain/payload tasarımı korunarak yalnızca ilgili pattern incremental adapte edilmelidir. Büyük rewrite otomatik olarak önerilmez.

## 6. Bizde Olmayan Özellikler

| Özellik | Nasıl Çalışıyor? | Bizde Var mı? | Bize Faydası | Uygulama Zorluğu |
|---|---|---|---|---|
| tek eklentide provider aggregation | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| TMDB-ID tabanlı direkt resolver | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| TV Box modunda concurrency/bellek kısıtlama | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| provider enable/disable ve önerilenler profili | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |

## 7. Daha Performanslı Yaklaşımlar

- Bizim 8 aktif provider kapsamını dramatik biçimde genişletebilir.
- provider fan-out orchestration için referans.
- TMDB kimliğiyle site aramasını atlama fırsatı.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** tek eklentide provider aggregation.
- **[DOĞRULANDI/README]** TMDB-ID tabanlı direkt resolver.
- **[DOĞRULANDI/README]** TV Box modunda concurrency/bellek kısıtlama.
- **[DOĞRULANDI/README]** provider enable/disable ve önerilenler profili.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- Bizim 8 aktif provider kapsamını dramatik biçimde genişletebilir.
- provider fan-out orchestration için referans.
- TMDB kimliğiyle site aramasını atlama fırsatı.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- README kaynak ağacında main branch yalnızca dağıtım/spec görünümü veriyor; gerçek source builds/başka branch olabilir, kod-seviyesi ayrıntı kısmen doğrulanamadı.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P0 | Bizim 8 aktif provider kapsamını dramatik biçimde genişletebilir | Teknik öğrenim/coverage | Orta | Orta | Wiojelt/WioSinema |
| P1 | provider fan-out orchestration için referans | Teknik öğrenim/coverage | Orta | Orta | Wiojelt/WioSinema |
| P2 | TMDB kimliğiyle site aramasını atlama fırsatı | Teknik öğrenim/coverage | Düşük-Orta | Orta | Wiojelt/WioSinema |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [README.md](https://github.com/Wiojelt/WioSinema/blob/main/README.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [PROVIDERS.md](https://github.com/Wiojelt/WioSinema/blob/main/PROVIDERS.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [repo.json](https://github.com/Wiojelt/WioSinema/blob/main/repo.json) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [specs/](https://github.com/Wiojelt/WioSinema/tree/main/specs) | Repo için yüksek sinyalli dosya/dizin | P1 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. tek eklentide provider aggregation
2. TMDB-ID tabanlı direkt resolver
3. Bizim 8 aktif provider kapsamını dramatik biçimde genişletebilir

**Bizim için genel AR-GE değeri:** `9.7/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
