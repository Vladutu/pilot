# Romania Radio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Romanian internet radio as a new content type across Pilot (sender) and Copilot (receiver): Pilot discovers stations from radio-browser and lets you add/tap them; tapping publishes a `cmd="radio"` envelope; Copilot receives it and launches VLC on the stream URL.

**Architecture:** Each content type = a `Form` + a launch route + a catalog source (the audiobooks-ready generalizing principle from the spec). Radio reuses the existing v3 ntfy wire envelope with a new `cmd="radio"`/`Form.RADIO` pair and relaxed (scheme-only) URL validation. Copilot adds a VLC launch route + one home tile + a history list (all generic/data-driven). Pilot adds a radio-browser client + a hub-and-spoke navigation refactor + a search-to-add screen.

**Tech Stack:** Kotlin, Jetpack Compose, okhttp3 + org.json (already deps in both apps), kotlinx.serialization, DataStore preferences. Tests: JUnit4 + Robolectric + MockWebServer + kotlinx-coroutines-test (already deps). **No new Gradle dependencies are required.**

> **Cross-repo, lockstep rule:** Both apps must be rebuilt together (existing v3 envelope rule). An old Copilot rejects `cmd=radio` gracefully ("unknown cmd"), but radio only works end-to-end once both are rebuilt.

> **Build/test note:** The plan author cannot run Gradle in this environment. Each logic task lists the exact `gradlew` command and expected result so the engineer (or the final build pass) can run them. Run them where indicated.

> **VLC MIME caveat (from spec §10):** This plan implements `audio/*` for the VLC intent. If on the carbox a station opens VLC but does not auto-play, switch the MIME to `video/*` in `AppLauncher.buildRadioIntent` — a one-line change flagged in Task 3. Decide on the actual box.

---

## File Structure

### Copilot (receiver) — changes
- `history/Form.kt` — add `RADIO` enum + `fromWire("radio")`. *(modify)*
- `net/Message.kt` — add `radio` to known cmds; relaxed http(s) validation for radio. *(modify)*
- `launch/AppLauncher.kt` — VLC launch route + testable `buildRadioIntent`; `cmdForForm(RADIO)`; radio replay routing; `VLC_PKG`. *(modify)*
- `history/SavedItem.kt` — `RADIO -> sha1(url)`. *(modify)*
- `service/ListenerService.kt` — `"radio"` → recent-event label. *(modify)*
- `ui/lists/SavedTile.kt` — RADIO form badge + fallback glyph. *(modify)*
- `ui/lists/SavedListScreen.kt` — RADIO title + empty text. *(modify)*
- `ui/home/HomeScreen.kt` — data-driven media tile grid; Radio = 4th tile (wraps); knob count → 6. *(modify)*
- `MainActivity.kt` — `onOpenRadio` → `nav.navigate("list/radio")`. *(modify)*
- `res/values/strings.xml` — `home_radio`, `empty_radio`. *(modify)*
- `AndroidManifest.xml` — `<package android:name="org.videolan.vlc" />` in `<queries>`. *(modify)*

### Pilot (sender) — changes
- `catalog/Form.kt` — add `RADIO`. *(modify)*
- `net/NtfyPublisher.kt` — `publishRadio(...)`. *(modify)*
- `radio/RadioStation.kt` — model + pure JSON mapper. *(create)*
- `radio/RadioBrowserServerResolver.kt` — resolve healthy server, cache, fallback. *(create)*
- `radio/RadioBrowserClient.kt` — RO station search; pure `searchUrl`. *(create)*
- `radio/RadioCatalog.kt` — add-station-to-catalog + manual-paste helpers. *(create)*
- `ui/HomeHub.kt` — grid of category cards. *(create)*
- `ui/CategoryListScreen.kt` — per-`Form` tile grid (refactor of CatalogScreen body). *(create)*
- `ui/RadioSearchScreen.kt` — search field + results + add-to-catalog. *(create)*
- `ui/PilotNavHost.kt` — state-based hub-and-spoke router + BackHandler. *(create)*
- `ui/Tile.kt` — `formIcon(RADIO)`. *(modify)*
- `ui/AddUrlDialog.kt` — RADIO labels. *(modify)*
- `MainActivity.kt` — `setContent { PilotNavHost(...) }`. *(modify)*
- `PilotApp.kt` — wire `radioBrowserClient`. *(modify)*
- `ui/CatalogScreen.kt` — **deleted** (replaced by PilotNavHost + CategoryListScreen). *(delete)*

---

# PHASE 1 — Copilot wire contract (logic, full TDD)

### Task 1: Copilot `Form.RADIO`

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/history/Form.kt`
- Test: `Copilot/app/src/test/java/com/vladutu/copilot/history/FormTest.kt`

- [ ] **Step 1: Add failing test cases**

Append these two tests inside the existing `FormTest` class (after the `fromWire round-trips` test), and extend the existing assertions:

```kotlin
    @Test fun `radio wire value`() {
        assertEquals("radio", Form.RADIO.wire)
    }

    @Test fun `fromWire maps radio`() {
        assertEquals(Form.RADIO, Form.fromWire("radio"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Copilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.history.FormTest"`
Expected: FAIL — `Form.RADIO` does not resolve (compile error).

- [ ] **Step 3: Add the enum constant**

In `Form.kt`, add `RADIO` to the enum and a `fromWire` branch:

```kotlin
@Serializable
enum class Form {
    PLAYLIST,
    SONG,
    DESTINATION,
    RADIO;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): Form? = when (value) {
            "playlist" -> PLAYLIST
            "song" -> SONG
            "destination" -> DESTINATION
            "radio" -> RADIO
            else -> null
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Copilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.history.FormTest"`
Expected: PASS.

> Note: Adding `RADIO` makes several `when (form)` blocks non-exhaustive (SavedItem, AppLauncher, SavedTile, SavedListScreen). They are fixed in their own tasks; the project will not fully compile until Tasks 3–8 land. Run the single-class test above (it only compiles the `history` package's logic), and do the full build at the end.

- [ ] **Step 5: Commit**

```bash
cd Copilot && git add app/src/main/java/com/vladutu/copilot/history/Form.kt app/src/test/java/com/vladutu/copilot/history/FormTest.kt
git commit -m "feat(copilot): add Form.RADIO wire mapping"
```

---

### Task 2: Copilot `Message.parseEnvelope` radio branch (relaxed http(s) validation)

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/net/Message.kt:36-87`
- Test: `Copilot/app/src/test/java/com/vladutu/copilot/net/MessageTest.kt`

- [ ] **Step 1: Add failing tests**

Append these tests to `MessageTest`:

```kotlin
    @Test fun `accepts radio cmd with https stream url`() {
        val body = """{"v":3,"ts":$now,"cmd":"radio","form":"radio","url":"https://live.example.ro/europafm.mp3","title":"Europa FM","imageUrl":"https://example.ro/fav.png"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
        val msg = (res as ParseResult.Accepted).message
        assertEquals("radio", msg.cmd)
        assertEquals(Form.RADIO, msg.form)
        assertEquals("Europa FM", msg.title)
        assertEquals("https://example.ro/fav.png", msg.imageUrl)
    }

    @Test fun `accepts radio cmd with http stream url`() {
        val body = """{"v":3,"ts":$now,"cmd":"radio","form":"radio","url":"http://1.2.3.4:8000/stream.aac"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
    }

    @Test fun `accepts radio cmd from arbitrary host (no allow-list)`() {
        val body = """{"v":3,"ts":$now,"cmd":"radio","form":"radio","url":"https://some-random-icecast.example/stream"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Accepted)
    }

    @Test fun `rejects radio cmd with non-http scheme`() {
        val body = """{"v":3,"ts":$now,"cmd":"radio","form":"radio","url":"ftp://example.ro/stream"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("non-http(s) radio url", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects radio cmd with non-radio form`() {
        val body = """{"v":3,"ts":$now,"cmd":"radio","form":"playlist","url":"https://live.example.ro/x.mp3"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("cmd/form mismatch", (res as ParseResult.Rejected).reason)
    }

    @Test fun `rejects radio form with non-radio cmd`() {
        val body = """{"v":3,"ts":$now,"cmd":"ytmusic","form":"radio","url":"https://music.youtube.com/watch?list=L"}"""
        val res = Message.parseEnvelope(envelope(body), nowSec = now, maxAgeSec = maxAge)
        assertTrue(res is ParseResult.Rejected)
        assertEquals("cmd/form mismatch", (res as ParseResult.Rejected).reason)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd Copilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.net.MessageTest"`
Expected: FAIL — radio cmd hits the `else -> Rejected("unknown cmd=radio")` branch.

- [ ] **Step 3: Restructure the validation block**

Replace the body of `parseEnvelope` from the `val cmd = body.optString("cmd")` line through the `if (allowedPrefixes.none ...)` block (currently `Message.kt:56-78`) with this. It preserves existing reason strings and ordering (unknown-cmd → unknown-form → cmd/form mismatch → url) so the existing tests stay green:

```kotlin
            val cmd = body.optString("cmd")
            if (cmd !in KNOWN_CMDS) return ParseResult.Rejected("unknown cmd=$cmd", skew)

            val form = Form.fromWire(body.optString("form").takeIf { it.isNotBlank() })
                ?: return ParseResult.Rejected("unknown form", skew)

            val cmdFormConsistent = when (cmd) {
                "ytmusic" -> form == Form.PLAYLIST || form == Form.SONG
                "waze", "maps" -> form == Form.DESTINATION
                "radio" -> form == Form.RADIO
                else -> false
            }
            if (!cmdFormConsistent) return ParseResult.Rejected("cmd/form mismatch", skew)

            val url = body.optString("url")
            if (url.isBlank()) return ParseResult.Rejected("missing url", skew)

            // Radio streams come from arbitrary hosts, so the per-host allow-list does
            // not apply. Accept any http(s) URL; reject other schemes (don't launch
            // arbitrary intents). ytmusic/waze/maps keep their host allow-lists.
            if (cmd == "radio") {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return ParseResult.Rejected("non-http(s) radio url", skew)
                }
            } else {
                val allowedPrefixes = when (cmd) {
                    "ytmusic" -> YT_MUSIC_ALLOWED_PREFIXES
                    "waze" -> WAZE_ALLOWED_PREFIXES
                    "maps" -> MAPS_ALLOWED_PREFIXES
                    else -> emptyList()
                }
                if (allowedPrefixes.none { url.startsWith(it) }) {
                    return ParseResult.Rejected("untrusted host", skew)
                }
            }
```

Then add the `KNOWN_CMDS` constant inside the `companion object` (next to the existing prefix lists):

```kotlin
        private val KNOWN_CMDS = setOf("ytmusic", "waze", "maps", "radio")
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd Copilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.net.MessageTest"`
Expected: PASS — all new radio tests plus all pre-existing tests (untrusted host, stale, v2, maps cases) green.

- [ ] **Step 5: Commit**

```bash
cd Copilot && git add app/src/main/java/com/vladutu/copilot/net/Message.kt app/src/test/java/com/vladutu/copilot/net/MessageTest.kt
git commit -m "feat(copilot): accept cmd=radio with relaxed http(s) validation"
```

---

### Task 3: Copilot `AppLauncher` VLC route

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt`
- Test: `Copilot/app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt`

- [ ] **Step 1: Add failing tests**

Append to `AppLauncherTest`:

```kotlin
    @Test fun `buildRadioIntent targets VLC with audio mime and title extra`() {
        val intent = launcher.buildRadioIntent("https://live.example.ro/europafm.mp3", "Europa FM")
        assertEquals(AppLauncher.VLC_PKG, intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://live.example.ro/europafm.mp3", intent.data.toString())
        assertEquals("audio/*", intent.type)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("Europa FM", intent.getStringExtra("title"))
    }

    @Test fun `launches radio message via VLC`() {
        val res = launcher.launch(msg("radio", Form.RADIO, "https://live.example.ro/europafm.mp3"))
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AppLauncher.VLC_PKG, intent.`package`)
        assertEquals("audio/*", intent.type)
    }

    @Test fun `replays RADIO SavedItem via VLC`() {
        val item = SavedItem(Form.RADIO, "abc", "Europa FM", null, "https://live.example.ro/europafm.mp3", 0)
        val res = launcher.replay(item)
        assertTrue(res is AppLauncher.Result.Ok)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AppLauncher.VLC_PKG, intent.`package`)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd Copilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.launch.AppLauncherTest"`
Expected: FAIL — `buildRadioIntent` / `VLC_PKG` unresolved.

- [ ] **Step 3: Implement the radio route**

In `AppLauncher.kt`: replace `launch`, `replay`, and `cmdForForm`, and add the radio helpers + constant.

Replace the two entry points (`AppLauncher.kt:20-23`):

```kotlin
    /** Entry point for Pilot-driven launches via ListenerService. */
    fun launch(msg: Message): Result =
        if (msg.cmd == "radio") launchRadio(msg.url, msg.title)
        else launchUrl(msg.cmd, msg.form, msg.url)

    /** Entry point for UI-driven re-plays from a saved tile. */
    fun replay(item: SavedItem): Result =
        if (item.form == Form.RADIO) launchRadio(item.url, item.title)
        else launchUrl(cmdForForm(item.form), item.form, item.url)
```

Replace `cmdForForm` (`AppLauncher.kt:39-42`) to stay exhaustive:

```kotlin
    private fun cmdForForm(form: Form) = when (form) {
        Form.PLAYLIST, Form.SONG -> "ytmusic"
        Form.DESTINATION -> "waze"
        Form.RADIO -> "radio"
    }
```

Add these two functions (place them right after `launchUrl`):

```kotlin
    /**
     * Build the VLC launch intent for a radio stream. Internal + pure so it can be
     * asserted in tests without touching the Activity stack.
     *
     * MIME is "audio/*" (opens VLC's audio player). If a station opens VLC but does
     * not auto-play on the carbox, change this to "video/*" (known-reliable) — see plan.
     */
    internal fun buildRadioIntent(url: String, title: String?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setPackage(VLC_PKG)
            setDataAndTypeAndNormalize(Uri.parse(url), "audio/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            title?.let { putExtra("title", it) }
        }

    private fun launchRadio(url: String, title: String?): Result {
        return try {
            context.startActivity(buildRadioIntent(url, title))
            Result.Ok
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no activity for radio url=$url", e)
            Result.Failed("VLC not installed")
        } catch (e: SecurityException) {
            Log.w(TAG, "background activity start blocked", e)
            Result.Failed("background launch blocked — grant Display over other apps")
        }
    }
```

Add the package constant to the `companion object` (next to `WAZE_PKG`):

```kotlin
        const val VLC_PKG = "org.videolan.vlc"
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd Copilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.launch.AppLauncherTest"`
Expected: PASS (all existing AppLauncher tests too).

- [ ] **Step 5: Commit**

```bash
cd Copilot && git add app/src/main/java/com/vladutu/copilot/launch/AppLauncher.kt app/src/test/java/com/vladutu/copilot/launch/AppLauncherTest.kt
git commit -m "feat(copilot): launch VLC for cmd=radio"
```

---

### Task 4: Copilot `SavedItem.from` RADIO + ListenerService label

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/history/SavedItem.kt:20-26`
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/service/ListenerService.kt:90-94`
- Test: `Copilot/app/src/test/java/com/vladutu/copilot/service/ListenerServiceMappingTest.kt`

- [ ] **Step 1: Add failing tests**

In `ListenerServiceMappingTest`, the `msg` helper sets cmd from form; extend it to handle RADIO, then add the radio tests.

Replace the `msg` helper (`ListenerServiceMappingTest.kt:13-15`):

```kotlin
    private fun msg(form: Form, url: String, title: String? = null): Message {
        val cmd = when (form) {
            Form.DESTINATION -> "waze"
            Form.RADIO -> "radio"
            else -> "ytmusic"
        }
        return Message(v = 3, ts = 1_700_000_000L, cmd = cmd, form = form, url = url, title = title, imageUrl = null)
    }
```

Append:

```kotlin
    @Test fun `radio mapping uses sha1 of url`() {
        val m = msg(Form.RADIO, "https://live.example.ro/europafm.mp3", "Europa FM")
        val item = SavedItem.from(m, savedAt = 7L)
        assertEquals(Form.RADIO, item.form)
        assertEquals(40, item.id.length) // SHA-1 hex
        assertEquals("Europa FM", item.title)
    }

    @Test fun `savesToHistory returns true for radio`() {
        val radio = msg(Form.RADIO, "https://live.example.ro/europafm.mp3")
        assertEquals(true, radio.savesToHistory())
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd Copilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.service.ListenerServiceMappingTest"`
Expected: FAIL — `when (message.form)` in `SavedItem.from` is non-exhaustive (compile error).

- [ ] **Step 3: Implement**

In `SavedItem.kt`, add the RADIO branch to the `id` `when` (`SavedItem.kt:21-26`):

```kotlin
    val id = when (message.form) {
        Form.PLAYLIST -> PlaylistIdParser.parse(message.url) ?: sha1(message.url)
        Form.SONG -> Regex("""[?&]v=([A-Za-z0-9_\-]+)""").find(message.url)?.groupValues?.get(1)
            ?: sha1(message.url)
        Form.DESTINATION -> sha1(message.url)
        Form.RADIO -> sha1(message.url)
    }
```

In `ListenerService.kt`, add the `"radio"` label branch (`ListenerService.kt:90-94`):

```kotlin
                    val label = when (msg.cmd) {
                        "ytmusic" -> "play"
                        "waze", "maps" -> "navigate"
                        "radio" -> "listen"
                        else -> msg.cmd
                    }
```

(`savesToHistory()` is already `cmd != "maps"`, so radio persists — no change needed there.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd Copilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.copilot.service.ListenerServiceMappingTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd Copilot && git add app/src/main/java/com/vladutu/copilot/history/SavedItem.kt app/src/main/java/com/vladutu/copilot/service/ListenerService.kt app/src/test/java/com/vladutu/copilot/service/ListenerServiceMappingTest.kt
git commit -m "feat(copilot): persist radio history (sha1 id) + listen label"
```

---

# PHASE 2 — Copilot UI (no unit tests; verified by build + on-carbox)

> These are Compose changes. There are no Compose UI tests in this repo, so each task ends with a compile check (`./gradlew :app:compileDebugKotlin`) and a commit. The full unit-test suite + APK build is the final pass (Task 16).

### Task 5: Copilot radio strings + saved-list text + tile glyph

**Files:**
- Modify: `Copilot/app/src/main/res/values/strings.xml`
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/ui/lists/SavedListScreen.kt:52-61`
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/ui/lists/SavedTile.kt`

- [ ] **Step 1: Add strings**

In `strings.xml`, add next to the other `home_*` / `empty_*` entries:

```xml
    <string name="home_radio">Radio</string>
    <string name="empty_radio">Send a station from Pilot to fill this list</string>
```

- [ ] **Step 2: Handle RADIO in `SavedListScreen` title + empty text**

Add a `RADIO` branch to both `when (form)` blocks (`SavedListScreen.kt:52-61`):

```kotlin
    val title = when (form) {
        Form.PLAYLIST -> stringResource(R.string.home_playlists)
        Form.SONG -> stringResource(R.string.home_songs)
        Form.DESTINATION -> stringResource(R.string.home_destinations)
        Form.RADIO -> stringResource(R.string.home_radio)
    }
    val emptyText = when (form) {
        Form.PLAYLIST -> stringResource(R.string.empty_playlists)
        Form.SONG -> stringResource(R.string.empty_songs)
        Form.DESTINATION -> stringResource(R.string.empty_destinations)
        Form.RADIO -> stringResource(R.string.empty_radio)
    }
```

- [ ] **Step 3: Handle RADIO in `SavedTile` (badge + fallback glyph)**

`SavedTile` currently picks the fallback image with an `if (DESTINATION) ic_map_pin else ic_music_note`. Replace it with a `when` that uses the Material `Radio` glyph for RADIO via `rememberVectorPainter`.

Add these imports to `SavedTile.kt`:

```kotlin
import androidx.compose.material.icons.filled.Radio
import androidx.compose.ui.graphics.vector.rememberVectorPainter
```

Replace the fallback `Image(...)` in the `else` branch (`SavedTile.kt:100-108`) with:

```kotlin
                } else {
                    val fallbackPainter = when (item.form) {
                        Form.DESTINATION -> painterResource(id = R.drawable.ic_map_pin)
                        Form.RADIO -> rememberVectorPainter(Icons.Filled.Radio)
                        else -> painterResource(id = R.drawable.ic_music_note)
                    }
                    Image(
                        painter = fallbackPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
```

Add the `RADIO` branch to the private `formIcon` at the bottom of `SavedTile.kt:145-149`:

```kotlin
private fun formIcon(form: Form): ImageVector = when (form) {
    Form.PLAYLIST -> Icons.Filled.PlaylistPlay
    Form.SONG -> Icons.Filled.MusicNote
    Form.DESTINATION -> Icons.Filled.Place
    Form.RADIO -> Icons.Filled.Radio
}
```

- [ ] **Step 4: Compile check**

Run: `cd Copilot && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
cd Copilot && git add app/src/main/res/values/strings.xml app/src/main/java/com/vladutu/copilot/ui/lists/SavedListScreen.kt app/src/main/java/com/vladutu/copilot/ui/lists/SavedTile.kt
git commit -m "feat(copilot): radio list title, empty text, and tile glyph"
```

---

### Task 6: Copilot home — data-driven media grid + Radio tile

**Files:**
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt`
- Modify: `Copilot/app/src/main/java/com/vladutu/copilot/MainActivity.kt:135-146`

**Layout target (spec §6.4):** Row 1 = Waze + Maps (unchanged, 2 tiles). Media becomes a data-driven 3-column grid: Playlists, Songs, Places, **Radio**. With 4 tiles, Radio wraps to a second media row (cols 2 & 3 of that row are empty placeholders so tile sizes stay 3-wide). Knob rotation walks all 6 tiles in reading order.

- [ ] **Step 1: Rewrite `HomeScreen` with a data-driven media list**

Replace the entire body of `HomeScreen.kt` with the following. It adds an `onOpenRadio` callback, bumps `TILE_COUNT` to 6, and renders media tiles from a list chunked into rows of 3.

```kotlin
package com.vladutu.copilot.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.service.UiState

// Waze + Maps + Playlists + Songs + Places + Radio. Knob walks all six.
private const val TILE_COUNT = 6
private const val MEDIA_COLUMNS = 3

private data class MediaTile(val labelRes: Int, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun HomeScreen(
    state: UiState,
    onOpenWaze: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenDestinations: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenStatus: () -> Unit,
    onBackFromHome: () -> Unit,
) {
    BackHandler(onBack = onBackFromHome)

    // Knob twist (DPAD_LEFT/RIGHT) walks the six tiles linearly in reading order:
    // Waze → Maps → Playlists → Songs → Places → Radio. StatusPill is touch-only.
    val tileFocus = remember { List(TILE_COUNT) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusedIndex) {
        runCatching { tileFocus[focusedIndex].requestFocus() }
    }

    // Media tiles (indices 2..5). Order must match the knob reading order above.
    val mediaTiles = listOf(
        MediaTile(R.string.home_playlists, Icons.Filled.PlaylistPlay, onOpenPlaylists),
        MediaTile(R.string.home_songs, Icons.Filled.MusicNote, onOpenSongs),
        MediaTile(R.string.home_destinations, Icons.Filled.Place, onOpenDestinations),
        MediaTile(R.string.home_radio, Icons.Filled.Radio, onOpenRadio),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight ->
                        if (focusedIndex < TILE_COUNT - 1) { focusedIndex++; true } else false
                    Key.DirectionLeft ->
                        if (focusedIndex > 0) { focusedIndex--; true } else false
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header strip — pill flush right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(state = state, onClick = onOpenStatus)
        }
        // Top row — outbound nav apps (2 tiles, indices 0..1).
        Row(
            modifier = Modifier.weight(1f).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeTile(
                modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[0]),
                label = stringResource(R.string.home_waze),
                onClick = onOpenWaze,
                packageName = AppLauncher.WAZE_PKG,
                fallbackRes = R.drawable.ic_map_pin,
            )
            HomeTile(
                modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[1]),
                label = stringResource(R.string.home_maps),
                onClick = onOpenMaps,
                packageName = AppLauncher.MAPS_PKG,
                fallbackRes = R.drawable.ic_map_pin,
            )
        }
        // Media tiles — 3-column grid; Radio (4th) wraps to a second row.
        // Each row keeps weight 1f so tile size stays consistent; trailing slots
        // in a partial row are empty placeholders so tiles stay 3-wide.
        mediaTiles.chunked(MEDIA_COLUMNS).forEachIndexed { rowIndex, rowTiles ->
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (col in 0 until MEDIA_COLUMNS) {
                    val tile = rowTiles.getOrNull(col)
                    if (tile != null) {
                        // Global tile index: 2 (after Waze/Maps) + position in media list.
                        val globalIndex = 2 + rowIndex * MEDIA_COLUMNS + col
                        HomeTile(
                            modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[globalIndex]),
                            label = stringResource(tile.labelRes),
                            onClick = tile.onClick,
                            fallbackIcon = tile.icon,
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Wire `onOpenRadio` in `MainActivity`**

In `MainActivity.kt`, in the `composable("home")` block, add the `onOpenRadio` callback (after `onOpenDestinations`, `MainActivity.kt:142`):

```kotlin
                onOpenDestinations = { nav.navigate("list/destination") },
                onOpenRadio = { nav.navigate("list/radio") },
```

(The existing `composable("list/{form}")` route already resolves `radio` via `Form.fromWire`, and `SavedListScreen`/`SavedTile`/`AppLauncher.replay` are now RADIO-aware, so no other nav change is needed.)

- [ ] **Step 3: Compile check**

Run: `cd Copilot && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd Copilot && git add app/src/main/java/com/vladutu/copilot/ui/home/HomeScreen.kt app/src/main/java/com/vladutu/copilot/MainActivity.kt
git commit -m "feat(copilot): add Radio home tile (data-driven 3-col media grid)"
```

---

### Task 7: Copilot manifest — VLC package visibility

**Files:**
- Modify: `Copilot/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add VLC to `<queries>`**

In the existing `<queries>` block (alongside `com.waze`, `com.google.android.apps.maps`, `com.google.android.apps.youtube.music`), add:

```xml
    <package android:name="org.videolan.vlc" />
```

This makes VLC visible to `setPackage(...)`/intent resolution on Android 11+ (the receiver carbox). Without it, `startActivity` with `setPackage("org.videolan.vlc")` raises `ActivityNotFoundException` even when VLC is installed.

- [ ] **Step 2: Compile check**

Run: `cd Copilot && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd Copilot && git add app/src/main/AndroidManifest.xml
git commit -m "feat(copilot): declare org.videolan.vlc package visibility"
```

---

# PHASE 3 — Pilot wire + radio-browser (logic, full TDD)

### Task 8: Pilot `Form.RADIO`

**Files:**
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/catalog/Form.kt`
- Test: `Pilot/app/src/test/java/com/vladutu/pilot/catalog/FormTest.kt` *(create)*

- [ ] **Step 1: Write the failing test**

Create `Pilot/app/src/test/java/com/vladutu/pilot/catalog/FormTest.kt`:

```kotlin
package com.vladutu.pilot.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class FormTest {
    @Test fun `wire values`() {
        assertEquals("playlist", Form.PLAYLIST.wire)
        assertEquals("song", Form.SONG.wire)
        assertEquals("destination", Form.DESTINATION.wire)
        assertEquals("radio", Form.RADIO.wire)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.catalog.FormTest"`
Expected: FAIL — `Form.RADIO` unresolved.

- [ ] **Step 3: Add the enum constant**

In `Form.kt`:

```kotlin
@Serializable
enum class Form {
    PLAYLIST,
    SONG,
    DESTINATION,
    RADIO;

    /** The lowercase string used on the ntfy wire (`"playlist"` / `"song"`). */
    val wire: String get() = name.lowercase()
}
```

> Note: like Copilot, this makes several Pilot `when (form)` blocks non-exhaustive (Tile, CatalogScreen, AddUrlDialog, MetadataFetcher.refresh callers). Those are addressed in Phase 4; the run command above tests only this class.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.catalog.FormTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd Pilot && git add app/src/main/java/com/vladutu/pilot/catalog/Form.kt app/src/test/java/com/vladutu/pilot/catalog/FormTest.kt
git commit -m "feat(pilot): add Form.RADIO"
```

---

### Task 9: Pilot `RadioStation` model + JSON mapper

**Files:**
- Create: `Pilot/app/src/main/java/com/vladutu/pilot/radio/RadioStation.kt`
- Test: `Pilot/app/src/test/java/com/vladutu/pilot/radio/RadioStationTest.kt`

- [ ] **Step 1: Write the failing test**

Create `Pilot/app/src/test/java/com/vladutu/pilot/radio/RadioStationTest.kt`:

```kotlin
package com.vladutu.pilot.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationTest {

    @Test fun `maps radio-browser json fields`() {
        val json = """
            [
              {
                "stationuuid": "uuid-1",
                "name": "Europa FM",
                "url": "http://old/stream",
                "url_resolved": "https://live.example.ro/europafm.mp3",
                "favicon": "https://example.ro/fav.png",
                "codec": "MP3",
                "bitrate": 128,
                "lastcheckok": 1
              }
            ]
        """.trimIndent()
        val stations = RadioStation.listFrom(json)
        assertEquals(1, stations.size)
        val s = stations[0]
        assertEquals("uuid-1", s.stationUuid)
        assertEquals("Europa FM", s.name)
        assertEquals("https://live.example.ro/europafm.mp3", s.streamUrl) // url_resolved, not url
        assertEquals("https://example.ro/fav.png", s.faviconUrl)
        assertEquals("MP3", s.codec)
        assertEquals(128, s.bitrate)
        assertTrue(s.lastCheckOk)
    }

    @Test fun `lastcheckok 0 maps to false`() {
        val json = """[{"stationuuid":"u","name":"X","url_resolved":"https://a/s","lastcheckok":0}]"""
        assertEquals(false, RadioStation.listFrom(json)[0].lastCheckOk)
    }

    @Test fun `drops entries with blank url_resolved`() {
        val json = """
            [
              {"stationuuid":"u1","name":"Good","url_resolved":"https://a/s"},
              {"stationuuid":"u2","name":"NoStream","url_resolved":""}
            ]
        """.trimIndent()
        val stations = RadioStation.listFrom(json)
        assertEquals(1, stations.size)
        assertEquals("u1", stations[0].stationUuid)
    }

    @Test fun `blank favicon becomes null`() {
        val json = """[{"stationuuid":"u","name":"X","url_resolved":"https://a/s","favicon":""}]"""
        assertEquals(null, RadioStation.listFrom(json)[0].faviconUrl)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.radio.RadioStationTest"`
Expected: FAIL — `RadioStation` does not exist.

- [ ] **Step 3: Implement the model + mapper**

Create `Pilot/app/src/main/java/com/vladutu/pilot/radio/RadioStation.kt`:

```kotlin
package com.vladutu.pilot.radio

import org.json.JSONArray

/**
 * A station from the radio-browser community DB. [stationUuid] is the stable id
 * (survives stream-URL changes); [streamUrl] is `url_resolved` (the playable stream,
 * preferred over `url`).
 */
data class RadioStation(
    val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String?,
    val codec: String?,
    val bitrate: Int,
    val lastCheckOk: Boolean,
) {
    companion object {
        /** Map a radio-browser `/json/stations/search` response body to stations. */
        fun listFrom(body: String): List<RadioStation> {
            val arr = JSONArray(body)
            val out = ArrayList<RadioStation>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val streamUrl = o.optString("url_resolved").takeIf { it.isNotBlank() } ?: continue
                out.add(
                    RadioStation(
                        stationUuid = o.optString("stationuuid"),
                        name = o.optString("name").takeIf { it.isNotBlank() } ?: "Unknown station",
                        streamUrl = streamUrl,
                        faviconUrl = o.optString("favicon").takeIf { it.isNotBlank() },
                        codec = o.optString("codec").takeIf { it.isNotBlank() },
                        bitrate = o.optInt("bitrate", 0),
                        lastCheckOk = o.optInt("lastcheckok", 0) == 1,
                    )
                )
            }
            return out
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.radio.RadioStationTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd Pilot && git add app/src/main/java/com/vladutu/pilot/radio/RadioStation.kt app/src/test/java/com/vladutu/pilot/radio/RadioStationTest.kt
git commit -m "feat(pilot): RadioStation model + radio-browser JSON mapper"
```

---

### Task 10: Pilot `RadioBrowserServerResolver`

**Files:**
- Create: `Pilot/app/src/main/java/com/vladutu/pilot/radio/RadioBrowserServerResolver.kt`
- Test: `Pilot/app/src/test/java/com/vladutu/pilot/radio/RadioBrowserServerResolverTest.kt`

**Design (spec §3):** GET `/json/servers` → array of `{name}`. For each candidate, health-check `https://<name>/json/stats`; the first healthy one is cached for the session. The `baseFor` lambda (name → base URL) is injectable so tests can point every candidate at one MockWebServer and assert ordering/fallback/caching by response sequence.

- [ ] **Step 1: Write the failing test**

Create `Pilot/app/src/test/java/com/vladutu/pilot/radio/RadioBrowserServerResolverTest.kt`:

```kotlin
package com.vladutu.pilot.radio

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RadioBrowserServerResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var base: String

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        base = server.url("").toString().trimEnd('/')
    }

    @After fun tearDown() { server.shutdown() }

    /** All candidate names resolve to the one MockWebServer. */
    private fun resolver() = RadioBrowserServerResolver(
        client = OkHttpClient(),
        serversUrl = server.url("/json/servers").toString(),
        baseFor = { base },
    )

    @Test fun `picks first healthy server`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"name":"a"},{"name":"b"}]"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // stats for a → healthy
        val r = resolver()
        assertEquals(base, r.resolve())
        assertEquals(2, server.requestCount) // servers + one stats
    }

    @Test fun `falls back past an unhealthy server`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"name":"a"},{"name":"b"}]"""))
        server.enqueue(MockResponse().setResponseCode(500)) // stats for a → unhealthy
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // stats for b → healthy
        val r = resolver()
        assertEquals(base, r.resolve())
        assertEquals(3, server.requestCount) // servers + two stats
    }

    @Test fun `caches the resolved server`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"name":"a"}]"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val r = resolver()
        assertEquals(base, r.resolve())
        assertEquals(base, r.resolve()) // second call uses cache
        assertEquals(2, server.requestCount) // not 4 — no re-fetch
    }

    @Test fun `returns null when all servers unhealthy`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"name":"a"},{"name":"b"}]"""))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(resolver().resolve())
    }

    @Test fun `returns null when server list is empty`() = runTest {
        server.enqueue(MockResponse().setBody("""[]"""))
        assertNull(resolver().resolve())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.radio.RadioBrowserServerResolverTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement the resolver**

Create `Pilot/app/src/main/java/com/vladutu/pilot/radio/RadioBrowserServerResolver.kt`:

```kotlin
package com.vladutu.pilot.radio

import com.vladutu.pilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Resolves a healthy radio-browser mirror at runtime. Never hardcode a server —
 * mirrors come and go (spec §3). Strategy: GET /json/servers → for each candidate,
 * health-check /json/stats → cache the first that responds 2xx, fall back otherwise.
 */
class RadioBrowserServerResolver(
    private val client: OkHttpClient,
    private val serversUrl: String = "https://all.api.radio-browser.info/json/servers",
    private val baseFor: (String) -> String = { "https://$it" },
) {
    @Volatile private var cachedBase: String? = null

    suspend fun resolve(): String? = withContext(Dispatchers.IO) {
        cachedBase?.let { return@withContext it }
        val names = fetchServerNames() ?: return@withContext null
        for (name in names) {
            val candidate = baseFor(name)
            if (isHealthy(candidate)) {
                DiagnosticLog.i(TAG, "resolved radio-browser server: $candidate")
                cachedBase = candidate
                return@withContext candidate
            }
        }
        DiagnosticLog.w(TAG, "no healthy radio-browser server among ${names.size} candidates")
        null
    }

    private fun fetchServerNames(): List<String>? {
        val req = Request.Builder().url(serversUrl).header("User-Agent", USER_AGENT).build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val arr = JSONArray(body)
                (0 until arr.length()).mapNotNull {
                    arr.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() }
                }
            }
        } catch (e: Exception) {
            DiagnosticLog.w(TAG, "fetch server list failed", e)
            null
        }
    }

    private fun isHealthy(base: String): Boolean {
        val req = Request.Builder().url("$base/json/stats").header("User-Agent", USER_AGENT).build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            DiagnosticLog.w(TAG, "health-check failed for $base", e)
            false
        }
    }

    companion object {
        const val USER_AGENT = "Copilot/1.0"
        private const val TAG = "RadioResolver"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.radio.RadioBrowserServerResolverTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd Pilot && git add app/src/main/java/com/vladutu/pilot/radio/RadioBrowserServerResolver.kt app/src/test/java/com/vladutu/pilot/radio/RadioBrowserServerResolverTest.kt
git commit -m "feat(pilot): RadioBrowserServerResolver (resolve, fallback, cache)"
```

---

### Task 11: Pilot `RadioBrowserClient` (search URL + RO search)

**Files:**
- Create: `Pilot/app/src/main/java/com/vladutu/pilot/radio/RadioBrowserClient.kt`
- Test: `Pilot/app/src/test/java/com/vladutu/pilot/radio/RadioBrowserClientTest.kt`

- [ ] **Step 1: Write the failing test**

Create `Pilot/app/src/test/java/com/vladutu/pilot/radio/RadioBrowserClientTest.kt`:

```kotlin
package com.vladutu.pilot.radio

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadioBrowserClientTest {

    private lateinit var server: MockWebServer
    private lateinit var base: String

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        base = server.url("").toString().trimEnd('/')
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `searchUrl includes RO ordering and broken filter`() {
        val url = RadioBrowserClient.searchUrl(base, query = null)
        assertTrue(url.contains("countrycode=RO"))
        assertTrue(url.contains("order=votes"))
        assertTrue(url.contains("reverse=true"))
        assertTrue(url.contains("limit=50"))
        assertTrue(url.contains("hidebroken=true"))
    }

    @Test fun `searchUrl adds name filter when query given`() {
        val url = RadioBrowserClient.searchUrl(base, query = "kiss fm")
        assertTrue(url, url.contains("name=kiss%20fm") || url.contains("name=kiss+fm"))
    }

    @Test fun `searchRomania resolves a server then returns mapped stations`() = runTest {
        // /json/servers, then /json/stats (health), then the search response.
        server.enqueue(MockResponse().setBody("""[{"name":"a"}]"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(
            MockResponse().setBody(
                """[{"stationuuid":"u1","name":"Europa FM","url_resolved":"https://live/europafm.mp3","favicon":"https://f/p.png","lastcheckok":1}]"""
            )
        )
        val resolver = RadioBrowserServerResolver(
            client = OkHttpClient(),
            serversUrl = server.url("/json/servers").toString(),
            baseFor = { base },
        )
        val client = RadioBrowserClient(OkHttpClient(), resolver)
        val stations = client.searchRomania(query = null)
        assertEquals(1, stations.size)
        assertEquals("Europa FM", stations[0].name)
        assertEquals("https://live/europafm.mp3", stations[0].streamUrl)
    }

    @Test(expected = RadioBrowserException::class)
    fun `searchRomania throws when no server resolves`() = runTest {
        server.enqueue(MockResponse().setBody("""[]""")) // empty server list → resolve() null
        val resolver = RadioBrowserServerResolver(
            client = OkHttpClient(),
            serversUrl = server.url("/json/servers").toString(),
            baseFor = { base },
        )
        RadioBrowserClient(OkHttpClient(), resolver).searchRomania(query = null)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.radio.RadioBrowserClientTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement the client**

Create `Pilot/app/src/main/java/com/vladutu/pilot/radio/RadioBrowserClient.kt`:

```kotlin
package com.vladutu.pilot.radio

import com.vladutu.pilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class RadioBrowserException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Searches radio-browser for Romanian stations. Resolves a healthy mirror via
 * [resolver], then GETs /json/stations/search. Returns mapped [RadioStation]s with
 * a non-blank stream URL (radio-browser's `hidebroken=true` already drops dead ones).
 */
class RadioBrowserClient(
    private val client: OkHttpClient,
    private val resolver: RadioBrowserServerResolver,
) {
    suspend fun searchRomania(query: String?): List<RadioStation> = withContext(Dispatchers.IO) {
        val base = resolver.resolve()
            ?: throw RadioBrowserException("no healthy radio-browser server")
        val url = searchUrl(base, query)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", RadioBrowserServerResolver.USER_AGENT)
            .build()
        DiagnosticLog.i(TAG, "radio search url=$url")
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw RadioBrowserException("search HTTP ${resp.code}")
                val body = resp.body?.string() ?: return@use emptyList()
                RadioStation.listFrom(body)
            }
        } catch (e: RadioBrowserException) {
            throw e
        } catch (e: Exception) {
            throw RadioBrowserException("search failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "RadioClient"

        /**
         * Build the RO station-search URL (spec §3). When [query] is non-blank, add a
         * `name=` filter so the user can narrow results; otherwise return the top 50 by votes.
         */
        fun searchUrl(base: String, query: String?): String {
            val builder = "$base/json/stations/search".toHttpUrl().newBuilder()
                .addQueryParameter("countrycode", "RO")
                .addQueryParameter("order", "votes")
                .addQueryParameter("reverse", "true")
                .addQueryParameter("limit", "50")
                .addQueryParameter("hidebroken", "true")
            query?.trim()?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("name", it) }
            return builder.build().toString()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.radio.RadioBrowserClientTest"`
Expected: PASS.

> If the `name=kiss%20fm` vs `kiss+fm` assertion fails, okhttp encodes spaces as `%20` in query values — the test already accepts both forms.

- [ ] **Step 5: Commit**

```bash
cd Pilot && git add app/src/main/java/com/vladutu/pilot/radio/RadioBrowserClient.kt app/src/test/java/com/vladutu/pilot/radio/RadioBrowserClientTest.kt
git commit -m "feat(pilot): RadioBrowserClient (RO search + search URL builder)"
```

---

### Task 12: Pilot `NtfyPublisher.publishRadio`

**Files:**
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/net/NtfyPublisher.kt`
- Test: `Pilot/app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `NtfyPublisherTest`:

```kotlin
    @Test fun `publishRadio sends radio envelope`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishRadio(
            streamUrl = "https://live.example.ro/europafm.mp3",
            title = "Europa FM",
            imageUrl = "https://example.ro/fav.png",
        )
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals(3, body.getInt("v"))
        assertEquals("radio", body.getString("cmd"))
        assertEquals("radio", body.getString("form"))
        assertEquals("https://live.example.ro/europafm.mp3", body.getString("url"))
        assertEquals("Europa FM", body.getString("title"))
        assertEquals("https://example.ro/fav.png", body.getString("imageUrl"))
    }

    @Test fun `publishRadio omits null imageUrl`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishRadio(streamUrl = "https://live.example.ro/x.mp3", title = "X", imageUrl = null)
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals("radio", body.getString("cmd"))
        assertTrue(!body.has("imageUrl") || body.isNull("imageUrl"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.net.NtfyPublisherTest"`
Expected: FAIL — `publishRadio` unresolved.

- [ ] **Step 3: Implement `publishRadio`**

In `NtfyPublisher.kt`, add this method after `publishMaps` (the private `postEnvelope` already produces the v3 envelope shape and accepts `Form.RADIO` since `form.wire` works for any enum value):

```kotlin
    open suspend fun publishRadio(streamUrl: String, title: String?, imageUrl: String?) {
        postEnvelope(
            cmd = "radio",
            form = Form.RADIO,
            url = streamUrl,
            title = title,
            imageUrl = imageUrl,
        )
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.net.NtfyPublisherTest"`
Expected: PASS.

> Note: `NtfyPublisher.ytMusicUrl`'s `when (form)` has a `Form.DESTINATION` branch but no `RADIO` branch. Because `RADIO` was added to the enum, that `when` is now non-exhaustive and will fail to compile. Add a `Form.RADIO -> throw IllegalArgumentException("RADIO is not a YouTube Music form; use publishRadio")` branch alongside the existing `DESTINATION` one in `ytMusicUrl` (`NtfyPublisher.kt:101-107`).

- [ ] **Step 5: Commit**

```bash
cd Pilot && git add app/src/main/java/com/vladutu/pilot/net/NtfyPublisher.kt app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTest.kt
git commit -m "feat(pilot): NtfyPublisher.publishRadio (cmd=radio envelope)"
```

---

### Task 13: Pilot `RadioCatalog` (add station + manual paste)

**Files:**
- Create: `Pilot/app/src/main/java/com/vladutu/pilot/radio/RadioCatalog.kt`
- Test: `Pilot/app/src/test/java/com/vladutu/pilot/radio/RadioCatalogTest.kt`

**Design:** A station becomes a `CatalogEntry(form=RADIO, id=stationUuid, title=name, imageUrl=faviconUrl)`. `CatalogStore.upsert` already dedupes by `(form, id)`, so adding the same station twice (same `stationUuid`) replaces rather than duplicates. Manual paste has no `stationUuid`, so it keys on `sha1(streamUrl)`. Favicon download reuses `MetadataFetcher.downloadImage`.

- [ ] **Step 1: Write the failing test**

Create `Pilot/app/src/test/java/com/vladutu/pilot/radio/RadioCatalogTest.kt`:

```kotlin
package com.vladutu.pilot.radio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RadioCatalogTest {

    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun store(): CatalogStore {
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "catalog.preferences_pb") },
        )
        return CatalogStore(ds, clock = { 1000L })
    }

    private fun station(uuid: String, name: String) = RadioStation(
        stationUuid = uuid, name = name, streamUrl = "https://live/$uuid.mp3",
        faviconUrl = "https://f/$uuid.png", codec = "MP3", bitrate = 128, lastCheckOk = true,
    )

    @Test fun `addStation creates a RADIO entry keyed on stationUuid`() = runTest {
        val store = store()
        RadioCatalog.addStation(store, station("u1", "Europa FM"), imagePath = null)
        val entries = store.entries.first()
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals(Form.RADIO, e.form)
        assertEquals("u1", e.id)
        assertEquals("Europa FM", e.title)
        assertEquals("https://live/u1.mp3", e.url)
        assertEquals("https://f/u1.png", e.imageUrl)
    }

    @Test fun `addStation twice dedupes by stationUuid`() = runTest {
        val store = store()
        RadioCatalog.addStation(store, station("u1", "Europa FM"), imagePath = null)
        RadioCatalog.addStation(store, station("u1", "Europa FM (renamed)"), imagePath = null)
        val entries = store.entries.first().filter { it.form == Form.RADIO }
        assertEquals(1, entries.size)
        assertEquals("Europa FM (renamed)", entries[0].title)
    }

    @Test fun `addManual keys on sha1 of stream url`() = runTest {
        val store = store()
        RadioCatalog.addManual(store, streamUrl = "https://live/manual.mp3", title = "My Stream")
        val e = store.entries.first().single()
        assertEquals(Form.RADIO, e.form)
        assertEquals(40, e.id.length) // SHA-1 hex
        assertEquals("My Stream", e.title)
        assertEquals("https://live/manual.mp3", e.url)
    }

    @Test fun `inCatalogUuids returns ids of existing RADIO entries`() = runTest {
        val store = store()
        RadioCatalog.addStation(store, station("u1", "A"), imagePath = null)
        RadioCatalog.addStation(store, station("u2", "B"), imagePath = null)
        val ids = RadioCatalog.inCatalogUuids(store.entries.first())
        assertEquals(setOf("u1", "u2"), ids)
    }
}
```

> Note: `CatalogEntry` must carry a stream `url`. The current `CatalogEntry` has no `url` field (destinations stash the Waze URL in `id`, ytmusic reconstructs the URL from `id`). Radio needs the raw stream URL separate from the id (id = stationUuid). Step 3 adds a nullable `url` field to `CatalogEntry`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.radio.RadioCatalogTest"`
Expected: FAIL — `RadioCatalog` and `CatalogEntry.url` do not exist.

- [ ] **Step 3: Add `url` to `CatalogEntry` and implement `RadioCatalog`**

In `Pilot/app/src/main/java/com/vladutu/pilot/catalog/CatalogEntry.kt`, add a nullable `url` field (backward-compatible — `Json { ignoreUnknownKeys = true; encodeDefaults = true }` and the default keep old stored entries decodable):

```kotlin
@Serializable
data class CatalogEntry(
    val form: Form,
    val id: String,
    val title: String,
    val imagePath: String?,
    val imageUrl: String? = null,
    val googleMapsUrl: String? = null,
    /** For RADIO: the raw stream URL (id holds the stationUuid, not the URL). Null for other forms. */
    val url: String? = null,
    val savedAt: Long,
)
```

Create `Pilot/app/src/main/java/com/vladutu/pilot/radio/RadioCatalog.kt`:

```kotlin
package com.vladutu.pilot.radio

import com.vladutu.pilot.catalog.CatalogEntry
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import java.security.MessageDigest

/** Maps radio stations into the shared catalog as RADIO entries. */
object RadioCatalog {

    /** Add a discovered station. Id = stationUuid (stable across stream-URL changes). */
    suspend fun addStation(store: CatalogStore, station: RadioStation, imagePath: String?) {
        store.upsert(
            CatalogEntry(
                form = Form.RADIO,
                id = station.stationUuid,
                title = station.name,
                imagePath = imagePath,
                imageUrl = station.faviconUrl,
                url = station.streamUrl,
                savedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Manual fallback: paste a raw stream URL. No stationUuid, so key on sha1(url). */
    suspend fun addManual(store: CatalogStore, streamUrl: String, title: String?) {
        store.upsert(
            CatalogEntry(
                form = Form.RADIO,
                id = sha1(streamUrl),
                title = title?.takeIf { it.isNotBlank() } ?: streamUrl,
                imagePath = null,
                imageUrl = null,
                url = streamUrl,
                savedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Station ids already in the catalog — used to mark/disable already-added search results. */
    fun inCatalogUuids(entries: List<CatalogEntry>): Set<String> =
        entries.filter { it.form == Form.RADIO }.map { it.id }.toSet()

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.radio.RadioCatalogTest"`
Expected: PASS.

- [ ] **Step 5: Run existing catalog tests (guard the schema change)**

Run: `cd Pilot && ./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.catalog.*"`
Expected: PASS — adding a defaulted nullable field must not break `CatalogStoreTest` / `CatalogEntryTest`.

- [ ] **Step 6: Commit**

```bash
cd Pilot && git add app/src/main/java/com/vladutu/pilot/catalog/CatalogEntry.kt app/src/main/java/com/vladutu/pilot/radio/RadioCatalog.kt app/src/test/java/com/vladutu/pilot/radio/RadioCatalogTest.kt
git commit -m "feat(pilot): RadioCatalog add/dedupe + CatalogEntry.url field"
```

---

# PHASE 4 — Pilot UI (hub-and-spoke + search; verified by build + on-carbox)

> Compose changes, no unit tests. End each with `./gradlew :app:compileDebugKotlin` + commit. Final suite/APK is Task 16.

### Task 14: Pilot tile glyph + AddUrlDialog radio labels

**Files:**
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/ui/Tile.kt`
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/ui/AddUrlDialog.kt`

- [ ] **Step 1: Add RADIO to `Tile.formIcon`**

In `Tile.kt`, add the import and the `when` branch:

```kotlin
import androidx.compose.material.icons.filled.Radio
```

```kotlin
private fun formIcon(form: Form): ImageVector = when (form) {
    Form.PLAYLIST -> Icons.Filled.PlaylistPlay
    Form.SONG -> Icons.Filled.MusicNote
    Form.DESTINATION -> Icons.Filled.Place
    Form.RADIO -> Icons.Filled.Radio
}
```

- [ ] **Step 2: Add RADIO to `AddUrlDialog`'s `when` blocks**

`AddUrlDialog` has two non-exhaustive `when (activeForm)` blocks (label at `:29-32`, title at `:37-41`). Add RADIO branches:

```kotlin
    val urlLabel = when (activeForm) {
        Form.PLAYLIST, Form.SONG -> "YouTube Music URL"
        Form.DESTINATION -> "Google Maps or Waze URL"
        Form.RADIO -> "Radio stream URL (http/https)"
    }
```

```kotlin
            val title = when (activeForm) {
                Form.PLAYLIST -> "Add playlist"
                Form.SONG -> "Add song"
                Form.DESTINATION -> "Add destination"
                Form.RADIO -> "Add stream URL"
            }
```

- [ ] **Step 3: Compile check** *(will still fail until Task 15 removes CatalogScreen's tab `when`s — that's expected; just confirm Tile.kt/AddUrlDialog.kt have no remaining `when` errors via the next task's build)*

Skip the standalone build here; commit and proceed (Task 15 replaces the remaining non-exhaustive `when`s in one cohesive change).

- [ ] **Step 4: Commit**

```bash
cd Pilot && git add app/src/main/java/com/vladutu/pilot/ui/Tile.kt app/src/main/java/com/vladutu/pilot/ui/AddUrlDialog.kt
git commit -m "feat(pilot): RADIO tile glyph + AddUrlDialog labels"
```

---

### Task 15: Pilot hub-and-spoke navigation + radio tap publishing

**Files:**
- Create: `Pilot/app/src/main/java/com/vladutu/pilot/ui/HomeHub.kt`
- Create: `Pilot/app/src/main/java/com/vladutu/pilot/ui/CategoryListScreen.kt`
- Create: `Pilot/app/src/main/java/com/vladutu/pilot/ui/PilotNavHost.kt`
- Delete: `Pilot/app/src/main/java/com/vladutu/pilot/ui/CatalogScreen.kt`
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/MainActivity.kt`

**Design:** State-based hub-and-spoke (no `navigation-compose` dependency). `PilotNavHost` holds `var route: PilotRoute`, a `BackHandler` that pops back to Home, and dispatches to `HomeHub`, `CategoryListScreen(form)`, or `RadioSearchScreen` (added in Task 16). `CategoryListScreen` is the existing CatalogScreen grid restricted to one `Form`; the per-tile tap now switches on form, calling `publishRadio` for RADIO.

- [ ] **Step 1: Create `HomeHub`**

Create `Pilot/app/src/main/java/com/vladutu/pilot/ui/HomeHub.kt`:

```kotlin
package com.vladutu.pilot.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.diagnostics.DiagnosticsActivity

private data class CategorySpec(val form: Form, val label: String, val icon: ImageVector)

private val CATEGORIES = listOf(
    CategorySpec(Form.PLAYLIST, "Playlists", Icons.Filled.PlaylistPlay),
    CategorySpec(Form.SONG, "Songs", Icons.Filled.MusicNote),
    CategorySpec(Form.DESTINATION, "Places", Icons.Filled.Place),
    CategorySpec(Form.RADIO, "Radio", Icons.Filled.Radio),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHub(
    publishStatus: PublishStatusHolder,
    onOpenCategory: (Form) -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Pilot") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                actions = {
                    StatusPill(statusFlow = publishStatus.state)
                    IconButton(onClick = {
                        context.startActivity(Intent(context, DiagnosticsActivity::class.java))
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Diagnostics")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(items = CATEGORIES, key = { it.form.name }) { spec ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable { onOpenCategory(spec.form) },
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp).padding(bottom = 12.dp),
                        )
                        Text(
                            text = spec.label,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
```

> Add the missing import `androidx.compose.foundation.layout.size` at the top (used by `Modifier.size(64.dp)`).

- [ ] **Step 2: Create `CategoryListScreen`**

This is the existing CatalogScreen grid restricted to one `Form`, with a back arrow and a RADIO-aware tap + add path. Create `Pilot/app/src/main/java/com/vladutu/pilot/ui/CategoryListScreen.kt`:

```kotlin
package com.vladutu.pilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vladutu.pilot.catalog.CatalogEntry
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.destination.DestinationPipeline
import com.vladutu.pilot.diagnostics.DiagnosticLog
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.radio.RadioCatalog
import com.vladutu.pilot.share.MapsNavUrlBuilder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    form: Form,
    publisher: NtfyPublisher,
    store: CatalogStore,
    pipeline: DestinationPipeline,
    publishStatus: PublishStatusHolder,
    onBack: () -> Unit,
    onOpenRadioSearch: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val entries by store.entries.collectAsState(initial = emptyList())
    val gridState = rememberLazyGridState()
    var menuFor by remember { mutableStateOf<CatalogEntry?>(null) }
    var renameFor by remember { mutableStateOf<CatalogEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }

    val title = when (form) {
        Form.PLAYLIST -> "Playlists"
        Form.SONG -> "Songs"
        Form.DESTINATION -> "Places"
        Form.RADIO -> "Radio"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                actions = { StatusPill(statusFlow = publishStatus.state) },
            )
        },
        floatingActionButton = {
            if (form == Form.RADIO) {
                FloatingActionButton(onClick = onOpenRadioSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Find stations")
                }
            } else {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        },
    ) { padding ->
        val visible = entries.filter { it.form == form }
        if (visible.isEmpty()) {
            EmptyCategoryState(form = form, modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                state = pullState,
                onRefresh = { refreshing = false }, // radio/destinations have no metadata refresh
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = visible, key = { "${it.form}-${it.id}" }) { entry ->
                        val key = "${entry.form}-${entry.id}"
                        Box {
                            Tile(
                                form = entry.form,
                                title = entry.title,
                                imagePath = entry.imagePath,
                                busy = busy[key] == true,
                                onClick = {
                                    busy[key] = true
                                    DiagnosticLog.i("Tap", "tap ${entry.form}:${entry.id} '${entry.title}'")
                                    scope.launch {
                                        try {
                                            when (entry.form) {
                                                Form.DESTINATION -> publisher.publishWaze(entry.id, title = entry.title)
                                                Form.RADIO -> publisher.publishRadio(
                                                    streamUrl = entry.url ?: error("radio entry has no url"),
                                                    title = entry.title,
                                                    imageUrl = entry.imageUrl,
                                                )
                                                Form.PLAYLIST, Form.SONG -> publisher.publishYtMusic(
                                                    entry.form, entry.id, title = entry.title, imageUrl = entry.imageUrl,
                                                )
                                            }
                                            publishStatus.markOk()
                                            store.touch(entry.form, entry.id)
                                            gridState.animateScrollToItem(0)
                                            snackbar.showSnackbar("Sent: ${entry.title}")
                                        } catch (e: Exception) {
                                            DiagnosticLog.e("Tap", "tap publish failed (${e.javaClass.simpleName})", e)
                                            publishStatus.markFailed()
                                            snackbar.showSnackbar("Send failed — check connection")
                                        } finally {
                                            busy[key] = false
                                        }
                                    }
                                },
                                onLongClick = { menuFor = entry },
                            )
                            DropdownMenu(expanded = menuFor == entry, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = { renameFor = entry; menuFor = null },
                                )
                                if (entry.form == Form.DESTINATION && entry.googleMapsUrl != null) {
                                    DropdownMenuItem(
                                        text = { Text("Send as Maps") },
                                        onClick = {
                                            val target = entry
                                            val navUrl = MapsNavUrlBuilder.fromWazeUrl(target.id)
                                            menuFor = null
                                            if (navUrl == null) {
                                                scope.launch { snackbar.showSnackbar("No coordinates for this destination") }
                                                return@DropdownMenuItem
                                            }
                                            busy[key] = true
                                            scope.launch {
                                                try {
                                                    publisher.publishMaps(navUrl, title = target.title)
                                                    publishStatus.markOk()
                                                    store.touch(target.form, target.id)
                                                    gridState.animateScrollToItem(0)
                                                    snackbar.showSnackbar("Sent as Maps: ${target.title}")
                                                } catch (e: Exception) {
                                                    publishStatus.markFailed()
                                                    snackbar.showSnackbar("Send failed — check connection")
                                                } finally {
                                                    busy[key] = false
                                                }
                                            }
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        val target = entry
                                        scope.launch { store.delete(target.form, target.id) }
                                        menuFor = null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        renameFor?.let { target ->
            RenameDialog(
                initialTitle = target.title,
                onDismiss = { renameFor = null },
                onConfirm = { newTitle ->
                    scope.launch { store.rename(target.form, target.id, newTitle) }
                    renameFor = null
                },
            )
        }

        if (showAddDialog) {
            AddUrlDialog(
                activeForm = form,
                onSubmit = { urlText, manualTitle ->
                    showAddDialog = false
                    scope.launch {
                        try {
                            if (form == Form.RADIO) {
                                // Manual stream-URL fallback (spec §5.6): accept any http(s) URL.
                                if (!urlText.startsWith("http://") && !urlText.startsWith("https://")) {
                                    snackbar.showSnackbar("Enter an http(s) stream URL")
                                    return@launch
                                }
                                RadioCatalog.addManual(store, streamUrl = urlText, title = manualTitle)
                                snackbar.showSnackbar("Added: ${manualTitle ?: urlText}")
                            } else {
                                val result = pipeline.ingest(urlText = urlText, manualTitle = manualTitle, subject = null)
                                snackbar.showSnackbar(result.toastText)
                            }
                        } catch (t: Throwable) {
                            DiagnosticLog.e("AddUrl", "manual add threw ${t.javaClass.simpleName}", t)
                            snackbar.showSnackbar("Add failed — check log")
                        }
                    }
                },
                onDismiss = { showAddDialog = false },
            )
        }
    }
}

@Composable
private fun EmptyCategoryState(form: Form, modifier: Modifier = Modifier) {
    val text = when (form) {
        Form.PLAYLIST -> "Share a playlist from YT Music or tap + to paste a URL"
        Form.SONG -> "Share a song from YT Music or tap + to paste a URL"
        Form.DESTINATION -> "Share a Google Maps link or tap + to add a destination"
        Form.RADIO -> "Tap search to find Romanian stations, or add a stream URL"
    }
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
```

> `RenameDialog` currently lives as a `private` composable inside `CatalogScreen.kt` (deleted in Step 4). Move it into `CategoryListScreen.kt` (copy the `RenameDialog` function verbatim from `CatalogScreen.kt:345-371` into this file, keeping it `private`). It is used above.

- [ ] **Step 3: Create `PilotNavHost`**

Create `Pilot/app/src/main/java/com/vladutu/pilot/ui/PilotNavHost.kt`:

```kotlin
package com.vladutu.pilot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.destination.DestinationPipeline
import com.vladutu.pilot.meta.MetadataFetcher
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.radio.RadioBrowserClient

private sealed interface PilotRoute {
    data object Home : PilotRoute
    data class Category(val form: Form) : PilotRoute
    data object RadioSearch : PilotRoute
}

@Composable
fun PilotNavHost(
    publisher: NtfyPublisher,
    store: CatalogStore,
    metadataFetcher: MetadataFetcher,
    pipeline: DestinationPipeline,
    publishStatus: PublishStatusHolder,
    radioBrowserClient: RadioBrowserClient,
) {
    var route by remember { mutableStateOf<PilotRoute>(PilotRoute.Home) }

    // Hardware back: any spoke returns to the hub; on the hub, let the system handle it.
    BackHandler(enabled = route != PilotRoute.Home) {
        route = when (route) {
            is PilotRoute.RadioSearch -> PilotRoute.Category(Form.RADIO)
            else -> PilotRoute.Home
        }
    }

    when (val r = route) {
        is PilotRoute.Home -> HomeHub(
            publishStatus = publishStatus,
            onOpenCategory = { route = PilotRoute.Category(it) },
        )
        is PilotRoute.Category -> CategoryListScreen(
            form = r.form,
            publisher = publisher,
            store = store,
            pipeline = pipeline,
            publishStatus = publishStatus,
            onBack = { route = PilotRoute.Home },
            onOpenRadioSearch = { route = PilotRoute.RadioSearch },
        )
        is PilotRoute.RadioSearch -> RadioSearchScreen(
            client = radioBrowserClient,
            store = store,
            metadataFetcher = metadataFetcher,
            onBack = { route = PilotRoute.Category(Form.RADIO) },
        )
    }
}
```

> `RadioSearchScreen` is created in Task 16. To compile this task independently, Task 16 must land in the same build; if executing strictly task-by-task, implement Task 16's `RadioSearchScreen` file before running the build in Step 6 here. (They are split only for review granularity.)

- [ ] **Step 4: Delete `CatalogScreen.kt`**

```bash
cd Pilot && git rm app/src/main/java/com/vladutu/pilot/ui/CatalogScreen.kt
```

(Its `RenameDialog` and `AddUrlDialog` usage now live in `CategoryListScreen.kt`; `EmptyState` is replaced by `EmptyCategoryState`.)

- [ ] **Step 5: Rewire `MainActivity`**

Replace `MainActivity.kt`'s `setContent` body to call `PilotNavHost`:

```kotlin
package com.vladutu.pilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vladutu.pilot.ui.PilotNavHost
import com.vladutu.pilot.ui.theme.PilotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PilotApp
        setContent {
            PilotTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PilotNavHost(
                        publisher = app.ntfyPublisher,
                        store = app.catalogStore,
                        metadataFetcher = app.metadataFetcher,
                        pipeline = app.destinationPipeline,
                        publishStatus = app.publishStatus,
                        radioBrowserClient = app.radioBrowserClient,
                    )
                }
            }
        }
    }
}
```

> `app.radioBrowserClient` is added to `PilotApp` in Task 16.

- [ ] **Step 6: Commit** (build happens after Task 16, since these reference Task 16's symbols)

```bash
cd Pilot && git add app/src/main/java/com/vladutu/pilot/ui/HomeHub.kt app/src/main/java/com/vladutu/pilot/ui/CategoryListScreen.kt app/src/main/java/com/vladutu/pilot/ui/PilotNavHost.kt app/src/main/java/com/vladutu/pilot/MainActivity.kt
git commit -m "feat(pilot): hub-and-spoke navigation + radio tap publishing"
```

---

### Task 16: Pilot `RadioSearchScreen` + app wiring + final build

**Files:**
- Create: `Pilot/app/src/main/java/com/vladutu/pilot/ui/RadioSearchScreen.kt`
- Modify: `Pilot/app/src/main/java/com/vladutu/pilot/PilotApp.kt`

- [ ] **Step 1: Wire the radio client into `PilotApp`**

In `PilotApp.kt`, add lazy properties (after `metadataFetcher`):

```kotlin
    val radioServerResolver: com.vladutu.pilot.radio.RadioBrowserServerResolver by lazy {
        com.vladutu.pilot.radio.RadioBrowserServerResolver(client = httpClient)
    }

    val radioBrowserClient: com.vladutu.pilot.radio.RadioBrowserClient by lazy {
        com.vladutu.pilot.radio.RadioBrowserClient(client = httpClient, resolver = radioServerResolver)
    }
```

- [ ] **Step 2: Create `RadioSearchScreen`**

Create `Pilot/app/src/main/java/com/vladutu/pilot/ui/RadioSearchScreen.kt`. It loads RO stations on open, supports a name query, shows logos, and lets the user tap to add (marking already-added stations).

```kotlin
package com.vladutu.pilot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.diagnostics.DiagnosticLog
import com.vladutu.pilot.meta.MetadataFetcher
import com.vladutu.pilot.radio.RadioBrowserClient
import com.vladutu.pilot.radio.RadioCatalog
import com.vladutu.pilot.radio.RadioStation
import kotlinx.coroutines.launch

private sealed interface SearchState {
    data object Loading : SearchState
    data class Loaded(val stations: List<RadioStation>) : SearchState
    data class Error(val message: String) : SearchState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioSearchScreen(
    client: RadioBrowserClient,
    store: CatalogStore,
    metadataFetcher: MetadataFetcher,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Loading) }
    val entries by store.entries.collectAsState(initial = emptyList())
    val inCatalog = RadioCatalog.inCatalogUuids(entries)

    suspend fun runSearch(q: String?) {
        state = SearchState.Loading
        state = try {
            // lastCheckOk pre-filter (spec §8): hidebroken already drops dead streams; keep verified ones.
            SearchState.Loaded(client.searchRomania(q).filter { it.lastCheckOk })
        } catch (e: Exception) {
            DiagnosticLog.w("RadioSearch", "search failed", e)
            SearchState.Error("Couldn't load stations. Check your connection and retry.")
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { runSearch(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Find stations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Romanian stations") },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { scope.launch { runSearch(query) } }) { Text("Go") }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            when (val s = state) {
                is SearchState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is SearchState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { scope.launch { runSearch(query.takeIf { it.isNotBlank() }) } }) {
                            Text("Retry")
                        }
                    }
                }
                is SearchState.Loaded -> {
                    if (s.stations.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No stations found") }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(items = s.stations, key = { it.stationUuid }) { station ->
                                val already = station.stationUuid in inCatalog
                                ListItem(
                                    headlineContent = { Text(station.name) },
                                    supportingContent = {
                                        Text(listOfNotNull(station.codec, station.bitrate.takeIf { it > 0 }?.let { "$it kbps" }).joinToString(" · "))
                                    },
                                    leadingContent = {
                                        if (station.faviconUrl != null) {
                                            AsyncImage(
                                                model = station.faviconUrl,
                                                contentDescription = null,
                                                modifier = Modifier.size(40.dp),
                                            )
                                        } else {
                                            Icon(Icons.Filled.Radio, contentDescription = null, modifier = Modifier.size(40.dp))
                                        }
                                    },
                                    trailingContent = {
                                        if (already) Icon(Icons.Filled.Check, contentDescription = "Already added")
                                    },
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = !already) {
                                        scope.launch {
                                            val imagePath = station.faviconUrl?.let {
                                                runCatching { metadataFetcher.downloadImage(it, Form.RADIO, station.stationUuid)?.absolutePath }.getOrNull()
                                            }
                                            RadioCatalog.addStation(store, station, imagePath = imagePath)
                                            snackbar.showSnackbar("Added: ${station.name}")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2b: Add `IngestResult.toastText` confirmation**

`CategoryListScreen` references `result.toastText` (carried over from the old CatalogScreen). Confirm `IngestResult` still exposes `toastText` (it does — unchanged). No edit needed; this is a verification checkpoint.

- [ ] **Step 3: Full Pilot build + unit tests**

Run:
```bash
cd Pilot && ./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL; all unit tests pass (new radio tests + all pre-existing Pilot tests). This is the first point the whole Pilot module compiles (HomeHub/CategoryListScreen/PilotNavHost/RadioSearchScreen + deleted CatalogScreen all resolve).

- [ ] **Step 4: Full Copilot build + unit tests**

Run:
```bash
cd Copilot && ./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL; all unit tests pass.

- [ ] **Step 5: Commit**

```bash
cd Pilot && git add app/src/main/java/com/vladutu/pilot/ui/RadioSearchScreen.kt app/src/main/java/com/vladutu/pilot/PilotApp.kt
git commit -m "feat(pilot): RadioSearchScreen + wire RadioBrowserClient"
```

---

## On-carbox verification (after both APKs install)

Per spec §10 — these can't be unit-tested; verify on the actual box:

1. **VLC MIME:** Tap a radio tile in Pilot → Copilot should open VLC already playing. If VLC opens but doesn't auto-play, change `AppLauncher.buildRadioIntent` MIME from `"audio/*"` to `"video/*"` (Task 3) and rebuild Copilot.
2. **Background launch:** Confirm the receiver device has **SYSTEM_ALERT_WINDOW** ("Display over other apps") granted — same requirement as the BAL note. If YT Music launches from background, radio will too.
3. **Missing VLC:** With VLC uninstalled, a radio tap should toast "VLC not installed" (via the existing `launchOrReport` path) rather than failing silently.
4. **History:** After a successful radio launch, the station should appear in Copilot's Radio list (favicon shown) and replay from there.

---

## Self-Review

**Spec coverage:**
- §4 wire (cmd=radio, Form.RADIO, relaxed http(s), envelope shape) → Tasks 1, 2, 12.
- §5 Pilot (Form.RADIO, radio-browser pkg, search screen, add-to-catalog, publish on tap, manual fallback, no share-sheet) → Tasks 8–16. *(No share-sheet path added — ShareReceiverActivity untouched. ✓)*
- §6 Copilot (Form.RADIO, parseEnvelope radio, AppLauncher VLC route + cmdForForm, home tile + knob count, history sha1) → Tasks 1–7.
- §3 radio-browser (dynamic resolve, /servers→/stats health, cache+fallback, RO search params, field mapping, User-Agent) → Tasks 9–11.
- §8 error handling (resolver exhausts→error+manual paste; search retry; lastCheckOk filter) → Tasks 11, 15, 16.
- §10 risks (VLC MIME, background launch, favicon null, stationuuid vs sha1) → Task 3 note + on-carbox section + RadioStation null handling + Copilot sha1 decision.
- Icon Material `Radio` glyph → Tasks 5, 6, 14, 15, 16. ✓

**Decisions locked & called out:** Copilot RADIO id = `sha1(url)` (envelope carries no stationuuid; spec §10 fallback). Pilot nav = state-based (no new dep). `CatalogEntry.url` added (defaulted/nullable, backward-compatible) because radio needs the stream URL separate from the stationUuid id.

**Type consistency:** `Form.RADIO` both apps; `publishRadio(streamUrl, title, imageUrl)` (Pilot) matches envelope consumed by Copilot `parseEnvelope`; `AppLauncher.VLC_PKG` / `buildRadioIntent` consistent; `RadioCatalog.{addStation,addManual,inCatalogUuids}` signatures match call sites in CategoryListScreen/RadioSearchScreen; `RadioBrowserClient.searchRomania(query)` / `searchUrl(base, query)` consistent across tests and screen.

**Build ordering caveat (documented in-task):** Adding enum constants makes several `when` blocks non-exhaustive across both apps; each module only fully compiles once its phase completes. Per-class test commands are given for incremental TDD; full `assembleDebug` is the Task 16 final pass.
