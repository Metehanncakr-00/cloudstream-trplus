# afkcodes/sunoh — Teknik AR-GE Raporu

> Baseline: **Bizim Repo = Emre-Kahveci/CloudStreamHub**  
> İnceleme tarihi: 2026-09-14

## 1. Repository Özeti

**Repo kategorisi:** müzik streaming uygulaması  
**Bizim açımızdan önem seviyesi:** Orta  
**Teknoloji stack:** Flutter/Dart, mpv_audio_kit, multi-platform

YouTube Music + Gaana + Saavn + local media + podcast/audiobook kaynaklarını tek aramada birleştiren müzik uygulaması.

**Neden önemli?** Bu repo, Bizim Repo için ya provider coverage, extractor/resolver tekniği ya da streaming client mimarisi bakımından karşılaştırma değeri taşıyor. Repo tipi CloudStream değilse öneriler yalnızca konsept/architecture seviyesinde ele alınmıştır.

Kaynak: [afkcodes/sunoh](https://github.com/afkcodes/sunoh)

## 2. Teknik Mimari

- **[DOĞRULANDI/README]** federated multi-source search.
- **[DOĞRULANDI/README]** mpv playback.
- **[DOĞRULANDI/README]** SponsorBlock.
- **[DOĞRULANDI/README]** çoklu lyrics kaynağını paralel sorgulama.
- **[DOĞRULANDI/README]** offline-first Android Auto yaklaşımı.

**[ÇIKARIM]** Mimari sonuç: Bu tam bir client olduğundan networking/cache/playback fikirleri alınabilir; CloudStream provider contractına birebir taşınmamalıdır.

**[DOĞRULANAMADI]** Repo genelindeki tüm retry/timeout/cache/concurrency davranışları, her source dosyası eksiksiz okunmadığı durumlarda kesin kabul edilmemiştir.

## 3. Desteklenen Siteler / Servisler / Providerlar

| Site / Servis | Tür | Implementasyon | Bizde Var mı? | İlginç Nokta | Araştırma Değeri |
|---|---|---|---|---|---|
| YouTube Music | Müzik/servis | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Gaana | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Saavn | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Local media | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Podcast | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |
| Audiobook | Film/Dizi/Canlı | Repo-specific | Hayır/karşılaştırılmalı | Coverage/implementation karşılaştırması | Yüksek |

## 4. Site Entegrasyon Teknikleri

- Provider/streaming reposunda gözlenen veya README/source yapısıyla desteklenen ana yöntemler: federated multi-source search, mpv playback, SponsorBlock, çoklu lyrics kaynağını paralel sorgulama, offline-first Android Auto yaklaşımı.
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
| testability | source görünürlüğüne bağlı | provider test dirs + smoke metadata | Bizim Repo | Kanıt kapsamına göre |

Temel farkın teknik sonucu: bu repo geniş coverage veya client-level abstraction sağlıyorsa Bizim Repo'nun güvenli domain/payload tasarımı korunarak yalnızca ilgili pattern incremental adapte edilmelidir. Büyük rewrite otomatik olarak önerilmez.

## 6. Bizde Olmayan Özellikler

| Özellik | Nasıl Çalışıyor? | Bizde Var mı? | Bize Faydası | Uygulama Zorluğu |
|---|---|---|---|---|
| federated multi-source search | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| mpv playback | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| SponsorBlock | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Orta |
| çoklu lyrics kaynağını paralel sorgulama | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |
| offline-first Android Auto yaklaşımı | Repo patterni | Hayır/Kısmen | Coverage, bakım veya reliability | Yüksek |

## 7. Daha Performanslı Yaklaşımlar

- CloudStream search fan-out ve source prioritization için konsept.
- çoklu kaynaktan sonuç normalize etme.
- offline/cache UX ilkeleri.

> Bu maddeler benchmark olmadan performans gerçeği olarak sunulmamıştır. "Daha iyi olabilir" denilen yerlerde ölçüm gereklidir.

## 8. İlginç Kod / Tasarım Patternleri

- **[DOĞRULANDI/README]** federated multi-source search.
- **[DOĞRULANDI/README]** mpv playback.
- **[DOĞRULANDI/README]** SponsorBlock.
- **[DOĞRULANDI/README]** çoklu lyrics kaynağını paralel sorgulama.
- **[DOĞRULANDI/README]** offline-first Android Auto yaklaşımı.

İlgili başlangıç noktaları aşağıdaki "Araştırılacak Dosyalar" tablosundadır.

## 9. AR-GE Açısından Ne Öğrenebiliriz?

### Bu projeden ne öğrenebiliriz?
- CloudStream search fan-out ve source prioritization için konsept.
- çoklu kaynaktan sonuç normalize etme.
- offline/cache UX ilkeleri.

### Hangi sistemi benchmark etmeliyiz?
- Aynı içerik için search latency ve HTTP request count.
- Link resolution p50/p95 süresi.
- Fallback chain başarı oranı.
- Provider/source health doğruluğu.
- Client repo ise cache hit ratio ve cold-start yalnızca konsept karşılaştırması olarak ölçülmeli.

### Riskler / sınırlamalar
- Video provider koduyla doğrudan uyumlu değil; fikirler mimari seviyede alınmalı.

## 10. Uygulanabilir Fikirler

| Öncelik | Fikir | Beklenen Kazanç | Efor | Risk | Kaynak |
|---|---|---|---|---|---|
| P1 | CloudStream search fan-out ve source prioritization için konsept | Teknik öğrenim/coverage | Orta | Orta | afkcodes/sunoh |
| P1 | çoklu kaynaktan sonuç normalize etme | Teknik öğrenim/coverage | Orta | Orta | afkcodes/sunoh |
| P2 | offline/cache UX ilkeleri | Teknik öğrenim/coverage | Düşük-Orta | Orta | afkcodes/sunoh |

## 11. Araştırılacak Dosyalar

| Dosya | Neden Önemli? | Öncelik |
|---|---|---|
| [README.md](https://github.com/afkcodes/sunoh/blob/main/README.md) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [pubspec.yaml](https://github.com/afkcodes/sunoh/blob/main/pubspec.yaml) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [lib/](https://github.com/afkcodes/sunoh/tree/main/lib) | Repo için yüksek sinyalli dosya/dizin | P0 |
| [test/](https://github.com/afkcodes/sunoh/tree/main/test) | Repo için yüksek sinyalli dosya/dizin | P1 |

## 12. Sonuç

**Bu repodan alınabilecek en önemli 3 fikir:**
1. federated multi-source search
2. mpv playback
3. CloudStream search fan-out ve source prioritization için konsept

**Bizim için genel AR-GE değeri:** `7.8/10`

Puan; Bizim Repo'ya benzerlik, provider coverage, reusable pattern yoğunluğu ve source doğrulanabilirliği birlikte değerlendirilerek verilmiştir.
