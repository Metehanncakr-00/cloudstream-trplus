# falsisdev/anthology — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** Nuvio plugin/catalog repository  
**Bizim açımızdan önem seviyesi:** Kritik  
**Teknoloji stack:** JavaScript/Nuvio/Stremio-style manifests + static GitHub hosting

35 doğrulanmış scraper + 13 katalog ile Nuvio tarafında Türkçe film/dizi/anime/canlı TV ekosistemi.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [falsisdev/anthology](https://github.com/falsisdev/anthology)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** catalog ve resolver katmanını ayırma.
- **[DOĞRULANDI/README]** serverless static manifest dağıtımı.
- **[DOĞRULANDI/README]** JS scraper portability.
- **[DOĞRULANDI/README]** çoklu HLS/MP4 normalize etme.

**[ÇIKARIM]** Mimari sonuç: Provider kapsamı ve resolver orkestrasyonu doğrudan karşılaştırılabilir.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| DDizi | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziBox | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziMom | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| SineWix | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| FilmModu | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| AnimeciX | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| TurkAnime | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| CizgiMax | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziPal | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| JetFilmIzle | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| SinemaCX | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Vidlink | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Vidmody | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| WebteIzle | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Mahsun Dizi | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| YabanciDizi | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| SezonlukDizi | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziYou | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Canli TV/Spor/Haber | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: catalog ve resolver katmanını ayırma, serverless static manifest dağıtımı, JS scraper portability, çoklu HLS/MP4 normalize etme.
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
| catalog ve resolver katmanını ayırma | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| serverless static manifest dağıtımı | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| JS scraper portability | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| çoklu HLS/MP4 normalize etme | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |

## 7. Daha Performanslı Yaklaşımlar

- CloudStream provider coverage boşluklarını bulmak için çok değerli.
- catalog/resolver separation Bizim Repo architecture ile uyumlu bir fikir.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** catalog ve resolver katmanını ayırma.
- **[DOĞRULANDI/README]** serverless static manifest dağıtımı.
- **[DOĞRULANDI/README]** JS scraper portability.
- **[DOĞRULANDI/README]** çoklu HLS/MP4 normalize etme.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- CloudStream provider coverage boşluklarını bulmak için çok değerli.
- catalog/resolver separation Bizim Repo architecture ile uyumlu bir fikir.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- README iddiaları source ile tam doğrulanmadan kesin kalite/başarı oranı kabul edilmemeli.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P0 | CloudStream provider coverage boşluklarını bulmak için çok değerli | Teknik öğrenim/coverage | Orta | Orta | falsisdev/anthology |
| P1 | catalog/resolver separation Bizim Repo architecture ile uyumlu bir fikir | Teknik öğrenim/coverage | Orta | Orta | falsisdev/anthology |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [README.md](https://github.com/falsisdev/anthology/blob/main/README.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [manifest.json](https://github.com/falsisdev/anthology/blob/main/manifest.json) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [stremio/](https://github.com/falsisdev/anthology/tree/main/stremio) | Repo için yüksek sinyalli dosya/dizin | P0 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. catalog ve resolver katmanını ayırma
2. serverless static manifest dağıtımı
3. CloudStream provider coverage boşluklarını bulmak için çok değerli

**Bizim için genel AR-GE değeri:** `9.2/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
