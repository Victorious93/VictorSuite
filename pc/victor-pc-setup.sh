#!/usr/bin/env bash
# victor-pc-setup.sh - VictorSuite helper for the Ubuntu PC side of "My PC".
#
# Scope of this script (deliberately small):
#   * preflight (Ubuntu 24.04 primary, 22.04 best effort)
#   * --status    : report what is installed
#   * --scrcpy    : OPTIONAL, OFF BY DEFAULT. Install Genymobile/scrcpy so this PC can
#                   mirror and control the phone over ADB on Wi-Fi or Tailscale.
#
# A run without --scrcpy installs nothing. SSH hardening, Tailscale, pairing, Waydroid and
# uninstall belong to the wider Remote PC plan (docs/REMOTE_PC_PLAN.md section 6) and are not
# implemented here.
#
# scrcpy is (c) Genymobile, Apache-2.0: https://github.com/Genymobile/scrcpy
# This script does not bundle scrcpy; it downloads the official release on the PC when asked.

# shellcheck disable=SC1090  # os-release path is configurable for tests
set -euo pipefail

SCRIPT_NAME="victor-pc-setup.sh"

# Pinned official release. SHA-256 is the value Genymobile publishes in doc/linux.md at tag v5.0.1
# and in the release's SHA256SUMS.txt (signed with the maintainer's GPG key).
SCRCPY_VERSION="5.0.1"
SCRCPY_SHA256="9f969d30cc574816077edecda65719c1c70b0aee1df1ba5fa00779ac07b8fd6e"
SCRCPY_BASE_URL="https://github.com/Genymobile/scrcpy/releases/download/v${SCRCPY_VERSION}"
# Romain Vimont (Genymobile) primary key fingerprint, from doc/verify-release.md.
SCRCPY_SIGNER_FPR="456958E85A185DD5C2D1E4E80C822B298461FA03"
SCRCPY_KEY_URL="https://blog.rom1v.com/keys/rom1v.asc"

# Test hooks. Ignored unless VICTOR_PC_TESTING=1 so a stray variable cannot redirect a real install.
if [ "${VICTOR_PC_TESTING:-0}" = "1" ]; then
    SCRCPY_VERSION="${VICTOR_PC_SCRCPY_VERSION:-$SCRCPY_VERSION}"
    SCRCPY_SHA256="${VICTOR_PC_SCRCPY_SHA256:-$SCRCPY_SHA256}"
    SCRCPY_BASE_URL="${VICTOR_PC_SCRCPY_BASE_URL:-$SCRCPY_BASE_URL}"
    OS_RELEASE_FILE="${VICTOR_PC_OS_RELEASE:-/etc/os-release}"
    ARCH_OVERRIDE="${VICTOR_PC_ARCH:-}"
else
    OS_RELEASE_FILE="/etc/os-release"
    ARCH_OVERRIDE=""
fi

DRY_RUN=0
WANT_SCRCPY=0
SCRCPY_METHOD="release"
FORCE=0
WANT_STATUS=0
TMP_DIR=""

log()  { printf '%s\n' "$*"; }
warn() { printf 'WARNING: %s\n' "$*" >&2; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
    cat <<EOF
Usage: $SCRIPT_NAME [options]

Ubuntu PC helper for VictorSuite "My PC". Without options it only runs preflight checks and
installs nothing.

Options:
  --scrcpy                 Install scrcpy so this PC can mirror and control the phone.
                           OFF by default; nothing is installed unless you pass this.
  --scrcpy-method METHOD   release (default) or apt.
                             release  Official Genymobile static build v${SCRCPY_VERSION}, installed under
                                      ~/.local (no root). SHA-256 is checked against a pinned value
                                      and the GPG signature is checked when gpg is available.
                             apt      Ubuntu package (needs sudo). Genymobile marks the distro
                                      package obsolete; it may not work with recent Android.
  --status                 Show whether scrcpy and adb are installed, then exit.
  --dry-run                Print what would be done; change nothing, download nothing.
  --force                  Continue on an unsupported OS (anything but Ubuntu 22.04 / 24.04).
  -h, --help               Show this help.

Security: wireless ADB plus scrcpy gives this PC screen access and input control of the phone.
Use it only on a trusted LAN or Tailscale. Never expose ADB to the internet.
scrcpy is Apache-2.0, (c) Genymobile: https://github.com/Genymobile/scrcpy
EOF
}

run() {
    if [ "$DRY_RUN" = "1" ]; then
        printf '[dry-run] %s\n' "$*"
        return 0
    fi
    "$@"
}

parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --scrcpy) WANT_SCRCPY=1 ;;
            --scrcpy-method)
                [ $# -ge 2 ] || die "--scrcpy-method needs a value (release or apt)"
                SCRCPY_METHOD="$2"
                shift
                ;;
            --scrcpy-method=*) SCRCPY_METHOD="${1#*=}" ;;
            --status) WANT_STATUS=1 ;;
            --dry-run) DRY_RUN=1 ;;
            --force) FORCE=1 ;;
            -h|--help) usage; exit 0 ;;
            *) usage >&2; die "unknown option: $1" ;;
        esac
        shift
    done
    case "$SCRCPY_METHOD" in
        release|apt) ;;
        *) die "--scrcpy-method must be 'release' or 'apt', got '$SCRCPY_METHOD'" ;;
    esac
}

# Prints "<id>|<version_id>" from the os-release file.
read_os() {
    local id="" version=""
    if [ -r "$OS_RELEASE_FILE" ]; then
        id="$(. "$OS_RELEASE_FILE" >/dev/null 2>&1; printf '%s' "${ID:-}")"
        version="$(. "$OS_RELEASE_FILE" >/dev/null 2>&1; printf '%s' "${VERSION_ID:-}")"
    fi
    printf '%s|%s\n' "$id" "$version"
}

preflight() {
    local os id version
    os="$(read_os)"
    id="${os%%|*}"
    version="${os#*|}"
    if [ "$id" = "ubuntu" ] && [ "$version" = "24.04" ]; then
        log "OS: Ubuntu 24.04 (primary target)"
    elif [ "$id" = "ubuntu" ] && [ "$version" = "22.04" ]; then
        log "OS: Ubuntu 22.04 (best effort)"
        warn "Ubuntu 22.04 is best effort; 24.04 is the tested target."
    elif [ "$FORCE" = "1" ]; then
        warn "unsupported OS '${id:-unknown} ${version:-?}', continuing because of --force."
    else
        die "unsupported OS '${id:-unknown} ${version:-?}'. Ubuntu 24.04 (primary) or 22.04 is required; use --force to override."
    fi
    if [ "${EUID:-$(id -u)}" = "0" ] && [ "$DRY_RUN" != "1" ]; then
        die "run this script as your normal user, not root; it uses sudo only where needed."
    fi
}

machine_arch() {
    if [ -n "$ARCH_OVERRIDE" ]; then
        printf '%s' "$ARCH_OVERRIDE"
    else
        uname -m
    fi
}

install_prefix()  { printf '%s' "${VICTOR_PC_PREFIX:-$HOME/.local}"; }
scrcpy_opt_dir()  { printf '%s/opt/scrcpy-v%s' "$(install_prefix)" "$SCRCPY_VERSION"; }
scrcpy_link()     { printf '%s/bin/scrcpy' "$(install_prefix)"; }
adb_link()        { printf '%s/bin/adb' "$(install_prefix)"; }

# True when $1 >= $2 as dotted versions.
version_ge() {
    [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | head -n1)" = "$2" ]
}

sha256_of() {
    sha256sum "$1" | awk '{print $1}'
}

verify_sha256() { # file expected
    local got
    got="$(sha256_of "$1")"
    if [ "$got" != "$2" ]; then
        warn "SHA-256 mismatch for $1"
        warn "  expected $2"
        warn "  got      $got"
        return 1
    fi
}

# Checks SHA256SUMS.txt.asc with a throwaway keyring. A bad or foreign signature is a hard
# failure. If gpg or the key is unavailable the pinned SHA-256 above is still enforced.
verify_gpg_signature() { # dir
    local dir="$1" gnupg status fpr
    if ! command -v gpg >/dev/null 2>&1; then
        warn "gpg not installed; skipping signature check (pinned SHA-256 still enforced)."
        return 0
    fi
    if [ ! -s "$dir/SHA256SUMS.txt" ] || [ ! -s "$dir/SHA256SUMS.txt.asc" ]; then
        warn "signature files not available; skipping signature check (pinned SHA-256 still enforced)."
        return 0
    fi
    gnupg="$dir/gnupg"
    mkdir -m 700 "$gnupg"
    if ! curl -fsSL --proto '=https' --tlsv1.2 "$SCRCPY_KEY_URL" -o "$dir/signer.asc"; then
        warn "could not fetch the signing key; skipping signature check (pinned SHA-256 still enforced)."
        return 0
    fi
    fpr="$(GNUPGHOME="$gnupg" gpg --batch --show-keys --with-colons "$dir/signer.asc" 2>/dev/null \
        | awk -F: '$1 == "fpr" {print $10; exit}')"
    if [ "$fpr" != "$SCRCPY_SIGNER_FPR" ]; then
        warn "signing key fingerprint '$fpr' does not match the pinned one."
        return 1
    fi
    GNUPGHOME="$gnupg" gpg --batch --import "$dir/signer.asc" >/dev/null 2>&1
    status="$(GNUPGHOME="$gnupg" gpg --batch --status-fd 1 --verify \
        "$dir/SHA256SUMS.txt.asc" "$dir/SHA256SUMS.txt" 2>/dev/null || true)"
    if ! grep -q "^\[GNUPG:\] VALIDSIG .* $SCRCPY_SIGNER_FPR\$" <<<"$status"; then
        warn "GPG signature on SHA256SUMS.txt is not valid for the pinned signer."
        return 1
    fi
    # The signed list must also contain the checksum we pinned.
    if ! grep -q "^$SCRCPY_SHA256  scrcpy-linux-x86_64-v${SCRCPY_VERSION}.tar.gz\$" "$dir/SHA256SUMS.txt"; then
        warn "signed SHA256SUMS.txt does not list the pinned checksum."
        return 1
    fi
    log "GPG signature OK (key $SCRCPY_SIGNER_FPR)."
}

# The release ships its own adb. Expose it only when this PC has no adb of its own (or the existing
# one is our link); two different adb versions fight over the server on port 5037.
link_bundled_adb() { # dest
    local dest="$1" link existing
    link="$(adb_link)"
    if [ -L "$link" ] && case "$(readlink "$link")" in "$(install_prefix)"/opt/scrcpy-v*/adb) true ;; *) false ;; esac; then
        ln -sfn "$dest/adb" "$link"
        return 0
    fi
    existing="$(command -v adb 2>/dev/null || true)"
    if [ -n "$existing" ]; then
        log "Found existing adb at $existing; scrcpy keeps using its bundled adb."
        log "To share one adb server, run: export ADB=$existing"
        return 0
    fi
    ln -sfn "$dest/adb" "$link"
    log "Linked bundled adb at $link"
}

warn_path() {
    local bin_dir
    bin_dir="$(install_prefix)/bin"
    case ":$PATH:" in
        *":$bin_dir:"*) ;;
        *) warn "$bin_dir is not on your PATH. Add it (for example in ~/.profile) or run $(scrcpy_link) directly." ;;
    esac
}

install_scrcpy_release() {
    local arch dest link tarball tmp
    arch="$(machine_arch)"
    if [ "$arch" != "x86_64" ] && [ "$arch" != "amd64" ]; then
        die "the official Linux release is x86_64 only (this PC is '$arch'). Use --scrcpy-method apt, or build from source: https://github.com/Genymobile/scrcpy/blob/master/doc/build.md"
    fi
    dest="$(scrcpy_opt_dir)"
    link="$(scrcpy_link)"
    tarball="scrcpy-linux-x86_64-v${SCRCPY_VERSION}.tar.gz"

    if [ -x "$dest/scrcpy" ] && [ "$(readlink "$link" 2>/dev/null || true)" = "$dest/scrcpy" ]; then
        log "scrcpy v$SCRCPY_VERSION is already installed at $dest (nothing to do)."
        link_bundled_adb "$dest"
        return 0
    fi
    if [ -e "$link" ] || [ -L "$link" ]; then
        case "$(readlink "$link" 2>/dev/null || true)" in
            "$(install_prefix)"/opt/scrcpy-v*/scrcpy) ;;
            *)
                if [ "$FORCE" != "1" ]; then
                    die "$link already exists and was not created by this script; refusing to replace it (use --force)."
                fi
                ;;
        esac
    fi

    if [ "$DRY_RUN" = "1" ]; then
        log "[dry-run] would download $SCRCPY_BASE_URL/$tarball"
        log "[dry-run] would require SHA-256 $SCRCPY_SHA256"
        log "[dry-run] would verify SHA256SUMS.txt.asc against GPG key $SCRCPY_SIGNER_FPR when gpg is available"
        log "[dry-run] would extract to $dest and link $link (and adb, if this PC has none)"
        return 0
    fi

    command -v curl >/dev/null 2>&1 || die "curl is required (sudo apt install curl ca-certificates)."
    command -v tar >/dev/null 2>&1 || die "tar is required."
    command -v sha256sum >/dev/null 2>&1 || die "sha256sum is required."

    tmp="$(mktemp -d)"
    TMP_DIR="$tmp"
    trap 'rm -rf -- "$TMP_DIR"' EXIT
    log "Downloading $tarball ..."
    curl -fsSL --proto '=https,file' --tlsv1.2 "$SCRCPY_BASE_URL/$tarball" -o "$tmp/$tarball"
    verify_sha256 "$tmp/$tarball" "$SCRCPY_SHA256" || die "checksum check failed; nothing was installed."
    log "SHA-256 OK."
    curl -fsSL --proto '=https,file' --tlsv1.2 "$SCRCPY_BASE_URL/SHA256SUMS.txt" -o "$tmp/SHA256SUMS.txt" 2>/dev/null || true
    curl -fsSL --proto '=https,file' --tlsv1.2 "$SCRCPY_BASE_URL/SHA256SUMS.txt.asc" -o "$tmp/SHA256SUMS.txt.asc" 2>/dev/null || true
    verify_gpg_signature "$tmp" || die "signature check failed; nothing was installed."

    mkdir -p "$tmp/extract"
    tar -xzf "$tmp/$tarball" -C "$tmp/extract" --no-same-owner
    local top="$tmp/extract/scrcpy-linux-x86_64-v${SCRCPY_VERSION}"
    [ -x "$top/scrcpy" ] || die "unexpected archive layout; nothing was installed."

    mkdir -p "$(dirname "$dest")" "$(dirname "$link")"
    rm -rf -- "$dest"
    mv "$top" "$dest"
    ln -sfn "$dest/scrcpy" "$link"
    log "Installed scrcpy v$SCRCPY_VERSION to $dest"
    link_bundled_adb "$dest"
    warn_path
}

install_scrcpy_apt() {
    command -v apt-get >/dev/null 2>&1 || die "apt-get not found; this method needs Ubuntu/Debian."
    warn "The Ubuntu scrcpy package is old (Genymobile marks it obsolete) and may not support recent Android versions. Prefer --scrcpy-method release."
    if [ "$DRY_RUN" != "1" ] && dpkg -s scrcpy >/dev/null 2>&1 && dpkg -s adb >/dev/null 2>&1; then
        log "scrcpy and adb packages are already installed (nothing to do)."
        return 0
    fi
    command -v sudo >/dev/null 2>&1 || die "sudo is required for the apt method."
    run sudo apt-get update
    run sudo apt-get install -y scrcpy adb
}

report_tool() { # name
    local path ver
    path="$(command -v "$1" 2>/dev/null || true)"
    if [ -z "$path" ] && [ -x "$(install_prefix)/bin/$1" ]; then
        path="$(install_prefix)/bin/$1"
    fi
    if [ -n "$path" ]; then
        ver="$("$path" --version 2>/dev/null | head -n1 || true)"
        log "  $1: $path (${ver:-version unknown})"
    else
        log "  $1: not installed"
    fi
}

show_status() {
    log "Status:"
    report_tool scrcpy
    report_tool adb
    if [ -x "$(scrcpy_opt_dir)/scrcpy" ]; then
        log "  release build: $(scrcpy_opt_dir)"
    fi
}

next_steps() {
    cat <<'EOF'

Next steps (PC mirrors and controls the phone; the phone is the device being controlled):
  1. On the phone: VictorSuite -> My PC -> read the warning, then enable Wireless debugging.
     Use only a trusted Wi-Fi network, or Tailscale. Never expose ADB to the internet.
  2. On this PC (Android 11+ pairing, run once):   adb pair <phone-ip>:<pairing-port>
  3. Then:                                         adb connect <phone-ip>:<adb-port>
  4. Mirror and control:                           scrcpy -s <phone-ip>:<adb-port>
  5. When finished, close the window or press Ctrl+C, then:
                                                   adb disconnect <phone-ip>:<adb-port>
     and turn Wireless debugging off on the phone.
EOF
}

main() {
    parse_args "$@"
    preflight

    if [ "$WANT_STATUS" = "1" ]; then
        show_status
        return 0
    fi

    if [ "$WANT_SCRCPY" != "1" ]; then
        log "Nothing to do: scrcpy is optional and off by default. Re-run with --scrcpy to install it (see --help)."
        return 0
    fi

    if [ "$DRY_RUN" = "1" ]; then
        log "scrcpy step (dry run): method=$SCRCPY_METHOD"
    else
        log "scrcpy step: method=$SCRCPY_METHOD"
    fi
    case "$SCRCPY_METHOD" in
        release) install_scrcpy_release ;;
        apt) install_scrcpy_apt ;;
    esac

    if [ "$DRY_RUN" != "1" ]; then
        show_status
    fi
    next_steps
    log
    log "scrcpy is Apache-2.0, (c) Genymobile: https://github.com/Genymobile/scrcpy"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
    main "$@"
fi
