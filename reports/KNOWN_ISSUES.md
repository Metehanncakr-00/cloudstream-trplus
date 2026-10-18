# Bilinen sınırlar ve kalan işler

Bu liste kapsamı daraltmaz; kanıtlanmamış veya henüz tamamlanmamış ana sözleşme maddelerini görünür tutar. Başlangıç envanteri `BASELINE.md`, güncel test kanıtları `CONTINUATION_TEST_RESULTS.md` içindedir.

## Oynatma / dil — MANUAL_REQUIRED

- `adb devices -l` boş: The Office otomatik kaynak/track seçimi, gerçek Türkçe konuşma, Android 12 ve TV D-pad davranışı cihazda doğrulanmadı. HLS/DASH LANGUAGE metadata'sı, segment erişimi ve duyulan dil farklı kanıt katmanlarıdır.
- DiziYou The Office US S01E01 için normal ve `_tr` HLS/ilk segment erişilebilir; hiçbir audio-language metadata kanıtı bulunmadı. `_tr` veya `Dublaj` adı tek başına Türkçe doğrulaması değildir.
- AAC/AC3/EAC3/Opus ve çok kanallı ses için cihaz/host/çıkış kombinasyonları denenmedi. Player codec'i değiştirildiği veya Android 12 uyumluluğu sağlandığı iddia edilmiyor.

## Başlangıç toolchain uyarısı

- Başlangıç paket derlemesi başarıyla sonuçlandı, fakat AGP 8.7.3'ün D8/R8 8.7.18 sürümü Kotlin 2.3 metadata'sı için tekrar eden `Unexpected error during rewriting of Kotlin metadata` uyarıları üretti.
- Kaynak: `reports/evidence/baseline-sdk35-gradle.log`. Resmî uyumluluk kaynağı: <https://developer.android.com/build/kotlin-support>. İnceleme anındaki tabloda Kotlin 2.3 için R8 8.13.191 listeleniyor; mevcut 8.7.18 bu sürümden eski.
- Bu uyarılar kullanıcıya saklanmaz. Yalnız uyarıyı susturmak için compiler/lockfile düşürülmez veya metadata denetimi kapatılmaz. Derleme PASS, runtime metadata/serialization/player uyumluluğunun kanıtı değildir. Toolchain değişikliği yapılırsa kilitler ve dar/genel regresyon ayrıca doğrulanmalıdır.

## Git / dağıtım

- Özgün klasör `.git` içermiyordu; Git tabanı içerik karşılaştırmasıyla çözüldü. Upstream `0beda1f3e445e50d86576111f6b1879287a3d777`, 445 yedek dosyasının 441'iyle byte-identical; diğer dört dosyada yalnız satır sonu farkı var. Çalışma kaynakları checkout edilmeden `.git`, `v36-baseline` ve `trplus/development` kuruldu; yeni commit yok. Sürüm kapısı bu açık tabana karşı koşulmalı; uzak hedef main bunun yerine kullanılmamalıdır.
- Hedef deponun mevcut `builds/repo.json` ve `builds/plugins.json` adresleri HTTP 200/geçerli JSON döndü. Bu, burada yeni derlenen paketlerin yayımlandığı veya uzak hash/sürümlerle aynı olduğu anlamına gelmez. Yerel yeni paketler için yayın yapılmadı.
- `.github/workflows/build.yml`, `main` push ile otomatik yayın içerir. Kullanıcı açıkça yasakladığı için push, workflow dispatch, release veya başka yayın mutasyonu yapılmaz.

## Sağlayıcı kapsamı / yeni entegrasyon

- Başlangıçta 25 etkin sağlayıcı derlenebilir; bu 25 sağlayıcının katalog/arama/oynatmasının canlı doğrulandığı anlamına gelmez.
- DiziYou ilk sağlayıcıdır; teşhis ve düzeltme/test döngüsü tamamlanmadan diğer sağlayıcılara toplu parser değişikliği yapılmaz. DiziYou arama sonuç kirliliği ve mevcut year/plot selector kaybı ek teşhislerdir; henüz onarıldığı iddia edilmez.
- Diğer 24 etkin sağlayıcının güncel canlı katmanları, devre dışı kaynakların onarımı, örnek içeriklerin tümü ve mutlak bölüm numarası senaryoları tamamlanmadı.
- Diğer 24 kaynak için HTTP-only helper ölçümü yapıldı: 11 yalnız HTTP-semantic pass, 10 kısmi, 1 config/2 tool destek sınırı. Bunlar runtime onarım/oynatma kabulü değildir; ayrıntı `HTTP_PROVIDER_STATUS.md` içindedir.
- Yeni kaynaklar için bakım/provenans/entegrasyon izni ayrıca doğrulanmalıdır. Bu oturumda doğrulanmamış yeni sağlayıcı etkinleştirilmez; yeni modül eklenmesi tamamlandı gösterilmez.
- Yerel dağıtım hazırlığı ana sözleşmenin bütün özelliklerinin veya canlı sağlayıcıların tamamlandığı anlamına gelmez. Paket durum dosyası yayın/manuel oynatma sınırını korumalıdır.
