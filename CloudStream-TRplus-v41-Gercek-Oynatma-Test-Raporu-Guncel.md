# CloudStream TR+ v41 — Gerçek Android Oynatma Test Raporu (Güncel)

**Güncelleme tarihi:** 18 Ekim 2026  
**Kapsam:** v41 `builds` dağıtımındaki 26 CloudStream eklentisi  
**Kanıt kaynağı:** Kullanıcının emülatörde elle yaptığı oynatma denemeleri, önceki v40/v39 test raporu ve v41 derleme/dağıtım bildirimi.  
**Sınır:** Bu rapor yeni canlı Android testi veya sitelerin güncel durumunun bağımsız yeniden doğrulanması değildir. Kod derleme ve HTTP/manifest kontrolleri, gerçek oynatma başarıları yerine kullanılmamıştır.

## 1. v41'de doğrudan test edilenler — 8/26

| Sağlayıcı | v40 sonucu | v41 gerçek oynatma sonucu | Dil / not |
|---|---|---|---|
| **HDFilmDelisi** | Başarısız: “Web sitemizden izleyin” | **ÇALIŞIYOR** | Test edilen içeriklerde Türkçe seçenek bulunamadı; tüm kataloga genellenemez. |
| **YTS** | Başarısız: Bağlantı yok | **ÇALIŞIYOR** | Test edilen içeriklerde Türkçe seçenek bulunamadı; bu tek başına oynatma hatası değildir. |
| **SetFilmİzle** | Bağlantı yok | **BAŞARISIZ — bağlantı bulunamadı** | v41 onarım iddiası gerçek oynatmada doğrulanmadı. |
| **DiziGecesi** | Bağlantı yok | **BAŞARISIZ — bağlantı bulunamadı** | v41 onarım iddiası gerçek oynatmada doğrulanmadı. |
| **Dizilla** | Doğrudan açılmıyor | **BAŞARISIZ — hata veriyor** | v41 hata mesajı/kodu bildirilmedi. Önceki sürümde HTTP 2004 görülmüştü; aynı hata olduğu varsayılmamalı. |
| **FullHDFilmizlesene** | Bağlantı yok | **BAŞARISIZ — bağlantı bulunamadı** | v41 onarım iddiası gerçek oynatmada doğrulanmadı. |
| **Animeler** | Bağlantı yok | **BAŞARISIZ — bağlantı bulunamadı** | v41 onarım iddiası gerçek oynatmada doğrulanmadı. |
| **FourKHDHub** | Bağlantı yok / açılmıyor | **BAŞARISIZ — bağlantı bulunamadı** | v41 geliştirme raporunda da erişim/bot engeli bildiriliyor. |

**v41 test özeti:** **2 çalışıyor · 0 kısmen çalışıyor · 6 başarısız · 18 henüz v41'de doğrulanmadı**.

## 2. Diğer 18 sağlayıcı — yalnızca eski sürüm gözlemleri

Aşağıdakiler **v41'de yeniden test edilmiş sayılmaz**. Bilinen en son kullanıcı gözlemi korunmuştur.

| Sağlayıcı | Son gerçek test sürümü | Son bilinen durum | v41 oynatma |
|---|---|---|---|
| CloudStreamHub (Süper Eklenti) | v40 | Çalışıyor; The Office Türkçe açılıyor, alt kaynak kesinleşmedi | **TEST EDİLMEDİ** |
| DramaDizilerim | v40 | Çalışıyor | **TEST EDİLMEDİ** |
| KultFilmler | v40 | Çalışıyor | **TEST EDİLMEDİ** |
| SinemaCX | v40 | Çalışıyor | **TEST EDİLMEDİ** |
| StarTV | v40 | Çalışıyor | **TEST EDİLMEDİ** |
| Diziİzle | v40 | Kısmen çalışıyor; bazı diziler/bölümler açılıyor | **TEST EDİLMEDİ** |
| AnimeciX | v40 | Kısmen çalışıyor; denenen içeriklerde Türkçe seçenek yoktu | **TEST EDİLMEDİ** |
| YesilCamTv | v40 | Kısmen çalışıyor | **TEST EDİLMEDİ** |
| SezonlukDizi | v40 | Bağlantı yok; v41 geliştirme raporunda CAPTCHA/Turnstile engeli | **TEST EDİLMEDİ** |
| HDFilmCehennemi | v39 | Çalışıyor | **TEST EDİLMEDİ** |
| FilmMakinesi | v39 | Çalışıyor | **TEST EDİLMEDİ** |
| DiziYou | v39 | Çalışıyor; The Office Türkçe oynuyordu | **TEST EDİLMEDİ** |
| DiziKorea | v39 | Çalışıyor, denenen içeriklerde Türkçe yok | **TEST EDİLMEDİ** |
| BelgeselX | v39 | Kısmen çalışıyor | **TEST EDİLMEDİ** |
| JetFilmİzle | v39 | Oynatma sırasında donma/hata | **TEST EDİLMEDİ** |
| BlenderOpenMovies | v39 | Bağlantı bulunamadı | **TEST EDİLMEDİ** |
| WebDramaTurkey | v39 | Bağlantı bulunamadı | **TEST EDİLMEDİ** |
| RareFilmm | v39 | Bağlantı bulunamadı | **TEST EDİLMEDİ** |

## 3. Önceki sürümlere göre teyit edilen değişim

- **HDFilmDelisi:** v40 başarısız → v41 video oynatıyor. **Doğrulanmış ilerleme.**
- **YTS:** v40 başarısız → v41 video oynatıyor. **Doğrulanmış ilerleme.**
- **SetFilmİzle, DiziGecesi, FullHDFilmizlesene, Animeler, FourKHDHub:** v40 başarısız → v41 de başarısız. **Gerçek oynatma iyileşmesi doğrulanmadı.**
- **Dizilla:** v40 doğrudan açılmıyordu → v41 hata veriyor. **Başarı doğrulanmadı; hata tipi belirsiz.**
- **Türkçe içerik:** HDFilmDelisi ve YTS'de kullanıcının denediği içeriklerde Türkçe bulunamadı. Bu durum, bütün sağlayıcının Türkçe içeriğe sahip olmadığını kanıtlamaz.

## 4. Geliştirme raporuyla çelişkiler

Gemini'nin v41 geliştirme raporunda toplam 24/26 sağlayıcı için `BAĞLANTI DOĞRULANDI` ifadesi kullanıldı. Kullanıcı tarafından Android'de doğrulanan 8 sağlayıcının 6'sı başarısız. Bu bir teknik çelişki olmak zorunda değil: HTTP isteği / manifesto erişimi, CloudStream eklentisinin `loadLinks` aşamasında oynatılabilir medya üretmesi ve gerçek oynatma ayrı doğrulama düzeyleridir. Ancak **24/26 gerçekten çalışıyor** ifadesi kullanılmamalıdır.

Ayrıca `SezonlukDizi` ve `FourKHDHub` için v41 geliştirme raporunda `BLOCKED_WITH_EVIDENCE` kayıtları yer almaktadır. Koruma aşımı veya yetkisiz erişim hedeflenmemeli; bunlar engelli/belirsiz olarak raporlanmalıdır.

## 5. Sonraki onarım için öncelik sırası

**P0 — v41'de gerçekten başarısız olanlar:**
- SetFilmİzle, DiziGecesi, FullHDFilmizlesene, Animeler, Dizilla: uygulama içi `search → load → loadLinks → kaynak listesi → medya isteği` zincirinde hangi aşamanın başarısız olduğunu belirle. Test edilmiş bir içerik/URL, son HTTP durum kodu ve varsa Logcat kaydıyla teşhis et.
- FourKHDHub: mevcut bot korumasını raporla; erişim korumalarını aşan yöntemler kullanma.

**P1 — eski sürümde kısmen çalışanlar:** Diziİzle, AnimeciX, YesilCamTv. Çalışan örnekleri koruyarak içerik bazlı regresyon testleri ekle.

**P1 — v41'de testi olmayanlar:** v40'ta başarılı olan CloudStreamHub, DramaDizilerim, KultFilmler, SinemaCX, StarTV ve v39'da çalışan HDFilmCehennemi, FilmMakinesi, DiziYou'nun regresyon risklerini otomatik testlerle kontrol et. BlenderOpenMovies, WebDramaTurkey, RareFilmm, JetFilmİzle, BelgeselX ve DiziKorea için gerçek oynatmayı doğrulanmış sayma.

**P2 — dil ve kullanım deneyimi:** Var olduğu doğrulanmış Türkçe dublaj/altyazılı medya seçeneklerini öne çıkar; olmayan dil seçeneklerini uydurma. Doğrulanamayan ses dili etiketlerini `unverified` olarak tut; otomatik seçim ve manuel kaynak değiştirmeyi ayrı test et.

## 6. Teslim ve karar

**Karar:** v41, HDFilmDelisi ve YTS tarafında v40'a göre gerçek oynatma kazanımı sağlamış; fakat diğer altı sağlayıcı henüz başarısızdır. **Tüm sağlayıcılar çalışıyor veya v41 tam onarıldı denemez.** v41 dağıtımını koru; sonraki kod düzeltmelerini hedefli uygula. Kullanıcıyı 26 sağlayıcının tamamını yeniden test etmeye zorlamadan, mevcut kanıtlara dayalı onarım öncelikleriyle ilerle.

**Test ayrımı:** `BUILD / MANIFEST / NETWORK / LINKS / ANDROID PLAYBACK / AUDIO & LANGUAGE`. Her aşamada ayrı doğrulama etiketi kullanılmalı.
