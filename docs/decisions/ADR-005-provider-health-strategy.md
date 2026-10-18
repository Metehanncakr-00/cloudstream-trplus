# ADR-005: Katmanlı Eklenti Sağlık Denetim Stratejisi (L0 - L5)

## Durum
Hedef siteleri sürekli ve kontrolsüz şekilde sorgulamak hem hedef sunucularda DDoS algısı yaratabilir hem de IP bloklanmasına yol açabilir. Sadece "siteye HTTP 200 gitti mi" kontrolü ise parser kırılmalarını yakalamada yetersizdir.

## Karar
Katmanlı bir sağlık denetim modeli (L0 - L5) benimsenmiştir:
- **L0 (Configuration)**: Eklenti modülü ve meta verileri geçerli mi?
- **L1 (Domain)**: Domain ve SSL sertifikası erişilebilir mi?
- **L2 (Homepage)**: Ana sayfa ayrıştırılıp en az bir içerik bulunabiliyor mu?
- **L3 (Search)**: Arama fonksiyonu yapılandırılmış sonuç döndürüyor mu?
- **L4 (Load)**: Detay ve bölüm meta verisi başarılı çözümleniyor mu?
- **L5 (Playback Smoke)**: Kamuya açık yetkili akış linki başarıyla tespit ediliyor mu?

Zamanlama: L0 ve L1 kontrolleri daha sık; L2-L5 derin kontrolleri ise makul gecikme ve hız sınırı (rate-limit) gözetilerek günlük çalıştırılır.

## Sonuçlar
- Hedef sistemler gereksiz trafikle boğulmaz.
- Sorunun tam olarak hangi aşamada gerçekleştiği (`NETWORK`, `PARSER`, `NO_LINKS`) net olarak raporlanır.
