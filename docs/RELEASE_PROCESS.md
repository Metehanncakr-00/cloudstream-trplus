# Sürüm ve Yayınlama Süreci (Release Process)

Bu doküman, bir eklentinin geliştirilmesinden CloudStream kullanıcılarına ulaşmasına kadar olan tüm yayınlama döngüsünü açıklar.

## 1. Uçtan Uca Yayınlama Döngüsü

```
Geliştirme / Düzeltme
       ↓
Modül build.gradle.kts (version++)
       ↓
Pull Request (Validate Workflow çalışır)
       ↓
Main Branch Merge
       ↓
Build & Publish Workflow tetiklenir
       ↓
Gradle: make & makePluginsJson & ensureJarCompatibility
       ↓
.cs3 & plugins.json doğrulanır (SHA-256)
       ↓
builds branch'ine atomik yayın
       ↓
CloudStream İstemcisi Yeni Sürümü Görür ve Günceller
```

---

## 2. Sürüm Artırma Kuralı (Critical)

CloudStream'in eklenti sürümleme mekanizması tamsayı (`integer`) değerine dayanır:

- Bir eklentinin kodunda veya runtime parser mantığında herhangi bir değişiklik yapıldığında modülün `build.gradle.kts` dosyasındaki `version` değeri **mutlaka en az 1 artırılmalıdır**.
- Sürüm artırılmazsa CloudStream yüklü eklentinin yeni sürümünü algılamaz ve kullanıcı güncelleme alamaz.
- Legacy bir eklenti yeniden canlandırılıyorsa:
  $$\text{newVersion} > \text{legacyVersion}$$
  örneğin eski sürüm 97 ise yeni sürüm $\ge 98$ olmalıdır.

---

## 3. builds Branch ve Atomik Dağıtım

- Kaynak dosyalar `main` branch'inde yer alır.
- Üretilen `.cs3` dosyaları ve `plugins.json` manifesti yalnızca `builds` branch'inde bulunur.
- Yayın işlemi atomiktir: Derleme veya hash doğrulaması aşamalarından herhangi biri başarısız olursa `builds` branch'ine hiçbir dosya yazılmaz ve kullanıcıların çalışan mevcut sürümleri bozulmaz.
