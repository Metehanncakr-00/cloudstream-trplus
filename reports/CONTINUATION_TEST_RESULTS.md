# SDK 35 sonrası geliştirme ve doğrulama

Bu belge önceki başlangıç kaydını silmeden yeni kanıtları kaydeder. Hiçbir derleme/link kanıtı Android oynatma başarısı değildir.

## Değiştirilmemiş v36 — başlangıç kapısı

- Ortam: önceki checksum doğrulamalı Temurin JDK 17; `local.properties` ile kullanıcı tarafından kurulan `C:/Users/Metehan/AppData/Local/Android/Sdk`, API 35.
- Komut: `gradlew.bat testDebugUnitTest --parallel --continue make makePluginsJson --console=plain`.
- Sonuç: **BUILD SUCCESSFUL in 1m 42s**, 477 görev (205 yürütüldü, 272 cache). 39 JUnit XML dosyası: **210 test / 0 hata / 0 başarısız / 0 atlanan**.
- 25 etkin modül için `.cs3` ve `build/plugins.json` üretildi. `python tools/validate_repo.py --verify-artifacts`: **PASS**, 0 hata; 25 kayıt/paket hash kontrolü.
- Bu ölçüm Kotlin/Gradle/config runtime değişikliği başlamadan alındı. Yalnız makineye özgü SDK yolu tanımlanmıştı. Başlangıç metadata URL'leri henüz upstream adresleriydi; bunlar TR+ final dağıtımı değildir.
- Paket ve JUnit yedeği: `backups/v36-baseline-built-sdk35.zip` (66 dosya), SHA-256 `35df2ba4a7d9c4e965de9f007a66c80820fb36960da4b3d571148127af488fd6`; ZIP CRC geçti. Orijinal kaynak yedeğine dokunulmadı.
- Ham Gradle logu `reports/evidence/baseline-sdk35-gradle.log` içindedir. Çok sayıda D8 Kotlin metadata uyarısı içerir; derlemenin başarılı olması bunların cihazdaki etkisinin doğrulandığı anlamına gelmez. Final ZIP'e büyük ham log alınmamalıdır.

## Git ve cihaz sınırı

- Başlangıçtaki `.git` yokluğu derlemeden bağımsızdı. Sonraki provenans kontrolünde upstream `0beda1f3e445e50d86576111f6b1879287a3d777` kaynakları yedekle eşleşti (445 tracked dosya: 441 byte-identical, dört dosyada yalnız satır sonu farkı). Kaynaklar checkout edilmeden Git metaverisi, `v36-baseline` ve `trplus/development` kuruldu. Yeni commit oluşturulmadı. Kanıt: `reports/evidence/upstream-provenance.json`. Bundan sonra gerçek sürüm kapısı `--base v36-baseline` ile koşulabilir.
- SDK içindeki `platform-tools/adb.exe devices -l`: komut çalıştı, cihaz listesi boş. Android 12 ses/otomatik seçim/TV kumanda kabulü **MANUAL_REQUIRED / NOT TESTED**.
- Daha önce geçen 92 Python testi gereksiz yere tekrarlanmadı; geçmişe bağlı tek başarısızlık gizlenmedi. Yeni Python araçları için yalnız ilgili dar testler koşulmalıdır.
- Git kurulduktan sonra `python tools/verify_version_bumps.py --base v36-baseline`: **PASS**, core değişimi için 34 fiziksel modül sürüm artışı doğrulandı. Önceki tek başarısız pytest ayrıca dar olarak yeniden koşuldu: **1 PASS in 1.67s**; `reports/evidence/version-gate-restored.xml`. Daha önce geçen 92 test gereksiz tekrarlanmadı; upstream commit fixture'ları çalıştırılmadı.

## DiziYou — ilk sağlayıcı için güncel HTTP teşhisi

Salt HTTP ile, challenge/auth/DRM aşılmadan araştırıldı. **Durum: DEGRADED**; duyulan ses ve Android oynatma doğrulanmadı.

- `/?s=The Office` arama sayfası ve `/the-office/` ayrıntısı HTTP 200. Sayfa The Office **US (2005)** kimliğini açıklaması/oyuncularıyla destekliyor; 192 benzersiz bölüm URL'si mevcut.
- S01E01 `/the-office-1-sezon-1-bolum/` → public player `/player/50601.html`. Provider'ın bölüm kimliği regex'i `50601` ile eşleşiyor.
- `storage.diziyou.one/episodes/50601/play.m3u8` ve `/episodes/50601_tr/play.m3u8` geçerli HLS; ilk variant playlist'leri 349 segment içeriyor.
- Her iki akışta ilk segmentin sınırlı 65.536 bayt örneği HTTP 206, `video/mp2t`, MPEG-TS paket senkronizasyonu gösteriyor. Güvenli host/path örnekleri: `cdn2.dystream.com/episodes/50601/720p_000.ts`, `flow1.dystream.com/episodes/50601_tr/720p_000.ts`.
- Bu bölümde `_tr` URL'si eksik manifest değildir; ancak master'larda audio-language rendition yok, örnekte dil descriptor kanıtı bulunmadı ve public player `_tr` varyantını tanıtmıyor. **`_tr` erişimi Türkçe konuşmanın kanıtı değildir.**

### Kanıtlanmış onarım hedefi: altyazı yolları

| Host: storage.diziyou.one, yol | HTTP | Kanıt |
|---|---|---|
| `/episodes/50601/tr.vtt` | 404 | Provider'ın eski oluşturduğu yol; WebVTT değil |
| `/episodes/50601/en.vtt` | 404 | Provider'ın eski oluşturduğu yol; WebVTT değil |
| `/subtitles/50601/tr.vtt` | 200 | Geçerli WebVTT; public player tarafından ilan ediliyor |
| `/subtitles/50601/en.vtt` | 200 | Geçerli WebVTT; public player tarafından ilan ediliyor |

En küçük kök neden düzeltmesi `DiziYou.kt` altyazı tabanını `/subtitles/<id>/` yapmak ve parser regression testini güçlendirmektir. İlk sağlayıcı düzeltmesi test edilmeden diğer sağlayıcılara toplu parser değişikliği uygulanmayacaktır.

Ek teşhis, henüz onarım değil: mevcut ayrıntı year/plot selector'ları sayfadaki 2005 ve `.diziyou_desc` bilgisini kaçırıyor; arama selector'ları alfabetik arşivi de kapsıyor (1.167 seçilen eleman). Bunlar dil/sıralama düzeltmesinin geçtiği varsayılarak topluca değiştirilmeyecektir.

## Uygulanan düzeltmeler ve dar testler

- Dil kanıtı mevcut bounded preflight baytlarından alınır; ek medya HTTP taraması yapılmaz. HLS referanslı AUDIO/SUBTITLES grupları ve DASH açık dil/type metadata'sı ayrı sınıflanır. Kaynak/release/URL adı doğrulanmış dil değildir.
- Hub, mevcut sınırlı keşif bütçeleri tamamlandıktan sonra adayları canlı callback sınırında **TR audio metadata > TR subtitle metadata > bilinen başka audio > bilinmeyen** olarak sıralar; kalite/ulaşım bu katmanın önüne geçemez. Torrent kotası sıralamadan sonra uygulanır; geç aday ve alternatifler korunur. Kaynak toplama gecikmesi artabilir (mevcut keşif bütçesi kadar); host otomatik oynatma veya ses parçası seçimi zorlanamaz.
- `ORIGINAL` yalnız açıkça bilinen Türkçe dışı sesi tercih eder; adında dublaj yazmaması özgün dil sayılmaz. Torrentio dil beyanları korunur, doğrulanmamış oldukları belirtilir. The Office gibi eşit güvenli ve farklı URL'li eser eşleşmeleri rastgele ilk sonuca düşmez; aynı URL tekrarları korunur.
- Header/referrer/extractorData/type/audioTracks korunumu ve iptal/timeout davranışı dar testlerle kapsandı. FourKhub'da iç keşif timeout'u sırasında doğrulanmış kısmi adayların kaybolması önlendi; dış iptal yutulmaz.
- Implementer'ın önce dar, sonra iki modüllü koşusu: **157 test PASS / 18 suite** (`CloudStreamHub` 105, `AnimeciX` 52). Yapısal validator 0 hata. Bunlar implementer sonuçlarıdır; bağımsız genel gate ayrıca kaydedilecektir.
- DiziYou altyazı yolu ve belirsiz `_tr` etiket düzeltmesi: `:DiziYou:testDebugUnitTest --tests com.cloudstream.tr.diziyou.DiziYouParserTest` **PASS**, Gradle 9 sn; dört parser testi. Her iki WebVTT yolu, ayrı video ağacı ve normal/`_tr` URL/referrer regresyonu kapsandı. Diğer sağlayıcılara parser değişikliği uygulanmadı.
- Ayar penceresine mevcut `audioPreference` için standart Spinner eklendi; Kaydet mevcut SharedPreferences yolunu kullanır, İptal kaydetmez. Kaynak tercihinin host track seçimi olmadığı açıklanır; ScrollView içeriği ve dışındaki Kaydet/İptal düzeni korunur. Derleme/genel gate bekleniyor; D-pad odağı ve gerçek tercih kalıcılığı MANUAL_REQUIRED / NOT TESTED.
- Paketleme aracı: **37 dar Python testi PASS**; metadata elle değiştirilmeden hedef URL, etkin envanter, artifact/manifest kimlik-sürüm-boyut/hash, güvenli kaynak filtreleri ve branch-root ZIP denetlenir. Eksik devre dışı TrtDizi/DiziBox kaynakları uydurulmaz. Gerçek paketleme bağımsız genel gate/review sonrasındadır.

## Bağımsız genel kapı ve sonlu düzeltme

Verifier, mevcut ağaç üzerinde genel gate'i çalıştırdı:

| Sınıf / kontrol | Sonuç |
|---|---|
| AUTO_REQUIRED — diff/whitespace ve Python syntax | PASS |
| AUTO_REQUIRED — repo registry/metadata validator | PASS; 25 etkin / 34 fiziksel modül |
| AUTO_REQUIRED — `verify_version_bumps.py --base v36-baseline` | PASS |
| AUTO_REQUIRED — tüm Kotlin unit testleri ve `make makePluginsJson` | PASS; **226 test, 0 failure/error/skip**, 25 paket |
| AUTO_REQUIRED — artifact validator | PASS |
| AUTO_REQUIRED — genel Python suite (commit fixture'ları hariç) | **130 PASS** |
| AUTO_REQUIRED — katı paket hazırlama giriş denetimi | İlk koşuda BOM kusuru; düzeltme sonrası **25 gerçek paket PASS** |
| AUTO_REQUIRED — BOM düzeltmesi sonrası bağımsız dar paketleme testleri | **40 PASS**; tam suite 133 koşuldu diye sunulmadı |
| MANUAL_REQUIRED — gerçek ses/host auto-track/Android 12/TV | UNVERIFIED_MANUAL / NOT TESTED |

Sonlu **HIGH** bulgu: Kotlin plugin kaynaklarındaki UTF-8 BOM, paket aracının anchored package regex'ine takılıyordu. Altı gerçek modülde tekrar üretildi; Kotlin/Gradle kaynakları `utf-8-sig` ile okunarak düzeltildi. Gerçek source-class eşitliği zayıflatılmadı; BOM'lu geçerli kaynak/Gradle kabulü ve yanlış plugin class reddi testleri eklendi. Yalnız iki Python araç/test dosyası değişti; bu nedenle Kotlin derlemesi gereksiz tekrarlanmadı. Fixer ve bağımsız verifier aynı 25 gerçek pakette doğruladı.

Güncel bağımsız sonuç **VERIFICATION_STATUS: PASS (uygulanan sonlu kapsam)**. Tam master sözleşmesi tamamlanmış değildir. Kanıtlar: `reports/evidence/final-verification-summary.json`, `final-gradle.log`, `final-pytest.xml`. Yeni derleme, başlangıçta da görülen 3.420 D8 metadata uyarı satırı içerir; cihazdaki etkisi doğrulanmadı.

Bağımsız reviewer gerçek tracked diff ve yeni dosyaları ayrıca inceledi: **REVIEW_STATUS: CLEAN**. HIGH BOM bulgusu kapandı; dil metadata'sının kota/callback öncesine taşınması, alternatiflerin ve host metadata'sının korunumu ile paket güvenliği için güncel engelleyici kusur bulunmadı. Bu sonuç cihaz kabulü veya tam master kapsamının tamamlanması değildir.

## Diğer sağlayıcıların HTTP katmanı

İlk sağlayıcı testinden sonra diğer 24 etkin kaynak, üç eşzamanlı worker ve tüm nested fetch'lerde zorunlu HTTP-only moduyla incelendi (55 fetch, 7.81 sn). Dynamic/stealth/browser veya hesap erişimi kullanılmadı. Envanter tekrarlanmadı; mevcut kayıtlar kullanıldı.

- **11 HTTP_SEMANTIC_PASS_ONLY:** yalnız paylaşılan Python katalog/detail/player marker'ları; HDFilmCehennemi, HDFilmDelisi, FilmMakinesi dahil. Plugin runtime veya playback PASS değildir.
- **10 HTTP_SEMANTIC_PARTIAL:** örneğin DiziKorea fixture'ında soft-404; AnimeciX/BelgeselX/SezonlukDizi/Dizilla player marker bulunamaması. Bu, Kotlin sağlayıcısının kök nedeninin kanıtlandığı anlamına gelmez.
- **1 CONFIG_UNSUPPORTED:** 4KHDHub detail fixture eksik.
- **2 TOOL_UNSUPPORTED:** CloudStreamHub/YTS JSON uçları HTML helper'a uygun değil.
- Bu koşuda WAF/Cloudflare blok saptanmadı; sağlayıcılar ölü veya oynatma çalışıyor diye ilan edilmedi. Arama/audio/Android playback tamamında NOT TESTED.

Ayrıntılı tablo [HTTP_PROVIDER_STATUS.md](HTTP_PROVIDER_STATUS.md), güvenli fetch kanıtı `reports/evidence/http-provider-smoke.json`. Diğer sağlayıcılar için toplu düzeltme uygulanmadı; gerekli sağlayıcıya özel debug/test döngüsü ana sözleşmede bekliyor.
