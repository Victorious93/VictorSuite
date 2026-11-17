#!/usr/bin/env bash
# Regression tests for pc/victor-pc-setup.sh. No network, no sudo, no real scrcpy:
# the release flow is exercised against a local file:// fixture with a throwaway checksum.
# shellcheck disable=SC2015,SC2016,SC2034 # conditions are evaluated from strings; RC/OUT are read by them
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="$ROOT/pc/victor-pc-setup.sh"
WORK="$(mktemp -d)"
trap 'rm -rf -- "$WORK"' EXIT
fail=0
pass() { echo "ok:   $1"; }
bad()  { echo "FAIL: $1"; fail=1; }
check() { # description, condition (evaluated by the shell)
    if eval "$2"; then pass "$1"; else bad "$1"; fi
}

# --- fixtures -------------------------------------------------------------------------------
mkdir -p "$WORK/shims"
for tool in sudo apt-get curl gpg; do
    cat >"$WORK/shims/$tool" <<EOF
#!/usr/bin/env bash
echo "$tool \$*" >>"$WORK/shim.log"
exit 0
EOF
    chmod +x "$WORK/shims/$tool"
done

printf 'ID=ubuntu\nVERSION_ID="24.04"\n' >"$WORK/os-ubuntu2404"
printf 'ID=ubuntu\nVERSION_ID="22.04"\n' >"$WORK/os-ubuntu2204"
printf 'ID=debian\nVERSION_ID="12"\n'    >"$WORK/os-debian"

FIX_VER="9.9.9"
FIX_DIR="$WORK/release"
mkdir -p "$FIX_DIR/build/scrcpy-linux-x86_64-v$FIX_VER"
printf '#!/bin/sh\necho "scrcpy %s fixture"\n' "$FIX_VER" >"$FIX_DIR/build/scrcpy-linux-x86_64-v$FIX_VER/scrcpy"
printf '#!/bin/sh\necho "adb fixture"\n' >"$FIX_DIR/build/scrcpy-linux-x86_64-v$FIX_VER/adb"
chmod +x "$FIX_DIR/build/scrcpy-linux-x86_64-v$FIX_VER/"*
tar -czf "$FIX_DIR/scrcpy-linux-x86_64-v$FIX_VER.tar.gz" -C "$FIX_DIR/build" "scrcpy-linux-x86_64-v$FIX_VER"
FIX_SHA="$(sha256sum "$FIX_DIR/scrcpy-linux-x86_64-v$FIX_VER.tar.gz" | awk '{print $1}')"

# run NAME ARGS... : runs the script in an isolated HOME; sets OUT and RC; HOME is $WORK/home-NAME
run() {
    local name="$1"; shift
    export T_HOME="$WORK/home-$name"
    mkdir -p "$T_HOME"
    : >"$WORK/shim.log"
    OUT="$(env HOME="$T_HOME" VICTOR_PC_TESTING=1 VICTOR_PC_OS_RELEASE="${T_OS:-$WORK/os-ubuntu2404}" \
        PATH="$PATH_FOR_RUN" "$@" 2>&1)"
    RC=$?
}
PATH_FOR_RUN="$WORK/shims:$PATH"

# --- default run installs nothing ------------------------------------------------------------
run default bash "$SCRIPT"
check "default run exits 0" '[ "$RC" -eq 0 ]'
check "default run says scrcpy is optional/off" 'grep -q "off by default" <<<"$OUT"'
check "default run touches no installer tool" '[ ! -s "$WORK/shim.log" ]'
check "default run writes nothing into HOME" '[ -z "$(ls -A "$T_HOME")" ]'

run default_status bash "$SCRIPT" --status
check "--status exits 0 and installs nothing" '[ "$RC" -eq 0 ] && [ -z "$(ls -A "$T_HOME")" ] && [ ! -s "$WORK/shim.log" ]'

# --- dry run ---------------------------------------------------------------------------------
run dry bash "$SCRIPT" --scrcpy --dry-run
check "--scrcpy --dry-run exits 0" '[ "$RC" -eq 0 ]'
check "dry run prints the pinned checksum" 'grep -q "SHA-256 9f969d30cc574816077edecda65719c1c70b0aee1df1ba5fa00779ac07b8fd6e" <<<"$OUT"'
check "dry run downloads and installs nothing" '[ ! -s "$WORK/shim.log" ] && [ -z "$(ls -A "$T_HOME")" ]'

run dry_apt bash "$SCRIPT" --scrcpy --scrcpy-method apt --dry-run
check "apt dry run lists the package install" 'grep -q "\[dry-run\] sudo apt-get install -y scrcpy adb" <<<"$OUT"'
check "apt dry run never calls sudo/apt for real" '[ ! -s "$WORK/shim.log" ]'

# --- argument and OS handling ----------------------------------------------------------------
run badopt bash "$SCRIPT" --nope
check "unknown option fails" '[ "$RC" -ne 0 ]'
run badmethod bash "$SCRIPT" --scrcpy --scrcpy-method snap --dry-run
check "unknown method fails" '[ "$RC" -ne 0 ]'

T_OS="$WORK/os-debian" run debian bash "$SCRIPT" --scrcpy --dry-run
check "unsupported OS is refused" '[ "$RC" -ne 0 ] && grep -q "unsupported OS" <<<"$OUT"'
T_OS="$WORK/os-debian" run debian_force bash "$SCRIPT" --scrcpy --dry-run --force
check "--force overrides the OS check" '[ "$RC" -eq 0 ]'
T_OS="$WORK/os-ubuntu2204" run u2204 bash "$SCRIPT" --dry-run
check "22.04 is accepted with a best-effort warning" '[ "$RC" -eq 0 ] && grep -q "best effort" <<<"$OUT"'

run arch env VICTOR_PC_ARCH=aarch64 bash "$SCRIPT" --scrcpy
check "release method refuses non-x86_64" '[ "$RC" -ne 0 ] && grep -q "x86_64 only" <<<"$OUT"'

# --- release flow against the local fixture --------------------------------------------------
FIXTURE_ENV=(VICTOR_PC_SCRCPY_VERSION="$FIX_VER" VICTOR_PC_SCRCPY_SHA256="$FIX_SHA" VICTOR_PC_SCRCPY_BASE_URL="file://$FIX_DIR")
PATH_FOR_RUN="$(dirname "$(command -v curl)"):$(dirname "$(command -v tar)"):/usr/bin:/bin"

run install env "${FIXTURE_ENV[@]}" bash "$SCRIPT" --scrcpy
check "fixture install exits 0" '[ "$RC" -eq 0 ]'
check "scrcpy is linked into ~/.local/bin" '[ -L "$T_HOME/.local/bin/scrcpy" ]'
check "linked scrcpy runs" '[ "$("$T_HOME/.local/bin/scrcpy")" = "scrcpy $FIX_VER fixture" ]'
check "bundled adb is linked when the PC has none" '[ -L "$T_HOME/.local/bin/adb" ]'

export T_HOME="$WORK/home-install"
OUT="$(env HOME="$T_HOME" VICTOR_PC_TESTING=1 VICTOR_PC_OS_RELEASE="$WORK/os-ubuntu2404" PATH="$PATH_FOR_RUN" \
    "${FIXTURE_ENV[@]}" bash "$SCRIPT" --scrcpy 2>&1)"; RC=$?
check "second run is idempotent" '[ "$RC" -eq 0 ] && grep -q "already installed" <<<"$OUT"'

run tamper env VICTOR_PC_SCRCPY_VERSION="$FIX_VER" \
    VICTOR_PC_SCRCPY_SHA256="0000000000000000000000000000000000000000000000000000000000000000" \
    VICTOR_PC_SCRCPY_BASE_URL="file://$FIX_DIR" bash "$SCRIPT" --scrcpy
check "checksum mismatch aborts" '[ "$RC" -ne 0 ] && grep -q "SHA-256 mismatch" <<<"$OUT"'
check "checksum mismatch installs nothing" '[ ! -e "$T_HOME/.local/bin/scrcpy" ] && [ ! -e "$T_HOME/.local/opt" ]'

# An existing scrcpy that this script did not create is never replaced without --force.
export T_HOME="$WORK/home-foreign"
mkdir -p "$T_HOME/.local/bin"
printf '#!/bin/sh\necho mine\n' >"$T_HOME/.local/bin/scrcpy"; chmod +x "$T_HOME/.local/bin/scrcpy"
OUT="$(env HOME="$T_HOME" VICTOR_PC_TESTING=1 VICTOR_PC_OS_RELEASE="$WORK/os-ubuntu2404" PATH="$PATH_FOR_RUN" \
    "${FIXTURE_ENV[@]}" bash "$SCRIPT" --scrcpy 2>&1)"; RC=$?
check "foreign scrcpy is not clobbered" '[ "$RC" -ne 0 ] && [ "$("$T_HOME/.local/bin/scrcpy")" = "mine" ]'

# --- guard rails in the script itself --------------------------------------------------------
check "test hooks are gated behind VICTOR_PC_TESTING" 'grep -q '\''VICTOR_PC_TESTING:-0}" = "1"'\'' "$SCRIPT"'
check "pinned SHA-256 is 64 hex chars" 'grep -Eq '\''^SCRCPY_SHA256="[0-9a-f]{64}"$'\'' "$SCRIPT"'
check "script carries no secrets" '! grep -Eiq "(api[_-]?key|password)=" "$SCRIPT"'
check "download only over https in production" 'grep -q "SCRCPY_BASE_URL=\"https://github.com/Genymobile/scrcpy/releases/download" "$SCRIPT"'

if [ "$fail" -ne 0 ]; then echo "SOME TESTS FAILED"; exit 1; fi
echo "all pc setup tests passed"
