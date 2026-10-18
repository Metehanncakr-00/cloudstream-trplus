# CloudStream TR+ v45 — Ana Sayfa & Arama Yükleme Düzeltmesi

`CloudStreamHub` **v45**, Android 12 TV Box gerçek cihaz testinde doğrulanan v44 performans, ayarlar, Spinner ve CheckBox D-Pad odak vurgusu düzeltmelerini koruyarak **ana sayfa kataloglarının yüklenememesi ("CloudStreamHub yüklenemedi")**, **132–205 saniyelik gecikmeler** ve **arama sonuçlarının görünmemesi** sorunlarını kök nedenlerinden çözer.

---

## v45 Öne Çıkan Düzeltmeler

### 1. Ana Sayfa Blokajının Kaldırılması & TMDB Ayrımı
- **TMDB Başarısı Canlı Kazımadan Ayrıldı:** TMDB 20 öğe döndürdüğünde harici sağlayıcılarda gereksiz canlı HTML kazıma başlatılmaz. TMDB kartları önbellekteki sağlayıcı referanslarıyla 0ms ağ maliyetiyle birleştirilerek **1–1.5 saniye** içinde ana sayfaya teslim edilir.
- **120s Host Zaman Aşımı Riski Sıfırlandı:** 16 paralel ana sayfa kategorisinin sağlayıcı kuyruğunda 132–205 saniye beklemesi ve CloudStream'in 120s zaman aşımına uğrayarak `"CloudStreamHub yüklenemedi"` hatası vermesi engellendi.
- **Güvenli Fallback:** Canlı kazıma yalnızca TMDB'nin olmadığı (`Türkçe Dublaj`, `Türkçe Altyazı`) veya TMDB'nin başarısız/boş olduğu durumlarda çalışır; bu durumlarda da sağlayıcı sayısı ve zaman aşımı kesin olarak sınırlandırılmıştır.

### 2. Arama Sistemi Onarımı & URL Kodlaması
- **URL Encoding:** Arama sorguları `URLEncoder.encode(query, "UTF-8")` ile güvenli hale getirildi. Boşluk ve Türkçe karakter içeren (`"kurtlar vadisi"`, `"spider man"`, `"yüzüklerin efendisi"`) aramalarda OkHttp `IllegalArgumentException` hatası giderildi.
- **Tüm Arama İmzaları Desteklendi:** `search(query: String)` tek parametreli arama overload'u eklenerek ana uygulama arama çubuğunun agregatöre doğrudan bağlanması sağlandı.
- **Çok Katmanlı Arama Federasyonu:** Arama sonuçları TMDB, taranmış yerel katalog önbelleği ve aktif canlı sağlayıcılardan toplanarak tek kart altında birleştirilir.

### 3. Ayrıntılı Ölçüm ve Canlı Tanılama (`Tanılama` Sekmesi)
- Ana sayfa başlangıcı (`GET_MAIN_PAGE_START`), TMDB kart dönüşümü (`TMDB_PARSE_CONVERSION`), kart birleştirme (`CARD_MERGE`), disk yazma süresi (`DISK_WRITE`) ve tamamlanma (`GET_MAIN_PAGE_SUCCESS`) adımları Tanılama ekranından canlı izlenebilir.
- Olası zaman aşımı veya iptaller `GET_MAIN_PAGE_CANCELLED` olarak kaydedilir; hiçbir hata sessizce yutulmaz.
- Arama çağrıları için `SEARCH_START`, `SEARCH_TMDB`, `SEARCH_PROVIDERS` ve `SEARCH_SUCCESS` tanılamaları eklendi.

---

## Korunan v44 Kazanımları
- Otomatik arka plan katalog tarayıcısı varsayılan olarak **kapalıdır** (`enableBackgroundCrawler = false`).
- D-Pad kumanda CheckBox odak vurgusu (`HubCheckBoxStylePolicy`) ve yüksek kontrastlı seçim rozetleri (`▶ [✓]  ...`).
- Spinner yüksek kontrast görünürlüğü (`HubSpinnerStylePolicy`).
- Yansımasız deterministik TMDB JSON ayrıştırıcı (`TmdbJsonParser`).
- Diğer 25 eklentiye dokunulmamış, yalnızca `CloudStreamHub.cs3` (`version = 45`) güncellenmiştir.

---

## Doğrulama ve Raporlar
- Kök neden ve kod değişiklik raporu: [`V45-ROOT-CAUSE-AND-FIX-REPORT.md`](file:///c:/Users/Metehan/Desktop/cloudstream-trplus-v36/V45-ROOT-CAUSE-AND-FIX-REPORT.md)
- Birim test ve derleme doğrulama raporu: [`TEST-REPORT-v45.md`](file:///c:/Users/Metehan/Desktop/cloudstream-trplus-v36/TEST-REPORT-v45.md)
