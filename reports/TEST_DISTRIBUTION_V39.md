# CloudStream TR+ v39 — yerel test teslimi

**Yayınlanmamış test adayıdır; tam ana sözleşme ve Android oynatma kabulü tamamlanmadı.** Kullanıcının kullanım sınırı talimatıyla yeni analiz/test/derleme durduruldu. Aşağıdaki başarılı mevcut çıktılar paketlemede kullanılır. Commit, push, workflow dispatch veya yayın yapılmadı.

## Teslim edilen geliştirmeler

- Hub sürümü **39**, yayımlanmış v38'in üzerindedir. Diğer değiştirilmiş paketlerin sürümleri de yayımlanmış sürümlerden yüksektir; eşit sürüm/farklı hash ve downgrade kapıları geçti.
- **26 etkin paket / 35 fiziksel kaynak modülü** korunur. Yayımlanmış BlenderOpenMovies kimliği yeni ve bağımsız v3 kaynak modülüyle envantere geri getirildi. Devre dışı mevcut modüller doğrulanmadan etkinleştirilmedi.
- `build.yml` artık yalnız read-only test/build ve immutable artifact akışıdır; builds dalına commit/push/force yayın adımları kaldırıldı. Workflow bu oturumda çalıştırılmadı.
- Core ortak dil politikası: TR audio metadata → TR subtitle metadata → bilinen diğer audio → bilinmeyen. Hub, DiziYou standalone ve Blender ortak sıralama/bounded teslim yolunu kullanır. Başlık, `_tr`, provider dili veya Torrentio beyanı doğrulanmış ses sayılmaz. Diğer standalone modüllerin tümü taşınmış gibi gösterilmez.
- DiziYou arama kartı/arşiv sınırı, poster/yıl/açıklama/query ve önceki altyazı onarımları korundu. Son kritik nested-deadline düzeltmesiyle DiziYou bütçesi **8+6 sn** oldu; Hub'ın 30 sn sınırında erken adayların tamamen kaybolması regresyonla kapatıldı.
- DiziKorea query encoding düzeltildi; çalışan katalog/bölüm/extractor kodu spekülatif değiştirilmedi. Eski soft-404 helper bulgusu gerçek provider arızası gibi sunulmadı.
- Blender yalnız resmî public PeerTube kanalında açık lisans/ownership/privacy ve native dosya allowlist'i kullanır. Üyelikli içerik/engelli aynalar kullanılmaz. Katalog alias dedup, kalite etiketi, iptal ve belirsiz fallback düzeltmeleri korundu.

## Mevcut doğrulama kanıtı

| Kontrol | Sonuç |
|---|---|
| Genel Kotlin koşusu ve 26 paket derlemesi | **259 PASS, 1 optional live SKIP** |
| Genel Python koşusu | **208 PASS**; commit oluşturan fixture suite'i kullanıcı yasağı nedeniyle dışlandı |
| Son deadline düzeltmesi sonrası yalnız etkilenen testler | **DiziYou 15 PASS / Hub 106 PASS** |
| Son etkilenen paketlerin yeniden derlenmesi / metadata üretimi | PASS; genel suite gereksiz tekrarlanmadı |
| Canonical artifact ve katı paket doğrulaması | **26 paket PASS** |
| Yayımlanmış snapshot'a karşı sürüm/hash/envanter | PASS; eksik/yeni ID istisnası gerekmedi |
| Son bağımsız verifier | **PASS**, kritik deadline bulgusu kapalı |
| v39 için ayrıca son reviewer | Kullanıcının yeni analizleri durdurma talimatı nedeniyle ayrıca başlatılmadı |
| Android 12 / gerçek ses / host auto-track / TV | **NOT TESTED / UNVERIFIED_MANUAL** |

Genel koşu, kritik düzeltme ve etkilenen modül koşuları ayrı kanıtlardır; hepsi yeniden tam suite koşulmuş gibi gösterilmez. Eski D8/Kotlin metadata uyarıları biliniyor; cihazdaki etkisi doğrulanmadı. Hiçbir sağlayıcı Android playback PASS veya `IMPLEMENTED_WORKING` ilan edilmedi.

Kanıt: `reports/evidence/v39-verification.json`, `v39-release-safety.json`, `v39-gradle.log`, `v39-critical-final.log`, `v39-pytest.xml`. Büyük ham loglar/gizli kayıtlar teslim ZIP'lerine alınmaz; çalışma klasöründe korunur. Paketleme aracının kendi manifest/hash kontrolleri paket üretiminin parçasıdır; yeni test veya derleme değildir.

## Dosya yolları

Teslim kökü: `C:\Users\Metehan\Desktop\cloudstream-trplus-v36\cloudstream-trplus-test-v39\`

- `main/`: tam Kotlin/Gradle/provider/core kaynakları, testler, CI, lisans/atıf, `README-TR.md` ve filtrelenmiş raporlar.
- `builds/`: 26 başarılı `.cs3`, `plugins.json`, `repo.json`, `SHA256SUMS`.
- `reports/`: Türkçe raporlar, test-count özetleri, `release-safety.json`, `distribution-status.json`, `NEXT_STEPS.md`.
- Kaynak ZIP: `C:\Users\Metehan\Desktop\cloudstream-trplus-v36\cloudstream-trplus-test-v39\cloudstream-trplus-main.zip`
- Paket ZIP: `C:\Users\Metehan\Desktop\cloudstream-trplus-v36\cloudstream-trplus-test-v39\cloudstream-trplus-builds.zip`
- Hash listesi: `C:\Users\Metehan\Desktop\cloudstream-trplus-v36\cloudstream-trplus-test-v39\SHA256SUMS`

ZIP'ler branch-root dosyaları içerir; fazladan main/builds üst klasörü yoktur. `.git`, `local.properties`, SDK/cache/yedekler ve büyük ham loglar dahil edilmez. Hazırlık başarılı olup bu dosyalar oluşmadan teslim var sayılmaz. Paket durumunda **published=false**, **manualPlayback=NOT_TESTED** kaydedilir.

## Güvenli test, GitHub ve geri dönüş

Uzak depo hâlâ yayımlanmış **Hub v38 / 26 paket** içeriyor; son salt okunur manifest kontrolü snapshot ile aynı SHA-256 verdi: `265a4dcb3ca6ac18d934f0c39baeb1e8a2d5bda5c4bf5cad2cc8373754cfe368`. Bu oturum yeni paketi yayımlamadı. Mevcut uzak repo URL'si v39 yerel paketini kurmaz.

Çalışan v38/orijinal kurulumu kaldırmayın. Test adayının ID'leri aynıdır; aynı CloudStream uygulamasına doğrudan `.cs3` yüklemek eski yerel eklentiyi değiştirebilir. Ayrı test cihazı/uygulama profili ve önce SHA256SUMS kontrolü önerilir; cihaz kurulum akışı burada denenmedi. GitHub'a main/builds ZIP yüklemesi veya commit/push için ayrıca kullanıcı onayı gerekir.

Geri dönüş için kaynak v36 ve derlenmiş v36 ZIP'leri ile `backups/published-builds-20261016T175412Z/` altındaki hash doğrulanmış yayımlanmış v38 paketleri korunuyor. Arşivleri ayrı boş dizine açın; çalışma kaynaklarını reset/checkout/delete ile kaybetmeyin. Önceki `cloudstream-trplus-final/` v37 adayı yalnız tarihsel kanıttır ve yeni yayın/kurulum için kullanılmamalıdır.

## Kalan kapsam

**[NEXT_STEPS.md](NEXT_STEPS.md)** sonraki oturumun başlangıcıdır: diğer provider runtime onarımları, yeni izinli kategoriler/kaynaklar, tüm içerik ve mutlak bölüm testleri, MP4/MKV/TS dil metadata genişlemesi, D8 toolchain uyumu, gerçek Türkçe konuşma/otomatik seçim ve Android 12/TV kabulü. Yeni sağlayıcı araştırması kullanıcı talimatıyla bu oturumda durduruldu; bu işler tamamlanmış veya kapsam dışı sayılmadı.
