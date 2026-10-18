# CloudStream TR+ v42 — Bağımsız kaynak kodu ve dağıtım denetimi

**İncelenen artefaktlar:** Kullanıcının 18 Ekim'de yüklediği `cloudstream-trplus-v42-main(1).zip` ve `cloudstream-trplus-v42-builds(1).zip`.

## 1. Bütünlük ve yayın kimliği

- Kaynak ZIP: **32.078.772 bayt**, SHA-256 `25ff440ece9a8a9bb5663726354dffa29f04b4f30b09ef9b57d5920000824424`; ZIP CRC sağlam; 597 giriş.
- Builds ZIP: **4.499.584 bayt**, SHA-256 `fd0934046c6b3758decaba016dc962753a6c81b908dbc4254b101d68ab2acc93`; ZIP CRC sağlam; 29 giriş.
- `builds` içinde **26 `.cs3`** + `plugins.json` + `repo.json` + `SHA256SUMS` bulunuyor.
- 26 paketin her birinde `manifest.json` ve `classes.dex` var; eklenti JSON sürümleri iç manifestlerle, `fileSize` ve `fileHash` alanları gerçek dosyalarla uyumlu.
- CloudStreamHub sürüm **42**. Diğer eklentilerin sürümleri tutarlı. `repo.json` mevcut GitHub `builds/plugins.json` adresine işaret ediyor.
- ZIP bütünlüğü **Android'de oynatmanın geçtiğini göstermez**. Gradle testleri bu denetim ortamında yeniden çalıştırılmadı.

## 2. Kritik işlevsel boşluklar

### A — Kalıcı katalog indeksinin ana sayfaya bağlanmaması (yüksek)

`CloudStreamHub.kt` `getMainPage()` içinden `UnifiedCatalogEngine.getCategoryPage(...)` çağırırken **`context` parametresi geçmiyor**. Motorun parametresi varsayılan `null`; `HubCatalogCache.get/put` disk okuma-yazmayı yalnızca `context != null` iken yapıyor. Bu nedenle canlı ana sayfa akışında vaat edilen **disk önbelleği devre dışı** kalıyor.

Ayrıca `UnifiedCatalogEngine.crawlBatch()` tanımlı ama proje kaynaklarında ana uygulamadan veya planlayıcıdan **hiç çağrılmıyor**. Bu, kalıcı/sürekli katalog indekslemenin fiilen başlatılmadığını gösteriyor.

### B — Tüm kategori ve tüm sağlayıcı kapsaması garanti değil (yüksek)

`ProviderCapabilityMatrix.getProvidersForCategory(...)` her sağlayıcıdan belirli kategori için yalnızca **`firstOrNull`** ile bir eşleşen kategori alıyor; eşleşme yoksa türden çıkarım yapıp **`cap.categories.first()`** seçebiliyor. Aynı sağlayıcıda birden fazla uygun kategori varsa tümünü toplamak yerine ilkine düşüyor. `ALL_CONTENT` de bir sağlayıcının bütün alt kategorileri anlamına gelmiyor.

`CloudStreamProviderRegistryAdapter.getRegisteredProviders()` **YTS ve 4KHDHub'ı** genel katalog federasyonundan açıkça hariç tutuyor. Bunlar torrent/link çözümünde ayrı işlenebilir; ama "26'sının tüm katalogları tek yerde" iddiasını doğrulamıyor. **26 sağlayıcılı test sentetik mock sağlayıcılarla** koşulmuş, gerçek mobil kayıt mekanizmasıyla değil.

### C — Tek kart kaynaklarının yeniden başlatma/önbellek sonrası korunması garanti değil (yüksek)

`HubSingleCardMerger.buildMergedCard()` referansları `HubMediaRegistry` içine kaydediyor; ama bu registry **yalnızca bellek içi**, üst sınırı **500 öğe** ve diskten restore yolu yok. `HubCatalogCache` JSON'da yalnızca `name/url/type/poster/year/score` saklıyor; `canonicalId` veya `ProviderContentReference` bilgisi saklamıyor. Bu nedenle uzun kullanımda veya yeniden başlatma ardından çoklu kaynağın sürdürülebilirliği kanıtlanmış değil.

### D — Yanlış tekilleştirme ihtimali (orta)

`HubSingleCardMerger.isConfidentSameMedia` kodu, bilinen yıllar arasında **1 yıl farkına izin veriyor** (`diff > 1` engeli) ve yalnızca başlık token benzerliği (`>=0.70`) ile de birleşmeye izin verebiliyor. Aynı isimli farklı yapımları yanlışlıkla birleştirmeme gereksinimi için testler ve kurallar daha sıkı olmalı.

### E — Bellek ve cevap büyüklüğü kısıtları (orta)

`HubCatalogCache.put` tek kategori-sayfa anahtarı için yalnızca **ilk 150 öğeyi** saklıyor. Oysa bir birleşik kategori çok sayıda sağlayıcı sonucu içerebilir. Kesilen öğelerin hangi sonraki sayfada sunulacağına ilişkin koordinasyon görünmüyor.

### F — TV ayarları (doğrulanması gereken)

`CloudStreamHubPlugin.kt` kaydırılabilir içerikten ayrı **sabit alt çubuk**, `Kaydet`, `İptal`, `Varsayılana Dön` düğmelerini ve `HubSettingsManager.save` bağlantısını gerçekten içeriyor. `torrentsAtBottom`, `unknownAudioPolicy`, `disabledProviders`, timeout/concurrency gibi ayarların en azından bazı runtime tüketicileri mevcut. Fakat **Android TV'de görünürlük, kumanda odağı ve gerçek kalıcılık henüz burada çalıştırılarak doğrulanmadı**.

## 3. Kaynak ZIP'in 32 MB olmasının sebebi

`reports/evidence/` altındaki dört geçmiş Gradle günlüğünün sıkıştırılmış toplam boyutu yaklaşık **29,9 MB**:

- `v39-gradle.log` ~10,46 MB sıkıştırılmış, ~147,45 MB açılmış.
- `final-gradle.log` ~8,90 MB sıkıştırılmış, ~125,80 MB açılmış.
- `baseline-sdk35-gradle.log` ~8,83 MB sıkıştırılmış, ~124,71 MB açılmış.
- `v39-critical-final.log` ~1,74 MB sıkıştırılmış, ~24,61 MB açılmış.

Bu günlükler temiz bir kaynak dağıtımının parçası olmak zorunda değil; ZIP'in büyük bölümü geçmiş test kayıtları. Yayından önce kaynak paketinden dışlamak yerinde olur. Günlükler harici yayımlanacaksa hassas bilgi sızıntısı açısından ayrıca kontrol edilmeli.

`V42-COMPLETION-AUDIT.md` adı raporda geçiyor ama **yüklenen kaynak ZIP'inde bulunmuyor**; büyük olasılıkla paketlemeden sonra oluşturuldu. `README-v42.md` ve `TEST-REPORT-v42.md` ZIP'te mevcut.

## 4. Net değerlendirme ve önerilen kapılar

- **PASS (statik):** ZIP CRC, 26 eklenti dosyası, manifestlerin sürüm/size/hash eşleşmesi, depo adresi, CloudStreamHub v42.
- **PARTIAL:** Birleşik katalog kodu, tek kart ve çoklu kaynak akışı, ayarlar; tasarım mevcut fakat yukarıdaki entegrasyon ve kapsam eksikleri bulunuyor.
- **NOT TESTED:** Bu oturumda Gradle/JUnit, Android TV görsel arayüzü ve gerçek video oynatma.
- **BLOCKED:** Sunucu tarafı 404, Turnstile veya bot engelleri olan sağlayıcılarda kesin oynatma garantisi yok. Yeni aktif çizgi film/nostaljik dizi sağlayıcısı görülmüyor; toplam aktif eklenti sayısı 26.

**Yayın öncesi öneri:** (1) CloudStreamHub getMainPage içine gerçek Android context sağlama ve disk cache yolunu test etme; (2) provider × category tüm uygun eşleşmeleri eksiksiz sayfalama; (3) tarama işini güvenli planlayıcıya bağlama; (4) canonical provider reference bilgisini kalıcı ve geri yüklenebilir tutma; (5) tekilleştirme kurallarını sıkılaştırma; (6) gereksiz logları temiz kaynak ZIP'inden çıkarma; (7) emülatörde birkaç kritik senaryoyu gerçek çalıştırma. Bunlar yapılana kadar GitHub'daki çalışan v41 sürümünü koru.
