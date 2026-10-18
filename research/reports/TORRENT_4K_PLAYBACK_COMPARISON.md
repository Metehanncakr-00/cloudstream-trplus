# 4K torrent oynatma karşılaştırması

İnceleme: 2026-10-05. Kapsam: `research/reports` içindeki 32 rapor, cskraptor envanteri, erişilebilir güncel kaynak kodları ve CloudStream'in mevcut torrent sunucusu. Bu çalışma araştırmadır; yeni bir oynatma düzeltmesi uygulanmadı veya cihazda doğrulanmadı.

## Sonuç

İncelenen CloudStream eklenti kaynaklarında Hub'a taşınabilecek ayrı bir 4K torrent motoru bulunmadı. En somut aday, mevcut CloudStream sunucusunun **seçilen dosyanın başını ve sonunu ön yükleme** özelliğini Hub'ın harici oynatıcı aktarımında kullanmaktır. Bu özellik kaynak kodunda vardır; mevcut `Torrent.transformLink` akışı bunu çağırmadan `&play` bağlantısı döndürür. Sorunun kesin çözümü olduğu henüz kanıtlanmadı.

SkyStream, önbellek/ileri okuma politikasını yönetme açısından yararlı bir örnektir. SkyStream bağımsız Flutter uygulamasıdır; CS3'e takılabilen bir oynatma motoru değildir.

## Mevcut hata kanıtı ve sınırları

- Önceki TV denemeleri: iki farklı Bridgerton 4×6 2160p torrentinde MX yaklaşık iki dakika hazırlanmış, görüntü başlamamıştır.
- Başlangıç ve yaklaşık 2,6 MiB konumlarında HTTP 206 ve hızlı veri okunmuştur; dosya sonu okumaları 12 saniyede yanıt vermemiştir.
- VLC'nin başlangıç başlıklarını okumasından sonra konum değiştirme isteği başarısız olmuş; Matroska ayrıştırıcı dosyayı açamamış ve oynatma ekranı kapanmıştır. Aynı konum sonraki bağımsız istekte erişilebilir olmuştur; kalıcı bir HTTP koşul başlığı hatası kanıtlanmamıştır.
- İç oynatıcıda `UpdatedMatroskaExtractor → SampleQueue.commitSample` hatası ayrıca vardır. Ön yükleme bu uygulama hatasını düzeltmiş sayılmaz.
- Son parçaların hazırlanmasının MX'i beklettiği bir **hipotezdir**. Codec reddi kanıtlanmamıştır. Bu araştırmada TV oynatımı yeniden denenmemiştir.

## cskraptor ve ilişkili YTS/TorraStream kaynakları

Yerel `research/cs-kraptor-provider.json` envanterinde YTS v42'nin açıklaması 4K torrent desteği içerir; yazarları kraptor, Phisher98 ve ByAyzen'dir. Bu envanterde Torrentio/Stremio/TorraStream yoktur. Eski başarılı denemenin YTS filmi olması olasıdır, ancak kullanıcının tam eklenti/içerik bilgisi henüz yoktur.

Güncel [cs-kraptor](https://github.com/Kraptor123/cs-kraptor/tree/64d1ab59a01e048cadf075351b1b9342c133ca97) deposunda eski sağlayıcı kaynakları bulunmuyor. Kontrol edilen beş fork da bu Kotlin kaynaklarını sunmadı. [Cs-Karma](https://github.com/Kraptor123/Cs-Karma/tree/e8fce02f867589802ba3c404eeaad7dc9f80ef8e) Kotlin taramasında gerçek torrent motoru veya magnet çözücüsü bulunmadı. Bu nedenle cskraptor'un özel bir motor kullandığı söylenemez.

İlişkili Phisher fork'u, kesinlikle aynı cskraptor sürümü olduğu iddia edilmeden incelendi: [tehrzky/cloudstream-extensions-xt, 3cff4df](https://github.com/tehrzky/cloudstream-extensions-xt/tree/3cff4df096556225de9c9b1c56a94eb994797cf4).

| Kaynak | Doğrulanan davranış | Hub için anlamı |
|---|---|---|
| [YTS.kt](https://github.com/tehrzky/cloudstream-extensions-xt/blob/3cff4df096556225de9c9b1c56a94eb994797cf4/YTS/src/main/kotlin/com/YTS/YTS.kt#L82) | Sitenin indirme bağlantısını `INFER_TYPE` olarak verir. | `.torrent` metadatası/magnet başlangıcı karşılaştırılabilir; gerçek yanıt türü bu çalışmada denenmedi. |
| [YTS MAX.kt](https://github.com/tehrzky/cloudstream-extensions-xt/blob/3cff4df096556225de9c9b1c56a94eb994797cf4/YTS/src/main/kotlin/com/YTS/YTS%20MAX.kt#L93) | Hash + dinamik tracker listesi → magnet. | Farklı oynatma motoru yok; tracker yenileme yalnızca aday iyileştirme. |
| [TorraStreamExtractor.kt](https://github.com/tehrzky/cloudstream-extensions-xt/blob/3cff4df096556225de9c9b1c56a94eb994797cf4/TorraStream/src/main/kotlin/com/TorraStream/TorraStreamExtractor.kt#L20) | Torrentio JSON → magnet; başka dalda dönen HTTP bağlantısını aktarır. | Normal torrent yolu motoru değiştirmez. `fileIdx` atlandığı için bu davranış kopyalanmamalı. |

Fork README'si GPLv3 veya sonrası bildirir. Kaynak kopyalamadan önce lisans/provenans uyumu gerekir. Tracker, `.torrent` veya ücretli debrid yolu, mevcut iki başarısız torrent için kanıtlanmış çözüm değildir.

## SkyStream'den öğrenilebilecekler

İncelenen sürüm: [32852114a54ad4fcef21251fc4d0d053a65247f4](https://github.com/akashdh11/skystream/tree/32852114a54ad4fcef21251fc4d0d053a65247f4).

- [Torrent hizmeti](https://github.com/akashdh11/skystream/blob/32852114a54ad4fcef21251fc4d0d053a65247f4/lib/core/services/torrent_service.dart#L27): 128 MiB önbellek, %75 ileri okuma varsayılanı; HTTP `/settings` ile ayarlanır.
- Aynı dosyada metadata için saniyede bir, en fazla 120 deneme yapılır. **Metadata bulunması oynatılabilir parçaların hazır olması anlamına gelmez.**
- `/settings` isteğindeki `preload: true` alanının yerel AAR tarafından tanındığı doğrulanamadı. SkyStream'in kesin başlangıç/son hazırlık kontrolü yaptığı iddia edilmemelidir.
- [Native köprü](https://github.com/akashdh11/skystream/blob/32852114a54ad4fcef21251fc4d0d053a65247f4/packages/flutter_torrent_server/android/src/main/kotlin/com/example/flutter_torrent_server/FlutterTorrentServerPlugin.kt#L48): uygulamaya gömülü TorrServer başlatır; ayrı native paket ve yaşam döngüsü vardır.
- [Buffer politikası](https://github.com/akashdh11/skystream/blob/32852114a54ad4fcef21251fc4d0d053a65247f4/lib/features/player/domain/network_buffer.dart#L73): geçmiş veri için pay bırakır. Hub'ın paylaşılan sunucu ayarını değiştirmek diğer torrentlerin belleğini/davranışını da etkileyebilir.
- Alfabetik ilk video, ardından en büyük dosyayı seçme davranışı sezon paketinde yanlış bölüm açabilir. Hub'ın açık dosya indeksini koruma davranışı sürdürülmelidir.
- Real-Debrid/AllDebrid entegrasyonu vardır; hesap/hizmet gerektirir ve mevcut ücretli hizmet eklememe kapsamına dahil değildir.

Kök lisans dosyaları bulunamadı; API lisansı boştu. SkyStream kodunu doğrudan kopyalamak yerine protokolü mevcut kod mimarisinde bağımsız uygulamak gerekir.

## CloudStream'e müdahale etmeden somut aday

CloudStream 4.8 ve incelenen güncel kaynakta bağımlılık `com.github.recloudstream:torrentserver:7861970` olarak belirlenmiştir. Hub MX/VLC hazırlığı `Torrent.transformLink` kullanır; motor değiştirmez.

Sunucunun [stream API'si](https://github.com/recloudstream/torrentserver/blob/7861970/web/api/stream.go#L134) `preload` bayrağını `stat` veya `play` yanıtından önce işler. [Ön yükleme](https://github.com/recloudstream/torrentserver/blob/7861970/torr/preload.go#L74) başlangıçla birlikte dosyanın sonundan en az 8 MiB/piece boyutu kadar bölge okur. İşlem senkrondur. [API yardımcısı](https://github.com/recloudstream/torrentserver/blob/7861970/torr/apihelper.go#L242) ön yükleme miktarını önbellek ve yüzde üzerinden hesaplar; varsayılan 64 MiB/%50 yaklaşık 32 MiB toplam hedeftir. İndeksler gerçek sunucu kaydında sıfır tabanlıdır; `+1` uygulanmamalı.

Önerilen dar deney:

1. Aynı torrent hash'i ve dosya indeksiyle mevcut aktarım ve açık ön yükleme arasında karşılaştırma yap.
2. Motorun ürettiği doğrulanmış loopback bağlantısından `preload`/`stat` işlemini yürüt; başarı HTTP koduyla değil seçilen dosyanın başlangıç/son okumalarıyla ölçülsün.
3. Hazırlık süresi, gerçek peer/seed bağlantısı ve veri hızını kaydet; timeout ve kullanıcı iptalini sınırla. İstemci isteğinin iptali sunucudaki senkron ön yüklemeyi otomatik durdurmuş sayılmamalı.
4. Veriler hazırsa aynı dosyayı MX/VLC'ye aktar; başarısızsa sonsuz arabelleğe alma yerine anlamlı tanı ver.
5. Görüntü, ses ve ileri sarma cihazda doğrulanmadan 4K oynatma düzeltildi denmesin. İç oynatıcının Matroska hatası ayrı kalır.

Bu yol yeni uygulama, ücretli hizmet veya CloudStream fork'u gerektirmeyebilir; uygulanabilirliği ve faydası cihazda ölçülmelidir. Ön yükleme eksik peer/parça sorununu veya codec uyumsuzluğunu sihirli biçimde gidermez. Daha büyük önbellek varsayılan ilk değişiklik olmamalıdır.

## Rapor envanterinin kapsamı

32 rapor sınıflandırıldı. Erişilebilir 27 kaynak arşivi tarandı: 21'i commit SHA'sına sabitlendi; altısı API sınırı nedeniyle HEAD arşivi olarak incelendi. Dört kaynak 404 döndürdü; bir müzik deposu API sınırından sonra indirilmedi. Kaynak içinde sembol bulunmaması, erişilemeyen ikili eklentinin nasıl çalıştığını kanıtlamaz.

| İlgili repo | Sürüm / erişim | Bu incelemenin sonucu |
|---|---|---|
| akashdh11/skystream | 32852114a5 | Ayrı uygulama içinde TorrServer/buffer yönetimi. |
| Ripplay/cloudstream-repo | e5251571a7 | 75 kaynak dosyada torrent motoru uygulaması bulunmadı. |
| blackhope01/cloudstream-plugins | 923137bfa1 | 62 kaynak dosyada torrent motoru uygulaması bulunmadı. |
| MRTDEVM/cloudstream-turkce | b5a1a61d13 | 14 kaynak dosyada torrent motoru uygulaması bulunmadı. |
| anisector/Anz | 7e90ce5c24 | Yedi kaynak dosyada torrent motoru uygulaması bulunmadı. |
| falsisdev/anthology | f1eee8cc4e | Nuvio katalog/sağlayıcı kodu; torrent motoru bulunmadı. |
| manitux-app/cs-plugins | ed264abaa5 | İncelenen sürümde oynatma motoru kaynak kodu yok. |
| pltmustafa/plt-stream | 7267b4ff0f | Dağıtım/yönlendirme; oynatma motoru kaynak kodu yok. |
| ctnkyaumt/cstest | 604710a6e0 | İkili dağıtım; motor kaynak kodu yok. |
| Saloo1575/SalooRepo | HEAD; SHA doğrulanamadı | 44 kaynak dosyada motor bulunmadı; eski boş-repo raporu güncel değil. |
| Wiojelt/TurkSinema, TurkSpor | HEAD; SHA doğrulanamadı | Her birinde bir kaynak dosya; torrent motoru bulunmadı. |
| Wiojelt/WioSinema, WioSpor | HEAD; SHA doğrulanamadı | İncelenebilir motor kaynak kodu yok. |
| neoser1984/cloudstream-extensions | 404 | Eski rapor doğrulanamadı. |
| kaankirsan/akin-cloudstream | 404 | Eski rapor doğrulanamadı. |

Müzik kategorisindeki 16 rapor: sunoh, ViTune, GamerX-Music, Estrella-Music, BitChord, Square, Levyra-deepsound, ApexTunes, N-Zik, NexMusic-Android, NexBeat, VibeFlow, MetroVerse, sakayori-music, Orchard ve vivi-music. Tam adlar ve erişim kayıtları çalışma sırasında oluşturulan `build/research-torrent/peers/coverage.json` dosyasındadır. BitChord eşleşmesi ses ön yüklemesidir; BitTorrent değildir. NexMusic/NexBeat 404, vivi-music API sınırı nedeniyle incelenemedi. Bu gruptan 4K torrent motoru önerisi çıkarılmadı.

## Doğrulama

- Kaynak arşivleri ve ilgili fonksiyonlar incelendi; motor/API iddiaları README ile sınırlı bırakılmadı.
- Önceki cihaz kayıtları ile kaynak kodu karşılaştırıldı; yeni oynatma kanıtı üretilmedi.
- Sağlayıcı, ortak core, sürüm, artifact veya GitHub issue değişikliği yok. Bu belge için uygulama testleri/derleme çalıştırılmadı.
