# Provider: Yeşilçam TV (YesilCamTv)

## 1. Genel Bilgiler
- **Eklenti Adı**: YesilCamTv
- **Internal Name**: `YesilCamTv`
- **Sürüm**: 10 (Legacy: 9)
- **Durum**: 1 (Aktif / OK)
- **Dil**: Türkçe (`tr`)
- **İçerik Türleri**: `Movie`
- **Resmi / Canonical Domain**: `https://yesilcamtv.com.tr`
- **İzin Verilen Hostlar**: `yesilcamtv.com.tr`, `www.yesilcamtv.com.tr`

## 2. Mimari ve Yaşam Döngüsü
- **Ana Sayfa (`getMainPage`)**: Son eklenenler ve türler (Komedi, Dram, Macera, vb.) üzerinden içerik listesi çeker.
- **Arama (`search`)**: `/?s={query}` sorgusuyla film arar.
- **Detay (`load`)**: Film başlığı, poster, özet, yıl, oyuncu kadrosu ve türleri ayrıştırır.
- **Oynatma (`loadLinks`)**: Yerleşik video oynatıcı iframe'lerini ve doğrudan HTML5 video linklerini çözümler.

## 3. Güvenlik ve Uyumluluk
- Kamu malı (public domain) ve serbest dağıtılan Türk klasik sinema arşividir.
- Ücretli abonelik veya DRM koruması içermez.
- Fixture testleri: `YesilCamTv/src/test/resources/` altındadır.
