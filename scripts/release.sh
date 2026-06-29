#!/usr/bin/env bash
set -euo pipefail

# Pilot release: test -> build release-signed APK -> update CHANGELOG.md ->
# publish as the latest GitHub release. Runs on the Mac. Requires: JDK 17/21, Android SDK, gh (authed),
# the in-repo keystore (keystore/signing.properties + .jks), and a committed
# Gradle wrapper (see scripts/bootstrap-wrapper.sh).
#
# Usage: scripts/release.sh <MAJOR.MINOR.PATCH>

REPO="Vladutu/pilot"
die() { echo "error: $*" >&2; exit 1; }

# --- args / version math (runs before any side effects) ---
VERSION="${1:-}"
[ -n "$VERSION" ] || die "usage: scripts/release.sh <MAJOR.MINOR.PATCH>"
if [[ ! "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  die "version must be semver MAJOR.MINOR.PATCH (got '$VERSION')"
fi
MAJOR="${BASH_REMATCH[1]}"; MINOR="${BASH_REMATCH[2]}"; PATCH="${BASH_REMATCH[3]}"
if [ "$MINOR" -ge 100 ] || [ "$PATCH" -ge 100 ]; then
  die "minor and patch must each be < 100 (versionCode scheme)"
fi
VCODE=$(( MAJOR * 10000 + MINOR * 100 + PATCH ))
TAG="v$VERSION"

cd "$(dirname "$0")/.."

# --- preflight ---
[ -z "$(git status --porcelain)" ] || die "working tree is dirty; commit or stash first"
if git rev-parse "$TAG" >/dev/null 2>&1; then die "tag $TAG already exists locally"; fi
if git ls-remote --exit-code --tags origin "$TAG" >/dev/null 2>&1; then
  die "tag $TAG already exists on origin"
fi

command -v gh >/dev/null 2>&1 || die "gh not found; install: brew install gh && gh auth login"
gh auth status >/dev/null 2>&1 || die "gh not authenticated; run: gh auth login"

if [ -z "${JAVA_HOME:-}" ] || ! "${JAVA_HOME:-/nonexistent}/bin/java" -version >/dev/null 2>&1; then
  JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  [ -d "$JBR" ] || die "set JAVA_HOME to a JDK 17/21 (Android Studio JBR not found at $JBR)"
  export JAVA_HOME="$JBR"
fi
echo "Using JAVA_HOME=$JAVA_HOME"

if [ ! -f "keystore/signing.properties" ] || [ ! -f "keystore/pilot-release.jks" ]; then
  die "in-repo keystore missing (keystore/signing.properties + keystore/pilot-release.jks)"
fi

if [ ! -x "./gradlew" ] || [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  die "Gradle wrapper missing; run scripts/bootstrap-wrapper.sh first"
fi

# --- test ---
echo "==> unit tests"
./gradlew --no-daemon testDebugUnitTest

# --- build ---
echo "==> assembleRelease ($VERSION / code $VCODE)"
./gradlew --no-daemon assembleRelease -PversionName="$VERSION" -PversionCode="$VCODE"

SRC_APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$SRC_APK" ] || die "APK not found at $SRC_APK"
OUT_APK="pilot-$VERSION.apk"
cp "$SRC_APK" "$OUT_APK"

# --- changelog ---
echo "==> update CHANGELOG.md"
./scripts/changelog.sh "$VERSION"
if ! git diff --quiet -- CHANGELOG.md; then
  git add CHANGELOG.md
  git commit -m "docs: changelog for $TAG"
fi

# --- push, tag + publish ---
echo "==> push branch + tag + GitHub release"
git push origin HEAD
git tag -a "$TAG" -m "Pilot $VERSION"
git push origin "$TAG"
gh release create "$TAG" \
  --repo "$REPO" \
  --latest \
  --title "Pilot $VERSION" \
  --generate-notes \
  "$OUT_APK"

rm -f "$OUT_APK"
echo "==> released $TAG -> https://github.com/$REPO/releases/latest"
