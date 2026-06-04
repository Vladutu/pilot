# Send-as-Maps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Send as Maps" option to Pilot's long-press menu on saved destinations, so the user can re-send a destination to the car as a Google Maps launch instead of the default Waze launch.

**Architecture:** Pilot persists the original Google Maps URL on the catalog entry when ingesting a Maps share. A new long-press menu item (shown only when that URL is present) emits a new `cmd="maps"` ntfy message carrying the original Maps URL. Copilot accepts the new cmd, launches Google Maps with the URL via `Intent.ACTION_VIEW + setPackage("com.google.android.apps.maps")`, and **does not** add a row to history (the corresponding Waze row is already there from the original ingest).

**Tech Stack:** Kotlin, Jetpack Compose (Pilot UI), kotlinx-serialization + DataStore Preferences (Pilot storage), OkHttp + ntfy (transport), JUnit 4 + Robolectric + MockWebServer (tests).

**Compatibility note:** Schema version stays at v=3. The change extends the `cmd` allowlist within the existing envelope shape — no new fields. Both apps must be deployed together; an old Copilot would reject `cmd="maps"` as "unknown cmd".

---

## File Structure

**Pilot (modify):**
- `app/src/main/java/com/vladutu/pilot/catalog/CatalogEntry.kt` — add nullable `googleMapsUrl` field.
- `app/src/main/java/com/vladutu/pilot/destination/DestinationPipeline.kt` — capture `MapsShare.rawUrl` and pass it into the saved entry.
- `app/src/main/java/com/vladutu/pilot/net/NtfyPublisher.kt` — add `publishMaps(url, title)` emitting `cmd="maps"`.
- `app/src/main/java/com/vladutu/pilot/ui/CatalogScreen.kt` — new `DropdownMenuItem` "Send as Maps" gated on `entry.googleMapsUrl != null`.

**Pilot (tests, modify):**
- `app/src/test/java/com/vladutu/pilot/destination/DestinationPipelineTest.kt` — assert `googleMapsUrl` is stored for MapsShare and null for WazeShare.
- `app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTest.kt` — assert `publishMaps` produces a `cmd=maps` envelope with the right url/title.

**Copilot (modify):**
- `app/src/main/java/com/vladutu/copilot/net/Message.kt` — accept `cmd="maps"` with Google Maps URL prefixes; require `form=DESTINATION`.
- `app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt` — new `MAPS_PKG` constant + `"maps"` branch in `launchUrl`.
- `app/src/main/java/com/vladutu/copilot/service/ListenerService.kt` — skip `history.save(item)` when `msg.cmd == "maps"` (via a small predicate on `Message`).

**Copilot (tests, modify):**
- `app/src/test/java/com/vladutu/copilot/net/MessageTest.kt` — accept maps+destination; reject untrusted host; reject cmd/form mismatch.
- `app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt` — maps cmd intent has Google Maps package.
- `app/src/test/java/com/vladutu/copilot/service/ListenerServiceMappingTest.kt` — predicate returns true for waze/ytmusic, false for maps.

No new files. No schema migration (DataStore JSON deserializes old entries with `googleMapsUrl = null`).

---

## Task 1 (Pilot): Persist original Google Maps URL on the catalog entry

**Files:**
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/catalog/CatalogEntry.kt`
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/destination/DestinationPipeline.kt:119-174`
- Modify: `Pilot/app/src/test/java/com/vladutu/pilot/destination/DestinationPipelineTest.kt`

- [ ] **Step 1: Add a failing test that asserts `googleMapsUrl` is stored when a Maps URL is ingested**

Open `Pilot/app/src/test/java/com/vladutu/pilot/destination/DestinationPipelineTest.kt`. Add this test method to the class:

```kotlin
@Test
fun ingest_mapsUrl_persistsOriginalGoogleMapsUrl() = runBlocking {
    val rawMapsUrl = "https://www.google.com/maps/place/Brandenburg+Gate/@52.5,13.4,17z/"
    converterServer.enqueue(MockResponse().setResponseCode(302).setHeader("Location", wazeUrl))

    newPipeline().ingest(urlText = rawMapsUrl, manualTitle = null, subject = "Brandenburg Gate")

    assertEquals(1, savedEntries.size)
    assertEquals(rawMapsUrl, savedEntries[0].googleMapsUrl)
}

@Test
fun ingest_wazeUrl_leavesGoogleMapsUrlNull() = runBlocking {
    newPipeline().ingest(
        urlText = "https://ul.waze.com/ul?ll=52.5,13.4",
        manualTitle = "Home",
        subject = null,
    )

    assertEquals(1, savedEntries.size)
    assertEquals(null, savedEntries[0].googleMapsUrl)
}
```

- [ ] **Step 2: Run the tests and verify they fail with a compile error**

Run: `cd /home/geo/projects/Pilot && ./gradlew :app:testDebugUnitTest --tests "*DestinationPipelineTest.ingest_mapsUrl_persistsOriginalGoogleMapsUrl" --tests "*DestinationPipelineTest.ingest_wazeUrl_leavesGoogleMapsUrl*"`

Expected: compile failure — `Unresolved reference: googleMapsUrl` (the property doesn't exist on `CatalogEntry` yet).

- [ ] **Step 3: Add `googleMapsUrl` to `CatalogEntry`**

Replace the data class in `Pilot/app/src/main/java/com/vladutu/pilot/catalog/CatalogEntry.kt` with:

```kotlin
package com.vladutu.pilot.catalog

import kotlinx.serialization.Serializable

@Serializable
data class CatalogEntry(
    val form: Form,
    val id: String,
    val title: String,
    val imagePath: String?,
    val imageUrl: String? = null,
    val googleMapsUrl: String? = null,
    val savedAt: Long,
)
```

The new field is nullable with a default, so DataStore-stored entries written by the old code still deserialize cleanly (the existing `Json { ignoreUnknownKeys = true }` config in `CatalogStore.kt:59` is already tolerant of missing fields when the default is supplied).

- [ ] **Step 4: Populate `googleMapsUrl` in `DestinationPipeline.ingestDestination`**

In `Pilot/app/src/main/java/com/vladutu/pilot/destination/DestinationPipeline.kt`, modify the `ingestDestination` method. After the `wazeUrl` computation and before the title resolution (around line 142), add a local capturing the original Maps URL:

```kotlin
val googleMapsUrl: String? = (classified as? ClassifiedShare.MapsShare)?.rawUrl
```

Then update the `CatalogEntry(...)` construction (currently lines 151–157) to include the new field:

```kotlin
val entry = CatalogEntry(
    form = Form.DESTINATION,
    id = wazeUrl,
    title = title,
    imagePath = null,
    googleMapsUrl = googleMapsUrl,
    savedAt = clock(),
)
```

- [ ] **Step 5: Run the tests and verify they pass**

Run: `cd /home/geo/projects/Pilot && ./gradlew :app:testDebugUnitTest --tests "*DestinationPipelineTest"`

Expected: all DestinationPipelineTest tests pass, including the two new ones and the pre-existing tests (`ingest_mapsUrl_convertsSavesAndPublishes`, `ingest_wazeUrl_normalizesSavesAndPublishes`, etc.) — they don't check `googleMapsUrl` so they remain green.

- [ ] **Step 6: Commit**

```bash
cd /home/geo/projects/Pilot && git add app/src/main/java/com/vladutu/pilot/catalog/CatalogEntry.kt app/src/main/java/com/vladutu/pilot/destination/DestinationPipeline.kt app/src/test/java/com/vladutu/pilot/destination/DestinationPipelineTest.kt && git commit -m "feat(pilot): persist original Google Maps URL on destination entries

Captures classified.rawUrl when a MapsShare is ingested so we can later
re-send the destination as a Google Maps launch instead of the (default)
Waze launch.

Backward compatible: googleMapsUrl is nullable with default null;
existing serialized entries deserialize with no migration."
```

---

## Task 2 (Pilot): Add `publishMaps` on NtfyPublisher

**Files:**
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/net/NtfyPublisher.kt`
- Modify: `Pilot/app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTest.kt`

- [ ] **Step 1: Add a failing test**

Open `Pilot/app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTest.kt`. Look at the existing `publishWaze` test (search for `publishWaze` in the file) and add a parallel test for `publishMaps`. Pattern after the `publishWaze` test exactly — same `MockWebServer.enqueue`, same envelope-assertion style. Example shape (adapt to match the file's existing helpers):

```kotlin
@Test
fun publishMaps_emitsMapsCmdEnvelope() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(200))
    val mapsUrl = "https://www.google.com/maps/place/Brandenburg+Gate/@52.5,13.4,17z/"

    publisher.publishMaps(mapsUrl, title = "Brandenburg Gate")

    val recorded = server.takeRequest()
    val body = JSONObject(recorded.body.readUtf8())
    assertEquals(3, body.getInt("v"))
    assertEquals("maps", body.getString("cmd"))
    assertEquals("destination", body.getString("form"))
    assertEquals(mapsUrl, body.getString("url"))
    assertEquals("Brandenburg Gate", body.getString("title"))
}
```

(If `JSONObject` isn't already imported in the file, add `import org.json.JSONObject`.)

- [ ] **Step 2: Run the test and verify it fails**

Run: `cd /home/geo/projects/Pilot && ./gradlew :app:testDebugUnitTest --tests "*NtfyPublisherTest.publishMaps_emitsMapsCmdEnvelope"`

Expected: compile failure — `Unresolved reference: publishMaps`.

- [ ] **Step 3: Add `publishMaps` to `NtfyPublisher`**

In `Pilot/app/src/main/java/com/vladutu/pilot/net/NtfyPublisher.kt`, after the existing `publishWaze` method (line 37–45), add:

```kotlin
open suspend fun publishMaps(url: String, title: String?) {
    postEnvelope(
        cmd = "maps",
        form = Form.DESTINATION,
        url = url,
        title = title,
        imageUrl = null,
    )
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `cd /home/geo/projects/Pilot && ./gradlew :app:testDebugUnitTest --tests "*NtfyPublisherTest"`

Expected: all NtfyPublisherTest tests pass.

- [ ] **Step 5: Commit**

```bash
cd /home/geo/projects/Pilot && git add app/src/main/java/com/vladutu/pilot/net/NtfyPublisher.kt app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTest.kt && git commit -m "feat(pilot): add publishMaps with cmd=maps envelope

Emits the same v=3 envelope shape as publishWaze, with cmd=\"maps\" so
Copilot can route the URL to Google Maps instead of Waze."
```

---

## Task 3 (Pilot): "Send as Maps" long-press menu item

**Files:**
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/ui/CatalogScreen.kt:194-215`

No unit test — Compose UI tests are not used in this codebase. Verify manually after the build.

- [ ] **Step 1: Insert the new menu item before "Delete"**

In `Pilot/app/src/main/java/com/vladutu/pilot/ui/CatalogScreen.kt`, locate the `DropdownMenu` block at lines 194–215. Insert the following `DropdownMenuItem` block **immediately before** the existing `DropdownMenuItem` for "Delete" (currently at line 207):

```kotlin
if (entry.form == Form.DESTINATION && entry.googleMapsUrl != null) {
    DropdownMenuItem(
        text = { Text("Send as Maps") },
        onClick = {
            val target = entry
            val mapsUrl = target.googleMapsUrl
            menuFor = null
            if (mapsUrl == null) return@DropdownMenuItem
            busy[key] = true
            DiagnosticLog.i("Tap", "send-as-maps ${target.form}:${target.id} '${target.title}'")
            scope.launch {
                try {
                    publisher.publishMaps(mapsUrl, title = target.title)
                    DiagnosticLog.i("Tap", "send-as-maps publish ok ${target.form}:${target.id}")
                    store.touch(target.form, target.id)
                    gridState.animateScrollToItem(0)
                    snackbar.showSnackbar("Sent as Maps: ${target.title}")
                } catch (e: Exception) {
                    DiagnosticLog.e("Tap", "send-as-maps publish failed (${e.javaClass.simpleName})", e)
                    snackbar.showSnackbar("Send failed — check connection")
                } finally {
                    busy[key] = false
                }
            }
        },
    )
}
```

(`busy`, `key`, `scope`, `gridState`, `snackbar`, `store`, `publisher` are all already in scope at this position in the file — see lines 76, 85, 75, 74 of CatalogScreen.kt.)

- [ ] **Step 2: Build the app to verify it compiles**

Run: `cd /home/geo/projects/Pilot && ./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual verification**

Install the debug build on the phone, then:

1. Share a Google Maps link to Pilot (use any place from the Google Maps app's share menu). Confirm the destination appears in Pilot's "Destinations" tab.
2. Long-press the destination tile. Confirm the menu shows: **Send as Maps**, **Delete**.
3. Tap **Send as Maps**. Confirm the snackbar reads "Sent as Maps: <title>" and the tile bumps to the top of the grid.
4. (Cross-app verification — only meaningful once Tasks 4–6 are merged in Copilot.) On the car, verify Google Maps opens with the destination. Skip until Copilot is updated.
5. Also long-press a destination that was added by pasting a **Waze URL** (manual add dialog, no Maps URL ever involved). Confirm the menu shows **only Delete** — no "Send as Maps".

- [ ] **Step 4: Commit**

```bash
cd /home/geo/projects/Pilot && git add app/src/main/java/com/vladutu/pilot/ui/CatalogScreen.kt && git commit -m "feat(pilot): add 'Send as Maps' long-press option on destinations

Shown only when the entry has a stored googleMapsUrl (i.e. the destination
originated from a Google Maps share, not from a pasted/shared Waze URL).
Tapping sends cmd=maps via NtfyPublisher and re-promotes the entry."
```

---

## Task 4 (Copilot): Accept `cmd="maps"` in Message parsing

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/net/Message.kt:20-64`
- Modify: `Copilot/app/src/test/java/com/vladutu/copilot/net/MessageTest.kt`

- [ ] **Step 1: Add failing tests**

Open `Copilot/app/src/test/java/com/vladutu/copilot/net/MessageTest.kt` and add the following tests inside the class:

```kotlin
@Test fun `accepts v3 with cmd=maps and Google Maps URL`() {
    val body = """{"v":3,"ts":$now,"cmd":"maps","form":"destination","url":"https://www.google.com/maps/place/Brandenburg+Gate/@52.5,13.4,17z/"}"""
    val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
    assertTrue(res is ParseResult.Accepted)
    val msg = (res as ParseResult.Accepted).message
    assertEquals("maps", msg.cmd)
    assertEquals(Form.DESTINATION, msg.form)
}

@Test fun `accepts maps cmd with maps_app_goo_gl short URL`() {
    val body = """{"v":3,"ts":$now,"cmd":"maps","form":"destination","url":"https://maps.app.goo.gl/abc123"}"""
    val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
    assertTrue(res is ParseResult.Accepted)
}

@Test fun `rejects maps cmd with untrusted host`() {
    val body = """{"v":3,"ts":$now,"cmd":"maps","form":"destination","url":"https://evil.example/place/x"}"""
    val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
    assertTrue(res is ParseResult.Rejected)
    assertEquals("untrusted host", (res as ParseResult.Rejected).reason)
}

@Test fun `rejects maps cmd with non-destination form`() {
    val body = """{"v":3,"ts":$now,"cmd":"maps","form":"playlist","url":"https://www.google.com/maps/place/X"}"""
    val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
    assertTrue(res is ParseResult.Rejected)
    assertEquals("cmd/form mismatch", (res as ParseResult.Rejected).reason)
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests "*MessageTest.*maps*"`

Expected: failure on the first two ("accepts v3 with cmd=maps…" and the short URL one) with `Rejected("unknown cmd=maps")`. The two `rejects` tests likely pass already because `cmd=maps` is rejected as unknown — but they're rejecting for the wrong reason ("unknown cmd" not "untrusted host" / "cmd/form mismatch"). All four tests should turn green after step 3.

- [ ] **Step 3: Add MAPS_ALLOWED_PREFIXES and the `"maps"` cmd branch**

In `Copilot/app/src/main/java/com/vladutu/copilot/net/Message.kt`, update the `companion object` to add a new prefix list and route `cmd="maps"` through it:

Add this constant alongside the existing prefix lists (around line 27):

```kotlin
private val MAPS_ALLOWED_PREFIXES = listOf(
    "https://www.google.com/maps",
    "https://maps.google.com/",
    "https://maps.app.goo.gl/",
    "https://goo.gl/maps/",
)
```

Then update the `when (cmd)` block at lines 50–54 to:

```kotlin
val allowedPrefixes = when (cmd) {
    "ytmusic" -> YT_MUSIC_ALLOWED_PREFIXES
    "waze" -> WAZE_ALLOWED_PREFIXES
    "maps" -> MAPS_ALLOWED_PREFIXES
    else -> return ParseResult.Rejected("unknown cmd=$cmd", skew)
}
```

And update the `cmdFormConsistent` check at lines 59–63:

```kotlin
val cmdFormConsistent = when (cmd) {
    "ytmusic" -> form == Form.PLAYLIST || form == Form.SONG
    "waze", "maps" -> form == Form.DESTINATION
    else -> false
}
```

- [ ] **Step 4: Run the tests and verify they pass**

Run: `cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests "*MessageTest"`

Expected: all MessageTest tests pass — both the existing waze/ytmusic ones and the four new maps ones.

- [ ] **Step 5: Commit**

```bash
cd /home/geo/projects/Copilot && git add app/src/main/java/com/vladutu/copilot/net/Message.kt app/src/test/java/com/vladutu/copilot/net/MessageTest.kt && git commit -m "feat(copilot): accept cmd=maps with Google Maps URL prefixes

Routes cmd=maps through MAPS_ALLOWED_PREFIXES (google.com/maps,
maps.google.com, maps.app.goo.gl, goo.gl/maps) and requires form=destination.
Schema v stays at 3 — only the cmd allowlist expands."
```

---

## Task 5 (Copilot): Launch Google Maps for `cmd="maps"`

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt:37-64`
- Modify: `Copilot/app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt`

- [ ] **Step 1: Add a failing test**

In `Copilot/app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt`, add:

```kotlin
@Test fun `launches maps destination message`() {
    val res = launcher.launch(msg("maps", Form.DESTINATION, "https://www.google.com/maps/place/X"))
    assertTrue(res is AppLauncher.Result.Ok)
    val intent = shadowOf(context as android.app.Application).nextStartedActivity
    assertTrue(intent.`package` == AppLauncher.MAPS_PKG)
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests "*AppLauncherTest.launches_maps_destination_message"`

Expected: compile failure — `Unresolved reference: MAPS_PKG`.

- [ ] **Step 3: Add MAPS_PKG and the `"maps"` branch in `launchUrl`**

In `Copilot/app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt`:

Add a `MAPS_PKG` constant in the `companion object` (line 77–81), making it:

```kotlin
companion object {
    const val TAG = "AppLauncher"
    const val YT_MUSIC_PKG = "com.google.android.apps.youtube.music"
    const val WAZE_PKG = "com.waze"
    const val MAPS_PKG = "com.google.android.apps.maps"
}
```

Then update both `when (cmd)` blocks in `launchUrl` (lines 38–47) to include the maps branch:

```kotlin
private fun launchUrl(cmd: String, form: Form, url: String): Result {
    val targetPkg = when (cmd) {
        "ytmusic" -> YT_MUSIC_PKG
        "waze" -> WAZE_PKG
        "maps" -> MAPS_PKG
        else -> return Result.Failed("unknown command: $cmd")
    }
    val missingMsg = when (cmd) {
        "ytmusic" -> "YouTube Music not installed"
        "waze" -> "Waze not installed"
        "maps" -> "Google Maps not installed"
        else -> "target app not installed"
    }
    // ...rest of the method unchanged
```

(Leave lines 49–64 alone — the intent construction, error handling, and `ActivityNotFoundException`/`SecurityException` paths are already cmd-agnostic.)

- [ ] **Step 4: Run the test and verify it passes**

Run: `cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests "*AppLauncherTest"`

Expected: all AppLauncherTest tests pass.

- [ ] **Step 5: Commit**

```bash
cd /home/geo/projects/Copilot && git add app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt && git commit -m "feat(copilot): launch Google Maps for cmd=maps

Adds MAPS_PKG (com.google.android.apps.maps) and routes cmd=maps messages
to Intent.ACTION_VIEW with that package, mirroring the ytmusic/waze paths."
```

---

## Task 6 (Copilot): Skip history save on `cmd="maps"`

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/net/Message.kt`
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/service/ListenerService.kt:101-107`
- Modify: `Copilot/app/src/test/java/com/vladutu/copilot/service/ListenerServiceMappingTest.kt`

Rationale: a `cmd="maps"` message is only ever sent for a destination that already exists in history (sent by Pilot's long-press on a saved entry, whose Waze counterpart was saved when first ingested). Saving a second sha1(mapsUrl) row would produce a duplicate-looking entry for the same physical place.

- [ ] **Step 1: Add a failing test for the predicate**

In `Copilot/app/src/test/java/com/vladutu/copilot/service/ListenerServiceMappingTest.kt`, add:

```kotlin
@Test fun `savesToHistory returns true for waze and ytmusic`() {
    val waze = msg(Form.DESTINATION, "https://ul.waze.com/ul?ll=1,2")
    val ytmusic = msg(Form.SONG, "https://music.youtube.com/watch?v=abc")
    assertEquals(true, waze.savesToHistory())
    assertEquals(true, ytmusic.savesToHistory())
}

@Test fun `savesToHistory returns false for maps`() {
    val maps = Message(
        v = 3, ts = 1_700_000_000L, cmd = "maps",
        form = Form.DESTINATION,
        url = "https://www.google.com/maps/place/X",
        title = null, imageUrl = null,
    )
    assertEquals(false, maps.savesToHistory())
}
```

(The existing `msg(...)` helper at the top of the file picks `cmd="waze"` for DESTINATION; that's fine for the first test. The second test builds the Message directly because we explicitly need `cmd="maps"`.)

You'll also need to add an import: `import com.vladutu.copilot.net.savesToHistory`.

- [ ] **Step 2: Run the test and verify it fails**

Run: `cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest --tests "*ListenerServiceMappingTest.savesToHistory*"`

Expected: compile failure — `Unresolved reference: savesToHistory`.

- [ ] **Step 3: Add the `savesToHistory` extension on Message**

In `Copilot/app/src/main/java/com/vladutu/copilot/net/Message.kt`, add at the bottom of the file (after the `Message` class, outside any class):

```kotlin
/**
 * Whether this message should be persisted to Copilot's history list after a
 * successful launch. The "maps" cmd is a one-off launch override for a
 * destination that already exists in history (as a Waze entry, sha1-keyed on
 * the Waze URL); saving a second row keyed on the Maps URL would look like a
 * duplicate entry for the same physical place.
 */
fun Message.savesToHistory(): Boolean = cmd != "maps"
```

- [ ] **Step 4: Gate the history save in ListenerService**

In `Copilot/app/src/main/java/com/vladutu/copilot/service/ListenerService.kt`, lines 101–107 currently read:

```kotlin
if (ok) {
    val savedAt = System.currentTimeMillis() / 1000L
    val item = SavedItem.from(msg, savedAt)
    history.save(item)
    msg.imageUrl?.let { imgUrl ->
        scope.launch { artwork.download(imgUrl, item.form, item.id) }
    }
```

Wrap the `history.save(item)` and the `artwork.download` (which uses the saved item's id) under the predicate. Replace lines 101–107 with:

```kotlin
if (ok) {
    val savedAt = System.currentTimeMillis() / 1000L
    val item = SavedItem.from(msg, savedAt)
    if (msg.savesToHistory()) {
        history.save(item)
        msg.imageUrl?.let { imgUrl ->
            scope.launch { artwork.download(imgUrl, item.form, item.id) }
        }
    }
```

Then add the import at the top of the file: `import com.vladutu.copilot.net.savesToHistory`.

(The bubble-show on line 111 stays unconditional — the user still wants the bubble overlay even for a one-off Maps launch.)

- [ ] **Step 5: Update the `label` lookup to display Maps launches in the status line**

Still in `ListenerService.kt`, the `when (msg.cmd)` at lines 89–93 currently has:

```kotlin
val label = when (msg.cmd) {
    "ytmusic" -> "play"
    "waze" -> "navigate"
    else -> msg.cmd
}
```

Change to:

```kotlin
val label = when (msg.cmd) {
    "ytmusic" -> "play"
    "waze", "maps" -> "navigate"
    else -> msg.cmd
}
```

So both Waze and Maps launches render as "▶ navigate · launched" in the recent-events list.

- [ ] **Step 6: Run all Copilot tests**

Run: `cd /home/geo/projects/Copilot && ./gradlew :app:testDebugUnitTest`

Expected: all tests pass, including the new `savesToHistory` ones.

- [ ] **Step 7: Manual cross-app verification (only meaningful after Tasks 1–6 are merged in both apps)**

1. Install fresh debug builds of both Pilot and Copilot.
2. On phone (Pilot): share a Google Maps link → destination tile appears.
3. Tap the tile (normal tap) → on the car (Copilot), Waze launches and **one** entry appears in Copilot's history.
4. Long-press the same tile on Pilot → "Send as Maps" → on the car, **Google Maps** launches with the same destination.
5. Inspect Copilot's history: it still shows **one** row (the original Waze one). No duplicate.

- [ ] **Step 8: Commit**

```bash
cd /home/geo/projects/Copilot && git add app/src/main/java/com/vladutu/copilot/net/Message.kt app/src/main/java/com/vladutu/copilot/service/ListenerService.kt app/src/test/java/com/vladutu/copilot/service/ListenerServiceMappingTest.kt && git commit -m "feat(copilot): skip history save for cmd=maps one-off launches

cmd=maps is sent by Pilot only for destinations that already exist in
history (as a Waze sha1-keyed row from original ingest). Saving a second
row keyed on the Maps URL would look like a duplicate for the same place,
so gate history.save and artwork.download behind a savesToHistory predicate.

Also map cmd=maps to the 'navigate' label in the status line."
```

---

## Self-Review Notes

- **Spec coverage:** Each requirement from the design discussion maps to a task —
  - Preserve original Maps URL → Task 1.
  - New cmd=maps transport → Task 2 (Pilot send) + Task 4 (Copilot parse).
  - Long-press menu UI → Task 3.
  - Car-side launch behavior → Task 5.
  - No duplicate history entry → Task 6.
- **Schema:** v stays at 3; no migration. Existing serialized `CatalogEntry` rows deserialize to `googleMapsUrl = null` (Task 1 step 3 notes this).
- **Cross-app compatibility:** an old Copilot would reject `cmd=maps` as unknown. The user controls both deployments, so this is acceptable; the "Send as Maps" UI gracefully reports the rejection via the existing snackbar/diagnostics path.
- **Naming consistency:** `googleMapsUrl` field, `publishMaps()` method, `MAPS_ALLOWED_PREFIXES`, `MAPS_PKG`, `cmd="maps"`, `Message.savesToHistory()` — verified consistent across all tasks.
