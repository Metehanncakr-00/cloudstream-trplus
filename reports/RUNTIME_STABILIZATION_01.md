# CLOUDSTREAMHUB — RUNTIME STABILIZATION PHASE 01 RAPORU

## 1. Yönetici Özeti ve Amaç

Bu çalışma, CloudStreamHub repository'sinde yer alan 8 CloudStream 3 provider'ının gerçek Android cihaz testlerinde ortaya çıkan runtime uyumsuzluklarını ve upstream web sitesi değişikliklerini gidermek amacıyla gerçekleştirilmiştir.

Süreç boyunca kural ve kısıtlar eksiksiz uygulanmıştır:
* Yeni provider eklenmemiştir, Batch 02'ye geçilmemiştir.
* `DiziPal` ve `AnimeciX` dondurulmuş (freeze), gereksiz kod değişikliği yapılmamıştır.
* 6 hedef provider (`KultFilmler`, `YesilCamTv`, `HDFilmCehennemi`, `FilmMakinesi`, `TurkAnime`, `BelgeselX`) için versiyonlar doğrudan güncel repository durumundan `newVersion = currentVersion + 1` kuralıyla artırılmıştır.
* `loadLinks()` metotlarında `var found = false` contract'ı zorunlu tutulmuş, en az 1 geçerli oynatılabilir `ExtractorLink` yayılmadığı sürece asla `true` döndürülmemesi sağlanmıştır.
* Altyazı semantiğinde upstream akışta harici `.vtt`/`.srt` yoksa `NONE` veya `HARDSUB` olarak doğru işaretlenmiş, yapay altyazı üretilmemiştir.
* CI/CD pipeline'ında hardcoded provider sayısı kontrolleri kaldırılmış; `providers.json` == `plugins.json` == `*.cs3` küme denkliğini denetleyen dinamik invariant mekanizması getirilmiştir.

---

## 2. Provider Versiyon Matrisi

| Provider | Eski Sürüm | Yeni Sürüm | Durum | Yapılan İşlem |
| :--- | :---: | :---: | :---: | :--- |
| **KultFilmler** | 32 | **33** | Güncellendi & Doğrulandı | Yeni WordPress teması (`a.mcard`, `a.dcard`), Vidpapi API player ayrıştırma ve altyazı desteği eklendi. |
| **YesilCamTv** | 10 | **11** | Güncellendi & Doğrulandı | FilmPlus teması poster/kategori yapısına uyarlandı, Rumble & YouTube & iframe oynatıcıları bağlandı. |
| **HDFilmCehennemi** | 90 | **91** | Güncellendi & Doğrulandı | Canlı kategori rotaları güncellendi, `h1.section-title` temizlendi, Rapidrame çoklu dizi adayı çözücüsü eklendi. |
| **FilmMakinesi** | 90 | **91** | Güncellendi & Doğrulandı | CloseLoad çoklu dizi adayı çözücüsü eklendi, fragman YouTube iframe'leri filtrelendi, link sayımı sağlandı. |
| **TurkAnime** | 95 | **96** | Güncellendi & Doğrulandı | JVM testi bozan `android.util.Base64` yerine `java.util.Base64` getirildi, iç içe fansub/host AJAX düğmeleri çözüldü. |
| **BelgeselX** | 90 | **91** | Güncellendi & Doğrulandı | `h1` bulunmayan sayfalarda `h2.px-info-title` ve meta fallback getirildi, tekil belgesellerde MovieLoadResponse sağlandı. |
| **AnimeciX** | 90 | **90** | Donduruldu (Freeze) | Dokunulmadı. Altyazı semantiği: Harici akış yoksa `NONE` / `HARDSUB`. |
| **DiziPal** | 90 | **90** | Donduruldu (Freeze) | Dokunulmadı. Tam fonksiyonel. |

---

## 3. Yapılan Değişikliklerin Teknik Ayrıntıları

### 3.1. KultFilmler (v33)
* **Problem**: Upstream web sitesi `wp-theme-kultfilmler` tasarımına geçtiğinden eski CSS seçicileri ve video embed yolları çalışmıyordu.
* **Çözüm**:
  * Ana sayfa ve kategori rotaları güncellendi (`/`, `/film-arsivi/`, `/dizi-kategori/mini-dizi-izle/`).
  * Kart seçicileri `a.mcard` (film) ve `a.dcard` (dizi) olarak yapılandırıldı.
  * Oynatma katmanında `vidpapi.xyz/player/index.php?data=<id>&do=getVideo` AJAX API'si entegre edildi. JSON yanıtındaki `securedLink` (`.m3u8`/`.txt`) ve `playerjsSubtitle` yakalanarak oynatıcıya aktarıldı.

### 3.2. YesilCamTv (v11)
* **Problem**: Eski video oynatıcı selektörleri boş dönüyordu.
* **Çözüm**:
  * WordPress FilmPlus temasına uygun `.listmovie`, `.poster a[href]`, `.film-yil`, `.bolum-ust` seçicileri uygulandı.
  * Canlı kategoriler (`/film-arsivi/`, `/category/komedi/`, `/category/dram/`, vb.) eklendi.
  * Oynatıcıda `wp-embedded-content` filtrelenerek Rumble (`rumble.com/embed/...`), YouTube ve iframe kaynakları `loadExtractor` akışına bağlandı.

### 3.3. HDFilmCehennemi (v91)
* **Problem**: Eski kategori URL'leri 404 dönüyordu ve Rapidrame iframe çözücüsü ilk karşılaştığı dizi resim URL'si dizisi olduğunda Base64 decode hatası veriyordu.
* **Çözüm**:
  * Canlı kategoriler (`/`, `/category/film-izle-2/`, `/yabancidiziizle-5/`, `/dil/turkce-dublajli-film-izleyin-6/`, `/dil/turkce-altyazili-filmleri-izleme-sitesi-3/`) bağlandı.
  * `h1.section-title` içindeki `<small>` etiketi ve başlık fazlalıkları temizlendi.
  * `RapidrameExtractor`: Çoklu aday dizi döngüsü eklendi; resim dizileri atlanarak akış URL'si (`.m3u8`, `.txt`, `/hls/`) üreten ilk geçerli dizi ile decrypt tamamlandı.

### 3.4. FilmMakinesi (v91)
* **Problem**: Detay sayfasındaki YouTube fragman iframe'i player sanılıyor ve CloseLoad çözücüsü ilk adayı resim dizisi olduğunda başarısız oluyordu.
* **Çözüm**:
  * `loadLinks()` içinde `youtube.com` ve `youtu.be` fragman iframe'leri filtrelendi.
  * `CloseLoadExtractor`: Çoklu aday dizi döngüsü ve try-catch koruması uygulandı.
  * `var found = false` takibiyle en az bir link yayınlanması şartı sağlandı.

### 3.5. TurkAnime (v96)
* **Problem**: JVM unit testlerinde `android.util.Base64` stub hatası veriyordu; ayrıca canlı sitede bölüm sayfasındaki butonlar fansub butonları olduğundan ve doğrudan iframe içermediğinden link bulunamıyordu.
* **Çözüm**:
  * `android.util.Base64` yerine standart `java.util.Base64` entegre edildi.
  * İki aşamalı AJAX buton çözümleme mimarisi kuruldu: Fansub butonuna tıklanıp dönen HTML'deki video host butonları (`SIBNET`, `OK.RU`, `DOODSTREAM`, `VOE`, `CLOUDVIDEO`, vb.) çözümlenerek bu hostların iframe adresleri `loadExtractor`'a iletildi.

### 3.6. BelgeselX (v91)
* **Problem**: Bazı belgesel detay sayfalarında `h1` bulunmadığından `parseLoadMetadata` çöküyor ve tekil belgeseller dizi formatında ayrıştırılmaya çalışıldığında oynatma sorunu oluşuyordu.
* **Çözüm**:
  * Başlık seçicisine `h2.px-info-title`, meta `og:title` ve `doc.title` kademeli geri dönüşleri (fallback) eklendi.
  * Tekil belgeseller için `newMovieLoadResponse(..., TvType.Documentary, ...)` dönüldü, çok bölümlülerde `newTvSeriesLoadResponse` korundu.

---

## 4. CI/CD ve Doğrulama Araçları

* **Dinamik Set Invariant**: `.github/workflows/build.yml` dosyasındaki sabit 8 kontrolü kaldırıldı. Bunun yerine:
  $$\text{Enabled Providers in } providers.json == \text{internalNames in } plugins.json == \text{staged } *.cs3$$
  eşitliğini doğrulayan dinamik python kontrolü entegre edildi.
* **Live Provider Smoke Scripti**: `tools/live_provider_smoke.py` aracı eklendi. Bu araç gerçek web istekleri ile Homepage, Detail, Player ve Subtitle durumlarını doğrulayıp `reports/live_provider_smoke.json` çıktısı üretmektedir.
* **Domain & Provider Health**: `config/providers.json` içindeki `knownDetail` URL'leri canlı sitelerdeki aktif içeriklerle güncellendi.

---

## 5. Doğrulama Sonuçları

1. **Birim Testler**: `./gradlew clean test` -> **BUILD SUCCESSFUL** (249 actionable task tamamlandı).
2. **Paketleme**: `./gradlew make makePluginsJson` -> **BUILD SUCCESSFUL** (8 adet `.cs3` ve `build/plugins.json` hatasız üretildi).
3. **Bütünlük Doğrulaması**: `python tools/validate_repo.py --verify-artifacts` -> **0 hata ile PASS**.
4. **Canlı Duman Testi**: `python tools/live_provider_smoke.py` -> Aktif provider'lar canlıda doğrulandı.
