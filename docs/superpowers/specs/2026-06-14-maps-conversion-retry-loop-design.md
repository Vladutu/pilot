# Maps→Waze conversion retry loop (foreground, until success or stop)

**Date:** 2026-06-14
**Status:** Approved design, pre-implementation
**Repo:** Pilot

## Problem

When a Google Maps link is shared to Pilot and the on-device resolver can't extract
coordinates (a place-id-only link — no `lat,lng` in the link, the page, or the HTTP
redirect), the only thing that can resolve it is the `waze.papko.org` converter, which
holds a Google Places API key. That converter is **flaky per-request**: it intermittently
returns its HTTP 200 "Oops" page instead of the expected 302. Verified 2026-06-14: the
exact link that failed for the user (`maps.app.goo.gl/ipizZRWsPRTsQ5jR8`, "Dedeman")
returned a valid 302 on 10/10 immediate retries.

Today `MapsToWazeConverter.convert()` makes a **single** attempt; the first non-302 is
fatal → toast "Couldn't convert link". A retry would almost always succeed.

## Goal

When a shared Maps link falls back to papko and papko's **first** attempt fails, bring
Pilot to the foreground and **loop-retry papko every 1 second** with a visible attempt
counter, until it succeeds or the user stops. This only applies to the papko fallback
path; in-app resolutions and first-try papko successes behave exactly as today (invisible,
toast, done).

## Decisions (from brainstorming)

- **Loop lives in the Activity/ViewModel**, scoped to its lifecycle — not the service.
  Leaving Pilot (Home, back to Maps, app switch) cancels the loop, same as pressing Stop.
  This is the only architecture that gives "pause when I leave" for free and avoids
  Android background-activity-launch (BAL) restrictions, because the loop dies with the
  screen.
- **Show the retry screen on the first papko failure** (no silent pre-retries).
- **Only papko is retried.** In-app resolution failure on a place-id link is deterministic
  (no Places API on-device), so retrying it is pointless.
- **Retry forever** at a fixed 1s interval until success or stop. No attempt cap.
- The conversion phase is interactive (pauses on leave). **Once a Waze URL is obtained**,
  save + publish is handed to the existing `ShareIngestService` (foreground) so it
  completes even if the user immediately leaves — leaving cancels an *in-progress
  conversion*, never a *completed* one.

## Non-goals

- No change to YT Music or Waze share paths (they keep the instant handoff to the service).
- No change to the manual-entry path (`AddUrlDialog` → `DestinationPipeline.ingest`): it
  keeps single-shot conversion. The retry loop is share-flow only.
- No on-device Places API key (the alternative that would remove the papko dependency).
  Out of scope.

## Architecture

Split responsibilities between the (now non-trivial) share activity and the service:

- **Activity owns resolution (interactive).** `ShareReceiverActivity` becomes a
  `ComponentActivity` (Compose; project already uses Compose). It keeps its
  `Theme.Translucent.NoTitleBar` theme so it is **invisible while attempting** — the fast
  path looks exactly like today. It hosts a `ConversionViewModel`.
- **Service owns persistence (headless, unchanged role).** `ShareIngestService` gains an
  "already-resolved" entry that skips conversion and only does save + publish, reusing the
  existing `DestinationPipeline` save/publish tail (which still gets the metered-network
  foreground guarantee that the service exists for).

### Flow

```
Share SEND → ShareReceiverActivity.onCreate
  classify (UrlClassifier.classifyUrl)
  ├─ not MapsShare (YtMusic / Waze / unrecognized):
  │     start ShareIngestService (full ingest, as today) → finish()   [no UI, unchanged]
  └─ MapsShare:
        ConversionViewModel.start(rawUrl, subject)
          1. inAppResolver.resolve(rawUrl, hints=[subject])
                 non-null → RESOLVED(wazeUrl, titleSourceUrl=resolvedUrl)
          2. else loop (attempt = 1,2,3…):
                 try converter.convert(rawUrl)
                     success → RESOLVED(wazeUrl, titleSourceUrl=rawUrl)
                 catch WazeConversionException →
                     state = Retrying(label, attempt); delay(1000); continue
          on RESOLVED:
                 start ShareIngestService (resolved payload) → finish()
          on user Stop / activity leave:
                 cancel loop → finish()   (no send)
```

While in step 1 and the first `convert()` (step 2, attempt 1) the activity renders
nothing (transparent). The retry screen appears only when attempt 1 fails.

## Components

### `ConversionViewModel` (new) — `share/ConversionViewModel.kt`

- Constructor deps: `MapsResolver` (in-app), `MapsToWazeConverter`, and the classifier
  (static `UrlClassifier`). Built via a simple `ViewModelProvider.Factory` from `PilotApp`'s
  existing singletons (`inAppMapsResolver`, `mapsToWazeConverter`).
- State: `StateFlow<ConversionUiState>` where
  `ConversionUiState = Working | Retrying(label: String, attempt: Int)`.
  - `Working` → activity shows nothing (transparent).
  - `Retrying` → activity shows the card. `attempt` = number of papko attempts *made so
    far* (≥1 once the card is visible). The card displays the attempt **about to run**,
    i.e. `attempt + 1`, matching the agreed mockup (after attempt 1 fails → "attempt 2").
- One-shot events: `SharedFlow<ConversionEvent>` where
  `ConversionEvent = Resolved(wazeUrl, googleMapsUrl, titleSourceUrl, provisionalTitle)
  | DelegateToService(rawUrl, subject)  // non-Maps share
  | Stopped`.
- `start(rawUrl, subject)` launches the loop in `viewModelScope` (idempotent — guards
  against re-launch on recreate). `stop()` cancels the job and emits `Stopped`.
- Retry interval is an injectable `retryDelayMs = 1_000L` (so tests can use a virtual clock).
- The converter call inherits the activity-foreground process state → metered network is
  available during the loop.

### `ShareReceiverActivity` (changed) — `share/ShareReceiverActivity.kt`

- `Activity` → `ComponentActivity`. Reads `EXTRA_TEXT`/`EXTRA_SUBJECT` as today.
- Obtains `ConversionViewModel`, calls `start(...)` once.
- `setContent { … }` collects state:
  - `Working` → empty (transparent).
  - `Retrying` → a centered Material card over a dim scrim:
    `Converting "<label>"…` / `attempt N` / `[ Stop ]`.
    Optional: after ~15 attempts, a muted line "papko may be down — you can stop and try
    later". (Include; two lines, low risk.)
- Collects events:
  - `Resolved` → `startForegroundService(ShareIngestService.intentResolved(...))`, `finish()`.
  - `DelegateToService` → `startForegroundService(ShareIngestService.intent(...))`, `finish()`
    (today's exact behavior for non-Maps shares).
  - `Stopped` → `finish()`.
- `onStop()` (user left, not via a Resolved finish): call `viewModel.stop()`. Guard so the
  normal finish-after-Resolved path doesn't double-handle. User-initiated stop and leaving
  are **silent** (no toast) — the user chose to abandon; only failures/successes toast.
- Back press while Retrying = leave = stop.
- ViewModel survives rotation; the card re-renders from state.

### `DestinationPipeline` (refactor) — `destination/DestinationPipeline.kt`

Extract the save + publish tail of `ingestDestination` into a reusable method so the
service's resolved path and the existing full path share one implementation:

```kotlin
suspend fun ingestResolvedDestination(
    wazeUrl: String,
    googleMapsUrl: String?,
    provisionalTitle: String?,
    titleSourceUrl: String?,   // resolved /place/ URL when available, else rawUrl
    manualTitle: String? = null,
): IngestResult            // title resolution + catalog upsert + publishWaze (unchanged logic)
```

`ingestDestination` keeps doing in-app/papko resolution for the manual path, then delegates
its tail to `ingestResolvedDestination`. No behavior change for existing callers/tests.

### `ShareIngestService` (changed) — `share/ShareIngestService.kt`

- New extras: `EXTRA_WAZE_URL`, `EXTRA_GMAPS_URL`, `EXTRA_TITLE_SOURCE_URL`
  (`EXTRA_SUBJECT` reused for provisional title).
- New factory `intentResolved(context, wazeUrl, googleMapsUrl, titleSourceUrl, subject)`.
- `onStartCommand`: if `EXTRA_WAZE_URL` present → call
  `ingestResolvedDestination(...)`; else → existing `ingest(...)` full path.
- Everything else (foreground notification, in-flight counter, toast-from-result,
  stopSelf) unchanged. Toasts stay owned by the service (single source of copy).

## Error handling

- `WazeConversionException` inside the loop → counts as a failed attempt → keep looping.
  (Both the non-302 "Oops" 200 and network failures surface as this exception today.)
- In-app resolver never throws (contract) → null just means "go to papko loop".
- If the user stops, nothing is saved or published (same as today's ConversionFailed,
  minus the toast).
- Save/publish failures after a successful conversion are handled by the existing service
  result → toast mapping (`PublishFailed`, `SaveFailed`, etc.) — unchanged.

## Testing

- **`ConversionViewModelTest`** (new, `kotlinx-coroutines-test` virtual time):
  - in-app success → emits `Resolved` immediately, converter never called, no `Retrying`.
  - papko fails N times then 302 → state goes `Retrying(attempt=1..N)`, advancing 1s each,
    then emits `Resolved`; assert attempt count and that delays are 1s.
  - `stop()` while `Retrying` → job cancelled, no `Resolved`, emits `Stopped`.
  - non-Maps url → emits `DelegateToService`, no resolver/converter calls.
- **`DestinationPipelineTest`**: add a case for `ingestResolvedDestination` (save+publish
  only). Existing `ingest` tests stay green (tail extraction is behavior-preserving).
- Existing `MapsToWazeConverterTest` / `InAppMapsToWazeResolverTest` unchanged.
- No instrumentation/UI test for the Compose card (project has no UI test harness); the
  card is thin and state-driven.

## Build / review

Per workspace workflow: no Gradle run here (no Android SDK on this box) and no commits at
code-writing time. Georgian builds + runs the suite on his Mac, then commits.

## Revision 1 (2026-06-14, post on-device test)

First on-device build exposed a UX bug: the activity was invisible (translucent) **but still on
top**, so during the ~1–2s fallback window (in-app network resolve + first papko call) Maps showed
through but was frozen — even when papko 302'd first try. An invisible window still eats touches.

Fix: a `ConversionUiState.Converting` (spinner card, no attempt counter) is now shown once
resolution outlasts a `graceDelayMs` (≈450 ms) grace window. Genuinely fast resolves still finish
inside the grace and flash nothing; anything slower shows a visible "Converting… [Stop]" card so the
user sees Pilot working instead of a frozen Maps. The retry card is the same overlay plus the
attempt counter. The controller arms the grace via a `launch { delay; if Working → Converting }`
cancelled in `finally`.

## Open questions

None — design fully specified.
