# Wiojelt/TurkSpor — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** CloudStream provider aggregation repository  
**Bizim açımızdan önem seviyesi:** Kritik  
**Teknoloji stack:** Kotlin / CloudStream + JSON domain/catalog metadata

27 ayrı spor sağlayıcısı, 150+ kanal, provider başına ayar ve bağlantı kontrolü.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [Wiojelt/TurkSpor](https://github.com/Wiojelt/TurkSpor)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** provider başına bağımsız paket.
- **[DOĞRULANDI/README]** domains.json merkezi domain kataloğu.
- **[DOĞRULANDI/README]** catalogs/ ayrımı.
- **[DOĞRULANDI/README]** Nuvio bridge çıktısı.
- **[DOĞRULANDI/README]** provider-level update/health ekranı.

**[ÇIKARIM]** Mimari sonuç: Provider kapsamı ve resolver orkestrasyonu doğrudan karşılaştırılabilir.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| SelcukSports | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Taraftarium24 | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| InatTV | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Crex | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| AslanTV | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| InatBox | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| BeyazElma | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| MahsunSports | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| ArdaSpor | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| MacKeyfi | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| ZbahisTV | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| InterSporTV | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| KralSporHD | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Streamed | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| VivoXSpor | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DomatesTV | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DominoTV | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| TRGoals | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| BetmatikTV | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| NETV Gold | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| NTVStream | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| DaddyLive | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| StreamEast | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Hesgoal | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| LiveXTV | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| PapazSports | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| JestYayin | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: provider başına bağımsız paket, domains.json merkezi domain kataloğu, catalogs/ ayrımı, Nuvio bridge çıktısı, provider-level update/health ekranı.
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
| provider başına bağımsız paket | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| domains.json merkezi domain kataloğu | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| catalogs/ ayrımı | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| Nuvio bridge çıktısı | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |
| provider-level update/health ekranı | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |

## 7. Daha Performanslı Yaklaşımlar

- Bizim domain management yaklaşımımızla doğrudan karşılaştırılabilir.
- live provider registry için güçlü referans.
- CloudStream dışı katalog/bridge üretimi araştırılabilir.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** provider başına bağımsız paket.
- **[DOĞRULANDI/README]** domains.json merkezi domain kataloğu.
- **[DOĞRULANDI/README]** catalogs/ ayrımı.
- **[DOĞRULANDI/README]** Nuvio bridge çıktısı.
- **[DOĞRULANDI/README]** provider-level update/health ekranı.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- Bizim domain management yaklaşımımızla doğrudan karşılaştırılabilir.
- live provider registry için güçlü referans.
- CloudStream dışı katalog/bridge üretimi araştırılabilir.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- Spor akışlarının dinamik ve sık değişen yapısı yüksek bakım maliyeti yaratır.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P0 | Bizim domain management yaklaşımımızla doğrudan karşılaştırılabilir | Teknik öğrenim/coverage | Orta | Orta | Wiojelt/TurkSpor |
| P1 | live provider registry için güçlü referans | Teknik öğrenim/coverage | Orta | Orta | Wiojelt/TurkSpor |
| P2 | CloudStream dışı katalog/bridge üretimi araştırılabilir | Teknik öğrenim/coverage | Düşük-Orta | Orta | Wiojelt/TurkSpor |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [README.md](https://github.com/Wiojelt/TurkSpor/blob/main/README.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [domains.json](https://github.com/Wiojelt/TurkSpor/blob/main/domains.json) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [channel-rules.json](https://github.com/Wiojelt/TurkSpor/blob/main/channel-rules.json) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [catalogs/](https://github.com/Wiojelt/TurkSpor/tree/main/catalogs) | Repo için yüksek sinyalli dosya/dizin | P1 |
| [NUVIO.md](https://github.com/Wiojelt/TurkSpor/blob/main/NUVIO.md) | Repo için yüksek sinyalli dosya/dizin | P1 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. provider başına bağımsız paket
2. domains.json merkezi domain kataloğu
3. Bizim domain management yaklaşımımızla doğrudan karşılaştırılabilir

**Bizim için genel AR-GE değeri:** `9.3/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
