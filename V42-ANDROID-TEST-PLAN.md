# CloudStream TR+ v42 — Android TV ve Manuel Doğrulama Test Planı
## (V42-ANDROID-TEST-PLAN)

**Hedef Platform:** Android TV (Android 9.0 - 14), Google TV, Android Mobil / Tablet  
**Eklenti Sürümü:** CloudStream TR+ v42 (CloudStreamHub v42 + 26 Sağlayıcı)  
**Tarih:** 18 Ekim 2026

---

## 1. Test Ortamı ve Ön Koşullar

1. **Uygulama:** CloudStream 3 son kararlı veya ön sürüm (pre-release).
2. **Depo Kurulumu:** `repo.json` üzerinden eklenti deposunun eklenmesi veya `cloudstream-trplus-final-v42/builds/` içindeki `.cs3` dosyalarının yerel kurulumu.
3. **Donanım:**
   - 1x Android TV / TV Box (D-Pad kumanda ile).
   - 1x Android Mobil Telefon (Dokunmatik ekran ile).
   - 1x 2.4/5GHz Wi-Fi bağlantısı.

---

## 2. Test Senaryoları Matrisi

### Senaryo 1: D-Pad TV Navigasyonu ve Süper Hub Ayarları
- **Adımlar:**
  1. CloudStream uygulamasında **CloudStreamHub** eklentisinin ayarlarını açın.
  2. Kumanda D-Pad YUKARI/AŞAĞI/SAĞ/SOL tuşlarıyla sekmeler arasında geçiş yapın (Genel, Katalog, Dil, Oynatma, Performans, Tanılama, Görünüm).
  3. Odaklanan her buton veya sekmenin belirgin vurgu rengi (accent color veya beyaz zemin/siyah metin) aldığını doğrulayın.
  4. Sekmeler içindeki kaydırılabilir alanı aşağı doğru kaydırın; en alttaki **`Kaydet`**, **`İptal`** ve **`Varsayılana Dön`** butonlarının TV ekranından taşmadığını ve sabit kaldığını gözlemleyin.
  5. `maxConcurrency` değerini değiştirin ve `Kaydet` butonuna basın.
  6. Eklenti ayarlarını kapatıp tekrar açın; kaydedilen değerin korunduğunu teyit edin.
- **Beklenen Sonuç:** Tüm öğeler D-Pad ile erişilebilir, butonlar ekranda her zaman görünür, ayarlar kalıcı olarak `SharedPreferences`'a kaydedilir.

---

### Senaryo 2: Birleşik Katalog ve Kategori Sayfalama
- **Adımlar:**
  1. CloudStream ana sayfasına gidin ve **CloudStreamHub** ana sayfasını açın.
  2. "Tüm İçerikler", "Filmler", "Diziler", "Türk Dizileri", "Yeşilçam" kategorilerini görüntüleyin.
  3. Bir kategoride sayfanın sonuna kadar kaydırarak sonraki sayfanın (page 2, 3...) yüklenmesini tetikleyin.
  4. Uygulamayı arka plana atıp kapatın ("Zorla Durdur" / Force Stop).
  5. Uygulamayı yeniden açıp aynı kategoriye girin.
- **Beklenen Sonuç:**
  - İlk açılışta veriler hızla yüklenir.
  - İkinci açılışta `cache_v3_*.json` disk önbelleği sayesinde içerikler anında ekrana gelir (ağ beklemesi olmaz).
  - Sayfalarda içerik tekrarı veya kaybolma yaşanmaz.

---

### Senaryo 3: Tek Karttan Çoklu Kaynak (Multi-Source Single Card)
- **Adımlar:**
  1. Popüler bir filmi (örneğin *Interstellar*, *Dune*, *Yıldızlararası*) arayın veya katalogdan seçin.
  2. Birden fazla sağlayıcıda bulunan bu film için **yalnızca tek bir kart** çıktığını doğrulayın.
  3. Karta tıklayarak detay sayfasına girin.
  4. Oynat / Kaynak Seç düğmesine basarak gelen kaynak listesini inceleyin.
- **Beklenen Sonuç:**
  - Farklı sağlayıcılardan (ör. FilmMakinesi, HDFilmCehennemi, JetFilmIzle, FullHDFilmizlesene) gelen akışlar tek bir listede toplanır.
  - Aynı URL'ye sahip yinelenen linkler elenir.
  - Sağlayıcılardan biri zaman aşımına uğrasa bile diğer sağlayıcıların linkleri oynatılabilir olarak sunulur.

---

### Senaryo 4: Soğuk Yeniden Başlatma (Cold Boot) Kalıcılık Testi
- **Adımlar:**
  1. Senaryo 3'teki filmi açıp detaylarını görüntüleyin (bu sırada disk önbelleğine yazılmış olur).
  2. Cihazı yeniden başlatın veya uygulama ayarlarından "Önbelleği Boşalt" (bellek tasfiyesi) işlemi yapın.
  3. Cihaz açıldığında internet bağlantısını kesip uygulamayı açın (çevrimdışı mod testi).
  4. Katalogdaki önceden yüklenmiş karta tıklayın.
- **Beklenen Sonuç:**
  - Kart ve detay ekranı disk önbelleğinden açılır.
  - `HubMediaRegistry` belleği sıfırlanmış olsa dahi diskteki `canonicalId` ve `providerReferences` sayesinden kaynak kökenleri kaybolmaz.

---

### Senaryo 5: Ayrıştırma ve Yanlış Birleştirme Engeli (False-Merge Prevention)
- **Adımlar:**
  1. "Dune" (1984) ve "Dune" (2021) filmlerini aratın.
  2. "Breaking Bad" dizisi ile "El Camino: A Breaking Bad Movie" yapımlarını inceleyin.
  3. "Demon Slayer" TV dizisi ile anime filmini inceleyin.
- **Beklenen Sonuç:**
  - Farklı yapım yıllarına sahip remakeler **kesinlikle ayrı kartlar** olarak listelenir.
  - Film ve dizi türleri asla birleşmez.
  - Sezon 0 (Özel Bölümler) normal bölümlerle karışmaz.

---

## 3. Kabul ve İmza Kriterleri

- [ ] Senaryo 1: TV D-Pad odağı ve ayar kalıcılığı onaylandı.
- [ ] Senaryo 2: Birleşik katalog disk önbelleği ve sayfalama onaylandı.
- [ ] Senaryo 3: Tek karttan çoklu sağlayıcı link akışı onaylandı.
- [ ] Senaryo 4: Soğuk yeniden başlatma sonrası referans kalıcılığı onaylandı.
- [ ] Senaryo 5: Yanlış tekilleştirme koruması onaylandı.

*Not: Fiziksel donanım testleri tamamlandığında sonuçlar bu belgeye imza ve cihaz modeli ile işlenecektir.*
