# kaankirsan/akin-cloudstream — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** CloudStream provider repository  
**Bizim açımızdan önem seviyesi:** Yüksek  
**Teknoloji stack:** Kotlin / CloudStream

DiziYou, 4KFilmIzlesene, FilmKovasi, FullHDFilmizlesene, HDFilmCehennemi gibi ayrı provider modülleri.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [kaankirsan/akin-cloudstream](https://github.com/kaankirsan/akin-cloudstream)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** provider-per-module template.
- **[DOĞRULANDI/README]** legacy CloudStream repo structure.

**[ÇIKARIM]** Mimari sonuç: Provider kapsamı ve resolver orkestrasyonu doğrudan karşılaştırılabilir.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| DiziYou | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DortKFilmIzlesene | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| FilmKovasi | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| FullHDFilmizlesene | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| HDFilmCehennemi | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: provider-per-module template, legacy CloudStream repo structure.
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
| provider-per-module template | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| legacy CloudStream repo structure | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |

## 7. Daha Performanslı Yaklaşımlar

- DiziYou/4KFilmIzlesene/FilmKovasi coverage gaplerini doldurabilir.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** provider-per-module template.
- **[DOĞRULANDI/README]** legacy CloudStream repo structure.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- DiziYou/4KFilmIzlesene/FilmKovasi coverage gaplerini doldurabilir.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- Repo küçük/eski olabilir; güncel site uyumu test edilmeli.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P1 | DiziYou/4KFilmIzlesene/FilmKovasi coverage gaplerini doldurabilir | Teknik öğrenim/coverage | Orta | Orta | kaankirsan/akin-cloudstream |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [build.gradle.kts](https://github.com/kaankirsan/akin-cloudstream/blob/master/build.gradle.kts) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [settings.gradle.kts](https://github.com/kaankirsan/akin-cloudstream/blob/master/settings.gradle.kts) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [DiziYou/](https://github.com/kaankirsan/akin-cloudstream/tree/master/DiziYou) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [DortKFilmIzlesene/](https://github.com/kaankirsan/akin-cloudstream/tree/master/DortKFilmIzlesene) | Repo için yüksek sinyalli dosya/dizin | P1 |
| [FilmKovasi/](https://github.com/kaankirsan/akin-cloudstream/tree/master/FilmKovasi) | Repo için yüksek sinyalli dosya/dizin | P1 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. provider-per-module template
2. legacy CloudStream repo structure
3. DiziYou/4KFilmIzlesene/FilmKovasi coverage gaplerini doldurabilir

**Bizim için genel AR-GE değeri:** `8.1/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
