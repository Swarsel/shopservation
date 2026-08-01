#!/usr/bin/env bash
set -euo pipefail

cd "${SHOPSERVATION_ROOT:-${PRJ_ROOT:-$PWD}}"
if [[ ! -f flake.nix || ! -d app ]]; then
  echo "error: run this from the shopservation checkout (or set SHOPSERVATION_ROOT)" >&2
  exit 1
fi

source "${SHOPSERVATION_LIB:-$(dirname "${BASH_SOURCE[0]}")}/keystore-pass.sh"
resolve_keystore_pass

export SHOPSERVATION_KEYSTORE="${SHOPSERVATION_KEYSTORE:-$PWD/keys/shopservation.p12}"
export SHOPSERVATION_KEY_ALIAS="${SHOPSERVATION_KEY_ALIAS:-shopservation}"

if [[ ! -f "$SHOPSERVATION_KEYSTORE" ]]; then
  echo "error: no signing key at $SHOPSERVATION_KEYSTORE — run 'nix run .#init-keys' first" >&2
  exit 1
fi
if [[ ! -f fdroid/keystore.p12 ]]; then
  echo "error: no repo key at fdroid/keystore.p12 — run 'nix run .#init-keys' first" >&2
  exit 1
fi

check_keystore_pass "$SHOPSERVATION_KEYSTORE"

if [[ $(grep -c '^keystore:' fdroid/config.yml) -gt 1 ]]; then
  echo "error: fdroid/config.yml has more than one 'keystore:' line, which fdroid rejects." >&2
  echo "       keep only 'keystore: keystore.p12' and delete the other." >&2
  exit 1
fi
if ! grep -qE '^repo_url:' fdroid/config.yml; then
  echo "error: set repo_url in fdroid/config.yml before publishing" >&2
  exit 1
fi

if [[ -n ${SHOPSERVATION_PAGES:-} && $SHOPSERVATION_PAGES == "$(git branch --show-current)" ]]; then
  echo "error: SHOPSERVATION_PAGES=$SHOPSERVATION_PAGES is the branch you are on." >&2
  echo "       It publishes from a SEPARATE branch (e.g. gh-pages) via its own worktree," >&2
  echo "       so it cannot be the current branch." >&2
  echo "       To publish from this branch instead, use:" >&2
  echo "           SHOPSERVATION_DOCS=docs nix run .#publish" >&2
  exit 1
fi

echo "==> building signed release APK"
gradle --console=plain --no-daemon assembleRelease

apk=app/build/outputs/apk/release/app-release.apk
if [[ ! -f $apk ]]; then
  echo "error: expected $apk but it is missing" >&2
  exit 1
fi

echo "==> verifying signature"
if ! apksigner verify --print-certs "$apk" >/tmp/shopservation-certs.txt 2>&1; then
  echo "error: $apk is not validly signed:" >&2
  cat /tmp/shopservation-certs.txt >&2
  exit 1
fi
if grep -qi "CN=Android Debug" /tmp/shopservation-certs.txt; then
  echo "error: $apk is debug-signed; refusing to publish" >&2
  exit 1
fi
sed -n 's/^Signer #1 certificate SHA-256 digest: /    apk signer SHA-256: /p' /tmp/shopservation-certs.txt

echo "==> updating F-Droid repo"
mkdir -p fdroid/repo
cp -f "$apk" "fdroid/repo/win.swarsel.shopservation.apk"
( cd fdroid && fdroid update --create-metadata --pretty ) 2>&1 | tee /tmp/shopservation-fdroid.log

fingerprint=$(
  grep -A1 'Creating signed index with this key' /tmp/shopservation-fdroid.log \
    | tail -1 | grep -oE '([0-9A-F]{2} ){31}[0-9A-F]{2}' | tr -d ' ' || true
)
repo_url=$(sed -n 's/^repo_url: *//p' fdroid/config.yml | tr -d '"' || true)

echo
echo "==> repo ready in fdroid/repo"
echo "    URL:         ${repo_url:-<set repo_url in fdroid/config.yml>}"
if [[ -n $fingerprint ]]; then
  echo "    fingerprint: $fingerprint"
  echo
  echo "Add this in F-Droid (fingerprint pinned, so a MITM cannot swap the repo):"
  echo "    ${repo_url}?fingerprint=${fingerprint}"
else
  echo "    fingerprint: <see the 'Creating signed index' lines above>"
fi
echo
if [[ -n ${SHOPSERVATION_DOCS:-} ]]; then
  echo "==> copying into ${SHOPSERVATION_DOCS}/"
  rm -rf "$SHOPSERVATION_DOCS/repo"
  mkdir -p "$SHOPSERVATION_DOCS/repo"
  cp -r fdroid/repo/. "$SHOPSERVATION_DOCS/repo/"
  touch "$SHOPSERVATION_DOCS/.nojekyll"
  if ! git add -n "$SHOPSERVATION_DOCS/repo/win.swarsel.shopservation.apk" >/dev/null 2>&1; then
    echo "error: the APK in $SHOPSERVATION_DOCS/ is gitignored, so it would not be published." >&2
    echo "       .gitignore needs '!$SHOPSERVATION_DOCS/repo/*.apk' after the blanket '*.apk' rule." >&2
    exit 1
  fi
  echo "    done — commit and push it on your current branch:"
  echo "        git add $SHOPSERVATION_DOCS && git commit && git push"
elif [[ -n ${SHOPSERVATION_PAGES:-} ]]; then
  echo "==> staging into the $SHOPSERVATION_PAGES worktree"
  worktree=.gh-pages

  if [[ ! -d $worktree/.git && ! -f $worktree/.git ]]; then
    if git show-ref --quiet "refs/heads/$SHOPSERVATION_PAGES"; then
      git worktree add --quiet "$worktree" "$SHOPSERVATION_PAGES"
    else
      git worktree add --quiet --detach "$worktree"
      git -C "$worktree" checkout --quiet --orphan "$SHOPSERVATION_PAGES"
      git -C "$worktree" rm -rq --cached . 2>/dev/null || true
      find "$worktree" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
    fi
  fi

  rm -rf "$worktree/repo"
  mkdir -p "$worktree/repo"
  cp -r fdroid/repo/. "$worktree/repo/"
  touch "$worktree/.nojekyll"
  git -C "$worktree" add -A

  if git -C "$worktree" diff --cached --quiet; then
    echo "    no changes to publish"
  else
    echo "    staged in $worktree/ — commit and push it yourself:"
    echo "        git -C $worktree commit -m 'repo: shopservation $(cat app/build.gradle.kts | sed -n 's/.*versionName = "\(.*\)".*/\1/p')'"
    echo "        git -C $worktree push origin $SHOPSERVATION_PAGES"
  fi
elif [[ -n ${SHOPSERVATION_DEPLOY:-} ]]; then
  echo "==> deploying to $SHOPSERVATION_DEPLOY"
  rsync -rlt --delete --chmod=D755,F644 fdroid/repo/ "$SHOPSERVATION_DEPLOY/"
  echo "    done"
else
  echo "Nothing deployed. Choose one:"
  echo "  SHOPSERVATION_DOCS=docs        copy into docs/ on the current branch (simplest)"
  echo "  SHOPSERVATION_PAGES=gh-pages   stage into a separate gh-pages worktree"
  echo "  SHOPSERVATION_DEPLOY=u@h:/path rsync fdroid/repo/ to your own web root"
fi
