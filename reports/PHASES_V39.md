# v39 test adayı — ana sözleşmeye devam

## Koruma ve etkin kapsam

`cloudstream_trplus_v36_zip_audit.md` başlangıç kabul girdisidir. Önceki v36 kaynak/paket yedekleri ve `cloudstream-trplus-final/` çıktısı değiştirilmeden korunur. Kullanıcının beş fazı kapsamda kalır; aşağıdaki sonuçlar tam sözleşme tamamlandı demek değildir. Yeni commit, push, workflow dispatch veya yayın yapılmaz.

## Faz 1 — dağıtım güvenliği

- Uzak `builds/plugins.json` salt okunur alındı: SHA-256 `265a4dcb3ca6ac18d934f0c39baeb1e8a2d5bda5c4bf5cad2cc8373754cfe368`. 26 yayımlanmış `.cs3` dosyasının tamamı metadata hash/boyutuyla doğrulanarak `backups/published-builds-20261016T175412Z/` altında saklandı. Hub v38 ve BlenderOpenMovies v2 buna dahildir. Uzak depo değiştirilmedi.
- Yeni yerel sürümler `max(yerel, yayımlanmış) + 1` tabanıyla seçildi: Hub **39**, AnimeciX **108**, DiziYou **20**; shared core etkisi nedeniyle 34 fiziksel modülün sürümü güncellendi. Yeni metadata/paketler ancak normal derlemeyle üretilecek; JSON/artifact elle değiştirilmedi.
- `.github/workflows/build.yml` artık **build/test + immutable Actions artifact** akışıdır. `contents: read`, checkout için `persist-credentials: false`; builds dalına checkout/silme/commit/push/force yayın komutları kaldırıldı. Workflow burada çalıştırılmadı.
- `verify_release_safety.py` düşük sürümü, eşit sürüm/farklı hash'i, yinelenen/bozuk kimlikleri ve onaysız envanter değişimini reddeder. Aday hazırlama CLI'ı açık bir `--published` snapshot'ı ister; eksik kaynak test istisnası ancak açıkça seçilebilir ve raporlanır. Son CI akışında Blender kaybını otomatik affeden istisna bırakılmadı.
- İlk release/packager dar koşusu **93 PASS**. Ardından mevcut teslimi koruma invariant'ı için artifact validator düzeltildi: yalnız etkin modülün canonical `<module>/build/<internalName>.cs3` çıktısı denetlenir; eski teslimin korunmuş farklı hash'i yeni derleme hatası değildir. Güncel artifact yokken tarihsel kopya başarı sağlayamaz. Bu kapsam ve workflow denetiminde **68 dar test PASS**. Koşular örtüşen testler içerir; sayılar toplanıp ayrı test adedi gibi sunulmaz.
- Yayın snapshot'ında bulunan ancak kaynakta eksik `BlenderOpenMovies` için silme kararı verilmedi: eski binary aynen yedeklendi; bağımsız ve açık lisanslı yeni kaynak modülü Faz 4'te geliştiriliyor. Envanter kapısı, yeni derleme sonrası yeniden değerlendirilecektir.

## Faz 2 — ortak dil politikası

- `StreamPrioritySorter.comparator(preference, evidenceFor, preflightFor)` core'a alındı; Hub aynı karşılaştırıcıyı kullanır. Öncelik TR audio metadata → TR subtitle metadata → bilinen diğer audio → bilinmeyen; kalite/ulaşım sonra gelir. Başlık, kaynak adı veya `_tr` URL'si dil kanıtı değildir.
- ISO dil alias/BCP47 normalizasyonu genişletildi; Farsça/Flemenkçe gibi önceden yanlışlıkla unknown olan açık metadata korunur. `und/mul/zxx`, bozuk ve uydurma etiketler known dil sayılmaz.
- Yeni `BoundedLinkDelivery.collectAndDeliver` ortak toplama/preflight/sıralı teslim sınırıdır. Sınırlar: 128 aday, eşzamanlılık 4, keşif 20 sn, preflight toplam 10 sn, tek probe 5 sn. İptal yayılır; iç timeout'ta kısmi sonuç korunur; kesin geçersizler düşer, belirsiz alternatifler kaybolmaz.
- DiziYou standalone native/fallback yolu bu ortak katmana taşındı. Hub tüm federasyon adaylarında ortak politikayı kullanır. Diğer standalone eklentilerin hepsi otomatik taşınmış gibi gösterilmez; ortak API bütün modüllere enjekte edilir ve kademeli kullanılabilir. İçerik/başlık özel dalı yoktur.
- Dar → üç modüllü koşu: **175 test, 0 failure/error/skip** (AnimeciX/core 60, Hub 106, DiziYou 9). Manifest dili, gerçek konuşma ve host autoplay/track seçimi ayrı kanıttır. Cihaz sonucu **NOT TESTED**.
- Son bağımsız kabul incelemesi, DiziYou'nun 20+10 sn varsayılan toplamının Hub'ın 30 sn provider deadline'ına çarpabildiğini doğruladı: iki native + altı bekleyen fallback/probe durumunda ilk callback hiç gelmeyebiliyordu. Gerçek varsayılanları kullanan sanal-zaman testi önce RED üretti; yalnız DiziYou varsayılanları **8 sn keşif + 6 sn preflight** yapıldı. Generic helper varsayılanları değişmedi. Regresyon ve DiziYou'nun **15 testi PASS**; dış iptal semantiği korunur. Bu kritik düzeltme sonrası yalnız DiziYou ve onu bundle eden Hub'ın yeniden test/derlemesi bağımsız kapıda bekleniyor.

## Faz 3 — sıradaki sağlayıcı onarımı

Önceki başarılı altyazı/HLS araştırması tekrar edilmedi. DiziYou için iki güncel public HTML isteğiyle kalan arama/kimlik kusuru kanıtlandı: global header alfabetik arşivi sonuçlara karışıyor, güncel `#list-series` kartı `.cat-item` içermiyor; yapım yılı metadata label'ından sonra text node olarak geliyor; `.diziyou_desc` kaçıyor. Raw query interpolasyonu `&/#/+` karakterlerinde query anlamını değiştirebiliyor.

**DiziYou düzeltmesi tamamlandı:** güncel ve legacy arama kartları aynı parser'da destekleniyor, global arşiv dışlanıyor, posterler korunuyor; query değeri UTF-8 bir kez encode ediliyor; açık yapım yılı label'ının takip eden text node'u kullanılıyor; synopsis'ten metadata alanları çıkarılıyor. Homepage, bölüm ve önceki bounded/native teslim mantığı değiştirilmedi. Parser-only **9 PASS**, modülün tamamı **14 PASS / 0 failure/error/skip**. Üç yeni fixture eklendi. Kanıt: `reports/evidence/phase3-diziyou-tests.log`. Yeni parser'ın Android runtime/gerçek ses sonucu değildir.

**DiziKorea ikinci modül:** dört HTTP-only istekte homepage 42 kart, güncel ayrıntıda 15 bölüm ve bölüm sayfasında üç embed adayı görüldü. Eski Flex X Cop helper soft-404 sonucu gerçek provider bozukluğu sayılmadı; fixture mı detector mı olduğu kesin değil. Selector/extractor/domain değiştirilmedi. Kaynakla kanıtlanan raw query `&/#/+` kusuru minimal encode helper ile düzeltildi; modülün **3 testi PASS**. Mevcut kart/episode normalization ve dedup fixture'ları korundu. Önceki provider testi geçmeden diğer modüle parser değişikliği uygulanmadı.

Takip eden yalnız iki HTTP isteği, search 302'nin aynı HTTPS host'taki `/?s=...` aramasına gittiğini doğruladı: HTTP 200 arama başlığı ve 24 selector kartı. Bu kod/cihaz runtime testi değildir; ilk rapordaki çözümlenmemiş redirect bu dar HTTP katmanında açıklığa kavuştu. Ham gözlem korunur, takip kanıtı `reports/evidence/dizikorea-search-redirect.json` içindedir. Tanı sınıfı fixture/detector belirsizliği olarak bırakıldı; doğrulama bütçesi bitmesi upstream playback blokajı gibi sunulmadı. Extractor/media/segment/player kabulü **NOT TESTED**.

## Faz 4 — lisanslı yeni modül / eksik kaynağı geri getirme

- Resmî kaynak `https://video.blender.org` PeerTube API'si: yalnız `blender_open_movies` kanalı, public privacy ve doğrulanmış lisans koşulları. `studio.blender.org` üyelikli gallery/asset erişimi kullanılmaz.
- Hak kanıtı: `https://peach.blender.org/about/`, `https://durian.blender.org/about/`, `https://mango.blender.org/about/`: Big Buck Bunny/Sintel/Tears of Steel **CC BY 3.0**, atıf ve krediler korunmalı. Studio genel dijital içerik varsayılanı CC BY 4.0; ayrıca API'de açık Attribution/ShareAlike lisans kaydı bulunan public içerikler değerlendirilebilir. Bilinmeyen/federated/private kaynaklar otomatik alınmaz.
- Public kanal JSON'u HTTP 200, 23 kayıt; Sintel ayrıntısı public/kendi kanalı olarak doğrulandı. Resmî `/object-storage/web_videos/...-240.mp4` isteği **206 video/mp4**, 65.536 baytta `ftyp`; toplam 49.205.262 bayt. Bunlar medya erişim kanıtıdır; duyulan ses/oynatma değil.
- Eski Google ve download.blender.org medya adayları erişim hatası verdi; hiçbir erişim kısıtı aşılmadı, bu adaylar yeni modüle taşınmadı. Mux test manifesti açılması da film kaynağının yerine kullanılmadı; resmî API/medya tercih edildi.
- `BlenderOpenMovies` v3 yeni Kotlin implementasyonu, plugin/Gradle/test/registry/Hub entegrasyonu eklendi. Plugin kimliği korunuyor; önceki binary/kaynak kopyalanmadı. İlk **8 test**, module `make`, Hub compilation ve structural validator geçti; opt-in canlı Sintel search/load + beş HTTP 206/ftyp probe'u yapıldı. Bu Android oynatma değildir.
- Kaynak incelemesindeki üç ek invariant düzeltildi: callback sırasında iptal sonrası teslimin durması/ortak helper kullanımı; aynı bilinen filmin iki UUID'sinin Hub'da eşit güven belirsizliği yaratmaması; API `id=818,label=1080p` kalite raporunun 720p'nin altına düşmemesi. Önce dört RED regresyon üretildi; sonra ortak helper, belgelenmiş canonical film ID tercihi ve doğrulanmış quality label eşlemesiyle kapandı. **13 test PASS, 1 opt-in live test SKIP**; yeni canlı istek tekrarlanmadı. İlk canlı kanıt ayrı korunur. Bağımsız verifier bu dört kapanışı kaynak/test üzerinde ayrıca doğruladı.

## Faz 5 — sıradaki kapılar

Yeni ve değişen modüllerde dar testler → genel regresyon/derleme → canonical artifact, yayın snapshot'ına karşı sürüm/hash/envanter kapısı → bağımsız verifier/reviewer → **yeni** `cloudstream-trplus-test-v39/` yerel teslimi. Önceki final klasörü ezilmeyecek. Android 12 cihaz yoksa MANUAL_REQUIRED / NOT TESTED kalacak; diğer bağımsız çalışmalar durdurulmayacak. Film/dizi/Türk dizisi/çizgi film/anime/belgesel için izinli ek araştırma ve tüm sağlayıcı runtime onarımları henüz tamamlanmadı.

## Kullanım sınırı sonrası kapanış

Kullanıcı kalan kullanım nedeniyle yeni sağlayıcı araştırmasını durdurdu. Korunacak geliştirmeler ve kalan kapsam `NEXT_STEPS.md` içinde. Genel bağımsız koşu **259 Kotlin PASS / 1 optional live SKIP**, **208 Python PASS**, **26 artifact** için yapısal/katı/release-snapshot kapıları PASS verdi. Ardından yukarıdaki deadline HIGH bulgusu nedeniyle genel PASS tek başına kapanış sayılmadı; sonlu düzeltme ve yalnız etkilenen modüllerin yeniden üretimi yürütülüyor. Genel suite gereksiz tekrarlanmayacak. Sonraki kapanış kaydı bu yeni gate/review ve yerel teslim sonucunu ayrıca belirtir.

Salt okunur son uzak kontrolü: yayımlanmış manifest hash'i hâlâ `265a4dcb3ca6ac18d934f0c39baeb1e8a2d5bda5c4bf5cad2cc8373754cfe368`, 26 paket, Hub v38; yayın değişmedi (`reports/evidence/v39-published-unchanged.json`).

### Kritik kapanış ve son kullanıcı talimatı

Deadline düzeltmesi bağımsız doğrulandı: DiziYou **15 PASS**, Hub **106 PASS**; yalnız iki etkilenen artifact ve metadata yeniden üretildi. Canonical/katı **26 paket** kapısı ve yayımlanmış snapshot release güvenliği tekrar PASS. `v39-verification.json` güncel sonucu **PASS**; önceki genel PASS/sonraki HIGH/sonlu kapanış tarihçesi korunur.

Kullanıcı daha sonra **yeni analiz/test/derleme başlatmadan hemen paketle** talimatı verdi. Ayrı yeni reviewer veya araştırma başlatılmadı; başarılı mevcut çıktılar kullanılıyor. Türkçe teslim `TEST_DISTRIBUTION_V39.md`, kalan kapsam `NEXT_STEPS.md`; Android/gerçek ses/host otomatik seçim NOT TESTED. Yeni test adayı paketleme tamamlanınca kendi SHA256SUMS ve published=false durumuyla teslim edilir; eski final ve uzak v38 korunur.
