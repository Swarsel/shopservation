resolve_keystore_pass() {
  if [[ -n ${SHOPSERVATION_KEYSTORE_PASS_FILE:-} ]]; then
    if [[ ! -r $SHOPSERVATION_KEYSTORE_PASS_FILE ]]; then
      echo "error: cannot read SHOPSERVATION_KEYSTORE_PASS_FILE=$SHOPSERVATION_KEYSTORE_PASS_FILE" >&2
      return 1
    fi
    IFS= read -r SHOPSERVATION_KEYSTORE_PASS < "$SHOPSERVATION_KEYSTORE_PASS_FILE" || true
    SHOPSERVATION_KEYSTORE_PASS=${SHOPSERVATION_KEYSTORE_PASS%$'\r'}
    SHOPSERVATION_KEYSTORE_PASS=${SHOPSERVATION_KEYSTORE_PASS%%[[:space:]]}
  elif [[ -z ${SHOPSERVATION_KEYSTORE_PASS:-} ]]; then
    if [[ ! -t 0 ]]; then
      echo "error: no password available. Set SHOPSERVATION_KEYSTORE_PASS_FILE, or run interactively." >&2
      return 1
    fi
    read -r -s -p "keystore password: " SHOPSERVATION_KEYSTORE_PASS
    echo >&2
  fi

  if [[ ${#SHOPSERVATION_KEYSTORE_PASS} -lt 6 ]]; then
    echo "error: keystore password must be at least 6 characters (keytool requirement)" >&2
    return 1
  fi
  export SHOPSERVATION_KEYSTORE_PASS
}

check_keystore_pass() {
  local ks=$1
  [[ -f $ks ]] || return 0
  if ! keytool -list -keystore "$ks" -storepass "$SHOPSERVATION_KEYSTORE_PASS" >/dev/null 2>&1; then
    echo "error: the keystore password does not open $ks" >&2
    if [[ -n ${SHOPSERVATION_KEYSTORE_PASS_FILE:-} ]]; then
      echo "       read from $SHOPSERVATION_KEYSTORE_PASS_FILE (first line only, trailing whitespace and CR stripped)" >&2
      echo "       check for stray characters: cat -A '$SHOPSERVATION_KEYSTORE_PASS_FILE'" >&2
    fi
    echo "       if you rotated or lost the password, delete keys/ and fdroid/keystore.p12 and re-run init-keys" >&2
    return 1
  fi
}
