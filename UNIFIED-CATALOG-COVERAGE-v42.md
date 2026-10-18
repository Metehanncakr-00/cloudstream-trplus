# CLOUDSTREAM TR+ v42 — BİRLEŞİK KATALOG VE KAPSAMA RAPORU (UNIFIED-CATALOG-COVERAGE-v42)

**Sürüm:** v42  
**Tarih:** 18 Ekim 2026  
**Durum:** Üretim Seviyesi / Tam Kapsama ve Mimari Belgesi  
**Modül:** `CloudStreamHub` (Süper Eklenti 2.0) ve 26 Aktif Sağlayıcı Federasyonu  

---

## 1. Birleşik Katalog Mimarisi (Unified Catalog Architecture)

CloudStream TR+ v42 ile CloudStreamHub, yalnızca bir TMDB meta veri toplayıcısı olmaktan çıkarılarak tüm aktif sağlayıcıların kataloglarını tek bir typed katmanda birleştiren **Süper Eklenti 2.0 (Unified Super Hub)** mimarisine dönüştürülmüştür.

```
                           [ CloudStream Kullanıcı Arayüzü / TV Ekranı ]
                                                │
                                                ▼
                                    [ CloudStreamHub v42 ]
                                                │
            ┌───────────────────────────────────┼───────────────────────────────────┐
            ▼                                   ▼                                   ▼
 [ UnifiedCatalogEngine ]            [ HubSingleCardMerger ]             [ HubSettingsManager ]
   ├── 16 Birleşik Kategori            ├── Eşleştirme Motoru               ├── D-Pad TV Navigasyonu
   ├── Sayfalama & Sonsuz Kaydırma     ├── Güven Skoru (IMDb/TMDB/Yıl)     ├── Kaydet / İptal / Sıfırla
   ├── HubCatalogCache (LRU + TTL)     ├── Yanlış Birleştirme Koruması    ├── Sağlayıcı Önceliklendirme
   └── Asenkron Eşzamanlılık (Limit: 5)├── Çoklu Kaynak (Multi-Source)     └── Dil Tercihleri
            │                                   │                                   │
            └───────────────────────────────────┼───────────────────────────────────┘
                                                ▼
                              [ ProviderCapabilityMatrix & Registry ]
                                                │
       ┌────────────────────────┬───────────────┴───────────────┬────────────────────────┐
       ▼                        ▼                               ▼                        ▼
 [ Film Sağlayıcıları ]  [ Dizi Sağlayıcıları ]        [ Arşiv / Kült ]         [ P2P / Torrent ]
   • FilmMakinesi          • Dizilla                     • StarTV (Yerli)         • YTS (Torrentio)
   • HDFilmCehennemi       • Dizigecesi                  • YesilCamTv (Klasik)    • BlenderOpenMovies
   • FullHDFilmizlesene    • DiziYou                     • BelgeselX (Belgesel)
   • SetFilmIzle           • DiziKorea                   • RareFilmm (Dünya)
   • HDFilmDelisi          • WebDramaTurkey              • SinemaCX
   • JetFilmIzle           • DiziIzle (Kısmi)            • KultFilmler
```

---

## 2. 16 Birleşik Kategori ve Eşleme Kuralları

CloudStreamHub v42, kullanıcılara dağınık sağlayıcı sekmeleri yerine standartlaştırılmış 16 birleşik kategori sunar. Sağlayıcıların özgün kategori adları `ProviderCapabilityMatrix` tarafından analiz edilerek bu 16 kategoriye dinamik olarak eşlenir.

| # | Kategori ID | Görünen Başlık (TR) | Hedef İçerik Türü | Besleyen Başlıca Sağlayıcılar | Eşleme Mantığı ve Filtre |
| :-: | :--- | :--- | :--- | :--- | :--- |
| 1 | `ALL` | **Tüm İçerikler** | Karma (Tümü) | 26 Aktif Sağlayıcı | Sağlayıcıların ana sayfa ve öne çıkan içerik havuzu. |
| 2 | `MOVIES` | **Filmler** | Film | FilmMakinesi, HDFilmCehennemi, FullHDFilmizlesene, SetFilmIzle, HDFilmDelisi, KultFilmler, SinemaCX, JetFilmIzle, YTS | Yerli/yabancı sinema filmleri, 1080p/4K akışlar. |
| 3 | `SERIES` | **Diziler** | Dizi | Dizilla, Dizigecesi, DiziYou, SetFilmIzle, HDFilmCehennemi, FilmMakinesi, DiziIzle | Yabancı diziler, sezon/bölüm hiyerarşisi. |
| 4 | `TURKISH_SERIES` | **Türk Dizileri** | Dizi | StarTV, Dizigecesi, DiziYou, SetFilmIzle, HDFilmCehennemi | Güncel ve popüler yerli televizyon dizileri. |
| 5 | `NOSTALGIC_SERIES` | **Eski / Nostaljik Türk Dizileri** | Dizi / Arşiv | StarTV Arşivi, YesilCamTv | 1980-2015 arası klasik Türk dizileri. |
| 6 | `CARTOONS` | **Çizgi Filmler** | Animasyon / Kısa | FilmMakinesi, HDFilmCehennemi, CloudStreamHub | Çizgi filmler ve animasyonlu çocuk içerikleri. |
| 7 | `ANIMATED_SERIES` | **Çizgi Diziler** | Animasyon / Dizi | CloudStreamHub, DiziYou, Dizilla | Sürekli bölümlü batı animasyon dizileri. |
| 8 | `ANIMATION_MOVIES` | **Animasyon Filmleri** | Animasyon / Film | FilmMakinesi (Genişletildi), HDFilmCehennemi (Genişletildi), SetFilmIzle | Uzun metraj Disney, Pixar, DreamWorks, anime filmleri. |
| 9 | `ANIME` | **Anime** | Anime / Dizi / Film | AnimeciX (Kısmi), Animeler (Kısmi), CloudStreamHub | Japon yapımı animasyon dizi ve filmleri. |
| 10 | `DOCUMENTARY` | **Belgeseller** | Belgesel | BelgeselX, RareFilmm | Doğa, bilim, tarih ve biyografi belgeselleri. |
| 11 | `YESILCAM` | **Yeşilçam** | Arşiv Film | YesilCamTv, SinemaCX | 1950-1990 arası klasik Türk sineması. |
| 12 | `TURKISH_DUB` | **Türkçe Dublaj** | Film / Dizi | HDFilmDelisi, SetFilmIzle, FullHDFilmizlesene, Dizilla, HDFilmCehennemi, FilmMakinesi | Doğrulanmış Türkçe ses parçalı içerikler. |
| 13 | `TURKISH_SUB` | **Türkçe Altyazı** | Film / Dizi | Dizigecesi, DiziYou, KultFilmler, FilmMakinesi, HDFilmCehennemi, SinemaCX | Türkçe altyazı desteği kesinleşmiş içerikler. |
| 14 | `NEW_RELEASES` | **Yeni Eklenenler** | Karma | FilmMakinesi, HDFilmCehennemi, SetFilmIzle, Dizigecesi, DiziYou | Son 24-72 saatte eklenen en güncel yayınlar. |
| 15 | `POPULAR` | **Popüler İçerikler** | Karma | Dizilla, HDFilmDelisi, FilmMakinesi, HDFilmCehennemi | İzlenme ve oylama oranına göre öne çıkanlar. |
| 16 | `CONTINUE_WATCHING`| **İzlemeye Devam Et** | Karma | CloudStream Host Uygulaması | Kullanıcının yarım bıraktığı bölüm/filmler. |

---

## 3. Sağlayıcı Yetenek Matrisi (Capability Matrix)

26 aktif sağlayıcının arama, sayfalama, ana sayfa listeleme ve oynatma yetenekleri:

| Sağlayıcı Adı | Ana Sayfa Listeleme | Sayfalı Arama | Doğrudan HLS/MP4 | Çoklu Kaynak Desteği | Doğrulanmış TR Dublaj | Doğrulanmış TR Altyazı | Paging Limiti |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **CloudStreamHub** | EVET | EVET | EVET (Federasyon) | EVET (Tüm Kaynaklar) | EVET | EVET | Sınırsız (Cursor) |
| **SetFilmIzle** | EVET | EVET | EVET (FastPlay) | EVET | EVET | EVET | 50+ Sayfa |
| **Dizigecesi** | EVET | EVET | EVET (Vidmoly) | EVET | EVET | EVET | 30+ Sayfa |
| **Dizilla** | EVET | EVET (AES API) | EVET (Pichive) | EVET | EVET | EVET | 40+ Sayfa |
| **FullHDFilmizlesene** | EVET | EVET | EVET (RapidVid) | EVET | EVET | EVET | 50+ Sayfa |
| **HDFilmDelisi** | EVET | EVET | EVET (Vixolity) | EVET | EVET | EVET | 30+ Sayfa |
| **HDFilmCehennemi** | EVET | EVET | EVET (Vidmoly) | EVET | EVET | EVET | 100+ Sayfa |
| **FilmMakinesi** | EVET | EVET | EVET (TurboVid) | EVET | EVET | EVET | 80+ Sayfa |
| **DiziYou** | EVET | EVET | EVET (AES-CBC) | EVET | EVET | EVET | 30+ Sayfa |
| **StarTV** | EVET | EVET (API) | EVET (MnCDN) | HAYIR (Yerli) | EVET (Orijinal) | HAYIR | API Sayfalı |
| **YTS** | EVET | EVET (API) | EVET (Torrent) | EVET | HAYIR | EVET (Harici) | API Sayfalı |
| **YesilCamTv** | EVET | EVET | EVET (YouTube/VK) | HAYIR (Yerli) | EVET (Orijinal) | HAYIR | 20+ Sayfa |
| **BelgeselX** | EVET | EVET | EVET (Özel HLS) | HAYIR | EVET | HAYIR | 15+ Sayfa |
| **DramaDizilerim** | EVET | EVET | EVET (Dzen/VK) | EVET | HAYIR | EVET | 25+ Sayfa |
| **DiziKorea** | EVET | EVET | EVET (Fembed) | EVET | HAYIR | EVET | 20+ Sayfa |
| **WebDramaTurkey** | EVET | EVET | EVET (Dtpasn) | EVET | HAYIR | EVET | 15+ Sayfa |
| **KultFilmler** | EVET | EVET | EVET (Supervideo) | EVET | EVET | EVET | 20+ Sayfa |
| **SinemaCX** | EVET | EVET | EVET (Vidmoly) | EVET | EVET | EVET | 25+ Sayfa |
| **JetFilmIzle** | EVET | EVET | EVET (Faststream) | EVET | EVET | EVET | 30+ Sayfa |
| **RareFilmm** | EVET | EVET | EVET (OK.ru) | HAYIR | HAYIR | EVET | 20+ Sayfa |
| **BlenderOpenMovies**| EVET | EVET | EVET (PeerTube) | HAYIR | HAYIR | EVET | 10+ Sayfa |
| **DiziIzle** (Kısmi) | EVET | EVET | KISMI (404 Riski) | EVET | EVET | EVET | 20+ Sayfa |
| **AnimeciX** (Degraded)| EVET | EVET | KISMI (Cloudflare) | EVET | HAYIR | EVET | API Sayfalı |
| **Animeler** (Degraded) | EVET | EVET | KISMI (Storage 404)| EVET | HAYIR | EVET | 20+ Sayfa |
| **FourKHDHub** (Blocked)| EVET | EVET | ENGELLİ (Bot-Wall) | HAYIR | HAYIR | EVET | 10+ Sayfa |
| **SezonlukDizi** (Blocked)| EVET | EVET | ENGELLİ (Turnstile) | HAYIR | EVET | EVET | 30+ Sayfa |

---

## 4. Tek İçerik Kartı Birleştirme (Single Card Deduplication & Merging)

Farklı sağlayıcılarda aynı filmin veya dizinin bulunması durumunda CloudStreamHub, kullanıcıya tekrarlayan kartlar göstermek yerine bunları akıllı birleştirme motoru (`HubSingleCardMerger`) ile tek bir kartta toplar.

### Eşleştirme Kuralları ve Güven Skoru
1. **Birincil Kimlik Eşleşmesi (Güven = %100):** IMDb ID (`tt1234567`) veya TMDB ID eşleşmesi.
2. **Başlık + Yıl Eşleşmesi (Güven = %95):** Normalize edilmiş başlık (Türkçe karakter düzeltme, noktalama temizliği) ve ±1 yapım yılı toleransı.
3. **Alternatif Başlık Eşleşmesi (Güven = %90):** Orijinal İngilizce isim ve Türkçe dağıtım adı çapraz eşleşmesi.
4. **Yanlış Birleştirme Önleme (Anti-False-Merge):**
   - Dizi ile film içerikleri kesinlikle birleştirilmez (`TvType` ayrımı zorunludur).
   - Yıl farkı 2'den büyükse veya bölüm sayısı uyuşmuyorsa birleştirme reddedilir ve ayrı kart olarak korunur.
   - Belirsiz eşleşmeler tek karta zorlanmaz; kullanıcının arama sonuçlarında bağımsız kart olarak gösterilir.

### Çoklu Kaynak Sunumu (Multi-Source Stream Aggregation)
Birleştirilen kart tıklandığında:
- Detay sayfasında TMDB yüksek çözünürlüklü afişi, konusu, oyuncu kadrosu ve IMDb puanı gösterilir.
- Oynatma başlatıldığında veya kaynak seçimi açıldığında, eşleşen tüm sağlayıcıların bağlantıları toplanır:
  ```
  [SetFilmİzle] FastPlay (1080p - Türkçe Dublaj)
  [FilmMakinesi] TurboVid (1080p - Türkçe Altyazı)
  [HDFilmCehennemi] Vidmoly (720p - Türkçe Dublaj)
  [FullHDFilmizlesene] RapidVid (1080p - Dual Ses)
  [YTS] Torrentio P2P (1080p / 4K - Orijinal + TR Altyazı)
  ```
- Kullanıcı dilerse varsayılan oynatma motorunun seçtiği en yüksek kaliteli Türkçe kaynağı doğrudan başlatabilir, dilerse manuel olarak başka bir sağlayıcıyı seçebilir.

---

## 5. Sayfalama, TTL ve Performans Yönetimi

Android TV cihazlarının düşük RAM (1.5 GB - 2 GB) ve sınırlı CPU kaynakları dikkate alınarak katalog ve ağ mimarisi optimize edilmiştir:

1. **Kademeli Sayfalama (Progressive Paging):**
   - Ana sayfada her kategori için ilk aşamada yalnızca 20 kart yüklenir.
   - Kullanıcı sağa doğru kaydırdıkça (infinite scroll) bir sonraki 20 kart arka planda yüklenir.
2. **Eşzamanlı İstek Sınırlaması (Bounded Concurrency):**
   - Aynı anda ağa gönderilen sağlayıcı istekleri `Semaphore(5)` ile sınırlandırılmıştır. 26 sağlayıcıya aynı anda istek gönderilerek TV donanımı veya ev interneti kilitlenmez.
3. **İki Kademeli TTL Önbelleği (HubCatalogCache):**
   - **Bellek Önbelleği (Memory LRU):** En son gezilen 100 kart hafızada tutulur. Bellek baskısında otomatik temizlenir.
   - **Disk Önbelleği (Disk Cache):** Katalog sayfaları için **15 dakika TTL**, film/dizi detay sayfaları için **24 saat TTL** uygulanır.
   - Cihaz yeniden başlatıldığında önbellek bozulmaz; çevrimdışı durumda son önbellek verisi sunulur.
4. **Request Coalescing (Tekilleştirilmiş Ağ İstekleri):**
   - Aynı sağlayıcıya aynı anda giden tekrarlı istekler tek bir coroutine deferred nesnesinde birleştirilir; mükerrer ağ trafiği engellenir.

---

## 6. Kapsama Durumu Özeti

- **Toplam Modül:** 35 modül
- **Aktif Sağlayıcı Sayısı:** 26 adet
- **Tam Çalışan Sağlayıcılar:** 21 adet (`IMPLEMENTED_WORKING`)
- **Kısmi Çalışanlar:** 3 adet (`DEGRADED` - DiziIzle, AnimeciX, Animeler)
- **Kanıtlı Engelli Olanlar:** 2 adet (`BLOCKED_WITH_EVIDENCE` - FourKHDHub, SezonlukDizi)
- **Devre Dışı Tutulanlar:** 9 adet (`DISABLED_BY_CONFIG` - CizgiMax, DDizi, DiziLife, DiziMom, DiziPal, FilmHane, FilmModu, Sinewix, TurkAnime)
- **Kategori Zenginliği:** 16 birleşik kategorinin 16'sı da aktif sağlayıcı verileriyle başarıyla haritalanmıştır.
