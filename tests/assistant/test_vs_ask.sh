#!/usr/bin/env bash
# Regression tests for the ?? bashrc hook and vs-ask helper. No network, no device.
# shellcheck disable=SC2016,SC2034 # check() evals strings that read OUT/RC/OUT_NI
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HOOK="$ROOT/app/src/main/assets/assistant/bashrc-hook.sh"
HELPER="$ROOT/app/src/main/assets/runcommand/vs-ask"
INTENTS="$ROOT/app/src/main/assets/assistant/intents.json"
WORK="$(mktemp -d)"
trap 'rm -rf -- "$WORK"' EXIT
fail=0
pass() { echo "ok:   $1"; }
bad()  { echo "FAIL: $1  (${OUT:-})"; fail=1; }
check() { if eval "$2"; then pass "$1"; else bad "$1"; fi; }

# --- glob safety of the bashrc hook ----------------------------------------------------------
# Create 2-character filenames that a bare ?? would otherwise expand to.
GLOBDIR="$WORK/glob"
mkdir -p "$GLOBDIR"
touch "$GLOBDIR/ab" "$GLOBDIR/cd" "$GLOBDIR/xy"
mkdir -p "$WORK/prefix/libexec/victorsuite"
cat >"$WORK/prefix/libexec/victorsuite/vs-ask" <<'EOT'
#!/bin/sh
printf 'ARGS'
for a in "$@"; do printf ' [%s]' "$a"; done
printf '\n'
EOT
chmod +x "$WORK/prefix/libexec/victorsuite/vs-ask"

# Interactive bash reads commands from stdin so aliases are expanded the same
# way they are in a real terminal. --norc avoids the tester's own rc files.
OUT="$(
    cd "$GLOBDIR" || exit 1
    export PREFIX="$WORK/prefix"
    export PS1='$ '
    printf '%s\n' \
        "source \"$HOOK\"" \
        "??" \
        "?? cpu temp" \
        "?? what is my ip?" \
        "source \"$HOOK\"" \
        "?? again" \
        | bash --norc -i 2>/dev/null
)"

check "bare ?? does not expand to 2-char filenames" '! grep -q "\[ab\]" <<<"$OUT" && ! grep -q "\[cd\]" <<<"$OUT" && ! grep -q "\[xy\]" <<<"$OUT"'
check "bare ?? calls vs-ask with no arguments" 'grep -q "^ARGS\$" <<<"$OUT"'
check "?? cpu temp passes two words, not files" 'grep -q "ARGS \[cpu\] \[temp\]" <<<"$OUT"'
check "?? what is my ip? keeps the question mark" 'grep -q "ARGS \[what\] \[is\] \[my\] \[ip?\]" <<<"$OUT"'
check "re-sourcing the hook still works" 'grep -q "ARGS \[again\]" <<<"$OUT"'

# Non-interactive shells must not define the alias (check $-). Use an empty dir
# so a typo cannot be hidden by globbing.
EMPTY="$WORK/empty"
mkdir -p "$EMPTY"
OUT_NI="$(cd "$EMPTY" && PREFIX="$WORK/prefix" bash --norc -c "source \"$HOOK\"; alias '??' 2>&1" || true)"
check "non-interactive bash does not define ??" '! grep -q "__vs_ask" <<<"$OUT_NI"'

# --- vs-ask disable flag, preview, dry-run ---------------------------------------------------
TABLE="$WORK/table"
mkdir -p "$TABLE"
python3 - "$INTENTS" "$TABLE/intents.idx" <<'PY'
import json, sys
doc = json.load(open(sys.argv[1]))
out = open(sys.argv[2], "w")
for i in doc["intents"]:
    keys = "|".join(i.get("keywords", []) + i.get("aliases", []))
    def flat(s):
        return (s or "").replace("\t", " ").replace("\n", " ")
    print("\t".join([
        i["id"], i.get("title",""), keys,
        flat(i.get("command_user")), flat(i.get("command_root")),
        flat(i.get("explain")), flat(i.get("android_note")),
    ]), file=out)
PY
cp "$INTENTS" "$TABLE/intents.json"

run_ask() {
    # run_ask NAME [VAR=value ...] -- query words
    shift
    envvars=()
    while [ "$#" -gt 0 ] && [ "$1" != "--" ]; do
        envvars+=("$1")
        shift
    done
    if [ "${1-}" = "--" ]; then
        shift
    fi
    set +e
    OUT="$(env PREFIX="$WORK/prefix" VS_ASK_TABLE_DIR="$TABLE" VS_ASK_SKIP_APP=1 \
        VS_ASK_DISABLED="$TABLE/disabled" VS_ASK_IDX="$TABLE/intents.idx" \
        "${envvars[@]}" \
        sh "$HELPER" "$@" 2>&1)"
    RC=$?
    set -e
}

# The rest of this script uses `check` only; keep set +e after the first helper call pattern.
set +e

touch "$TABLE/disabled"
run_ask off -- cpu temp
check "disabled flag makes vs-ask refuse" '[ "$RC" -ne 0 ] && grep -q "turned off" <<<"$OUT"'
rm -f "$TABLE/disabled"

run_ask help -- --help
check "--help exits 0 and mentions ??" '[ "$RC" -eq 0 ] && grep -q "cpu temp" <<<"$OUT"'

run_ask bare
check "bare vs-ask (no query) exits non-zero" '[ "$RC" -ne 0 ]'

run_ask cpu VS_ASK_ROOT=0 VS_ASK_CONFIRM=auto-yes VS_ASK_DRY_RUN=1 -- cpu temp
check "cpu temp proposes dumpsys thermalservice" '[ "$RC" -eq 0 ] && grep -q "dumpsys thermalservice" <<<"$OUT"'
check "cpu temp dry-run does not claim to have failed" 'grep -q "Would run: dumpsys thermalservice" <<<"$OUT"'

run_ask unknown VS_ASK_ROOT=0 VS_ASK_CONFIRM=auto-yes -- make me a sandwich
check "unknown query is not a runnable command" '[ "$RC" -ne 0 ] && grep -q "I don'\''t know" <<<"$OUT" && ! grep -q "Would run:" <<<"$OUT"'

run_ask magisk VS_ASK_ROOT=0 VS_ASK_CONFIRM=auto-yes VS_ASK_DRY_RUN=1 -- magisk modules
check "magisk modules without root is not run" '[ "$RC" -eq 2 ] && grep -q "needs root" <<<"$OUT" && ! grep -q "Would run:" <<<"$OUT"'

run_ask magisk_root VS_ASK_ROOT=1 VS_ASK_CONFIRM=auto-no VS_ASK_DRY_RUN=1 -- magisk modules
check "magisk modules with root still requires confirm (su)" '[ "$RC" -ne 0 ] && grep -q "su -c" <<<"$OUT" && grep -q "Cancelled" <<<"$OUT"'

run_ask magisk_yes VS_ASK_ROOT=1 VS_ASK_CONFIRM=auto-yes VS_ASK_DRY_RUN=1 -- magisk modules
check "magisk modules with root + confirm dry-runs su -c" '[ "$RC" -eq 0 ] && grep -q "Would run: su -c" <<<"$OUT"'

run_ask kernel VS_ASK_ROOT=0 VS_ASK_CONFIRM=auto-yes VS_ASK_DRY_RUN=1 -- kernel version
check "kernel version matches uname" '[ "$RC" -eq 0 ] && grep -q "uname -a" <<<"$OUT"'

run_ask android VS_ASK_ROOT=0 VS_ASK_CONFIRM=auto-yes VS_ASK_DRY_RUN=1 -- android version
check "android version matches getprop" '[ "$RC" -eq 0 ] && grep -q "getprop ro.build.version.release" <<<"$OUT"'

run_ask ip VS_ASK_ROOT=0 VS_ASK_CONFIRM=auto-yes VS_ASK_DRY_RUN=1 -- ip address
check "ip address matches ip or ifconfig" '[ "$RC" -eq 0 ] && grep -Eq "ip -o -4 addr|ifconfig" <<<"$OUT"'

if [ "$fail" -ne 0 ]; then
    exit 1
fi
echo "All vs-ask / bashrc hook tests passed."
exit 0
