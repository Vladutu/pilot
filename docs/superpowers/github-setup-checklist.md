# GitHub Setup Checklist — Vulnerability-Patch Flow

Do these once for this repository. They cannot be set from committed files.

> **Plan note:** This setup targets a **free personal account with a private repo**, where
> GitHub's native auto-merge and branch-protection/required-checks are **not** available.
> Instead, merging is done by the CI workflow itself: the `dependabot-auto-merge` job in
> `.github/workflows/ci.yml` runs only after the `build` job succeeds (`needs: build`), so a
> Dependabot patch/minor PR is merged only when build + unit tests + lint pass. No paid
> features required. No emulator runs on CI — instrumented tests are out of scope.

## 1. Enable detection (Settings → Advanced Security)
- [ ] Enable **Dependency graph** (required for security updates on private repos).
- [ ] Enable **Dependabot alerts**.
- [ ] Enable **Dependabot security updates** (this is what auto-opens fix PRs for CVEs).
  - Note: auto-merge is gated on patch/minor version type, not on security specifically — so every security fix that is a patch/minor merges automatically once the build passes; major fixes wait for you.

## 2. Notifications (per-repo, easy to miss)
- Owning the repo subscribes you at "Participating & @mentions" only — Dependabot does not
  @mention you, so **you get no email for its PRs by default**.
- [ ] On the repo page: **Watch ▾ → Custom → Pull requests** (or All Activity).
- [ ] Account level: Settings → Notifications → "Watching" has **Email** ticked.

## 3. Verify end-to-end
- [ ] Wait for / trigger a Dependabot PR. To trigger immediately instead of waiting for the
      weekly schedule: repo → Insights → Dependency graph → Dependabot → **"Check for updates"**.
- [ ] On the PR, confirm the **CI** workflow runs and the **Build & Test** job is green.
- [ ] For a **patch or minor** Dependabot PR: confirm the `dependabot-auto-merge` job runs
      after `build` succeeds and merges the PR (branch auto-deleted).
- [ ] For a **major** Dependabot PR (e.g. an AGP or Kotlin major): confirm it is **not**
      merged (the merge step is skipped); it waits for you to review manually.

## Releases are unaffected
Merging to `master` only runs CI — it does not publish anything. Releases stay manual via
`make release V=x.y.z` from the Mac, same as before.

## Android-specific notes
- The `build` job runs `./gradlew assembleDebug testDebugUnitTest lintDebug` — debug
  variant only, so the release signing config (`keystore/signing.properties`) is never
  needed on CI.
- `local.properties` is gitignored; on CI the Android SDK comes preinstalled on
  `ubuntu-latest` and AGP finds it via `ANDROID_HOME`, no setup step needed.
- Dependabot's `gradle` ecosystem reads `gradle/libs.versions.toml`, so catalog-managed
  versions (AGP, Kotlin, Compose BOM, OkHttp, …) are all covered. It also bumps the
  Gradle wrapper (`gradle-wrapper.properties`) — verified 2026-06-10.
- Lint runs without a baseline: every lint **error** fails CI (warnings don't). If a
  dependency bump ever introduces an unfixable false positive, prefer a targeted
  `@SuppressLint("...")` at the call site over a baseline file.
