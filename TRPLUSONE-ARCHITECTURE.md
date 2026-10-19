# TR+ ONE A1 — Mimari

## Karar

`TRPlusOne` kendi `MainAPI` nesnesini CloudStream'e kaydeder. Üç sağlayıcı sınıfının `.kt` kaynakları Gradle `Copy` göreviyle aynı modülün `classes.dex` derlemesine eklenir. Sibling eklentinin sınıfının reflection ile yüklenmesine güvenilmez. `APIHolder` nesnesinin gerçek cihazda cross-plugin erişilebilirliği **NOT_TESTED**; SDK üzerinden tüm sağlayıcı nesnelerine hiç erişilemez varsayımı bu tasarımın zorunlu ön şartı değildir.

`CloudStreamHub` ile hiçbir runtime, kod veya pref bağımlılığı yoktur. `settings.gradle.kts` otomatik modül keşfeder; mevcut dosya değişmez.

## Veri akışı

`getMainPage(category,page)` → kısa süreli, global `Semaphore(2)` ile sınırlandırılmış sağlayıcı kataloğu → `ProviderListing` → `CanonicalEngine.merge` → `OneIndex.ingest` atomik JSON → `SearchResponse(one://media/id)`.

`load(one://media/id)` → eşleşen gerçek sağlayıcı(lar)ın `load()` işlemi → film metadata veya bölüm birleşimi → `one://movie/id` / `one://episode/id/season/episode`.

`loadLinks(route)` → her eşleşen sağlayıcının kendi `load` / `loadLinks` fonksiyonuna gerçek URL ile delegasyon → CloudStream'e `Provider • Host` etiketli `ExtractorLink`. Başarılı olmayan kaynak hiçbir video bağlantısı üretmez.

TMDB katalog yaratmaz ve oynatılabilirlik kanıtı olarak kullanılmaz.

## Kimlik ve güvenlik

- ID: önce güvenilir IMDb/TMDB (ileride kimlik çıkartıldığında), değilse tip + normalize başlık + yıl.
- Yılı belirsiz iki ayrı sağlayıcının içeriği başlığa bakarak **birleştirilmez**.
- Film/seri, anime filmi/anime serisi ve farklı yapım yılları ayrı kimliktir.
- Dizi bölümleri yalnızca doğrulanmış aynı sezon ve bölüm numarasıyla birleşir; bilinmeyen numaralar atlanır.
- Her sağlayıcı özgün içerik URL'siyle saklanır; oynatma kaynakları başka içeriğe sessizce yönlendirilmez.
- Kimlik verisinin doğruluğu gerçek provider metadata'sı kadar iyidir; çekilen başlık/yıl yanıltıcıysa bunları mutlak kesin olarak kabul etmez.

## Performans

- Başlangıç ağ bütçesi `8.5s`; sağlayıcı isteği `3.5s`; tüm çağrılarda ortak iki eşzamanlı istek sınırı.
- Önce yerel sayfa indeksi sunulur; en fazla 10 dakikada bir arka planda yenileme denenir.
- Geçerli indeks boş ağ sonucuyla silinmez. JSON `AtomicFile` ile yazılır.
- Arama yerel indeksi kullanabilir; arka planda tekrar sorgu yapar.
- Tek bir işlemde 25 kaynağın yanıtları beklenmez; ilk aşamada sadece üç kaynak vardır.

## Eksik özellik / riskler

- Gerçek Android TV CloudStream sürümüyle API imzaları ve CS3 manifest uyumu henüz derlenerek doğrulanmadı.
- Bazı sağlayıcıların anti-bot/anti-DDoS önlemleri katalog ve oynatmayı engelleyebilir.
- AnimeciX'in `load()` metodu anime filmleri için bile `TvSeriesLoadResponse` döndürebilir. Prototip bunu episodik anime olarak ele alır.
- Numaralandırılmamış bölümler gösterilmez; bunlara özgü açıkça adreslenebilir oynatma şeması sonraki aşamada gerekir.
- Uygulama arayüzünde özel kaynak seçici yerine CloudStream'in standart oynatma listesi kullanılır.
- Başlık/yıl bazlı eşleşmede yanlış pozitif riskini azaltmak için belirsiz kayıtlar ayrı kalır; bu nedenle gerçek kataloglarda birkaç tekrar olabilir.
- Birden fazla sağlayıcının tüm medya listesini ilk açılışta tamamen indirme hedefi yok; kontrollü, sınırlı katalog sayfalaması vardır.
