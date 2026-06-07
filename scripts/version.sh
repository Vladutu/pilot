#!/usr/bin/env bash
set -euo pipefail

# Print the current released version (latest git tag), and how many commits
# have landed since — i.e. what's waiting to go out in the next release.

cd "$(dirname "$0")/.."

tag="$(git describe --tags --abbrev=0 2>/dev/null || true)"
if [ -z "$tag" ]; then
  echo "no releases yet"
  exit 0
fi

ahead="$(git rev-list --count "${tag}..HEAD" 2>/dev/null || echo 0)"
if [ "$ahead" -gt 0 ]; then
  echo "$tag (+$ahead commit(s) since)"
else
  echo "$tag"
fi
