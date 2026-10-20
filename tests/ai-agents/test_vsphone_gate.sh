#!/usr/bin/env bash
# Tests for vsphone's classifier and approval gate. Runs anywhere (no device needed).
# shellcheck disable=SC2015 # "A && B || fail" is the intended assertion form here
set -u
VS="$(cd "$(dirname "$0")/../.." && pwd)/scripts/vsphone"
export HOME; HOME="$(mktemp -d)"
fail=0
expect() { # SAFE|RISKY args...
    local want="$1"; shift
    local got; got="$("$VS" classify "$@")"
    if [ "${got%% *}" != "$want" ]; then echo "FAIL: classify $* -> $got (want $want)"; fail=1; fi
}
expect SAFE  pm list packages
expect SAFE  pm path com.example
expect SAFE  pm install /sdcard/x.apk
expect RISKY pm install -r -d /sdcard/x.apk
expect RISKY pm uninstall com.android.systemui
expect RISKY pm uninstall --user 0 com.google.android.gms
expect RISKY pm clear com.android.settings
expect RISKY pm disable-user com.android.phone
expect RISKY pm frobnicate
expect SAFE  am start -n com.example/.Main
expect SAFE  am force-stop com.example
expect RISKY am broadcast -a android.intent.action.MASTER_CLEAR
expect RISKY am broadcast -a android.intent.action.FACTORY_RESET
expect SAFE  settings get global adb_enabled
expect SAFE  settings put system screen_brightness 100
expect RISKY settings put global adb_enabled 1
expect RISKY settings put global package_verifier_enable 0
expect RISKY settings put secure enabled_accessibility_services x/y
expect RISKY settings put secure lockscreen.disabled 1
expect RISKY settings delete global verifier_verify_adb_installs
expect RISKY settings reset global
expect SAFE  input tap 100 200
expect SAFE  input text hello
expect SAFE  input keyevent 3
expect SAFE  screencap
expect SAFE  uidump
expect SAFE  pull /sdcard/a.txt
expect SAFE  push a.txt /sdcard/Download/a.txt
expect RISKY push a.txt /sdcard/../data/system/x
expect RISKY push a.txt /data/system/locksettings.db
expect RISKY shell ls
expect RISKY shell dd if=/dev/zero of=/dev/block/by-name/boot
expect RISKY wipe data
expect RISKY flash boot.img
expect RISKY dd if=x of=/dev/block/sda
expect RISKY factory-reset
expect RISKY reboot recovery
expect RISKY something-unknown

# Gate: risky must be refused non-interactively, with remote env, and over SSH; never runs.
out="$(VSPHONE_BACKEND=root "$VS" pm uninstall com.android.systemui </dev/null 2>&1)"; rc=$?
[ "$rc" = 3 ] && grep -q REFUSED <<<"$out" || { echo "FAIL: non-interactive risky not refused (rc=$rc): $out"; fail=1; }
out="$(VS_REMOTE_TRIGGER=1 VSPHONE_BACKEND=root "$VS" shell id </dev/null 2>&1)"; rc=$?
[ "$rc" = 3 ] || { echo "FAIL: remote trigger not refused"; fail=1; }
out="$(SSH_CONNECTION="1 2 3 4" VSPHONE_BACKEND=root "$VS" wipe data </dev/null 2>&1)"; rc=$?
[ "$rc" = 3 ] || { echo "FAIL: ssh not refused"; fail=1; }
# Safe action in dry-run goes through without a prompt.
out="$(VSPHONE_BACKEND=root "$VS" --dry-run input tap 1 2 </dev/null 2>&1)"; rc=$?
[ "$rc" = 0 ] && grep -q "dry-run:root" <<<"$out" || { echo "FAIL: safe dry-run: $out"; fail=1; }
# Audit log: exists, mode 600, redacts input text and settings values.
VSPHONE_BACKEND=root "$VS" --dry-run input text 'supersecretpw' </dev/null >/dev/null 2>&1
VSPHONE_BACKEND=root "$VS" --dry-run settings put system screen_brightness 77 </dev/null >/dev/null 2>&1
log="$HOME/.local/state/victorsuite/vsphone-audit.log"
[ "$(stat -c %a "$log")" = 600 ] || { echo "FAIL: audit log mode"; fail=1; }
grep -q supersecretpw "$log" && { echo "FAIL: input text leaked to audit log"; fail=1; }
grep -qw 77 "$log" && { echo "FAIL: settings value leaked to audit log"; fail=1; }
grep -q "refused" "$log" || { echo "FAIL: refusals not audited"; fail=1; }
# Gate cannot be auto-approved by piping the confirmation word into stdin.
out="$(printf 'CONFIRM-0000\n' | VSPHONE_BACKEND=root "$VS" pm clear com.android.settings 2>&1)"; rc=$?
[ "$rc" = 3 ] || { echo "FAIL: piped confirmation accepted"; fail=1; }

[ "$fail" = 0 ] && echo "vsphone gate tests: all passed"
exit "$fail"
