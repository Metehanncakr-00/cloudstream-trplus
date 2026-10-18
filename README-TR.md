# CloudStream TR+ — v36 tabanlı v39 test geliştirmesi

**Tam master sözleşmesi henüz tamamlanmadı; yayın yapılmadı.** Değiştirilmemiş v36 için 210 Kotlin testi ve 25 paket/artifact kontrolü geçti. Türkçe kanıt/canlı sıralama ve ilk sağlayıcı düzeltmesinin güncel sonuçları aşağıdaki raporlardadır. Android 12 gerçek ses, otomatik oynatma ve TV kabulü cihaz bağlanmadığı için **NOT TESTED**. Paket oluşturulması bütün sağlayıcıların çalıştığı anlamına gelmez.

**Önceki `cloudstream-trplus-final/` v37 adayı kurulum/yayın için kullanılmamalı:** ZIP denetimi, yayımlanmış Hub v38'e göre sürüm gerilemesi ve diğer paketlerde eşit sürüm/farklı hash saptadı. Bu çıktı delil olarak korunuyor; ezilmiyor. Yeni çalışma **ayrı** `cloudstream-trplus-test-v39/` test adayıdır. TV Box'taki çalışan v38/orijinal depoyu kaldırmayın; yeni adayın burada hazırlanması uzak yayın veya cihaz kabulü değildir.

Hub'ın yeni sürümü **39**, yayımlanan 38'in üzerindedir; diğer değiştirilmiş paketler de yayımlanan sürümlerinden yüksek olacak şekilde artırıldı. Bu, eski v37/v38 kodunun taşındığı anlamına gelmez. Shared core etkisi için fiziksel modüllerde sürüm kapısı korunur; devre dışı sağlayıcılar doğrulanmadan etkinleştirilmez.

Türkçe öncelik, canlı callback tesliminde manifest dil metadata'sına göre uygulanır; kaynak adı, Torrentio dil beyanı veya `_tr` URL'si doğrulanmış ses sayılmaz. Host kendi kaynak sıralamasını/otomatik seçimini uygulayabilir; **The Office gerçek otomatik seçim kabulü ve çoklu ses parçasında Türkçe seçimi cihazda henüz doğrulanmadı**. Tercih mevcut keşif zaman bütçesinin tamamlanmasını bekleyebilir; erken İngilizce adayın önce gönderilmesini önlemek için bu gecikme bilinçlidir.

## Dosyalar ve değişiklik özeti

- [Başlangıç envanteri](reports/BASELINE.md): kaynak, modül, plugin, extractor ve aggregator kayıtları.
- [İlerleme](reports/PROGRESS.md): tamamlanan/engelli/bekleyen aşamalar.
- [Başlangıç sağlayıcı matrisi](reports/PROVIDER_STATUS.md): ilk ölçümdeki derleme ve canlı katmanların tarihsel durumu; güncel derleme sonucu devam raporundadır.
- [Türkçe ses başlangıç teşhisi](reports/TURKISH_AUDIO_DIAGNOSIS.md): düzeltme öncesi kod yolunun kaydı; uygulama sonrası sonuçlarla karıştırılmamalıdır.
- [Android 12 manuel kontrol listesi](reports/ANDROID12_CHECKLIST.md).
- [Güncel test ve geliştirme sonuçları](reports/CONTINUATION_TEST_RESULTS.md), [ilk oturum testleri](reports/TEST_RESULTS.md), [kalan işler](reports/KNOWN_ISSUES.md), [değişiklik günlüğü](CHANGELOG.md).
- [Diğer 24 kaynağın HTTP katmanı](reports/HTTP_PROVIDER_STATUS.md): Python helper ölçümü; plugin/audio/playback doğrulaması değildir.
- [Güncel v39 test teslimi ve tam yollar](reports/TEST_DISTRIBUTION_V39.md); [sonraki oturum işleri](reports/NEXT_STEPS.md). Önceki `DELIVERY.md` v37 adayının tarihsel kaydıdır.
- [Güncel v39 fazları ve denetim düzeltmeleri](reports/PHASES_V39.md); [önceki ZIP denetimi](reports/cloudstream_trplus_v36_zip_audit.md).

**Önceki adayın** bağımsız gate'i 226 Kotlin / 130 Python / 25 paketti; bu sayı v39'un doğrulandığı anlamına gelmez. Güncel v39 dar/genel testleri ve yeni teslim gate'i `PHASES_V39.md` içinde ayrı kaydedilir. Dil metadata'sı, gerçek konuşma ve host otomatik seçimi farklı kabul kapılarıdır.

v39 mevcut kanıtı: **genel 259 Kotlin PASS + 1 optional live SKIP, 208 Python PASS, 26 paket**. Son kritik deadline düzeltmesi sonrası yalnız DiziYou **15** ve Hub **106** testleri/iki paketi yeniden üretildi; artifact ve yayın-snapshot kapıları geçti, bağımsız verifier PASS verdi. Yeni genel koşu/araştırma başlatılmadı. Android oynatma ve gerçek Türkçe otomatik seçim NOT TESTED.

## Kurulum ve yerel doğrulama

Yerel dağıtım aracı doğrulanmış yeni paketleri `cloudstream-trplus-test-v39/builds/`, kaynakları `main/`, filtrelenmiş kanıtları `reports/` altında toplar; ayrı branch-root `cloudstream-trplus-main.zip` ve `cloudstream-trplus-builds.zip` üretir. Eski final klasörünü ezmez. Araç bir yayın veya oynatma sertifikası değildir; `distribution-status.json` ve `release-safety.json` bunu kaydeder. Paket mevcut değilse hazırlık tamamlanmış sayılmaz.

Gereksinimler: JDK 17, Android SDK API 35, Python 3 ve Git. Yerel SDK yolu `local.properties` içinde tanımlanır; bu dosya dağıtıma girmez. Bu çalışma için doğrulanmış Git tabanı `v36-baseline` (`0beda1f3e445e50d86576111f6b1879287a3d777`), geliştirme dalı `trplus/development` oluşturuldu; yeni commit oluşturulmadı. Git metaverisi kaynak ZIP'ine girmez. Python bağımlılıklarını sanal ortama kurun; önce ilgili dar testleri, sonra genel kapıları çalıştırın:

```powershell
python tools/validate_repo.py
python tools/verify_version_bumps.py --base v36-baseline
python -m pytest -q tools/tests --ignore=tools/tests/test_upstream_intelligence.py
.\gradlew.bat testDebugUnitTest --parallel --continue
.\gradlew.bat make makePluginsJson
python tools/validate_repo.py --verify-artifacts
python tools/verify_release_safety.py --local build/plugins.json --published backups/published-builds-20261016T175412Z/plugins.json --mode test
python tools/prepare_trplus_distribution.py --repository Metehanncakr-00/cloudstream-trplus --published backups/published-builds-20261016T175412Z/plugins.json --output cloudstream-trplus-test-v39
```

`test_upstream_intelligence.py` geçici Git depolarında commit oluşturan fixture'lar içerir; mevcut commit yasağı nedeniyle koşulmamıştır. İzin verildiğinde ayrı koşulmalıdır. Kaynak ZIP'inden yeni kurulumda Git ref'leri bulunmaz; sürüm kapısı için doğrulanmış kaynak geçmişi gerekir. Mevcut çalışma klasöründe `upstream` orijinal, `origin` hedef depodur. Derlemenin geçmesi katalog veya oynatma kanıtı değildir.

Yayın snapshot'ı yedeklerle birlikte çalışma klasöründe bulunur; kaynak ZIP'ine yedekler dahil edilmez. Başka makinede `--published` için yayımlanmış deponun güvenilir, salt okunur alınmış `plugins.json` snapshot'ını ayrıca sağlayın. CLI snapshot olmadan paketlemeyi reddeder; düşük sürüm ve eşit sürüm/farklı hash hatası aşılmaz. Kayıp/yeni kimlikler varsayılan olarak reddedilir; test istisnaları otomatik seçilmez, açık ve raporlu olmalıdır.

Yerel denemede önce `builds/SHA256SUMS` ile paket hash'lerini doğrulayın; CloudStream sürümünüz dosyadan `.cs3` kurulumunu destekliyorsa test paketini buradan yükleyin. Uzak depo URL'si yalnız **zaten yayımlanmış uzak sürümü** kurar; bu oturumun yeni yerel paketini kurduğu varsayılmamalıdır:

```text
https://raw.githubusercontent.com/Metehanncakr-00/cloudstream-trplus/builds/repo.json
```

CloudStream → Ayarlar → Eklentiler → Depo Ekle üzerinden URL eklenebilir. Bu menü/cihaz akışı burada çalıştırılmadı; manuel kabul listesi ayrıca tamamlanmalıdır.

## GitHub'a yükleme ve dağıtım

Hedef: `https://github.com/Metehanncakr-00/cloudstream-trplus`.

**Kullanıcı onayı olmadan yükleme/commit/yayın yapmayın.** `.github/workflows/build.yml` artık yalnız kaynak doğrulama, derleme ve immutable test artifact'i yükleme yapar; izin `contents: read`, builds dalına commit/push/force işlemi yoktur. Main yüklemesi bu yeni workflow ile otomatik builds yayını değildir. Bu oturumda workflow da çalıştırılmaz; gelecekte gerçek yayın ayrıca onay ve tasarlanmış yayın süreci gerektirir.

Onaylı gelecekteki yüklemede main ZIP içeriği `main` dalının köküne, builds ZIP içeriği `builds` dalının köküne konur; ZIP'lerde fazladan `main/`/`builds/` üst klasörü yoktur. `.git`, yedekler, SDK, `local.properties`, cache ve gizli anahtarlar kaynak ZIP'ine girmez. Filtrelenmiş raporlar README bağlantıları için main içine de kopyalanır.

`repo.json`, Gradle'ın ürettiği `plugins.json` ve paket URL'leri hedef depoyla eşleşmelidir. Etkin kayıtlar = plugin internalName'leri = `.cs3` dosyaları koşulu, ZIP manifest plugin kimliği/sürümü/boyutu ve SHA-256 araçla kontrol edilir; metadata elle düzeltilmez. Hedef JSON URL'leri bu oturumda HTTP 200 döndü; bu yeni paketlerin uzak sürüm/hash'lerinin yayımlandığını kanıtlamaz. Hiçbir GitHub yükleme, commit veya yayın komutu bu çalışma tarafından yürütülmez.

## Regresyon ve geri dönüş

- The Office için doğru yıl/sezon/bölüm; doğrulanmış Türkçe varken İngilizcenin otomatik öne geçmemesi; Türkçe yokken alternatiflerin korunması ayrı kabul testleridir.
- Çoklu ses ve AAC/AC3/EAC3/Opus, HLS/DASH; Android 12 kumanda odağı, Kaydet/İptal ve tercih kalıcılığı cihazda sınanmalıdır.
- Çalışma klasöründeki kaynak yedeği: `C:\Users\Metehan\Desktop\cloudstream-trplus-v36\backups\cloudstream-v36-original-20261016-111427.zip`. Yedekler main/builds ZIP'lerine bilerek dahil edilmez.
- ZIP SHA-256: `ce29af53dc53552a7c810e7fefc8e446a6dd07640d7c79cb1e4aa2b27ea06c7d`.
- Değiştirilmemiş v36 paket/JUnit yedeği: `backups/v36-baseline-built-sdk35.zip`, SHA-256 `35df2ba4a7d9c4e965de9f007a66c80820fb36960da4b3d571148127af488fd6`.
- Geri dönüş için önce hash'i doğrulayın; ZIP'i **ayrı boş klasöre** açın ve içindeki `BACKUP-SOURCE-SHA256.txt` ile dosyaları kontrol edin. Mevcut çalışma klasörünü silmeyin veya üzerine körlemesine yazmayın. Yedeğin ayrı depolamada ikinci kopyasını tutmak önerilir.

## Lisans ve atıf

Orijinal lisans ve atıflar korunur: [LICENSE](LICENSE), upstream [Emre-Kahveci/CloudStreamHub](https://github.com/Emre-Kahveci/CloudStreamHub). Bu aşamada yeni sağlayıcı veya üçüncü taraf kaynak kodu eklenmemiştir. Yeni entegrasyonlarda izin/provenans kontrolü gereklidir; DRM, abonelik veya erişim kısıtlaması aşılması kapsam dışıdır.
