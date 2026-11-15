#!/usr/bin/env bash
# Regression tests for scripts/bridge-rish-doctor using a fake `rish`. No device needed.
set -u
DOC="$(cd "$(dirname "$0")/../.." && pwd)/scripts/bridge-rish-doctor"
root="$(mktemp -d)"; trap 'rm -rf -- "$root"' EXIT
fail=0
t() { echo "ok - $1"; }
f() { echo "FAIL - $1"; fail=1; }

mk() { # name stdout stderr rc
    local d="$root/$1"; mkdir -p "$d/bridge" "$d/data/data/com.termux/files/usr"
    printf 'dex' > "$d/bridge/rish_shizuku.dex"; chmod 600 "$d/bridge/rish_shizuku.dex"
    cat > "$d/bridge/rish" <<EOS
#!/bin/sh
[ -n "\$RISH_APPLICATION_ID" ] || { echo "RISH_APPLICATION_ID is not set" >&2; exit 1; }
printf '%s' "$3" >&2
printf '%s' "$2"
exit $4
EOS
}
run() { # name -> output, sets rc
    out="$(HOME="$root/$1" PREFIX="/data/data/com.termux/files/usr" "$DOC" --dir "$root/$1/bridge" --timeout 5 2>&1)"; rc=$?
}

mk adb '2000' 'rish: commands run as ADB (shell) through Victorious Bridge; com.termux has legacy access.' 0
run adb
[ $rc -eq 0 ] && echo "$out" | grep -q "privilege in use: ADB" && t "adb mode detected" || { f "adb: $out"; }
echo "$out" | grep -q "terminal app: com.termux" && t "package derived from PREFIX, not hard-coded" || f "pkg: $out"
[ "$(stat -c %a "$root/adb/bridge/rish_shizuku.dex")" = 400 ] && t "dex made read-only (mode 400)" || f "dex mode not 400"
echo "$out" | grep -q "through Victorious Bridge" && t "bridge privilege line surfaced" || f "banner missing"

mk root '0' '' 0; run root
[ $rc -eq 0 ] && echo "$out" | grep -q "privilege in use: root" && t "root mode detected" || f "root: $out"

mk denied '' 'Permission denied' 1; run denied
[ $rc -eq 1 ] && echo "$out" | grep -q "Legacy full access" && echo "$out" | grep -q "ordinary app" && t "not approved -> explains approval, reports ordinary app" || f "denied: $out"

mk timeout '' 'Request timeout. The connection may be blocked' 1; run timeout
echo "$out" | grep -q "battery optimisation" && t "timeout -> battery hint" || f "timeout: $out"

mk down '' 'Server is not running' 1; run down
echo "$out" | grep -q "service is not running" && t "service down -> start hint" || f "down: $out"

mkdir -p "$root/nofiles/bridge"; run nofiles
[ $rc -eq 1 ] && echo "$out" | grep -q "rish not found" && t "missing rish reported" || f "nofiles: $out"

mk mismatch '2000' '' 0
out="$(RISH_APPLICATION_ID=com.other HOME="$root/mismatch" PREFIX="/data/data/com.termux/files/usr" "$DOC" --dir "$root/mismatch/bridge" 2>&1)"; rc=$?
[ $rc -eq 1 ] && echo "$out" | grep -q "RISH_APPLICATION_ID is 'com.other'" && t "identity mismatch refused" || f "mismatch: $out"

exit $fail
