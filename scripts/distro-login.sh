#!/data/data/com.termux/files/usr/bin/bash
# Log in to a Linux distribution running in a proot-distro container, installing
# it first if it is not there yet. Used by the drawer's "Linux Login" buttons.
#
#   distro-login.sh ubuntu   Ubuntu 24.04        image ubuntu:24.04
#   distro-login.sh kali     Kali Linux rolling  image kalilinux/kali-rolling
#   distro-login.sh parrot   Parrot OS (Core)    image parrotsec/core
#
# Each distribution gets its own container named ubuntu, kali or parrot, so a
# Kali container made by the "Kali Linux Rootless (proot)" online script is
# reused. The first run downloads a few hundred MB. You are root inside the
# container; leave it with `exit`. Container storage is private to Termux.
#
# The images are Docker Hub's official ones. Parrot publishes no 32-bit ARM
# image (only amd64, arm64, riscv64), so it cannot be installed on such phones.
#
# Needs a proot-distro new enough to install Docker/OCI images; `pkg install`
# below updates it.

set -euo pipefail

log() { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

usage() { sed -n '2,/^$/{s/^# \{0,1\}//;p}' "$0"; }

case "${1:-}" in
    ubuntu) NAME=ubuntu; IMAGE="ubuntu:24.04"; LABEL="Ubuntu 24.04"
            TIP="Use apt, e.g. 'apt update && apt install <package>'." ;;
    kali)   NAME=kali; IMAGE="kalilinux/kali-rolling"; LABEL="Kali Linux"
            TIP="Minimal image. Add tools with apt, e.g. 'apt update && apt install kali-tools-top10'." ;;
    parrot) NAME=parrot; IMAGE="parrotsec/core"; LABEL="Parrot OS (Core)"
            TIP="Minimal Core image. Add tools with apt." ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "unknown or missing distribution: ${1:-<none>}" ;;
esac

if [ -z "${PREFIX:-}" ] || ! command -v pkg >/dev/null 2>&1; then
    die "run this from a Termux/VictorSuite shell (PREFIX and pkg not found)"
fi

if [ "$NAME" = parrot ]; then
    case "$(uname -m)" in
        aarch64|arm64|x86_64|riscv64) ;;
        *) die "Parrot OS has no image for $(uname -m) (it publishes amd64, arm64 and riscv64 only)" ;;
    esac
fi

log "Making sure proot-distro is installed and current"
pkg install -y proot-distro

if proot-distro list -q 2>/dev/null | grep -qx "$NAME"; then
    log "Using the existing '$NAME' container"
else
    avail_kb="$(df -Pk "$PREFIX" 2>/dev/null | awk 'NR==2 {print $4}')"
    case "$avail_kb" in
        ''|*[!0-9]*) ;;
        *) [ "$avail_kb" -ge 2097152 ] || warn "only $((avail_kb / 1024)) MB free; $LABEL needs a few hundred MB to download and more once unpacked" ;;
    esac
    log "Installing $LABEL from $IMAGE as '$NAME' (first run only, this downloads several hundred MB)"
    if ! proot-distro install "$IMAGE" --name "$NAME"; then
        echo "Install failed. If the error says the image or option is unknown, your" >&2
        echo "proot-distro is too old for Docker images: run 'pkg upgrade proot-distro'" >&2
        echo "and tap the button again. Otherwise check your network and free space." >&2
        exit 1
    fi
fi

echo
echo "$LABEL: $TIP"
echo "Type 'exit' to return to Termux."
echo
exec proot-distro login "$NAME"
