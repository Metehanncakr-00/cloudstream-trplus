# ADR-003: Güvenli Domain Güncelleme ve Allowlist Politikası

## Durum
Yayın siteleri sık sık domain değiştirmektedir. Ancak süresi dolan veya kapanan domainler kötü niyetli kişiler tarafından satın alınabilmekte (domain hijack) ve kör yönlendirme takibi kullanıcıları sahte/zararlı sitelere maruz bırakabilmektedir.

## Karar
Otomatik kör domain geçişi yasaklanmıştır.
Tüm domainler `config/domains.json` dosyasındaki izinli host listesi (`allowedHosts`) ve içerik bütünlük işaretçileri (`expectedMarkers`) ile denetlenir. Yeni bir domain tespit edildiğinde doğrudan `main` branch'ine basılmayacak, CI tarafından doğrulanmış bir PR oluşturulacaktır.

## Sonuçlar
- Tedarik zinciri saldırıları ve phishing tuzakları engellenir.
- Domain geçişleri şeffaf ve denetlenebilir olur.
