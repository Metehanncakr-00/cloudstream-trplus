# ARCHITECTURE_PATTERNS

## Tekrarlanan yüksek değerli patternler

| Pattern | Görüldüğü Repolar | Bizim Durum | Öneri | Zorluk |
|---|---|---|---|---|
| Provider-per-module isolation | Bizim Repo, TurkSinema, akin-cloudstream | Var | Koru; shared family base ile dengele | Orta |
| Unified multi-provider resolver | WioSinema | Yok | POC yap; bounded concurrency kullan | Yüksek |
| Shared provider family/base | Ripplay | Sınırlı | Aynı engine kullanan sitelerde uygula | Orta |
| Central domain registry + health | Bizim Repo, TurkSpor | Var/Kısmen | Health score ve runtime ordering ekle | Orta |
| Catalog/resolver separation | Bizim Repo, Anthology | Var | Discovery katmanını genişlet | Orta |
| JS extension runtime | SkyStream | Yok | Kısa vadede kopyalama; uzun vadeli R&D | Çok yüksek |
| Service adapter modules | MetroVerse, VibeFlow | Kısmen | Provider-family adapters | Orta |
| Source health check/fallback | BitChord, TurkSpor, WioSpor | Kısmen | Extractor/provider health modeli | Orta |
| Optional build variants | NexMusic, VibeFlow | Repo için doğrudan yok | Tooling/monitoring features için değerlendir | Orta |
| In-app/plugin observability | SkyStream | Repo düzeyinde yok | Debug manifest/log export standardı | Orta |

## Önerilen prensip

Bizim Repo'nun güvenli `PlaybackPayload`, domain allowlist ve atomik distribution temeli korunmalı. En iyi dış fikirler bu temel üzerine **incremental** eklenmeli; client rewrite yapılmamalı.
