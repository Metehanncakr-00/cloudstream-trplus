# Başlangıç test sonuçları — 2026-10-16

> Bu belge ilk oturumun tarihsel sonucudur. SDK kurulduktan sonra v36 derlemesi tamamlandı, Git tabanı doğrulanarak kuruldu ve sürüm kapısı geçti. **Güncel sonuç: [CONTINUATION_TEST_RESULTS.md](CONTINUATION_TEST_RESULTS.md).** Aşağıdaki eski BLOCKED/FAIL kayıtları güncel durum yerine yorumlanmamalıdır.

Bu sonuçlar runtime değişikliği yapılmamış v36 çalışma kopyasına aittir. Başarılı parser/tooling unit testleri canlı sağlayıcı veya cihaz oynatması değildir.

| Kapı | Sınıf | Sonuç | Kanıt / sınır |
|---|---|---|---|
| Kaynak yedeği ve ZIP CRC | AUTO_REQUIRED | PASS | 445 dosya, hash listesi ZIP içinde; SHA-256 aşağıda |
| Orijinal dosya bütünlüğü | AUTO_REQUIRED | PASS | Python testlerinden sonra 445 dosya yedekle byte/hash aynı |
| `git status --short --branch` | AUTO_REQUIRED | BLOCKED | `.git` bulunmuyor; Git diff/dal/upstream karşılaştırması yapılamadı |
| `python tools/validate_repo.py` | AUTO_REQUIRED | PASS | 0 hata; 34 fiziksel modül / 25 etkin sağlayıcı |
| `gradlew.bat testDebugUnitTest --parallel --continue` | AUTO_REQUIRED | BLOCKED | Java 8 ile yapılandırma başarısız; Kotlin testlerine ulaşılmadı |
| Aynı testler, izole JDK 17, `--console=plain` | AUTO_REQUIRED | BLOCKED | Java engeli aşıldı; `SDK location not found`, `:AnimeciX:testDebugUnitTest` bağımlılık belirlemesinde durdu (30 sn) |
| `python -m unittest discover -s tools/tests -v` | OPTIONAL_DIAGNOSTIC | FAIL | 1 PASS, 12 modül import hatası; pytest suite için yanlış runner + eksik bağımlılıklar |
| İzole ortamda pytest | AUTO_REQUIRED | FAIL | 92 PASS, 1 FAIL, 51 lxml deprecation uyarısı; aşağıya bakınız |
| Upstream intelligence Git-fixture testleri | AUTO_REQUIRED | NOT TESTED | Testler geçici depolarda commit oluşturur; kullanıcı commit yasağı nedeniyle dışlandı |
| `python tools/validate_repo.py --verify-artifacts` | AUTO_REQUIRED | FAIL | `build/plugins.json` bulunamadı; `.cs3` üretimi doğrulanmadı |
| `make makePluginsJson` | AUTO_REQUIRED | NOT TESTED | Önce test/derleme ortamı kapısı çözülmeli; başarısız temele dağıtım üretilmedi |
| Sürüm artışı / Git tabanı | AUTO_REQUIRED | BLOCKED | Kaynak geçmişi yok; checker'ın skip sonucuna PASS denilmedi |
| Canlı katalog/arama/bölüm/link/manifest/segment | OPTIONAL_DIAGNOSTIC | NOT TESTED | Bu çalışmada ağdan sağlayıcı başarı kanıtı üretilmedi |
| Türkçe audio-track / gerçek otomatik seçim | MANUAL_REQUIRED | NOT TESTED | Kaynak adı ve dublaj etiketi doğrulanmış ses kabul edilmedi |
| Android 12 gerçek oynatma ve TV D-pad | MANUAL_REQUIRED | NOT TESTED | adb konvansiyonel konumlarda bulunamadı; cihaz varlığı sorgulanamadı |

## Python çalıştırması

Sistem paketleri değiştirilmeden `tools/requirements.txt` şu sanal ortama kuruldu:

`C:\Users\Metehan\AppData\Local\Temp\opencode\trplus-validation-venv`

```powershell
& 'C:\Users\Metehan\AppData\Local\Temp\opencode\trplus-validation-venv\Scripts\python.exe' -m pytest -q tools/tests --ignore=tools/tests/test_upstream_intelligence.py -p no:cacheprovider --junitxml=C:\Users\Metehan\AppData\Local\Temp\opencode\trplus-baseline-pytest.xml
```

Çıkış: `1 failed, 92 passed, 51 warnings in 27.08s`.

Tek başarısızlık: `tools/tests/test_version_bumps.py::test_verify_version_bumps_on_current_worktree`, satır 34. Test `origin/main` karşılaştırması sonrası başarı mesajını bekliyor. Kaynakta `.git` bulunmadığı için `tools/verify_version_bumps.py:80–81` karşılaştırmayı atlıyor. Bu ortamda sürüm kapısı doğrulanmış sayılmaz. Sırf yeşil sonuç için assertion değiştirilmedi.

Kalıcı JUnit raporu: [evidence/baseline-pytest.xml](evidence/baseline-pytest.xml). İlk çıktı: `C:\Users\Metehan\AppData\Local\Temp\opencode\trplus-baseline-pytest.xml`.

## JDK 17 ile ikinci Gradle denemesi

Üreticinin API metadata'sından alınan Temurin 17.0.20.1+1 Windows x64 ZIP'i `e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0` SHA-256 ile doğrulandı. Yalnız shell süreci için `JAVA_HOME`/PATH ayarlandı; kalıcı sistem kurulumu yapılmadı.

```powershell
$env:JAVA_HOME = 'C:\Users\Metehan\AppData\Local\Temp\opencode\trplus-jdk17\jdk-17.0.20.1+1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat testDebugUnitTest --parallel --continue --console=plain
```

Sonuç: `BUILD FAILED in 30s`, `Could not determine the dependencies of task ':AnimeciX:testDebugUnitTest'` → `SDK location not found`. SDK yolu `ANDROID_HOME` veya `local.properties` ile sağlanmalıdır; bu dosya oluşturulmadı. Kotlin testleri çalışmadı. İki girişimden sonra yeniden deneme yapılmadı. Log: [evidence/baseline-gradle-jdk17.log](evidence/baseline-gradle-jdk17.log).

## Koruma ve belirsizlik

- Kalıcı yedek: `backups/cloudstream-v36-original-20261016-111427.zip`.
- SHA-256: `ce29af53dc53552a7c810e7fefc8e446a6dd07640d7c79cb1e4aa2b27ea06c7d`.
- Upstream commit bilinmiyor; klasörün orijinal v36 ile birebir özdeşliği iddia edilmez.
- Java 8 engeli izole JDK 17 ile aşıldı; kalan otomatik kapı engeli Android SDK'dır. Sistem ayarları kalıcı değiştirilmedi.
- `repo.json` halen upstream plugin listesine işaret ediyor. Hedef depo için geçerli indirme URL'si veya yayın testi yapılmadı.
- Yeni final dağıtım, ZIP main/builds, commit, push veya yayın yoktur.

## Bağımsız kapı sonucu

Verifier, yedek SHA-256/CRC ve manifestini doğruladı: 445 özgün dosyada değişiklik yok. Matrisin 36 satırı mevcut kayıtlarla, 34 kaynak modülüyle ve 25 etkin/11 devre dışı sayısıyla tutarlı; 216 runtime hücresi NOT TESTED. Yapısal validator yeniden PASS. JUnit XML bağımsız olarak 92 PASS/1 FAIL/0 ERROR doğruluyor; 51 uyarı ve 27.08 sn konsol ölçümüdür (XML suite süresi 26.776 sn).

**Belge/koruma kapsamı PASS; genel `VERIFICATION_STATUS: FAIL`.** Nedeni Git geçmişi, SDK, başarısız pytest kapısı ve üretilmemiş paketlerdir; manuel cihaz kapısı otomatik geçmiş sayılmadı. Ayrı reviewer sonucu `REVIEW_STATUS: CLEAN`; belge kapsamındaki somut engelleyici kusur bulunmadı. Bu sonuç runtime onarımı veya ürünün tamamlandığı anlamına gelmez.
