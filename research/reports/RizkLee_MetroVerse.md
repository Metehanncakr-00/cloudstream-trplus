# RizkLee/MetroVerse — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** müzik streaming uygulaması  
**Bizim açımızdan önem seviyesi:** Yüksek  
**Teknoloji stack:** Kotlin multi-module; innertube/kugou/lastfm modules

Çoklu servis modülleri açıkça ayrılmış müzik istemcisi.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [RizkLee/MetroVerse](https://github.com/RizkLee/MetroVerse)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** servis başına ayrı modül.
- **[DOĞRULANDI/README]** API adapter isolation.
- **[DOĞRULANDI/README]** scrobble service separation.

**[ÇIKARIM]** Mimari sonuç: Bu tam bir client olduğundan networking/cache/playback fikirleri alınabilir; CloudStream provider contractına birebir taşınmamalıdır.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| InnerTube/YouTube Music | Müzik/servis | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| KuGou | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Last.fm | Müzik/servis | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: servis başına ayrı modül, API adapter isolation, scrobble service separation.
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
| servis başına ayrı modül | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| API adapter isolation | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| scrobble service separation | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |

## 7. Daha Performanslı Yaklaşımlar

- Provider adapterlarını site-family modüllerine ayırma açısından güçlü referans.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** servis başına ayrı modül.
- **[DOĞRULANDI/README]** API adapter isolation.
- **[DOĞRULANDI/README]** scrobble service separation.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- Provider adapterlarını site-family modüllerine ayırma açısından güçlü referans.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- İç modüllerin implementasyon ayrıntıları bu turda bütünüyle okunmadı.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P1 | Provider adapterlarını site-family modüllerine ayırma açısından güçlü referans | Teknik öğrenim/coverage | Orta | Orta | RizkLee/MetroVerse |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [innertube/](https://github.com/RizkLee/MetroVerse/tree/main/innertube) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [kugou/](https://github.com/RizkLee/MetroVerse/tree/main/kugou) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [lastfm/](https://github.com/RizkLee/MetroVerse/tree/main/lastfm) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [app/](https://github.com/RizkLee/MetroVerse/tree/main/app) | Repo için yüksek sinyalli dosya/dizin | P1 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. servis başına ayrı modül
2. API adapter isolation
3. Provider adapterlarını site-family modüllerine ayırma açısından güçlü referans

**Bizim için genel AR-GE değeri:** `8.0/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
