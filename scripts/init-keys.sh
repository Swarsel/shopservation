#!/usr/bin/env bash
set -euo pipefail

cd "${SHOPSERVATION_ROOT:-${PRJ_ROOT:-$PWD}}"
if [[ ! -f flake.nix || ! -d app ]]; then
  echo "error: run this from the shopservation checkout (or set SHOPSERVATION_ROOT)" >&2
  exit 1
fi
mkdir -p keys

source "${SHOPSERVATION_LIB:-$(dirname "${BASH_SOURCE[0]}")}/keystore-pass.sh"
resolve_keystore_pass

umask 077

if [[ -f keys/shopservation.p12 ]]; then
  echo "keys/shopservation.p12 already exists, leaving it alone"
else
  echo "creating APK signing key -> keys/shopservation.p12"
  keytool -genkeypair \
    -keystore keys/shopservation.p12 -storetype PKCS12 \
    -storepass "$SHOPSERVATION_KEYSTORE_PASS" -keypass "$SHOPSERVATION_KEYSTORE_PASS" \
    -alias shopservation \
    -keyalg RSA -keysize 4096 -validity 10950 \
    -dname "CN=shopservation, OU=shopservation, O=swarsel, C=AT"
  echo "  fingerprint:"
  keytool -list -keystore keys/shopservation.p12 -storepass "$SHOPSERVATION_KEYSTORE_PASS" \
    | sed -n 's/^.*SHA-256: /  SHA-256: /p'
fi

if [[ -f fdroid/keystore.p12 ]]; then
  echo "fdroid/keystore.p12 already exists, leaving it alone"
else
  echo "creating F-Droid repo index key -> fdroid/keystore.p12"
  ( cd fdroid && fdroid init --keystore keystore.p12 --repo-keyalias shopservation-repo )
fi

if [[ -f fdroid/config.yml ]]; then
  if [[ $(grep -c '^keystore:' fdroid/config.yml) -gt 1 ]]; then
    echo "fixing duplicate 'keystore:' keys in fdroid/config.yml"
  fi
  python3 - <<'EOF'
lines = open('fdroid/config.yml').read().split('\n')
out, seen = [], False
for l in lines:
    if l.startswith('keystore:'):
        if seen:
            continue
        out.append('keystore: keystore.p12')
        seen = True
        continue
    out.append(l)
open('fdroid/config.yml', 'w').write('\n'.join(out))
EOF
  chmod 600 fdroid/config.yml
fi

echo
echo "done. Back these up somewhere safe, they cannot be regenerated:"
echo "  keys/shopservation.p12   (APK signing key, password: yours)"
echo "  fdroid/keystore.p12      (repo index key)"
echo "  fdroid/config.yml        (holds the repo key password fdroidserver generated)"
echo
echo "config.yml is gitignored and mode 0600. fdroidserver only reads that password"
echo "from the file, so it cannot be moved to the environment."
