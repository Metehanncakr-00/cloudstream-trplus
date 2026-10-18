# CloudStream TR+ v36 tabanlı aday — ZIP denetimi

16 Ekim 2026. Bu inceleme kaynak ve dağıtım ZIP'lerinin statik denetimidir; Android 12 gerçek oynatma testi değildir.

## Doğrulananlar

- `cloudstream-trplus-main.zip`: 399 ZIP girdisi; ZIP CRC testi başarılı.
- `cloudstream-trplus-builds.zip`: 25 `.cs3` + `plugins.json` + `repo.json` + `SHA256SUMS` (28 girdi); ZIP CRC testi başarılı.
- `main/SHA256SUMS` listesindeki 398 dosyanın tüm SHA-256 değerleri eşleşiyor.
- `builds/SHA256SUMS` listesindeki 27 dosyanın tüm SHA-256 değerleri eşleşiyor.
- `plugins.json` içindeki 25 `internalName`, `.cs3` dosya adlarıyla eşleşiyor. Tekrarlanan `internalName` yok. `fileHash`, `fileSize`, bağlantı dosya adı doğrulaması geçti.
- 25 `.cs3` paketinin ZIP bütünlüğü ve içindeki `manifest.json` okunabilirliği doğrulandı; bunların Android'de yüklendiği veya oynatıldığı anlamına gelmez.
- Kaynak ZIP'inde Kotlin, Gradle, sağlayıcı klasörleri, `LICENSE`, `README-TR.md`, testler ve CI dosyaları mevcut.

## Kritik yayın engelleri

1. **Sürüm geri gitmiş:** Yerel `CloudStreamHub.cs3` / `plugins.json` sürümü **37**; GitHub `builds` dalındaki `CloudStreamHub` **38**. Aynı eklenti kimliğiyle güncelleme/downgrade davranışı güvenilir değil.
2. **Aynı sürüm, farklı içerik:** Örneğin `AnimeciX` yerelde ve uzakta 107, `DiziYou` her ikisinde 19; ancak kaydedilen dosya hash'leri farklı. Sürüm numarası artmadan uzaktan üzerine yazma, güncelleme algılamasını bozabilir.
3. **Otomatik yayın tehlikesi:** Kaynak ZIP'indeki `.github/workflows/build.yml`, `main` dalına kod gönderildiğinde `builds` dalını `git push origin builds --force` ile güncelliyor. Bu nedenle `main` yüklemesi yalnız kaynak yayınlama değildir.
4. **Kaynak farkı:** Yerelde 25 aktif paket var; eski yayın 26 paket içeriyordu. Eski `BlenderOpenMovies` yeni manifestte bulunmuyor. Kaldırma/koruma kararı bilinçli verilmeli.
5. **Gerçek oynatma doğrulanmamış:** Türkçe/HLS dil metadata sıralaması kodda mevcut; ancak otomatik seçim ve Türkçe ses Android 12 üzerinde doğrulanmamış. Doğrudan MP4 veya dil bildirimi olmayan akışlarda dil `unknown` kalabilir. `reports/KNOWN_ISSUES.md` bu sınırları kabul ediyor.
6. **D8 uyarıları:** Kotlin 2.3 ve kullanılan eski D8/R8 metadata uyumluluk uyarıları devam ediyor. Derleme PASS, cihaz uyumluluğu PASS değildir.

## Kodda bulunan gerçek değişiklikler

- `CloudStreamHub/.../HubLinkSelection.kt`: `rankedDelivery` ve dil kanıtına dayalı callback sıralaması, torrent kaynaklarının dil/kalite önceliklerinin ayrılması.
- `core/.../ManifestLanguageEvidence.kt`: HLS AUDIO/SUBTITLES ve DASH bildirimlerinden dil kanıtı oluşturma.
- `DiziYou/.../DiziYou.kt`: altyazı yolu `/episodes/{id}/...` yerine `/subtitles/{id}/...`.
- Kodda kaynağın ismindeki `_tr` veya `Dublaj` ifadesi kesin ses kanıtı kabul edilmiyor. Bu ihtiyatlı bir seçim; Türkçe olduğu bildirilmeyen gerçek Türkçe videoları otomatik seçmeyi de engelleyebilir.

## Güvenli sonraki adım

- TV Box'taki çalışan orijinal depoyu kaldırma.
- Mevcut GitHub main/builds dallarına şimdilik ZIP yükleme.
- Dağıtım sürümünü yayınlanan sürümlerden yüksek olacak şekilde tutarlı artır; değiştirilmiş her paketin manifest sürümü ile `plugins.json` sürümü eşleşsin. Eşit sürüm/farklı hash hatasını gider.
- `main` push ile otomatik `builds --force` yayınını güvenli kontrollü işleme dönüştürmeden yayınlama.
- Yeni sürümü mümkünse yalıtılmış test deposu veya doğrudan `.cs3` test kurulumu üzerinden Android 12'de dene.
- The Office aynı bölüm için: orijinal Türkçe kaynak → yeni Süper Eklenti Türkçe otomatik seçim → Torrentio'nun alternatifte kalması → ses çıkışı → geri dönüş testi.

## Token tasarruflu düzeltme görevi

> Mevcut kodu yeniden baştan araştırma. ZIP denetiminde yeni `CloudStreamHub` sürümünün 37, GitHub'da yayımlı sürümün 38 olduğu; AnimeciX 107 ve DiziYou 19 için aynı sürümde farklı hash bulunduğu; `.github/workflows/build.yml` dosyasının main push'unda builds dalını `--force` ile yayınladığı saptandı. Önce bu yayın/sürüm engellerini dar kapsamda düzelt. Eski çalışan sürümü koru, yeni yayın onaysız yapılmasın. Sonra Android 12 manuel kabul listesi ve test deposu için ayrı çıktı hazırla. Kod/derleme testini cihaz oynatması sayma. Mevcut raporları kullan, yeniden geniş envanter çıkarma. Commit, push, yayın yapma.
