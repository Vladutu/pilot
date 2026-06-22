#!/usr/bin/env bash
set -euo pipefail

# One-time signing setup for Pilot. Generates a fresh release keystore and a
# matching keystore/signing.properties, both of which are gitignored and must
# NEVER be committed. Run this once on your build machine (the Mac).
#
# Requires: JDK (keytool) and openssl on PATH.
#
# BACK UP keystore/pilot-release.jks somewhere safe after running this. If you
# lose it you can never ship an update over an installed Pilot again — you'd
# have to uninstall/reinstall on every device with a brand-new key.

APP="pilot"
ALIAS="pilot"

cd "$(dirname "$0")/.."
KEYSTORE_DIR="keystore"
KEYSTORE="$KEYSTORE_DIR/${APP}-release.jks"
PROPS="$KEYSTORE_DIR/signing.properties"

if [ -f "$KEYSTORE" ]; then
  echo "ERROR: $KEYSTORE already exists — refusing to overwrite your signing key." >&2
  echo "Delete it by hand only if you are certain you want a new identity." >&2
  exit 1
fi

mkdir -p "$KEYSTORE_DIR"

# Strong random password, alphanumeric so it survives any properties parsing.
# `|| true` swallows the SIGPIPE that head triggers in tr under `set -o pipefail`.
PASSWORD="$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom 2>/dev/null | head -c 32 || true)"

keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=Pilot, OU=, O=, L=, S=, C="

umask 077
cat > "$PROPS" <<EOF
storeFile=${APP}-release.jks
storePassword=${PASSWORD}
keyAlias=${ALIAS}
keyPassword=${PASSWORD}
EOF

echo
echo "Created $KEYSTORE and $PROPS (both gitignored, local only)."
echo "BACK UP $KEYSTORE now — losing it means you can never update an installed Pilot again."
