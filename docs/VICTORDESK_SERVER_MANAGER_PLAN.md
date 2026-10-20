# VictorDesk — RustDesk Server Manager + Custom-Server Client — Design Plan

> **Status: DESIGN ONLY.** No code, no build, no signing changes. Needs Tetra's (Don's) review. Do **not** auto-merge.

- App name: **VictorDesk** · applicationId: `com.victorious.victordesk` · Android only (for now)
- Relationship to PR #45 (`docs/RUSTDESK_PLUGIN_PLAN.md`, branch `docs/rustdesk-plugin-design`): #45 designs the custom-server-only **client**. This doc **extends** that scope to also **create and maintain the server(s)**. Client details (rebrand, default stripping, AGPL obligations, CI) are referenced from #45 and not repeated except where server management changes them. See §9 for the fold-in recommendation.

## Upstream pins (verified via GitHub API, 2026-10-20)

| Repo | Commit |
|---|---|
| rustdesk/rustdesk | `53996e3c404ea5083df0775057e4149e49102084` |
| rustdesk/rustdesk-server | `873230e35a8805de7fd057e7eb763c46f7a174ef` |
| rustdesk/hbb_common | `5d52530b636fb296a324260b074780cf559e0093` |

Verified facts (file:line at the pins above):

- `hbb_common/src/config.rs:117` — `RENDEZVOUS_SERVERS = ["rs-ny.rustdesk.com"]` (public default to strip).
- `hbb_common/src/config.rs:118` — `RS_PUB_KEY` hardcoded public key (replace with Don's key).
- `hbb_common/src/config.rs:120-123` — `RENDEZVOUS_PORT 21116`, `RELAY_PORT 21117`, `WS_RENDEZVOUS_PORT 21118`, `WS_RELAY_PORT 21119`.
- `hbb_common/src/config.rs:957` — falls back to `RENDEZVOUS_SERVERS` when no server is configured.
- `rustdesk-server/src/main.rs:19,23,26` — hbbs flags `-p/--port`, `-r/--relay-servers`, `-k/--key` ("Only allow the client with the same key").
- `rustdesk-server/src/hbbr.rs:17-18` — hbbr flags `-p/--port`, `-k/--key`.
- `rustdesk-server/src/common.rs:349-350` — `gen_sk` uses key file `id_ed25519` (+ `id_ed25519.pub`, line 628) in the working dir.
- `rustdesk-server/systemd/rustdesk-hbbs.service`, `rustdesk-hbbr.service` — upstream units (`ExecStart=/usr/bin/hbbs`, line 8).
- `rustdesk-server/LICENSE`, `rustdesk/LICENCE` — GNU AGPL v3.
- `rustdesk/flutter/android/app/build.gradle:102,104` — `applicationId "com.carriez.flutter_hbb"`, `targetSdkVersion 36`.

Port 21115 (hbbs NAT-type test, TCP) is from RustDesk's self-host docs, not pinned to a source line here. Firewall set used throughout: **TCP 21115–21119, UDP 21116**.

## 1. Goal

VictorDesk does two jobs:

1. **Server manager**: install, configure, start/stop/restart, check status/health, update, back up/restore, and manage keys for `hbbs` + `hbbr` on three targets:
   - **A** — on the phone, in a VictorSuite proot-distro container
   - **B** — a remote VPS Don owns, over SSH
   - **C** — a home server / Raspberry Pi on the LAN, over SSH
2. **Custom-server-only client**: can reach **only** Don's server. All `*.rustdesk.com` defaults are removed and blocked, Don's host and key are baked in, server settings are locked in the UI, and key validation is enforced (no unkeyed fallback).

## 2. Architecture

```
VictorDesk APK (AGPL, separate)
 ├─ Client (RustDesk fork, locked to Don's server; see #45 §3)
 └─ Server Manager
     ├─ Target A driver → VictorSuite RUN_COMMAND intent → proot-distro → hbbs/hbbr
     ├─ Target B driver → SSH (pinned host key) → systemd on VPS
     └─ Target C driver → SSH (pinned host key) → systemd on Pi/home server
```

One driver interface for all targets: `provision, start, stop, restart, status, update, backup, restore, rotateKey, exportPubKey`. Each target is a profile (host, port, auth ref, pinned host-key fingerprint). Secrets are referenced by Keystore alias, never stored in the profile.

### 2.1 Mode A — on-device (proot)
- VictorDesk asks for VictorSuite's `com.termux.permission.RUN_COMMAND` (user must also enable `allow-external-apps` in VictorSuite). Commands go to VictorSuite's RunCommandService. No Termux code goes into VictorDesk.
- Provision: `proot-distro install debian` (or reuse one), download the pinned upstream release binaries for the device ABI (arm64/armv7 if published), check SHA-256, put them in `/opt/victordesk/`.
- Run: `hbbs -k _` and `hbbr -k _` in the background via a VictorSuite session. No systemd in proot, so use a pidfile + supervisor script. Keep it alive with a VictorSuite wakelock.
- Limits: Android may kill background processes. Carrier NAT/CGNAT usually makes the phone unreachable from outside, so Mode A works mainly on the same LAN or over a VPN/tunnel. Ports >1024 are fine without root.
- Status: check the pidfile, TCP-connect localhost:21116/21117, and read the log tail.

### 2.2 Mode B — VPS over SSH
- Provision (idempotent script, shown to Don before it runs): create a `rustdesk` system user, install binaries to `/usr/bin` (matching the upstream units), set WorkingDirectory to `/var/lib/rustdesk-server` (so `id_ed25519` lives there), install units adapted from upstream `systemd/*.service` with `-k _`, then `systemctl enable --now`.
- Firewall: ufw/nftables/firewalld (detected) allow TCP 21115–21119 + UDP 21116 only. The cloud-provider firewall is a manual step listed in the checklist.
- Update: download the pinned release, verify the checksum, stop, swap, start, health-check, and roll back automatically if the check fails.
- Status: `systemctl is-active`, `journalctl -n`, port probe from the phone.

### 2.3 Mode C — LAN / Pi
Same as B over the LAN (mDNS/IP). For off-LAN clients, document a router port-forward for the same ports. ARM binary selection follows the Pi's `uname -m`.

**Pi SSH authentication — DECIDED (Don, 2026-10-20):**
1. Don images the Pi first (stock Raspberry Pi OS / Debian; any temporary first-boot access is Don's own, outside VictorDesk).
2. During VictorDesk setup, the phone generates a **fresh ed25519 SSH keypair** dedicated to that Pi. No existing key is imported or reused.
3. VictorDesk shows the public key (copy / QR / `ssh-ed25519 …` line) and Don adds it to `~/.ssh/authorized_keys` on the Pi.
4. The private key lives **only** in Android Keystore / secure storage (non-exportable where the platform allows, otherwise encrypted with a Keystore-wrapped key). It is never written to logs, backups, or the repo, and it never leaves the phone.
5. First connect pins the Pi host key (TOFU with fingerprint confirmation, §3). After that, VictorDesk recommends turning off password auth on the Pi (`PasswordAuthentication no`) and shows the command before it runs.
Losing or resetting the phone means re-authorizing a new phone-generated key on the Pi. There is no key export path by design.

### 2.4 Lifecycle (all modes)
| Op | Behaviour |
|---|---|
| provision | install binaries + service/supervisor, generate key on first start |
| start/stop/restart | systemctl or supervisor |
| status/health | process up + ports reachable + public key fingerprint matches the one baked into the client |
| update | pinned version, checksum, rollback |
| backup | `id_ed25519`, `id_ed25519.pub`, `db_v2.sqlite3`; encrypted on the phone with a Keystore-wrapped key |
| restore | push the backup, fix perms (600 on the private key), restart |
| rotate key | new keypair, then **all clients need the new key**. Two-phase: export new pub, update/redistribute the client, then switch the server. Warn that rotation locks out old clients |
| export pub key | QR / copy for the client build config |

## 2.5 NAS controller (all three modes)

VictorDesk also manages a **USB-attached SSD as a small NAS** on whichever target runs the server. The same UI and lifecycle apply everywhere, and per-mode drivers handle the differences.

### 2.5.1 Capabilities
| Op | Behaviour |
|---|---|
| detect | list block devices (`lsblk -J -o NAME,SIZE,FSTYPE,UUID,LABEL,MOUNTPOINT`), show model/size/FS and let Don pick one. Never auto-select. |
| mount | mount by **UUID** at `/srv/nas/<label>`, with an fstab/systemd `.mount` unit using `nofail` so boot never hangs if the drive is unplugged |
| filesystem | detect the existing FS and **never format without explicit typed confirmation** (device + size shown). Recommended: ext4 (Linux targets). Supported: ext4, exFAT (cross-platform), Btrfs (optional). NTFS is read/write only via `ntfs-3g` where present. Format is a separate, guarded op. |
| health | free space, `smartctl` (smartmontools) if the USB bridge passes SMART through, fsck on unmount when asked |
| share: SMB | Samba (`smbd`): one `[share]` block per share, generated in an include file (`/etc/samba/victordesk.conf`) and never by editing the user's `smb.conf` by hand. SMB2+ only (`server min protocol = SMB2_10` or higher), `guest ok = no` by default |
| share: NFS | kernel NFS server (`nfs-kernel-server` / `nfs-utils`): entries in `/etc/exports.d/victordesk.exports`, restricted to the LAN subnet Don picks, `root_squash` by default, NFSv4 preferred |
| users/permissions | NAS users are separate Linux users with no login shell (`/usr/sbin/nologin`). Samba passwords are set with `smbpasswd`, and the password is entered on the phone and passed via stdin, never logged. Per-share read-only / read-write lists, plus POSIX group ownership on the share dir |
| firewall | only when sharing is on: SMB TCP 445, NFS TCP/UDP 2049 (v4; v3 needs rpcbind 111 + mountd, so it is not enabled by default). LAN-only rules. **Never** opened to the internet; remote access goes through a VPN/tunnel (open question) |
| unmount/eject | stop shares that use the mount, then `umount`, then confirm it is safe to unplug |

Every command is shown to Don before it runs, as with the server lifecycle (§2.4).

### 2.5.2 Per-mode differences
- **A — Phone (USB-OTG SSD).** USB/device control on the phone is **owned by Don's RootForge app**. VictorDesk does **not** implement its own USB layer, request `UsbManager` permission for the drive, or bundle a userspace mass-storage/FS driver. See §2.5.3. Serving SMB/NFS from the phone is limited: in the proot/Termux userland (no root) Samba can only bind unprivileged ports (e.g. 4445, not 445, which many clients can't change), and the kernel NFS server is not usable. NFS on A therefore needs root (RootForge/Magisk context) or is **unsupported**; SMB on A is "best effort, LAN only, root recommended". The default for A is that the phone mounts the drive (via RootForge) and the Linux targets (B/C) remain the real NAS hosts.
- **B — VPS (attached/block volume).** The "SSD" is a provider block volume (e.g. `/dev/sdb`, `/dev/vdb`); attaching it in the provider console is a manual checklist step. Same mount/FS flow as C. **SMB/NFS must not be exposed publicly**; sharing on B is only offered over a VPN/WireGuard interface Don configures (otherwise the shares stay disabled). Provider snapshots are recommended for backup.
- **C — Pi (USB SSD).** The main NAS case. Full flow over the SSH key from §2.3. Notes: a powered hub or the official PSU is recommended for USB SSDs on a Pi; mount with `nofail`; and some USB-SATA bridges have UAS quirks, so the detect step surfaces kernel `dmesg` errors to Don.

### 2.5.3 RootForge integration boundary (Mode A)
Verified against `Victorious93/rootforge-os@3d059297b6cc6f000c3412ac35fe68785d8b87a4` (GPL-3.0): the RootForge Android app has a USB layer at `android-app/app/src/main/kotlin/ai/rootforge/os/usb/` (`UsbHost.kt`, `UsbTransport.kt`) plus `core/.../usb/UsbClassifier.kt`. **Today it targets ADB/fastboot/flash-mode devices only.** `res/xml/usb_device_filter.xml` matches class 255/subclass 66 (ADB proto 1, fastboot proto 3) and one vendor/product ID, and there is **no USB mass-storage (class 8) support yet**. So Mode A NAS depends on a RootForge feature that does not exist yet.

Proposed contract (to be implemented in RootForge, by Don's decision, not in this PR):
- RootForge remains the **single owner** of USB-OTG devices: device-attach handling, the Android USB permission prompt, and (with root) mounting the block device.
- RootForge exposes a narrow, **signature-permission-protected** API to VictorDesk (bound service or intent, the same pattern as the VictorSuite RUN_COMMAND note in §3): `listStorage()` → {id, label, size, fstype, mounted, mountPath}; `requestMount(id, readOnly)` / `requestUnmount(id)`, where RootForge shows its own confirmation; and state callbacks (attached/detached/mounted).
- VictorDesk only consumes the resulting **mount path** (for sharing/status) and never opens the USB device itself. If RootForge isn't installed or doesn't support storage, Mode A NAS is shown as unavailable with that reason.
- Formatting on the phone is **out of scope** for VictorDesk. It is a RootForge-side operation, if it exists at all.
- Licensing is clean: RootForge (GPLv3) and VictorDesk (AGPLv3 APK) are separate programs talking over IPC; no code is shared across the boundary.

## 3. Security
- No public default server, ever: compile-time removal of `RENDEZVOUS_SERVERS`/`RS_PUB_KEY` defaults, a CI grep that fails on `rustdesk.com`, and a runtime deny-list for `*.rustdesk.com`.
- Key validation: servers always run with `-k _` (key required). The client refuses to connect without Don's key and has no "skip key" path.
- Secrets (SSH private keys, passphrases, server `id_ed25519` backups) live only in Android Keystore / EncryptedFile. Never in the repo, logs, crash reports, or backups (`allowBackup=false`). Log redaction runs on all command output.
- SSH: ed25519 key auth preferred, generated on-device and non-exportable where possible. The host key is pinned on first use with an explicit fingerprint confirmation (TOFU), then a mismatch is a hard fail. Password auth is not allowed by default.
- Least privilege: dedicated `rustdesk` user. Sudo is used only for install/unit steps, and every command is shown to Don before it runs.
- The VictorSuite RUN_COMMAND permission is a strong capability. Document it and limit it to VictorDesk's signature.

## 4. Licensing
- rustdesk and rustdesk-server are **AGPL-3.0** (LICENSE files pinned above).
- **Server**: run unmodified upstream binaries. If Don offers the service to others, AGPL §13 still points to the upstream source, and the plan is to link it in the app.
- **Client**: rebranded fork, so recipients must be offered the source (a public or recipient-accessible fork repo). Modified files carry prominent change notices. Keep the AGPL license and notices.
- **VictorSuite is GPLv3**. GPLv3 §13 allows combining with AGPLv3 works, but the combined part would carry AGPL network terms. To keep them separate, VictorDesk is **its own APK** and talks to VictorSuite only through the RUN_COMMAND intent (separate programs).
- **targetSdk clash**: VictorSuite targets 28 (needed for Termux exec behaviour) and RustDesk targets 36. A separate APK keeps both. Note: Play-store distribution would require a modern targetSdk for VictorDesk, which is fine.
- **NAS components (§2.5):** nothing is bundled into the VictorDesk APK. On B/C, Samba, nfs-kernel-server/nfs-utils, smartmontools, exfatprogs, ntfs-3g are installed from the target distro's package manager (Don's machine, distro licenses apply). **Samba is GPL-3.0** (verified: `samba-team/samba` `COPYING` = GNU GPL v3, master `76368b5d2e16b29ac78bf547fd543d6866e28995`). If Samba binaries are ever shipped for Mode A (e.g. inside the proot rootfs), GPLv3 source-offer obligations apply to that distribution. Shipping via the distro package manager (apt in proot) avoids redistributing them ourselves. VictorDesk only generates config files.
- Name/logo: "VictorDesk" plus Don's own icon; no RustDesk trademarks (per #45 §2.3).

## 5. Repos & build
- New repo (proposed `Victorious93/VictorDesk`, AGPL), forked from rustdesk/rustdesk at a pinned tag. The server-manager module is Kotlin in the same APK. CI per #45 §6.
- VictorSuite only gets a doc and possibly a small permission note. No code change in this PR.

## 6. Test plan (for implementation PRs)
1. Static: CI fails on any `rustdesk.com` string or the upstream `RS_PUB_KEY` value in the client build.
2. Client: connect to Don's server (works); point at a server with the wrong key (refused); block DNS to Don's host (no fallback to public).
3. Mode A: provision on an arm64 test phone; ports up; survives screen-off for 1 h; stop/start; low-RAM behaviour.
4. Mode B: throwaway VPS: provision, firewall rule check (`nmap` from outside: only the expected ports), reboot persistence, update + forced rollback, backup/restore onto a fresh VM keeps the same pub key.
5. Mode C: Pi 4/5 arm64 same as B on the LAN.
6. SSH: host-key change → hard fail; secrets absent from logcat/logs (grep).
7. Key rotation: old client refused, new client accepted.
8. Pi SSH: fresh key generated per setup; private key not extractable from app storage/backup; unauthorized key → clean failure message.
9. NAS (C, then B): detect/mount by UUID, reboot with drive unplugged (boot not blocked by `nofail`), SMB share from Windows/macOS/Linux with RW/RO users, guest denied, NFS mount from an allowed vs disallowed subnet, `nmap` shows 445/2049 only on LAN/VPN interfaces, format refused without typed confirmation.
10. NAS (A): with RootForge storage API absent → "unavailable"; with it present → mount path received, VictorDesk never touches `UsbManager`.

## 7. Phased rollout
- **Phase 0**: this doc + #45 reviewed by Don.
- **Phase 1**: Client fork locked to Don's server (the #45 scope) with a manually set-up server on B.
- **Phase 2**: Server Manager Mode B (SSH, systemd, firewall, status).
- **Phase 3**: Mode C (LAN/Pi), backup/restore.
- **Phase 4**: Mode A on-device via VictorSuite RUN_COMMAND.
- **Phase 4b**: NAS controller on C, then B (VPN-only sharing); Mode A NAS after RootForge ships a storage API.
- **Phase 5**: Update/rollback automation, key rotation flow, release signing (**Don's approval required**).

## 8. Open questions for Don
1. VPS provider / host and OS (Debian/Ubuntu?), and the domain for the server.
2. Sudo vs root on B/C (the Pi SSH key question is resolved, see §2.3; Mode B uses the same fresh phone-generated key flow unless Don says otherwise).
3. Which target is the **primary** relay baked into the client (B is recommended)? Multiple servers or one?
4. Mode A limits: max RAM/CPU, OK to hold a wakelock, how to handle CGNAT (VPN/tunnel?)?
5. Signing key for VictorDesk: new key vs existing fleet key. **Always Don's decision and approval; nothing is created here.**
6. Where the AGPL source is published (public fork repo?).
7. Distribution: sideload only vs Play/F-Droid (affects targetSdk/permissions).
8. Pin to a specific rustdesk-server release tag vs the commit above?
9. NAS: preferred FS (ext4 vs exFAT for portability)? SMB only, NFS only, or both? Which LAN subnet / VPN for sharing on B?
10. RootForge: OK to add a mass-storage + signature-protected storage API to RootForge (separate PR in rootforge-os)? Should Mode A NAS sharing be supported at all, or should A only mount locally?

## 9. Relationship to PR #45 — recommendation
Keep #45 as the **client** design and merge it (or keep it open) first. This doc covers the server manager and the overall VictorDesk app, and points to #45 for client internals. Recommendation: **keep them separate but linked**, and once both are approved, fold #45's content into this doc's §client as a follow-up so VictorDesk has one doc. Where they differ, this doc wins on app name/ID (`VictorDesk`, `com.victorious.victordesk`).
