# HTTP-only provider diagnosis

UTC: 2026-10-16T09:02:25.376980+00:00

DiziYou excluded (separate current probe). Enabled registry used without configuration changes. Three workers maximum; 15 seconds per fetch. Browser/dynamic/stealth fallback forcibly disabled for all nested helper fetches; isolated external temporary adaptive caches.

**This is shared Python L0-L5 catalog/parser/embed evidence, not Kotlin plugin-runtime or playback certification. Search, runtimePlayback and audio are NOT_TESTED for every row. No provider repairs applied.**

Counts: HTTP_SEMANTIC_PARTIAL=10, HTTP_SEMANTIC_PASS_ONLY=11, CONFIG_UNSUPPORTED=1, TOOL_UNSUPPORTED=2

| Provider | Classification | Home | Detail | Player discovery | Runtime playback | Audio |
|---|---|---|---|---|---|---|
| AnimeciX | HTTP_SEMANTIC_PARTIAL | FAIL | PASS | PLAYER_NOT_FOUND | NOT_TESTED | NOT_TESTED |
| BelgeselX | HTTP_SEMANTIC_PARTIAL | PASS | PASS | PLAYER_NOT_FOUND | NOT_TESTED | NOT_TESTED |
| FilmMakinesi | HTTP_SEMANTIC_PASS_ONLY | PASS | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| 4KHDHub | CONFIG_UNSUPPORTED | FAIL | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| HDFilmCehennemi | HTTP_SEMANTIC_PASS_ONLY | PASS | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| KultFilmler | HTTP_SEMANTIC_PASS_ONLY | PASS | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| YesilCamTv | HTTP_SEMANTIC_PASS_ONLY | PASS | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| SezonlukDizi | HTTP_SEMANTIC_PARTIAL | PASS | PASS | PLAYER_NOT_FOUND | NOT_TESTED | NOT_TESTED |
| SinemaCX | HTTP_SEMANTIC_PASS_ONLY | PASS | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| Dizilla | HTTP_SEMANTIC_PARTIAL | PASS | PASS | PLAYER_NOT_FOUND | NOT_TESTED | NOT_TESTED |
| FullHDFilmizlesene | HTTP_SEMANTIC_PASS_ONLY | PASS | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| JetFilmIzle | HTTP_SEMANTIC_PARTIAL | FAIL | PASS | PLAYER_NOT_FOUND | NOT_TESTED | NOT_TESTED |
| CloudStreamHub | TOOL_UNSUPPORTED | FAIL | UNSUPPORTED_BY_HTML_HELPER | UNSUPPORTED_BY_HTML_HELPER | NOT_TESTED | NOT_TESTED |
| DiziKorea | HTTP_SEMANTIC_PARTIAL | PASS | FAIL | PLAYER_NOT_FOUND | NOT_TESTED | NOT_TESTED |
| DramaDizilerim | HTTP_SEMANTIC_PASS_ONLY | PASS | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| WebDramaTurkey | HTTP_SEMANTIC_PARTIAL | PASS | PASS | PLAYER_NOT_FOUND | NOT_TESTED | NOT_TESTED |
| Animeler | HTTP_SEMANTIC_PASS_ONLY | PASS | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| SetFilmIzle | HTTP_SEMANTIC_PARTIAL | PASS | PASS | PLAYER_NOT_FOUND | NOT_TESTED | NOT_TESTED |
| HDFilmDelisi | HTTP_SEMANTIC_PASS_ONLY | PASS | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| Dizigecesi | HTTP_SEMANTIC_PARTIAL | PASS | PASS | PLAYER_NOT_FOUND | NOT_TESTED | NOT_TESTED |
| RareFilmm | HTTP_SEMANTIC_PARTIAL | FAIL | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| StarTV | HTTP_SEMANTIC_PASS_ONLY | PASS | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| DiziIzle | HTTP_SEMANTIC_PASS_ONLY | PASS | PASS | PLAYER_DISCOVERED | NOT_TESTED | NOT_TESTED |
| YTS | TOOL_UNSUPPORTED | FAIL | UNSUPPORTED_BY_HTML_HELPER | UNSUPPORTED_BY_HTML_HELPER | NOT_TESTED | NOT_TESTED |

## Evidence and interpretation

- HTTP_SEMANTIC_PASS_ONLY means only the shared Python helper found catalog/detail/player markers; it does not prove media or plugin behavior.
- BLOCKED_WITH_EVIDENCE means a Cloudflare/WAF/403/429 access block, not a dead provider.
- CONFIG_UNSUPPORTED includes fail-closed untrusted redirects and missing canonical/allowlist configuration; this is not proof of provider outage.
- TOOL_UNSUPPORTED indicates the probe could not establish provider state; it is not a live provider failure.
- HTTP_PROBE_FAILED and HTTP_SEMANTIC_PARTIAL are observations of this HTTP helper and configured fixture, not definitive Kotlin-provider defects.
- NOT_TESTED can indicate the bounded task budget expired. Adaptive drift uses a fresh isolated cache, not historical fingerprints.

Per-fetch sanitized URLs, response status, fetch status, timings and error categories are recorded in `evidence/http-provider-smoke.json`. No query parameters, cookies, request headers or response bodies are retained.

## Observed non-pass evidence

- **AnimeciX**: https://animecix.tv → SUCCESS HTTP 200; https://animecix.tv/titles/naruto → SUCCESS HTTP 200; homepage: 0 valid content items discovered.
- **BelgeselX**: https://belgeselx.com → SUCCESS HTTP 200; https://belgeselx.com/belgesel/cosmos-bir-uzay-seruveni → SUCCESS HTTP 200.
- **4KHDHub**: https://4khdhub.one → SUCCESS HTTP 200; homepage: 0 valid content items discovered.
- **SezonlukDizi**: https://sezonlukdizi.cc → SUCCESS HTTP 200; https://sezonlukdizi.cc/diziler/breaking-bad.html → SUCCESS HTTP 200; https://sezonlukdizi.cc/breaking-bad/5-sezon-16-bolum.html → SUCCESS HTTP 200.
- **Dizilla**: https://dizilla.now → SUCCESS HTTP 200; https://dizilla.now/dizi/breaking-bad-2 → SUCCESS HTTP 200; https://dizilla.now/breaking-bad-5-sezon-16-bolum-c02 → SUCCESS HTTP 200.
- **JetFilmIzle**: https://jetfilmizle.now → SUCCESS HTTP 200; https://jetfilmizle.now/film/batman-ninja-vs-yakuza-league → SUCCESS HTTP 200; homepage: 0 valid content items discovered.
- **CloudStreamHub**: https://api.themoviedb.org → SUCCESS HTTP 200; https://api.themoviedb.org/3/movie/27205 → SUCCESS HTTP 200; homepage: 0 valid content items discovered.
- **DiziKorea**: https://dizikorea3.com → SUCCESS HTTP 200; https://dizikorea3.com/dizi/flex-x-cop-izle-dq → SUCCESS HTTP 200; detail: SOFT_404_PAGE.
- **WebDramaTurkey**: https://webdramaturkey2.com → SUCCESS HTTP 200; https://webdramaturkey2.com/dizi/hidden-love-izle → SUCCESS HTTP 200; https://webdramaturkey2.com/dizi/hidden-love-izle/1-sezon/1-bolum → SUCCESS HTTP 200.
- **SetFilmIzle**: https://www.setfilmizle.ltd → SUCCESS HTTP 200; https://www.setfilmizle.ltd/film/the-get-out/ → SUCCESS HTTP 200.
- **Dizigecesi**: https://dizigecesi.com → SUCCESS HTTP 200; https://dizigecesi.com/dizi/spartacus → SUCCESS HTTP 200; https://dizigecesi.com/dizi/spartacus/1-sezon/1-bolum → SUCCESS HTTP 200.
- **RareFilmm**: https://rarefilmm.com → SUCCESS HTTP 200; https://rarefilmm.com/2026/07/paris-seveille-1991/ → SUCCESS HTTP 200; homepage: 0 valid content items discovered.
- **YTS**: https://yts.gg → SUCCESS HTTP 200; https://yts.gg/api/v2/movie_details.json → SUCCESS HTTP 200; homepage: 0 valid content items discovered.

## Helper/configuration limitations (not provider outages)

- **4KHDHub:** registry lacks `smokeTest.knownDetail`; detail and player discovery NOT_TESTED. Homepage returned HTTP 200 but the shared selectors found zero valid cards. Classified CONFIG_UNSUPPORTED for the incomplete probe contract, not a live provider failure.
- **CloudStreamHub / YTS:** configured detail fixtures are JSON API endpoints. The shared HTML helper cannot truthfully validate their API detail/player contracts. Classified TOOL_UNSUPPORTED; original helper observations retained explicitly as helper observations in JSON. Catalog HTTP/card observations remain available, but API content cannot establish HTML player discovery.

No Cloudflare/WAF block was detected by the helper in this run. No provider is declared dead or runtime-working.
