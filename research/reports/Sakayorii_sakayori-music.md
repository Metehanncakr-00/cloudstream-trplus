# Sakayorii/sakayori-music — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** müzik streaming uygulaması  
**Bizim açımızdan önem seviyesi:** Yüksek  
**Teknoloji stack:** Android/Kotlin music client

Modern müzik istemcisi; Better Lyrics, listen-together backend, music recognition ve YouTube cipher/PoToken ekosistemiyle entegrasyon referansları içeriyor.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [Sakayorii/sakayori-music](https://github.com/Sakayorii/sakayori-music)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** çoklu yardımcı servis entegrasyonu.
- **[DOĞRULANDI/README]** cipher/deobfuscation abstraction.
- **[DOĞRULANDI/README]** real-time backend ayrımı.

**[ÇIKARIM]** Mimari sonuç: Bu tam bir client olduğundan networking/cache/playback fikirleri alınabilir; CloudStream provider contractına birebir taşınmamalıdır.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| YouTube Music | Müzik/servis | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| lyrics services | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| listen-together backend | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: çoklu yardımcı servis entegrasyonu, cipher/deobfuscation abstraction, real-time backend ayrımı.
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
| çoklu yardımcı servis entegrasyonu | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| cipher/deobfuscation abstraction | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| real-time backend ayrımı | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |

## 7. Daha Performanslı Yaklaşımlar

- Extractor alt sistemlerinde yardımcı servisleri ana providerdan ayırma fikri.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** çoklu yardımcı servis entegrasyonu.
- **[DOĞRULANDI/README]** cipher/deobfuscation abstraction.
- **[DOĞRULANDI/README]** real-time backend ayrımı.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- Extractor alt sistemlerinde yardımcı servisleri ana providerdan ayırma fikri.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- YouTube cipher teknikleri CloudStream Türkçe sitelere doğrudan uygulanmayabilir.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P1 | Extractor alt sistemlerinde yardımcı servisleri ana providerdan ayırma fikri | Teknik öğrenim/coverage | Orta | Orta | Sakayorii/sakayori-music |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [README.md](https://github.com/Sakayorii/sakayori-music/blob/main/README.md) | Repo için yüksek sinyalli dosya/dizin | P0 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. çoklu yardımcı servis entegrasyonu
2. cipher/deobfuscation abstraction
3. Extractor alt sistemlerinde yardımcı servisleri ana providerdan ayırma fikri

**Bizim için genel AR-GE değeri:** `8.0/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
