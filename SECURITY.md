# Security Policy

## Desteklenen Sürümler ve Güvenlik Sorumluluğu

CloudStream TR eklenti deposu, kullanıcı gizliliğini ve yazılım tedarik zinciri (software supply-chain) güvenliğini en üst düzeyde tutmayı hedefler.

## Güvenlik Sınırları ve İlkelerimiz

1. **Sıfır Credential İlkesi**: Bu repository'de hiçbir API anahtarı, kullanıcı adı, şifre, özel erişim belirteci (token) veya yetkilendirme çerezi (cookie) saklanmaz veya istenmez.
2. **DRM ve Erişim Kontrolü Kısıtlaması**: Hiçbir eklenti DRM/Widevine korumasını aşmaya, abonelik/ödeme duvarını (paywall) atlatmaya veya kimlik doğrulama bypass etmeye çalışmaz.
3. **Domain Hijack Koruması**: Eklentilerin canonical domainleri ve izinli host listeleri `config/domains.json` içinde tanımlıdır. Expire olmuş veya el değiştirmiş (kumar/phishing) domainler tespit edildiğinde eklenti derhal `status = 0` (Down) moduna alınır.
4. **Kod Yürütme Güvenliği**: Uzaktan indirilen dinamik JavaScript (`eval()`) veya harici DEX/APK yüklemesi kesinlikle yasaktır.

## Güvenlik Açığı / Kötü Amaçlı Durum Bildirimi

Bir eklentinin ele geçirildiğini (compromised domain), artifact tahrifatını (tampering) veya zararlı içerik sunduğunu fark ederseniz:

- **E-posta**: Lütfen doğrudan proje yöneticisiyle iletişime geçin: `aominemre@gmail.com`
- **GitHub Security Advisory**: Repository üzerindeki Security sekmesi üzerinden özel danışma (private vulnerability report) açabilirsiniz.
- **Rapor İçeriği**:
  - Etkilenen Eklenti Adı
  - Gözlemlenen davranış (yönlendirme adresi, zararlı link, sızıntı)
  - Tekrarlama adımları
  - *Lütfen kamuya açık issue'larda hassas istismar kodlarını paylaşmayınız.*
