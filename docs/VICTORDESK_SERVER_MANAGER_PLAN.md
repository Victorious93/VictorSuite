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

## 7. Phased rollout
- **Phase 0**: this doc + #45 reviewed by Don.
- **Phase 1**: Client fork locked to Don's server (the #45 scope) with a manually set-up server on B.
- **Phase 2**: Server Manager Mode B (SSH, systemd, firewall, status).
- **Phase 3**: Mode C (LAN/Pi), backup/restore.
- **Phase 4**: Mode A on-device via VictorSuite RUN_COMMAND.
- **Phase 5**: Update/rollback automation, key rotation flow, release signing (**Don's approval required**).

## 8. Open questions for Don
1. VPS provider / host and OS (Debian/Ubuntu?), and the domain for the server.
2. SSH auth: on-device generated ed25519 key (recommended) vs importing an existing key; is sudo allowed, or root?
3. Which target is the **primary** relay baked into the client (B is recommended)? Multiple servers or one?
4. Mode A limits: max RAM/CPU, OK to hold a wakelock, how to handle CGNAT (VPN/tunnel?)?
5. Signing key for VictorDesk: new key vs existing fleet key. **Always Don's decision and approval; nothing is created here.**
6. Where the AGPL source is published (public fork repo?).
7. Distribution: sideload only vs Play/F-Droid (affects targetSdk/permissions).
8. Pin to a specific rustdesk-server release tag vs the commit above?

## 9. Relationship to PR #45 — recommendation
Keep #45 as the **client** design and merge it (or keep it open) first. This doc covers the server manager and the overall VictorDesk app, and points to #45 for client internals. Recommendation: **keep them separate but linked**, and once both are approved, fold #45's content into this doc's §client as a follow-up so VictorDesk has one doc. Where they differ, this doc wins on app name/ID (`VictorDesk`, `com.victorious.victordesk`).
