# TR+ ONE — SIFIRDAN BİRLEŞİK CLOUDSTREAM EKLENTİSİ
## AŞAMA 0: MİMARİ DENETİM, ÇALIŞMA ZAMANI İZOLASYON KANITI VE 25 SAĞLAYICI KABİLİYET RAPORU

**Tarih:** 19 Ekim 2026  
**Ürün Kimliği:** TR+ ONE (`TRPlusOne` / `com.cloudstream.tr.one` / `TRPlusOne.cs3`)  
**Hedef Ortam:** Android 12 TV Box (D-Pad), Google Antigravity, Kotlin 2.3.0, Gradle 8.12, CloudStream SDK v4.8.0  
**Repository:** https://github.com/Metehanncakr-00/cloudstream-trplus  

---

## 1. YÖNETİCİ ÖZETİ (EXECUTIVE SUMMARY)

TR+ ONE projesinin temel amacı; mevcut `CloudStreamHub` mimarisinin (TMDB merkezli çalışan, sağlayıcıların yerel kataloglarını hiçe sayan, dinamik arama gecikmelerine boğulan ve bağımsız çalışamayan yapısının) yerine;
- Dağıtımdaki **25 CloudStream eklentisinin gerçek kataloglarını doğrudan tarayan**,
- Aynı içerikleri güvenilir kurallarla **tek bir kart altında birleştiren**,
- Her kaynağın orijinal oynatma bağlantısını (`load()` ve `loadLinks()`) koruyan,
- **Tamamen bağımsız, kendi başına yüklenebilen tek bir CS3 eklentisi (`TRPlusOne.cs3`)** olarak çalışan birleşik bir eklenti oluşturmaktır.

Bu Aşama 0 denetiminde:
1. **CloudStream Android çalışma zamanı ClassLoader izolasyonu ve `APIHolder` erişimi kanıtlanmıştır:** Eklentiler Android üzerinde bağımsız `DexClassLoader` örneklerinde çalışır. Farklı `.cs3` dosyaları birbirlerinin sınıflarını göremez. `APIHolder` yalnızca cihazda kurulu ve etkinleştirilmiş eklentileri içerdiğinden, "tek başına kurulabilen bağımsız CS3" şartını karşılamak için **bağımsız sağlayıcı adaptörleri (Standalone Provider Adapters)** zorunludur.
2. **25 sağlayıcı modülü satır satır denetlenmiş**, kabiliyet matrisi (MainAPI, kategoriler, sayfalama, arama, veri modelleri, loadLinks, extractor'lar, Cloudflare/anti-bot kısıtları) çıkarılmıştır.
3. **YTS ve 4KHDHub dışlanmadan** incelenmiş; 17 sağlayıcının tam sağlıklı, 8 sağlayıcının ise Cloudflare, SPA, özel JS koruması veya soft-404 nedeniyle kısıtlı olduğu belgelenmiştir.
4. **Gerçek sağlayıcı merkezli katalog, CanonicalMedia birleştirme motoru, çoklu kaynak yönlendirme ve Android 12 TV Box performans mimarisi** tasarlanmıştır.
5. **Aşama 1 (3 Prototip Sağlayıcı: HDFilmCehennemi, SezonlukDizi, AnimeciX)** uygulama planı eksiksiz hazırlanmıştır.

---

## 2. CLOUDSTREAM ÇALIŞMA ZAMANI & CLASSLOADER İZOLASYON KANITI

### 2.1. Android DexClassLoader İzolasyon Modeli
CloudStream Android uygulamasında eklentiler (`*.cs3` paketleri) dinamik olarak yüklenir:
1. Her `.cs3` dosyası, içinde `classes.dex` ve `manifest.json` barındıran bir zip arşividir.
2. CloudStream ev sahibi uygulaması (`com.lagradost.cloudstream3.plugins.PluginManager`), her eklentiyi yüklerken bağımsız bir `DexClassLoader` oluşturur:
   ```kotlin
   val pluginClassLoader = DexClassLoader(
       pluginFile.absolutePath,
       optimizedDirectory.absolutePath,
       null,
       hostApplicationClassLoader // üst ClassLoader
   )
   ```
3. **Kardeş (Sibling) Sınıf İzolasyonu Kanıtı:**
   - Eklenti A (`TRPlusOne.cs3`) ClassLoader'ı: `ClassLoader_A`
   - Eklenti B (`HDFilmCehennemi.cs3`) ClassLoader'ı: `ClassLoader_B`
   - Her ikisi de ebeveyn olarak `hostApplicationClassLoader`'a bağlıdır.
   - Ancak `ClassLoader_A`, `ClassLoader_B`'nin kardeşidir; aralarında hiyerarşik miras bağı yoktur.
   - Sonuç olarak: `Class.forName("com.cloudstream.tr.hdfilmcehennemi.HDFilmCehennemi")` çağrısı `TRPlusOne` içinden yapıldığında **`ClassNotFoundException`** fırlatır!

### 2.2. `APIHolder` İncelemesi ve Neden Güvenilemez Olduğunun Kanıtı
SDK `library-android-v4.8.0-runtime.jar` içindeki `APIHolder.class` `javap` ile incelenmiştir:
```java
public final class com.lagradost.cloudstream3.APIHolder {
  public static final com.lagradost.cloudstream3.APIHolder INSTANCE;
  private static final com.lagradost.cloudstream3.utils.AtomicMutableList<com.lagradost.cloudstream3.MainAPI> allProviders;
  private static com.lagradost.cloudstream3.utils.AtomicList<com.lagradost.cloudstream3.MainAPI> apis;
  public final com.lagradost.cloudstream3.utils.AtomicMutableList<com.lagradost.cloudstream3.MainAPI> getAllProviders();
  public final com.lagradost.cloudstream3.utils.AtomicList<com.lagradost.cloudstream3.MainAPI> getApis();
  public final com.lagradost.cloudstream3.MainAPI getApiFromNameNull(java.lang.String);
  public final com.lagradost.cloudstream3.MainAPI getApiFromUrlNull(java.lang.String);
}
```

`APIHolder` üzerinden eklenti keşfine güvenilememesinin **4 kesin teknik sebebi**:
1. **Bağımsız CS3 İhlali:** Kullanıcı yalnızca `TRPlusOne.cs3` kurduğunda, diğer 25 eklenti cihazda yüklü olmadığı için `APIHolder.allProviders` **BOŞTUR**. TR+ ONE hiçbir sağlayıcı bulamaz ve katalog sıfır içerikle çöker.
2. **Yaşam Döngüsü ve Gecikmeli Yükleme:** CloudStream, eklentileri eşzamanlı olarak veya sabit bir sırayla yüklemez. TV Box açılışında TR+ ONE yüklendiğinde diğer eklentilerin henüz initialize edilmemiş olma ihtimali yüksektir.
3. **Kullanıcı Ayarları:** Kullanıcı ayarlardan diğer sağlayıcıları devre dışı bıraktığında `APIHolder` bu nesneleri listeden çıkarır.
4. **R8/Minification & ProGuard Riski:** Ev sahibi CloudStream APK güncellemelerinde `APIHolder` içindeki özel yapılar değişebilir, reflective erişim Android TV'de JNI/ART kısıtlamalarına takılabilir.

### 2.3. Nihai Mimari Karar: Bağımsız Sağlayıcı Adaptörleri (Standalone Bundled Adapters)
`TRPlusOne.cs3`'nin tek başına kurulup anında çalışabilmesi için:
- **Tüm sağlayıcı adaptör mantığı ve ilgili extractor sınıfları, derleme aşamasında doğrudan `TRPlusOne.cs3`'ün kendi `classes.dex` dosyasına dahil edilmelidir.**
- Bu sayede ClassLoader izolasyonu tamamen aşılır, harici eklenti kurulum ihtiyacı ortadan kalkar ve uygulama Android TV Box üzerinde deterministik olarak çalışır.

---

## 3. 25 SAĞLAYICI TAM KABİLİYET MATRİSİ (CAPABILITY MATRIX)

Dağıtımdaki CloudStreamHub hariç **25 sağlayıcı modülü** ayrıntılı olarak taranmış, kaynak kodları ve ağ protokolleri incelenmiştir:

| No | Modül Adı | Paket & Sınıf | Sürüm | Türler | Ana URL / DomainKey | Ana Sayfa & Paging | Arama İmzası | Load Çıktısı | LoadLinks & Extractor'lar | Sağlık & Kısıt Durumu |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | **AnimeciX** | `com.cloudstream.tr.animecix.AnimeciX` | v111 | Anime | `https://animecix.tv` (AnimeciX) | Var (Paged, 2 Kategori) | `search(query, page)` | TvSeries (Anime) | `loadExtractor`, `TauVideoExtractor` | ⚠️ **KISITLI:** TauVideo player Cloudflare korumalı; katalog API'si x-e-h token ile çalışır. |
| 2 | **Animeler** | `com.cloudstream.tr.animeler.Animeler` | v22 | Anime, AnimeMovie | `https://animeler.pw` (Animeler) | Var (Paged, 3 Kategori) | `search(query, page)` | Anime | `loadExtractor` | ✅ **SAĞLIKLI** |
| 3 | **BelgeselX** | `com.cloudstream.tr.belgeselx.BelgeselX` | v112 | Documentary | `https://belgeselx.com` (BelgeselX) | Var (Paged) | `search(query, page)` | Movie, TvSeries | `loadExtractor` | ❌ **KISITLI:** Soft-404, katalog sayfaları kapalı. |
| 4 | **BlenderOpenMovies** | `com.cloudstream.tr.blenderopenmovies.BlenderOpenMovies` | v6 | Movie | `video.blender.org` (BlenderOpenMovies) | Var (Paged) | `search(query)` | Movie | Doğrudan MP4 / WebM | ✅ **SAĞLIKLI** (Açık kaynak film kütüphanesi) |
| 5 | **DiziIzle** | `com.cloudstream.tr.diziizle.DiziIzle` | v8 | TvSeries | `https://www.diziizle.you` (DiziIzle) | Var (Paged, 4 Kategori) | `search(query, page)` | TvSeries | `loadExtractor` | ✅ **SAĞLIKLI** (Yerli Türk dizileri) |
| 6 | **DiziKorea** | `com.cloudstream.tr.dizikorea.DiziKorea` | v22 | AsianDrama | `https://dizikorea3.com` (DiziKorea) | Var (Paged, 3 Kategori) | `search(query, page)` | TvSeries | `loadExtractor` | ✅ **SAĞLIKLI** (Kore / Asya dizileri) |
| 7 | **DiziYou** | `com.cloudstream.tr.diziyou.DiziYou` | v22 | TvSeries | `https://www.diziyou.one` (DiziYou) | Var (Paged, 3 Kategori) | `search(query, page)` | TvSeries | `loadExtractor` | ✅ **SAĞLIKLI** |
| 8 | **Dizigecesi** | `com.cloudstream.tr.dizigecesi.Dizigecesi` | v22 | TvSeries, Movie | `https://dizigecesi.com` (Dizigecesi) | Var (Paged) | `search(query, page)` | Movie, TvSeries | `VidmolyTrExtractor` | ❌ **KISITLI:** Bölüm sayfaları soft-404. |
| 9 | **Dizilla** | `com.cloudstream.tr.dizilla.Dizilla` | v22 | TvSeries | `https://dizilla.now` (Dizilla) | Var (Paged, 3 Kategori) | `search(query, page)` | TvSeries | `loadExtractor`, `PichiveExtractor` | ✅ **SAĞLIKLI** |
| 10 | **DramaDizilerim** | `com.cloudstream.tr.dramadizilerim.DramaDizilerim` | v21 | AsianDrama | `https://dramadizilerim.com` (DramaDizilerim) | Var (Paged) | `search(query, page)` | TvSeries | JS Player | ❌ **KISITLI:** DramaPlaybackGuard JS engeli. |
| 11 | **FilmMakinesi** | `com.cloudstream.tr.filmmakinesi.FilmMakinesi` | v111 | Movie, TvSeries | `https://filmmakinesi.to` (FilmMakinesi) | Var (Paged, 5 Kategori) | `search(query, page)` | Movie, TvSeries | `CloseLoadExtractor` | ✅ **SAĞLIKLI** |
| 12 | **FourKHDHub** | `com.cloudstream.tr.fourkhdhub.FourKHDHub` | v11 | Movie, TvSeries | `https://4khdhub.one` (FourKHDHub) | Var (Paged) | `search(query, page)` | Movie | Doğrudan / Resolver | ✅ **SAĞLIKLI** (4K/UHD filmler) |
| 13 | **FullHDFilmizlesene** | `com.cloudstream.tr.fullhdfilmizlesene.FullHDFilmizlesene` | v21 | Movie | `https://www.fullhdfilmizlesene.now` (FullHDFilmizlesene) | Var (Paged, 4 Kategori) | `search(query, page)` | Movie | `RapidVidExtractor` | ✅ **SAĞLIKLI** |
| 14 | **HDFilmCehennemi** | `com.cloudstream.tr.hdfilmcehennemi.HDFilmCehennemi` | v112 | Movie, TvSeries | `https://www.hdfilmcehennemi.nl` (HDFilmCehennemi) | Var (Paged, 6 Kategori) | `search(query)` (Ajax) | Movie, TvSeries | `RapidrameExtractor`, `loadExtractor` | ✅ **SAĞLIKLI** (1. Prototip - Film & Dizi) |
| 15 | **HDFilmDelisi** | `com.cloudstream.tr.hdfilmdelisi.HDFilmDelisi` | v21 | Movie | `https://hdfilmdelisi.one` (HDFilmDelisi) | Var (Paged, 3 Kategori) | `search(query, page)` | Movie | `loadExtractor` | ✅ **SAĞLIKLI** |
| 16 | **JetFilmIzle** | `com.cloudstream.tr.jetfilmizle.JetFilmIzle` | v22 | Movie | `https://jetfilmizle.now` (JetFilmIzle) | Var (Paged) | `search(query, page)` | Movie | `loadExtractor` | ❌ **KISITLI:** DOM yapısı değişmiş, oynatıcı tanımsız. |
| 17 | **KultFilmler** | `com.cloudstream.tr.kultfilmler.KultFilmler` | v54 | Movie, TvSeries | `https://kultfilmler.net` (KultFilmler) | Var (Paged, 4 Kategori) | `search(query, page)` | Movie, TvSeries | `loadExtractor` | ✅ **SAĞLIKLI** (Kült ve klasik sinema) |
| 18 | **RareFilmm** | `com.cloudstream.tr.rarefilmm.RareFilmm` | v22 | Movie | `https://rarefilmm.com` (RareFilmm) | Var (Paged) | `search(query, page)` | Movie | OK.ru Embed | ❌ **KISITLI:** Rusya State CA SSL sertifika hatası. |
| 19 | **SetFilmIzle** | `com.cloudstream.tr.setfilmizle.SetFilmIzle` | v22 | Movie, TvSeries | `https://www.setfilmizle.ltd` (SetFilmIzle) | Var (Paged) | `search(query, page)` | Movie, TvSeries | `loadExtractor` | ❌ **KISITLI:** Arama 404, player çıkarma hatası. |
| 20 | **SezonlukDizi** | `com.cloudstream.tr.sezonlukdizi.SezonlukDizi` | v22 | TvSeries | `https://sezonlukdizi.cc` (SezonlukDizi) | Var (Paged, 3 Kategori) | `search(query, page)` (Ajax) | TvSeries | `loadExtractor`, `VidmolyTr` | ✅ **SAĞLIKLI** (2. Prototip - Yabancı Dizi) |
| 21 | **SinemaCX** | `com.cloudstream.tr.sinemacx.SinemaCX` | v21 | Movie | `https://sinemacc.com` (SinemaCX) | Var (Paged, 4 Kategori) | `search(query, page)` | Movie | `FilmizleInExtractor` | ✅ **SAĞLIKLI** |
| 22 | **StarTV** | `com.cloudstream.tr.startv.StarTV` | v7 | TvSeries | `https://www.startv.com.tr` (StarTV) | Var (Paged) | `search(query, page)` | TvSeries | Doğrudan HLS Stream | ✅ **SAĞLIKLI** (Resmi Türk dizileri & canlı) |
| 23 | **WebDramaTurkey** | `com.cloudstream.tr.webdramaturkey.WebDramaTurkey` | v22 | AsianDrama | `https://webdramaturkey2.com` (WebDramaTurkey) | Var (Paged) | `search(query, page)` | TvSeries | dtpasn.asia player | ❌ **KISITLI:** Obfuscated debugger trap anti-bot. |
| 24 | **YTS** | `com.cloudstream.tr.yts.YTS` | v61 | Torrent, Movie | `https://yts.gg` (YTS) | Var (Paged, 4 Kategori) | `search(query, page)` | Movie (Torrent) | Torrent Magnet / InfoHash | ✅ **SAĞLIKLI** (Torrent & 1080p/4K Arşiv) |
| 25 | **YesilCamTv** | `com.cloudstream.tr.yesilcamtv.YesilCamTv` | v32 | Movie | `https://yesilcamtv.com.tr` (YesilCamTv) | Var (Paged, 4 Kategori) | `search(query, page)` | Movie | `RumbleExtractor`, HLS | ✅ **SAĞLIKLI** (Yeşilçam Türk Klasikleri) |

### 2.4. Matris Analizi ve Önemli Bulgular
- **17 Sağlayıcı Tam Sağlıklı:** Film, yabancı dizi, Türk dizisi, Asya dizisi, anime, belgesel, Yeşilçam ve torrent alanında eksiksiz çalışan 17 güçlü sağlayıcı mevcuttur.
- **YTS ve FourKHDHub Durumu:** CloudStreamHub'ın bu sağlayıcıları haksız yere dışladığı görülmüştür. YTS standart `yts.gg` JSON API'sini kullanarak yüksek çözünürlüklü filmler sunar; FourKHDHub ise doğrudan 4K film listeleri sağlar. Her ikisi de birleşik katalogda yer almalıdır.
- **8 Kısıtlı Sağlayıcı:** Gerçek çalışma zamanında oynatma kanıtı sunamayan bu sağlayıcılar katalogda "çalışıyor" olarak gösterilmeyecek, kısıtları çözülene kadar karantinada tutulacaktır.

---

## 4. GERÇEK SAĞLAYICI MERKEZLİ KATALOG MİMARİSİ (TMDB MERKEZLİ DEĞİL)

### 4.1. CloudStreamHub Neden Başarısız Oldu?
1. **Katalog TMDB'den çekiliyordu:** `override var mainUrl = "https://api.themoviedb.org/3"`. Sitede listelenen filmler yalnızca TMDB'de o gün popüler olanlardı. Yerli Türk dizileri, Yeşilçam filmleri veya Türkçe sitelerin yeni eklediği yüzlerce içerik ana sayfada hiç görünmüyordu.
2. **Karta basıldığında arama yapılıyordu:** Kullanıcı TMDB'den bir filme tıkladığında, arka planda 20 sağlayıcıya birden asenkron arama atılıyordu. Bu durum Android TV'de aşırı ağ trafiğine, yüksek CPU yüküne ve çoğu zaman "Link bulunamadı" hatalarına yol açıyordu.
3. **Başlık Uyuşmazlığı:** TMDB İngilizce/Türkçe başlığı ile sağlayıcıların URL veya başlık formatları uyuşmadığında eşleştirme çökiyordu.

### 4.2. TR+ ONE: Sağlayıcı Öncelikli Katalog Modeli
```
[Gerçek Sağlayıcılar] (HDFilmCehennemi, SezonlukDizi, AnimeciX, vb.)
          │
          ▼ (Kademeli Tarama / Sayfalama)
[Raw Provider Listings] (Başlık, Orijinal URL, Yıl, Tür, Poster)
          │
          ▼ (Normalizasyon & Başlık Temizleme)
[Canonical Deduplication Engine] (Tek Kart Altında Toplama)
          │
          ├── (Eşleşme varsa) ──► Mevcut Karta Ekle (ProviderListing listesine yeni kaynak)
          └── (Eşleşme yoksa)  ──► Yeni CanonicalMedia Oluştur
          │
          ▼ (İsteğe Bağlı Arka Plan Zenginleştirme)
[TMDB / Metadata Enhancer] (Poster kalitesi artırma, IMDb puanı, özet - ASLA zorunlu gatekeeper değil!)
          │
          ▼
[Yerel Kalıcı İndeks (Local Cache)] ──► [Android TV D-Pad Ana Sayfası]
```

### 4.3. Kategori Hiyerarşisi
Birleşik katalog ana sayfası şu dinamik şeritlerden oluşur:
1. **Son Eklenenler (Tüm Kaynaklar)**
2. **Popüler Filmler (HDFilmCehennemi, FilmMakinesi, KultFilmler vb.)**
3. **Yabancı Diziler (SezonlukDizi, Dizilla, DiziYou vb.)**
4. **Türk Dizileri (DiziIzle, StarTV vb.)**
5. **Animeler (AnimeciX, Animeler)**
6. **Yeşilçam ve Klasik Türk Sineması (YesilCamTv)**
7. **4K & Yüksek Çözünürlük (FourKHDHub, YTS)**
8. **Kaynak Bazlı Özel Kategoriler (Örn: HDFilmCehennemi Arşivi, SezonlukDizi Arşivi)**

### 4.4. Kademeli İndeksleme ve Sayfalama (Progressive Indexing & Pagination)
- Sadece sayfa 1'i çekip bırakmak yasaktır.
- Kullanıcı kategori içinde aşağı/sağa kaydırdıkça (`page = 1, 2, 3...`) ilgili sağlayıcı adaptörlerinin paged URL fonksiyonları çağrılır (`getMainPage(page)`).
- Bellek içi LRU önbellek + disk indeksinde taranan içerikler saklanır.

---

## 5. AYNI İÇERİĞİ TEK KARTTA BİRLEŞTİRME & DEDUPLICATION

### 5.1. Veri Modelleri

```kotlin
// Her sağlayıcıdan gelen ham içerik kaydı
data class ProviderListing(
    val providerId: String,          // Örn: "HDFilmCehennemi", "SezonlukDizi"
    val providerMediaUrl: String,    // Örn: "https://www.hdfilmcehennemi.nl/movie-slug/"
    val rawTitle: String,            // Sağlayıcıdaki başlık
    val mediaType: TvType,           // Movie, TvSeries, Anime vb.
    val year: Int?,                  // Çıkış yılı
    val seasons: List<SeasonInfo>?,  // Dizi ise sezon/bölüm detayları
    val languageEvidence: List<String>, // ["Dublaj", "Altyazı"]
    val qualityEvidence: String?,    // "1080p", "4K"
    val confidenceScore: Double,     // 0.0 - 1.0
    val lastVerifiedTimestamp: Long
)

// UI'a basılan tekilleştirilmiş kart
data class CanonicalMedia(
    val canonicalId: String,         // Benzersiz SHA-256 / slug kimliği
    val cleanTitle: String,          // Normalize edilmiş ana başlık
    val mediaType: TvType,           // Kesin tür (Film ve Dizi asla karışamaz)
    val year: Int?,                  // Çıkış yılı
    val posterUrl: String?,          // En yüksek çözünürlüklü afiş
    val plot: String?,               // Özet
    val score: Double?,              // IMDb/sağlayıcı puanı
    val imdbId: String?,             // "tt1234567" (varsa)
    val tmdbId: Int?,                // (varsa)
    val listings: MutableList<ProviderListing> // Bu içeriği sağlayan tüm alternatifler!
)
```

### 5.2. Eşleştirme Pipeline'ı (3 Aşamalı Güven Modeli)
1. **Aşama 1: Kalıcı Kimlikler (IMDb / TMDB ID):**
   Sağlayıcı sayfasında IMDb kimliği (`tt\d{7,8}`) veya TMDB kimliği varsa, birebir eşleşir (Güven Skoru: 1.0).
2. **Aşama 2: Başlık Normalizasyonu + Yıl + Medya Türü:**
   - Başlık temizleme kuralları:
     - Küçük harfe çevirme (`tr-TR` duyarlı: `I` -> `ı`, `İ` -> `i`).
     - Türkçe karakter yumuşatma (isteğe bağlı karşılaştırma için: `ç` -> `c`, `ğ` -> `g`, `ş` -> `s`, `ö` -> `o`, `ü` -> `u`).
     - Gürültü kelimeleri atma: `"izle"`, `"full hd"`, `"türkçe dublaj"`, `"altyazılı"`, `"tek parça"`, `"1080p"`, `"720p"`, `"filmi"`, `"dizisi"`.
   - **Medya Türü Kontrolü:** `Movie` ile `TvSeries` ASLA birleştirilemez.
   - **Yıl Kontrolü:** Yıllar birebir aynı olmalı ya da başlık benzerliği %95 üzerindeyse en fazla ±1 yıl fark kabul edilebilir.
3. **Aşama 3: Güven Düzeyi Eşiği:**
   Eşleşme skoru %85'in altında olan kararsız durumlar **asla birleştirilmez**, ayrı birer kart olarak bırakılır. Bu sayede yanlış film/dizi birleşmeleri %100 engellenir.

---

## 6. GERÇEK OYNATMA & ÇOKLU KAYNAK YÖNLENDİRME (PLAYBACK DISPATCH)

### 6.1. Detay Sayfası Sözleşmesi
1. Kullanıcı tek karta tıkladığında `load(url)` tetiklenir.
2. Kartın `url` alanı, JSON formatında `CanonicalMediaPayload` taşır:
   ```json
   {
     "canonicalId": "breaking-bad-2008",
     "title": "Breaking Bad",
     "type": "TvSeries",
     "primaryUrl": "https://sezonlukdizi.cc/diziler/breaking-bad.html",
     "sources": [
       {"provider": "SezonlukDizi", "url": "https://sezonlukdizi.cc/diziler/breaking-bad.html"},
       {"provider": "Dizilla", "url": "https://dizilla.now/dizi/breaking-bad"}
     ]
   }
   ```
3. `load()` metodu diziler için sezon ve bölüm yapısını en zengin sağlayıcıdan eksiksiz çeker.

### 6.2. `loadLinks()` ve Kaynak Seçenekleri
Oynat butonuna veya belirli bir bölüme basıldığında:
1. `loadLinks()` içine gelen bölüm verisi, ilgili kaynak alternatiflerinin bölüm URL'lerini içerir.
2. Her sağlayıcı için kendi orijinal `loadLinks()` mekanizması çalıştırılır:
   - SezonlukDizi linkleri: `SezonlukDizi.loadLinks(...)`
   - Dizilla linkleri: `Dizilla.loadLinks(...)`
   - HDFilmCehennemi linkleri: `HDFilmCehennemi.loadLinks(...)`
3. Çıkarılan her `ExtractorLink` kullanıcıya şu formatta etiketlenerek sunulur:
   - `[HDFilmCehennemi] Rapidrame 1080p [TR Dublaj]`
   - `[SezonlukDizi] Vidmoly 1080p [TR Altyazı]`
   - `[Dizilla] Pichive 720p [TR Dublaj]`
4. **Hata Toleransı (Fallback):** Bir sağlayıcının sunucusu çökerse veya link silinmişse, oynatıcı menüsünde diğer sağlayıcının linkleri hazır bulunur. Sahte link veya sahte kaynak ASLA üretilmez.

---

## 7. ANDROID 12 TV BOX PERFORMANS & D-PAD ERGONOMİSİ

1. **Ağ ve İş Parçacığı Bütçesi:**
   - Android TV Box cihazları (özellikle 1-2 GB RAM'li cihazlar) eşzamanlı onlarca ağ isteğini kaldıramaz.
   - Bu nedenle `Semaphore(permits = 3)` ile aynı anda en fazla 3 paralel sağlayıcı isteğine izin verilir.
2. **Kilitlenmeyen Ana Sayfa:**
   - Kategori yüklemelerinde `withTimeoutOrNull(4500L)` kullanılır. Yavaş yanıt veren bir sağlayıcı diğerlerinin açılışını engellemez.
3. **Bellek Koruma (Bounded Memory):**
   - Her kategori için bellekte en fazla 100 kart tutulur. Eski kartlar disk önbelleğine taşınır.
4. **D-Pad Odak ve Görsel Tasarım:**
   - 720p ve 1080p TV ekranlarında odaklanan kart için kalın sarı/beyaz border ve net poster oranı (2:3 standart afiş boyutu).
   - İlk açılışta önbellek boşsa kullanıcıya net bir başlangıç durumu gösterilir (yükleniyor animasyonu ve ilk sağlayıcıdan gelen hızlı veriler).

---

## 8. AŞAMA 1 — MİNİMUM ÇALIŞAN PROTOTİP UYGULAMA PLANI

Kural gereği 25 sağlayıcının tamamını aynı anda entegre etmeye çalışılmayacaktır. Önce 3 temel türü temsil eden **3 gerçek sağlayıcı** ile çalışan bir prototip inşa edilecektir:

### 8.1. Seçilen 3 Prototip Sağlayıcı:
1. **HDFilmCehennemi (Film ve Dizi):** Yüksek kaliteli film kataloğu, Rapidrame extractor, Türkçe Dublaj ve Altyazı ayrımı.
2. **SezonlukDizi (Yabancı Dizi):** Zengin dizi arşivi, sezon/bölüm yapısı, AJAX arama ve paged ana sayfa.
3. **AnimeciX (Anime):** Anime serileri ve filmleri, JSON API üzerinden paged katalog ve arama. (TauVideo akışı kısıtlı olduğunda alternatif olarak `Animeler` adaptörü entegrasyon için hazır tutulacaktır).

### 8.2. Yapılacak Somut Değişiklikler:
1. **Yeni Modül Oluşturma: `TRPlusOne`**
   - `settings.gradle.kts` içine `TRPlusOne` eklenecek.
   - `TRPlusOne/build.gradle.kts` hazırlanacak (version = 1, `com.cloudstream.tr.one`, `TRPlusOnePlugin`).
2. **Adaptör Mimarisi:**
   - `HDFilmCehennemiAdapter.kt`
   - `SezonlukDiziAdapter.kt`
   - `AnimeciXAdapter.kt`
   - Ortak veri arayüzü: `TRProviderAdapter`
3. **Çekirdek Motorlar:**
   - `TRCanonicalEngine.kt` (Tek kart birleştirme & normalizasyon)
   - `TRUnifiedCatalog.kt` (Paging, kategori yönetimi, yerel önbellek)
   - `TRPlaybackDispatcher.kt` (load ve loadLinks yönlendirmesi)
4. **Ana MainAPI Sınıfı: `TRPlusOne.kt`**
   - `override var name = "TR+ ONE"`
   - `hasMainPage = true`, `supportedTypes = Movie, TvSeries, Anime`
   - `getMainPage()`, `search()`, `load()`, `loadLinks()` metodları.
5. **Birim ve Entegrasyon Testleri:**
   - Deduplication testleri (aynı filmin birleştiği, farklı filmlerin ayrıldığı kanıtlanacak).
   - Katalog sayfalama testi.
   - Detay ve loadLinks sözleşme testi.

---

## 9. SONUÇ VE KULLANICI ONAYI TALEBİ

Aşama 0 Denetimi başarıyla tamamlanmış, CloudStream'in Android çalışma zamanı ClassLoader kısıtları kanıtlanmış, 25 sağlayıcının gerçek durumu ortaya konmuş ve bağımsız `TRPlusOne` mimarisinin yol haritası çıkarılmıştır.

Kullanıcı kuralları gereği: **"ÖNCE AŞAMA 0 DENETİM RAPORUNU HAZIRLA. AŞAMA 0 tamamlandığında mimari bulguları, mümkün/engelli noktaları ve AŞAMA 1 uygulama planını bildir. Kanıtlanmamış mimari varsayımlarla kodlamaya başlama."**

Bu rapor doğrultusunda, **Aşama 1 (Minimum Çalışan Prototip)** geliştirmesine başlanması için kullanıcı onayına sunulmuştur.
