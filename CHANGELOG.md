# Değişiklik günlüğü

## Yayımlanmamış — v39 denetim sonrası test adayı

- Yayımlanmış Hub v38 ve 26 paket salt okunur indirilip hash/boyut doğrulamasıyla yedeklendi. Önceki v37 yerel ZIP'ler ve v36 yedekleri korundu; uzakta değişiklik yok.
- Hub 39 ve diğer modüllerde yayımlanmış sürümden yüksek numaralar seçildi; eşit sürüm/farklı hash ve downgrade için zorunlu yayın snapshot kapısı eklendi. Metadata normal build ile üretilecek.
- `build.yml` otomatik builds yayını yerine read-only izinli test/build/immutable-artifact akışına çevrildi; tüm destructive yayın adımları kaldırıldı. Canonical artifact doğrulaması eski teslimleri ezmeyi gerektirmiyor.
- Ortak dil karşılaştırıcısı core'a alındı; HLS/DASH dil normalizasyonu genişletildi. DiziYou standalone ortak bounded preflight/sıralı teslim yolunu kullanır; diğer standalone modüllerin tamamı taşınmış gibi sunulmaz.
- DiziYou aramasında global arşiv kirliliği, güncel kart/poster tanıma, query encoding ve yapım yılı/synopsis sınırı düzeltildi; 14 modül testi geçti. Homepage/bölüm/native teslim korundu.
- Sonraki DiziKorea query encoding görevi ve resmî açık lisanslı BlenderOpenMovies kaynak modülü yürütülüyor; sonuçlar `reports/PHASES_V39.md` içinde kaydedilir. Helper marker eksikliği provider playback arızası gibi sunulmuyor.
- Yeni test teslimi ayrı `cloudstream-trplus-test-v39/` altında hazırlanacaktır. Commit, push, yayın ve Android oynatma kabulü yapılmadı.

## Yayımlanmamış — SDK sonrası devam

- API 35 yerel SDK yolu tanımlandı; değiştirilmemiş v36 için 210 Kotlin testi, 25 paket ve artifact hash kontrolü geçti. Kaynak ve derlenmiş v36 yedekleri korundu.
- Git tabanı upstream ile dosya bazında doğrulandı; kaynakları checkout etmeden `v36-baseline` ve `trplus/development` kuruldu. Yeni commit oluşturulmadı.
- DiziYou altyazı yolları, public player'ın ilan ettiği `/subtitles/<id>/{tr,en}.vtt` yollarına düzeltildi; `_tr` akış adı doğrulanmamış ses adayını açıkça belirtiyor. Dört dar parser testi geçti.
- Türkçe manifest dil kanıtı/canlı teslim sıralaması, geç aday/kota politikası ve güvenli eser eşleştirmesi uygulandı; 157 iki-modüllü Kotlin testi geçti. Bağımsız genel gate/review ayrıca raporlanır; callback sırası host otomatik oynatma kanıtı değildir.
- Kaynak dil tercihi standart ayar spinner'ına bağlandı; mevcut SharedPreferences, ScrollView ve dış Kaydet/İptal düzeni korunur. TV/cihaz davranışı henüz test edilmedi.
- Sürüm kapısı gerçek `v36-baseline` karşısında geçti; shared core etkisi nedeniyle 34 fiziksel modülün sürümü arttı. Sağlayıcı etkinliği değişmedi.
- Hedef depo metadata URL'leri TR+ adresine taşındı. Bağımsız yerel dağıtım aracı için 37 dar test geçti; paketleme yayımlama yapmaz, hash/sürüm/plugin kimliğini doğrular ve gizli/yerel dosyaları dışlar.
- Android 12 gerçek oynatma, The Office otomatik seçim ve yeni sağlayıcı entegrasyonlarının tamamlandığı iddia edilmiyor. Commit, push veya yayın yoktur.

## İlk oturum başlangıç kaydı — 2026-10-16

- v36 çalışma klasörünün hash kayıtlı kaynak yedeği alındı; mevcut dosyalar korundu.
- Başlangıç envanteri, aşama ilerlemesi, sağlayıcı katman durumları ve Türkçe kaynak seçimi teşhisi raporlanıyor.
- Android 12 manuel kabul kontrol listesi ve Türkçe başlangıç/geri dönüş belgesi eklendi.
- Kaynakta `.git` bulunmaması ve başlangıç test ortamı engelleri görünür kılındı.
- **Runtime değişikliği, sürüm artışı, doğrulanmış sağlayıcı onarımı veya yeni entegrasyon yoktur.** Türkçe ses/otomatik seçim ve gerçek oynatma henüz doğrulanmadı. Commit, push ve yayın yapılmadı; final dağıtım üretilmedi.
