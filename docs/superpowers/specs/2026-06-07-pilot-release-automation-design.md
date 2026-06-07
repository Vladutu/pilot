# Pilot release automation — design

**Date:** 2026-06-07
**Status:** Approved (pending spec review)
**Scope:** Pilot only. Copilot gets the same treatment once this is proven.

## Problem

Today, shipping a new Pilot build is manual and annoying:

1. Build the APK in Android Studio (GUI only — no working CLI build).
2. Upload the APK to Google Drive.
3. On the phone: open Drive, download, install.
4. On the car box (different Google account): share the Drive link by email, then download/install.

Goal: a single command on the Mac — `./scripts/release.sh 0.2.0` — that runs tests,
builds a signed APK, and publishes it as the **latest** GitHub release on the private
`Vladutu/pilot` repo. [Obtainium](https://github.com/ImranR98/Obtainium) on both devices
then detects the new release and prompts to update. No more Drive/email.

## Constraints & environment

- The real dev machine is a **Mac** (`local.properties` → `/Users/geo/Library/Android/sdk`).
  The Linux container where these files are edited has no Gradle/SDK/JDK and never builds.
  **All scripts run on the Mac**, the same place Android Studio builds.
- `Vladutu/pilot` is and stays **private**. No public repos (the ntfy topic is hardcoded in
  `Config.kt`; a public repo would leak it).
- The author builds/tests/commits himself in Android Studio. This work **adds** a CLI path;
  it does not change that the author drives git.
- Both apps are `minSdk 29 / target 34`, Compose, Gradle **8.9** (per `gradle-wrapper.properties`).

## Decisions (locked)

| Topic | Decision |
|---|---|
| APK hosting | GitHub Releases on the private repo; Obtainium auto-updates both devices via a PAT. |
| Friends feature | **Dropped.** |
| Signing | Dedicated release keystore, **committed in-repo with its password** (explicit owner choice 2026-06-07; password is therefore not a secret — see Security note). |
| Versioning | **Passed to the script**: `release.sh 0.2.0`. |
| Release tooling | `gh` CLI. |
| CI | None — builds happen locally on the Mac. |

## Design

### 1. Restore the Gradle wrapper

The repo has `gradle/wrapper/gradle-wrapper.properties` (pins Gradle 8.9) but is **missing**
`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` — they were never committed
and aren't gitignored. Android Studio builds anyway because it uses its own bundled Gradle, but
a CLI script has nothing to invoke.

- Commit the two standard wrapper scripts: `gradlew` (executable) and `gradlew.bat`.
- The release script **bootstraps** `gradle-wrapper.jar` if absent: download the jar pinned to
  Gradle 8.9 and verify its SHA‑256 against a hardcoded expected value before use. Abort on
  mismatch. This keeps a binary blob out of manual authoring while guaranteeing integrity.

### 2. Dedicated release keystore (committed in-repo)

> **Decision changed 2026-06-07:** the owner chose to commit the keystore and its password
> into the (private) repo for zero-setup builds, accepting the risk. The original design kept
> it outside VCS; that is no longer the case.

- `keystore/pilot-release.jks` (PKCS12, RSA 4096) and `keystore/signing.properties`
  (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) are **committed**.
- `app/build.gradle.kts`:
  - A `release` `signingConfig` loads `keystore/signing.properties` via `java.util.Properties`
    when the file exists.
  - **Fallback:** if `keystore/signing.properties` is absent, the release build falls back to
    debug signing (so an isolated checkout still builds). `release.sh` requires the keystore
    files (preflight fails otherwise) so published APKs are always release-signed.
  - Replaces the original `signingConfig = signingConfigs.getByName("debug")` line.
- **Security note:** the password is not a secret (it sits beside the key). Anyone with repo
  read access can sign an APK as `com.vladutu.pilot` and ship an "update" that inherits granted
  permissions. Mitigation: keep the repo private; keep the Obtainium PAT read-only.
- **One-time cost:** switching signing identity from the old debug key means the new APK won't
  install over the old one — uninstall + reinstall once on both devices. Cheap at v0.1.0.

### 3. Versioning (arg-driven)

- `versionName` = the script argument (e.g. `0.2.0`).
- `versionCode` = `major*10000 + minor*100 + patch` → `0.2.0` = `200`, `1.2.3` = `10203`.
  Monotonically increasing as long as minor/patch < 100, which Obtainium needs to detect updates.
- Values are passed to Gradle as `-PversionName=… -PversionCode=…`; `defaultConfig` in
  `build.gradle.kts` reads them via `project.findProperty(...)` with the current `0.1.0` / `1`
  as fallback. No manual edits to the build file per release.

### 4. `scripts/release.sh`

Committed in the Pilot repo. Bash, `set -euo pipefail`. Single argument: the semver version.

**Validation / preflight (fail fast, no side effects):**
1. Argument is valid semver `MAJOR.MINOR.PATCH`; minor and patch < 100.
2. Git working tree is clean; tag `v<version>` does not already exist locally or on origin.
3. `gh` is installed and authenticated (`gh auth status`).
4. `JAVA_HOME` resolves to a Gradle-8.9-compatible JDK: auto-detect Android Studio's bundled JBR
   (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`), allow override via env.
5. Release signing properties are present (in `~/.gradle/gradle.properties` or env).
6. `gradle-wrapper.jar` present; bootstrap (download + SHA‑256 verify) if not.

**Build & publish:**
7. `./gradlew testReleaseUnitTest` — abort on failure.
8. `./gradlew assembleRelease -PversionName=<v> -PversionCode=<code>`.
9. Rename `app/build/outputs/apk/release/app-release.apk` → `pilot-<v>.apk`.
10. `git push origin HEAD` (advance the branch), then `git tag v<v> && git push origin v<v>`.
11. `gh release create v<v> --repo Vladutu/pilot --latest --title "Pilot <v>" --notes "<generated>" pilot-<v>.apk`.
12. Print the Obtainium URL and a success summary.

The script does **not** run `git commit` — the author commits build-file/wrapper changes in Studio.
Its git writes are pushing the current branch and the annotated tag in step 10, which are intrinsic
to cutting a release.

### 5. Obtainium setup (one-time, per device — documentation, not code)

Documented in the spec/plan, executed by the author:
1. Install Obtainium (from its own GitHub release).
2. Settings → add a **fine-grained GitHub PAT** with read-only **Contents** on `Vladutu/pilot`.
3. Add App → `https://github.com/Vladutu/pilot` → APK filter `pilot-.*\.apk` → track latest release.
4. Repeat on phone and car box (the PAT is what authorizes the private repo; the device's Google
   account is irrelevant).

## Out of scope

- Friends-can-play-music feature (dropped this round).
- Copilot — replicated after Pilot is proven.
- GitHub Actions / CI — builds stay local on the Mac.
- ProGuard/minify changes (`isMinifyEnabled` stays `false`).

## Testing / verification

- The author runs `./scripts/release.sh <next-version>` on the Mac.
- Expected: tests pass, a `pilot-<v>.apk` appears as the latest release on `Vladutu/pilot`,
  Obtainium prompts to update on both devices, and the update installs in place (after the
  one-time reinstall caused by the signing-key switch).
