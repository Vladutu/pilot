# Releasing Pilot

## One-time setup (Mac)

1. **Gradle wrapper** — only if `gradlew` is missing (fresh clone usually has it committed).
   Needs a Gradle on PATH first: `sdk install gradle 8.9` (or `brew install gradle`), then
   `./scripts/bootstrap-wrapper.sh`. Commit the generated wrapper files.
2. **gh CLI:** `brew install gh && gh auth login`
3. **Signing key** — run `./scripts/setup-signing.sh` once. It generates
   `keystore/pilot-release.jks` and `keystore/signing.properties` with a random
   password. Both are **gitignored and never committed** — they exist only on this
   machine. **Back up the `.jks`**: if you lose it you can never ship an update over
   an installed Pilot again (you'd have to uninstall/reinstall everywhere with a new key).

The keystore and its password are **not** in the repo, so this build setup is safe to
make public. A fresh clone with no keystore still builds — the `release` build type
falls back to debug signing when `keystore/signing.properties` is absent (see
`app/build.gradle.kts`); only your machine, with the real keystore, produces installable
release updates.

> **Why local-only:** the signing key lets anyone build an APK that installs as a
> legitimate *update* over an installed Pilot and inherits its granted permissions
> (overlay). That power must never be public, so the key lives only here.

## Cut a release

```bash
./scripts/release.sh 0.2.0
```
Runs tests, builds a release-signed APK, tags `v0.2.0`, and publishes it as the latest
release on `Vladutu/pilot` with `pilot-0.2.0.apk` attached.

`versionCode` = `major*10000 + minor*100 + patch` (so `0.2.0` -> `200`). Keep minor/patch < 100.

## Verify the installed version

Open Pilot → tap the **ⓘ** (Diagnostics) action in the top bar → the version `vX.Y.Z`
(the build's `versionName`) shows at the top of the log view. Use it to confirm a device
picked up a new release. Debug builds read `vX.Y.Z-debug`.

## Obtainium (each device: phone + car box)

1. Install Obtainium (from its GitHub releases page).
2. Settings -> add a **fine-grained GitHub PAT**, repo access limited to `Vladutu/pilot`,
   permission **Contents: Read-only**.
3. Add App -> URL `https://github.com/Vladutu/pilot`:
   - APK filter regex: `pilot-.*\.apk`
   - Track only the latest release.
4. Obtainium then prompts to update whenever `release.sh` publishes a newer version.

> The signing switch to the dedicated keystore means the first new build won't install
> over an old debug-signed Pilot — uninstall the old app once on each device, then install.
