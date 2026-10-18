# CloudStream TR+ v43 — Super Hub Kritik Stabilizasyon Sürümü

**Modül:** `CloudStreamHub` (`version = 43`)  
**Hedef Cihaz:** Android 12 TV Box / Android TV / Mobil  
**Çalışma Dalı:** `trplus/v43-stabilization` *(Yalnızca yerel — GitHub'a yüklenmedi)*  
**Üretilen Eklenti Paketi:** `CloudStreamHub/build/CloudStreamHub.cs3` (ve `build/CloudStreamHub.cs3`)  
**Paket Boyutu / SHA-256:** `610,839` bayt / `0243b7f720fe06acc6d13480b910832bf2dda8949a84aa784c8367e0a3101a10`

---

## 1. v43 Sürümünün Amacı

Android 12 TV Box üzerinde yapılan gerçek cihaz testlerinde `CloudStreamHub v42` yüklüyken tespit edilen 4 kritik sorun v43'te kök nedenleriyle giderilmiştir:

1. **Uygulamanın Ciddi Şekilde Kasması:**
   - **Kök Neden 1 (Reflection & Nesne Üretim Fırtınası):** `CloudStreamProviderRegistryAdapter.getBundledProviders()` her çağrıda 25 gömülü sağlayıcı sınıfını `Class.forName().newInstance()` ile sıfırdan oluşturuyordu; `HubSingleCardMerger.toCandidate()` ise her bir katalog kartı için `getAllDiscoverableProviders()` çağırarak tek bir ana sayfa yüklemesinde ~70.000+ kez sınıf yükleme ve nesne üretimi tetikliyordu.
   - **Kök Neden 2 (Global Olmayan Semaphore & Eşzamanlı Ağ Fırtınası):** `UnifiedCatalogEngine.getCategoryPage()` içindeki `Semaphore` yerel değişkendi; CloudStream ana sayfası 16 kategoriyi paralel istediğinde eşzamanlılık sınırı satır başına uygulanıyor ve aynı anda 80+ HTTP/HTML kazıma isteği başlatılıyordu.
   - **Kök Neden 3 (Otomatik Arka Plan Tarayıcısı):** `triggerBackgroundPrewarm()` her ana sayfa açılışında otomatik tetiklenerek kullanıcı arayüzü ile aynı anda ağır katalog taraması (`crawlBatch`) yapıyordu.
   - **v43 Çözümü:** Gömülü sağlayıcı örnekleri `bundledProviderInstances by lazy` ile süreç ömrü boyunca yalnızca **1 kez** oluşturulur. Keşfedilebilir sağlayıcı listesine 30 saniyelik TTL önbelleği eklendi ve `HubSingleCardMerger.mergeCandidates()` sağlayıcı listesini parti başına tek sefer alır. `UnifiedCatalogEngine` global `Semaphore` ve `(categoryName, page)` bazlı `Mutex` tekilleştirmesi (in-flight deduplication) kullanır. Otomatik arka plan taraması (`enableBackgroundCrawler`) varsayılan olarak **KAPALI** (`false`) yapıldı.

2. **Film ve Dizi Kataloglarının Kaybolması:**
   - **Kök Neden:** Geçici ağ zaman aşımı veya boş yanıtlarda `HubCatalogCache.putUnified()` mevcut geçerli önbelleğin üzerine boş liste (`emptyList()`) yazıyordu; ayrıca `loadFromFile()` süresi dolan önbellek dosyasını yeni veri gelmeden önce diskten siliyordu (`file.delete()`).
   - **v43 Çözümü:** `HubCatalogCache.putUnified()` boş listeyle mevcut önbelleği asla ezmez. Canlı ağ sorgusu boş dönerse süresi geçmiş olsa bile son geçerli önbellek (`getUnifiedStale()`) korunur ve döndürülür. Tüm 16 kategori için güvenilir TMDB ve yerel önbellek geri dönüş (fallback) zinciri bağlandı.

3. **Bazı Kategorilerin ve Menülerin Açılmaması:**
   - **Kök Neden:** `TR_DUB` ve `TR_SUB` kategorilerinin TMDB yedek sorgusu yoktu; `CONTINUE_WATCHING` kategorisi ağır canlı sorguya düşüyordu; `CloudStreamHub.load(url)` ise yönlendirilmiş alan adlarını (redirected domain) eşleştiremediğinde veya `canonicalMedia == null` olduğunda `AggregatorLinkPayload` sarmalamasını atlıyordu.
   - **v43 Çözümü:** `findProviderForUrl()` ile normalize host ve sağlayıcı slug eşleştirmesi eklendi. `MovieLoadResponse`, `TvSeriesLoadResponse` ve `AnimeLoadResponse` yanıtları `canonicalMedia` bulunamasa bile her zaman `AggregatorLinkPayload` ile sarmalanır; TMDB detay sorgusu başarısız olduğunda `HubMediaRegistry` üzerinden çevrimdışı/önbellek detay sayfası oluşturulur.

4. **Ayarlar Ekranındaki Spinner Değerlerinin Görünmemesi:**
   - **Kök Neden:** `CloudStreamHubPlugin.kt` içindeki 4 `Spinner` (`perfSpinner`, `qualitySpinner`, `langSpinner`, `unknownAudioSpinner`) varsayılan `android.R.layout.simple_spinner_item` şablonunu açık metin/arka plan rengi olmadan kullanıyordu; Android 12 TV Box temasında koyu zemin üzerinde koyu metin (veya açık zeminde beyaz metin) oluştuğu için seçili değerler okunamıyordu.
   - **v43 Çözümü:** `HubSpinnerStylePolicy` ve `createTvSpinner()` eklendi. Tüm Spinner'lar Android TV kumandasına uygun `Spinner.MODE_DIALOG` modunda çalışır, `getView()` (`▾  ...`) ve `getDropDownView()` (`✓  ...`) metodlarında yüksek kontrastlı açık/koyu tema renkleri (`#FFFFFF` / `#1E2638` koyu mod, `#111827` / `#F3F4F6` açık mod) ve D-Pad odak kenarlığı (`#3D5AFE`) zorunlu kılındı.

---

## 2. Yerel Kurulum (Android 12 TV Box)

> **ÖNEMLİ:** Bu sürüm GitHub'a yüklenmemiştir. Yalnızca yerel `CloudStreamHub.cs3` dosyası ile test edilmelidir.

1. Bilgisayarınızdaki `CloudStreamHub\build\CloudStreamHub.cs3` (veya `build\CloudStreamHub.cs3`) dosyasını ADB veya USB bellek ile Android 12 TV Box cihazınıza aktarın.
2. ADB üzerinden doğrudan kopyalamak için:
   ```powershell
   adb push CloudStreamHub/build/CloudStreamHub.cs3 /sdcard/Download/CloudStreamHub.cs3
   ```
3. CloudStream uygulamasında eski `CloudStreamHub` (v42) eklentisini silin veya doğrudan yerel eklenti yükleme / eklenti dizini üzerinden `CloudStreamHub.cs3` (v43) dosyasını yükleyin ve uygulamayı yeniden başlatın.

---

## 3. Android 12 TV Box Manuel Test Kontrol Listesi

| # | Test Adımı | Beklenen Sonuç | Durum |
|---|---|---|---|
| 1 | CloudStreamHub v43 yüklüyken uygulamayı soğuk başlat (Cold Boot) | Arayüzde donma, kumanda gecikmesi veya ANR olmamalı; başlangıçta arka plan taraması otomatik başlamamalı | `NOT_TESTED` (Fiziksel Cihaz) |
| 2 | Ana sayfada D-Pad ile 16 kategori satırı arasında hızlıca aşağı/yukarı gezin | Satırlar akıcı kaymalı; "Popüler Filmler", "Popüler Diziler", "Türkçe Dublaj", "Türkçe Altyazı" ve "Anime" satırları boş kalmamalı | `NOT_TESTED` (Fiziksel Cihaz) |
| 3 | Bir film, bir dizi ve bir anime kartına tıklayarak detay sayfasını aç | Detay sayfası hatasız açılmalı; "Bölümler" veya "Oynat" butonu çalışmalı | `NOT_TESTED` (Fiziksel Cihaz) |
| 4 | Oynat'a basarak kaynak listesini (`loadLinks`) görüntüle | Birden fazla sağlayıcıdan (veya birincil sağlayıcıdan) kaynaklar gelmeli; zaman aşımı yaşayan sağlayıcı diğerlerini kilitlememeli | `NOT_TESTED` (Fiziksel Cihaz) |
| 5 | `CloudStreamHub` Ayarlar ekranını aç (`Genel` ve `Performans` sekmeleri) | 4 Spinner'ın tamamında (`Performans Modu`, `Varsayılan Kalite`, `Dil Tercihi`, `Bilinmeyen Dil Politikası`) seçili değer (`▾  ...`) net okunmalı | `NOT_TESTED` (Fiziksel Cihaz) |
| 6 | Kumanda (D-Pad OK) ile bir Spinner'a tıkla ve değer değiştirip **KAYDET**'e bas | Açılan diyalog listesinde tüm seçenekler (`✓  ...`) okunaklı olmalı; ayar kaydedilip tekrar açıldığında seçilen değer korunmalı | `NOT_TESTED` (Fiziksel Cihaz) |
| 7 | İnternet bağlantısı zayıfken veya kapatılıp açıldığında ana sayfayı yenile | Önceden önbelleğe alınmış film/dizi katalogları silinmemeli, boş ekran yerine önbellekteki içerikler gösterilmeli | `NOT_TESTED` (Fiziksel Cihaz) |

---

## 4. v42'ye Güvenli Geri Dönüş (Rollback) Kılavuzu

Herhangi bir sorun durumunda v42 sürümüne anında geri dönebilirsiniz:

1. **Yerel Yedek Dosyası:**
   - v42 eklenti paketi: `backups/v42-pre-v43-stabilization/CloudStreamHub-v42.cs3` (`594,831` bayt)
   - v42 kaynak kod yedeği: `backups/v42-pre-v43-stabilization/src/` ve `backups/v42-pre-v43-stabilization/build.gradle.kts`
2. **Eklenti Paketi Geri Yükleme Komutu:**
   ```powershell
   Copy-Item -Force "backups\v42-pre-v43-stabilization\CloudStreamHub-v42.cs3" "CloudStreamHub\build\CloudStreamHub.cs3"
   Copy-Item -Force "backups\v42-pre-v43-stabilization\CloudStreamHub-v42.cs3" "build\CloudStreamHub.cs3"
   ```
3. **Git Dalı Üzerinden Geri Dönüş:**
   - Tüm v43 çalışmaları `trplus/v43-stabilization` yerel dalındadır. `main` veya `builds` dallarına hiçbir commit/push yapılmamıştır.
