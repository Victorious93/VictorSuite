#!/usr/bin/env bash
# Verifies a built APK matches the source: applicationId, versionName/versionCode from
# app/build.gradle, targetSdk 28 (intentional, see CLAUDE.md), and (for per-ABI APKs) the native libs.
# Prints the signing certificate SHA-256 so it can be compared with the published one.
# Usage: verify_apk.sh <apk> [expected-abi]   Requires ANDROID_HOME with build-tools.
set -euo pipefail
apk="$1"; abi="${2:-}"
bt=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
want_name=$(grep -oP '^\s*versionName "\K[^"]+' app/build.gradle | head -1)
want_code=$(grep -oP '^\s*versionCode \K[0-9]+' app/build.gradle | head -1)
badging=$("$bt/aapt2" dump badging "$apk")
pkgline=$(grep -m1 '^package:' <<<"$badging")
get() { grep -oP "(?<=[ :])$1='\K[^']*" <<<"$pkgline" | head -1; }
pkg=$(get name); code=$(get versionCode); name=$(get versionName)
tsdk=$(sed -n "s/^targetSdkVersion:'\([0-9]*\)'.*/\1/p" <<<"$badging")
fail=0
chk() { if [ "$2" != "$3" ]; then echo "FAIL $1: got '$2' want '$3'"; fail=1; else echo "ok   $1 = $2"; fi; }
chk applicationId "$pkg" com.termux
chk versionName "$name" "$want_name"
chk versionCode "$code" "$want_code"
chk targetSdk "$tsdk" 28
entries=$(unzip -Z -1 "$apk")
if [ -n "$abi" ]; then
  abis=("$abi")
else
  # A universal APK must be usable on every supported architecture.
  abis=(arm64-v8a armeabi-v7a x86 x86_64)
fi
for expected_abi in "${abis[@]}"; do
  for library in libtermux.so libtermux-bootstrap.so liblocal-socket.so libtermux-api-helper.so; do
    entry="lib/$expected_abi/$library"
    if grep -Fxq "$entry" <<<"$entries"; then
      echo "ok   $entry"
    else
      echo "FAIL: required native library missing: $entry"; fail=1
    fi
  done
done
if "$bt/apksigner" verify --print-certs "$apk" > /tmp/apksigner.out 2>&1; then
  grep "certificate SHA-256" /tmp/apksigner.out | head -1
else
  echo "note: $(head -1 /tmp/apksigner.out)"; echo "(unsigned/debug-signed APK has no verifiable release cert)"
fi
exit $fail
