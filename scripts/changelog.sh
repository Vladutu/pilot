#!/usr/bin/env bash
set -euo pipefail

# Prepend a CHANGELOG.md section for a new release, built from the commit
# subjects since the previous tag. Conventional Commit prefixes are sorted into
# Added / Changed / Fixed / Build / Docs sections; the version's compare link is
# added to the reference block at the bottom.
#
# Idempotent: if [<version>] is already in the file, it does nothing.
#
# Usage: scripts/changelog.sh <MAJOR.MINOR.PATCH>

cd "$(dirname "$0")/.."

VERSION="${1:-}"
[ -n "$VERSION" ] || { echo "usage: scripts/changelog.sh <MAJOR.MINOR.PATCH>" >&2; exit 1; }

TAG="v$VERSION"
DATE="$(date +%F)"
REPO_URL="https://github.com/Vladutu/pilot"
FILE="CHANGELOG.md"

PREV_TAG="$(git describe --tags --abbrev=0 2>/dev/null || true)"
if [ -n "$PREV_TAG" ]; then
  RANGE="${PREV_TAG}..HEAD"
  LINK="[$VERSION]: $REPO_URL/compare/$PREV_TAG...$TAG"
else
  RANGE="HEAD"
  LINK="[$VERSION]: $REPO_URL/releases/tag/$TAG"
fi

if [ -f "$FILE" ] && grep -q "^## \[$VERSION\]" "$FILE"; then
  echo "changelog: [$VERSION] already present, skipping"
  exit 0
fi

if [ -z "$(git log --no-merges --format='%s' $RANGE 2>/dev/null)" ]; then
  echo "changelog: no commits since ${PREV_TAG:-start}, skipping" >&2
  exit 0
fi

SECTION_FILE="$(mktemp)"
trap 'rm -f "$SECTION_FILE"' EXIT

# Categorize commit subjects into Keep-a-Changelog sections.
git log --no-merges --format='%s' $RANGE | awk -v ver="$VERSION" -v date="$DATE" '
  function cap(s){ return toupper(substr(s,1,1)) substr(s,2) }
  /^docs: changelog for v/ { next }   # skip the auto-generated changelog commits
  {
    desc=$0; type=""
    if (match($0, /^[a-zA-Z]+(\([^)]*\))?!?: /)) {
      pre=substr($0, 1, RLENGTH); desc=substr($0, RLENGTH+1)
      sub(/[(!:].*$/, "", pre); type=tolower(pre)
    }
    sec="Changed"
    if      (type=="feat")                                              sec="Added"
    else if (type=="fix")                                               sec="Fixed"
    else if (type=="docs")                                              sec="Docs"
    else if (type=="build"||type=="ci"||type=="deps")                   sec="Build"
    items[sec]=items[sec] "- " cap(desc) "\n"
  }
  END {
    printf "## [%s] - %s\n\n", ver, date
    split("Added Changed Fixed Build Docs", ord, " ")
    for (i=1;i<=5;i++){ s=ord[i]; if (s in items) printf "### %s\n%s\n", s, items[s] }
  }
' > "$SECTION_FILE"

# Splice: insert the section above the first existing entry, and the compare
# link above the first existing link reference (falling back to end-of-file).
awk -v secfile="$SECTION_FILE" -v link="$LINK" '
  BEGIN { while ((getline l < secfile) > 0) sec = sec l "\n" }
  /^## \[/ && !ds { printf "%s", sec; ds=1 }
  /^\[[^]]+\]: http/ && !dl { print link; dl=1 }
  { print }
  END {
    if (!ds) printf "%s", sec
    if (!dl) print link
  }
' "$FILE" > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"

echo "changelog: added [$VERSION] - $DATE (${PREV_TAG:-start}..HEAD)"
