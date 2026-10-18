# Link Çözümleme Mimarisi (Link Resolution)

Bu doküman, oynatma verisinin kalıcılığı, `PlaybackPayload` tasarımı ve `loadLinks()` pipeline'ının detaylarını açıklar.

## CloudStreamHub kaynak seçimi

Hub, paketlenmiş etkin sağlayıcıları ayrıca kurulmalarını gerektirmeden kullanır. HTTP kaynakları hazır oldukça iletilir; Torrentio ve YTS adayları birlikte değerlendirilir. Sağlayıcı taraması, eşzamanlılık izni alındıktan sonra 30 saniye ile sınırlıdır ve aynı anda en fazla dört federasyon sağlayıcısı çalışır.

Bulunan her çözünürlükte en fazla üç farklı torrent hash'i seçilir. Önce farklı kaynaklar, sonra seed sayısı ve sabit eşitlik sırası tercih edilir. Aynı hash'in tracker veya görüntülenen ad farklılıkları yeni bir alternatif oluşturmaz. Dosya indeksi eşdeğer adaylarda YTS tercih edilir; açıkça verilen Torrentio dosya indeksi değiştirilmez. HTTP/debrid bağlantıları bu kotaya dahil değildir. Önbellekteki torrentler, güncel sonucu olmayan kaynaklar için yedektir.

Hub payload'ındaki isteğe bağlı `category` alanı `general`, `animation` veya `documentary` olabilir; önceki payload'lar geçerliliğini korur. Bölümlü içeriklerde istenen sezon/bölüm bulunamazsa başka bir bölüm açılmaz. Belgeselin film veya bölüm biçimi gerçek `load()` yanıtıyla doğrulanır.

API yanıtı, seed sayısı, extractor callback'i veya derleme başarısı oynatma kanıtı değildir. Medya erişimi ve cihazda oynatma ayrı doğrulanır; dış kaynakta bulunmayan bir kalite listede üretilmez.

## 1. Kararlı Veri (Stable) vs. Geçici Veri (Ephemeral)

| Tür | Açıklama | Saklama Yeri | Örnekler |
|---|---|---|---|
| **Kararlı Veri (Stable)** | Zamanla değişmeyen, içeriğin veya bölümün sabit referansı. | `Episode.data` veya `LoadResponse.data` | `contentId`, `episodeId`, `pageUrl`, `season/episode` |
| **Geçici Veri (Ephemeral)** | Birkaç dakika veya saat geçerli olan oturum ve yayın akışı linkleri. | Asla Git'te veya Payload'da tutulmaz; yalnızca `loadLinks` runtime anında üretilir. | `.m3u8` playlist, `.mp4` CDN linki, imzalı query parametreleri (`?token=xyz`), oturum çerezleri |

> [!CAUTION]
> Asla geçici yayın URL'lerini commit etmeyiniz veya sabit bir veritabanında saklamayınız. Bunlar CDN oturumu sona erdiğinde bozulacaktır.

---

## 2. Tip Güvenli PlaybackPayload Tasarımı

Bölüm verisi tek bir ID ile temsil edilemiyorsa düzensiz metin ayırıcılar (`id|url|season`) yerine JSON serileştirilmiş veri modeli kullanılmalıdır:

```kotlin
@Serializable
data class PlaybackPayload(
    val contentId: String,
    val episodeId: String? = null,
    val pageUrl: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val sourceName: String? = null
)
```

JSON dönüşümü için CloudStream'in standart Jackson veya KotlinX Serialization modülü kullanılır:

```kotlin
// Episode üretirken:
val payload = PlaybackPayload(
    contentId = "film-123",
    pageUrl = episodeUrl
)
val episodeData = AppUtils.toJson(payload)

newEpisode(episodeData) {
    this.name = "Bölüm 1"
    this.season = 1
    this.episode = 1
}
```

---

## 3. loadLinks Pipeline ve Extractor Entegrasyonu

```kotlin
override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    // 1. Payload çözümleme
    val payload = try {
        AppUtils.parseJson<PlaybackPayload>(data)
    } catch (e: Exception) {
        PlaybackPayload(contentId = data, pageUrl = data)
    }

    val targetUrl = payload.pageUrl ?: return false

    // 2. Sayfa ve player kaynağını çekme
    val doc = app.get(targetUrl).document
    val videoSources = extractPlayerSources(doc)

    var foundAny = false

    // 3. Extractor veya doğrudan akış üretimi (StreamValidator ile doğrulanmış)
    val count = BoundedParallelResolver.resolveProgressive(
        candidates = videoSources,
        provider = name,
        resolver = { src, emitLink ->
            if (src.isDirectStream) {
                val preflight = StreamValidator.validateStream(
                    url = src.streamUrl,
                    headers = mapOf("Referer" to mainUrl),
                    provider = name
                )
                if (preflight.isValid) {
                    emitLink(
                        newExtractorLink(
                            source = name,
                            name = "$name Stream",
                            url = src.streamUrl,
                            referer = mainUrl,
                            quality = Qualities.Unknown.value,
                            type = preflight.streamType
                        )
                    )
                }
            } else {
                loadExtractor(src.embedUrl, referer = mainUrl, subtitleCallback, emitLink)
            }
        },
        onLinkFound = { link ->
            callback(link)
            foundAny = true
        }
    )

    return foundAny || count > 0
}
```

---

## 4. Altyazı Pipeline'ı

Altyazı dosyaları tespit edildiğinde CloudStream'in `newSubtitleFile` veya `SubtitleFile` arayüzü ile bildirilir:

```kotlin
subtitleCallback(
    newSubtitleFile(
        lang = "tr",
        url = subtitleUrl
    )
)
```
ISO 639-1 dil kodları (`tr`, `en`, vb.) tercih edilmelidir.

## 5. Torrent kaynaklarını dış oynatıcıda açma

CloudStreamHub, CloudStream 4.8.0 üzerinde torrentler için iki ayrı bölüm menüsü seçeneği ekler: `CloudStreamHub: Torrent → VLC` ve `CloudStreamHub: Torrent → MX Player`. İlgili oynatıcı kurulu olduğunda görünürler; MX seçeneği ücretsiz `com.mxtech.videoplayer.ad` sürümünü kullanır.

Bölüme uzun basın, Hub dış oynatıcı seçeneğini seçin ve kaynak listesinden istediğiniz torrenti açın. Eklenti, seçilen torrentin dosya indeksini değiştirmeden CloudStream torrent sunucusunun oluşturduğu HTTP akışını oynatıcıya aktarır. Mevcut oturumda torrent izni verilmemişse P2P onayı istenir.

Bu aktarım CloudStream APK’sını ve varsayılan oynatıcı ayarını değiştirmez. Torrentin eşlere bağlanamamasını, cihazın codec/çözünürlük sınırlarını veya dış oynatıcının desteklemediği ses biçimlerini gidermez. CloudStream arka planda kapatılırsa yerel torrent akışı kesilir.

v30, HTTP bağlantısı oluşturulduktan sonra seçilen dosya için sunucunun `preload`/`stat` işlemini çağırır. Oynatıcı açılmadan önce dosyanın başından ve sonundan en fazla 64 KiB gerçek veri okunur; HTTP 206, Content-Range, dosya boyutu ve okunan veri uzunluğu doğrulanır. Dosya indeksi değiştirilmez. Bu hazırlık en fazla 90 saniye sürer; başarısızsa oynatıcı açılmaz ve başka kaynak denenmesini isteyen hata gösterilir. Sunucunun bağlantıyı oluşturduğu önceki bootstrap aşaması bu 90 saniyeye dahil değildir.

Kullanıcı iptali HTTP isteğini durdurur; sunucuda başlamış senkron ön yükleme devam edebilir. Eklenti paylaşılan sunucuyu kapatmaz, torrent silmez veya önbellek ayarını değiştirmez. Başlangıç/son verisinin hazır olması sürekli oynatma, ses, codec desteği veya ileri sarma başarısı anlamına gelmez; bunlar cihazda ayrıca doğrulanmalıdır.
