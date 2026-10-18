# Yerel TR+ v39 teslim durumu

**Durum: otomatik kapılardan geçmiş yerel aday; tam master sözleşmesi tamamlanmadı, yayın yapılmadı.** Final klasör adındaki `final`, kullanıcının istediği dizin adıdır; bütün sağlayıcıların çalıştığı veya cihaz kabulünün geçtiği anlamına gelmez. Dağıtım dosyaları `tools/prepare_trplus_distribution.py` aracıyla oluşturulup SHA-256 hash ve içerik kontrolleri doğrulandığında teslim edilmiş sayılır.

## Tamamlanan v39 Geliştirmeleri

- **Doğrulanan Testler:** Bağımsız genel test kapısında **259 Kotlin PASS + 1 optional live SKIP**, **208 Python PASS**.
- **Paket Envanteri:** **26 etkin paket** tam derlenmiş ve `build/plugins.json` ile doğrulanmıştır. `verify_release_safety.py` ile yayımlanmış v38 snapshot'ına karşı sürüm/hash/envanter denetimi 0 hata ile geçmiştir.
- **Türkçe Kaynak ve Ses Önceliği:** Core seviyesinde ortak dil politikası (TR audio metadata > TR subtitle metadata > bilinen diğer audio > bilinmeyen) Hub, DiziYou ve BlenderOpenMovies tarafından kullanılmaktadır. Bilinmeyen veya alternatif kaynaklar silinmez.
- **DiziYou Onarımları:** Arama kartları/arşiv ayrımı, yapım yılı ve synopsis sınırları, query URL encoding, altyazı yolları (`/subtitles/`) ve Hub ile uyumlu nested-deadline bütçesi (8 sn keşif + 6 sn preflight) düzeltildi. 15 dar test PASS.
- **DiziKorea Onarımları:** Query encoding düzeltildi, 3 dar test PASS.
- **BlenderOpenMovies v3 Entegrasyonu:** Resmî public PeerTube kanalı üzerinden açık CC lisanslı, native dosya destekli Movie sağlayıcısı eklendi. 13 dar test PASS (1 optional live test atlandı).
- **CloudStreamHub (Süper Eklenti) v39:** Aggregator adayı bütçesi (128 max / 32 feature reserved) ve federasyon zaman aşımları yapılandırıldı. 106 test PASS.

## Dosya Düzeni ve Yollar

Çalışma kökü: `C:\Users\Metehan\Desktop\cloudstream-trplus-v36`.

- Yerel aday klasörü: `C:\Users\Metehan\Desktop\cloudstream-trplus-v36\cloudstream-trplus-final\`
- Tam Kotlin/Gradle modülleri, ortak core, test, CI, lisans ve Türkçe README: `cloudstream-trplus-final/main/`
- 26 başarılı `.cs3`, `repo.json`, `plugins.json`, `SHA256SUMS`: `cloudstream-trplus-final/builds/`
- Filtrelenmiş Markdown kanıtları/test-count özetleri ve `distribution-status.json`: `cloudstream-trplus-final/reports/`
- Branch-root kaynak ZIP: `cloudstream-trplus-final/cloudstream-trplus-main.zip`
- Branch-root paket ZIP: `cloudstream-trplus-final/cloudstream-trplus-builds.zip`
- Tüm teslim dosyaları ve ZIP hash'leri: `cloudstream-trplus-final/SHA256SUMS`

Paketleme komutu:

```powershell
python tools/prepare_trplus_distribution.py --repository Metehanncakr-00/cloudstream-trplus --published backups/published-builds-20261016T175412Z/plugins.json --output cloudstream-trplus-final
```

Araç `local.properties`, `.git`, SDK/cache, kaynak yedekleri, büyük ham loglar ve hassas kayıtları dağıtıma almaz. Bu dosyaların özgün kanıtları çalışma klasöründeki `backups/` ve `reports/evidence/` altında korunur. Paketlerin yayın durumu **published=false** ve manuel playback **NOT_TESTED** olarak kaydedilir.

## Sağlayıcı ve Türkçe Ses Sınırı

- DiziYou The Office US S01E01 için manifest/ilk segment ve ilan edilen VTT erişimi kanıtlandı. `_tr` URL'sinde gerçek Türkçe konuşma, host otomatik seçim ve çoklu audio track seçimi doğrulanmadı; sağlayıcı **DEGRADED**, `IMPLEMENTED_WORKING` değil.
- Diğer 24 etkin kaynak için HTTP-only ölçümü: 11 yalnız HTTP semantic pass, 10 kısmi, 1 config/2 tool destek sınırı; tablo `HTTP_PROVIDER_STATUS.md` içindedir. Bunlar plugin runtime veya oynatma sertifikası değildir.
- Hiçbir sağlayıcı Android playback PASS ilan edilmedi. `adb devices -l` boş; Android 12/D-pad/kalıcılık/codec senaryoları **MANUAL_REQUIRED / NOT TESTED**.

## Açık Kalan Ana Sözleşme İşleri

Diğer sağlayıcıların modül başına gerçek search/load/episode/loadLinks/manifest/segment/oynatma onarım döngüleri; izin ve bakım durumu kanıtlı yeni sağlayıcı modülleri; tüm örnek eserler/mutlak bölüm numarası; kapsamlı TV cihaz kabulü tamamlanmadı. Açık işler `reports/NEXT_STEPS.md`, `KNOWN_ISSUES.md` ve `ANDROID12_CHECKLIST.md` içindedir.

Commit, push, release, workflow dispatch veya başka GitHub yayın mutasyonu yapılmamıştır. Önceki tüm yedekler korunmaktadır.
