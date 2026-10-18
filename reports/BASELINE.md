# v36 başlangıç kaydı

Tarih: 16 Ekim 2026. Bu belge, düzeltme yapılmadan önceki v36 için tek başlangıç kaydıdır; tekrar edilen testlerin sonucu başlangıç sonucu yerine geçirilmemelidir. Sonraki doğrulamalar ayrı, tarihli kanıt olarak tutulmalıdır. Eski `reports/*` dosyaları canlı çalışma kanıtı olarak kullanılmadı.

## Koruma ve sınır

- CloudStreamHub sürümü: `CloudStreamHub/build.gradle.kts:1` → `version = 36`.
- Çalışma dizininde Git metaverisi yok: `git status --short` → `fatal: not a git repository (or any of the parent directories): .git`. Commit/branch/diff geçmişi doğrulanamadı; Git başlatılmadı, commit/push yapılmadı.
- Ana oturumun testlerden önce oluşturduğu kaynak yedeği: `C:\Users\Metehan\AppData\Local\Temp\opencode\cloudstream-v36-original-20261016-111427.zip`.
- ZIP SHA256: `ce29af53dc53552a7c810e7fefc8e446a6dd07640d7c79cb1e4aa2b27ea06c7d`.
- Kalıcı yedek kopyası: `backups/cloudstream-v36-original-20261016-111427.zip`; aynı SHA-256 ile doğrulandı.
- Yedek 445 özgün kaynak dosyasını içerir; `build`, `.gradle`, `__pycache__` dışarıda bırakıldı. ZIP içindeki `BACKUP-SOURCE-SHA256.txt` kaynak özetlerini içerir; CRC kontrolü geçti. Bu, derlenmiş dağıtım yedeği değildir.
- İlk eşzamanlı `Compress-Archive` denemesi dosya kilidi nedeniyle başarısız oldu; başarılı yedek sonraki Python ZIP işlemidir. Başarısız deneme başarı olarak sayılmadı.
- Belgeleme kapsamı yalnız `BASELINE.md`, `PROVIDER_STATUS.md`, `TURKISH_AUDIO_DIAGNOSIS.md`, `ANDROID12_CHECKLIST.md`; Kotlin/Gradle/config, sürümler ve önceki raporlar değiştirilmez.

## Başlangıç komutları ve gerçek sonuçları

Aşağıdaki çalıştırma sonuçları ana oturumun başlangıç ölçümüdür; belgeleme alt görevi Gradle veya canlı ağ testini yeniden çalıştırmadı.

| Kontrol | Sınıf | Başlangıç sonucu | Kanıt sınırı |
| --- | --- | --- | --- |
| `python tools/validate_repo.py` | AUTO_REQUIRED | PASS | Yapısal envanter; canlı katalog/oynatma değil |
| `.\gradlew.bat testDebugUnitTest --parallel --continue` | AUTO_REQUIRED | BLOCKED | Java 8 ile yapılandırma aşamasında JVM 11+ bağımlılık uyumsuzluğu; Android Gradle Plugin için JDK 17 gerekli. Kotlin testleri çalışmadı |
| Python `unittest` keşfi | OPTIONAL_DIAGNOSTIC | Yanlış koşucu / eksik bağımlılık | Envanter unittest'i 1 PASS; 12 modül import hatası (`pytest`/`scrapling` eksik). Pytest paketinin başarılı koşusu değildir |
| Doğru pytest koşucusu ve izole ortam | AUTO_REQUIRED | Bu kayıt anında sonuç yok | Ana oturum hazırlıyor; sonraki sonuç burada varsayılmadı |
| `make makePluginsJson` / artifact doğrulaması | AUTO_REQUIRED (dağıtım öncesi) | NOT TESTED | Başlangıçta `.cs3` ve `plugins.json` bulunmadı; paket üretimi kanıtlanmadı |
| Canlı sağlayıcı / manifest / segment | OPTIONAL_DIAGNOSTIC | NOT TESTED | Ağ testi yapılmadı |
| Android 12 telefon / TV oynatma | MANUAL_REQUIRED | NOT TESTED | Cihazda doğrulama yok; kontrol listesi ayrı belgede |

JDK 17, Android SDK ve `adb`, ana oturumun geleneksel dizin/ortam değişkeni kontrollerinde bulunmadı. Bu ifade tüm diskin tarandığı veya bağlı cihaz olmadığı iddiası değildir. Eksik ortam, kaynakların derlenemediğini kanıtlamaz; bu ortamda derleme/test kanıtı elde edilemediğini gösterir.

## Kaynak ve modül envanteri

`config/providers.json`: 36 kayıt, 25 etkin, 11 devre dışı. Fiziksel sağlayıcı modülü: 34. `TrtDizi` ve `DiziBox` kayıtları devre dışı ve modülleri yok. `core` paylaşılan kaynak alanıdır; sağlayıcı kaydı değildir. Modül bazında sürüm/domain/etkinlik için [PROVIDER_STATUS.md](PROVIDER_STATUS.md).

`settings.gradle.kts`, üst düzey `build.gradle.kts` dosyalarını keşfeder; 25 sağlayıcı dahil edilir. Mevcut dışlama listesi: CizgiMax, DDizi, DiziLife, DiziMom, DiziPal, FilmHane, FilmModu, Sinewix, TurkAnime. Bu rapor ikinci bir derleme listesi oluşturmaz.

34 fiziksel modül: AnimeciX, Animeler, BelgeselX, CizgiMax, CloudStreamHub, DDizi, DiziIzle, DiziKorea, DiziLife, DiziMom, DiziPal, DiziYou, Dizigecesi, Dizilla, DramaDizilerim, FilmHane, FilmMakinesi, FilmModu, FourKHDHub, FullHDFilmizlesene, HDFilmCehennemi, HDFilmDelisi, JetFilmIzle, KultFilmler, RareFilmm, SetFilmIzle, SezonlukDizi, SinemaCX, Sinewix, StarTV, TurkAnime, WebDramaTurkey, YTS, YesilCamTv. Her birinde `<modül>/build.gradle.kts` bulunur.

### Eklenti giriş noktaları: 34

Her dosya kendi sağlayıcısı için bir `registerMainAPI` çağrısı içerir. Kaynakta kayıt bulunması, host üzerinde yükleme/çalışma kanıtı değildir.

```text
AnimeciX/src/main/kotlin/com/cloudstream/tr/animecix/AnimeciXPlugin.kt
Animeler/src/main/kotlin/com/cloudstream/tr/animeler/AnimelerPlugin.kt
BelgeselX/src/main/kotlin/com/cloudstream/tr/belgeselx/BelgeselXPlugin.kt
CizgiMax/src/main/kotlin/com/cloudstream/tr/cizgimax/CizgiMaxPlugin.kt
CloudStreamHub/src/main/kotlin/com/cloudstream/tr/hub/CloudStreamHubPlugin.kt
DDizi/src/main/kotlin/com/cloudstream/tr/ddizi/DDiziPlugin.kt
DiziIzle/src/main/kotlin/com/cloudstream/tr/diziizle/DiziIzlePlugin.kt
DiziKorea/src/main/kotlin/com/cloudstream/tr/dizikorea/DiziKoreaPlugin.kt
DiziLife/src/main/kotlin/com/cloudstream/tr/dizilife/DiziLifePlugin.kt
DiziMom/src/main/kotlin/com/cloudstream/tr/dizimom/DiziMomPlugin.kt
DiziPal/src/main/kotlin/com/cloudstream/tr/dizipal/DiziPalPlugin.kt
DiziYou/src/main/kotlin/com/cloudstream/tr/diziyou/DiziYouPlugin.kt
Dizigecesi/src/main/kotlin/com/cloudstream/tr/dizigecesi/DizigecesiPlugin.kt
Dizilla/src/main/kotlin/com/cloudstream/tr/dizilla/DizillaPlugin.kt
DramaDizilerim/src/main/kotlin/com/cloudstream/tr/dramadizilerim/DramaDizilerimPlugin.kt
FilmHane/src/main/kotlin/com/cloudstream/tr/filmhane/FilmHanePlugin.kt
FilmMakinesi/src/main/kotlin/com/cloudstream/tr/filmmakinesi/FilmMakinesiPlugin.kt
FilmModu/src/main/kotlin/com/cloudstream/tr/filmmodu/FilmModuPlugin.kt
FourKHDHub/src/main/kotlin/com/cloudstream/tr/fourkhdhub/FourKHDHubPlugin.kt
FullHDFilmizlesene/src/main/kotlin/com/cloudstream/tr/fullhdfilmizlesene/FullHDFilmizlesenePlugin.kt
HDFilmCehennemi/src/main/kotlin/com/cloudstream/tr/hdfilmcehennemi/HDFilmCehennemiPlugin.kt
HDFilmDelisi/src/main/kotlin/com/cloudstream/tr/hdfilmdelisi/HDFilmDelisiPlugin.kt
JetFilmIzle/src/main/kotlin/com/cloudstream/tr/jetfilmizle/JetFilmIzlePlugin.kt
KultFilmler/src/main/kotlin/com/cloudstream/tr/kultfilmler/KultFilmlerPlugin.kt
RareFilmm/src/main/kotlin/com/cloudstream/tr/rarefilmm/RareFilmmPlugin.kt
SetFilmIzle/src/main/kotlin/com/cloudstream/tr/setfilmizle/SetFilmIzlePlugin.kt
SezonlukDizi/src/main/kotlin/com/cloudstream/tr/sezonlukdizi/SezonlukDiziPlugin.kt
SinemaCX/src/main/kotlin/com/cloudstream/tr/sinemacx/SinemaCXPlugin.kt
Sinewix/src/main/kotlin/com/cloudstream/tr/sinewix/SinewixPlugin.kt
StarTV/src/main/kotlin/com/cloudstream/tr/startv/StarTVPlugin.kt
TurkAnime/src/main/kotlin/com/cloudstream/tr/turkanime/TurkAnimePlugin.kt
WebDramaTurkey/src/main/kotlin/com/cloudstream/tr/webdramaturkey/WebDramaTurkeyPlugin.kt
YTS/src/main/kotlin/com/cloudstream/tr/yts/YTSPlugin.kt
YesilCamTv/src/main/kotlin/com/cloudstream/tr/yesilcamtv/YesilCamTvPlugin.kt
```

Hub'ın `CloudStreamHub/build.gradle.kts:19-42` listesindeki 22 sağlayıcı kaynağı pakete kopyalanır; `*Plugin.kt` dosyaları dışlanır. Bunlar FilmMakinesi, HDFilmCehennemi, SinemaCX, SezonlukDizi, StarTV, DiziIzle, KultFilmler, Dizilla, DiziYou, HDFilmDelisi, JetFilmIzle, DiziKorea, YesilCamTv, FullHDFilmizlesene, AnimeciX, Animeler, BelgeselX, DramaDizilerim, WebDramaTurkey, SetFilmIzle, Dizigecesi, RareFilmm'dir. Paketleme tanımı, gerçek `.cs3` içeriği doğrulaması değildir. Hub giriş noktası yalnız Hub MainAPI'sini ve TauVideo extractor'ını doğrudan kaydeder; 22 sınıfı 22 ayrı host kaydı diye saymayın.

### Özel extractor kaynakları: 10 sınıf/dosya

```text
AnimeciX/src/main/kotlin/com/cloudstream/tr/animecix/TauVideoExtractor.kt
HDFilmCehennemi/src/main/kotlin/com/cloudstream/tr/hdfilmcehennemi/RapidrameExtractor.kt
FilmMakinesi/src/main/kotlin/com/cloudstream/tr/filmmakinesi/CloseLoadExtractor.kt
core/src/main/kotlin/com/cloudstream/tr/core/extractors/CloseLoadExtractor.kt
core/src/main/kotlin/com/cloudstream/tr/core/extractors/FilmizleInExtractor.kt
core/src/main/kotlin/com/cloudstream/tr/core/extractors/PeacemakerExtractor.kt
core/src/main/kotlin/com/cloudstream/tr/core/extractors/PichiveExtractor.kt
core/src/main/kotlin/com/cloudstream/tr/core/extractors/RapidVidExtractor.kt
core/src/main/kotlin/com/cloudstream/tr/core/extractors/RumbleExtractor.kt
core/src/main/kotlin/com/cloudstream/tr/core/extractors/VidmolyTrExtractor.kt
```

FilmMakinesi CloseLoad, core CloseLoad için uyumluluk alt sınıfıdır; on bağımsız extraction motoru iddiası yoktur. Açık `registerExtractorAPI(TauVideo())` çağrıları AnimeciX ve Hub giriş noktalarındadır (iki çağrı, aynı extractor türü). Diğer sınıfların dosyada bulunması host'a kayıt edildiğini veya canlı çalıştığını göstermez.

### Android manifest ve test yüzeyleri

13 kaynak manifesti bulunur: aşağıdaki modüllerin her birinde tam yol `<modül>/src/main/AndroidManifest.xml`:

AnimeciX, BelgeselX, DiziMom, DiziPal, DiziYou, Dizilla, FilmMakinesi, HDFilmCehennemi, KultFilmler, SezonlukDizi, SinemaCX, TurkAnime, YesilCamTv.

Diğer 21 fiziksel modülde bu kaynak dosyasının bulunmaması tek başına derleme hatası değildir; üretilmiş/merge edilmiş manifest ayrıca incelenmelidir. 48 Kotlin test dosyası ve 14 Python test dosyası başlangıç envanterinde bulunur. Dosya sayısı yürütülen test sayısı değildir.

## Kabul ve belirsizlik

Yapısal doğrulama geçti; derleme ve Kotlin testleri ortamda engellendi; canlı kaynak, gerçek Türkçe ses ve Android oynatma doğrulanmadı. Hiçbir sağlayıcı bu kayıtla `IMPLEMENTED_WORKING` veya `DEAD_WITH_EVIDENCE` ilan edilmez. Sonraki otomatik doğrulama için JDK 17/uygun SDK ile aynı kaynak anlık görüntüsü kullanılmalı; Android kabulü [kontrol listesine](ANDROID12_CHECKLIST.md) göre ayrı kaydedilmelidir.

### Belgeleme sonrası kontrol (başlangıç ölçümünün yerine geçmez)

16 Ekim 2026: `python tools/validate_repo.py` yeniden PASS (0 hata). Dar belge kontrolü 36 tablo satırını, 25 BLOCKED/11 NOT TESTED derleme durumunu, 34 mevcut kaynak modülünü, belgelenen 44 Kotlin dosya yolunu ve 13 manifesti doğruladı. Dış ZIP'in SHA256/CRC kontrolü yeniden geçti; arşivdeki 445 özgün dosyanın mevcut dosyalarla bayt karşılaştırmasında fark bulunmadı. Bu kontrol belgeleme alt görevinin kaynakları değiştirmediğini gösterir; canlı oynatma veya derleme başarısı değildir. Git diff, `.git` bulunmadığından kullanılamadı.

### Ana oturum sonraki başlangıç doğrulamaları

Başlangıç tablosunun yerine geçirilmeden: izole ortamda doğru pytest koşusu **92 PASS / 1 FAIL**; tek hata Git geçmişiyle sürüm karşılaştırması beklentisidir. Commit fixture'lı upstream testleri koşulmadı. Checksum doğrulamalı izole JDK 17 ile ikinci Gradle koşusu Java engelini aştı; **SDK location not found** nedeniyle yine hiçbir Kotlin testi çalışmadı. Artifact kontrolü `build/plugins.json` yokluğuyla başarısız. Ayrıntı ve kalıcı kanıtlar [TEST_RESULTS.md](TEST_RESULTS.md) içinde; başlangıç envanteri tekrar taranarak değiştirilmedi.

### SDK kurulduktan sonra tamamlanan değişiklik öncesi kapı

Kullanıcının API 35 SDK kurulumundan sonra aynı v36 runtime kaynakları **210 Kotlin testi / 0 hata** ile geçti; 25 etkin paket ve `plugins.json` üretildi; artifact validator PASS. Başlangıç paketleri/JUnit ayrıca yedeklendi. Bu kanıt canlı oynatma değildir; eski SDK engeli silinmeden [CONTINUATION_TEST_RESULTS.md](CONTINUATION_TEST_RESULTS.md) içinde güncel sonuç ve checksum kayıtlıdır. Envanter tekrar çıkarılmadı; `.git` geçmişi halen eksiktir.
