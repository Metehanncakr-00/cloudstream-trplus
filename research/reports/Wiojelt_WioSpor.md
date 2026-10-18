# Wiojelt/WioSpor — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** CloudStream live/sports plugin  
**Bizim açımızdan önem seviyesi:** Yüksek  
**Teknoloji stack:** Kotlin / CloudStream

Canlı spor/ulusal kanal kaynaklarını tek eklentide çoklu kaynakla sunuyor.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [Wiojelt/WioSpor](https://github.com/Wiojelt/WioSpor)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** aynı kanal için çoklu kaynak listeleme.
- **[DOĞRULANDI/README]** kaynak health kontrolü.
- **[DOĞRULANDI/README]** web link yenileme.

**[ÇIKARIM]** Mimari sonuç: Provider kapsamı ve resolver orkestrasyonu doğrudan karşılaştırılabilir.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| Mor Spor | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Turuncu Spor | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Yeşil Spor | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Sarı Spor | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Mavi Spor | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Yıldız Spor | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Ulusal | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: aynı kanal için çoklu kaynak listeleme, kaynak health kontrolü, web link yenileme.
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
| aynı kanal için çoklu kaynak listeleme | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| kaynak health kontrolü | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| web link yenileme | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |

## 7. Daha Performanslı Yaklaşımlar

- live stream provider modeli.
- aynı logical channel için fallback chain.
- runtime source health UI fikri.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** aynı kanal için çoklu kaynak listeleme.
- **[DOĞRULANDI/README]** kaynak health kontrolü.
- **[DOĞRULANDI/README]** web link yenileme.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- live stream provider modeli.
- aynı logical channel için fallback chain.
- runtime source health UI fikri.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- Kaynakların hukuki/erişim ömrü değişken; kalıcı stream URL saklamaktan kaçınılmalı.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P1 | live stream provider modeli | Teknik öğrenim/coverage | Orta | Orta | Wiojelt/WioSpor |
| P1 | aynı logical channel için fallback chain | Teknik öğrenim/coverage | Orta | Orta | Wiojelt/WioSpor |
| P2 | runtime source health UI fikri | Teknik öğrenim/coverage | Düşük-Orta | Orta | Wiojelt/WioSpor |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [README.md](https://github.com/Wiojelt/WioSpor/blob/main/README.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [build.gradle.kts](https://github.com/Wiojelt/WioSpor/blob/main/build.gradle.kts) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [src/](https://github.com/Wiojelt/WioSpor/tree/main/src) | Repo için yüksek sinyalli dosya/dizin | P0 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. aynı kanal için çoklu kaynak listeleme
2. kaynak health kontrolü
3. live stream provider modeli

**Bizim için genel AR-GE değeri:** `8.7/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
