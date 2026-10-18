# Sağlayıcı durum matrisi — v36

> Bu tablo ilk oturumun başlangıç anlık görüntüsüdür. Sonraki SDK kurulumuyla aynı 25 etkin modül derlendi, 210 test ve artifact kontrolü geçti. Güncel uygulama/genel gate sonuçları [CONTINUATION_TEST_RESULTS.md](CONTINUATION_TEST_RESULTS.md) içindedir; kaynak envanteri tekrar çıkarılmadı.

Tarih: 16 Ekim 2026. Kaynak: mevcut `config/providers.json`, `config/domains.json`, modül `build.gradle.kts` dosyaları ve `settings.gradle.kts`. Eski raporlar canlı kanıt sayılmadı. Başlangıç komutlarının sınırları [BASELINE.md](BASELINE.md) içindedir.

## Durumların anlamı

- `COMPILE`: Kotlin/eklenti derleme kanıtı. Etkin 25 modül için `BLOCKED`: toplu Gradle başlangıcı Java 8/JVM bağımlılık uyumsuzluğunda, kaynak derlenmeden durdu. Sağlayıcıya özgü derleyici hatası değildir. Dışlanan/modülü olmayan 11 kayıt için `NOT TESTED`.
- Sonraki JDK 17 denemesi Java engelini aştı, ancak Android SDK yokluğunda test bağımlılık belirlemesi durdu. Derleme sütunlarını PASS yapacak kanıt elde edilmedi; tablo başlangıç durumunu korur. Ayrıntı: [TEST_RESULTS.md](TEST_RESULTS.md).
- `CATALOG`: canlı ana sayfa/katalog ayrıştırma; `SEARCH`: canlı arama ve doğru eser; `EPISODE`: doğru ayrıntı/sezon/bölüm eşlemesi; `LINK`: gerçek medya adayı ve gerekli istek bağlamı; `AUDIO`: gerçek ses izi/dili; `ANDROID PLAYBACK`: cihazda görüntü, ses ve devam eden oynatma. Bunlar birbirinin yerine kullanılamaz.
- `NT` hücrelerinin tümü açıkça **NOT TESTED** demektir; başarısız, boş sonuç veya desteklenmiyor anlamına gelmez. Film sağlayıcılarının EPISODE uygulanabilirliği de test edilmedi; gelecekte kanıtla `N/A` ayrılabilir.
- Domain sütunundaki `active/dead/unsupported/blocked` yalnız **statik yapılandırma etiketi**dir. 36 domain kaydının `lastChecked` alanı `null`; bu çalışma canlı domain testi yapmadı. HTTP 200 veya callback bile tek başına oynatma kanıtı değildir.
- `Kaynak` fiziksel sağlayıcı modülü ve Kotlin giriş noktası varlığını gösterir, derlenmiş paket varlığını değil. Her mevcut modülün tam giriş yolu başlangıç belgesindedir. Kayıt adı `4KHDHub`, modül/domain anahtarı `FourKHDHub`'dır.

| Kayıt / modül | Kaynak | Sürüm | Etkin | Yapılandırılmış canonical domain | Statik domain etiketi | COMPILE | CATALOG | SEARCH | EPISODE | LINK | AUDIO | ANDROID PLAYBACK |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AnimeciX | Var | 106 | Evet | https://animecix.tv | unsupported | BLOCKED | NT | NT | NT | NT | NT | NT |
| BelgeselX | Var | 107 | Evet | https://belgeselx.com | dead | BLOCKED | NT | NT | NT | NT | NT | NT |
| DiziPal | Var | 106 | Hayır | https://dizipalorjinal7.com | dead | NOT TESTED | NT | NT | NT | NT | NT | NT |
| FilmMakinesi | Var | 107 | Evet | https://filmmakinesi.to | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| 4KHDHub / FourKHDHub | Var | 7 | Evet | https://4khdhub.one | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| HDFilmCehennemi | Var | 108 | Evet | https://www.hdfilmcehennemi.nl | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| KultFilmler | Var | 50 | Evet | https://kultfilmler.net | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| TurkAnime | Var | 112 | Hayır | https://www.turkanime.tv | dead | NOT TESTED | NT | NT | NT | NT | NT | NT |
| YesilCamTv | Var | 28 | Evet | https://yesilcamtv.com.tr | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| TrtDizi | Yok | — | Hayır | https://www.trt1.com.tr | active | NOT TESTED | NT | NT | NT | NT | NT | NT |
| SezonlukDizi | Var | 17 | Evet | https://sezonlukdizi.cc | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| SinemaCX | Var | 17 | Evet | https://sinemacc.com | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| DiziMom | Var | 17 | Hayır | https://www.dizimom.beer | dead | NOT TESTED | NT | NT | NT | NT | NT | NT |
| DiziYou | Var | 18 | Evet | https://www.diziyou.one | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| Dizilla | Var | 17 | Evet | https://dizilla.now | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| FullHDFilmizlesene | Var | 17 | Evet | https://www.fullhdfilmizlesene.now | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| DiziBox | Yok | — | Hayır | https://www.dizibox.live | blocked | NOT TESTED | NT | NT | NT | NT | NT | NT |
| FilmModu | Var | 19 | Hayır | https://www.filmmodu.one | unsupported | NOT TESTED | NT | NT | NT | NT | NT | NT |
| JetFilmIzle | Var | 17 | Evet | https://jetfilmizle.now | unsupported | BLOCKED | NT | NT | NT | NT | NT | NT |
| CizgiMax | Var | 17 | Hayır | https://cizgimax.online | dead | NOT TESTED | NT | NT | NT | NT | NT | NT |
| DDizi | Var | 17 | Hayır | https://www.ddizi.im | dead | NOT TESTED | NT | NT | NT | NT | NT | NT |
| Sinewix | Var | 17 | Hayır | https://sinewix.net | dead | NOT TESTED | NT | NT | NT | NT | NT | NT |
| CloudStreamHub | Var | 36 | Evet | https://api.themoviedb.org | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| DiziKorea | Var | 17 | Evet | https://dizikorea3.com | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| DramaDizilerim | Var | 17 | Evet | https://dramadizilerim.com | unsupported | BLOCKED | NT | NT | NT | NT | NT | NT |
| WebDramaTurkey | Var | 17 | Evet | https://webdramaturkey2.com | unsupported | BLOCKED | NT | NT | NT | NT | NT | NT |
| Animeler | Var | 18 | Evet | https://animeler.pw | unsupported | BLOCKED | NT | NT | NT | NT | NT | NT |
| SetFilmIzle | Var | 17 | Evet | https://www.setfilmizle.ltd | unsupported | BLOCKED | NT | NT | NT | NT | NT | NT |
| DiziLife | Var | 17 | Hayır | https://dizi74.life | dead | NOT TESTED | NT | NT | NT | NT | NT | NT |
| HDFilmDelisi | Var | 17 | Evet | https://hdfilmdelisi.one | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| Dizigecesi | Var | 17 | Evet | https://dizigecesi.com | dead | BLOCKED | NT | NT | NT | NT | NT | NT |
| RareFilmm | Var | 17 | Evet | https://rarefilmm.com | unsupported | BLOCKED | NT | NT | NT | NT | NT | NT |
| FilmHane | Var | 17 | Hayır | https://www.filmhane.shop | dead | NOT TESTED | NT | NT | NT | NT | NT | NT |
| StarTV | Var | 3 | Evet | https://www.startv.com.tr | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| DiziIzle | Var | 3 | Evet | https://www.diziizle.you | active | BLOCKED | NT | NT | NT | NT | NT | NT |
| YTS | Var | 57 | Evet | https://yts.gg | active | BLOCKED | NT | NT | NT | NT | NT | NT |

## Statik tutarsızlıklar — canlı arıza kararı değildir

Etkin olup domain etiketi `dead` olanlar: BelgeselX, Dizigecesi. Etkin olup `unsupported` olanlar: AnimeciX, Animeler, JetFilmIzle, DramaDizilerim, WebDramaTurkey, SetFilmIzle, RareFilmm. Animeler dışındaki bu sekiz kaydın ayrıca `disabledReason` metni bulunur; `enabled=true` yine de mevcut etkinlik değeridir. Bu çelişki otomatik devre dışı bırakma veya çalışma garantisi gerekçesi yapılmadı; config değiştirilmedi.

TrtDizi ve DiziBox modülü/kodu bulunmayan, devre dışı iki kayıt olduğundan 36 kayıt ≠ 36 uygulanmış sağlayıcı. TrtDizi'nin `active` domain etiketi eksik implementasyonu tamamlamaz. Diğer dokuz devre dışı kayıt fiziksel kaynak içerir ama Gradle dahil etme listesinden çıkarılmıştır. Eski `disabledReason` açıklamaları yeniden üretilmiş güncel ağ kanıtı değildir.

Sonraki kanıtlar her sütun için ayrı tarih, komut/cihaz, eser kimliği/yılı, sezon/bölüm ve güvenli kanıt bağlantısıyla eklenmelidir. Bir katmanın PASS olması diğer katmanları yükseltmez; özellikle LINK → AUDIO → ANDROID PLAYBACK geçişleri ayrı doğrulama ister.
