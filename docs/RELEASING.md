# Releasing Pilot

## One-time setup (Mac)

1. **Gradle wrapper** — only if `gradlew` is missing (fresh clone usually has it committed).
   Needs a Gradle on PATH first: `sdk install gradle 8.9` (or `brew install gradle`), then
   `./scripts/bootstrap-wrapper.sh`. Commit the generated wrapper files.
2. **gh CLI:** `brew install gh && gh auth login`

That's it — the release keystore (`keystore/pilot-release.jks`) and its credentials
(`keystore/signing.properties`) are committed in the repo, so any clone can build a
signed release with no extra setup.

> **Security note (deliberate choice):** the signing key and its password are committed.
> The password is therefore not a secret. Anyone with read access to this repo can build
> an APK that installs as a legitimate *update* over an installed Pilot/Copilot and
> inherits its granted permissions (overlay, accessibility on Copilot). Keep the repo
> private and keep the Obtainium PAT read-only. To rotate the key you must
> uninstall/reinstall on every device.

## Cut a release

```bash
./scripts/release.sh 0.2.0
```
Runs tests, builds a release-signed APK, tags `v0.2.0`, and publishes it as the latest
release on `Vladutu/pilot` with `pilot-0.2.0.apk` attached.

`versionCode` = `major*10000 + minor*100 + patch` (so `0.2.0` -> `200`). Keep minor/patch < 100.

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
