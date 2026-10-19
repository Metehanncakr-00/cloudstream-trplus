# TR+ ONE — Aşama 1 Kaynak Yaması

**Durum:** SOURCE_PROTOTYPE; **Android CS3: NOT_BUILT**; **TV Box: NOT_TESTED**.

Bu paket eski CloudStreamHub'ı değiştirmez. Yeni bağımsız `TRPlusOne` Gradle modülünü ve raporları içerir. GitHub'a hiçbir yayın yapılmadı.

## Güncel projeye uygulama

1. Güncel GitHub `main` kodunun bulunduğu `C:\Users\Metehan\Desktop\cloudstream-trplus-v36` klasörünü açın (klasör adındaki `v36` bir sürüm kanıtı değildir).
2. Bu ZIP'teki `TRPlusOne/` dizinini proje köküne kopyalayın. Dört raporu da köke kopyalayın.
3. `settings.gradle.kts` zaten `build.gradle.kts` içeren ve engellenmeyen yeni modülleri otomatik keşfettiği için onu değiştirmeyin.
4. Windows PowerShell içinde proje kökünde sırayla:

```powershell
.\gradlew.bat :TRPlusOne:testDebugUnitTest --console=plain
.\gradlew.bat :TRPlusOne:make --console=plain
```

5. `TRPlusOne/build/` altında `.cs3` çıktısı oluşursa, **önce derleme ve paket metadata incelemesini bitirin**. `make` görevinin çıktısının gerçek yolunu raporlayın; yol tahmin etmeyin.
6. Fiziksel Android 12 TV Box'ta doğrudan USB/ADB kurulum yoksa GitHub yayını için **ayrı açık onay** isteyin. Eski 25 sağlayıcı ve CloudStreamHub'ı hiçbir koşulda değiştirmeyin.

## Prototipin kapsadığı davranış

- Kaynak dosyaları derleme aşamasında aynı DEX'e eklenen üç bağımsız sağlayıcı: `HDFilmCehennemi`, `SezonlukDizi`, `AnimeciX`.
- Gerçek sağlayıcı `getMainPage()`, `search()`, `load()`, `loadLinks()` fonksiyonlarının adaptasyonu.
- Filmler, Diziler, Anime, Son Eklenenler ana sayfa bölümleri.
- Aynı güvenilir başlık + yıl + medya türüne sahip filmlerin tek kartta toplanması ve özgün URL'lerin korunması.
- Numaraları güvenilir olarak bilinen sezon/bölümlerin ortak bölüm altında birleşmesi; bilinmeyen bölüm numaralarının güvensiz biçimde eşlenmemesi.
- Filmlerde ve dizilerde `Provider • Host` etiketli çoklu kaynak çözümlemesi.
- Yerel, atomik JSON indeksi (`trplusone-index-v1.json`), 2.500 içerik sınırı, sağlayıcı aç/kapat ayarı ve tanılama.

## Bilinen sınırlar

- Sadece üç sağlayıcı eklidir; diğer 22 kaynak henüz birleşmez.
- Gerçek site erişimi, scraper doğruluğu, oynatma, CloudStream host uyumu ve Android DEX paketleme **bu ortamda test edilemedi**.
- AnimeciX filmleri ayrıntıda bölüm tabanlı anime olarak ele alınır; gerçek film moduyla uyumu ayrıca test edilmelidir.
- IMDb/TMDB anahtarları için veri modeli hazırdır ancak bu ilk prototip sağlayıcılardan bu kimlikleri henüz çıkarmamaktadır; eksik yıl varsa farklı sağlayıcıları yanlış birleştirmemek için ayrı kart tutulur.
- İlk açılışta ağ gereklidir, web sağlayıcılar yanıt vermezse boş sayfa görünmesi mümkündür. Boş kart üretilmez.
- `SourcePage` zaman aşımı tüm kaynakların daha sonra mutlaka katalogda görüneceği anlamına gelmez.
- Gerçek CloudStream `APIHolder` ve kardeş eklentilerin classloader erişimi fiziksel host üzerinde test edilmedi. Aşama 0'da "kesin imkânsız" denmesi kanıtlı değildir. Bu nedenle eklenti o mekanizmaya bağımlı değildir.
- Bu prototip güvenlik ve uyumluluk gözden geçirmesi olmadan genel dağıtıma hazır değildir.
