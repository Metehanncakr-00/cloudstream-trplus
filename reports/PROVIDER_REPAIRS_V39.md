# Sağlayıcı onarımları ve kalan arızalar — v39

Bu rapor önceki envanterin devamıdır, yeni geniş tarama değildir. `PASS` yalnız yazıldığı teknik katman içindir. Hiçbir sağlayıcı Android oynatma PASS veya `IMPLEMENTED_WORKING` ilan edilmez. Bağlı Android cihaz listesi boştur.

| Sağlayıcı / yüzey | Derleme / dar test | Katalog / arama | Ayrıntı / bölüm | Link / medya | Ses | Android playback |
|---|---|---|---|---|---|---|
| DiziYou | Deadline düzeltmesi dahil 15 modül testi PASS | Güncel/legacy kartlar, poster, arşiv dışlama ve UTF-8 query FIXTURE PASS; public HTML biçimi gözlendi | Yapım yılı/synopsis sınırı ve stabil bölüm kimliği FIXTURE PASS | Ortak bounded teslim ve nested deadline regresyonu PASS; önceki HLS/ilk segment/VTT HTTP kanıtı korunur | `_tr` yalnız aday; gerçek TR speech NOT TESTED | NOT TESTED |
| DiziKorea | 3 modül testi PASS | Reserved-character/Unicode query FIXTURE PASS; aynı-host redirect sonrası public arama HTTP 200 / 24 kart | Güncel HTTP ayrıntıda 15 bölüm; normalization/dedup fixture'ı PASS | Üç embed adayı keşfedildi; extractor API → medya → segment denenmedi | NOT TESTED | NOT TESTED |
| BlenderOpenMovies v3 | İlk live/module make PASS; invariant düzeltmesi 13 PASS / 1 optional SKIP; genel gate PASS | Yalnız resmî public PeerTube kanalında katalog/search/load testi; lisans/private/federated kaynak reddi ve canonical alias dedup PASS | Movie payload stabil `/w/UUID`; bölüm N/A | Beş Sintel MP4'ünde HTTP 206/ftyp; ortak bounded teslim/cancellation/fallback/quality testleri PASS | API `language` alanı stream audio doğrulaması değildir; UNKNOWN | NOT TESTED |
| Hub / paylaşılan core | Üç modülde 175 test PASS | Film/dizi/anime/belgesel gibi içerik türünden bağımsız karşılaştırıcı | Başlık özel dalı yok; önceki belirsiz eşleştirme reddi korunur | TR metadata > TR subtitle metadata > known other > unknown; iptal/timeout/alternatif testleri | Metadata testi ≠ duyulan dil | Host auto/track seçimi NOT TESTED |

## DiziYou — giderilen kök nedenler

1. Global header alfabetik arşivi arama sonucu sanılıyordu; gerçek güncel kart `.cat-item` içermiyordu. Sınır gerçek kart + legacy fallback olacak şekilde düzeltildi; homepage arşivi değiştirilmedi.
2. `Yapım Yılı:` değerinin takip eden text node'u okunmuyordu; genel `div.info span` içindeki ilgisiz rakamlar yıl olabiliyordu. Açık alan sınırı ve dört haneli yıl doğrulaması eklendi; bölüm/tarih sayıları yapım yılına dönüşmüyor.
3. `.diziyou_desc` synopsis'i kaçıyordu; metadata alanları açıklamaya karışabiliyordu. Blank olmayan fallback ve metadata sınırı düzeltildi.
4. Sorgu `&/#/+` karakterlerinde URL anlamını değiştirebiliyordu; yalnız query value bir kez UTF-8 encode ediliyor. Normal/native/fallback teslim ve önceki altyazı onarımı korunuyor.

## DiziKorea — giderilen ve giderildiği söylenmeyenler

- Raw query interpolasyonu düzeltildi. Selector, domain, çalışan katalog/bölüm ve extractor mantığı değiştirilmedi.
- Aramadaki 302 bir playback arızası değildir: aynı HTTPS host'taki `/?s=...` gerçek arama sayfasına gidiyor. Dar takipte 200 ve 24 selector kartı görüldü.
- Eski Flex X Cop `SOFT_404_PAGE` helper sonucu, güncel Kotlin sağlayıcısının bozukluğu sayılmadı. Eski fixture'ın gerçekten stale olması ile marker false-positive ayrıştırılmış değil; fixture sırf testi yeşil göstermek için değiştirilmedi.
- PlayerDKorea/diğer embed'lerden gerçek medya ve player kabulü yapılmadı. Bu bilinmezlik için `DEAD` veya upstream `BLOCKED` etiketi kullanılmaz.

## Diğer kaynaklar ve açık onarım kuyruğu

Önceki `HTTP_PROVIDER_STATUS.md` korunur: 11 yalnız HTTP-semantic pass, 10 kısmi, 1 config ve 2 tool destek sınırı vardı. Bu sınıflar plugin veya playback durumu değildir. HDFilmCehennemi ve FilmMakinesi dahil çalışan orijinal provider parser/extractor'ları bu turda spekülatif olarak değiştirilmedi; shared core regresyon kapısına dahil edilecekler.

AnimeciX, BelgeselX, SezonlukDizi, Dizilla, JetFilmIzle, WebDramaTurkey, SetFilmIzle, Dizigecesi, RareFilmm gibi önceki kısmi helper gözlemleri modül başına gerçek kod zinciriyle yeniden üretim gerektirir. Devre dışı dokuz fiziksel modül ve kaynağı olmayan iki kayıt çalıştıkları kanıtlanmadan etkinleştirilmedi. Tüm örnek eserler, mutlak bölüm eşlemesi ve gerçek ses/cihaz kabulü açık kalır.

## Yeni kaynak kapsamı

Blender için resmî PeerTube ve açık CC lisansları kullanılıyor; abonelikli Studio dosyaları, erişimi reddedilen eski aynalar ve kimlik doğrulama kullanılmıyor. Movie/animasyon filmlerine yeni kaynak sağlar; Türk dizisi/anime/belgesel kategorilerinin tümüne yeni modül sağlandığı iddia edilmez. Bu kategoriler için izin/bakım/medya doğrulamalı araştırma ve ayrı modül testleri ana sözleşmede bekliyor. Yalnız kaynak adı eklenmesi başarı değildir; yeni module compile/catalog/search/load/native-link kanıtı ayrı kaydedilir.
