# PERFORMANCE_FINDINGS

## Networking
- Bounded parallel provider fan-out: WioSinema tipi aggregation için gerekli.
- Request deduplication: aynı detail/player URL'sine eşzamanlı istekleri tek future altında birleştirme.
- Shared client/connection reuse: Bizim Repo NiceHttp katmanında standardize edilmeli.
- Timeout budget: tek tek extractor timeoutlarından ziyade toplam resolution budget.

## Parsing
- Selector compilation/central parser helpers; aynı CMS ailesinde reusable parser.
- JSON endpoint varsa HTML scrape yerine API-first tercih.
- Early validation ile yanlış detail/search sonuçlarını parse etmeden eleme.

## Extraction
- Health-score tabanlı source ordering.
- Direct stream bulununca configurable early termination.
- Fallback chain telemetry: hangi extractor ne kadar sürede/başarıyla sonuç veriyor.

## Search
- Concurrent provider search ancak device profile'a göre concurrency cap.
- Query normalization ve duplicate result merging.
- TMDB-ID resolver ile bazı sağlayıcılarda site içi search'ü atlama (WioSinema hipotezi).

## Caching
- Metadata-only kısa TTL cache.
- Ephemeral stream URL cache'lenmemeli veya çok kısa/expiry-aware tutulmalı.
- Negative cache: kısa süreli 404/provider down sonucu.

## Benchmark gerekliliği
Hiçbir rakip yaklaşım yalnız kod görünümüne bakılarak "daha hızlı" ilan edilmemiştir. Aşağıdaki KPI'lar ölçülmelidir: p50/p95 search, p50/p95 loadLinks, HTTP request count, cache hit ratio, timeout rate, memory delta, cold-start build/plugin load.
