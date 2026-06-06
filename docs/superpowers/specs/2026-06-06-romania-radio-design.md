# Romania Radio — design spec

**Date:** 2026-06-06
**Status:** Approved in brainstorming; ready for implementation planning (writing-plans).
**Scope:** Add internet-radio (Romanian stations) as a new content type across Pilot + Copilot,
built so a future Voxa-audiobooks type drops in the same way.

> Cross-repo feature. Most new UI is Pilot-side; Copilot gets a launch route + one home tile.
> This spec lives in the Pilot repo but covers both. Both apps must be rebuilt in lockstep
> (same as the existing v3 envelope rule).

---

## 1. Decisions locked in brainstorming

- **Playback mechanism:** launch **VLC for Android** (`org.videolan.vlc`) with the stream URL.
  Chosen because the constraint was *free + no ads + no payment* — VLC is the only clean fit
  (TuneIn/myTuner/Radio Garden are ad-supported). Same launch model as YT Music / Waze today.
- **Station source:** **Pilot fetches the list from radio-browser** (community DB) and the user
  taps to add — no manual URL hunting (hand-extracting stream URLs proved painful). Manual
  paste of a stream URL is kept as a secondary fallback.
- **Curation split:** Pilot = stations you've added (catalog) + a search-to-discover flow.
  Copilot = only stations that were sent from Pilot (history list). Mirrors the existing model.
- **Navigation:** both apps go **hub-and-spoke**. Pilot home becomes a grid of category cards
  (Playlists, Songs, Places, Radio) → tap → that category's tile list. Copilot is already a hub;
  it just gains a Radio tile.
- **Copilot home layout:** Row 1 = Waze + Maps (unchanged). Media stays a **3-column grid**;
  Radio appends as the 4th tile (wraps to a new line). Tile sizes unchanged. Audiobooks later = 5th.
- **Icon:** Material `Icons.Default.Radio` glyph, to match the existing Material form icons.

## 2. Generalizing principle (audiobooks-ready)

Each content type = **a `Form` + a launch route + a catalog source**. Radio establishes the
pattern (VLC route, radio-browser source); Voxa audiobooks later reuses it (Voxa app route, its
own source). The hub-and-spoke nav + data-driven tile/knob lists mean adding a type is additive,
not a relayout.

---

## 3. radio-browser API (verified during brainstorming)

- **Do not hardcode a server.** `de1` 404'd; servers come and go. Resolve dynamically.
- **Server list:** `GET https://all.api.radio-browser.info/json/servers` → array of `{name, ...}`.
  Pick one, health-check with `GET https://<server>/json/stats`, cache for the session, fall back
  to the next on failure. Send header `User-Agent: Copilot/1.0` (the API asks clients to identify).
- **Station search (working endpoint):**
  `GET https://<server>/json/stations/search?countrycode=RO&order=votes&reverse=true&limit=50&hidebroken=true`
- **Fields used:** `name`, `url_resolved` (the playable stream — prefer over `url`), `favicon`
  (logo), `codec`, `bitrate`, `stationuuid` (stable id), `lastcheckok` (1 = last verified working;
  prefer these).
- **Format note:** streams are either direct (Icecast `.mp3`/`.aac`) or HLS `.m3u8`. VLC plays all;
  browsers don't (mixed-content/codec) — that's why stations failed to preview in the browser but
  play fine in VLC. Confirmed: Europa FM `url_resolved` plays in desktop VLC.

---

## 4. Wire protocol changes (shared)

`Message` schema stays **v3**; we add a command and a form.

- **New `cmd = "radio"`**, **new `Form.RADIO`** (`fromWire("radio")`).
- **cmd/form consistency:** `radio` ⇔ `RADIO`.
- **Relaxed URL validation for radio:** radio streams come from arbitrary hosts, so the per-host
  allow-list does not apply. For `cmd=radio`, accept **any `http://` or `https://` URL** (still
  reject other schemes — don't launch arbitrary intents). Existing ytmusic/waze/maps allow-lists
  unchanged.
- **Envelope for a radio launch:**
  `{ v:3, ts, cmd:"radio", form:"radio", url:<stream>, title:<station name>, imageUrl:<favicon> }`
- Backward compat: an old Copilot rejects `unknown cmd=radio` gracefully. Rebuild both together.

---

## 5. Pilot changes (phone / sender)

1. **Navigation refactor (hub-and-spoke).** Replace the segmented-tab `CatalogScreen` with:
   - `HomeHub` — grid of category cards (data-driven list of categories).
   - `CategoryListScreen` — the existing tile-grid/list for one `Form` (reuse current Tile/list code).
2. **`Form.RADIO`** added to Pilot's `Form`, `CatalogEntry`, `CatalogStore`; Material `Radio` icon.
3. **radio-browser integration (new package, e.g. `radio/`):**
   - `RadioBrowserServerResolver` — `/json/servers` → health-check `/json/stats` → cache + fallback.
   - `RadioBrowserClient` — station search by `countrycode=RO`; maps JSON → `RadioStation`
     (`stationuuid, name, streamUrl=url_resolved, faviconUrl, codec, bitrate, lastCheckOk`).
   - `RadioSearchScreen` — search field + results list with logos; tap to add to catalog;
     mark/disable stations already in the catalog (dedupe by `stationuuid`, fall back to stream URL).
4. **Add-to-catalog:** a chosen station becomes a `CatalogEntry` (RADIO): `id=stationuuid`,
   `title=name`, `url=streamUrl`, image from `faviconUrl` (reuse existing metadata/image fetch).
5. **Publish on tap:** tapping a radio tile publishes the `cmd="radio"` envelope (§4) via
   `NtfyPublisher` (new `publishRadio` or generalize the existing publish path).
6. **Fallback:** keep manual "paste a stream URL" (reuse `AddUrlDialog`) as a secondary add path.
7. **No share-sheet path for radio** (you don't "share" a station from a phone app).

## 6. Copilot changes (carbox / receiver)

1. **`Form.RADIO`** (`fromWire("radio")`); Material `Radio` icon in tiles/badges.
2. **`Message.parseEnvelope`** — handle `cmd="radio"` with relaxed validation (§4).
3. **`AppLauncher`** — new `radio` route firing the VLC intent:
   ```kotlin
   Intent(Intent.ACTION_VIEW).apply {
       setPackage("org.videolan.vlc")
       setDataAndTypeAndNormalize(Uri.parse(url), "audio/*")  // see risk: audio/* vs video/*
       addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
       title?.let { putExtra("title", it) }
   }
   ```
   - Missing-app reason: `"VLC not installed"`. Background-launch SecurityException → existing
     `"background launch blocked — grant Display over other apps"` message.
   - `cmdForForm(RADIO) = "radio"` for saved-tile replay.
4. **Home layout** — add Radio to the (now data-driven) media tile list: Row1 Waze/Maps unchanged,
   media = 3-col grid, Radio is 4th (wraps). Update the iDrive-knob rotation count to include it.
5. **History** — `SavedItem.from` for RADIO: `id = stationuuid` if carried, else `sha1(url)`.
   `savesToHistory()` is already `cmd != "maps"`, so radio persists. `SavedListScreen`/`SavedTile`
   are generic; the `list/{form}` route handles RADIO via `Form.fromWire`. Radio list shows favicon.

## 7. Data flow (end to end)

Pilot discovery → add RADIO catalog entry → tap tile → publish `{cmd:"radio",...}` over ntfy →
Copilot `NtfySubscriber` → `Message.parseEnvelope` (radio branch) → `ListenerService` →
`AppLauncher.launch` → VLC opens already playing → item saved to Copilot's Radio history.
Bubble/back brings Copilot back afterward (same as Waze/YT Music).

## 8. Error handling

- **Pilot:** server resolver exhausts mirrors → show error + offer manual paste. Search failure →
  error state with retry. Filter to `lastCheckOk == 1` by default.
- **Copilot:** VLC missing / background blocked → surfaced via the existing `launchOrReport` toast
  (added in commit `3c191c8`). Validation rejects non-http(s) radio URLs. Dead stream → VLC's own
  error (out of scope; `lastCheckOk` pre-filtering reduces it).

## 9. Testing (TDD)

- **Pilot:** `RadioBrowserServerResolver` (picks healthy, falls back), `RadioBrowserClient` JSON
  mapping, catalog dedupe-by-stationuuid, `Form.RADIO` wire round-trip, radio publish envelope shape.
- **Copilot:** `Message.parseEnvelope` radio cases (valid http(s) accepted; non-http rejected;
  cmd/form consistency; RADIO form), `AppLauncher` radio intent builder (package, mime, NEW_TASK,
  title extra) via a testable builder, `SavedItem.from` RADIO id, `cmdForForm(RADIO)`.
- All existing Pilot/Copilot tests stay green.

## 10. Open risks / verify on the carbox

- **VLC MIME:** `audio/*` should open VLC's audio player; if a station doesn't auto-play, fall back
  to `video/*` (known-reliable). Decide on the actual box.
- **Background launch:** same `SYSTEM_ALERT_WINDOW` requirement as the BAL note — if YT Music
  launches, radio will too.
- **Stream staleness / favicon size:** URLs rot (mitigated by `lastCheckOk`); some favicons missing
  or large — handle null/oversized gracefully (reuse existing image handling).
- **stationuuid vs sha1(url):** using `stationuuid` as the stable id avoids dupes when a station's
  stream URL changes; requires carrying it (title/url/imageUrl already cover display). If we don't
  carry it, `sha1(url)` is the fallback and a URL change looks like a new entry.

## 11. Out of scope (now)

- Voxa audiobooks (future; reuses this pattern).
- In-app radio playback baked into Copilot (rejected: VLC route is simpler and free/ad-free).
- A custom drawn radio icon (using the Material glyph for consistency).
