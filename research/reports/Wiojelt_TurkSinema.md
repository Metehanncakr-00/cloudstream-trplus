# Wiojelt/TurkSinema — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** CloudStream provider/extension repository  
**Bizim açımızdan önem seviyesi:** Kritik  
**Teknoloji stack:** Kotlin / CloudStream

60 ayrı indirilebilir film/dizi/anime/belgesel eklentisini provider başına ayıran geniş Türkçe repo.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [Wiojelt/TurkSinema](https://github.com/Wiojelt/TurkSinema)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** provider başına ayrı paket.
- **[DOĞRULANDI/README]** domain erişim kontrolü.
- **[DOĞRULANDI/README]** yönlendirme güncelleme mekanizması.
- **[DOĞRULANDI/README]** istatistik/metadata ayrımı.

**[ÇIKARIM]** Mimari sonuç: Provider kapsamı ve resolver orkestrasyonu doğrudan karşılaştırılabilir.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| Film/Dizi/Anime/Belgesel sağlayıcıları; tam liste PROVIDERS.md | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: provider başına ayrı paket, domain erişim kontrolü, yönlendirme güncelleme mekanizması, istatistik/metadata ayrımı.
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
| provider başına ayrı paket | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| domain erişim kontrolü | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| yönlendirme güncelleme mekanizması | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| istatistik/metadata ayrımı | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |

## 7. Daha Performanslı Yaklaşımlar

- Bizim legacy inventorydeki providerları canlandırmak için en yakın karşılaştırma.
- provider isolation bakım blast-radius azaltabilir.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** provider başına ayrı paket.
- **[DOĞRULANDI/README]** domain erişim kontrolü.
- **[DOĞRULANDI/README]** yönlendirme güncelleme mekanizması.
- **[DOĞRULANDI/README]** istatistik/metadata ayrımı.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- Bizim legacy inventorydeki providerları canlandırmak için en yakın karşılaştırma.
- provider isolation bakım blast-radius azaltabilir.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- Tam source tree bu turda bütünüyle çekilmedi; provider implementasyon detayı için manuel takip gerekli.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P0 | Bizim legacy inventorydeki providerları canlandırmak için en yakın karşılaştırma | Teknik öğrenim/coverage | Orta | Orta | Wiojelt/TurkSinema |
| P1 | provider isolation bakım blast-radius azaltabilir | Teknik öğrenim/coverage | Orta | Orta | Wiojelt/TurkSinema |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [README.md](https://github.com/Wiojelt/TurkSinema/blob/main/README.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [PROVIDERS.md](https://github.com/Wiojelt/TurkSinema/blob/main/PROVIDERS.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [domains.json](https://github.com/Wiojelt/TurkSinema/blob/main/domains.json) | Repo için yüksek sinyalli dosya/dizin | P0 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. provider başına ayrı paket
2. domain erişim kontrolü
3. Bizim legacy inventorydeki providerları canlandırmak için en yakın karşılaştırma

**Bizim için genel AR-GE değeri:** `9.5/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
