# Remote PC ("My PC") — plan

> **Status: DESIGN ONLY (PR 1 of 9).** No app code. Do not merge without Don's approval.
> Shared wire formats and contracts: [`docs/specs/VICTORPC_PAIRING.md`](specs/VICTORPC_PAIRING.md) (frozen v1).
> Written Nov 10, 2026 against `main` @ `ea51fad` (v0.118.3.61). **[unverified]** = not confirmed in the repo or upstream.

## 0. Goal

One tap from Don's phone (Moto G Play, Android 16) to his headless Ubuntu PC: a terminal or an SFTP
file browser, on the same Wi-Fi and remotely. VictorSuite owns the **My PC** screen and the PC setup
script. Victorious File Manager (PC pane) and droidcommand-AI (hub tile) reuse the pairing through
the shared spec.

## 1. Decisions (Don, Nov 10, 2026)

| Topic | Decision |
|---|---|
| Android on the PC | **Unknown (Don, Nov 10).** Waydroid and AVD emulator support are optional and **auto-detected** by the script. No ADB setup unless one is found. ADB only through an SSH local forward limited by `permitopen`; **no LAN listener** by default. |
| PC OS | **Ubuntu 24.04 LTS is the primary target** (Don's PC; confirmed Nov 10). 24.04 uses `ssh.socket` activation by default: port/listen changes need a socket override, not just `sshd_config`. **22.04 LTS is best effort** (classic `ssh.service`), tested if time allows. |
| Remote path | **Confirmed** default: VictorSuite's built-in userspace `tailscaled` (proxy `127.0.0.1:1055`). The official Tailscale app is also supported (auto-detected). **LAN must work with Tailscale off.** |
| Connect UX | **Confirmed:** one tap, **no biometric prompt by default**. Biometric-bound key is an opt-in toggle. **Hard stop only on a host-key change** — no "accept anyway". |
| Fleet keys | **Confirmed: one separate key per app.** VictorSuite enrolls consumer keys on the PC through `PcEnrollService`. The signing broker (VictorSuite signs for other apps) is **rejected for v1**. |

## 2. What the repo already has (verified on `ea51fad`)

| Area | Finding | Use |
|---|---|---|
| SSH/SFTP library | **None on the classpath.** No sshj / JSch / MINA sshd / `org.connectbot:sshlib` in `app/build.gradle`. `app/libs/mina-core-2.0.16.jar` is MINA *core* (FTP server). `avnc.aar` references `com.trilead.ssh2` (`SshTunnel`) and ships `sshlib.txt`, but the classes are not in the aar and no dependency is declared, so AVNC's SSH tunnel is probably broken **[unverified at runtime]**. `proguard-rules.pro` already keeps `com.trilead.ssh2.**`. The existing "quick SSH" feature shells out to Termux's `ssh`/`sshpass` (see §9). | Add `org.connectbot:sshlib` 2.2.51 (trilead-ssh2 fork, BSD-style, GPLv3-compatible); this also supplies AVNC's missing classes |
| QR | `victor/util/ScanActivity.java` (BGAQRCode ZXingView, Apache-2.0); `com.google.zxing:core:3.5.3` | Reuse for pairing |
| Terminal launch | `SingletonCommunicationUtils…sendTextToTerminal(...)` (e.g. `DistroLoginClickConfig`). `TerminalSession` is pty/subprocess-bound. | Terminal = Termux session running OpenSSH (§4) |
| Drawer | `MainMenuConfig.init()`, `*ClickConfig`, `assets/mainmenu/{en,cn}/zt_menu_config.xml`, `MenuDefaultsMerger` | `MyPcClickConfig` |
| Tailscale | `HeadscaleClickConfig` runs `tailscaled --tun=userspace-networking` with SOCKS5 + HTTP proxy on `127.0.0.1:1055` | Default remote path |
| Keystore exposure | Embedded Termux:API `KeystoreAPI` lists/signs/deletes **any** alias in the app's AndroidKeyStore (no filter, `termux-api-lib/.../KeystoreAPI.java:83`) | Reserve the `victorpc.` alias prefix (PR 3) |
| Caller pinning | `theme/FleetThemePins.kt`, `remotecontrol/TrustModel.kt` (`PairingTable`) | Same model for consumers |
| Fleet signing | Each fleet app has its own key (`app/victorsuite-debug.keystore` here) | Signature permission can't span the fleet → allowlist of package + cert SHA-256 |
| scrcpy | `com.termux.victor.scrcpy.MainActivity` exists (`AndroidManifest.xml:243`) but is **not exported** and reads no extras; ADB on `<ip>:5555` (`SendCommands.java:123`), video on `<ip>:7007` (`Scrcpy.java:142`) | Mirroring handoff only via `OPEN_PC mode=mirror` (reserved, PR 9) |
| NSD / mDNS | none yet | New |

## 3. Files and modules (by PR)

**PR 1 (this PR):** `docs/REMOTE_PC_PLAN.md`, `docs/specs/VICTORPC_PAIRING.md`, path fix in `docs/REMOTE_CONTROL_ENVELOPE.md`.

**PC side — `pc/`:**
- `pc/victor-pc-setup.sh` — bash, idempotent, `--dry-run`; flags `--tailscale [--login-server URL]`, `--pair`, `--unpair <comment-glob>`, `--adb` (only acts if Waydroid/AVD is detected), `--status`, `--uninstall`
- `pc/victorpc-pair.py` — one-shot enrollment listener, python3 stdlib only (spec §3)
- `pc/templates/10-victorpc.conf` — `sshd_config.d` drop-in
- `pc/templates/victorpc.service` — avahi service file (`_victorpc._tcp` + `_ssh._tcp`)
- `pc/README.md`; `tests/pc/` — shellcheck + python test reproducing the spec §3.1 vector

**App — `app/src/main/java/com/termux/victor/pc/`:**
- `model/PcProfile.kt`, `model/PairingPayload.kt`, `crypto/PairingTranscript.kt`, `crypto/SshWire.kt` (pubkey blob, DER→SSH ECDSA sig, host-key form), `share/PcConsumerPins.kt` — pure JVM, unit-tested
- `key/PcKeyStore.kt` — EC P-256, alias `victorpc.<profileId>.victorsuite`
- `pairing/PairingClient.kt`, `ui/PairPcActivity.kt` (QR + manual code)
- `store/PcProfileStore.kt` — EncryptedSharedPreferences (`security-crypto` already used), public data only
- `discovery/PcDiscovery.kt` (NsdManager), `discovery/SubnetScanner.kt`
- `ui/MyPcActivity.kt` + `res/layout/victor_activity_my_pc.xml` (fleet chrome via `FleetThemeApplier`, neon theme)
- `agent/VictorSshAgent.kt` — ssh-agent protocol on a Unix socket in `$PREFIX/var/run/victorpc/`
- `ssh/PcSshClient.kt` — sshlib wrapper, host-key pin verifier, path selection (LAN → official Tailscale VPN → userspace proxy)
- `sftp/SftpBrowserActivity.kt`
- `share/PcProfileProvider.kt`, `share/PcEnrollService.kt`, `ui/PcConsentActivity.kt`, `ui/OpenPcActivity.kt`
- `config/mainmenu/config/MyPcClickConfig.kt` + `MainMenuConfig` + menu XML (en/cn) + strings (en/zh-rCN)
- Manifest: activities, exported provider `com.termux.victor.pc`, exported service (`…pc.action.ENROLL`), `OPEN_PC` / `PC_CONSENT` intent filters, `<queries>` for consumers (inert at targetSdk 28)
- `termux-api-lib/.../KeystoreAPI.java`: refuse `victorpc.*` aliases (note in `UPSTREAM.md`)
- `app/build.gradle`: `org.connectbot:sshlib:2.2.51`; `THIRD_PARTY_NOTICES.md`, `docs/LICENSE_AUDIT.md`
- Tests: `app/src/test/java/com/termux/victor/pc/`

## 4. Keys, SSH, terminal, SFTP

- **Key:** Android Keystore EC P-256 → SSH `ecdsa-sha2-nistp256`, `SHA256withECDSA`, Keystore DER signature re-encoded as SSH `mpint r, mpint s`. API 23+ (matches minSdk). StrongBox tried first (API 28+), TEE fallback; `setUnlockedDeviceRequired(true)` where available.
- **No biometric by default** (Don's decision). Opt-in toggle in My PC generates a new key with `setUserAuthenticationRequired(true)` and re-enrolls it.
- **No Ed25519 in Keystore** on most devices (needs Android 13+ KeyMint support; Moto G Play **[unverified]**); ConnectBot also keeps Ed25519 software-only.
- **sshlib + non-exportable keys:** trilead-ssh2 commit 8ddc2cf (2020) removed private-key casts so `KeyPair`s from `AndroidKeyStore` work; ConnectBot ships Keystore EC keys. **[re-verify on 2.2.51 in PR 3; also its minSdk and transitive deps]**.
- **Terminal:** a Termux session runs real OpenSSH:
  `ssh -o IdentityAgent=$PREFIX/var/run/victorpc/agent.sock -o IdentitiesOnly=yes -o UserKnownHostsFile=~/.ssh/victorpc_known_hosts -o StrictHostKeyChecking=yes -p <port> <user>@<host>`
  `VictorSshAgent` signs with the Keystore key, runs only while a My PC session is live, and only signs data shaped like an SSH userauth request. known_hosts is written from the pinned `host_keys`. Needs `pkg install openssh` (My PC offers it).
- **SFTP:** sshlib `SFTPv3Client`, minimal browser (list, download, upload, rename, delete with confirm). File Manager owns the full PC pane.
- **Host-key change:** hard-stop screen, no override. The only action is "Re-pair".

## 5. Remote path and discovery

- **Order:** LAN host (last-seen first) → if the official Tailscale VPN is up, the 100.x / MagicDNS host directly → else, if the built-in userspace `tailscaled` is running (default remote path), connect through `127.0.0.1:1055` (sshlib `HTTPProxyData`; OpenSSH `ProxyCommand` via `nc -X connect` **[check which nc Termux ships]**). LAN never depends on Tailscale.
- Detecting the official app: an active VPN transport on `ConnectivityManager` plus a reachable 100.64.0.0/10 address **[heuristic, unverified]**.
- `127.0.0.1:1055` is **not** a fleet contract (spec §9).
- **Discovery:** `NsdManager` for `_victorpc._tcp` (TXT `v=1`, `id`) then `_ssh._tcp`, only while My PC is visible. Fallback: on-demand TCP scan of the Wi-Fi prefix (from `LinkProperties`), port 22, 300 ms timeout, ~32 concurrent, read the `SSH-2.0-` banner. A host is "my PC" only if its key matches the pin.
- **Android 16 local network protection** is expected not to apply at targetSdk 28 **[unverified; test on the Moto]**. NsdManager needs no multicast permission.

## 6. PC setup script (`pc/victor-pc-setup.sh`)

1. Preflight: Ubuntu 24.04 (primary); 22.04 best effort with a warning; anything else refused unless `--force`, `sudo`. **Lockout guard**: refuses to turn password login off unless a working key is already in `authorized_keys` or `--pair` is completing now, unless `--i-have-console`.
2. `apt install openssh-server` (+ `avahi-daemon`, `qrencode`, `python3` if missing; degrade to text without qrencode).
3. Drop-in `/etc/ssh/sshd_config.d/10-victorpc.conf`: `PasswordAuthentication no`, `KbdInteractiveAuthentication no`, `PermitRootLogin no`, `PubkeyAuthentication yes`, `AllowUsers <user>`, `PubkeyAcceptedAlgorithms +ecdsa-sha2-nistp256`. Check the main config `Include`s the drop-in dir; `sshd -t` before reload.
   - **24.04 (primary):** `ssh.socket` is active by default → apply with `systemctl daemon-reload && systemctl restart ssh.socket`; a non-22 port goes in `/etc/systemd/system/ssh.socket.d/10-victorpc.conf` (`ListenStream=`), not `Port`. The script detects socket mode with `systemctl is-active ssh.socket` rather than by version.
   - **22.04 (best effort):** `systemctl reload ssh`.
4. Firewall (ufw if present): allow 22/tcp and the pairing port only from the LAN subnet, `100.64.0.0/10`, and `tailscale0`. Never touch the router; no UPnP.
5. `--tailscale`: Tailscale's signed apt repo, `tailscale up [--login-server]`; add 100.x / MagicDNS names to `h`.
6. `--pair`: token + manual key, start `victorpc-pair.py` on specific IPs, print terminal QR (`qrencode -t ansiutf8`), `~/victorpc-pair.png`, the manual code and the host key (spec §2.1). Token removed on exit (`trap`).
7. `--adb` (only if detected): Waydroid (`waydroid status`, container IP, default `192.168.240.112` **[unverified]**) or an AVD (`adb devices` on the PC). Adds `permitopen` for `:5555` and `:7007` to VictorSuite's key line only. No socat / LAN listener by default. Headless ADB authorization (pre-seeding `adb_keys` via `waydroid shell`) **[unverified]**.
8. `--unpair victorpc:*:<app>` removes lines by comment. `--uninstall` removes the drop-in, avahi file and ufw rules (keeps openssh-server).

## 7. Sequence (small PRs; each through CI; no version bump unless Don asks)

| # | PR | Estimate |
|---|---|---|
| 1 | **This PR** — plan + spec v1 + `REMOTE_CONTROL_ENVELOPE.md` path fix | 0.5 d |
| 2 | `pc/` script, listener, templates, shellcheck + python vector test in CI | 1.5–2 d |
| 3 | Pure-JVM core (payload, transcript reproducing spec §3.1, `SshWire`, pins) + sshlib dep + notices + `KeystoreAPI` alias guard | 1.5 d |
| 4 | `PcKeyStore`, `PairingClient`, `PairPcActivity`, `PcProfileStore`, drawer entry — first Moto test | 2 d |
| 5 | Discovery (NSD + scan) + My PC list with reachability | 1–1.5 d |
| 6 | `VictorSshAgent` + one-tap terminal + remote path selection | 2 d |
| 7 | SFTP browser | 2 d |
| 8 | `PcProfileProvider`, `PcEnrollService`, consent, `OPEN_PC` — unblocks File Manager and droidcommand-AI | 1.5–2 d |
| 9 | ADB over SSH forward + `OPEN_PC mode=mirror` → internal scrcpy | 1 d |

About 13–15 dev-days, plus Don's device tests after PRs 4, 6 and 8. The separate security fix (§9) is its own PR and can go before PR 2.

## 8. Risks

- **Same-UID access to keys.** Code in the `com.termux` UID (Termux shell, RUN_COMMAND scripts) could use the Keystore via `termux-keystore` or the agent socket. Mitigations: `victorpc.` alias guard, agent only during a user-started session and only for userauth data, optional biometric key. Residual risk is inherent to a Termux fork; documented.
- **Lockout** when password login goes off: preflight guard + printed rollback (`sudo rm /etc/ssh/sshd_config.d/10-victorpc.conf && sudo systemctl restart ssh.socket` on 24.04 / `sudo systemctl reload ssh` on 22.04).
- **Token replay / guessing:** single use, 10-minute expiry, 3 bad MACs, nonce-bound HMAC both ways, listener on LAN/tailnet IPs only; the 50-bit manual key works only online.
- **Host-key pinning:** hard stop on change; reinstalling the PC means re-pairing.
- **Keystore realities:** P-256 only; keys are device-bound (uninstall / clear data / reset → re-pair; keep console access). Biometric off by default trades protection for one-tap.
- **ssh.socket on 24.04 (Don's PC):** a `Port` change in `sshd_config` is ignored under socket activation; the script must use a socket override.
- **Waydroid / AVD ADB:** not confirmed to exist; container IP varies; ADB authorization prompt needs a display; ADB is root-equivalent on the container → SSH forward only, `permitopen`-limited. scrcpy through the forward is **[unverified]**.
- **Tailscale:** userspace `tailscaled` drains battery if left running (My PC stops what it started); the official app's VPN conflicts with other VPNs; default-Tailscale (non-headscale) login via `HeadscaleClickConfig` **[unverified]**. The loopback proxy is reachable by any local app, so it is not a fleet contract.
- **Headless PC:** QR readable only via a terminal Don can see (SSH from another device) or the PNG; the manual code covers the rest. No avahi → subnet scan.
- **Battery / background:** no persistent service; discovery, agent and listener run only while in use.
- **Android 16 local-network permission:** expected not to apply at targetSdk 28 **[unverified]**.
- **Consumer pins** were reported by the other apps' bots, not verified; a blank pin never authorizes. `ai.droidcommand.app.debug` is an open item (spec §8).
- **Licensing:** sshlib (BSD-style), Tink if pulled in (Apache-2.0) — GPLv3-compatible; recorded in notices. No AGPL code.
- **Never** expose SSH/ADB/VNC to the internet.

## 9. Separate security fix (not part of Remote PC)

droidcommand-AI reported that the existing "quick SSH" device list skips host-key checks and stores
passwords in plain text. **Verified on `ea51fad` — true for password entries, with more issues.**
It uses **no SSH library**: it builds a shell command and types it into the Termux terminal, which runs
Termux's `ssh` (and `sshpass` for passwords).

Evidence:

| # | Finding | Where |
|---|---|---|
| a | **Host-key checks disabled for password entries:** `ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null` | `app/src/main/java/com/termux/victor/bean/SSHDeviceBean.java:104` (inside `generateConnectCommand()`, line 90) |
| a′ | Key-mode entries do **not** add those flags (`ssh -p … -i ~/.ssh/<alias>.key`), so OpenSSH's default first-use prompt and change-detection apply there | `SSHDeviceBean.java:93-97` |
| b | **Password stored in plain text:** `SSHDeviceBean.password` (field, line 10) serialized with Gson into unencrypted `SharedPreferences("xinhao")`, key `ssh_device_list` | `view/BoomWindow.java:59` (key), `:751` / `:782` (`SaveData.saveData(KEY_SSH_LIST, new Gson().toJson(list))`), `:353`, `:405`; `utils/SaveData.java:15-17` (`getSharedPreferences("xinhao", MODE_PRIVATE)…putString`) |
| c | **Password on the command line:** `sshpass -p '<password>'` — visible on the terminal screen, in the process list, and likely in shell history; single quotes are not escaped, so a `'` in the password breaks the command | `SSHDeviceBean.java:100-101`; sent at `BoomWindow.java:384,388` |
| d | **Password in logcat:** the full command, including `sshpass -p '<password>'`, is logged with `Log.d` unconditionally | `BoomWindow.java:385` → `z_file/.../util/LogUtils.java:27-28` |
| e | Key mode generates an **unencrypted** RSA-2048 key file (`-N ''`) at `~/.ssh/<alias>.key` | `utils/SSHKeyUtils.java:24`, called from `BoomWindow.java:588` |

Mitigating fact: `android:allowBackup="false"` (`AndroidManifest.xml:70`), so the prefs file is not in
Android cloud backups. Whether VictorSuite's own home backup (`BackupFragment`, to shared storage)
includes `~/.ssh/*.key` is **[unverified]**.

Proposed fix PR (its own PR, independent of Remote PC):
1. **Pin host keys:** drop `StrictHostKeyChecking=no` / `UserKnownHostsFile=/dev/null`; use a per-entry known_hosts with `StrictHostKeyChecking=accept-new` (trust on first use), and a **hard stop** on change (no override; "forget host key" only via an explicit edit).
2. **Move passwords** to EncryptedSharedPreferences (or Keystore-wrapped), never in the command line: pass via `SSHPASS` env + `sshpass -e`, or better, drop sshpass and let `ssh` prompt.
3. **Stop logging** the command (or redact everything after `ssh`).
4. **Offer key auth** as the default: generate keys with a passphrase or (later) reuse `VictorSshAgent`/Keystore from Remote PC; show an `ssh-copy-id` helper.
5. **Migrate** existing entries on first launch: read `ssh_device_list`, move passwords into the encrypted store, rewrite the JSON without them, and clear the old value.
6. Escape all user fields with a single shell-quote helper.
7. JVM tests for the command builder (no password in argv, no `StrictHostKeyChecking=no`).
