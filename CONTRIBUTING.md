# Katkıda Bulunma Rehberi (Contributing)

CloudStream TR repository'sine yeni eklenti eklemek veya mevcut olanları güncellemek istiyorsanız lütfen aşağıdaki kurallara ve kontrol listesine uyunuz.

## Eklenti Ekleme / Güncelleme Kontrol Listesi (Checklist)

Yeni bir Pull Request açmadan önce aşağıdaki maddeleri doğrulayınız:

- [ ] **Uygun Kaynak (Eligibility)**: Kaynak yalnızca kamuya açık, telif/lisans kurallarına uygun ve erişim izni olan medyaları sunar.
- [ ] **Güvenli İletişim**: Hedef domain HTTPS protokolünü destekler.
- [ ] **Modül Meta Verisi**: `build.gradle.kts` içinde `authors`, `language`, `description`, `status`, `tvTypes` ve `iconUrl` eksiksiz tanımlanmıştır.
- [ ] **Sürüm Politikası**: Kod veya runtime davranışı değiştiğinde sürüm (version integer) artırılmıştır.
- [ ] **Geçici Link Yasağı**: Runtime stream URL'leri (`.m3u8`, geçici CDN token'ları) hardcoded olarak commit edilmemiştir; çözümleme `loadLinks()` içinde dinamik yapılır.
- [ ] **DRM / Kimlik Bilgisi Yok**: DRM bypass, hesap/şifre hırsızlığı, gizli cookie kullanımı yapılmamıştır.
- [ ] **Hata Yakalama & Null-Safety**: Tek bir kırık HTML kartı veya bölüm tüm sayfayı çökertmez (`mapNotNull`, `parsedSafe`).
- [ ] **Fixture Testi Eklendi**: `src/test/resources` altında örnek HTML yanıtları ile parser testleri yazılmıştır.
- [ ] **Yerel Derleme Başarılı**: Modül `./gradlew make` ile başarıyla `.cs3` artifact'ine derlenmiştir.

## Yerel Geliştirme ve Test Komutları

```powershell
# Eklentileri derlemek için:
./gradlew make

# plugins.json üretmek için:
./gradlew makePluginsJson

# Provider birim testlerini çalıştırmak için:
./gradlew test

# Repository ve meta veri doğrulaması:
python tools/validate_repo.py

# Scrapling araç testlerini çalıştırmak için:
python -m pytest tools/tests

# Provider sağlık denetimi (L0-L5) çalıştırmak için:
python tools/provider_health.py
```

