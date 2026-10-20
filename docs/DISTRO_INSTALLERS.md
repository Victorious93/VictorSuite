# One-tap Linux distribution installers

The drawer's **Linux Login** group (English and Chinese menus) has four buttons. Each one runs
the bundled `scripts/distro-login.sh <key>` in the terminal. The script installs the distribution
the first time you tap it, then logs you in. Everything runs in Termux's `proot-distro`, so you
do not need root.

| Button | Key | Official source | Container |
|---|---|---|---|
| Ubuntu | `ubuntu` | Docker Hub official image `ubuntu:24.04` (latest LTS) | `ubuntu` |
| Kali Linux | `kali` | Kali's official image `kalilinux/kali-rolling` | `kali` |
| Parrot OS | `parrot` | Parrot Security's official image `parrotsec/core` | `parrot` |
| BlackArch | `blackarch` | proot-distro's built-in `archlinux` plus BlackArch's official `https://blackarch.org/strap.sh` | `archlinux` |

## Notes

- **Image installs.** Installing from Docker/OCI images needs a recent `proot-distro`, so the
  script runs `pkg install -y proot-distro` first. If the install fails with an unknown-option or
  unknown-image error, run `pkg upgrade proot-distro` and tap the button again.
- **Parrot.** Parrot ships no 32-bit ARM image. On CPUs other than aarch64, x86_64 and riscv64,
  the script stops with a clear error and installs nothing. It never uses an unofficial rootfs.
- **BlackArch.** BlackArch has no proot or Termux build of its own. The documented way to get it
  is to run `strap.sh` on an existing Arch install, so the script:
  1. installs Arch Linux with `proot-distro install archlinux` (Arch Linux ARM on ARM devices),
  2. downloads `strap.sh` from blackarch.org,
  3. reads the SHA1 that is published at run time on https://blackarch.org/downloads.html, and runs
     `strap.sh` only if the downloaded file matches it (the hash is never hardcoded),
  4. runs `pacman -Syu` and marks the setup done with `/etc/blackarch-strap-done`.
  If any step fails, Arch stays installed and the next tap retries the BlackArch setup. This
  combination of proot-distro and strap.sh is community-level. Neither BlackArch nor Termux
  verifies it.
- The standalone scripts in `remote/on_line_sh/scripts/` (`install-kali-proot.sh`,
  `install-blackarch-proot.sh`, …) still work for the online-script menu.
- First installs download several hundred MB. Type `exit` to leave a distribution.
