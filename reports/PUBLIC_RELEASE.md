# CLOUDSTREAMHUB — PUBLIC RELEASE REPORT

**Date:** 2026-09-13  
**Repository:** `Emre-Kahveci/CloudStreamHub`  
**Target Branch:** `main`  
**Publication Branch:** `builds`  

---

## 1. Security Audit & Secret Scan Gate

A comprehensive automated security scan was conducted covering both the full working tree and all historic commits across all branches (`git log -p --all`).

- **Scanner Findings:** 0 secrets, 0 API keys, 0 private keys, 0 JWTs, 0 credentials.
- **Sensitive Local Files:** `local.properties` was verified untracked and strictly excluded by `.gitignore`.
- **Author Identity & Privacy Warning:** Commit metadata in Git history contains `Emre-Kahveci <aominemre@gmail.com>`. As instructed, historical Git commits were preserved without destructive history rewrites (`git filter-repo` / `filter-branch`). It is strongly recommended that subsequent commits utilize GitHub's privacy-protected no-reply address (`ID+username@users.noreply.github.com`).
- **Permissions Principle:** All GitHub Actions workflows operate under minimal necessary permissions:
  - `Validate`: Default read-only.
  - `Build and Publish`: `contents: write` (required solely for updating the dedicated `builds` distribution branch).
  - `Provider Health Monitoring`: `contents: read`, `issues: write` (for automated health diagnostic issue reporting and auto-recovery closing).

---

## 2. Git Hygiene & Toolchain Portability

- **`.gitignore` Hardening:** Hardened to comprehensively exclude IDE files (`.idea/`, `.vscode/`, `.iml`), build directories (`build/`, `**/build/`), binaries (`*.apk`, `*.aab`, `*.cs3`), certificates/keystores (`*.jks`, `*.keystore`, `*.p12`, `*.pem`), environment secrets, and Python caches.
- **Gradle Wrapper Critical Inclusion:** Whitelisted `!gradle/wrapper/gradle-wrapper.jar` ensuring `gradlew`, `gradlew.bat`, `gradle-wrapper.properties`, and `gradle-wrapper.jar` are fully tracked. Fresh clones on any OS can execute `./gradlew` immediately without prerequisite Gradle installation.
- **`.gitattributes` Normalization:** Configured LF line endings for Kotlin source code (`*.kt`, `*.kts`), configuration (`*.json`, `*.properties`, `*.yml`), scripts, and markdown. Configured CRLF for Windows scripts (`*.bat`, `*.ps1`). Explicitly marked binaries (`*.cs3`, `*.jar`, images).
- **`gradle.properties` OS-Independent Fix:** Removed local Windows-specific absolute JDK paths (`org.gradle.java.home=C:/Program Files/...`) from the repository root. GitHub Actions leverages standard Temurin JDK 17 via `actions/setup-java`. Local development machines maintain user-level configuration via `~/.gradle/gradle.properties`.

---

## 3. Provider Configuration Reconciliation

`config/providers.json` was audited and strictly reconciled against physical modules on disk and `config/domains.json`:

- **Active / Enabled Providers (8 Total):**
  1. `AnimeciX` (Module: `AnimeciX`, Types: `["Anime"]`, Canonical: `https://animecix.tv`)
  2. `BelgeselX` (Module: `BelgeselX`, Types: `["Documentary"]`, Canonical: `https://belgeselx.com`)
  3. `DiziPal` (Module: `DiziPal`, Types: `["Movie", "TvSeries"]`, Canonical: `https://dizipal1430.com`)
  4. `FilmMakinesi` (Module: `FilmMakinesi`, Types: `["Movie", "TvSeries"]`, Canonical: `https://filmmakinesi.to`)
  5. `HDFilmCehennemi` (Module: `HDFilmCehennemi`, Types: `["Movie", "TvSeries"]`, Canonical: `https://www.hdfilmcehennemi.nl`)
  6. `KultFilmler` (Module: `KultFilmler`, Types: `["Movie", "TvSeries"]`, Canonical: `https://kultfilmler.net`)
  7. `TurkAnime` (Module: `TurkAnime`, Types: `["Anime", "AnimeMovie"]`, Canonical: `https://www.turkanime.tv`)
  8. `YesilCamTv` (Module: `YesilCamTv`, Types: `["Movie"]`, Canonical: `https://yesilcamtv.com.tr`)
- **Ghost Provider Resolution (`TrtDizi`):** Marked `enabled: false` and `healthTier: unsupported` with explicit notes to eliminate phantom health check failures.
- **Repository Invariant Enforcement:** `tools/validate_repo.py` enforces bidirectional consistency:
  $$\text{Enabled Config Provider} \iff \text{Root Gradle Module} \iff \text{@CloudstreamPlugin} \iff \text{MainAPI} \iff \text{domains.json Entry}$$

---

## 4. Local Build & Test Verification

All verification gates were executed locally and passed with zero errors:

- **Gradle Clean:** `./gradlew clean` -> `BUILD SUCCESSFUL` (9 actionable tasks executed).
- **Unit Test Suite:** `./gradlew test` -> `BUILD SUCCESSFUL` (All 48 unit tests across 8 modules passed).
- **Package Compilation:** `./gradlew make` -> `BUILD SUCCESSFUL` (Generated 8 `.cs3` packages under `build/`).
- **Repository Indexing:** `./gradlew makePluginsJson` -> `BUILD SUCCESSFUL` (Generated `build/plugins.json` containing 8 plugin metadata objects and SHA-256 hashes).
- **Static & Artifact Validator:** `python tools/validate_repo.py --verify-artifacts` -> `[SUCCESS] Repository validation passed with 0 errors! (8 active providers verified)`.

---

## 5. Automatic Provider Health Monitoring Architecture

Rather than superficial HTTP 200 checks, the repository incorporates an adversarial, multi-tier inspection model:

- **Tier L0 (Configuration):** Cross-references module directory, gradle configuration, manifests, Kotlin annotations, and domain mapping.
- **Tier L1 (Domain Reachability):** Evaluates DNS/HTTPS, redirect chains (up to 5 hops), host allowlist verification, and Cloudflare challenge detection.
- **Tier L2 (Homepage Smoke Probe):** Extracts DOM content cards/links verifying non-empty titles and valid media paths.
- **Tier L3 (Search Probe):** Queries provider search endpoints (HTML or JSON API) using stable search terms (`smokeTest.searchQuery`).
- **Tier L4 (Load Metadata):** Probes detail pages verifying title and media metadata extraction.
- **Tier L5 (Playback Discovery):** Verifies the resolution of player iframes, script player configs, or embed containers without downloading stream bytes.

### Automated Issue Management & Deduplication (`tools/manage_health_issues.py`)
- **Failure Issues:** Opens a structured diagnostic issue (`[Provider Health] <Provider> — <Tier> regression`) if critical failure occurs. Searches existing open issues to avoid duplicates and posts diagnostic comment updates instead.
- **Recovery Auto-Close:** Automatically posts a recovery comment and closes open issues when a provider passes health checks.
- **Domain Change Alerts (`tools/domain_health.py`):** Automatically detects unexpected redirects to new domains and files `[Domain Watch] <Provider> possible domain change`, requesting manual review before updating configuration.
- **Job Summary Matrix:** Formats a markdown table for `$GITHUB_STEP_SUMMARY` and uploads raw JSON reports as artifacts with 7-day retention.

---

## 6. CloudStream Installation URLs

Once published to the `builds` branch via GitHub Actions:

- **Raw Repository Descriptor:**
  ```text
  https://raw.githubusercontent.com/Emre-Kahveci/CloudStreamHub/builds/repo.json
  ```
- **Direct CloudStream App Deep-Link:**
  ```text
  cloudstreamrepo://raw.githubusercontent.com/Emre-Kahveci/CloudStreamHub/builds/repo.json
  ```
- **Plugin Manifest Index:**
  ```text
  https://raw.githubusercontent.com/Emre-Kahveci/CloudStreamHub/builds/plugins.json
  ```

---

## 7. Known Limitations & Recommendations

1. **AnimeciX Cloudflare Protection:** `animecix.tv` enforces Cloudflare browser verification against direct headless scripts. CloudStream on Android bypasses this transparently via internal WebView/OkHttp interceptors. The health monitor truthfully reports `CLOUDFLARE` (degraded) rather than false positive failure.
2. **Dynamic Domain Shifts:** Turkish streaming providers frequently rotate domains due to regulatory blocks. The `domain-watch` workflow is specifically designed to detect these rotations early.
