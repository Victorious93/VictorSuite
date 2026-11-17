# pc/ - Ubuntu PC helpers for VictorSuite "My PC"

`victor-pc-setup.sh` is a small, idempotent Ubuntu 24.04 (22.04 best effort) helper with `--dry-run`.

Today it does one optional thing: install [scrcpy](https://github.com/Genymobile/scrcpy) (Apache-2.0,
Genymobile) so the PC can mirror and control the phone over wireless ADB on Wi-Fi or Tailscale.
**A run without `--scrcpy` installs nothing.**

```sh
./pc/victor-pc-setup.sh --help
./pc/victor-pc-setup.sh --scrcpy --dry-run
./pc/victor-pc-setup.sh --scrcpy
./pc/victor-pc-setup.sh --status
```

- Default method: the official Genymobile static release, pinned to one version, installed under
  `~/.local` (no root). The tarball's SHA-256 is pinned in the script; the release GPG signature is
  also checked when `gpg` is available. Nothing is vendored in this repository.
- `--scrcpy-method apt`: the Ubuntu package (needs sudo; Genymobile calls it obsolete).
- To move to a newer scrcpy, change `SCRCPY_VERSION` and `SCRCPY_SHA256` together, using the checksum
  Genymobile publishes in `doc/linux.md` and `SHA256SUMS.txt` for that tag.

SSH hardening, Tailscale install, pairing, Waydroid and uninstall are part of the wider plan in
[`docs/REMOTE_PC_PLAN.md`](../docs/REMOTE_PC_PLAN.md) and are not implemented here.

Security: wireless ADB plus scrcpy gives the PC screen access and input control of the phone. Use it
only on a trusted LAN or Tailscale and never expose ADB to the internet.

Tests: `bash tests/pc/test_victor_pc_setup.sh` (no network, no sudo; the install flow runs against a
local fixture).
