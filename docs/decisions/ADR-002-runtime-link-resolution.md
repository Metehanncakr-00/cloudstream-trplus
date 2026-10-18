# ADR-002: Çalışma Zamanı (Runtime) Link Çözümleme ve Kalıcı Veri Ayrımı

## Durum
Bazı eski yaklaşımlarda son video akış linkleri (`.m3u8`, geçici CDN URL'leri) doğrudan eklenti meta verisine veya merkezi bir veritabanına yazılmaktaydı. Ancak bu linkler CDN oturum süreleri dolduğunda veya IP tabanlı imzalandığında birkaç saat içinde çalışmaz hale gelmektedir.

## Karar
Repository içinde hiçbir geçici yayın linki commit edilmeyecektir.
`Episode.data` içerisinde yalnızca kalıcı içerik belirleyicisi (`PlaybackPayload`) tutulacak; oynatma akış linkleri (`ExtractorLink`) kullanıcı oynat butonuna bastığında `loadLinks()` metodu aracılığıyla çalışma zamanında (runtime) çözümlenecektir.

## Sonuçlar
- Linkler her oynatma denemesinde canlı ve taze olarak üretilir.
- Git repository'sinde geçersiz veya süresi dolmuş medya bağlantıları birikmez.
