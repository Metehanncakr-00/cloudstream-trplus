# Türkçe ses tanısı — v36 kaynak incelemesi

Tarih: 16 Ekim 2026. **Bu belge tanı ve gerekli düzeltme sınırlarını kaydeder; hiçbir düzeltme uygulanmadı.** Canlı The Office vakası, medya ses izi veya Android otomatik seçim davranışı burada yeniden üretilmedi. Aşağıdaki kaynakla kanıtlı mekanizmalar, yanlış dil seçimine yol açabilecek ayrı katmanlardır; belirli bir oturumun tek kesin kök nedeni diye sunulmaz.

## İzlenen zincir

Eser kimliği/yıl → sağlayıcı arama eşlemesi → doğru sezon/bölüm → bağlantı callback'i → kaynak sıralaması → host otomatik kaynak seçimi → manifest/dosya ses izleri → host ses izi seçimi → cihaz ses çıkışı.

Türkçe katalog, Türkçe altyazı, `Dublaj` adı ve gerçekten Türkçe konuşma içeren ses izi ayrı şeylerdir. Altyazı resolver'ının Türkçe sonuç vermesi dublaj kanıtı değildir.

## Kaynakla doğrulanan bulgular

### 1. Canlı doğrudan callback, sonradan yapılan sıralamayı beklemiyor

`CloudStreamHub/src/main/kotlin/com/cloudstream/tr/hub/HubLinkSelection.kt:213-228`: `direct()` doğrudan `publish()` çağırır; URL tekilleştirmesinden sonra `callback(link)` hemen çalışır. Callback sırası ağ dönüş zamanından etkilenebilir; global Türkçe ses sıralama kapısı yoktur.

`CloudStreamHub/src/main/kotlin/com/cloudstream/tr/hub/CloudStreamHub.kt:266-295`: `flushCollectedLinks()` bağlantıları sonradan `StreamPrioritySorter.sortByPriority` ile sıralar ama bu listeyi yalnız torrent cache'ine verir. Önceden yapılmış canlı doğrudan callback'leri geri alamaz. Dolayısıyla yalnız cache/sorter değişikliği, ilk canlı adayın yanlış dilde gelmesini güvenilir biçimde çözmez. Host'un ilk callback'i otomatik oynattığı ise bu kaynak incelemesinden kanıtlanamaz; cihaz testi gerekir.

### 2. SmartPlay dil kanıtı değil, isim sezgisidir

`core/src/main/kotlin/com/cloudstream/tr/core/streaming/SmartPlayEngine.kt:28-78`: doğrudan VIDEO/M3U8 için `dublaj`, `türkçe ses`, `tr dub` ile `altyazı`, `tr alt` aynı +1400 ulaşım puanını alır. CDN/debrid +1500; kalite, REMUX, HDR ve ses formatı ek puanları dil tercihinin önüne geçebilir. `TR_DUB` için +500 yalnız adında `dublaj`/`türkçe ses` olduğunda uygulanır; `tr dub` burada aynı şekilde tanınmaz. `ORIGINAL`, dublaj etiketi olmayanı özgün varsayar; bilinmeyen dil de ödüllendirilebilir.

Örnek, preflight yokken: Türkçe dublaj etiketli 720p VIDEO = 1400 + 100 + 500 = 2000; dil etiketi olmayan 2160p debrid REMUX DV TrueHD Atmos 7.1 = 1500 + 500 + 400 + 300 + 350 = 3050. Bu hesap sıralama fonksiyonundaki riski gösterir; fonksiyonun canlı direct callback sırasını uyguladığı iddiası değildir.

### 3. DiziYou `_tr` adayı doğrulanmış dublaj değildir

`DiziYou/src/main/kotlin/com/cloudstream/tr/diziyou/DiziYou.kt:271-275`, bölüm kimliğinden `episodes/${itemId}_tr/play.m3u8` üretip `DiziYou Dublaj HLS` adı verir. URL kalıbı, manifestin erişilebilir olduğunu veya sesin gerçekten Türkçe olduğunu kanıtlamaz. `DiziYou/src/test/kotlin/com/cloudstream/tr/diziyou/DiziYouParserTest.kt` ad/üretim beklentisini içerir; gerçek ses dinleme testi değildir. URL, header/referrer, manifest ve ses rendition/segmenti ayrı doğrulanmalı.

### 4. Torrentio gösteriminde dil korunmuyor; kota geç Türkçe adayı silebilir

`core/src/main/kotlin/com/cloudstream/tr/core/resolvers/TorrentioResolver.kt:124-152`, kalite/video/ses formatından yeni `displayName` üretir; ham başlıktaki Türkçe/İngilizce dil işaretini bu ada taşımaz. Magnet `dn` veya filename içinde bir işaret kalması, isimden çalışan sıralayıcının bunu okuyacağı anlamına gelmez.

`CloudStreamHub/src/main/kotlin/com/cloudstream/tr/hub/HubLinkSelection.kt:148-198`, seed/release/profil odaklı puan, kalite başına 3 (2160p için 4) toplam ve origin başına 3 kota uygular. `emitted` adaylar kotayı tüketir; geç gelen başka dil adayı için yeniden seçim/geri çağırma yoktur. `HubCandidateBudget` ayrıca 96 standart/32 özellik adayı sınırı koyar. Bu kontroller dil garantisi değildir. Torrentio'daki `keepTopThreePerQuality` yardımcı fonksiyonunun varlığı tek başına canlı yolda kullanıldığına kanıt sayılmadı.

### 5. The Office US/UK ayrımı dil seçiminden önce çözülmeli

`CloudStreamHub/src/main/kotlin/com/cloudstream/tr/hub/HubMatchingEngine.kt:118-137`, yıl varsa uygular; eksik yılda güçlü başlık eşleşmesine +0.05 verir. `:162-168`, yalnız `score > highestScore` olduğunda değiştirir; eşit puanda ilk aday kalır. İki yılı eksik, aynı başlıklı The Office adayı için sonuç sırası belirleyici olabilir. Bu statik eşitlik davranışıdır; canlı sağlayıcıda gerçekten iki böyle aday geldiği doğrulanmadı. US (2005)/UK (2001), sezon/bölüm ve mümkünse TMDB/IMDb kimliği doğru eşleşmeden ses incelemesi yapılmamalı.

### 6. Eklenti, host oynatıcısının tamamını yönetmez

`CloudStreamHub/src/main/kotlin/com/cloudstream/tr/hub/CloudStreamHubPlugin.kt:84-96` profili yalnız kaynak sıralaması olarak tanımlar; `:252-271` kaydet/iptal akışında `audioPref = DebridConfig.audioPreference` korunur, bu pencerede ayrı ses tercihi seçicisi bulunmaz. Link tercih ayarı, host'un global otomatik oynatma veya çoklu ses izi seçimi değildir. Derleme bağımlılığı da host SDK'sını compile-only olarak tanımlar (`CloudStreamHub/build.gradle.kts:4-5`). Eklentinin player codec'ini/track selector'ını zorla değiştirdiği iddia edilmemeli.

## Gerekli düzeltme sınırları — henüz uygulanmadı

1. **Kimlik:** US/UK/yıl/medya türü belirsizliğinde sıraya bağlı güvenli olmayan eşleşmeyi önlemek. Eşitlik ve eksik metadata politikası açık olmalı; doğru sezon/bölüm korunmalı.
2. **Dil kanıt modeli:** Türkçe dublaj, özgün ses, Türkçe altyazı, çoklu ses ve bilinmeyeni ayırmak. Başlık/URL sezgisini düşük güvenli etiket olarak saklamak; gerçek track kanıtı olmadan `doğrulandı` yazmamak. Torrentio dil metadata'sını kaybetmemek.
3. **Seçim kapısı:** Tercihi yalnız cache'e değil canlı callback sınırına uygulamak. Sınırlı bekleme/geç aday politikası; deterministik sıralama, iptal, süre bütçesi, tekilleştirme ve kota davranışı birlikte tasarlanmalı. Türkçe yoksa alternatifler kaybolmamalı; tercih, sonsuz bekleme veya sessiz boş listeye dönüşmemeli.
4. **DiziYou:** `_tr` adayı için gerçek erişim/dil kanıtı; olmayan varyantı doğrulanmış dublaj diye etiketlememek. Kimlik bilgisi veya geçici token sabitlememek.
5. **Host sınırı:** Eklenti tercihi ile host ses izi/otomatik oynatma ayarını ayrı açıklamak. Host global tema/oynatıcı davranışını değiştirme sözü vermemek. Sağlayıcı değişikliği yapılırsa sürüm-bump ve artifact tutarlılığı kapıları uygulanmalı.

## Gerekli testler ve kabul kanıtı

- **AUTO_REQUIRED:** aynı girdiyi farklı callback zamanlarında veren yarış testi; erken EN/geç TR, cache ve taze sonuç eşdeğerliği, timeout/iptal, URL tekilleştirme, dolu kota sonrası TR adayını kapsayan testler. Mevcut `HubLinkSelectionTest.kt`, `HubCachePolicyTest.kt`, `HubSourceResolutionTest.kt` yüzeyleri genişletilmeli; yalnız sorter birim testi yeterli değil.
- **AUTO_REQUIRED:** dublaj ≠ altyazı; bilinmeyen ≠ özgün; `TR_DUB`/`ORIGINAL`/bilinmeyen tercih; dil ve kalite çatışması; Torrentio ham başlık/filename dilinin korunması; çoklu ses sınıflaması. `core/src/test/kotlin/com/cloudstream/tr/core/resolvers/TorrentResolversTest.kt` ilgili yüzeydir.
- **AUTO_REQUIRED:** yıl eksik/eşit puan The Office US/UK, doğru/yanlış yıl, film/dizi, sezon/bölüm karışması ve kimlik uyuşmazlığı. `CloudStreamHubTest.kt` ve eşleştirme kaynağı çevresinde deterministik fixture gereklidir.
- **AUTO_REQUIRED:** DiziYou TR manifest yok/erişim engelli/EN-only/TR track var fixture'ları; üretilmiş adı doğrulamak ile gerçek medya doğrulamasını ayırmak.
- **MANUAL_REQUIRED:** Android 12'de The Office otomatik seçim, alternatif seçimi, gerçek track dili ve ses çıkışı. Ayrıntılar [ANDROID12_CHECKLIST.md](ANDROID12_CHECKLIST.md).

Mevcut testlerin çalıştığı iddia edilmiyor: başlangıç Kotlin koşusu yapılandırmada engellendi. Yeni test veya çalışma zamanı düzeltmesi bu belgeleme kapsamında eklenmedi.
