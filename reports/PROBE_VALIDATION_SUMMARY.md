# Probe Diagnostics Validation Summary

This document captures high-level, sanitized diagnostics evidence produced by `tools/provider_probe.py`.
Raw JSON traces contain ephemeral network captures, request IDs, and session metadata; therefore, raw probe JSON files are excluded from source control (`.gitignore: reports/probe_*.json`).

---

## 1. Executive Summary

| Provider | Probe Target URL | Fetch Mode | Browser Used | Challenge Observed | Content Cards | Captured Requests (by Type) | Result |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- | :---: |
| **AnimeciX (HTTP Baseline)** | `https://animecix.tv/` | HTTP | No | None | 0 (SPA client render) | Total: 0 | SUCCESS (HTTP 200) |
| **AnimeciX (Stealth Session)** | `https://animecix.tv/` | STEALTH | Yes (Chromium) | None observed | 0 (SPA client render) | XHR: 5<br>Fetch: 0<br>Script: 68<br>Media: 0<br>WebSocket: 0<br>**Total: 73** | SUCCESS (HTTP 200) |
| **KultFilmler (Dynamic Session)** | `https://kultfilmler.net/` | DYNAMIC | Yes (Chromium) | None observed | 15 content cards | XHR: 13<br>Fetch: 6<br>Script: 23<br>Media: 0<br>WebSocket: 0<br>**Total: 42** | SUCCESS (HTTP 200) |

---

## 2. Probe Details

### 2.1 AnimeciX — Stealth Investigation
- **Target URL**: `https://animecix.tv/`
- **Execution Mode**: `STEALTH` (Playwright Chromium via `StealthySession`)
- **Status**: SUCCESS (`HTTP 200`, ~1400ms)
- **Anti-Bot Observation**: Stealth browser probe executed; no challenge observed.
- **Homepage Structure**: SPA client-side rendering (card extraction via static DOM yields 0 cards; dedicated API search endpoint `/secure/search/{query}` is functional).
- **Network Request Breakdown**:
  - `XHR`: 5
  - `Fetch`: 0
  - `Script`: 68
  - `Media`: 0
  - `WebSocket`: 0
  - **Total**: 73 requests intercepted and sanitized.

### 2.2 KultFilmler — Dynamic Session & Player Discovery
- **Target URL**: `https://kultfilmler.net/`
- **Execution Mode**: `DYNAMIC` (Playwright Chromium via `DynamicSession`)
- **Status**: SUCCESS (`HTTP 200`, ~950ms)
- **Anti-Bot Observation**: No challenge encountered.
- **Homepage Content**: 15 content cards parsed. Top item: *Soldier – Asker*.
- **Detail & Player Analysis**:
  - Followed detail URL: `https://kultfilmler.net/soldier-asker/` (HTTP 200).
  - Discovered stream embed player: `https://vidpapi.xyz/video/...` (`PLAYER_DISCOVERED`).
- **Network Request Breakdown**:
  - `XHR`: 13
  - `Fetch`: 6
  - `Script`: 23
  - `Media`: 0
  - `WebSocket`: 0
  - **Total**: 42 requests intercepted and sanitized.

---

## 3. Security and Sanitization Policy
1. **Raw Network Traces Excluded**: Probe JSON outputs (`reports/probe_*.json`) are local/CI diagnostic artifacts and are strictly ignored in `.gitignore`.
2. **Sanitization Engine (`XhrRedactor`)**:
   - Sensitive headers (`Authorization`, `Cookie`, `Set-Cookie`, `x-e-h`, `x-*-token`, `x-*-auth`, `x-*-key`, `api-key`) are redacted to `<REDACTED>`.
   - Sensitive JSON body tokens (`siteToken`, `accessToken`, `refreshToken`, `csrfToken`, `authToken`, `apiToken`, `clientSecret`, passwords, credentials, keys) are redacted to `<REDACTED>`.
   - Streaming URLs (.m3u8, .mpd, .ts) have security tokens/query parameters stripped to `<REDACTED_QUERY>`.
