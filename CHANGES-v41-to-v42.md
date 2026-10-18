# CloudStream TR+ — Değişiklik Günlüğü: v41 -> v42

**Yayın Sürümü:** v42 (Süper Eklenti 2.0 + Sağlayıcı Onarımları + Birleşik Katalog)  
**Tarih:** 18 Ekim 2026  
**Aktif Sağlayıcı Sayısı:** 26  
**Toplam Fiziksel Modül:** 35  

---

## 1. AŞAMA 1 — BİRLEŞİK SÜPER HUB 2.0 & ANDROID TV AYARLARI

### A. Birleşik Katalog Motoru (`UnifiedCatalogEngine`)
- **Typed Birleşik Katalog Katmanı:** TMDB kataloğu ile yerel sağlayıcı kataloglarını tür güvenlikli (`UnifiedMediaItem`, `UnifiedCategory`) tek bir pipeline altında birleştiren motor kuruldu.
- **Sağlayıcı Yetenek Matrisi (`ProviderCapabilityMatrix`):** Sağlayıcıların `mainPage`, `search`, desteklenen `TvType` kümeleri dinamik olarak analiz edilerek 16 standart kategoriye eşleştirildi.
- **Kademeli İndeksleme ve Önbellek (`HubCatalogCache`):** Bellek içi (L1) ve SharedPreferences/disk (L2) önbellek katmanı eklendi. TTL süresi, sınırlandırılmış eşzamanlılık ve talep üzerine yenileme uygulandı.
- **Canlı Birleşik Arama:** Eksik indeks durumunda 26 sağlayıcıya paralel ve zaman aşımlı sorgu gönderen dayanıklı arama yapısı kuruldu.

### B. Tek İçerik, Tüm Sağlayıcılar (`HubSingleCardMerger`)
- **Metadata Eşleştirme Motoru (`HubMatchingEngine`):** TMDB ID, IMDb ID, yapım yılı, normalize edilmiş Türkçe ve orijinal başlıklar kullanılarak aynı film/dizilerin farklı sağlayıcılardaki kopyaları tek bir karta birleştirildi.
- **Yanlış Birleştirmeleri Önleme:** Film-dizi, anime filmi-anime dizisi ve farklı yapım yıllarındaki yeniden çekimlerin birbirine karışması kesin sınırlarla engellendi.
- **Dizilerde Kesin Sezon/Bölüm Hizalama:** Mutlak bölüm numarası hesaplamalarıyla özel bölümlerin yanlış sezonlara bağlanması engellendi.
- **Çoklu Kaynak Toplama:** İçerik detay ekranında tüm çalışan sağlayıcıların kaynakları etiketlendi (Kalite, Sağlayıcı Adı, Dil Bilgisi) ve kullanıcının manuel kaynak seçebilme özelliği korundu.

### C. Android TV Ayar Penceresi Düzeltmesi
- **Görünür Kaydet / İptal Düğmeleri:** `CloudStreamHubPlugin.kt` ayar penceresi yeniden yapılandırıldı. Kaydırılabilir içerik bölgesi (`ScrollView`) ile sabit alt kontrol paneli (`LinearLayout`) ayrıldı.
- **Kumanda & D-pad Desteği:** Android TV 720p ve 1080p ekran ölçeklerinde Kaydet ve İptal düğmelerinin ekran dışına taşması / görünmez olması sorunu giderildi; net odak çerçevesi ve D-pad gezintisi sağlandı.
- **Ayar Yönetimi (`HubSettingsManager`):** Kullanıcı tercihleri (sağlayıcı filtreleri, önbellek temizleme, dil öncelikleri) güvenli şekilde saklanıp yüklenebilir hale getirildi.

---

## 2. AŞAMA 2 — ÖNCELİKLİ SAĞLAYICI ONARIMLARI VE KATEGORİLER

### A. Düzeltilen Sağlayıcılar
1. **SetFilmIzle (Onarıldı):**
   - Ajax yanıtında `subtitles` dizisi Jackson modelinde tanımlanmadığı için oluşan deserialization çökmesi giderildi.
   - `@JsonIgnoreProperties(ignoreUnknown = true)` ve regex fallback eklendi.
   - FastPlay HLS (`X-Sp` token) akışı doğrulanarak oynatılabilir kılındı.
2. **Dizigecesi (Onarıldı):**
   - Eklentide eksik olan `VidmolyTrExtractor`, `VidmolyNetExtractor`, `VidmolyMeExtractor` tescil edildi.
   - Doğrudan `VidmolyTrExtractor` çağrısı ile akış çıkarma güvenceye alındı.
3. **Dizilla (Onarıldı):**
   - Pichive CDN'in `Content-Type: text/html` başlığıyla sunduğu geçerli `#EXTM3U` oynatma listesinin `StreamValidator.kt` tarafından reddedilmesi (Hata 2004) engellendi.
   - Gövde öneki kontrolü eklenerek geçerli HLS akışı ve doğrulanmış Türkçe ses kurtarıldı.
4. **FullHDFilmizlesene (Onarıldı):**
   - RapidVid `_p8` JSON yapısına eklenen `"q"` alanı nedeniyle oluşan Jackson hatası `FAIL_ON_UNKNOWN_PROPERTIES=false` ve `@JsonIgnoreProperties` ile çözüldü; regex fallback ile `cm`/`tm` akışları bağlandı.
5. **FilmMakinesi & HDFilmCehennemi (Genişletildi):**
   - Her iki sağlayıcıya `Animasyon Filmleri` kategorileri eklendi ve Süper Hub `ANIMATION_MOVIES` vitrinine entegre edildi.

### B. Engelli Kaynakların Şeffaf Durumu
- **CizgiMax:** Cloudflare Turnstile koruması nedeniyle `disabled` olarak tutuldu.
- **cizgifilmizle.info:** Gömülü oynatıcıların 403 ve silinmiş YouTube bağlantısı vermesi nedeniyle bağımsız modül olarak eklenmedi.
- **Animeler:** Makisekurisu oynatıcı API'si çözüldü ancak sunucu CDN depolama alanlarının 404 döndürdüğü kanıtlandı (`DEGRADED`).
- **FourKHDHub:** Arama kartları güncellendi ancak indirme bağlantıları `greenmotors.club` bot duvarı arkasında olduğundan engelli (`BLOCKED_WITH_EVIDENCE`).
- **SezonlukDizi:** Cloudflare Turnstile koruması nedeniyle engelli (`BLOCKED_WITH_EVIDENCE`).

---

## 3. AŞAMA 3 — SÜRÜM ARTIRIMLARI, BÜTÜNLEŞTİRME VE PAKETLEME

- **Sürüm Artırımları (Version Bumps):**
  - Paylaşılan çekirdek kod (`core/**`) güncellendiği için 26 aktif sağlayıcının tamamının sürümleri `verify_release_safety.py` kuralları doğrultusunda artırıldı.
  - `CloudStreamHub` sürümü **42** yapıldı.
- **Eklenti Paketleri (`make makePluginsJson`):**
  - 26 aktif sağlayıcının `.cs3` paketleri derlendi ve `build/plugins.json` oluşturuldu.
  - `tools/validate_repo.py --verify-artifacts`: **0 Hata ile geçti**.
  - `tools/verify_release_safety.py`: **0 Hata ile geçti (passed: true)**.
