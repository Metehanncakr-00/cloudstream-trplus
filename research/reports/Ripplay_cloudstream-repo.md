# Ripplay/cloudstream-repo — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** CloudStream provider repository  
**Bizim açımızdan önem seviyesi:** Kritik  
**Teknoloji stack:** Kotlin / CloudStream, Gradle, JDK17/API36

20 Türkçe kaynağı 13 cs3 paketinde; ortak altyapılı siteleri OwnedSites gibi paketlerde gruplayan repo.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [Ripplay/cloudstream-repo](https://github.com/Ripplay/cloudstream-repo)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** ortak altyapılı siteleri tek modülde gruplayan OwnedSites.
- **[DOĞRULANDI/README]** 13 paket/20 source.
- **[DOĞRULANDI/README]** otomatik builds branch yayını.

**[ÇIKARIM]** Mimari sonuç: Provider kapsamı ve resolver orkestrasyonu doğrudan karşılaştırılabilir.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| HDFilmCehennemi | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| JetFilmIzle | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DDizi | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziBox | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| TrDiziIzle | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| YabanciDizi | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziBal | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| FilmMakinesi | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| DiziRella | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| HDFilmIzle Ink | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziPal | Film/Dizi/Canlı | Repo-specific | Evet | Coverage/implementation karşılaştırması | Orta |
| DiziMom | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziBol | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| FilmIzzle | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| FullHDFilmIzlesene | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| HDFilmIzle Vip | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| LiderFilmIzle | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| SinemaGG | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DiziFilmIzle | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| FilmModu | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: ortak altyapılı siteleri tek modülde gruplayan OwnedSites, 13 paket/20 source, otomatik builds branch yayını.
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
| ortak altyapılı siteleri tek modülde gruplayan OwnedSites | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| 13 paket/20 source | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| otomatik builds branch yayını | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |

## 7. Daha Performanslı Yaklaşımlar

- Aynı engine kullanan sitelerde shared base provider ile duplication azaltma için güçlü aday.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** ortak altyapılı siteleri tek modülde gruplayan OwnedSites.
- **[DOĞRULANDI/README]** 13 paket/20 source.
- **[DOĞRULANDI/README]** otomatik builds branch yayını.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- Aynı engine kullanan sitelerde shared base provider ile duplication azaltma için güçlü aday.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- Aşırı gruplama bir site değişikliğinin blast-radiusunu artırabilir; abstraction sınırı dikkatli seçilmeli.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P0 | Aynı engine kullanan sitelerde shared base provider ile duplication azaltma için güçlü aday | Teknik öğrenim/coverage | Orta | Orta | Ripplay/cloudstream-repo |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [README.md](https://github.com/Ripplay/cloudstream-repo/blob/main/README.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [OwnedSites/](https://github.com/Ripplay/cloudstream-repo/tree/main/OwnedSites) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [build.gradle.kts](https://github.com/Ripplay/cloudstream-repo/blob/main/build.gradle.kts) | Repo için yüksek sinyalli dosya/dizin | P0 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. ortak altyapılı siteleri tek modülde gruplayan OwnedSites
2. 13 paket/20 source
3. Aynı engine kullanan sitelerde shared base provider ile duplication azaltma için güçlü aday

**Bizim için genel AR-GE değeri:** `9.0/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
