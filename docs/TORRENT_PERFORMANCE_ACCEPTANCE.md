# CloudStreamHub high-bitrate torrent stabilization (PR #21)

## Why this change exists
A valid magnet, a Torrentio stream entry, successful HTTP 206 or passing unit tests **do not** demonstrate 4K Blu-ray/REMUX playback. The reference investigation in `research/reports/TORRENT_4K_PLAYBACK_COMPARISON.md` documents failed Android TV MX/VLC playback, slow final-file ranges, and a separate Media3/Matroska stack failure.

### Implementation status (do not confuse with real-device acceptance)
- **Implemented:** Torrentio file index fail-closed behavior, zero-vs-unknown seed distinction, bounded parallel preflight, two-mirror YTS waves, per-session adaptive source deadlines, quality/compatibility source profiles, bounded LRU magnet cache, local HTTP connection reuse, metadata/range and source timing reports.
- **Limited read-ahead:** Only the optional FEATURE profile increases the initial range probe from 64 KiB to 256 KiB; the plugin does **not** configure TorrServer's native piece priority or playback seek-cache strategy.
- **Playback compatibility:** The settings UI reports advertised HEVC decoder availability and offers advisory source profiles. It does not force Android Media3/MX/VLC decoder selection, HDR handling, audio passthrough, or transcoding.
- **Torrent engine:** Connectivity changes affect the local loopback HTTP transport and mirror discovery. Native BitTorrent peer limits, tracker health, DHT, native cache sizes and seek piece scheduling remain controlled by the host application.
- **Telemetry limitations:** No player-decoded-first-frame, buffer-depth, dropped-frame, native peer throughput or device temperature metrics are available to the plugin through the current integration.

### Protocol facts verified from pinned upstream TorrServer source
- Version `recloudstream/torrentserver@7861970` serves `/stream/<name>?link=...&index=N&preload&stat`.
- Preload is synchronous and prepares beginning/end file pieces before responding.
- The JSON `file_stats` contains zero-based `id`, `path`, `length`. File ID 0 may be omitted from JSON because `omitempty`.
- `/settings` with `action=set` drops all running torrents and restarts the connection. This PR **does not** change global server cache settings.
- Byte-range support comes from `http.ServeContent`; malformed range or wrong selected file must not be presented as playback-ready.

## Scope of runtime changes
1. Check the server-reported file metadata before handing off to external players. If preload HTTP times out or returns a transient error, follow up with a **stat-only** request; media probes are forbidden until selected file identity is verified. Reject missing/ambiguous index, non-video selection and inconsistent file size. Recognize `.m2ts` for Blu-ray transport streams. Do **not** treat `.iso` as directly playable.
2. Retain bounded head/tail byte probes and preserve cancellation. Transient server responses (408/425/429/5xx) retry; invalid/missing range protocol and file identity mismatches fail early.
3. Keep different file indices of a multi-file torrent distinct during source selection (same info hash is not the same episode).
4. Deliver Torrentio/YTS discoveries independently instead of awaiting both before emitting; failures of one source do not cancel valid other results. Emit elapsed milliseconds per batch for benchmarking.
5. Bound aggregation to a 45-second total federation window, plus independent 20-second torrent/direct discovery ceilings; already-emitted links survive expiration. Cap provider callback queues at 128 candidates with a reserved high-quality 32-candidate lane, so late-arriving 2160p/REMUX results are not excluded solely because low-resolution links arrived first.
6. Reserve a fourth 2160p result position for a **different source origin**; at most three of the four 4K candidates may originate from the same source. This prevents a fast YTS response from excluding later Torrentio Blu-ray/REMUX alternatives.
7. Prevent caching resolved short-lived HTTP media URLs from Hub (torrent hash/link candidates only). Only emit cached torrents through the torrent policy **after** fresh sources, never through the direct-media collector (which deliberately rejects magnets).

## Further P0/P1 performance package

Implemented after the first performance baseline:
- **Global HTTP budget:** at most six total concurrent stream preflight requests across all providers and concurrent Hub invocations, in addition to the existing per-provider concurrency limit of three.
- **Top-K feature lane:** publish one 4K candidate early, reserve the remaining 31 feature slots for the best later candidates based on resolution, peer evidence and selected source profile; flush after each provider finishes. This staging may delay later 4K results until provider completion, so measure p95 first usable link.
- **YTS first-success:** each two-mirror wave returns the first non-empty, valid movie/IMDb-matched result and cancels the slower sibling; no-success waves continue to other mirrors.
- **Single-flight lookups:** concurrent non-debrid requests for the same Torrentio/YTS title join the same in-flight request. Cache only torrent/magnet candidates for 30 seconds, never a signed/direct URL. A *confirmed empty Torrentio streams array* is negatively cached for 10 seconds. Parsing/network errors, a failed mirror wave, seed-based exclusions or missing payload are **not** negatively cached. Debrid-specific responses bypass shared memoization.
- **Read-only TorrServer network snapshot:** the existing `preload&stat` or stat-only response can report download speed (converted to Mbps), active peers, connected seeders, preload completion and estimated average video bitrate when duration metadata exists. Stats are optional. They do not report a decoder's rendered first frame, buffer time at the current seek position, or assured sustained throughput.

### Remaining host-dependent integrations (not implemented)
- Continuous TorrentServer piece availability, peer bandwidth sampling during active playback and seek-time piece reprioritization require a dedicated host-facing API integration or a verified host change. The current plugin only reads optional point-in-time transfer stats during readiness.
- Native Media3 playback events (first frame, dropped frames, current buffer position, decoder errors) require hooks in CloudStream itself. VLC/MX external-player internals cannot be observed from a normal CloudStream extension action.
- Do not claim a high-bitrate title plays successfully based on `preload`, `HTTP 206`, CI success, peer count or bitrate estimate. Actual 2160p REMUX with lossless audio and seeking remains a manual release gate.
- A larger buffer, more peers, or host settings changes should be considered **only after** comparing TV cold-start, playback stalls and seek latency; some global TorrServer settings changes disrupt active torrents.

### Regression gates
- Global active preflight count never exceeds six across four simultaneous providers.
- First YTS usable response wins without waiting for the slower mirror, and cancellation is preserved.
- Two same-key lookups perform one outbound operation. Direct/CDN links, transient errors and filtered candidates cannot enter positive/negative cache.
- Late REMUX candidates can replace worse waiting 4K candidates without increasing the memory cap. Already delivered links are never silently retracted.
- TorrServer metrics are parsed only from valid stat responses, redacted, and never promoted to proof of decoded playback.

## Homepage and plugin appearance personalization (v35)

- **Scope:** CloudStreamHub provider home page only. Ten predefined TMDB sections support independent show/hide and up/down ordering in the plugin settings. User settings are saved in Android SharedPreferences and loaded **before** registering the provider on startup.
- **Network savings:** Disabled sections are removed from the provider's `mainPage` list and a stale/snapshotted home request for a hidden section returns an empty response without making a TMDB HTTP request.
- **Host refresh:** CloudStream's `APIRepository` caches the provider's home-page list when it is constructed. A full CloudStream restart or refreshing/reselecting the provider may be necessary after saving to rebuild the host's list.
- **Safe defaults:** All ten sections are enabled in their previous order on fresh installs. Unknown or duplicate persisted IDs are ignored/deduplicated, and at least one section always remains enabled.
- **Appearance:** Light/Dark/System applies to **CloudStreamHub settings dialogs**, with six accent presets tinting the Hub's section headings and controls. The CloudStream application's global theme, player overlays and other providers are not changed: the CloudStream plugin SDK has no global-theme override.
- **Security:** Theme and home preferences do not log debrid credentials or affect torrent access controls.

### Manual acceptance
1. Open plugin settings: confirm the new “Görünüm ve tema” and “Ana sayfa kişiselleştirme” controls appear.
2. Disable **Popüler Filmler**, save, restart CloudStream, select CloudStreamHub home; verify the row is absent while **Popüler Diziler** remains.
3. Re-enable Popüler Filmler, move it above Günün Trendleri, save and restart; verify row order is preserved.
4. Disable nine sections and try disabling the tenth: at least one must remain enabled. Reset to the default configuration if needed.
5. Select Koyu + Turkuaz and reopen settings; verify dialog/heading color change. The host app theme should remain unchanged.
6. Restart again; confirm preferences persist. Confirm search, 1080p, 4K/HDR10+ and torrent settings still work.

## Profile behavior
- AUTO: 90s preparation budget, 64KiB first-range probe, peer-health-led source ranking.
- COMPATIBLE: AUTO-sized probe and a soft source-ranking penalty for demanding format labels. This is not a codec compatibility guarantee.
- FEATURE: 120s preparation budget, 256KiB first-range probe and a positive REMUX ranking bonus. Both are capped and remain opt-in; download peer availability still controls playback.
- The current playback profile is persisted locally and can be changed from CloudStreamHub settings. Diagnostics record stage, status class and elapsed milliseconds only; no magnet URLs or tokens.
- In-memory torrent cache: max 64 entries, 30-minute TTL. Only magnet/torrent candidates; HTTP media/CDN URLs are always re-resolved.

## Performance methodology
Record p50 and p95 across repeated runs, for *each* host/version/content type, and include both cold and warm states:
- Time to first **source**; time to first **decoded frame**; time to audible output.
- Selected torrent file hash and index (redact private info), peers/seeders, download speed, time to head/tail byte range.
- Seek time at ~25%, ~75% of duration; playback stalls over a 10-minute run; cancellation latency.
- Memory peak and device temperature/thermal throttling, if available.
- 1080p single-file MKV; 2160p HEVC; 2160p Blu-ray REMUX with TrueHD/DTS-HD; multi-file series torrent; zero/low peer negative case.
- CloudStream built-in, VLC and MX Player on **actual Android TV**, separately. Never infer codec support from the torrent title alone.

## Obtaining a candidate for device verification
- Open the **Validate** GitHub Actions run from this PR. Once checks complete successfully, download the `CloudStreamHub-pr-...` workflow artifact, unzip it, and verify it contains `CloudStreamHub.cs3`.
- This is **not** a production release. The workflow does not push to `main` or `builds`; the artifact is intended for controlled manual installation according to the CloudStream host's supported local testing method. If local plugin installation is unavailable, do not publish the unverified candidate through the production repository; use an isolated test distribution.
- Run the same device/content cases with both the current production plugin and the PR candidate; record CloudStream version, TV model, player, first frame, audio, 25%/75% seek and 10-minute buffering.
- Artifact success proves packaging, **not** runtime 4K decode or torrent swarm reliability.

## Before-and-after repeatable acceptance cases
1. Cold single-file 2160p HEVC (licensed test media): initial source, first frame, audio track, 10-minute stall count.
2. Multi-file 2160p REMUX: index verified; expected movie/episode selected; seek to 25%, 75%; audio.
3. TorrServer slow/missing peers: controlled timeout, no infinite buffering; preserve user cancellation.
4. Unsupported HEVC/HDR/TrueHD/DTS-HD: friendly diagnosis rather than marking source as playable.
5. Simulate large provider output: max three parallel HTTP preflights; bounded callback queue; 2160p alternatives not silently dropped.
6. Run on an Android TV and a phone, recording model, CloudStream host version, player, network conditions and all diagnostic stages.
7. Compare AUTO vs FEATURE. If FEATURE is slower without fewer playback stalls, return to AUTO and investigate the native engine before increasing cache.

## Mandatory manual checks before marking playback resolved
- [ ] On Android TV, selected movie/episode matches intended file and stream index.
- [ ] 2160p MKV/Remux first frame and audio work on real device, not just HTTP probes.
- [ ] Seek to middle and late part works without indefinite buffering.
- [ ] No false ready state for 404/403/500, malformed response, wrong file, subtitle/sample, or expired local server.
- [ ] Repeated runs show stable memory and no growth from duplicate resolver calls.
- [ ] The user's torrent consent and cancellation paths remain intact.
- [ ] GitHub CI `Validate` passes; no unrelated provider version bumps.

## Explicitly NOT claimed
No device verification was performed by this patch. Peer availability, network throughput, native decoder support, and external player bugs can still prevent playback. This does not patch the CloudStream application, change TorrServer internals or globally force cache sizes.
