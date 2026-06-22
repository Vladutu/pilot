# Pilot — Dynamic ntfy topic / device pairing — TDD implementation plan

**Date:** 2026-06-22
**Repo:** Pilot (`com.vladutu.pilot`)
**Spec:** `docs/superpowers/specs/2026-06-22-dynamic-ntfy-topic-design.md` (Section 1 = shared model, Section 3 = Pilot)
**Scope of THIS plan:** the Pilot portion only. Pilot is a *publisher* that *consumes* a topic owned by Copilot. It learns the topic by scanning a QR code or pasting it, stores it on disk so pairing survives restarts, and fails fast (red pill + diagnostic) if asked to publish before it is paired.

> **Commit note.** Per workspace conventions, Georgian reviews and tests on his Mac, then says "commit" — only then do commits actually happen. The numbered "commit" steps below are kept for TDD structure/ordering; **do not run `git commit` at code-writing time**, and **do not run gradle** — there is no Android SDK on this Linux box; Georgian builds and runs the tests on his Mac. The "run/FAIL → run/PASS" steps describe what *he* should see; we author the code to make them true.

---

## Goal

Replace Pilot's single hardcoded ntfy topic (`Config.NTFY_TOPIC`) with a per-user topic learned at runtime via QR scan or paste, persisted in DataStore, read live by `NtfyPublisher` at publish time. Add a Compose Settings screen (pairing + diagnostics entry), a gear entry point in `HomeHub`, and first-launch routing that opens on the pairing screen when unpaired.

## Architecture

- **`SettingsStore`** (new, `com.vladutu.pilot.settings`): thin DataStore wrapper mirroring `CatalogStore`/`DiscoverCategoryStore`. New DataStore named `"settings"`, string key `ntfy_topic`, exposes `topicFlow: Flow<String?>` and `suspend fun setTopic(topic: String)`. Null/empty until paired.
- **`TopicPairing`** (new, pure object in `com.vladutu.pilot.settings`): the only place that knows the regex and the `pilot://pair?topic=...` URI shape. Two pure functions — `validate(raw): String?` and `parsePairUri(uri): String?` — unit-tested. Reused by paste, QR, and (conceptually) every other entry point.
- **`NtfyPublisher`**: gains a `topicProvider: () -> String?`. Inside `postEnvelope` it reads `topicProvider()`; if null/blank it throws `NtfyPublishException("not paired")` *before* any network call (fail fast). Existing callers already `catch (NtfyPublishException)` and call `publishStatus.markFailed()` → red pill comes for free; we add a `DiagnosticLog` line.
- **`PilotApp`**: holds `@Volatile var currentTopic: String?`, updated by collecting `settingsStore.topicFlow` on `applicationScope`. The lazy `ntfyPublisher` is built once with `topicProvider = { currentTopic }`. No singleton rebuild on topic change.
- **`PilotNavHost`**: gains a `Settings` route and a `startUnpaired: Boolean` initial-route input. `MainActivity` gates startup with `produceState` over `topicFlow` (loading → splash; null → Settings; non-null → Home).
- **QR**: `com.journeyapps:zxing-android-embedded` + `CAMERA` permission. `SettingsScreen` launches `ScanContract` via `rememberLauncherForActivityResult`.

## Tech Stack

- Kotlin 2.4.0, AGP 9.2.1 (Kotlin compilation built into AGP — no `kotlin.android` plugin).
- Jetpack Compose (BOM `2026.05.01`), Material3, material-icons-extended.
- AndroidX DataStore Preferences `1.2.1`.
- OkHttp 5.4.0, kotlinx-serialization-json 1.11.0, Coil 2.7.0.
- Tests: JUnit4 `4.13.2`, kotlinx-coroutines-test `1.11.0`, okhttp mockwebserver, org.json. `testOptions { unitTests.isReturnDefaultValues = true }` is already set. DataStore tests use `TemporaryFolder` + `TestScope(UnconfinedTestDispatcher())` (see `DiscoverCategoryStoreTest`).
- **New dependency:** `com.journeyapps:zxing-android-embedded` (self-contained; no Google Play Services — relevant for the carbox).

## Global Constraints (verbatim)

- **Topic regex:** `^copilot-[0-9a-f]{32}$`
- **QR / pairing URI:** `pilot://pair?topic=...` (full example: `pilot://pair?topic=copilot-689e337645dc256a2b03d210d7b3c41b`)
- **Package:** `com.vladutu.pilot`
- **minSdk 29 / targetSdk 34** — `targetSdk` intentionally stays **34** (raising it changes BAL / background-activity-launch runtime behavior). `compileSdk` is 37 and may move freely.
- **Do NOT run gradle** — no Android SDK on this Linux box; Georgian builds/tests on his Mac.
- **Do NOT commit at code-writing time** — Georgian reviews + tests, then says "commit".

---

## File Structure

New files:
```
app/src/main/java/com/vladutu/pilot/settings/SettingsStore.kt        (new — DataStore wrapper)
app/src/main/java/com/vladutu/pilot/settings/TopicPairing.kt         (new — pure validate/parse)
app/src/main/java/com/vladutu/pilot/ui/SettingsScreen.kt             (new — Compose pairing + diagnostics)
app/src/test/java/com/vladutu/pilot/settings/TopicPairingTest.kt     (new — pure unit tests)
app/src/test/java/com/vladutu/pilot/settings/SettingsStoreTest.kt    (new — DataStore round-trip)
app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTopicTest.kt    (new — topicProvider fail-fast)
```

Modified files:
```
app/src/main/java/com/vladutu/pilot/config/Config.kt                 (remove NTFY_TOPIC)
app/src/main/java/com/vladutu/pilot/net/NtfyPublisher.kt             (topic → topicProvider)
app/src/main/java/com/vladutu/pilot/PilotApp.kt                      (settingsStore, currentTopic, wire publisher)
app/src/main/java/com/vladutu/pilot/ui/PilotNavHost.kt               (Settings route + startUnpaired)
app/src/main/java/com/vladutu/pilot/ui/HomeHub.kt                    ((i) → gear, onOpenSettings)
app/src/main/java/com/vladutu/pilot/MainActivity.kt                  (produceState routing gate)
app/src/main/AndroidManifest.xml                                     (CAMERA permission)
app/build.gradle.kts                                                 (zxing dependency)
gradle/libs.versions.toml                                            (zxing version + library)
```

> **Note on the spec's `CategoryListScreen.kt:101` reference:** that line is now just `actions = { StatusPill(...) }` — `CategoryListScreen` has **no** `(i)` info icon to remove or repoint. The only `Icons.Default.Info` usage in the app is in `HomeHub`. No change to `CategoryListScreen` is needed; flagged here so the reviewer isn't surprised.

---

## TDD Tasks

Tasks 1–3 are pure/unit-testable (real failing→passing tests). Tasks 4–8 are UI / Android-framework wiring that cannot run on JVM unit tests; for those the code is complete and verification is **manual on device** (called out per task).

---

### Task 1 — `TopicPairing`: pure validate + URI parse (UNIT TESTED)

**Files:** `app/src/main/java/com/vladutu/pilot/settings/TopicPairing.kt`, `app/src/test/java/com/vladutu/pilot/settings/TopicPairingTest.kt`

**Interfaces**
- Produces:
  - `object TopicPairing`
  - `fun validate(raw: String?): String?` — trims; returns the topic if it matches `^copilot-[0-9a-f]{32}$`, else `null`.
  - `fun parsePairUri(raw: String?): String?` — if `raw` is a `pilot://pair?topic=...` URI, extracts the `topic` query param and runs it through `validate`; else returns `null`. Implemented with pure string parsing (no `android.net.Uri`, so it runs on the JVM).
  - `const val SCHEME = "pilot"`, `const val HOST = "pair"`, `const val TOPIC_PARAM = "topic"`.
- Consumes: nothing.

**Steps**

1. Write the failing test file `TopicPairingTest.kt`:

```kotlin
package com.vladutu.pilot.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopicPairingTest {

    private val good = "copilot-689e337645dc256a2b03d210d7b3c41b"

    // --- validate ---

    @Test fun `validate accepts a well-formed topic`() {
        assertEquals(good, TopicPairing.validate(good))
    }

    @Test fun `validate trims surrounding whitespace`() {
        assertEquals(good, TopicPairing.validate("  $good\n"))
    }

    @Test fun `validate rejects wrong prefix`() {
        assertNull(TopicPairing.validate("pilot-689e337645dc256a2b03d210d7b3c41b"))
    }

    @Test fun `validate rejects uppercase hex`() {
        assertNull(TopicPairing.validate("copilot-689E337645DC256A2B03D210D7B3C41B"))
    }

    @Test fun `validate rejects wrong length`() {
        assertNull(TopicPairing.validate("copilot-689e337645dc256a2b03d210d7b3c41"))   // 31
        assertNull(TopicPairing.validate("copilot-689e337645dc256a2b03d210d7b3c41bb")) // 33
    }

    @Test fun `validate rejects non-hex characters`() {
        assertNull(TopicPairing.validate("copilot-689e337645dc256a2b03d210d7b3c41z"))
    }

    @Test fun `validate rejects null and blank`() {
        assertNull(TopicPairing.validate(null))
        assertNull(TopicPairing.validate(""))
        assertNull(TopicPairing.validate("   "))
    }

    // --- parsePairUri ---

    @Test fun `parsePairUri extracts and validates the topic`() {
        assertEquals(good, TopicPairing.parsePairUri("pilot://pair?topic=$good"))
    }

    @Test fun `parsePairUri tolerates extra query params and ordering`() {
        assertEquals(good, TopicPairing.parsePairUri("pilot://pair?v=1&topic=$good&x=y"))
    }

    @Test fun `parsePairUri trims whitespace around the uri`() {
        assertEquals(good, TopicPairing.parsePairUri("  pilot://pair?topic=$good  "))
    }

    @Test fun `parsePairUri rejects wrong scheme`() {
        assertNull(TopicPairing.parsePairUri("https://pair?topic=$good"))
    }

    @Test fun `parsePairUri rejects wrong host`() {
        assertNull(TopicPairing.parsePairUri("pilot://connect?topic=$good"))
    }

    @Test fun `parsePairUri rejects missing topic param`() {
        assertNull(TopicPairing.parsePairUri("pilot://pair?foo=bar"))
    }

    @Test fun `parsePairUri rejects an invalid topic inside a valid-looking uri`() {
        assertNull(TopicPairing.parsePairUri("pilot://pair?topic=not-a-topic"))
    }

    @Test fun `parsePairUri rejects a raw topic that is not a uri`() {
        assertNull(TopicPairing.parsePairUri(good))
    }

    @Test fun `parsePairUri rejects null`() {
        assertNull(TopicPairing.parsePairUri(null))
    }
}
```

2. Run on Mac: `./gradlew :app:testDebugUnitTest --tests "com.vladutu.pilot.settings.TopicPairingTest"` → **FAIL** (unresolved `TopicPairing`).

3. Create `TopicPairing.kt` with the minimal real implementation:

```kotlin
package com.vladutu.pilot.settings

/**
 * Single source of truth for the pairing-topic format and the `pilot://pair?topic=...`
 * QR/paste payload. Pure string logic only — no `android.net.Uri` — so it runs on JVM
 * unit tests and is identical across every entry point (paste, QR, deep link).
 */
object TopicPairing {

    const val SCHEME = "pilot"
    const val HOST = "pair"
    const val TOPIC_PARAM = "topic"

    private val TOPIC_REGEX = Regex("^copilot-[0-9a-f]{32}$")

    /** Returns the trimmed topic if it matches the shared format, else null. */
    fun validate(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        return if (TOPIC_REGEX.matches(t)) t else null
    }

    /**
     * If [raw] is a `pilot://pair?topic=<topic>` URI, extracts the `topic` query
     * parameter and validates it. Returns null for any other input (wrong scheme/host,
     * missing or invalid topic, or a non-URI string).
     */
    fun parsePairUri(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        val prefix = "$SCHEME://$HOST?"
        if (!s.startsWith(prefix)) return null
        val query = s.substring(prefix.length)
        val topic = query
            .split('&')
            .firstOrNull { it.substringBefore('=') == TOPIC_PARAM }
            ?.substringAfter('=', "")
        return validate(topic)
    }
}
```

4. Run on Mac → **PASS** (all `TopicPairingTest` cases green).

5. Commit (on say-so): `Pilot: add TopicPairing pure validate/parse for pairing topic`.

---

### Task 2 — `SettingsStore`: DataStore-backed topic persistence (UNIT TESTED)

**Files:** `app/src/main/java/com/vladutu/pilot/settings/SettingsStore.kt`, `app/src/test/java/com/vladutu/pilot/settings/SettingsStoreTest.kt`

**Interfaces**
- Consumes: `DataStore<Preferences>` (injected, mirrors `CatalogStore`/`DiscoverCategoryStore`).
- Produces:
  - `class SettingsStore(dataStore)`
  - `val topicFlow: Flow<String?>` — emits the stored topic, or `null` when unset/blank.
  - `suspend fun setTopic(topic: String)` — stores the topic verbatim (caller validates first).

**Steps**

1. Write the failing test `SettingsStoreTest.kt` (mirrors `DiscoverCategoryStoreTest` setup):

```kotlin
package com.vladutu.pilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsStoreTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsStore
    private val scope = TestScope(UnconfinedTestDispatcher())

    private val topic = "copilot-689e337645dc256a2b03d210d7b3c41b"

    @Before fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "settings.preferences_pb") },
        )
        store = SettingsStore(dataStore)
    }

    @Test fun `topic is null before pairing`() = runTest {
        assertNull(store.topicFlow.first())
    }

    @Test fun `setTopic then read returns the topic`() = runTest {
        store.setTopic(topic)
        assertEquals(topic, store.topicFlow.first())
    }

    @Test fun `setTopic overwrites a previous topic (re-pair)`() = runTest {
        val other = "copilot-00000000000000000000000000000000"
        store.setTopic(topic)
        store.setTopic(other)
        assertEquals(other, store.topicFlow.first())
    }
}
```

2. Run on Mac → **FAIL** (unresolved `SettingsStore`).

3. Create `SettingsStore.kt`:

```kotlin
package com.vladutu.pilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Pilot's on-disk app settings. Currently just the paired ntfy topic. Because it is
 * persisted, pairing is one-time — the topic is reloaded on every launch; re-pairing is
 * only needed if the user re-pairs or Copilot regenerates the topic. Mirrors the
 * CatalogStore / DiscoverCategoryStore DataStore pattern.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    /** Emits the stored topic, or null when unset/blank (not yet paired). */
    val topicFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY]?.takeIf { it.isNotBlank() }
    }

    /** Stores [topic] verbatim. Callers validate via TopicPairing first. */
    suspend fun setTopic(topic: String) {
        dataStore.edit { prefs -> prefs[KEY] = topic }
    }

    private companion object {
        val KEY = stringPreferencesKey("ntfy_topic")
    }
}
```

4. Run on Mac → **PASS**.

5. Commit (on say-so): `Pilot: add SettingsStore persisting paired ntfy topic in DataStore`.

---

### Task 3 — `NtfyPublisher`: topicProvider + fail-fast when unpaired (UNIT TESTED)

**Files:** `app/src/main/java/com/vladutu/pilot/net/NtfyPublisher.kt`, `app/src/test/java/com/vladutu/pilot/net/NtfyPublisherTopicTest.kt`

**Interfaces**
- Consumes: `topicProvider: () -> String?` (new constructor param, replaces `topic: String`).
- Produces: same public publish methods; throws `NtfyPublishException("not paired …")` *before* any HTTP call when `topicProvider()` is null/blank.

**Constructor change**

Old:
```kotlin
open class NtfyPublisher(
    private val client: OkHttpClient,
    private val base: String,
    private val topic: String,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
    private val maxRetries: Int = 3,
    private val retryBaseDelayMs: Long = 1_000L,
)
```
New:
```kotlin
open class NtfyPublisher(
    private val client: OkHttpClient,
    private val base: String,
    private val topicProvider: () -> String?,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
    private val maxRetries: Int = 3,
    private val retryBaseDelayMs: Long = 1_000L,
)
```

**Steps**

1. Add the failing test `NtfyPublisherTopicTest.kt` (the existing `NtfyPublisherTest` constructor calls also change — handle in step 3b):

```kotlin
package com.vladutu.pilot.net

import com.vladutu.pilot.catalog.Form
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class NtfyPublisherTopicTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun publisher(topic: () -> String?) = NtfyPublisher(
        client = OkHttpClient(),
        base = server.url("").toString().trimEnd('/'),
        topicProvider = topic,
        clock = { 12345L },
        maxRetries = 0,
        retryBaseDelayMs = 1L,
    )

    @Test fun `null topic fails fast without any network call`() = runTest {
        try {
            publisher { null }.publishCategory("Workout")
            fail("expected NtfyPublishException")
        } catch (e: NtfyPublishException) {
            assertTrue(e.message?.contains("not paired") == true)
        }
        assertEquals("must not hit the network when unpaired", 0, server.requestCount)
    }

    @Test fun `blank topic fails fast`() = runTest {
        try {
            publisher { "   " }.publishCategory("Workout")
            fail("expected NtfyPublishException")
        } catch (e: NtfyPublishException) {
            assertTrue(e.message?.contains("not paired") == true)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `present topic is used in the request path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher { "copilot-689e337645dc256a2b03d210d7b3c41b" }.publishCategory("Workout")
        val req = server.takeRequest()
        assertTrue(req.path!!.endsWith("/copilot-689e337645dc256a2b03d210d7b3c41b"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("category", body.getString("cmd"))
    }
}
```

2. Run on Mac → **FAIL** (`topicProvider` param does not exist; existing `NtfyPublisherTest` also won't compile).

3a. Edit `NtfyPublisher.kt` — change the constructor (see above) and resolve the topic at the top of `postEnvelope`:

```kotlin
    private suspend fun postEnvelope(
        cmd: String,
        formWire: String,
        url: String?,
        title: String?,
        imageUrl: String?,
    ) = withContext(Dispatchers.IO) {
        val topic = topicProvider()?.takeIf { it.isNotBlank() }
        if (topic == null) {
            DiagnosticLog.w(TAG, "publish blocked: not paired (no topic) cmd=$cmd")
            throw NtfyPublishException("not paired: no ntfy topic set")
        }

        val payload = JSONObject().apply {
            put("v", SCHEMA_VERSION)
            put("ts", clock())
            put("cmd", cmd)
            put("form", formWire)
            url?.let { put("url", it) }
            title?.let { put("title", it) }
            imageUrl?.let { put("imageUrl", it) }
        }.toString()

        val req = Request.Builder()
            .url("$base/$topic")
            .header("Title", "Copilot")
            .post(payload.toRequestBody(json))
            .build()

        DiagnosticLog.i(TAG, "publishing cmd=$cmd form=$formWire url=$url")
        executeWithRetry(req)
    }
```

> The `NtfyPublishException` is an `IOException`; existing callers in `CategoryListScreen` / `DiscoverCategoriesScreen` already `catch` it and call `publishStatus.markFailed()`, so the red pill + diagnostic "not paired" line are both satisfied with no caller changes.

3b. Update the **existing** `NtfyPublisherTest.kt` so it still compiles: in `setUp()` replace `topic = "topic"` with `topicProvider = { "topic" }`, and in `fastRetryPublisher()` likewise replace `topic = "topic"` with `topicProvider = { "topic" }`. The path-based assertions (`/topic`) remain valid.

4. Run on Mac → **PASS** (`NtfyPublisherTopicTest` + the updated `NtfyPublisherTest`).

5. Commit (on say-so): `Pilot: NtfyPublisher reads topic via provider, fails fast when unpaired`.

---

### Task 4 — `PilotApp`: settingsStore, currentTopic, wire publisher (MANUAL VERIFY)

**Files:** `app/src/main/java/com/vladutu/pilot/PilotApp.kt`

**Interfaces**
- Produces: `val settingsStore: SettingsStore`, `@Volatile var currentTopic: String?`, `ntfyPublisher` built with `topicProvider = { currentTopic }`.
- Consumes: `SettingsStore.topicFlow`, `applicationScope`.

**Steps** (no JVM unit test — Application wiring; verified on device in Task 8)

1. Add the new DataStore delegate next to the existing two:

```kotlin
private val Application.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
```

2. Add imports: `com.vladutu.pilot.settings.SettingsStore`, `kotlinx.coroutines.flow.collect` (or use `.collect {}` directly — `collectLatest` not needed here), and `kotlinx.coroutines.launch`.

3. Add the volatile topic field and the store, and collect the flow in `onCreate`:

```kotlin
    @Volatile
    var currentTopic: String? = null
        private set

    val settingsStore: SettingsStore by lazy { SettingsStore(settingsDataStore) }

    override fun onCreate() {
        super.onCreate()
        DiagnosticLog.init(this)
        DiagnosticLog.i("App", "PilotApp.onCreate (pid=${android.os.Process.myPid()})")
        applicationScope.launch {
            settingsStore.topicFlow.collect { topic ->
                currentTopic = topic
                DiagnosticLog.i("App", "topic updated: ${topic ?: "<none>"}")
            }
        }
    }
```

4. Change the publisher construction from the constant to the provider:

```kotlin
    val ntfyPublisher: NtfyPublisher by lazy {
        NtfyPublisher(client = httpClient, base = Config.NTFY_BASE, topicProvider = { currentTopic })
    }
```

5. **Manual verify (device):** covered by Task 8 end-to-end. Commit (on say-so): `Pilot: wire SettingsStore topic into PilotApp + publisher provider`.

---

### Task 5 — `Config.kt`: remove `NTFY_TOPIC` (MANUAL VERIFY via compile)

**Files:** `app/src/main/java/com/vladutu/pilot/config/Config.kt`

**Steps**

1. Delete the `NTFY_TOPIC` constant and its comment block; keep `NTFY_BASE` and `WAZE_CONVERTER_URL`. Result:

```kotlin
package com.vladutu.pilot.config

object Config {
    const val NTFY_BASE: String = "https://ntfy.sh"

    /** Maps→Waze converter endpoint. POST `url=<google maps url>` → 302 with Location: <waze url>. */
    const val WAZE_CONVERTER_URL: String = "https://waze.papko.org/"
}
```

2. **Manual verify:** the only `NTFY_TOPIC` reference was `PilotApp` (changed in Task 4). Georgian's compile confirms no other usage. Commit (on say-so): `Pilot: drop hardcoded NTFY_TOPIC constant`.

---

### Task 6 — `SettingsScreen` + QR dependency + CAMERA permission (MANUAL VERIFY)

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/vladutu/pilot/ui/SettingsScreen.kt`

**Interfaces**
- Produces: `@Composable fun SettingsScreen(settingsStore, onBack, onOpenDiagnostics)`.
- Consumes: `SettingsStore.topicFlow` + `setTopic`; `TopicPairing.validate` / `parsePairUri`; zxing `ScanContract` / `ScanOptions`.

**Steps** (UI + scanner — not JVM-unit-testable; the pure parse/validate it relies on is already covered by Task 1)

1. Add the zxing version + library to `gradle/libs.versions.toml`:

In `[versions]` add:
```toml
zxingEmbedded = "4.3.0"
```
In `[libraries]` add:
```toml
zxing-android-embedded = { group = "com.journeyapps", name = "zxing-android-embedded", version.ref = "zxingEmbedded" }
```

2. Add the dependency in `app/build.gradle.kts` (in the `dependencies {}` block, near the other `implementation`s):
```kotlin
    implementation(libs.zxing.android.embedded)
```

3. Add the CAMERA permission to `AndroidManifest.xml` (with the other `uses-permission` lines, after `POST_NOTIFICATIONS`):
```xml
    <uses-permission android:name="android.permission.CAMERA" />
```
> `zxing-android-embedded` ships its own `CaptureActivity` in its manifest, merged automatically — no `<activity>` entry needed here.

4. Create `SettingsScreen.kt` (complete, real code):

```kotlin
package com.vladutu.pilot.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.clickable
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.vladutu.pilot.settings.SettingsStore
import com.vladutu.pilot.settings.TopicPairing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentTopic by settingsStore.topicFlow.collectAsState(initial = null)

    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun savePaired(topic: String) {
        scope.launch {
            settingsStore.setTopic(topic)
            error = null
            input = ""
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents == null) {
            // user cancelled — leave state unchanged
            return@rememberLauncherForActivityResult
        }
        val topic = TopicPairing.parsePairUri(contents) ?: TopicPairing.validate(contents)
        if (topic != null) savePaired(topic)
        else error = "That QR code isn't a Copilot pairing code."
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Pairing", style = MaterialTheme.typography.titleMedium)

            Text(
                text = currentTopic?.let { "Paired: $it" } ?: "Not paired",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    error = null
                    scanLauncher.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan the Copilot pairing QR")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Text("  Scan QR")
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null },
                label = { Text("Or paste topic / pairing link") },
                isError = error != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            OutlinedButton(
                onClick = {
                    val topic = TopicPairing.parsePairUri(input) ?: TopicPairing.validate(input)
                    if (topic != null) savePaired(topic)
                    else error = "Invalid topic. Expected a copilot-… code or a pilot://pair link."
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("Open diagnostics") },
                leadingContent = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDiagnostics),
            )
        }
    }
}
```

> `MutableStateFlow` import above is unused — drop it; left out of the final import list. (Reviewer: keep imports tidy.)

5. **Manual verify (device):** see Task 8.

6. Commit (on say-so): `Pilot: add Settings screen (pairing scan/paste + diagnostics) + zxing + CAMERA`.

---

### Task 7 — `HomeHub` gear entry + `PilotNavHost` Settings route (MANUAL VERIFY)

**Files:** `app/src/main/java/com/vladutu/pilot/ui/HomeHub.kt`, `app/src/main/java/com/vladutu/pilot/ui/PilotNavHost.kt`

**Interfaces**
- `HomeHub` produces a new `onOpenSettings: () -> Unit` param; removes the `(i)` `Info` icon + the inline `DiagnosticsActivity` launch (Diagnostics now reached via Settings) and replaces it with a gear `IconButton`. Keeps `StatusPill`.
- `PilotNavHost` gains `settingsStore: SettingsStore`, a `startUnpaired: Boolean` param, a `Settings` route, and a `context.startActivity(DiagnosticsActivity)` launch from the Settings route.

**Steps**

1. `HomeHub.kt` — change the signature and the `actions` block. Remove the `Info` import and the `Intent`/`DiagnosticsActivity`/`LocalContext` usage from here (the Diagnostics entry moves to Settings). Add the `Settings` icon import.

Replace imports `import androidx.compose.material.icons.filled.Info` → `import androidx.compose.material.icons.filled.Settings`; remove `import android.content.Intent`, `import androidx.compose.ui.platform.LocalContext`, `import com.vladutu.pilot.diagnostics.DiagnosticsActivity`.

Signature:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHub(
    publishStatus: PublishStatusHolder,
    onOpenCategory: (Form) -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
) {
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        // ... LazyVerticalGrid body unchanged ...
```
> The `val context = LocalContext.current` line in `HomeHub` is removed (no longer used).

2. `PilotNavHost.kt` — add the route + params + the `Settings` branch. New imports: `androidx.compose.ui.platform.LocalContext`, `android.content.Intent`, `com.vladutu.pilot.diagnostics.DiagnosticsActivity`, `com.vladutu.pilot.settings.SettingsStore`.

```kotlin
private sealed interface PilotRoute {
    data object Home : PilotRoute
    data class Category(val form: Form) : PilotRoute
    data object RadioSearch : PilotRoute
    data object DiscoverCategories : PilotRoute
    data object Settings : PilotRoute
}

@Composable
fun PilotNavHost(
    publisher: NtfyPublisher,
    store: CatalogStore,
    discoverStore: DiscoverCategoryStore,
    metadataFetcher: MetadataFetcher,
    pipeline: DestinationPipeline,
    publishStatus: PublishStatusHolder,
    radioBrowserClient: RadioBrowserClient,
    settingsStore: SettingsStore,
    startUnpaired: Boolean,
) {
    val context = LocalContext.current
    var route by remember {
        mutableStateOf<PilotRoute>(if (startUnpaired) PilotRoute.Settings else PilotRoute.Home)
    }

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
            onOpenDiscover = { route = PilotRoute.DiscoverCategories },
            onOpenSettings = { route = PilotRoute.Settings },
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
        is PilotRoute.DiscoverCategories -> DiscoverCategoriesScreen(
            publisher = publisher,
            store = discoverStore,
            publishStatus = publishStatus,
            onBack = { route = PilotRoute.Home },
        )
        is PilotRoute.Settings -> SettingsScreen(
            settingsStore = settingsStore,
            onBack = { route = PilotRoute.Home },
            onOpenDiagnostics = {
                context.startActivity(Intent(context, DiagnosticsActivity::class.java))
            },
        )
    }
}
```

3. **Manual verify (device):** see Task 8.

4. Commit (on say-so): `Pilot: gear entry to Settings route; Diagnostics reached via Settings`.

---

### Task 8 — `MainActivity`: first-launch routing gate (MANUAL VERIFY)

**Files:** `app/src/main/java/com/vladutu/pilot/MainActivity.kt`

**Interfaces**
- Consumes: `app.settingsStore.topicFlow`, `app.ntfyPublisher`, etc.
- Produces: a `produceState` loading gate that decides `startUnpaired`, passes `settingsStore` + `startUnpaired` into `PilotNavHost`.

**Steps**

1. Rewrite `MainActivity.onCreate` to gate on the topic flow:

```kotlin
package com.vladutu.pilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.vladutu.pilot.ui.PilotNavHost
import com.vladutu.pilot.ui.theme.PilotTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PilotApp
        setContent {
            PilotTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // null = still loading; true/false = first stored value resolved.
                    val startUnpaired by produceState<Boolean?>(initialValue = null) {
                        value = app.settingsStore.topicFlow.first() == null
                    }
                    when (val unpaired = startUnpaired) {
                        null -> Unit // brief splash; Surface background only
                        else -> PilotNavHost(
                            publisher = app.ntfyPublisher,
                            store = app.catalogStore,
                            discoverStore = app.discoverCategoryStore,
                            metadataFetcher = app.metadataFetcher,
                            pipeline = app.destinationPipeline,
                            publishStatus = app.publishStatus,
                            radioBrowserClient = app.radioBrowserClient,
                            settingsStore = app.settingsStore,
                            startUnpaired = unpaired,
                        )
                    }
                }
            }
        }
    }
}
```

2. **Manual verify on device** (whole feature, in order):
   - Fresh install (no stored topic) → app opens directly on **Settings** (pairing).
   - **Save** with a valid `copilot-…` paste → "Paired: copilot-…" shows; back returns to Home.
   - Tap a category and publish → status pill green; car receives on the new topic.
   - Re-launch the app → opens on **Home** (pairing persisted across restart).
   - **Scan QR** of a `pilot://pair?topic=copilot-…` code (generated by Copilot) → pairs; non-pairing QR → inline "isn't a Copilot pairing code" error, nothing saved.
   - Invalid paste (e.g. `copilot-xyz`) → inline error, not saved.
   - Wipe app data (unpaired) then attempt a publish (e.g. via share) → status pill **red** and DiagnosticLog shows `publish blocked: not paired`.
   - Home top bar shows the **gear** (no `(i)`); gear → Settings; Settings → "Open diagnostics" launches the unchanged `DiagnosticsActivity`.

3. Commit (on say-so): `Pilot: first-launch routing — open on pairing when no stored topic`.

---

## Verification summary

- **JVM unit tests (run on Mac):** Tasks 1 (`TopicPairingTest`), 2 (`SettingsStoreTest`), 3 (`NtfyPublisherTopicTest` + updated `NtfyPublisherTest`). Command: `./gradlew :app:testDebugUnitTest`.
- **Manual on device (Mac build → carbox/phone):** Tasks 4–8 per the Task 8 checklist.
- The pure functions behind the QR/paste flow (`TopicPairing.validate` / `parsePairUri`) are fully unit-tested even though the scanner and routing themselves are manual.
