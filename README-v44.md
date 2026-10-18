# CloudStream TR+ v44 — Boş Katalog & Android TV CheckBox Odak Düzeltmesi

`CloudStreamHub` **v44**, Android 12 TV Box gerçek cihaz testinde doğrulanan v43 performans ve Spinner görünürlük iyileştirmelerini koruyarak **ana sayfa kataloglarının boş gelmesi** ve **CheckBox kontrollerinde kumanda (D-Pad) odak vurgusunun görünmemesi** sorunlarını kök nedenlerinden çözer.

---

## v44 Öne Çıkan Düzeltmeler

### 1. Ana Sayfa Katalog Yükleme & Sağlayıcı Fallback Düzeltmesi
- **Semaphore Kuyruk Açlığı Giderildi:** 16 ana sayfa kategorisi paralel açıldığında TMDB destekli satırların 80 canlı HTML kazıma isteğiyle birbirini bloklaması önlendi. TMDB yanıtı geldiğinde önbellekteki sağlayıcı verileriyle anında tek kart altında birleştirilir.
- **Yansımasız (Reflection-Free) TMDB JSON Ayrıştırıcı (`TmdbJsonParser`):** D8 Kotlin metadata yeniden yazımından etkilenmeyen deterministik JSON AST ayrıştırıcı eklendi.
- **Otomatik Sağlayıcı Katalog Fallback'i:** TMDB erişilemezse veya boş dönerse, görünür kategoriler otomatik olarak canlı sağlayıcı kataloglarından doldurulur; hiçbir kaynak yanıt vermezse mevcut geçerli önbellek korunur.
- **Gerçekçi Dil Kategorileri (`Türkçe Dublaj` & `Türkçe Altyazı`):** TMDB'den rastgele İngilizce içerik çekip Türkçe dublaj/altyazı gibi gösterme davranışı kaldırıldı; bu iki kategori yalnızca gerçek dublaj/altyazı kategorisine sahip sağlayıcılardan beslenir.
- **Çoklu-Tür (`TvType`) Kategori Sınıflandırma Düzeltmesi:** `ProviderCapabilityMatrix` içinde `Documentary`/`Anime` destekleyen film/dizi sağlayıcılarının `"Türkçe Dublaj"`, `"Türkçe Altyazı"`, `"Diziler"` ve `"Filmler"` kategorilerini yanlışlıkla `Belgeseller` olarak sınıflandırması düzeltildi.

### 2. Görünür ve Güvenli Katalog Tanılama (`Tanılama` Sekmesi)
- Katalog isteklerinin durumu (HTTP kodu, öğe sayısı, süre ms, hata/fallback aşaması) `HubCatalogDiagnostics` ile kaydedilir ve **Ayarlar -> Tanılama** sekmesinde canlı olarak gösterilir.
- API anahtarları (`api_key`), `Bearer` tokenları, JWT'ler ve Debrid anahtarları otomatik olarak `[REDACTED]` ile maskelenir.

### 3. Android TV Kumanda (D-Pad) CheckBox Odak Vurgusu
- `Genel` (sağlayıcı listesi ve hata raporlama), `Katalog` (16 kategori görünürlük satırı), `Oynatma` ve `Performans` sekmelerindeki tüm `CheckBox` kontrollerine `HubCheckBoxStylePolicy` ile yüksek kontrastlı D-Pad odak vurgusu eklendi:
  - **Odakta (`Focused`):** Vurgu rengi arka plan, 2dp yüksek kontrastlı kenarlık (`#FFFFFF` / `#111111`), kalın beyaz metin ve `▶ [✓]` / `▶ [ ]` odak göstergesi.
  - **Odak Dışında (`Unfocused`):** Tema uyumlu kart arka planı, 1dp kenarlık ve net `[✓]` / `[ ]` seçim durumu.
  - **Katalog Sıralama (`↑` / `↓`):** Kategori sırası değiştirildiğinde kumanda odağı taşınan satırda korunur.

---

## Korunan v43 Kazanımları
- Otomatik arka plan katalog tarayıcısı varsayılan olarak **kapalıdır** (`enableBackgroundCrawler = false`).
- `CloudStreamProviderRegistryAdapter` sağlayıcı örneklerini süreç boyunca yalnızca bir kez oluşturur.
- Spinner seçili değer ve açılır liste yüksek kontrast görünürlüğü (`HubSpinnerStylePolicy`) aynen korunmuştur.
- Diğer 25 eklentiye dokunulmamış, yalnızca `CloudStreamHub.cs3` (`version = 44`) güncellenmiştir.

---

## Doğrulama ve Raporlar
- Kök neden ve kod değişiklik raporu: [`V44-ROOT-CAUSE-AND-FIX-REPORT.md`](file:///c:/Users/Metehan/Desktop/cloudstream-trplus-v36/V44-ROOT-CAUSE-AND-FIX-REPORT.md)
- Birim test ve derleme doğrulama raporu: [`TEST-REPORT-v44.md`](file:///c:/Users/Metehan/Desktop/cloudstream-trplus-v36/TEST-REPORT-v44.md)
