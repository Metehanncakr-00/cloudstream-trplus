# TR+ ONE v2 — Kanıtlanmış Adres Yönlendirme Düzeltmesi (Aşama 2)

Bu sürüm, fiziksel **Android 12 TV Box** üzerinde yapılan canlı testlerde tespit edilen ve kanıtlanan film detayı / oynatma rotası hatasını çözmek için hazırlanmıştır.

---

## 1. Tespit Edilen Canlı Hata ve Kök Neden

Fiziksel TV kutusunda katalog ve indeksleme başarıyla çalışırken (`Indexed=137`, `HDFilmCehennemi=80`, `SezonlukDizi=25`, `AnimeciX=32`, 2–4 saniye yükleme süresi), film kartına basıldığında CloudStream şu hatayı vermiştir:
```
https://trplus.one.invalid/one://media/41bd11428d2324befba9e91
Error loading, try again later.
```

### Kök Neden:
1. `TRPlusOne.card()`, kart URL'si olarak `one://media/{id}` oluşturuyordu.
2. CloudStream dahili mekanizması, `http://` veya `https://` ile başlamayan URL'leri göreceli kabul edip `MainAPI.mainUrl` (`https://trplus.one.invalid`) ile birleştirerek `https://trplus.one.invalid/one://media/{id}` adresine dönüştürüyordu.
3. `TRPlusOne.load()` fonksiyonu ise yalnızca `url.removePrefix("one://media/")` kontrolü yapıyordu. Baştaki domain öneki kalkmadığı için `id` bozuluyor, `OneIndex.media(id)` null dönüyor ve detay sayfası açılamıyordu.

---

## 2. Yapılan Değişiklikler ve Mimari İyileştirmeler

### A. Kanonik ve Tam URL Sistemi (`OneRouteCodec`)
Yeni `OneRouteCodec` modülü oluşturuldu:
* **Kart / Medya Rotası:** `https://trplus.one.invalid/media/{id}`
* **Film Oynatma Rotası:** `https://trplus.one.invalid/movie/{id}`
* **Dizi Bölüm Rotası:** `https://trplus.one.invalid/episode/{id}/{season}/{episode}`

Kartlar artık doğrudan CloudStream'in değiştirmeyeceği tam kanonik URL ile üretilmektedir.

### B. Geriye Dönük Tam Uyumluluk
`OneRouteCodec.parse()`, hem eski hem TV tarafından dönüştürülmüş hem de yeni kanonik adresleri tek noktada güvenle ayrıştırır:
* `https://trplus.one.invalid/media/{id}` -> `OneRoute.Media(id)`
* `https://trplus.one.invalid/one://media/{id}` -> `OneRoute.Media(id)`
* `one://media/{id}` -> `OneRoute.Media(id)`
* `https://trplus.one.invalid/movie/{id}` -> `OneRoute.Movie(id)`
* `https://trplus.one.invalid/one://movie/{id}` -> `OneRoute.Movie(id)`
* `one://movie/{id}` -> `OneRoute.Movie(id)`
* `https://trplus.one.invalid/episode/{id}/{s}/{e}` -> `OneRoute.Episode(id, s, e)`
* `https://trplus.one.invalid/one://episode/{id}/{s}/{e}` -> `OneRoute.Episode(id, s, e)`
* `one://episode/{id}/{s}/{e}` -> `OneRoute.Episode(id, s, e)`

Geçersiz kimlikler, negatif sezon/bölüm numaraları ve yabancı alan adları güvenle `null` dönülerek reddedilir.

### C. Çift Mimari Temizliği
* Kullanılmayan `TRPlaybackDispatcher`, `TRAdapterRegistry`, `TRCanonicalEngine` gibi paralel/ölü paketler temizlendi.
* Tüm yükleme ve oynatma akışı doğrudan `TRPlusOne.kt` içindeki kanıtlanmış motor üzerinden tekilleştirildi. DEX boyutu 679 KB'tan 589 KB'a düşürüldü.

### D. Ayrıntılı Tanılama Sistemi (`OneDiagnostics`)
Aşağıdaki yapılandırılmış olaylar kronolojik ve sansürlü olarak kaydedilir:
* `LOAD_START`
* `ROUTE_PARSED`
* `MEDIA_FOUND` / `MEDIA_NOT_FOUND`
* `PROVIDER_LOAD_START`
* `PROVIDER_LOAD_SUCCESS` / `PROVIDER_LOAD_FAILED`
* `LOAD_RESPONSE_READY`
* `LOAD_LINKS_START`
* `LOAD_LINKS_RESULT`
Süre (ms), sağlayıcı adı ve link sayıları kaydedilir; hassas anahtarlar `[REDACTED]` ile filtrelenir.

---

## 3. Doğrulama Özeti

* **Sürüm:** `2` (v2)
* **Birim Testleri:** 32 / 32 test PASS (`CanonicalEngineTest`: 7, `OneRouteCodecTest`: 12, `TRPlusOneTest`: 13).
* **Derleme Durumu:** `BUILD SUCCESSFUL` (CS3 boyutu: 237,954 bytes, SHA-256: `c2874048f3d371c3e1a5fa1b6a0c604ed0eb5015e556a8ffd45a48b04d3838d8`).
* **Fiziksel Android TV Test Durumu:** `NOT_TESTED` (v2 kodu henüz cihaza yüklenmedi).
