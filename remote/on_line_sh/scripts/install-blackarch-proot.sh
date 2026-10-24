#!/data/data/com.termux/files/usr/bin/sh
#
# Install BlackArch on top of a proot-hosted Arch Linux, using Termux's
# official proot-distro tool plus BlackArch's own official strap.sh.
#
# BlackArch publishes no Termux/proot-specific installer of its own —
# strap.sh is documented for an existing real Arch Linux install
# (https://blackarch.org/downloads.html). This script supplies that Arch
# Linux base via Termux's official `proot-distro`, then runs strap.sh
# inside it exactly as BlackArch documents. This composition is not
# BlackArch- or Termux-official; treat it as community-level, not
# vendor-verified.
#
# IMPORTANT: strap.sh is a root-level installer running inside your Arch
# environment. BlackArch's downloads page publishes a SHA1 checksum for
# it, but that checksum has changed between the project's own pages in
# the past, so this script does NOT hardcode one. Instead it prints the
# checksum of the file it downloaded so you can compare it yourself
# against the current value shown at https://blackarch.org/downloads.html
# before continuing. Do not skip that check.

set -e

echo "Installing proot-distro..."
pkg install proot-distro -y

echo "Installing the Arch Linux base (if not already installed)..."
proot-distro install archlinux || true

echo "Fetching BlackArch's official strap.sh into the Arch Linux environment..."
proot-distro login archlinux -- sh -c '
  set -e
  pacman -Sy --noconfirm curl
  curl -fO https://blackarch.org/strap.sh
  echo "---------------------------------------------------------------"
  echo "sha1sum of the downloaded strap.sh:"
  sha1sum strap.sh
  echo "Compare this against the current hash published at:"
  echo "  https://blackarch.org/downloads.html"
  echo "Do NOT continue if it does not match."
  echo "---------------------------------------------------------------"
'

echo "If the checksum matched, run the following to install BlackArch:"
echo "  proot-distro login archlinux"
echo "  chmod +x strap.sh && ./strap.sh"
echo "  pacman -Syu"
echo "(Inside proot-distro's login shell you are already root, so no sudo is needed.)"
