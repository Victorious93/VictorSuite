#!/data/data/com.victorious.suite/files/usr/bin/sh
#
# Install Kali NetHunter Rootless using Kali's own official installer.
# No root or custom recovery is required.
#
# This wraps the exact steps published by Kali (https://www.kali.org/docs/nethunter/nethunter-rootless/):
#   1. termux-setup-storage   (grants Termux storage access)
#   2. pkg install wget
#   3. download the official installer from offs.ec/2MceZWr (Offensive
#      Security's own short link) as install-nethunter-termux
#   4. run it
#
# Before running this: install the NetHunter-Store app, then install
# NetHunter-KeX client and Hacker's Keyboard from it, per the Kali docs.
# Those are separate Android app installs this script cannot perform.

set -e

echo "Requesting Termux storage access..."
termux-setup-storage

echo "Installing wget..."
pkg install wget -y

echo "Downloading the official Kali NetHunter Rootless installer..."
wget -O install-nethunter-termux https://offs.ec/2MceZWr
chmod +x install-nethunter-termux

echo "Running the official installer..."
./install-nethunter-termux

echo "Done. See https://www.kali.org/docs/nethunter/nethunter-rootless/ for usage, KeX, and backup instructions."
