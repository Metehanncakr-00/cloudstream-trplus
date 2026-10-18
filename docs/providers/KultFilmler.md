# Provider: Kült Filmler (KultFilmler)

## 1. Genel Bilgiler
- **Eklenti Adı**: KultFilmler
- **Internal Name**: `KultFilmler`
- **Sürüm**: 32 (Legacy: 31)
- **Durum**: 1 (Aktif / OK)
- **Dil**: Türkçe (`tr`)
- **İçerik Türleri**: `Movie`, `TvSeries`
- **Resmi / Canonical Domain**: `https://kultfilmler.net`
- **İzin Verilen Hostlar**: `kultfilmler.net`, `www.kultfilmler.net`

## 2. Mimari ve Yaşam Döngüsü
- **Ana Sayfa (`getMainPage`)**: Son eklenenler ve tür kategorileri (Aksiyon, Bilim Kurgu, Dram, vb.) üzerinden sayfalama (pagination) destekler.
- **Arama (`search`)**: `/?s={query}` üzerinden HTML scraping yöntemiyle sonuç döndürür.
- **Detay (`load`)**: Başlık, poster, açıklama, yıl, süre, IMDb puanı, oyuncular ve etiketleri çıkarır.
- **Oynatma (`loadLinks`)**: Sayfa içerisindeki embed video player iframe'lerini tespit ederek CloudStream'in yerleşik extractor sistemine aktarır veya doğrudan HTML5 video akışını iletir.

## 3. Güvenlik ve Uyumluluk
- DRM koruması bulunmamaktadır.
- Abonelik veya kimlik doğrulama gerektirmez.
- Fixture testleri: `KultFilmler/src/test/resources/` altında yer almaktadır.
