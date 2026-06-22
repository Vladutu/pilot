# Dynamic ntfy topic / device pairing — design

**Date:** 2026-06-22
**Repos:** Copilot (`com.vladutu.copilot`), Pilot (`com.vladutu.pilot`), Wingman (Python Discord bot)
**Motivation:** A Reddit post drew testers. Today the ntfy topic is hardcoded to a single shared value across all three apps, so every tester would share one channel. Each user needs their own topic, created and managed without editing source.

---

## 1. Shared model

There is **one topic per user**, owned by their **Copilot** install (the receiver). Copilot mints it; Pilot and Wingman are publishers that must be told what it is. No server syncs them — each app keeps its own on-disk copy after pairing.

- **Topic format:** unchanged shape — `copilot-` followed by 32 lowercase hex characters, produced from a secure random source (16 bytes → hex). The `copilot-` prefix keeps topics recognizable and drop-in compatible with existing installs.
- **QR payload:** a custom URI, `pilot://pair?topic=copilot-<32hex>`. Encoding a URI (not the raw string) lets Pilot's scanner confirm a QR is genuinely a pairing code and reject unrelated QRs.
- **Single validation rule** used by every app and at every entry point (QR, paste, Discord command):
  - regex `^copilot-[0-9a-f]{32}$`
  - when reading a `pilot://pair?...` URI, extract the `topic` query parameter first, then apply the regex.
- **Regeneration is destructive by design:** minting a new topic on Copilot disconnects Pilot and Wingman until each is re-paired. This is inherent to having no sync server; it is guarded by a confirmation dialog with explicit copy.

**Current hardcoded value (all three repos):** `copilot-689e337645dc256a2b03d210d7b3c41b`
- Copilot: `app/.../config/Config.kt:7`
- Pilot: `app/.../config/Config.kt:9`
- Wingman: `config.py:21-23` (env `WINGMAN_NTFY_TOPIC` override + this default)

---

## 2. Copilot — owns & generates the topic

**Persistence.** Extend the existing `SettingsStore` (`app/.../settings/SettingsStore.kt`, DataStore "copilot_settings") with a string key `ntfy_topic`, exposing `topicFlow: Flow<String?>`. Add a `TopicProvider` (or a `SettingsStore.ensureTopic()` suspend) that, on first read, generates a topic and saves it if none exists, returning the value. Generation: `SecureRandom` → 16 bytes → lowercase hex → prefix `copilot-`.

**Live use in the listener.** `ListenerService.runLoop()` (`app/.../service/ListenerService.kt:69-81`) stops reading `Config.NTFY_TOPIC` and instead consumes `settingsStore.topicFlow`, collected with `collectLatest { topic -> subscribe(topic) }`. `collectLatest` cancels the in-flight subscription and resubscribes when the topic changes, so a regenerate takes effect with **no app restart**. The service must wait for / trigger `ensureTopic()` so a freshly installed Copilot has a topic before it subscribes.

**Status screen.** `StatusScreen.kt:97` (displays a truncated topic) reads the dynamic value instead of the constant.

**Settings UI.** Add a "Pairing" section to the existing `SettingsScreen` (`app/.../ui/settings/SettingsScreen.kt`):
- Current topic shown truncated, with a **Copy** button (full topic → clipboard via `ClipboardManager`).
- **Show QR code** → a dialog that renders the `pilot://pair?topic=...` value as a QR bitmap (zxing `BarcodeEncoder`), shown in a Compose `Image`.
- **Regenerate topic** → confirmation dialog ("This will disconnect Pilot and Wingman until you re-pair them. Continue?"). On confirm: mint a new topic, save via `SettingsStore`; the listener auto-resubscribes.

**Dependency.** Add `com.google.zxing:core` for QR bitmap generation.

**Cleanup.** Remove the `NTFY_TOPIC` constant from `Config.kt` (keep `NTFY_BASE`, `MAX_MESSAGE_AGE_SEC`).

---

## 3. Pilot — consumes the topic (scan / paste)

**Persistence (survives restart).** Add a new `SettingsStore` mirroring the existing DataStore pattern (`CatalogStore` / `DiscoverCategoryStore`), backed by a new DataStore "settings", with a string key `ntfy_topic` and `topicFlow: Flow<String?>`. Empty until paired. Because it is on disk, **pairing is one-time** — the topic is reloaded on every launch; no re-pairing after a restart. Re-pairing is only needed if the user explicitly re-pairs or Copilot regenerates.

**Live use in the publisher.** `NtfyPublisher` (`app/.../net/NtfyPublisher.kt`) is a lazy singleton built once in `PilotApp`. Give it a `topicProvider: () -> String?`. `PilotApp` holds a `@Volatile var currentTopic: String?` updated by collecting `topicFlow` on `applicationScope`; the publisher reads it inside `postEnvelope` (line ~97). If the topic is null at publish time, fail fast: surface a "not paired" error through `PublishStatusHolder` (status pill red) and the `DiagnosticLog`. This avoids rebuilding the singleton on every change.

**Settings screen (new).** Add a `Settings` route to the manual `PilotNavHost` state machine (`app/.../ui/PilotNavHost.kt`). The screen (Compose, matching app style) contains:
- **Pairing:** current topic or "Not paired"; a **Scan QR** button; a **manual paste / enter** text field; a **Save** action that validates (shared regex) and stores. Invalid input shows an inline error and does not save.
- **Diagnostics:** a row that launches the existing `DiagnosticsActivity` unchanged. The Activity is *not* ported to Compose — only its entry point moves here, minimizing risk.

**Entry point swap.** In `HomeHub` (`app/.../ui/HomeHub.kt:72-76`) remove the `(i)` info circle next to the status pill and put a **gear** icon there that navigates to `Settings`. (The `(i)` in `CategoryListScreen.kt:101` is removed or likewise repointed for consistency.)

**QR scanning.** Add `com.journeyapps:zxing-android-embedded` (self-contained; no Google Play Services dependency — relevant for the carbox) and the `CAMERA` permission to `AndroidManifest.xml`. Launch the scanner via its `ScanContract` `ActivityResultLauncher` from the Settings screen. On result: parse the `pilot://pair?topic=...` URI, validate, save to `SettingsStore`. A non-matching QR shows a clear error.

**First-launch routing.** At startup, decide the initial route from the stored topic. Use a `produceState`/loading gate around `topicFlow`: while loading show nothing/splash; if the topic is null, open directly on the pairing (Settings) screen; otherwise open `Home`.

**Cleanup.** Remove the `NTFY_TOPIC` constant from `Config.kt` (keep `NTFY_BASE`).

---

## 4. Wingman — set via Discord command, persisted on a volume

**Persistence.** Add `WINGMAN_STATE_FILE` to `config.py` (default `/data/wingman_state.json`). A small state module loads the file at startup: if it exists and holds a valid topic, use it; otherwise fall back to the current env/default `NTFY_TOPIC`. Writes are atomic (write temp + rename).

**Live topic.** Replace the startup-baked `functools.partial(publish, topic=config.NTFY_TOPIC)` (`bot.py:45`) with a read of the state module's current topic at publish time, so a change takes effect without restarting the bot.

**Command.** In the existing `on_message` handler (`bot.py:37-54`), recognize, in the watched channel:
- `!settopic <value>` — open to anyone in the channel (per decision). `<value>` may be a raw topic or a pasted `pilot://pair?topic=...` URI (strip to the topic). Validate with the shared regex; on success write the state file, react ✅, and reply confirming; on failure react ❌ with a short reason.
- `!topic` (no argument) — reply with the current topic.

These are parsed before the existing link-forwarding logic and short-circuit it.

**Deployment (TeamCity, not docker-compose).** Update the "Deploy container" script step in `.teamcity/settings.kts:55-79`: add a named volume to the `docker run` — `-v wingman-data:/data`. A named volume survives the `docker rm -f` + re-run redeploy cycle, so the topic persists across **both** restarts and redeploys. Optionally set `WINGMAN_STATE_FILE` there too, though the default already points at `/data`. Update `README.md` to document the volume (replacing any docker-compose mention).

---

## 5. Testing

- **Copilot:** unit-test topic generation (format/regex) and the validation function.
- **Pilot:** unit-test the `pilot://pair?...` URI parse + validation as a pure function. QR scanning and first-launch routing are verified manually on device.
- **Wingman:** pytest for command parsing (`!settopic`/`!topic`, raw vs URI input), validation, and state-file load/save round-trip (including the env-fallback path).

## 6. Error handling summary

- Pilot publish with no topic → fast failure, red status pill, diagnostic log entry "not paired".
- Invalid QR / paste / command input → clear inline (Pilot) or ❌ reply (Wingman); never saved.
- Copilot regenerate → guarded by confirmation; listener auto-resubscribes.

## 7. Process notes

- One coherent feature, **three separate repos** → **three independent implementation plans / commits**.
- Per workspace conventions: do not run gradle (no Android SDK on the Linux box) and do not commit at code-writing time — Georgian tests on his Mac, then says "commit".
- The workspace root (`/home/geo/projects`) is not a git repo, so this spec is written here but not committed; each repo's plan and code land in that repo.
