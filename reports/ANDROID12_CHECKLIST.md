# Android 12 telefon / TV kabul kontrol listesi

Tarih: 16 Ekim 2026. **Durum: tüm cihaz kontrolleri NOT TESTED / MANUAL_REQUIRED.** İşaretlenmemiş maddeler PASS değildir. Bu belge test planıdır, başarılı oynatma raporu değildir. v36 kaynak korunur; test edilen `.cs3` sonradan üretilirse dosya adı, sürümü ve SHA256 ayrıca kaydedilir.

## Her koşu için kayıt

- [ ] Telefon/TV modeli, Android 12/API 31 veya 32, mimari, CloudStream uygulama sürümü, eklenti sürümü/artifact SHA256, tarih/saat ve ağ türünü kaydet.
- [ ] Host otomatik oynatma, tercih edilen ses/altyazı dili, dahili/harici player, ses passthrough ayarı ve TV/AVR çıkışını kaydet. Eklenti profilini, torrent açık/kapalı ve debrid var/yok bilgisini ayrı yaz; token kaydetme.
- [ ] Temiz cache ve sıcak cache koşularını ayır. Kurulum/eklenti yükleme sonucunu COMPILE veya gerçek playback yerine kullanma.
- [ ] Eser adı, yıl, US/UK sürümü, TMDB/IMDb kimliği (varsa), sağlayıcı, sezon/bölüm ve seçilen kaynak için güvenli bir koşu kimliği oluştur.

## Kritik The Office senaryosu

- [ ] The Office **US (2005)** S01E01 ile **UK (2001)** S01E01'i ayrı arat; ayrıntı ve bölümün doğru sürüme ait olduğunu doğrula. Yılı olmayan sonuçta ilk sırayı doğru kabul etme.
- [ ] Türkçe dublaj tercihi ile otomatik oynatmayı dene; listede TR/EN adayları, geliş sırası, ilk seçilen kaynak, gerçek ses izi dili ve duyulan konuşmayı ayrı kaydet.
- [ ] EN erken/TR geç geldiğinde aynı denemeyi tekrarla. Sıcak cache, yeniden açma ve farklı ağ gecikmesinde seçim değişiyor mu kaydet.
- [ ] TR kaynak gerçekten yoksa yokluğunu dürüstçe göster; EN/altyazılı alternatif erişilebilir mi, kullanıcı elle değiştirebiliyor mu dene. Var olmayan TR linkini başarılı sayma.
- [ ] İlk kaynak 403, bozuk manifest veya sessiz ses verirse kaynak listesine dön; çalışan alternatife geçiş, hata mesajı ve ilerleme konumu davranışını kaydet. Otomatik fallback var diye varsayma.
- [ ] Özgün ses tercihiyle aynı denemeyi yap; Türkçe altyazıyı Türkçe ses diye değerlendirme. Çoklu ses dosyasında host'un varsayılan ve elle seçilmiş track'lerini ayrı doğrula.

## Diğer içerik ve kimlik örnekleri

Aşağıdakiler test girdisi örnekleridir; mevcut sağlayıcıda bulunabilirlik veya dublaj varlığı sözü değildir. Kullanıcının bildirdiği başka başlıklar varsa ad/yıl/sezon/bölümüyle ayrıca ekle.

- [ ] Breaking Bad (2008) S01E01; Arcane (2021) S01E01; Inception (2010); Avatar (2009) ve Avatar: The Last Airbender (2005) ayrımını kontrol et.
- [ ] Tuzlu Kahve için yıl/kimliği kaynak ayrıntısından kaydet; fragman ile tam bölümün karışmadığını doğrula. Kaynakta kayıtlı test başlığı bulunması canlı bölüm kanıtı değildir.
- [ ] DiziYou'da gerçekten bulunan bir bölüm için normal ve `_tr` adayını ayrı dene; 200 dönen HTML, olmayan TR varyantı ve ses dili belirsizliğini not et.
- [ ] Film/dizi, animasyon/anime ve belgesel örneğinde katalog → arama → ayrıntı → doğru bölüm → link aşamalarını ayrı puanla.

## Medya ve gerçek ses kanıtı

- [ ] Redirect sonrası URL'nin HTML/embed değil medya/manifest olduğunu, MIME türünü ve durum kodunu doğrula. Gerekli header/cookie/referrer zincirinin manifestten segmentlere taşındığını kontrol et.
- [ ] HLS için master + seçili variant + en az başlangıç ve ileri sarma sonrası medya segmenti; ayrı audio rendition varsa audio playlist/segment erişimini de doğrula.
- [ ] DASH için MPD, seçili adaptation/representation, init ve medya segment erişimini doğrula. Düz dosyada range/seek davranışını kaydet. Manifest PASS, tek başına PLAYBACK PASS değildir.
- [ ] Çoklu ses için track ID, bildirilen dil (`tr/tur/en/eng/und`), varsayılan işaret ve gerçekten duyulan dili kaydet. Etiket yanlış/boşsa `UNKNOWN` yaz; adından kesin dil çıkarma.
- [ ] AAC, AC3, EAC3 ve Opus örneklerini ayrı dene; stereo/çok kanallı, dahili hoparlör/HDMI/AVR ve passthrough farklarını kaydet. Başarı yalnız denenen cihaz/çıkış kombinasyonu için geçerlidir.
- [ ] HLS/DASH + H.264/HEVC örneklerinde görüntü, ses, A/V senkronu, en az 2 dakika devam, ileri/geri sarma, duraklat/devam, uygulamaya dönüşü kontrol et. HDR/codec etiketini donanım uyumluluğu kanıtı sayma.
- [ ] Torrent/debrid denenirse kullanıcı tarafından yetkilendirilmiş içerik/hesap kullan; magnet/peer keşfini gerçek medya oynatımı sayma. Harici VLC/MX sonucu ile dahili host sonucunu ayrı raporla.

## TV D-pad ve ayar kalıcılığı

- [ ] Yalnız kumanda D-pad/OK/Geri ile tüm spinner, checkbox, yukarı/aşağı taşıma ve Kaydet/İptal düğmelerine eriş; görünür odak, kaydırma ve odak kaybını dene.
- [ ] Ana sayfa sırası/gizleme, tema/vurgu, performans profili, torrent ve CDN tercihlerini değiştir → **Kaydet** → pencereyi ve uygulamayı yeniden aç; kalıcılığı kontrol et.
- [ ] Aynı ayarları değiştir → **İptal** ve ayrıca Geri ile kapat → yeniden aç; kaydedilmemiş değişikliklerin kalmadığını doğrula. En az bir kategori açık kalması kuralını dene.
- [ ] Eklenti temasının yalnız kendi ayar penceresine etkisini doğrula; host genel temasının değiştiğini varsayma. Mevcut pencerede ayrı ses tercihi seçicisi yok; olmayan kontrolü başarılı test diye işaretleme.
- [ ] Devam oturumunda eklenen **Kaynak ses dili tercihi** spinner'ının üç değerini Kaydet/İptal/Geri akışlarıyla sınayın; legacy ALL/TR_DUB/ORIGINAL değerlerinden açılış seçimini ve uygulama yeniden açılışını kontrol edin. Yukarıdaki ilk envanterde seçici bulunmadığı notu tarihsel durumdur; yeni kontrol henüz cihazda test edilmedi.
- [ ] Host ses izi ve altyazı tercihini değiştirip yeniden aç; host kalıcılığını eklenti ayarlarından ayrı kaydet.

## Güvenli kanıt ve sonuç şablonu

Ekran görüntüsü veya güvenli log kaydı; tarih, sağlayıcı, eser/yıl/kimlik, bölüm, medya türü, maskelenmiş host/path, HTTP durumu, gerekli header **adları**, segment sonucu, seçilen audio track ve cihaz sonucu içermeli. API token, hesap cookie'si, Authorization değeri, kişisel veri ve imzalı URL query'sini paylaşma. Referrer/header değerlerini gerekirse maskele; güvenlik kontrollerini/TLS doğrulamasını devre dışı bırakma.

| Koşu kimliği | Cihaz / host / artifact | Eser / yıl / S-E | CATALOG | SEARCH | EPISODE | LINK + manifest/segment | AUDIO (bildirilen / duyulan) | ANDROID PLAYBACK | Güvenli kanıt / hata katmanı |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Henüz koşulmadı | NOT TESTED | — | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | Kanıt yok |

Hata önce domain/arama/eşleme/extractor/header/manifest/segment/track/player/çıkış katmanına ayrılmalı. Media3 hatasını bu zincir incelenmeden codec sorunu diye sınıflandırma. Sonuç, yalnız kanıtın ulaştığı katmana yazılmalı; Android MANUAL_REQUIRED belirsizliği açık kalabilir, geçmiş testlerden PASS taşınmaz.
