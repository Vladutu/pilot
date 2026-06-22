# Pilot

The **sender** half of the Pilot/Copilot car-remote system. Pilot runs on your
phone; [Copilot](../Copilot) runs on the car's Android head unit. You share
content into Pilot (a YouTube Music link, a Google Maps place, a radio station),
and it relays a command to the car over [ntfy](https://ntfy.sh) so Copilot can
open it — music, navigation, or radio — without you touching the head unit while
driving.

```
  Phone (Pilot)                 ntfy.sh                 Car box (Copilot)
  ─────────────                 ───────                 ─────────────────
  Share / pick  ──► classify ──► publish JSON ──► subscribe ──► launch app
  + save to catalog              (shared topic)                + save to history
```

## What it does

- **Share YouTube Music** playlists or songs from the system share sheet. Pilot
  fetches the title and artwork, saves it to the local catalog, and publishes
  `cmd=ytmusic` to the car.
- **Share Google Maps / Waze links.** Maps URLs are converted to Waze format —
  first on-device (`InAppMapsToWazeResolver`), falling back to the
  `waze.papko.org` service with a visible retry loop if the first attempt fails.
  Published as `cmd=waze`.
- **Browse Romanian internet radio** via [radio-browser.info](https://www.radio-browser.info),
  add stations to the catalog, and publish the stream as `cmd=radio`.
- **Discover keywords** — author your own category keywords and republish them to
  the car on demand (`cmd=category`) for Copilot's live-only delivery window.
- **Local catalog** of everything you've saved (playlists, songs, destinations,
  radio), newest first, with rename/delete.
- **Diagnostics** — an in-app ring-buffer log viewer (the ⓘ button) that records
  every ingest, publish attempt, failure, and the process/network state at publish
  time, for troubleshooting flaky shares.

## Permissions

Pilot only needs ordinary phone permissions — no special-access grants (unlike
Copilot, which runs the car-side overlay/accessibility services):

| Permission | Why |
|---|---|
| `INTERNET` | Publish commands to ntfy; fetch titles/artwork, Maps→Waze conversion, radio search |
| `FOREGROUND_SERVICE` (+ `_DATA_SYNC`) | `ShareIngestService` keeps a publish alive on metered/slow networks |
| `POST_NOTIFICATIONS` | The foreground-service notification while a share is publishing (Android 13+) |
| `CAMERA` | Scan Copilot's QR code to pair the ntfy topic (runtime prompt, only when you open the scanner) |

## Tech stack

Kotlin · Jetpack Compose (Material 3) · OkHttp (ntfy / converter / radio-browser /
metadata) · DataStore + kotlinx.serialization (catalog & discover stores) ·
kotlinx.coroutines · Coil (artwork). Tests use JUnit + OkHttp MockWebServer. Exact
versions live in `gradle/libs.versions.toml` (kept current by Dependabot).

## Architecture

`PilotApp` is the composition root — it owns the shared `OkHttpClient`, the
DataStore-backed stores, the publisher, and the `DestinationPipeline`. The UI is a
Compose hub-and-spoke (`PilotNavHost`: Home → category list / radio search /
discover categories).

| Package | Role |
|---------|------|
| `share` | Share-intent ingestion. `ShareReceiverActivity`, `UrlClassifier`, `MapsToWazeConverter` + `InAppMapsToWazeResolver`, `ShareConversionController` (retry state machine), `ShareIngestService` (foreground service that keeps the publish alive on metered networks). |
| `destination` | `DestinationPipeline` — classify → convert → upsert catalog → publish, returning a sealed `IngestResult`. |
| `net` | `NtfyPublisher` — posts the JSON envelope to ntfy with 3-attempt exponential backoff; wraps `IOException` as `NtfyPublishException`. |
| `catalog` | `CatalogStore`, `CatalogEntry`, `Form` (PLAYLIST / SONG / DESTINATION / RADIO). DataStore + JSON. |
| `radio` | `RadioBrowserClient` / `RadioBrowserServerResolver` — searches Romanian stations (`countrycode=RO`, `hidebroken=true`, by votes). |
| `meta` | `MetadataFetcher` — YouTube oEmbed / OG-tag scrape + artwork download. |
| `discover` | `DiscoverCategoryStore` — local keyword list. |
| `diagnostics` | `DiagnosticLog`, `DiagnosticsActivity`, `ProcessState`. |
| `settings` | `SettingsStore` (per-device ntfy topic), `TopicPairing` (QR pairing with Copilot). |
| `config` | `Config` — ntfy base URL + papko converter endpoint (the topic is **not** here; it's per-device in `SettingsStore`). |
| `ui` | Compose screens + theme. |

### Wire protocol (v3)

Publishes a JSON envelope to the shared ntfy topic:

```json
{ "v": 3, "ts": 1718000000, "cmd": "ytmusic|waze|radio|category",
  "form": "song|playlist|destination|radio|category",
  "url": "…", "title": "…", "imageUrl": "…" }
```

Each device has its own ntfy topic (stored in `SettingsStore`), paired to Copilot
by scanning a QR code — there is no shared hardcoded topic. `ts` is a Unix
timestamp; Copilot rejects messages older than 30s to defend against ntfy's
replay cache.

## Build & run

Building needs an Android SDK (set `sdk.dir` in `local.properties`).

```bash
make check               # assembleDebug + testDebugUnitTest + lintDebug
make version             # latest released tag
make release V=0.3.0     # test → signed build → tag → GitHub release
```

`make release` drives `scripts/release.sh`: it validates semver, computes the
versionCode (`MAJOR*10000 + MINOR*100 + PATCH`), checks the tree is clean and the
keystore present, runs tests, builds a signed APK, tags, and publishes a GitHub
release. See [`docs/RELEASING.md`](docs/RELEASING.md) for the Obtainium auto-update
setup.

> **Signing:** the release keystore + `signing.properties` are **local-only and
> gitignored** — run `scripts/setup-signing.sh` once to generate them. A keyless
> clone still builds (it falls back to debug signing), so only your machine, with
> the real keystore, produces installable release updates.

CI (`.github/workflows/ci.yml`) runs the same `make check` on PRs and pushes to
`master`, with Dependabot patch/minor auto-merge once the build is green.

## Layout

```
Pilot/
├── Makefile                       # check / version / release / wrapper
├── app/
│   ├── build.gradle.kts           # namespace com.vladutu.pilot
│   └── src/main/java/com/vladutu/pilot/
│       ├── PilotApp.kt  MainActivity.kt
│       ├── share/ destination/ net/ catalog/ radio/
│       ├── meta/ discover/ diagnostics/ settings/ config/ ui/
├── scripts/                       # release.sh, version.sh, bootstrap-wrapper.sh
├── keystore/                      # signing.properties.template (real key is gitignored, local-only)
├── docs/                          # RELEASING.md + superpowers/ design docs & plans
└── .github/                       # ci.yml, dependabot.yml
```

## Related

- [Copilot](../Copilot) — the receiver app that runs in the car.
