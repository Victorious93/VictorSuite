#!/data/data/com.termux/files/usr/bin/sh
#
# Install Kali Linux (rolling) rootless under Termux's official proot-distro.
#
# Termux's proot-distro installs Docker/OCI images directly
# (https://github.com/termux/proot-distro), and the Kali project publishes
# an official image on Docker Hub as `kalilinux/kali-rolling`. This script
# installs that image as a proot container named "kali". No root, custom
# recovery or extra Android apps are needed.
#
# This is a minimal Kali userland. Install tools on demand afterwards, e.g.
# `apt install kali-tools-top10` (see https://www.kali.org/docs/general-use/metapackages/).
#
# If you want Kali's own NetHunter Rootless build (KeX desktop, NetHunter
# app integration) use the "Kali NetHunter Rootless" entry instead.
#
# Requires a proot-distro new enough to accept OCI image references. Older
# releases only know their built-in distro list and will reject the image
# name; update with `pkg upgrade proot-distro` and re-run.

set -e

NAME=kali
IMAGE=kalilinux/kali-rolling

echo "Installing proot-distro..."
pkg install proot-distro -y

if proot-distro list 2>/dev/null | grep -qw "$NAME"; then
  echo "A container named '$NAME' already exists; skipping install."
else
  echo "Installing $IMAGE as '$NAME' (this downloads several hundred MB)..."
  if ! proot-distro install "$IMAGE" --name "$NAME"; then
    echo "Install failed. If the error says the image/distro is unknown," >&2
    echo "your proot-distro is too old for OCI images: run" >&2
    echo "  pkg upgrade proot-distro" >&2
    echo "and re-run this script." >&2
    exit 1
  fi
fi

echo "Refreshing package lists inside Kali..."
proot-distro login "$NAME" -- sh -c 'apt-get update && apt-get -y upgrade'

echo "Done. Start Kali with:  proot-distro login $NAME"
echo "Inside it you are root; add tools with e.g. 'apt install kali-tools-top10'."
