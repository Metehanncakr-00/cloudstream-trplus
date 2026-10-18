# Domain Yönetimi ve Güvenlik İlkeleri (Domain Management)

Türk film/dizi siteleri sık sık domain değiştirdiği için domain yönetimi hem süreklilik hem de **tedarik zinciri güvenliği** açısından en kritik operasyondur.

## 1. Güvenlik Riski: Domain Hijack

Eski bir domain kapatıldığında veya süresi dolduğunda kötü niyetli aktörler (bahis, phishing, zararlı yazılım dağıtıcıları) bu domaini satın alabilir.

Eski Kraptor mimarisindeki kör yönlendirme takip eden otomatik sistemler bu senaryoda eklentiyi sahte bir siteye yönlendirme riski taşımaktaydı.

Yeni mimaride **Kör Otomasyon Yasaktır**.

---

## 2. config/domains.json Mimarisi

Her eklentinin domain yapılandırması açık bir izin listesi (allowlist) ve içerik parmak izi (fingerprint) ile denetlenir:

Çalışma zamanı çözücüsü `main/config/domains.json` dosyasındaki `providers` haritasını okur. Önce `canonical`, ardından varsa `mirrors` adresleri kullanılır; adresler HTTPS olmalı ve hostları `allowedHosts` içinde bulunmalıdır. `allowedHosts` bir URL veya ayna listesi değildir. Geçersiz ya da başarısız yenilemede son geçerli yapılandırma, yoksa sağlayıcının varsayılan adresi korunur.

```json
{
  "schemaVersion": 1,
  "providers": {
    "Example": {
      "canonical": "https://example.org",
      "allowedHosts": [
        "example.org",
        "www.example.org"
      ],
      "expectedMarkers": [
        "Example TV",
        "example-logo"
      ],
      "lastChecked": "2026-09-11T20:00:00Z",
      "status": "active"
    }
  }
}
```

### Alanlar

1. `canonical`: Eklentinin şu anda istek attığı resmi ve doğrulanmış URL.
2. `allowedHosts`: Bu eklenti için geçerli kabul edilen host isimleri. Yönlendirme bu liste dışına çıkarsa anında alarm verilir.
3. `expectedMarkers`: Yanıtın HTML gövdesinde bulunması zorunlu olan ve sitenin sahte olmadığını kanıtlayan içerik işaretçileri.
4. `status`: `active`, `degraded`, `compromised` veya `down`.

---

## 3. Otomatik Domain Güncelleme İş Akışı

1. `.github/workflows/domain-health.yml` periyodik olarak (günde iki kez) çalışır.
2. `tools/domain_health.py` şu kontrolleri yapar:
   - HTTP 301/302/307/308 yönlendirme zinciri incelenir (maksimum 5 hop).
   - Yeni domainin HTTPS kullandığı doğrulanır.
   - Yeni host `allowedHosts` listesinde var mı bakılır.
   - HTML yanıtında `expectedMarkers` aranır.
3. **Doğrulama Başarısız Olursa**:
   - Site kumar/park sayfasına dönüştüyse eklenti `status = 0` yapılır ve bildirim açılır.
4. **Yeni Bir Domain Doğrulanırsa**:
   - Asla doğrudan `main` branch'ine push yapılmaz.
   - Bir bot branch'i ve Pull Request oluşturulur (`bot/domain-update-<provider>`).
   - PR gövdesinde eski domain, yeni domain, yönlendirme zinciri ve parser dökümü yer alır.
   - İnceleme ve onay sonrasında merge edilir.
