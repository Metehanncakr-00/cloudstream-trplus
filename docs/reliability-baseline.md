# Reliability baseline (main@984dea4)

This inventory records the checkout used to implement the reliability roadmap. It is not a second provider registry.

## Module and test discovery

- `settings.gradle.kts` discovers top-level directories containing `build.gradle.kts`, excluding a fixed list of disabled/utility directories.
- The current checkout has 34 provider module build files. `build.gradle.kts` adds `core/src/main/kotlin` to every provider's main source set and adds `core/src/test/kotlin` only to `AnimeciX`'s test source set.
- The active-provider test inventory is:

  | Module | Existing parser/behavior test |
  |---|---|
  | AnimeciX | `AnimeciXParserTest` |
  | Animeler | `AnimelerParserTest` |
  | BelgeselX | `BelgeselXParserTest` |
  | CloudStreamHub | Five Hub behavior/transport test classes |
  | DiziIzle | `DiziIzleTest` |
  | DiziKorea | `DiziKoreaParserTest` |
  | DiziYou | `DiziYouParserTest` |
  | Dizigecesi | `DizigecesiParserTest` |
  | Dizilla | `DizillaParserTest` |
  | DramaDizilerim | `DramaDizilerimParserTest` |
  | FilmMakinesi | `FilmMakinesiParserTest` |
  | FourKHDHub | No fixture test existed; added card parsing fixture coverage |
  | FullHDFilmizlesene | `FullHDFilmizleseneParserTest` |
  | HDFilmCehennemi | `HDFilmCehennemiParserTest` |
  | HDFilmDelisi | `HDFilmDelisiParserTest` |
  | JetFilmIzle | `JetFilmIzleParserTest` |
  | KultFilmler | `KultFilmlerParserTest` |
  | RareFilmm | `RareFilmmParserTest` |
  | SezonlukDizi | `SezonlukDiziParserTest` |
  | SetFilmIzle | `SetFilmIzleParserTest` |
  | SinemaCX | `SinemaCXParserTest` |
  | StarTV | `StarTVTest` |
  | WebDramaTurkey | `WebDramaTurkeyParserTest` |
  | YTS | No provider mapping fixture existed; added API-to-search mapping coverage |
  | YesilCamTv | `YesilCamTvParserTest` |

  The enabled-module test task in validation and build workflows is `./gradlew testDebugUnitTest --parallel --continue`; it is the PR gate for these tests. Disabled modules are excluded by Gradle settings, so their existing tests do not run in that gate.

## Current regression/health coverage

- `DynamicDomainResolverTest` already covers allowlisted canonical/mirror normalization, rejects hosts outside the allowlist and unsafe URI forms, and verifies default fallback when no validated endpoint exists.
- `CoreTest` covers default headers and basic diagnostic redaction. Python `tools/tests/test_redaction.py` covers header, body, URL, and smoke-report redaction.
- `SafeHttpClient.getDedup` currently keys in-flight requests only by URL and stores a `Deferred` created in the first caller's coroutine scope. It has no focused concurrency, cancellation, or failure cleanup test.
- `StreamCacheManager` has no focused tests. TTL uses wall-clock time directly; empty writes are ignored and `clear()` clears all entries.
- Core network consumers mix direct `app.get` calls with a small number of `SafeHttpClient` wrappers. There is no evidence yet for one repeated, common retry policy; retries are therefore not presumed safe or added as part of the baseline.

## CI and health separation

- `.github/workflows/validate.yml` runs schema/version checks and unit tests on pull requests.
- `.github/workflows/build.yml` runs tests, builds `.cs3` packages, generates `plugins.json`, and validates artifact consistency before publishing.
- `.github/workflows/provider-health.yml` runs scheduled domain/provider health and L6–L8 playback-matrix tooling separately, then uploads reports. This avoids treating fixture tests as live playback evidence.

## Build dependency controls

- Root `build.gradle.kts` uses fixed versions for declared dependencies (the Cloudstream Gradle plugin is commit-pinned), but no Gradle dependency verification metadata or dependency lockfiles are present.
- GitHub Actions were originally referenced by mutable major-version tags; workflow references are now pinned to full commit SHAs.
- Dependency locking is enabled for project and buildscript configurations with strict lock mode. Each active Gradle module has a generated lockfile, and Gradle dependency verification checks SHA-256 metadata.

## Upstream comparison inputs

- The report identifies `etumen/cs-kraptor` as the reference fork and `Kraptor123/cs-kraptor` as its source upstream. The tool added for this roadmap treats these as comparison inputs and the local checkout's `origin` default branch as the target; it emits evidence only and does not modify repositories.
- Upstream comparison runs from weekly/manual workflow dispatch, reports JSON and Markdown as a retained workflow artifact, and runs its local Git-fixture tests in pull request validation.
