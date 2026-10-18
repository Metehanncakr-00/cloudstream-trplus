# Sonraki oturum — kalan kapsam ve güvenli başlangıç

Kullanıcı kalan kullanım hakkı nedeniyle yeni sağlayıcı araştırmasını durdurdu. Bu oturum Türkçe ortak öncelik, DiziYou, DiziKorea ve Blender değişikliklerini koruyup kritik düzeltme/test/yerel test paketi kapılarını tamamlar. **Tam ana sözleşme bitmiş değildir.** Başlangıç envanterini, tamamlanmış analizleri veya başarılı tüm-suite koşularını yeniden başlatmayın; önce `PROGRESS.md`, `PHASES_V39.md` ve v39 teslim raporunu okuyun.

## Ana Hedefler ve Açık İşler

1. **Tüm desteklenen içeriklerde Türkçe dublaj önceliği:** TR audio metadata > TR subtitle metadata > bilinen diğer audio > bilinmeyen. Core karşılaştırıcısı içerik türünden bağımsızdır. Android 12 ve cihaz üzerinde gerçek ses akışı/otomatik seçim doğrulanması.
2. **Bozuk sağlayıcıların onarımı:** AnimeciX, BelgeselX, SezonlukDizi, Dizilla, JetFilmIzle, WebDramaTurkey, SetFilmIzle, Dizigecesi, RareFilmm gibi kaynakların gerçek search/load/episode/loadLinks/manifest zincirlerinin tek tek onarımı.
3. **Yeni sağlayıcılar eklenmesi:** Açık lisanslı, izinli ve kamuya açık kaynakların (Blender v3 ötesi) hak/provenans ve medya kanıtı ile aşamalı entegrasyonu.
4. **Torrentio'nun alternatif olarak korunması:** Torrentio ve Debrid kaynaklarının alternatif olarak tutulması, kota ve fallback sıralamasıyla korunması; doğrulanmamış beyanların ses kanıtı sayılmaması.
5. **Süper Eklenti (CloudStreamHub) entegrasyonu:** Tüm sağlayıcıları TMDB/keşif çatısı altında toplayan aggregator mimarisinin güçlendirilmesi, aday bütçeleri ve federasyon zaman aşımlarının yönetimi.

## Korunacaklar

- `backups/cloudstream-v36-original-20261016-111427.zip` — özgün kaynak, SHA-256 `ce29af53dc53552a7c810e7fefc8e446a6dd07640d7c79cb1e4aa2b27ea06c7d`.
- `backups/v36-baseline-built-sdk35.zip` — başlangıç v36 paketleri/JUnit, SHA-256 `35df2ba4a7d9c4e965de9f007a66c80820fb36960da4b3d571148127af488fd6`.
- `backups/published-builds-20261016T175412Z/` — yayımlanmış 26 paket, Hub v38/Blender v2 ve hash listesi; manifest SHA-256 `265a4dcb3ca6ac18d934f0c39baeb1e8a2d5bda5c4bf5cad2cc8373754cfe368`.
- `backups/cloudstream-trplus-final-v37/` — önceki v37 teslimi, tarihsel kanıt olarak korunur.
- Git tabanı `v36-baseline`, dal `trplus/development`, HEAD `0beda1f3e445e50d86576111f6b1879287a3d777`. Tüm çalışmalar commit edilmemiştir. `reset/checkout/clean` ile kaybetmeyin; commit/push/yayın için ayrıca açık kullanıcı talebi gerekir.

## 1. Android 12 / gerçek Türkçe otomatik seçim — MANUAL_REQUIRED

Cihaz bağlı değildi; gerçek konuşma, host kaynak sırası/auto seçim, çoklu audio track seçimi ve TV kalıcılığı **NOT TESTED**. `ANDROID12_CHECKLIST.md` ile ayrı test cihazı/uygulama profili kullanın; çalışan v38/orijinal kurulum kaldırılmamalı. Aynı plugin ID'li v39'u aynı uygulamaya yüklemenin eski yerel kurulumu değiştirebileceğini hesaba katın.

Bu manuel kapının eksikliği bağımsız sağlayıcı/kod geliştirmelerini durdurmaz; cihaz testi yapılamıyorsa açıkça kaydedip sıradaki bağımsız işe geçin.

- The Office US (2005)/UK (2001), doğru sezon/bölüm; erken EN/geç doğrulanmış TR; sıcak/soğuk cache; TR yokken alternatif korunumu.
- Doraemon 1979/2005, Family Guy, Avatar: The Last Airbender (2005), Elif, Medcezir, Unutma Beni, Eşkıya Dünyaya Hükümdar Olmaz: bulunabilirlik/yıl/sezon/mutlak bölüm ve ses kanıtı ayrı tutulmalı; mevcut olmayan içerik/dublaj uydurulmamalı.
- AAC/AC3/EAC3/Opus, HLS/DASH/native dosya, seek/segment, stereo/AVR/passthrough ve D-pad/odak/Kaydet/İptal/Geri/uygulama yeniden açılışı.
- Manifest LANGUAGE, provider bildirimi, iframe, HTTP 200 veya ilk media baytları gerçek Türkçe konuşma/oynatma PASS değildir.

## 2. Mevcut sağlayıcı onarım kuyruğu

- DiziYou: yapılan arama kartı/arşiv sınırı, poster/yıl/synopsis/query, altyazı ve ortak delivery düzeltmelerini koruyun. Gerçek Kotlin/Android search/load/episode/loadLinks/host seçimini ölçün; `_tr` bilinmeyen ses adayını zorla doğrulanmış yapmayın.
- DiziKorea: query encoding düzeltmesini ve 3 testini koruyun. Arama 302'sinin aynı host'ta gerçek `/?s=` sonucuna gittiği gözlendi; bu araştırmayı yeniden yapmayın. Eski Flex X Cop helper `SOFT_404_PAGE` sonucunu fixture-stale ile detector false-positive arasında ayrıştırmak gerekiyor. Gerçek current HTML olmadan config'i yeşil göstermek için değiştirmeyin. PlayerDKorea/diğer embed → medya → header/referrer → manifest/dosya → segment → player zinciri açık.
- HDFilmCehennemi ve FilmMakinesi: bu turda parser/extractor'larına spekülatif değişiklik yapılmadı. Önceki çalışan v36/v38 ile aynı içerikte kontrollü regresyon/medya/gerçek ses karşılaştırması gerekiyor; compile PASS tek başına çalışma değildir.
- AnimeciX, BelgeselX, SezonlukDizi, Dizilla, JetFilmIzle, WebDramaTurkey, SetFilmIzle, Dizigecesi, RareFilmm: önceki kısmi HTTP helper bulguları `HTTP_PROVIDER_STATUS.md` içindedir. Sırayla ilgili gerçek Kotlin yolunu yeniden üretin; helper marker eksikliği upstream playback arızası değildir. Bir modül düzelip dar test geçmeden toplu parser değişikliği uygulamayın.
- Diğer mevcut sağlayıcıların canlı search/detail/episode/link/audio kapıları ve uzun dizilerde mutlak bölüm senaryoları tamamlanmadı. Önceki registry/baseline envanterini kullanın; devre dışı kaynakları doğrulamadan etkinleştirmeyin.

## 3. Ortak dil politikası — kalan entegrasyon sınırı

Core karşılaştırıcısı içerik türünden bağımsızdır; Hub tüm federasyon adaylarında kullanır. DiziYou ve Blender standalone ortak bounded delivery'yi kullanır. Diğer standalone modüllerin hepsi otomatik taşınmış değildir; gerçek modül davranışı/testiyle kademeli taşıyın. MP4/MKV/TS içindeki gerçek track-language bilgisi mümkünse bounded ve güvenli parse ile genişletilebilir; bugün okunmayan metadata için UNKNOWN dürüstçe korunur. Kaynak adı, `_tr`, provider `lang` veya Torrentio beyanı doğrulanmış audio değildir. Host auto/track seçimini eklentinin kontrol ettiği varsayılmamalı.

## 4. Yeni kaynaklar — bu oturumda araştırma durduruldu

BlenderOpenMovies v3 resmî public PeerTube kanalı, açık lisans/ownership/privacy ve native dosya allowlist'iyle yeniden kuruldu; önceki yayımlanmış binary korundu. Kaynak yalnız Movie/animasyon film kapsamındadır; Türkçe ses garantisi yok. Daha fazla film/dizi/Türk dizisi/çizgi film/anime/belgesel kaynağı için bakım, hak/provenans, API ve medya kanıtı araştırması sonraki oturumdadır. Henüz izin kanıtı olmayan adayları listeye veya etkin registry'ye eklemeyin. Abonelikli Blender Studio dosyaları, erişimi reddeden aynalar, DRM/auth/erişim kısıtı aşımı kapsam dışıdır.

## 5. Toolchain ve yayın güvenliği

- Eski D8/R8 ile Kotlin 2.3 metadata uyarıları biliniyor. Ayrı dar toolchain planıyla uyumlu sürümü ve yalnız gereken lock değişimlerini doğrulayın; uyarıyı gizlemek için validasyonu kapatmayın. Derleme başarıyla cihaz uyumluluğunu eşitlemeyin.
- `build.yml` artık yalnız read-only build/test/immutable artifact'tir. Otomatik `builds --force` yayınını geri getirmeyin. Bu oturumda workflow veya GitHub mutasyonu yapılmadı.
- Yeni her aday için normal Gradle metadata üretimi, canonical artifact doğrulaması ve açık güvenilir `--published` snapshot'ına karşı düşük sürüm/eşit sürüm-farklı hash/envanter kapısı zorunludur. Eksik/yeni kimlik istisnası sessizce açılmamalıdır.
- Mevcut test teslimi gelecekte yayın yapılmış gibi gösterilmemeli; gerçek yayın ayrı açık kullanıcı onayı ve kontrollü süreç ister. Başarısız aynı işlemi iki denemeden sonra kaydedip bağımsız işlere geçin.
