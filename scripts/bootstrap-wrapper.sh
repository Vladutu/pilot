#!/usr/bin/env bash
set -euo pipefail

# Regenerate the Gradle wrapper (gradlew, gradlew.bat, gradle-wrapper.jar,
# gradle-wrapper.properties) pinned to GRADLE_VERSION. Run once, then commit the
# generated files. Only needed when the wrapper is missing (fresh repo / Copilot).
#
# Requires a normally-installed Gradle on PATH. Install one first if needed:
#   sdk install gradle 8.9      # SDKMAN
#   brew install gradle         # Homebrew
#
# Why not download Gradle to a temp dir here? Running `gradle wrapper` from a
# throwaway distribution can bake that (soon-deleted) local path into
# gradle-wrapper.properties, breaking every later `./gradlew` call. Using an
# installed Gradle + an explicit distribution URL avoids that entirely.

GRADLE_VERSION="8.9"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
PROPS="gradle/wrapper/gradle-wrapper.properties"

cd "$(dirname "$0")/.."

if ! command -v gradle >/dev/null 2>&1; then
  echo "error: no 'gradle' on PATH. Install it first, e.g.:" >&2
  echo "  sdk install gradle ${GRADLE_VERSION}   # SDKMAN" >&2
  echo "  brew install gradle                    # Homebrew" >&2
  exit 1
fi

gradle --stop >/dev/null 2>&1 || true   # drop any stale daemon before regenerating
gradle wrapper --gradle-distribution-url "$DIST_URL"

# Guard: the published URL must be the official one (not a local/temp path).
if ! grep -q "^distributionUrl=https\\\\://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" "$PROPS"; then
  echo "error: unexpected distributionUrl in $PROPS:" >&2
  grep "^distributionUrl=" "$PROPS" >&2
  exit 1
fi

echo "Done. Commit: gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar $PROPS"
