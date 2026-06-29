# Changelog

All notable changes to Pilot are documented here. Each entry corresponds to a
released version tag and is built from the commits between that tag and the
previous one. This project loosely follows [Keep a Changelog](https://keepachangelog.com)
and [Semantic Versioning](https://semver.org).

## [0.9.1] - 2026-06-29

### Build
- Auto-generate CHANGELOG.md on release
- Bump androidx.compose:compose-bom (#15)
- Bump gradle-wrapper from 9.5.1 to 9.6.0 (#14)
- Bump actions/checkout from 6 to 7

### Docs
- Refresh README for public release

## [0.9.0] - 2026-06-22

### Build
- Move the signing key out of the repo (local-only keystore).

## [0.8.0] - 2026-06-22

### Changed
- Make the radio country configurable instead of hardcoded to Romania.

## [0.7.0] - 2026-06-22

### Added
- Dynamic ntfy topic via QR scan / paste pairing.

### Docs
- Add README.

## [0.6.4] - 2026-06-14

### Fixed
- Show a "Converting…" spinner instead of a frozen Maps screen during fallback.

## [0.6.3] - 2026-06-14

### Added
- Foreground retry loop for the papko Maps→Waze fallback.

## [0.6.2] - 2026-06-14

### Changed
- Drop the resolver self-test; split the fallback counter by cause.

## [0.6.1] - 2026-06-13

### Fixed
- Resolve Maps destinations from the share subject coordinates.

## [0.6.0] - 2026-06-13

### Added
- Resolve Google Maps links to Waze on-device, with papko as fallback.

### Build
- Add `make check` (assembleDebug + unit tests + lint).

## [0.5.0] - 2026-06-11

### Added
- Author and sync Discover categories to the headunit.

## [0.4.1] - 2026-06-11

### Fixed
- Stop treating coroutine cancellation as a publish/search failure.

## [0.4.0] - 2026-06-11

### Build
- Migrate to AGP 9.2.1 with built-in Kotlin, compileSdk 37 (interim: Kotlin 2.4.0, AGP 8.13.2, compileSdk 36).
- Run `testDebugUnitTest` in release.sh (AGP 9 dropped release unit tests).
- Add Dependabot weekly updates + CI auto-merge for patch/minor.
- Dependency bumps: okhttp 4.12.0 → 5.4.0, coroutines-test (#1), compose-bom,
  activity-compose (#3), serialization-json (#5), core-ktx 1.13.1 → 1.18.0 (#12),
  datastore-preferences (#11), org.json 20240303 → 20260522, gradle-wrapper 8.9 → 9.5.1.

## [0.3.0] - 2026-06-08

### Added
- Show the app version on the Diagnostics screen.

## [0.2.3] - 2026-06-08

### Fixed
- Run share ingest in a foreground service to keep the metered network alive.

## [0.2.2] - 2026-06-08

### Changed
- Log process importance / background-network restriction before publish.

## [0.2.1] - 2026-06-08

### Changed
- Make ntfy publish resilient to transient network failures.

### Build
- Add `scripts/version.sh` + Makefile (version/release/wrapper targets).
- `bootstrap-wrapper.sh` finds SDKMAN gradle when not on PATH.

## [0.2.0] - 2026-06-07

Initial released version. Highlights of the work leading up to it:

### Added
- Catalog model: `Form` enum (playlist/song/destination), `CatalogEntry` with JSON
  serialization, and `CatalogStore` backed by DataStore.
- Share intake — classify YT Music share URLs into playlist/song; fetch title +
  image via oEmbed / OG scrape; `ShareReceiverActivity` intent filter; manual paste.
- Maps & Waze sharing — classify Maps/Waze URLs, `MapsToWazeConverter` (waze.papko.org
  302 redirect), host-allowlist normalizer, and a `DestinationPipeline` orchestrating
  classify → convert → save → publish; `cmd=maps` "Send as Maps" that navigates directly.
- Romanian radio station discovery and sending.
- ntfy wire schema up to v3 (form, title, imageUrl); YT Music URL built on the phone.
- UI: two-tab catalog with image tiles, long-press rename/delete, pull-to-refresh
  metadata recovery, Places tab with manual-add dialog, promote-tapped-item-to-top.
- Persistent in-app diagnostics log to investigate share flakiness.

### Changed
- Rename package `be.doccle.pilot` → `com.vladutu.pilot`.
- Dark automotive-cockpit redesign (PilotTheme, segmented control, status pill);
  Destinations tab renamed to Places; new launcher icon.

### Build
- Release automation — wrapper, signing, `release.sh` (pushes branch + tag), docs.

[0.9.1]: https://github.com/Vladutu/pilot/compare/v0.9.0...v0.9.1
[0.9.0]: https://github.com/Vladutu/pilot/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/Vladutu/pilot/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/Vladutu/pilot/compare/v0.6.4...v0.7.0
[0.6.4]: https://github.com/Vladutu/pilot/compare/v0.6.3...v0.6.4
[0.6.3]: https://github.com/Vladutu/pilot/compare/v0.6.2...v0.6.3
[0.6.2]: https://github.com/Vladutu/pilot/compare/v0.6.1...v0.6.2
[0.6.1]: https://github.com/Vladutu/pilot/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/Vladutu/pilot/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/Vladutu/pilot/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/Vladutu/pilot/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/Vladutu/pilot/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/Vladutu/pilot/compare/v0.2.3...v0.3.0
[0.2.3]: https://github.com/Vladutu/pilot/compare/v0.2.2...v0.2.3
[0.2.2]: https://github.com/Vladutu/pilot/compare/v0.2.1...v0.2.2
[0.2.1]: https://github.com/Vladutu/pilot/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/Vladutu/pilot/releases/tag/v0.2.0
