# VictorSuite × RootForge OS — two apps, one suite

**Status:** planning only. No features implemented in this document’s PR.  
**Owner:** Don Victor (GitHub [Victorious93](https://github.com/Victorious93)).  
**Decision:** **do not merge into one APK.** VictorSuite stays on `targetSdk` 28 so Termux exec / `$PREFIX` keep working. RootForge stays on a modern `targetSdk` so USB host and storage permissions work. The product is a **suite**: two sideload APKs, one shared Kotlin library, one JSON command schema, matching branding and signing, and a deep-link handoff.

**Supersedes** the earlier one-APK `docs/MERGE_PLAN.md` (that path is now a stub).

**Scope of this document:** two Android APKs that a user installs together (or separately); a standalone Termux-native CLI that does not need either APK; a later PC-first edition; Termux:API built into **VictorSuite** behind a Settings toggle; **admin/client remote control** in the **RootForge** app (USB-C and same-LAN first; internet later via optional Tailscale / Headscale).

Inventories below are grounded in code as of:

| Tree | Ref | Notes |
|---|---|---|
| This repo (VictorSuite, private) | `main` @ `ced930b` | Termux/ZeroTermux fork, `applicationId` effectively `com.termux`, `targetSdk` **28** |
| [Victorious93/rootforge-os](https://github.com/Victorious93/rootforge-os) (public) | `main` @ `ae180a3` (includes merged [PR #49](https://github.com/Victorious93/rootforge-os/pull/49)) | Debian ISO + Termux rootfs + Kotlin `:core` + Android APK `ai.rootforge.os` `targetSdk` **35** + Compose desktop + Docker |
| [termux/termux-api](https://github.com/termux/termux-api) | `master` @ current clone (`versionName` 0.53.0 / `versionCode` 1002) | Companion APK `com.termux.api`, GPLv3 |
| [termux/termux-api-package](https://github.com/termux/termux-api-package) | README + `termux-api.c` (helper 0.60.0) | MIT `termux-*` scripts + C helper |

ZeroTermux research: [hanxinhao000/ZeroTermux](https://github.com/hanxinhao000/ZeroTermux) and [zerotermux.dev](https://zerotermux.dev/).

---

## 1. Goal and non-goals

### Goal

Ship **two sideload APKs** that form one suite. Users may install either app alone; together they share branding, signing lineage, a command schema, and a handoff.

| App | Owns |
|---|---|
| **VictorSuite** (`com.termux`, `targetSdk` 28) | Terminal, bootstrap, proot containers, X11, plugins, backup/restore, FTP, QEMU, online catalogues, and the **built-in Termux:API toggle**. |
| **RootForge** (`ai.rootforge.os`, `targetSdk` 35) | ADB / fastboot / EDL over USB OTG, root patching, module builder, recovery, USB logs, and **admin/client remote control** including optional Tailscale / Headscale. |

**Shared (not a third APK):**

1. RootForge’s Kotlin **`:core`** as a shared library (published Maven artifact **or** git submodule / `includeBuild`). JVM only — no Android SDK, no Termux.
2. **One JSON command schema** (`BridgeRequest` / `BridgeResponse`, `BRIDGE_PROTOCOL_MAJOR = 1`) so [droidcommand-ai](https://github.com/Victorious93/droidcommand-AI) talks to both apps the same way (`RUN_COMMAND` on VictorSuite, Messenger `ai.rootforge.os.action.BRIDGE` on RootForge, `peer.*` on RootForge for phone-to-phone).
3. VictorSuite **deep link / “Open in RootForge”** handoff (drawer items launch RootForge destinations; if RootForge is missing, offer sideload).
4. **Matching branding and signing** (suite look; same publisher key lineage — not a shared `sharedUserId`).

Termux-only users continue to `git clone` RootForge OS and run its CLI/scripts in stock Termux (or VictorSuite) **without either toolkit APK**.

A later **PC-first** Linux/Windows edition uses the same Kotlin `:core` and the existing Compose desktop client — not a phone UI stretched onto a monitor, and **not** Termux on the desktop classpath.

### Why not one APK

A single process cannot honestly satisfy both SDK contracts:

| Constraint | VictorSuite must | RootForge must |
|---|---|---|
| `targetSdk` | Stay **28**. Termux exec, `$PREFIX` under `/data/data/com.termux/files`, background `am broadcast` from the `termux-api` helper, and external-dir layout all assume the Termux/ZeroTermux 28 contract. Raising it to 33–35 breaks scoped storage, helper delivery, and bootstrap. | Stay **modern (35 today)**. USB host permission flow, scoped/storage access for images and dumps, foreground-service types, and notification rules for an always-on client agent are written against current `targetSdk`. Shipping the toolkit at 28 would fight Android 12+ USB and storage. |
| `applicationId` | `com.termux` is load-bearing (PREFIX, `RUN_COMMAND`, `core-termux`). | `ai.rootforge.os` is already the DCA Messenger package. |
| Permissions story | SMS / contacts leftovers + a future Termux:API embed. Sideload. | USB host + (later) optional VPN. No need to inherit VictorSuite’s SMS/call-log surface. |

The previous one-APK plan treated “keep `targetSdk` 28 for everything” as acceptable because the merged binary would never go to Play. That still **breaks RootForge’s USB/storage permission model** on current Android. Split is the owner decision.

### Non-goals (this plan)

- Merging the two APKs, rewriting ZeroTermux UI in Compose, or producing a Play Store listing for either full app.
- Building the Debian live ISO inside VictorSuite CI.
- Replacing Magisk/KernelSU/APatch with a custom patcher (RootForge already delegates patching to those apps on the **target** phone).
- Changing droidcommand-ai in the same PR as this document (protocol stays; DCA needs a small follow-up to treat the suite as two packages, one schema).
- Using ZeroTermux’s unauthenticated TCP **19951** (`ZTSocketService`) as the admin/client channel — cautionary existing control plane, not a base (see §12).
- Letting an admin flash/unlock the client through admin/client mode (that remains the **USB toolkit host→target** path; the far phone need not run any suite app).
- Putting Termux, zerocore, or Termux:API on RootForge’s `:core` / `:toolkit-ui` / `:desktop` classpath.

---

## 2. Inventory — VictorSuite (this repo)

VictorSuite is a Termux fork on the ZeroTermux codebase. It keeps Termux’s `applicationId` / `sharedUserId` (`com.termux`) and the public `RUN_COMMAND` contract so existing Termux tooling and droidcommand-AI’s `core-termux` module still work. **VictorSuite and genuine Termux cannot be installed on the same device.** VictorSuite and RootForge **can** — different applicationIds.

### Languages and size

| Kind | Count (approx.) |
|---|---|
| Java | 469 files |
| Kotlin | 122 files |
| Shell | 27 files |
| JNI C | 2 files (`terminal-emulator`) |
| Java/Kotlin LOC (app + three libraries) | ~89.5k |
| of which `com.termux.zerocore` | ~41.9k |

New zerocore work is Kotlin; upstream Termux files stay Java, wrapped in `// VictorSuite add {@` / `// VictorSuite modify {@` markers.

### Gradle modules (`settings.gradle`)

```
:app                 application, namespace com.termux
:termux-shared       Termux constants, RUN_COMMAND, file/logger/settings, JNI helpers
:terminal-emulator   VT100/xterm state machine (no Android UI)
:terminal-view       Android View renderer
```

No Compose. `:app` uses data binding, AppCompat, drawer + RecyclerView menus.

### Build

- AGP **8.13.2**, Kotlin **2.2.0**, JDK **17**.
- `minSdkVersion` **23**, `targetSdkVersion` **28**, `compileSdkVersion` **36**, NDK **29.0.14206865** (`gradle.properties`).
- `TERMUX_PACKAGE_VARIANT` default `apt-android-7`.
- `versionName` **0.118.3.54**, `versionCode` **117**.
- Display name placeholder `TERMUX_APP_NAME = "VictorSuite"`; package still `com.termux` via `manifestPlaceholders.TERMUX_PACKAGE_NAME` and `TermuxConstants.TERMUX_PACKAGE_NAME`. There is no explicit `applicationId` in `app/build.gradle`; AGP uses `namespace "com.termux"`.
- CI: `.github/workflows/android.yml` → `:app:assembleDebug` on `main` (JDK 17, platform-36, NDK 29, `packages: platform-tools`).
- Signing: committed `phone.jks` with env-overridable passwords in `app/build.gradle` (legacy ZeroTermux/UTermux key compatibility). **Owner-approval item** — see §2.1.

### What lives under `com.termux.zerocore` (ZeroTermux / VictorSuite)

Drawer categories in `MainMenuConfig.init()`:

| Category | Examples |
|---|---|
| Common | switch source, container switch, backup/restore, QEMU, ZT settings |
| X11 | X11 settings/environment, install X11, VNC, show/hide extra keys |
| Beautification | float window, fonts, particles, snowflake, fullscreen |
| Engine | file browser, Alpine x86, keys (optional `com.xinhao.zerocoremanage` APK via `ZeroCoreManage`) |
| Root | wireless ADB on/off, Docker check |
| Online | online scripts, BBS, downloads, public warehouse |
| Config | ADB shell, ZT command keys, bash/start-message, boot command |
| ZT features | install module, FTP, soft links, uninstall, remote, Headscale, APK analysis, AndroidIDE, AI CLIs, SMS, timers, language, GitHub |

Other notable packages: `plugin/` (authorized-use gate, encrypted prefs, JSONL audit), `deepseek/` (DeepSeek/Claude chat + SQLite), `back/` (`.tar.gz`/`.bz2`/`.xz`/`.Z`), `ftp/` + `ftp/new_ftp/`, `http_core`/`http_service`, `scrcpy/`, `utermux_windows/qemu/`, `adb/` (`libadb.so` + `com.tananaev:adblib:1.3`), `otg/` (**USB mass-storage** via libaums, not ADB), `bosybox/`, `config/ztcommand/` (`ZTSocketService` TCP **19951** JSON commands), `libsu` (topjohnwu 5.2.1).

Remote JSON catalogues in `remote/{plugins,repository,on_line_sh}/main.json`.

The **Headscale** drawer (`HeadscaleClickConfig`: userspace `tailscaled`, SOCKS `127.0.0.1:1055`, unpinned `pkgs.tailscale.com` tarball) is a Termux-side convenience today. **Admin/client mesh belongs in RootForge Settings → Remote** (§13.2.1). After that ships, this drawer becomes a shortcut (“Open in RootForge”) or a Termux-only userspace proxy — not a second tailscaled. Do not run two meshes.

### Android surface

- Launcher: `TermuxActivity` (terminal).
- Settings: upstream `SettingsActivity` (PreferenceFragment, Termux:API row **hides if `com.termux.api` is missing**) plus `VictorSuiteSettingsActivity` (SwitchCompat list — the place for the embedded Termux:API toggle).
- Manifest declares `android.hardware.usb.host` **`required="true"`** (ZeroTermux OTG file access). That **blocks install on phones without USB host**. Flip to `required="false"` as a standalone VictorSuite bugfix (mass-storage degrades; terminal must install everywhere). RootForge keeps its own USB-host feature for ADB/fastboot/EDL.
- Permissions already include `INTERNET`, storage, `READ_SMS`, `READ_CONTACTS`, `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, `WRITE_SECURE_SETTINGS`, `DUMP`, `PACKAGE_USAGE_STATS`. Not the full Termux:API set.
- `sharedUserId="${TERMUX_PACKAGE_NAME}"` (`com.termux`) — official Termux plugins share the prefix **when signed with the same key**. VictorSuite’s `phone.jks` is **not** the official Termux key, so official F-Droid Termux:API will **not** share UID. Embedding Termux:API is how `termux-*` works for VictorSuite users without a matching-signature companion.

### License

`LICENSE.md`: **GPLv3 only**, following ZeroTermux / Termux. Exceptions: Apache-2.0 terminal-emulator/view and StreamGobbler; GPLv2+Classpath for `com.termux.shared.file` ojluni bits.

### 2.1 VictorSuite-side prerequisites (owner approval)

These are **in this repo**, independent of RootForge, and should be decided before suite handoff or a Termux:API embed lands. They are not optional cleanup.

**1. Dedupe Gradle dependencies.** `:app` declares the same libraries at conflicting versions in three places (`android { dependencies {…} }`, the VictorSuite add block, and the bottom `dependencies { }` block) plus `:termux-shared`:

| Library | Versions present today |
|---|---|
| `com.google.android.material:material` | **1.12.0** (`:app` twice, `:termux-shared`) and **1.2.1** (bottom `dependencies`) |
| `com.google.guava:guava` | **24.1-jre** (`:app` + `:termux-shared`) and **31.0.1-jre** (VictorSuite add block), plus `listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` |
| `androidx.appcompat:appcompat` | **1.6.1** (`:termux-shared`) and **1.2.0** (`:app` bottom block) |

Resolution is currently “whatever Gradle picks,” which is a latent break for a `:core` `includeBuild` or any Compose-adjacent AndroidX bump. Owner approval: collapse to **one version each** (recommend Material 1.12.x, Guava 31.x or a single 24.x if 31 is unused, AppCompat 1.6.x) in a dedicated hygiene PR before suite work.

**2. Package name `com.termux` clash.** Keeping `com.termux` means VictorSuite **cannot coexist with official Termux**. That is load-bearing for PREFIX, `RUN_COMMAND`, and `core-termux`. Renaming would require rewriting bootstrap paths, helper retargets, and a DCA follow-up. **Owner approval:** keep `com.termux` (recommended) **or** plan a migration. RootForge’s `ai.rootforge.os` does **not** clash with either.

**3. Committed signing key.** `phone.jks` is in the tree. `app/build.gradle` embeds fallback `storePassword` / `keyPassword` (`123456` / `654321`, alias `phone1`) when env vars are unset. This is a **secret in git** and a supply-chain issue. Official Termux plugins will never share UID with this key. Suite “matching signing” must not mean “keep publishing the leaked ZeroTermux key.” **Owner approval:** rotate to a new Victorious keystore (CI secrets only, remove `phone.jks` from git, accept that existing installs will not upgrade in place) **or** keep the committed key for UTermux-compat upgrades and accept the leak. Matching RootForge signing (same publisher, **different** keystore from DCA) is a separate decision once this is settled.

---

## 3. Inventory — RootForge OS

Public repo: Debian 12 live/install ISO + Termux/PRoot (and rooted chroot) Debian rootfs + unified `rootforge` CLI + host-phone Android toolkit + (PR #49, now on `main`) Compose Multiplatform desktop + Docker headless API.

Honesty already in that README: ISO/VM/hardware and real USB host/target pairs are **not verified** in the environments that last updated the docs. The Android APK and desktop **compile and have hermetic tests**.

### Languages and size

| Kind | Approx. |
|---|---|
| Kotlin (`android-app/`) | 68 files, ~7.9k LOC |
| Python (`rootforge.core` + tests + docker API) | 33 files, ~7.6k LOC |
| Shell (scripts, hooks, termux, tests, docker) | 42 files, ~11.5k LOC |

Two “cores”, easy to confuse:

| Name | What it actually is |
|---|---|
| **Python `rootforge.core`** | `config/includes.chroot/usr/local/lib/rootforge/core/` — CLI argparse tree (`cli.py`, `device.py`, `backup.py`, `flashing.py`, `module.py`, `boot.py`, `ota.py`, `avd.py`, `doctor.py`, `config.py`, `log.py`, `audit.py`, `runner.py`). Wraps the bash scripts; does not reimplement flashing. |
| **Kotlin `:core`** | `android-app/core` — JVM library: flash planner, module zip builder, boot-image parser, USB classifier, approvals, bridge protocol, library (named files, not paths), `LinkState`. Used by Android `:app` and Compose `:desktop`. **No Android SDK dependency.** This is the suite’s shared library. |

### Gradle (`android-app/settings.gradle.kts`)

```
rootProject.name = "rootforge-os-android"
include(":core")      # kotlin("jvm") + kotlinx-serialization
include(":app")       # Android APK, applicationId ai.rootforge.os
include(":desktop")   # Compose Multiplatform desktop (PR #49)
```

`:app` (`android-app/app/build.gradle.kts`):

- `applicationId` / `namespace` **`ai.rootforge.os`**
- debug suffix `.debug` → `ai.rootforge.os.debug`
- `minSdk` **26**, `targetSdk` **35**, `compileSdk` **35**
- `versionName` **0.1.0** / `versionCode` **1**
- Compose BOM 2024.12.01, Material 3, Navigation, Paparazzi screenshots
- **No INTERNET permission today.** USB Host feature **required=true**. Internet remote / Tailscale will need `INTERNET` on this APK (not on VictorSuite’s behalf).
- Exported `RootForgeBridgeService` action `ai.rootforge.os.action.BRIDGE`
- Queries `ai.droidcommand.app`

`:desktop` compiles the **same Compose screens** from `app/src/main/kotlin/ai/rootforge/os/ui` (minus Previews) plus host `adb`/`fastboot`/`edl` (`HostController`, `HostTools`). Packages `.deb` / `.exe` / `.msi` / AppImage.

`:core` is the seam that makes a PC-first edition possible without dragging Termux into the desktop binary.

### Shell / Python tool layer (the Termux-native CLI)

Layout:

```
config/includes.chroot/usr/local/bin/          # 30 scripts + `rootforge` shim + `brain`
config/includes.chroot/usr/local/lib/rootforge/core/*.py
config/includes.chroot/usr/local/lib/rootforge/sh/common.sh
termux/                                        # rootfs build, install.sh.in, chroot launcher
```

`usr/local/bin/rootforge` is a POSIX shim:

```sh
exec env PYTHONPATH="/usr/local/lib:${PYTHONPATH:-}" python3 -m rootforge.core.cli "$@"
```

`common.sh` `rf_rootforge` already falls back to a **git-checkout** layout (PYTHONPATH relative to the lib dir) so scripts work without being installed to `/usr/local`. `runner.py` `find_script` looks beside the package, then `/usr/local/bin`, then `PATH`.

CLI groups (from README, implemented): `doctor`, `devices`, `device`, `config`, `flash`, `backup`, `module`, `boot`, `ota`, `avd`.

Termux delivery:

- **Release path:** generated `install.sh` (from `termux/templates/install.sh.in`) installs `proot-distro`, verifies plugin SHA-256, `proot-distro install rootforge`. Template **refuses to run** until a release fills digests. No GitHub Release exists yet (`v0.1.0` / `v0.1.1` tags only).
- **Checkout path (works today, needs documenting as first-class):** clone the public repo in Termux, put the Python package and scripts on `PYTHONPATH`/`PATH`, `pkg install python android-tools`, run `rootforge doctor`. Destructive commands still need host `adb`/`fastboot` (Termux `android-tools` or a Debian proot). This path **must stay first-class** and must not require either suite APK.

ISO/live-build (`auto/`, `config/hooks/`, `Makefile`) stays in RootForge. It is not part of either APK.

### Android toolkit screens (`RootForgeUi.kt` `Destination`)

Dashboard, ADB, Fastboot, EDL, Root/Patching, Module Builder, Recovery, Logs/Terminal, Settings. Modal navigation drawer + typed confirmation (`FLASH` / `UNLOCK` / `LOCK` / `SLOT` / `EDL`) via `ApprovalCenter`. USB stack is **pure Kotlin** (`adb/`, `fastboot/`, `edl/Sahara+Firehose`, `usb/UsbHost`) — not a bundled `adb` binary.

**Add** (this plan): Admin / Client destinations + Settings → Remote (Tailscale / Headscale). These stay in RootForge, not VictorSuite.

### License

**No `LICENSE` file.** README states redistribution terms are undefined. Third-party bits fetched at ISO build time keep their own licenses. This is an **open decision** and a blocker before advertising the public repo as open source or publishing `:core` as an artifact that VictorSuite (GPLv3) or DCA may depend on.

### Tests / CI

- `tests/run-tests.sh` + `tests/lint.sh` (Python + shell, hermetic stubs).
- `:core:test` (48), `:app:testDebugUnitTest` (protocol + Paparazzi).
- Workflows: `lint.yml`, `android-apk.yml`, `toolkit.yml` (desktop + Docker), `release.yml` (ISO + rootfs, draft).

---

## 4. ZeroTermux feature set (inspiration, not a third repo)

VictorSuite **already is** a ZeroTermux-lineage tree. Do not import another copy of ZeroTermux.

Upstream ZeroTermux ([hanxinhao000/ZeroTermux](https://github.com/hanxinhao000/ZeroTermux), GPLv3) adds on top of Termux: graphical panels, backup/restore packs, PRoot/chroot distros, X11/QEMU, download-site restore packs, and (recently) an in-app AI agent / LSP editor.

VictorSuite has the historical ZeroTermux management layer (`zerocore`) plus VictorSuite-specific plugins, AI CLI setup, and DeepSeek/Claude chat. It is **behind** current ZeroTermux on the AI-agent / LSP work (ZeroTermux 0.118.3.63+). This program should **not** fast-forward to that AI agent.

---

## 5. Termux:API — embed in VictorSuite only

Owner requirement, **unchanged in intent**, **relocated in home:** build Termux:API into the **VictorSuite** APK, Settings on/off, no companion `com.termux.api` APK. RootForge does **not** embed these receivers. Admin/client that needs `termux-*` on the client phone talks to VictorSuite over suite IPC / `RUN_COMMAND` when that toggle is on.

### 5.1 Two artifacts, two licenses, two package names

| Artifact | Repo | License | What it is |
|---|---|---|---|
| **Termux:API app** | [termux/termux-api](https://github.com/termux/termux-api) | **GPLv3** (README; there is **no `LICENSE` file** in the tree — still GPLv3 per project statement). App `versionName` 0.53.0. | Android APK `applicationId` **`com.termux.api`**, `sharedUserId` `com.termux`. |
| **`termux-api` package** | [termux/termux-api-package](https://github.com/termux/termux-api-package) | **MIT** (`TERMUX_PKG_LICENSE="MIT"` in termux-packages `build.sh`) | C helper `termux-api` + shell wrappers `termux-clipboard-get`, `termux-notification`, `termux-battery-status`, … Users install with `pkg install termux-api`. |

The commands in `$PREFIX/bin` are **not** the app. They only work if something on the device implements `TermuxApiReceiver`.

### 5.2 Call path (today)

```
termux-clipboard-get          # MIT shell wrapper
        │
        v
termux-api Clipboard …        # C helper (termux-api.c)
        │  1. creates two abstract UNIX sockets (UUIDs)
        │  2. tries LocalServerSocket "com.termux.api://listen"  (Android < 14)
        │  3. fallback:
        v
am broadcast --user 0 \
  -n com.termux.api/.TermuxApiReceiver \
  --es socket_input  <app-reads-this> \
  --es socket_output <app-writes-this> \
  --es api_method    Clipboard \
  --ei api_server_pid/uid/starttime …
        │
        v
TermuxApiReceiver.doWork()    # GPLv3 Java, switch(api_method)
        │  per-method: TermuxApiPermissionActivity.checkAndRequestPermissions(...)
        v
ClipboardAPI / BatteryStatusAPI / LocationAPI / SmsSendAPI / …
        │  ResultReturner writes JSON/text into the output socket
        v
helper copies socket → stdout of the shell command
```

Hard-coded in `termux-api.c` `exec_am_broadcast_v2`:

```c
child_argv[5] = "com.termux.api/.TermuxApiReceiver";
```

Hard-coded listen address: `LISTEN_SOCKET_ADDRESS "com.termux.api://listen"` which matches `SocketListener.LISTEN_ADDRESS = TermuxConstants.TERMUX_API_PACKAGE_NAME + "://listen"`.

`TermuxApiReceiver` is **`android:exported="false"`**. Delivery works because `am broadcast -n` uses an explicit component **in package `com.termux.api`**, and the helper runs as the Termux UID (`sharedUserId`).

VictorSuite already looks for the companion: `TermuxUtils.isTermuxAPIAppInstalled()` / `TermuxAPIAppSharedPreferences.build()` use package **`com.termux.api`**. Settings hides the Termux:API preference if that package is absent.

### 5.3 API methods in `TermuxApiReceiver` (embed this list)

AudioInfo, BatteryStatus, Brightness, CameraInfo, CameraPhoto, CallLog, Clipboard, ContactList, Dialog, Download, Fingerprint, InfraredFrequencies, InfraredTransmit, JobScheduler, Keystore, Location, MediaPlayer, MediaScanner, MicRecorder, Nfc, Notification / Channel / Remove / Reply / List, SAF, Sensor, Share, SmsInbox, SmsSend, SpeechToText, StorageGet, Telephony*, TextToSpeech, Toast, Torch, Usb, Vibrate, Volume, Wallpaper, Wifi*.

Permission-gated examples (runtime, via `TermuxApiPermissionActivity`): `CAMERA`, `READ_CALL_LOG`, `READ_CONTACTS`, `TRANSMIT_IR`, `ACCESS_FINE_LOCATION`, `RECORD_AUDIO`, plus `WRITE_SETTINGS`, Notification Listener access, etc.

Manifest-declared dangerous/special permissions in termux-api (must be copied **into VictorSuite** if we embed): location (fine/coarse/background), body sensors, call phone, camera, read call log, read contacts, read phone state, read SMS, **send SMS**, record audio, NFC, IR, biometric, wallpaper, wifi change, `MANAGE_EXTERNAL_STORAGE`, `SYSTEM_ALERT_WINDOW`, `WRITE_SETTINGS`, `DUMP`, `PACKAGE_USAGE_STATS`, `READ_PRIVILEGED_PHONE_STATE`, install/delete packages.

Hardware features in termux-api are all `required="false"`. Copy that pattern. Do **not** add these permissions to RootForge.

### 5.4 Why “just compile termux-api into `:app`” is not enough

The helper always targets **package `com.termux.api`**. VictorSuite’s `applicationId` must stay **`com.termux`**. An APK cannot have two applicationIds.

Component name if we keep class `com.termux.api.TermuxApiReceiver` inside applicationId `com.termux`:

```
com.termux/com.termux.api.TermuxApiReceiver
```

not

```
com.termux.api/.TermuxApiReceiver
```

Stock `pkg install termux-api` will keep broadcasting into a package that does not exist (unless the user also installed official Termux:API — which will not share UID with VictorSuite’s signing key). Embedding **requires a helper retarget**, not only Java sources in the APK.

### 5.5 Recommended embed design (VictorSuite)

**Library module, not a second APK.**

```
:termux-api-lib     # sources from termux/termux-api (GPLv3), android.library
                    # keep Java package com.termux.api.* to minimise drift
:app                # registers the receiver/services/activities in the VictorSuite manifest
```

Prefer a **git submodule** of `termux/termux-api` at a pinned tag (start at `v0.53.0`) plus a thin overlay (toggle, component enablement, helper install).

**Manifest (VictorSuite `:app`):**

- Merge termux-api’s `<uses-permission>` and optional `<uses-feature>` (all `required="false"`).
- Register `com.termux.api.TermuxApiReceiver` (`exported="false"`), permission/dialog/NFC/SAF activities, `KeepAliveService`, job/notification services, `ShareAPI$ContentProvider` authority `${TERMUX_PACKAGE_NAME}.sharedfiles`.
- **Do not** add a second LAUNCHER activity. Settings toggle is the surface.
- Receiver and related components start **disabled** (`android:enabled="false"`).

**Helper retarget:**

Ship a small overlay into `$PREFIX` when the toggle turns **on**:

1. **Preferred:** a patched `termux-api` binary (fork of MIT `termux-api.c`) with `-n com.termux/com.termux.api.TermuxApiReceiver`. Keep listen address `com.termux.api://listen` if `SocketListener` still binds that name.
2. **Fallback:** a `termux-api` **shell shim** that execs the same `am broadcast`. More fragile (`SCM_RIGHTS` for `termux-usb`). First milestone for non-FD APIs only.

On toggle **on:** enable components, start `SocketListener` (gate today’s unconditional `TermuxAPIApplication.onCreate`), install helper, prompt that Android will ask per command. Do **not** bulk-request every dangerous permission.

On toggle **off:** disable components; stub helper with a clear error; `revokeSelfPermissionsOnKill` on API 33+; deep-link to system Settings on older APIs and for special grants (Notification Listener, `WRITE_SETTINGS`, `SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE`). Do not uninstall `pkg`’s MIT scripts.

**Gate inside `TermuxApiReceiver`:** `doWork()` returns JSON error when the toggle pref is false.

**Do not ship a hidden `com.termux.api` APK.** Rewrite `TermuxUtils.isTermuxAPIAppInstalled()` and the Settings row to mean “embedded API enabled.” Detect official `com.termux.api` and explain the signature/UID mismatch.

### 5.6 Play Store and permission-policy risk

**Plan both suite APKs as sideload / GitHub Releases. Do not plan a Play listing for the full VictorSuite APK.**

1. Termux itself left Play (downloaded binaries, `REQUEST_INSTALL_PACKAGES`, `$PREFIX`).
2. SMS + call log (`SEND_SMS`, `READ_SMS`, `READ_CALL_LOG`, `CALL_PHONE`) trigger Play’s SMS/Call Log policy. A terminal is not an exception.
3. A Settings toggle does **not** help Play — review is on manifest-declared permissions.
4. RootForge is a **separate** APK, so it does **not** inherit VictorSuite’s SMS/call-log surface. That is a benefit of the split. RootForge still should not target Play if it later declares `VpnService` (§13.2.1).

VictorSuite already declares `READ_SMS` and `READ_CONTACTS`. Embedding Termux:API **adds SEND_SMS and CALL_PHONE**.

**If a future “Play-safe” VictorSuite flavour is wanted (open decision):** omit SMS/Call/Camera/Mic/Location APIs. Default distribution remains sideload.

F-Droid: possible in principle for Termux:API, but VictorSuite is private, uses a committed keystore, Aliyun Maven, and non-free blobs. Later owner decision, not a suite milestone.

### 5.7 Licensing of the embed

- Termux:API **app sources = GPLv3**. Copying them into this GPLv3 APK is compatible. Keep headers; add Termux:API to `LICENSE.md`; pin the upstream commit.
- `termux-api-package` helper/scripts = **MIT**. Keep the MIT notice on the overlay.
- RootForge `:core` is **not** required inside VictorSuite for the embed. If VictorSuite later depends on `:core` for shared schema constants, RootForge must have a GPLv3-compatible license first (§9).

---

## 6. Overlap and conflicts (suite split, not a merge)

| Topic | VictorSuite | RootForge | Suite choice |
|---|---|---|---|
| `applicationId` | `com.termux` (load-bearing) | `ai.rootforge.os` / `.debug` | **Keep both.** They coexist. DCA binds by transport, not by collapsing packages. |
| minSdk | 23 | 26 | **Keep both.** No Compose on VictorSuite. |
| targetSdk | 28 | 35 | **Keep both.** This is why there are two APKs. |
| compileSdk | 36 | 35 | Independent. |
| USB host feature | `required=true` (mass storage) | `required=true` (ADB/fastboot/EDL) | VS → **`required=false`** (standalone bugfix). RF may keep required USB host or also flip to false so the admin/client-only phone still installs. |
| USB use | libaums MSC (`OTGManager`) | Bulk USB ADB/fastboot/Sahara | **Different apps, different jobs.** A cable on a phone that has both: VS offers “Use as storage”; RF offers “Use as Android target.” Do not auto-claim every device in both. |
| ADB | `libadb.so` + tananaev; wireless ADB to **this** phone | Pure-Kotlin USB ADB to a **second** phone | Keep both. Label VS “ADB this device / wireless”; RF “USB target phone.” |
| Module “install” | ZeroTermux Termux-module zip | Magisk/KernelSU/APatch zip builder + flash on **target** | Different. No rename collision across apps. |
| Terminal | Full VT100 `TermuxActivity` | Log panel + command box | Termux remains the Linux environment. RF Logs stay a toolkit console. Remote PTY in admin/client is RF-owned UI talking to a VS session service when VS is installed (§12.2). |
| Compose vs Views | AppCompat / data binding | Compose Material 3 | **No hybrid APK.** Handoff is an Intent, not a `ComposeView` inside TermuxActivity. |
| INTERNET | Yes | No today | VS keeps INTERNET. RF adds INTERNET when admin/client or Tailscale ships. Toolkit flash code still must not use the network. |
| droidcommand | `RUN_COMMAND` / `core-termux` | Messenger bridge / `core-rootforge` | **Both stay.** One JSON envelope in `:core` (§11). |
| Signing | `phone.jks` (committed) | CI env keystore | Matching **publisher** lineage after owner approval on the leaked VS key (§2.1). Never share a key with DCA. |
| Public vs private | Private | Public | Do not move Termux sources into public RootForge. Publish `:core` from RootForge; publish VictorSuite APK from this repo. |
| Headscale / Tailscale | Drawer `HeadscaleClickConfig` (unpinned userspace) | `join_headscale.sh` on ISO/chroot | **RF Settings → Remote** is the admin/client mesh. Pin binaries. VS drawer becomes a handoff or a Termux-only proxy. |
| Gradle style | Groovy, duplicated deps | Kotlin DSL | Dedupe VS first (§2.1). RF Gradle stays in the public repo. |

---

## 7. Recommended target architecture

### 7.1 Which repo is home

| Deliverable | Home repo | Why |
|---|---|---|
| VictorSuite APK (terminal, proot, X11, plugins, backup, Termux:API) | **VictorSuite (this repo)** | GPLv3 Termux sources, `com.termux`, `targetSdk` 28, `RUN_COMMAND`, private catalogues. |
| RootForge APK (USB toolkit + admin/client + Tailscale/Headscale) | **RootForge OS (public)** | Already `ai.rootforge.os`, `targetSdk` 35, Compose toolkit, USB stack. |
| Kotlin `:core` (schema, planner, approvals, `peer.*` names) | **RootForge OS (public)** | Shared library for RF Android, RF desktop, and (optionally) VS schema constants / DCA. |
| Python CLI, bash scripts, ISO, Termux rootfs | **RootForge OS (public)** | `git clone` without either APK. |
| Termux:API Java + helper overlay | **VictorSuite** | Must live in the `com.termux` APK. |
| Compose desktop + Docker API | **RootForge OS** | PC-first. Not built by VictorSuite CI. |

Do **not** copy RootForge’s Python/ISO tree into VictorSuite.  
Do **not** vendor 89k LOC of Termux into RootForge.  
Do **not** `includeBuild` RootForge `:app` into VictorSuite — that re-creates the one-APK problem.

### 7.2 Module layout

```
VictorSuite (private)                         RootForge OS (public)
├── :terminal-emulator                        ├── android-app/
├── :terminal-view                            │     :core            # shared JVM library (publish or submodule)
├── :termux-shared                            │     :app             # ai.rootforge.os — toolkit + peer + Remote
├── :termux-api-lib   (later)                 │     :desktop         # PC-first, same :core
├── :app              # com.termux            ├── CLI / ISO / termux/ / docker/
└── docs/SUITE_PLAN.md                        └── (optional) published :core Maven coordinate

droidcommand-ai (separate)
├── core-termux       → RUN_COMMAND on com.termux
└── core-rootforge    → Messenger ai.rootforge.os.action.BRIDGE
         both speak the same JSON envelope from :core
```

Optional later split of RootForge `:app` (in the **public** repo, for desktop cleanliness — not so VictorSuite can absorb UI):

| Module | Contents |
|---|---|
| `:core` (exists) | Protocol, planner, module zip, approvals, library, `LinkState`, `peer.*` op table |
| `:toolkit-ui` | Compose screens (Android **and** desktop) |
| `:toolkit-android` | USB, `RootForgeBridgeService`, peer listen, Tailscale/Headscale glue |
| `:desktop` | Unchanged; `:core` + `:toolkit-ui` |

VictorSuite `:app` depends on **`:core` only if needed** for shared schema constants / deep-link extras. It does **not** depend on `:toolkit-android`.

### 7.3 Package names and components

| Item | Value |
|---|---|
| VictorSuite `applicationId` / `sharedUserId` | **`com.termux`** (unless the owner approves a rename — §2.1 / §16) |
| RootForge `applicationId` | **`ai.rootforge.os`** (debug `.debug`) |
| VictorSuite launcher | **VictorSuite** until the owner picks a suite brand |
| RootForge launcher | **RootForge** (own LAUNCHER — this is a real second app) |
| Handoff | `ai.rootforge.os.action.OPEN` (or documented `https` / custom-scheme deep links) with `destination` extra (`adb`, `fastboot`, `edl`, `peer`, `remote`, …) |
| Bridge service | Stays in **RootForge**: action **`ai.rootforge.os.action.BRIDGE`** |
| Termux:API receiver | VictorSuite only, disabled by default |
| `RUN_COMMAND` | Unchanged on VictorSuite (`com.termux.permission.RUN_COMMAND`) |

### 7.4 How shared Kotlin `:core` is consumed

```
                    ┌─────────────────┐
                    │  :core (JVM)    │  flash plan, module zip, bridge
                    │  kotlinx.json   │  protocol, approvals, peer.* names
                    └────────┬────────┘
          ┌──────────────────┼──────────────────┐
          v                  v                  v
   RootForge :app      :desktop          optional VS
   (USB, peer,         + toolkit-ui      schema constants /
    Tailscale)                           deep-link extras
          v                  v
   ai.rootforge.os     .deb / .exe       com.termux
   APK                 (RF repo)         APK (this repo)
```

Consumption options (open decision): **publish `:core` to Maven** (GitHub Packages / Maven Central once licensed) **or** git submodule / `includeBuild` of `rootforge-os/android-app` pinned to a commit. Prefer a published artifact once CI exists so VictorSuite does not clone the ISO tree.

Shell/Python tooling is **not** compiled into either APK. VictorSuite may offer “Install RootForge CLI into this environment” → `git clone` + `dev-env.sh` (convenience, not required). RootForge USB workflows stay in Kotlin; do not exec `rootforge` from the APK for flashes.

Longer term, destructive rules stay one contract (typed `FLASH <serial>`, vendor refuse list). Kotlin `FlashPlan` and Python `device.py` are **not** the same code today. Document a mapping; do not silently drift.

### 7.5 Termux-native CLI distribution (unchanged requirement)

Keep RootForge public. Document **two** paths in RootForge `README.md` **and** a short `docs/TERMUX_CLI.md` linked from VictorSuite:

**A. Git checkout (no APK, no GitHub Release) — must remain first-class**

```sh
# Inside Termux or VictorSuite
pkg install git python android-tools
git clone https://github.com/Victorious93/rootforge-os.git
cd rootforge-os
export PYTHONPATH="$PWD/config/includes.chroot/usr/local/lib${PYTHONPATH:+:$PYTHONPATH}"
export PATH="$PWD/config/includes.chroot/usr/local/bin:$PATH"
rootforge doctor
rootforge devices
```

`common.sh` / `runner.py` already support this. Add a one-line `termux/dev-env.sh` that exports those two variables. **Do not** require `proot-distro` or a multi-GB rootfs for `doctor` / `module scaffold` / host-side `adb`.

**B. Released Debian rootfs (when `release.yml` actually publishes)**

Existing `install.sh` + `proot-distro install rootforge` / `rootforge-chroot.sh`. Unchanged.

Neither suite APK is a gate for (A) or (B).

### 7.6 Deep link / “Open in RootForge” handoff

VictorSuite drawer entries that used to be “merge the toolkit into this APK” become **intents**:

```
TermuxActivity (drawer — existing MainMenuConfig)
├── (existing ZeroTermux categories — unchanged)
└── NEW category CODE_SUITE = "RootForge"
    ├── Open RootForge                 → MAIN on ai.rootforge.os
    ├── ADB (USB target)               → OPEN destination=adb
    ├── Fastboot / EDL / Recovery / …
    ├── Admin / Client                 → OPEN destination=peer
    └── Remote (Tailscale / Headscale) → OPEN destination=remote
```

If `PackageManager` cannot resolve `ai.rootforge.os` (or `.debug` during development): a dialog “RootForge is not installed” + link to the sideload/release URL. Do not crash the terminal.

RootForge may offer the reverse for terminal work: “Open in VictorSuite” → `com.termux` `MAIN` or a documented extra (create session / run command). That is how a remote-admin flow that needs a real PTY reaches Termux without embedding it.

**Matching branding:** same product wordmark / colour tokens in both launchers and the handoff dialogs. **Matching signing:** same publisher certificate lineage (after §2.1), so Android’s “same developer” story is honest. Not `sharedUserId`. Signature permission across the two apps is allowed for a private suite IPC (PTY session service) **if** both are signed with that lineage; do not use a signature permission that would also bind DCA.

---

## 8. Licenses and GPLv3 implications

| Component | License | Effect on the suite |
|---|---|---|
| Termux app + ZeroTermux + VictorSuite | **GPLv3 only** | The VictorSuite APK is a GPLv3 binary. Corresponding source for everything **linked into it**. |
| terminal-emulator / terminal-view | Apache-2.0 | Compatible. |
| Termux:API app sources | GPLv3 | Compatible in VictorSuite; keep notices. |
| termux-api-package helper | MIT | Compatible; patched overlay OK. |
| RootForge OS | **None today** | **Cannot publish `:core` (or ship it inside any GPLv3 APK) until RootForge has a GPLv3-compatible license.** |
| Compose / AndroidX / kotlinx | Apache-2.0 | Compatible. |
| Tailscale OSS client | typically BSD-3-Clause | Compatible in RootForge if notices kept (§13.2.1). |
| Headscale server | BSD-3-Clause | Not bundled; clients only. |

**What GPLv3 does *not* force:**

- Two APKs are **separate works** if they only talk by Intent / documented IPC and do not link each other’s GPLv3 objects. VictorSuite does not become a RootForge derivative merely because it deep-links. If VictorSuite **links** `:core`, `:core` needs a GPLv3-compatible license and VictorSuite’s corresponding source includes that version of `:core`.
- The **Python CLI and bash scripts**, distributed as source from a public git clone, can remain a separate work. They still need an explicit license.
- The **desktop app** links `:core` + Compose UI, **not** Termux. If `:core` is Apache-2.0, the desktop can be Apache-2.0. If `:core` is GPLv3, the desktop is GPLv3 too. Recommendation: license RootForge **GPL-3.0-or-later** for simplicity, **or** Apache-2.0 for `:core`/desktop and GPL-3.0 for scripts.
- Private VictorSuite remaining private is allowed under GPLv3 **until you distribute the APK**. Recipients of the APK are entitled to corresponding source (including Termux:API and any linked `:core`).

**Do not** relicense Termux or Termux:API. **Do not** keep RootForge unlicensed.

---

## 9. UI / navigation

**Two launchers.** Terminal stays home **in VictorSuite**. Toolkit and peer stay home **in RootForge**.

```
VictorSuite                              RootForge
TermuxActivity                           RootForgeUi (Compose)
├── existing ZeroTermux drawers          ├── Dashboard / ADB / Fastboot / EDL
└── RootForge (handoff)                  ├── Root / Patching / Module builder
      └── Open in RootForge →            ├── Recovery / Logs (USB)
                                         ├── Admin / Client          (§12)
                                         │     ├── Role: Off | Client | Administrator
                                         │     ├── Pair (QR / PIN) / Revoke
                                         │     ├── Remote terminal
                                         │     └── Client capabilities
                                         └── Settings
                                               ├── DCA pairing, node id, USB RSA
                                               └── Remote (Milestone 8)
                                                     ├── Connect Tailscale account
                                                     ├── Headscale URL + preauth key
                                                     ├── Mesh status / peer hostname
                                                     └── USB + plain LAN never require this
```

VictorSuite Settings: Termux:API toggle (default off), explainer, permission status, system-Settings deep-link. Upstream Settings row uses the same toggle (do not hide when `com.termux.api` is missing).

USB attach: VS handles MSC; RF handles ADB/fastboot/QCOM 9008. A phone with both installed should not auto-claim in both apps.

Do not merge RootForge’s Logs panel with `TermuxActivity`.

---

## 10. droidcommand-ai continuity

Today (from VS README + RootForge `docs/DROIDCOMMAND_INTEGRATION.md`):

| Path | Role | Transport |
|---|---|---|
| `core-termux` | Treats VictorSuite as Termux | `RUN_COMMAND` intent, package `com.termux` |
| `core-rootforge` | Independent node, not a companion | SSH (passive) and Android **Messenger** `ai.rootforge.os.action.BRIDGE` |

**Suite (two packages, one schema):**

1. **Keep `RUN_COMMAND` byte-for-byte** on VictorSuite. `core-termux` still cannot distinguish VictorSuite from Termux. Desired.
2. **Keep the RootForge bridge** on **`ai.rootforge.os`**. Same action, same pairing, typed confirmations. Do **not** move `RootForgeBridgeService` into `com.termux`.
3. **One JSON envelope in `:core`:** `BridgeRequest` / `BridgeResponse`, `BRIDGE_PROTOCOL_MAJOR = 1`, `ErrorCategory`, `Tier`. Operation **names** stay namespaced: `rootforge.*` (USB target on this host), `peer.*` (admin/client), Termux is not a `rootforge.*` op.
4. **DCA change (small, in droidcommand-ai):** speak that envelope to **both** transports. Bind `core-rootforge` by **action** (already `ai.rootforge.os.action.BRIDGE`) to `ai.rootforge.os` / `.debug`. Do not also look for the bridge inside `com.termux`. `core-termux` stays package `com.termux`. Capabilities payloads include `app_id` / `app_name` so DCA can label “VictorSuite” vs “RootForge.”
5. **Independence:** turning DCA off, or never pairing, must not disable terminal, toolkit GUI, Termux:API toggle, or peer listen.
6. Do **not** add a `BIND_ROOTFORGE` signature permission that DCA would need (RootForge docs already forbid sharing a signing key with DCA).
7. ZT TCP 19951 is a **third** control plane (LAN JSON → shell). Out of scope for DCA. Do not wire it to the bridge.
8. **Admin/client (§12) is a fourth control plane**, phone-to-phone, both running **RootForge**. It reuses the bridge JSON envelope and tier model but is **not** DCA and **not** `rootforge.adb.*`. DCA must not chain through an admin onto a client unless both sides opt in (default: no chaining).

SSH `rootforge bridge` on a Debian/Termux rootfs node is unchanged (CLI lives in RootForge).

---

## 11. Shared command schema (the suite contract)

Put the wire types in `:core` so Android, desktop, Docker, and DCA do not invent a second JSON shape.

```json
{
  "protocol_major": 1,
  "request_id": "…",
  "target_node_id": "rf-…",
  "op": "rootforge.devices.list",
  "timeout_ms": 15000,
  "params": {}
}
```

| Namespace | Who executes | Meaning |
|---|---|---|
| `rootforge.*` | RootForge on **this** host | USB target / planner / module zip / existing bridge ops |
| `peer.*` | RootForge on the **client** phone | Admin/client (§12) |
| Termux / `RUN_COMMAND` | VictorSuite | Not a `rootforge.*` name. DCA `core-termux` stays an Intent, not this Messenger. Optional later: `suite.termux.run` as a RootForge→VictorSuite handoff op for admin/client only |

`:core` owns the envelope, error categories, and the op **catalog** (name, tier, which app implements it). Each APK refuses ops it does not implement (`unknown_op`). Desktop can be an Administrator later without Termux on the PC.

---

## 12. Admin / Client dual-phone mode (RootForge-owned)

Two phones **both run the RootForge APK**. The owner sets one as **Administrator** and the other as **Client**. Control is **one-way**: only the admin sends operations; the client executes (after pairing and prompts) and returns results. The client never gains a control session on the admin.

VictorSuite is **optional** on each phone. When the **client** also has VictorSuite, user-level shell, a live PTY, and Termux:API become available via suite IPC. When it does not, the admin sees toolkit-adjacent capabilities only (no `prefix_ready`, no `termux_api`, no Termux PTY).

This is **not** the USB toolkit. Keep the stories distinct:

| Mode | Who runs which APK | What the far device is | Plane |
|---|---|---|---|
| **Toolkit host → target** (today’s RootForge `:app`) | Host runs RootForge | Any Android phone (ADB / fastboot / EDL). May be bricked, no app required | Kotlin USB wire protocol |
| **Admin → client** (this section) | **Both run RootForge** | A live RootForge install; VictorSuite optional for Termux | App TLS protocol (`peer.*`). USB-C is a **tunnel**, not raw `adb shell` |
| **OTG mass-storage** (VictorSuite `OTGManager`) | This phone runs VictorSuite | USB flash drive | libaums |

Conflating toolkit `rootforge.adb.shell` with admin remote-shell would skip the client’s Termux:API toggle, root prompts, and revoke — because `adbd` is uid `shell` and does not consult either app.

### 12.1 Roles and capability detection

RootForge Settings → **This phone’s role**: `Off` (default) | `Client` | `Administrator`. A phone cannot be both. Switching to Client starts the listen path (LAN and/or USB tunnel). Switching to Administrator does **not** start a server.

On connect, the admin calls `peer.capabilities.get` (read tier). The client answers with what is **true right now**:

```json
{
  "node_id": "rf-…",
  "role": "client",
  "protocol_major": 1,
  "app_id": "ai.rootforge.os",
  "suite_victorsuite": true,
  "rooted": false,
  "su_manager": "none",
  "su_granted": false,
  "termux_api": false,
  "prefix_ready": true,
  "operations": ["peer.capabilities.get", "peer.shell.user", "peer.session.open"]
}
```

| Flag | How | Do not |
|---|---|---|
| `suite_victorsuite` | Client `PackageManager` sees `com.termux` signed by the suite publisher (after matching signing) **or**, until then, simply installed. | Do not treat “any Termux” as VictorSuite if you need the embed toggle. |
| `rooted` / `su_granted` | **libsu on RootForge** (add the dependency to RF `:app`). Treat as rooted only if a shell can run `id`, uid is 0, **and** the user granted **RootForge** su. | Do not trust RootBeer. Do not assume VictorSuite’s `LibSuManage` grant applies to RootForge (different UID). |
| `su_manager` | Probe Magisk / KernelSU / APatch. | Don’t advertise `peer.shell.root` if `su` exists but RootForge is denied. |
| `termux_api` / `prefix_ready` | Ask VictorSuite over suite IPC (exported-to-signature session service or documented extras). Toggle from §5, not `isTermuxAPIAppInstalled("com.termux.api")`. | Don’t list camera/SMS ops if the VS toggle is off. If VS is absent, both flags are false and those ops disappear. |

The admin UI **hides** actions absent from `operations[]`. Re-fetch on reconnect, on VS toggle change (`peer.event.capabilities`), and after su grant/deny.

Never show fastboot/EDL/unlock on the peer screen (wrong mode).

### 12.2 Command schema (`peer.*` on the shared envelope)

Reuse `:core` `Protocol.kt`. Do **not** invent a second JSON shape. Do **not** reuse `rootforge.*` names.

| Name | Tier | Meaning |
|---|---|---|
| `peer.capabilities.get` | read | Flags + allowed op list above |
| `peer.session.open` / `.resize` / `.close` | control | Remote PTY. **Preferred:** RootForge on the client binds to a VictorSuite session service and forwards bytes (JSON control, length-prefixed PTY data). Admin renders in RootForge’s Logs/terminal pane (do **not** pull `:terminal-view` into RootForge). If VS is missing: `unavailable`. |
| `peer.shell.user` | control | One-shot user shell. **Preferred:** RootForge → VictorSuite `RUN_COMMAND` / session service in `$PREFIX`. Fallback if VS missing: open decision (deny vs a limited RF `sh`). Recommendation: **deny** (`prefix_not_ready`) so “remote shell” means Termux, not a second environment. |
| `peer.shell.root` | **destructive** + **root grant** | libsu on **RootForge**. Client prompt the first time (and if admin pairing id rotates). |
| `peer.termux_api.invoke` | control | `api_method` + extras, dispatched **through VictorSuite’s** embedded receiver (IPC). Fails if VS missing or toggle off. Sensitive methods need an extra client prompt. |
| `peer.pairing.revoke` | control (client local UI only) | Not callable by the admin. |

Same gates as `BridgeExecutor.process()`, in order: size cap → well-formed JSON → `protocol_major` → `target_node_id` is this client → **role is Client and listen enabled** → caller is the **paired admin identity** (TLS cert, not Android UID) → op in the advertised list → parameters → tier prompt → execute under `timeout_ms`.

`ApprovalCenter` on the **client** for `peer.shell.root` and sensitive Termux:API methods. Typed `FLASH` stays on the toolkit path; root peer ops use Allow/Deny (+ optional typed `ROOT`).

Put the op table in `:core`. The **listen server** stays in RootForge `:app`. The **PTY** stays in VictorSuite. That split is what keeps PC-first possible: a desktop Administrator talks `peer.*` to a phone client without Termux on the PC.

### 12.3 Transports

One protocol, two pipes. The client is always the TLS **server**. The admin is the TLS **client**.

**Wi-Fi / LAN (primary for v1)**

- Client binds `127.0.0.1` plus, only if “Allow LAN” is on, the LAN address (not `0.0.0.0` on cellular by default).
- TLS 1.3, per-install keypair (AndroidKeyStore).
- Discovery: optional mDNS (`_rootforge-peer._tcp` recommended — the listen app is RootForge; open decision vs `_victorsuite-peer._tcp`) advertising `node_id` + pairing-token **hash**. Manual `IP:port` always works.
- **Do not** extend VictorSuite `ZTSocketService` (port **19951**, cleartext, no auth). Leave it as a legacy VS feature or ask VS to disable it while Peer is on (handoff extra). Open decision whether to deprecate 19951.

**USB-C (ADB as a tunnel only)**

RootForge on the **admin** already has USB Host ADB:

1. Client has USB debugging on; accepts the RSA dialog.
2. Admin `AdbConnection` runs `adb reverse` / `forward` to the client’s **localhost TLS port**.
3. Admin opens TLS to `127.0.0.1:<peer>` — same pairing as Wi-Fi.
4. If USB debugging is off, this path is unavailable. Client need not be rooted.

Do **not** v1: USB gadget, AOA, or “admin types into `adb shell`.”

A cable should ask (in RootForge): “Android target (flash/ADB toolkit)” vs “Paired client (this app).” VictorSuite, if in the foreground, may still offer MSC — different app.

### 12.4 Pairing, auth, encryption, revoke, audit

**Pairing (always approved on the client, in person):**

1. Client role on, shows a rotating QR (node_id, TLS SPKI pin, short-lived pairing secret, LAN IP) **and** a 6–8 digit PIN.
2. Admin scans QR or types PIN + client address.
3. Client UI: “Allow **this** Administrator (pin `sha256:…`) to control this phone? They will get: [user shell if VS], [Termux:API if VS toggle on], [root if RF su granted].” Default deny. Timeout deny.
4. On allow: store `PairedAdmin { nodeId, spkiPin, pairedAtMs, grants }` in a **peer pairing store** (TLS SPKI, not Android package+cert). Do not overload DCA `PairingStore`.
5. Mutual TLS; later cert change is `CERT_CHANGED` / re-pair.

**QR/PIN risks:** SAS fingerprint compare (both humans). PIN rate-limited; secret expires in minutes.

**Per-capability grants:** pairing is not blanket root. `shell.root` starts unset. Sensitive Termux:API methods have grant bits. Admin UI never shows a button the grant list + capabilities would refuse.

**One-tap revoke (client):** RootForge Settings → Peer → Revoke. Drops TLS, deletes the paired admin. Does not disable VictorSuite’s Termux:API toggle or either app’s su grant. Works offline.

**Audit log (client, append-only JSONL):** `peer_audit.jsonl` on RootForge (same rotate-style as VictorSuite `PluginLogger`). Pairing, revoke, each `peer.*` (name, admin node_id, tier, result category — **not** full transcripts or secrets). No remote wipe. If an op was handed to VictorSuite, RF logs the invoke; VS may also log in its own plugin/API audit — do not try to unify files across UIDs.

### 12.5 Relation to droidcommand-ai

| Controller | Bound how | Can it drive a **peer client**? |
|---|---|---|
| DCA `core-termux` | `RUN_COMMAND` on **this** phone’s VictorSuite | No. Stays on-device Termux. |
| DCA `core-rootforge` | Messenger on **this** phone’s RootForge | **Default no.** Ops remain `rootforge.*`. |
| Admin phone | TLS `peer.*` | Yes, after client pairing. |
| DCA chained via admin | — | **Forbidden** unless a future explicit grant exists on **both** sides. No `peer.relay` in v1. |

Identity systems stay separate: Messenger pairing is Android UID → package + cert; peer pairing is TLS SPKI + `node_id`. DCA is not a `PairedAdmin`. Desktop as Administrator is a TLS admin, not `ROOTFORGE_API_TOKEN`.

### 12.6 Security risks (this feature)

Highest-risk feature in the suite: remote shell + optional `su` + optional SMS/camera (via VS). Treat it like a product-shaped RAT unless defaults stay off.

| Risk | Why it is real | Mitigation |
|---|---|---|
| Cleartext LAN shell | VS `ZTSocketService` :19951, no TLS/auth | Do not reuse it. Peer listen default **off**. |
| Raw `adb shell` as the remote terminal | RF USB ADB is built to run shell on a target | USB is tunnel-only. |
| Reverse control | Symmetric protocol | Admin does not bind the peer server. Client rejects `peer.*` unless `role==client`. |
| Cross-app confuse | VS and RF both on the client | Signature-checked IPC; VS refuses session binds from non-suite signers. |
| Pairing phishing / QR | User scans a QR from chat | SAS; short-lived PIN; client Allow screen. |
| MITM on LAN | mDNS / “enter IP” | TLS 1.3 + pinned SPKI; first pairing is TOFU plus human SAS. |
| Two su grants | VS `LibSuManage` vs RF libsu | Capabilities report **RF** grant for `peer.shell.root`. |
| Termux:API remotely | VS embed | Extra per-method grants; toggle off ⇒ ops disappear. Prefer **not** exposing SmsSend/Camera remotely in v1. |
| Play/OEM “remote control” heuristics | RF + INTERNET + USB + listen | Sideload; feature off by default. SMS stays on VS, not RF. |
| Cellular listen | Public IP / CGNAT leak | LAN-only default; cellular bind opt-in, warned. |

v1 exit criteria: unauthenticated connect refused; revoke drops session; role Off accepts nothing.

Internet-scale threats are **out of v1** and specified in §13. Do not add a cloud listen URL, TURN, or “works over LTE” into the USB/LAN milestone.

---

## 13. Remote / Internet control (RootForge-owned)

§12 is **same-link**: USB-C or the same LAN. This section is everything **beyond** that. The `peer.*` schema, one-way roles, capability advertisement, and pairing store do **not** change.

**Phasing (hard):** ship USB + same-LAN first (Milestone 7). Internet remote is Milestone 8. Do not put a public signaling hostname in either APK “just in case.”

Neither repo implements a relay today. What *does* exist:

| Existing | Use for internet remote? |
|---|---|
| RootForge USB ADB / `AdbConnection` | No. Local USB only. |
| VS `ZTSocketService` :19951 | **No.** Cleartext, no auth. |
| Docker `HeadlessApi` / `ROOTFORGE_API_TOKEN` on `127.0.0.1:8787` | Pattern only. Not a phone-to-phone relay. |
| VictorSuite **Headscale / Tailnet** drawer | Implementation **reference** (userspace, SOCKS 1055). Product surface moves to **RootForge Settings → Remote**. |
| RootForge `join_headscale.sh` | Same idea on Debian ISO / chroot; `curl\|sh` is a known smell. Pin before reuse. Not in the PRoot flavour. |

### 13.1 Same LAN (Milestone 7 — contract)

Internet remote must **keep**:

1. **Discovery:** mDNS advertising `node_id` + pairing-token **hash** only. Manual `IP:port` always works.
2. **Pairing:** QR or PIN, approved on the **client**, SAS, rate-limited PIN.
3. **Transport:** TLS 1.3, mutual auth, **pinned device keys**. Admin is TLS client; client is TLS server.
4. **Capabilities:** `peer.capabilities.get` as in §12.1.

USB-C remains the ADB **port tunnel** to that same localhost TLS server.

### 13.2 Remote over the internet (Milestone 8)

NAT/CGNAT mean the client usually cannot accept a public TCP connect. Three families; pick **one** before writing code. They share the same `peer.*` TLS payload.

```
                    paired earlier on LAN/USB
   Admin phone                              Client phone
   (RootForge, TLS client)                  (RootForge, TLS server)
         |                                          |
         |  1. session token / signaling            |
         |  2. hole-punch or join VPN               |
         |  3. E2E TLS (relay sees ciphertext)      |
         v                                          v
   ┌─────────────────────────────────────────────┐
   │  A mesh VPN   or   B signaling+TURN         │
   │  (Headscale/Tailscale/WireGuard)            │
   │  or C owner-hosted reverse-proxy with E2E   │
   └─────────────────────────────────────────────┘
```

**Architecture A — Tailscale / Headscale mesh (preferred internet path).**  
Optional in-app support **in RootForge**: both phones join the same tailnet, overlay IPs make the client look like LAN, then Milestone 7 `peer.*` TLS runs unchanged (`100.x.y.z:peer`). **USB-C and plain same-LAN admin/client must keep working with Tailscale completely off / uninstalled.** Details: **§13.2.1**.

This is the recommended M8 default because VictorSuite already proved userspace Tailscale in-process and RootForge already has `join_headscale.sh`. Architectures B (TURN) and C (VPS reverse tunnel) stay fallbacks if the owner rejects a mesh.

**Architecture B — discovery/signaling + optional TURN.**  
Signaling issues **short-lived session tokens**. Phones attempt P2P; if NAT wins, both connect to a **TURN/relay** that forwards opaque bytes. Relay must not terminate `peer.*` TLS. Signaling sees metadata only.

**Architecture C — reverse tunnel to an owner VPS.**  
Client maintains an outbound TLS connection to a box the owner runs. Still **E2E-encrypt** to the phone.

**Do not** ship a Victorious-operated multi-tenant cloud in v1 of internet remote unless the owner explicitly chooses it.

**Session rules (all of A/B/C):**

| Rule | Behaviour |
|---|---|
| Pairing | Still happens in person on USB/LAN first. Internet does **not** replace QR/PIN. |
| New remote session | Client shows **Approve this session?** Timeout deny. |
| Short-lived session token | Dies on idle timeout, revoke, or wall-clock TTL (recommend 15–60 min idle, hard cap e.g. 12 h). |
| Always-on agent | **Off** by default. If enabled: RootForge foreground service + persistent notification, idle timeout, cellular warning. `targetSdk` 35 **requires** a real FGS type and a visible notification — another reason this lives in RootForge, not VictorSuite@28. |
| Kill switch / revoke | **Either side.** Works offline for the local half. |
| Capability advertisement | Same `peer.capabilities.get`. Path type (`usb` / `lan` / `vpn` / `relay`) is metadata. |
| Root over internet | Default **off** even if `su_granted` on LAN. |

### 13.2.1 Preferred internet path: optional Tailscale and Headscale

**Recommendation:** include **optional** in-app Tailscale (official coordination) **and** Headscale (self-hosted control plane) **in the RootForge app** as the preferred way for admin/client to work across the internet. They are not required to use VictorSuite, the USB toolkit, the git-clone CLI, or same-LAN peer control.

Neither binary is a shell relay. Tailscale/Headscale carry **WireGuard** (DERP relays encrypted WireGuard frames). `peer.*` still uses **device-pinned TLS inside** that overlay. Coordination servers and DERP **must not** see PTY/JSON. Do not add a Victorious “shell proxy” in front of this.

**UX — RootForge Settings → Remote** (only if role is Client or Administrator; default all off):

| Control | Behaviour |
|---|---|
| **Connect Tailscale account** | Official Tailscale login (OAuth / auth URL). Same account (or tailnet ACL that allows the two nodes) on admin and client. |
| **Headscale** | User pastes **login server URL** (`https://headscale.example.com`) **and** a **preauth key**. Matches today’s VictorSuite `HeadscaleClickConfig` dialog (URL required; key optional today for interactive login — for in-app Remote, **require the preauth key** so the terminal is not the login UI). |
| **Mesh status** | Connected / connecting / off; tailnet name; this node’s hostname and `100.x` address; last error. |
| **Peer phone hostname** | On the admin: list tailnet peers, highlight the paired client’s hostname/`node_id` when it is online on the mesh. Do not treat “seen on Tailscale” as paired — pairing is still USB/LAN QR/PIN. |
| **Disconnect** | Leaves the tailnet; USB and LAN peer keep working. |

USB-C and plain LAN **ignore this screen**. No Tailscale process, no SOCKS proxy, no `BIND_VPN_SERVICE` for those paths.

**How to implement (prefer library / userspace, fallback to the Tailscale app):**

1. **Preferred:** Tailscale **userspace networking** (`tailscaled --tun=userspace-networking`, SOCKS5/HTTP on `127.0.0.1:1055`) **or** the official **Tailscale Android library / libtailscale** if it can run without a second APK. Admin’s `peer.*` TLS client dials the client’s overlay IP **via that local SOCKS proxy**. No `VpnService` ⇒ no Android VPN consent sheet and a weaker Play VPN-policy surface.
2. **Fallback:** if `com.tailscale.ipn` (official Tailscale Android app) is installed and its VPN is up, detect it (`PackageManager` + overlay route / Tailscale local API if documented) and dial `100.x` directly. Do not force users to uninstall that app. Show “Using Tailscale app” vs “Using built-in userspace” in status.
3. **Do not** require the Tailscale APK for Headscale (the official app does not point at arbitrary Headscale URLs as a first-class in-app flow). Headscale stays URL + preauth key in **RootForge**.
4. **Pin** version + SHA-256. Stop fetching unpinned “latest” tarballs from `pkgs.tailscale.com` (current VS `HeadscaleClickConfig`). Same rule as RootForge hooks.
5. If VictorSuite’s userspace tailscaled is already running on the same phone, **do not start a second one**. Detect SOCKS 1055 / document a single owner (RootForge). VS drawer → “Open in RootForge.”

**Licensing**

| Piece | License / terms | APK effect |
|---|---|---|
| Tailscale open client (`tailscale/tailscale`, typically **BSD-3-Clause**) | Compatible with GPLv3 / Apache if notices are kept | OK to link/bundle in **RootForge if** the owner uses the OSS client, not a proprietary blob without a grant |
| Tailscale Android app / Play listing | Tailscale’s ToS for **their** coordination server | Using `controlplane.tailscale.com` is a **SaaS** relationship, not a license to rebrand Tailscale |
| Headscale (`juanfont/headscale`, **BSD-3-Clause**) | Self-hosted; compatible | RootForge is only a **client**; we do not bundle the Headscale **server** |
| Current VS download of static `tailscale`/`tailscaled` tarballs | Redistribution needs the BSD notice and a pin | Supply-chain: pin + checksum; move the implementation to RF |

BSD-3-Clause is GPLv3-compatible. Add notices to RootForge `LICENSE` once it exists. Do not strip Tailscale trademarks from their login UI.

**Play policy (VPN)**

- A real TUN (`VpnService` / `BIND_VPN_SERVICE`) on **RootForge** triggers Play’s VPN app policy. RootForge is already not Play-bound; declaring VPN makes a future Play flavour even less likely. VictorSuite stays free of this permission.
- Userspace-networking + SOCKS **avoids** `VpnService` but does **not** capture all device traffic — only what RootForge dials through 1055. That is what we want (shell stays in-app / suite IPC).
- Fallback to the official Tailscale APK puts VPN permission on **their** app, which is the cleanest Play story if the owner ever wants one.

**Open decision (also §16 #22):** **bundle userspace Tailscale** (pinned `tailscaled` or libtailscale inside **RootForge**) **vs require the Tailscale app** for official Tailscale, and only bundle userspace for Headscale. Recommendation: **bundle userspace for both** so Headscale works without a second APK; detect the official app as a fallback for Tailscale.com accounts. USB/LAN never depend on either. VictorSuite does not bundle a second copy.

### 13.3 Threat model and mitigations

| Threat | What happens | Mitigation |
|---|---|---|
| **Stolen admin phone** | Thief opens RootForge and reaches every paired client | Biometric lock **before** any `peer.*` connect; short session TTL; client “Approve this session”; keys in AndroidKeyStore. |
| **Malicious relay / VPN operator** | Headscale/TURN/VPS inspects or injects | Device-level TLS with pins from LAN pairing; no TLS offload; `CERT_CHANGED` / re-pair. Keep `peer.*` TLS **inside** the overlay. |
| **MITM** | Fake signaling, downgrade | No plaintext `peer.*`. Pins from in-person pairing. |
| **Runaway root commands** | Stolen admin issues `peer.shell.root` | Remote root grant separate from LAN; rate limits; typed `ROOT` on the client; optional admin biometric; idle timeout; audit. |
| **Always-on agent as implant** | Client listens 24/7 | Default off; un-hideable notification (natural on `targetSdk` 35); LAN-only unless Internet remote is enabled. |
| **Stolen client phone** | Thief disables revoke | Hardware-backed keys; admin revoke invalidates tokens; audit not remotely deletable. |
| **Token theft** | Token copied from logs | Short TTL; redact like RF already redacts `tskey-auth-*`. |
| **Audit split-brain** | Client offline, admin forges history | Canonical log is **on the client**. Admin cannot push entries back. |
| **Suite IPC abuse** | A third APK claims to be VictorSuite | Signature check; matching publisher key. Refuse unsigned / foreign `com.termux`. |

**Audit log synced when online:** client RootForge remains source of truth. Sync over the already-pinned TLS session only.

### 13.4 Phasing relative to the rest of the suite

| When | What ships | What is forbidden |
|---|---|---|
| Milestone 7 (after VS Termux:API so `termux_api` is real) | RF USB tunnel + same-LAN mDNS/QR/PIN, mutual TLS, capability UI, local revoke, local audit; VS session IPC if VS is installed | Any cloud/signaling hostname in **either** APK; TURN; “works on cellular”; always-on without a notification |
| Milestone 8 (Tailscale/Headscale preferred; B/C only if owner rejects mesh) | RootForge Settings → Remote; optional Tailscale login or Headscale URL+preauth key; mesh status + peer hostname; `peer.*` TLS on overlay IPs; session tokens; per-session approve; idle timeout; kill switch both sides | Terminating `peer.*` TLS on Tailscale/Headscale/DERP; requiring Tailscale for USB/LAN; unpinned downloads; a second tailscaled in VictorSuite |

A PC admin (RootForge `:desktop`) over internet is the same Milestone 8 protocol — another reason `peer.*` lives in `:core`.

### 13.5 Open decisions (internet) — also listed in §16

These block Milestone 8 only; Milestone 7 can ship without them.

1. **Self-host only vs optional cloud** — Headscale/VPS the owner runs, vs a Victorious-operated signaling/TURN, vs Tailscale SaaS. Recommendation: **self-host first**; no multi-tenant cloud in the first internet release.
2. **Which VPN/mesh** if A — **resolved: optional in-app Tailscale + Headscale in RootForge** (§13.2.1). Remaining: bundle userspace vs require the Tailscale APK (decision 22).
3. **If B: who runs TURN** — owner VPS image (RootForge `docker/`) vs commercial TURN vs none.
4. **Remote root** — allowed over internet? Second factor: client typed `ROOT`, admin biometric, or both?
5. **Always-on agent** — in M8 or later? Default timeout values?
6. **Naming** — mDNS type (`_rootforge-peer._tcp` vs `_victorsuite-peer._tcp`). Recommendation: **`_rootforge-peer._tcp`**.

---

## 14. Phased program

Tracks can run in parallel after Milestone 0. VictorSuite CI must **not** start building RootForge `:app`. RootForge CI must **not** start building Termux.

### Milestone 0 — Decisions, legal, VictorSuite hygiene (no product behaviour required)

- Owner answers open decisions in §16, especially §2.1 (Gradle dedupe, `com.termux` clash, committed signing key).
- Add a `LICENSE` to RootForge OS (GPLv3-or-later or Apache-2.0 `:core` + GPL scripts — see §8).
- Document Termux CLI checkout path in RootForge (`termux/dev-env.sh` + README). Unblocks the git-clone requirement immediately; needs neither APK.
- Hygiene PR on VictorSuite: single Material / Guava / AppCompat versions (blocked on owner approval of the target versions).

**Exit:** licenses chosen; CLI checkout documented on RootForge `main`; VS dep policy recorded; signing/package decisions recorded.

### Milestone 1 — Shared `:core` contract (no UX merge)

- Publish `:core` (Maven) **or** pin a submodule / `includeBuild` consumed only for schema constants — **not** for embedding RF UI in VS.
- Freeze `BRIDGE_PROTOCOL_MAJOR = 1` envelope + namespace table (§11) in RootForge docs.
- VictorSuite USB host `required=false` (standalone bugfix).

**Exit:** `:core` is consumable as a library; VS debug APK still behaves as today’s VictorSuite; RF APK still behaves as today’s toolkit.

### Milestone 2 — Suite handoff (two launchers)

- RootForge exported `OPEN` action + destination extras.
- VictorSuite drawer category “RootForge” → deep links; missing-app dialog.
- Matching branding tokens (colours / wordmark) in both apps.
- Apply matching signing **only after** §2.1 approval (do not keep leaking `phone.jks` into a new RF keystore).
- Reverse handoff: RootForge “Open in VictorSuite” for terminal.

**Exit:** installing both APKs, a drawer tap opens the right RootForge screen; VS-only install shows a sideload prompt; neither APK contains the other’s UI toolkit.

### Milestone 3 — DCA talks to both the same way

- Envelope documented; RF bridge stays on `ai.rootforge.os`.
- Follow-up PR on droidcommand-ai: `core-termux` unchanged; `core-rootforge` binds by action to RootForge only; capabilities include `app_id`.
- Independence rule tested (DCA off ⇒ both apps still useful).

**Exit:** a debug DCA build can `devices.list` against RootForge and `RUN_COMMAND` against VictorSuite on one device that has both; no bridge inside `com.termux`.

### Milestone 4 — Embedded Termux:API in VictorSuite + Settings toggle

- `:termux-api-lib` (submodule pin `v0.53.0`).
- Components disabled by default; toggle in `VictorSuiteSettingsActivity` + upstream Settings row.
- Overlay helper (patched C for four ABIs, or shim first).
- On: enable, install helper, per-command runtime permission.
- Off: disable, stub helper, `revokeSelfPermissionsOnKill` on API 33+, deep-link otherwise.
- Rewrite `isTermuxAPIAppInstalled()` to the toggle.
- Manual matrix: clipboard, battery, toast, notification (no listener); camera/location only if granted; SMS opt-in tester.
- Document Play incompatibility in VictorSuite README.
- **Do not raise VictorSuite `targetSdk`.**

**Exit:** toggle on → `pkg install termux-api` + overlay → `termux-battery-status` / `termux-clipboard-get` work without `com.termux.api`. Toggle off → commands error.

### Milestone 5 — Harden and document

- README (both repos): suite diagram, CLI checkout, VS APK vs RF APK vs CLI vs desktop.
- `LICENSE.md` updates (Termux:API on VS; RootForge license on RF).
- Mapping table Kotlin flash/module vs `rootforge` CLI.
- **Keep both APKs.** There is no sunset of `ai.rootforge.os`.

### Milestone 6 — PC-first (already mostly built)

RootForge `:desktop` + Docker (PR #49). Suite work is **not** “embed Termux in Compose Desktop”:

- Desktop-specific IA (multi-window, host device list, udev/Windows drivers — `docs/DESKTOP.md`).
- Keep `:toolkit-ui` / `:core` free of `android.*` and of Termux types.
- Optional later: desktop as `peer.*` Administrator (Milestone 7/8 protocol). Not required to “keep PC possible.”

**The suite keeps PC-first possible by never putting Termux, zerocore, or Termux:API on the `:core` / `:toolkit-ui` / `:desktop` classpath.**

### Milestone 7 — Admin/client over USB + same LAN (RootForge)

Depends on M1 (schema in `:core`) and M4 (Termux:API flag via VS IPC). UI/protocol can start after M1.

- Role picker Off / Client / Administrator on RootForge; client is TLS server, admin is not.
- mDNS + QR/PIN pairing, mutual TLS, SPKI pins, `peer.*` in `:core`.
- USB-C = `AdbConnection` port reverse/forward to localhost TLS, not raw `adb shell`.
- Capability-filtered admin UI; libsu **on RootForge** for `rooted`; VS session IPC for user shell / Termux:API when VictorSuite is installed.
- Extra prompts for `peer.shell.root`.
- One-tap revoke; `peer_audit.jsonl` on RootForge; hostile checks (unauthenticated connect refused).
- No internet/signaling/TURN in this milestone.

**Exit:** two phones (or emulators) on one LAN, both running RootForge, complete pairing and a user-level remote shell **when VictorSuite is also on the client**; revoke drops the session; role Off accepts nothing. USB tunnel tested if a host/target pair exists; otherwise documented as unverified.

### Milestone 8 — Remote over internet (RootForge, later)

Blocked on remaining §13.5 / §16 decisions (bundle vs Tailscale APK, remote root 2FA). Preferred implementation is **A**: optional in-app Tailscale + Headscale in RootForge (§13.2.1).

- RootForge Settings → Remote: Tailscale account **or** Headscale URL + preauth key; mesh status; peer hostname. USB/LAN still work with this off.
- Dial `peer.*` TLS to overlay IPs (userspace SOCKS or detected Tailscale VPN). Pin binaries; do not curl “latest.”
- Single tailscaled owner (RootForge). VictorSuite drawer hands off.
- Short-lived session tokens; client approve **each** new remote session; idle timeout; kill switch both sides.
- E2E `peer.*` TLS so a relay cannot read PTY/JSON.
- Optional always-on client agent with visible notification (`targetSdk` 35 FGS).
- Optional audit-log sync (client is source of truth).
- Biometric gate on the admin before connect; rate limits on root ops.

**Exit:** a paired pair on **different** networks gets a session; a test relay/VPN operator with dump capability cannot read shell traffic; revoke from either side ends it; stolen-token-after-TTL fails.

---

## 15. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Raising VictorSuite `targetSdk` “to match RootForge” | Breaks Termux exec / helper / PREFIX | **Do not.** This is the reason for two APKs. |
| Lowering RootForge `targetSdk` to 28 for a future merge | Breaks USB/storage permissions | **Do not.** Keep RF modern. |
| GPLv3 VictorSuite linking unlicensed `:core` | Legal blocker | License RootForge before VS depends on `:core`. Deep links alone do not require a link. |
| Committed `phone.jks` + passwords in Gradle | Key leak, fake updates | Owner approval §2.1; prefer rotate + CI secrets. |
| `com.termux` vs official Termux | Cannot coexist | Owner approval to keep (recommended) or migrate. RF does not clash. |
| Duplicate Material / Guava / AppCompat | Silent Gradle resolution, future `includeBuild` pain | Hygiene PR in M0. |
| Two tailscaled on one phone | Broken mesh / two 1055 binds | RF owns Remote; VS drawer hands off. |
| Helper still pointing at `com.termux.api` | `termux-*` silently fail | Overlay helper is M4 exit criteria. |
| Play policy / SMS on VictorSuite | Store listing impossible | Sideload VS; RF does not inherit SMS. |
| Play VPN policy if RF uses `VpnService` | Extra Play block | Prefer userspace; sideload RF. |
| Suite IPC without matching signatures | Any app could request a PTY | Signature check; matching publisher key after §2.1. |
| USB `required=true` on VS | Drops devices without host | Flip to false in M1. |
| Dual ADB stacks confuse users | Support load | Naming: VS “This device” vs RF “USB target”; handoff, not a merged drawer of both stacks. |
| Official Termux:API installed beside VS | UID mismatch | Detect and explain; overlay helper wins for our PREFIX. |
| `revokeSelfPermissionsOnKill` not on API 23–32 | Toggle off still “has camera” in Settings | Honest copy + deep-link; disable receiver. |
| Private source vs GPLv3 distribution | Recipients’ rights | Offer corresponding source with VS APKs. |
| Admin/client listen path (LAN) | Remote shell | Default off; TLS+pinning; never reuse port 19951. |
| Internet remote / relay | Stolen admin, malicious relay, MITM, runaway root | Milestone 8 only; E2E pins; session approve; biometric on admin (§13.3). |
| Always-on client agent | Implant-shaped UX | Off by default; persistent notification; idle timeout. |
| Headscale `curl\|sh` | RF `join_headscale.sh` still unpinned | Pin installer before architecture A. |
| Real USB / Termux:API hardware untested | Honesty | Keep RF’s Certain/Likely/Guessing language in READMEs. |

---

## 16. Open decisions for the owner

**Resolved by this rewrite**

- **One APK vs two.** **Two apps, one suite.** VictorSuite stays `targetSdk` 28; RootForge stays modern.
- **Sunset `ai.rootforge.os`?** **No.** It is the toolkit + peer APK.
- **Raise VictorSuite `targetSdk` in this program?** **No.**

**VictorSuite prerequisites (block M0 / M2 signing)**

1. **Gradle dep dedupe** — collapse Material (1.12.0 vs 1.2.1), Guava (24.1-jre vs 31.0.1-jre), AppCompat (1.6.1 vs 1.2.0) to one version each. Recommend Material 1.12.x, one Guava line, AppCompat 1.6.x.
2. **Keep `com.termux` vs rename** — clash with official Termux. Recommendation: **keep** (PREFIX, `RUN_COMMAND`, `core-termux`).
3. **Committed `phone.jks` + plaintext fallback passwords** — rotate to CI secrets (existing installs will not upgrade) **or** keep for UTermux-compat and accept the leak. Suite matching signing follows this choice.

**Product and legal**

4. **Suite / launcher names** — keep “VictorSuite” + “RootForge”, or a shared suite name with two subtitles? Packages stay `com.termux` and `ai.rootforge.os` regardless (unless #2 migrates).
5. **RootForge license** — GPL-3.0-or-later (simplest) vs Apache-2.0 for Kotlin `:core`/desktop and GPL for scripts/ISO. Must pick before publishing `:core` or linking it into VictorSuite.
6. **Matching signing implementation** — one Victorious upload key for both APKs (after #3) vs two keys in the same publisher account. Never share with DCA.
7. **`:core` consumption** — published Maven artifact vs submodule / `includeBuild` of `android-app` only. Recommendation: **publish** once licensed so VS does not clone the ISO tree.
8. **Brand of the Termux:API toggle** — “Termux:API”, “Device API”, or “Android API commands”? Default **off** regardless.
9. **SMS / call log / camera in VS v1** — full Termux:API surface, or a reduced set with a second “sensitive APIs” toggle?
10. **Play-safe flavour** — never, or later stripped VS and/or RF? Recommendation: **never** for the full apps; sideload only.
11. **Corresponding source** — make VictorSuite public, or keep private and attach source archives to APK releases?
12. **DCA follow-up** — owner to schedule a droidcommand-ai PR: one envelope, two transports; do not look for the bridge inside `com.termux`.
13. **Keep ZT TCP 19951?** — out of scope, but confirm it stays on VictorSuite. Recommendation: leave it, disable while RF Peer is on if we can hand off a “stop 19951” extra.
14. **Helper strategy for M4** — patched C `termux-api` for all ABIs up front, or MIT shim first (no `termux-usb` FD passing) then C helper?
15. **F-Droid** — out of scope unless the owner wants a public, reproducible, blob-stripped VictorSuite flavour.
16. **`peer.shell.user` if VictorSuite is missing on the client** — deny (`prefix_not_ready`) vs a limited RootForge `sh`. Recommendation: **deny**.
17. **VS Headscale drawer after RF Remote ships** — delete, keep as Termux-only userspace, or shortcut to RootForge? Recommendation: **shortcut** (“Open in RootForge”).

**Internet (block M8 only)**

18. **Internet remote architecture** — **A** mesh VPN (Headscale/Tailscale; preferred, in RootForge), **B** signaling + optional TURN, or **C** reverse tunnel to a VPS.
19. **Self-host only vs optional cloud** — owner-run Headscale/VPS vs Tailscale SaaS vs a Victorious-operated signaling/TURN. Recommendation: **self-host first**.
20. **If B: who runs TURN** — RootForge `docker/` image, commercial TURN, or fail closed.
21. **Remote root** — allow `peer.shell.root` over internet? If yes, second factor: client typed `ROOT`, admin biometric, or both? Recommendation: **both**, default remote-root grant **off**.
22. **Always-on client agent** — include in M8 or later; idle/session TTLs.
23. **mDNS / product service name** — `_rootforge-peer._tcp` (recommended) vs `_victorsuite-peer._tcp`.
24. **Expose SmsSend / Camera over `peer.termux_api` in v1** — recommendation: **no** (LAN or internet).
25. **Bundle userspace Tailscale vs require the Tailscale app** — in RootForge Settings → Remote. Recommendation: **bundle pinned userspace** (`tailscaled --tun=userspace-networking` or official libtailscale) so Headscale works with no second APK; **detect `com.tailscale.ipn`** as a fallback for Tailscale.com. USB/LAN never depend on either. VictorSuite does not bundle a second copy.

---

## 17. Recommended approach (short)

**Two apps, one suite.** Do not merge APKs: VictorSuite must stay on `targetSdk` 28 for Termux exec; RootForge needs a modern `targetSdk` for USB host and storage.

- **VictorSuite** (`com.termux`) owns the terminal, proot, X11, plugins, backup/restore, and the **default-off Termux:API toggle**.
- **RootForge** (`ai.rootforge.os`) owns ADB / fastboot / EDL, root patching, module builder, recovery, and **admin/client** (USB + LAN first; internet later).
- **Shared:** Kotlin `:core` (published artifact or submodule), **one JSON envelope** so droidcommand-ai talks to both the same way (`RUN_COMMAND` vs Messenger `BRIDGE` vs `peer.*`), VictorSuite **“Open in RootForge”** deep links, matching branding and publisher signing.
- **Prerequisites on this repo (owner approval):** dedupe Material / Guava / AppCompat; decide the `com.termux` clash; decide the committed `phone.jks`.
- Keep the **git-clone CLI** and the **PC-first** desktop (`:core` + `:desktop`, no Termux on that classpath).
- **Internet:** optional in-app **Tailscale (official)** and **Headscale (URL + preauth key)** in RootForge Settings → Remote so shell traffic stays on the WireGuard mesh plus device TLS — not a third-party shell relay. USB/LAN work with Tailscale off.
- Do not target Play. License RootForge before publishing `:core`. No DCA→client relay in v1. Do not raise VictorSuite `targetSdk`.
