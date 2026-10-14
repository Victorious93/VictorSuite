# VictorSuite × RootForge OS merge plan

**Status:** planning only. No features implemented in this document’s PR.  
**Owner:** Don Victor (GitHub [Victorious93](https://github.com/Victorious93)).  
**Scope of this document:** one Android APK that combines VictorSuite’s Termux/Linux environment with RootForge OS’s GUI toolkit; a standalone Termux-native CLI that does not need the APK; a later PC-first edition; and Termux:API functionality built into the merged app behind a Settings toggle.

Inventories below are grounded in code as of:

| Tree | Ref | Notes |
|---|---|---|
| This repo (VictorSuite, private) | `main` @ `ced930b` | Termux/ZeroTermux fork, `applicationId` effectively `com.termux` |
| [Victorious93/rootforge-os](https://github.com/Victorious93/rootforge-os) (public) | `main` @ `ae180a3` (includes merged [PR #49](https://github.com/Victorious93/rootforge-os/pull/49)) | Debian ISO + Termux rootfs + Kotlin `:core` + Android APK + Compose desktop + Docker |
| [termux/termux-api](https://github.com/termux/termux-api) | `master` @ current clone (`versionName` 0.53.0 / `versionCode` 1002) | Companion APK `com.termux.api`, GPLv3 |
| [termux/termux-api-package](https://github.com/termux/termux-api-package) | README + `termux-api.c` (helper 0.60.0) | MIT `termux-*` scripts + C helper |

ZeroTermux research: [hanxinhao000/ZeroTermux](https://github.com/hanxinhao000/ZeroTermux) and [zerotermux.dev](https://zerotermux.dev/).

---

## 1. Goal and non-goals

### Goal

Ship **one sideload APK** a user installs instead of Termux + ZeroTermux-style extras + RootForge’s host-phone toolkit + Termux:API:

1. VictorSuite terminal, bootstrap, proot containers, backup/restore, plugins, X11, FTP, etc.
2. RootForge Compose Material 3 toolkit (ADB / fastboot / EDL over USB OTG, module builder, root-patching workflows, recovery, logs).
3. `termux-clipboard-get`, `termux-notification`, `termux-battery-status`, `termux-sensor`, `termux-camera-photo`, `termux-location`, `termux-sms-*`, and the rest of the `termux-*` API commands, **when a Settings toggle is on**, with no separate Termux:API APK.

Termux-only users continue to `git clone` RootForge OS and run its CLI/scripts in stock Termux (or VictorSuite) without that APK.

A later **PC-first** Linux/Windows edition uses the same Kotlin `:core` and the existing Compose desktop client — not a phone UI stretched onto a monitor.

### Non-goals (this plan)

- Implementing the merge, rewriting ZeroTermux UI in Compose, or producing a Play Store listing.
- Building the Debian live ISO inside VictorSuite CI.
- Replacing Magisk/KernelSU/APatch with a custom patcher (RootForge’s Android app already delegates patching to those apps on the **target** phone).
- Changing droidcommand-ai in the same PR as the APK merge (protocol stays; DCA needs a small follow-up).

---

## 2. Inventory — VictorSuite (this repo)

VictorSuite is a Termux fork on the ZeroTermux codebase. It keeps Termux’s `applicationId` / `sharedUserId` (`com.termux`) and the public `RUN_COMMAND` contract so existing Termux tooling and [droidcommand-AI](https://github.com/Victorious93/droidcommand-AI)’s `core-termux` module still work. **VictorSuite and genuine Termux cannot be installed on the same device.**

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
- Signing: committed `phone.jks` with env-overridable passwords in `app/build.gradle` (legacy ZeroTermux/UTermux key compatibility). Treat as sensitive.

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

### Android surface that matters for the merge

- Launcher: `TermuxActivity` (terminal).
- Settings: upstream `SettingsActivity` (PreferenceFragment, already has a Termux:API row that **hides if `com.termux.api` is missing**) plus `VictorSuiteSettingsActivity` (SwitchCompat list — the natural place for the Termux:API toggle).
- Manifest already declares `android.hardware.usb.host` **`required="true"`** (inherited from ZeroTermux OTG file access). That currently **blocks install on phones without USB host**. RootForge’s APK does the same for a different reason. The merged APK must flip this to `required="false"` or Termux-only users on non-OTG hardware lose the app.
- Permissions already include `INTERNET`, storage, `READ_SMS`, `READ_CONTACTS`, `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, `WRITE_SECURE_SETTINGS`, `DUMP`, `PACKAGE_USAGE_STATS`. Not the full Termux:API set (no `CAMERA`, `ACCESS_FINE_LOCATION`, `SEND_SMS`, `RECORD_AUDIO`, `CALL_PHONE`, `READ_CALL_LOG`, `BODY_SENSORS`, …).
- `sharedUserId="${TERMUX_PACKAGE_NAME}"` (`com.termux`) — this is why official Termux plugins (API, Styling, Widget, Tasker, Boot) can share the prefix directory **when signed with the same key**. VictorSuite’s `phone.jks` is **not** the official Termux key, so official F-Droid Termux:API will **not** share UID with this APK. Embedding Termux:API is the only way the `termux-*` commands work for VictorSuite users without a matching-signature companion.

### License

`LICENSE.md`: **GPLv3 only**, following ZeroTermux / Termux. Exceptions: Apache-2.0 terminal-emulator/view and StreamGobbler; GPLv2+Classpath for `com.termux.shared.file` ojluni bits.

Termux’s GPLv3 is the legal ceiling for **any APK that includes this code**. See §8.

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
| **Kotlin `:core`** | `android-app/core` — JVM library: flash planner, module zip builder, boot-image parser, USB classifier, approvals, bridge protocol, library (named files, not paths), `LinkState`. Used by Android `:app` and Compose `:desktop`. **No Android SDK dependency.** |

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
- **No INTERNET permission.** USB Host feature **required=true**.
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
- **Checkout path (works today, needs documenting as first-class):** clone the public repo in Termux, put the Python package and scripts on `PYTHONPATH`/`PATH`, `pkg install python android-tools`, run `rootforge doctor`. Destructive commands still need host `adb`/`fastboot` (Termux `android-tools` or a Debian proot). This is requirement (2).

ISO/live-build (`auto/`, `config/hooks/`, `Makefile`) stays in RootForge. It is not part of the APK merge.

### Android toolkit screens (`RootForgeUi.kt` `Destination`)

Dashboard, ADB, Fastboot, EDL, Root/Patching, Module Builder, Recovery, Logs/Terminal, Settings. Modal navigation drawer + typed confirmation (`FLASH` / `UNLOCK` / `LOCK` / `SLOT` / `EDL`) via `ApprovalCenter`. USB stack is **pure Kotlin** (`adb/`, `fastboot/`, `edl/Sahara+Firehose`, `usb/UsbHost`) — not a bundled `adb` binary.

### License

**No `LICENSE` file.** README states redistribution terms are undefined. Third-party bits fetched at ISO build time keep their own licenses. This is an **open decision** and a blocker before advertising the public repo as open source or linking `:core` into a GPLv3 APK without a grant.

### Tests / CI

- `tests/run-tests.sh` + `tests/lint.sh` (Python + shell, hermetic stubs).
- `:core:test` (48), `:app:testDebugUnitTest` (protocol + Paparazzi).
- Workflows: `lint.yml`, `android-apk.yml`, `toolkit.yml` (desktop + Docker), `release.yml` (ISO + rootfs, draft).

---

## 4. ZeroTermux feature set (inspiration, not a third repo to merge)

VictorSuite **already is** a ZeroTermux-lineage tree. Do not import another copy of ZeroTermux.

Upstream ZeroTermux ([hanxinhao000/ZeroTermux](https://github.com/hanxinhao000/ZeroTermux), GPLv3) adds on top of Termux:

- Graphical panels for source switch, timers, containers
- Full environment backup/restore and shareable restore packs
- PRoot/chroot distros (Ubuntu, Kali, Debian, …)
- X11 desktop (separate [ZeroTermux-X11-aar](https://github.com/hanxinhao000/ZeroTermux-X11-aar) internal channel) and QEMU
- Download-site restore packs
- Recent releases: in-app AI agent that can drive the terminal and settings, LSP/code editor, maintenance mode

VictorSuite has the historical ZeroTermux management layer (`zerocore`) plus VictorSuite-specific plugins, AI CLI setup, and DeepSeek/Claude chat. It is **behind** current ZeroTermux on the AI-agent / LSP work (ZeroTermux 0.118.3.63+). The merge should **not** attempt to fast-forward to that AI agent in the same program of work.

---

## 5. Termux:API — how it works, how to embed it, toggle, Play, license

This section is the additional owner requirement: **build Termux:API into the merged APK**, Settings on/off, no companion app.

### 5.1 Two artifacts, two licenses, two package names

| Artifact | Repo | License | What it is |
|---|---|---|---|
| **Termux:API app** | [termux/termux-api](https://github.com/termux/termux-api) | **GPLv3** (README; there is **no `LICENSE` file** in the tree — still GPLv3 per project statement). App `versionName` 0.53.0. | Android APK `applicationId` **`com.termux.api`**, `sharedUserId` `com.termux`. |
| **`termux-api` package** | [termux/termux-api-package](https://github.com/termux/termux-api-package) | **MIT** (`TERMUX_PKG_LICENSE="MIT"` in termux-packages `build.sh`) | C helper `termux-api` + shell wrappers `termux-clipboard-get`, `termux-notification`, `termux-battery-status`, `termux-sensor`, `termux-camera-photo`, `termux-location`, `termux-sms-list`, `termux-sms-send`, … Users install with `pkg install termux-api`. |

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

Hard-coded listen address: `LISTEN_SOCKET_ADDRESS "com.termux.api://listen"` which matches `SocketListener.LISTEN_ADDRESS = TermuxConstants.TERMUX_API_PACKAGE_NAME + "://listen"` (`com.termux.api://listen`).

`TermuxApiReceiver` is **`android:exported="false"`**. Delivery works because `am broadcast -n` uses an explicit component **in package `com.termux.api`**, and the helper runs as the Termux UID (`sharedUserId`). Same-UID + explicit component is how a non-exported receiver is reached.

The app README also requires the **same signing key** as the main Termux app so `sharedUserId` actually unifies the two APKs.

VictorSuite already looks for the companion: `TermuxUtils.isTermuxAPIAppInstalled()` / `TermuxAPIAppSharedPreferences.build()` use package **`com.termux.api`**. Settings hides the Termux:API preference if that package is absent.

### 5.3 API methods in `TermuxApiReceiver` (embed this list)

AudioInfo, BatteryStatus, Brightness, CameraInfo, CameraPhoto, CallLog, Clipboard, ContactList, Dialog, Download, Fingerprint, InfraredFrequencies, InfraredTransmit, JobScheduler, Keystore, Location, MediaPlayer, MediaScanner, MicRecorder, Nfc, Notification / Channel / Remove / Reply / List, SAF, Sensor, Share, SmsInbox, SmsSend, SpeechToText, StorageGet, Telephony*, TextToSpeech, Toast, Torch, Usb, Vibrate, Volume, Wallpaper, Wifi*.

Permission-gated examples (runtime, via `TermuxApiPermissionActivity`): `CAMERA`, `READ_CALL_LOG`, `READ_CONTACTS`, `TRANSMIT_IR`, `ACCESS_FINE_LOCATION`, `RECORD_AUDIO`, plus `WRITE_SETTINGS` (special settings screen), Notification Listener access, etc.

Manifest-declared dangerous/special permissions in termux-api (must be copied if we embed): location (fine/coarse/background), body sensors, call phone, camera, read call log, read contacts, read phone state, read SMS, **send SMS**, record audio, NFC, IR, biometric, wallpaper, wifi change, `MANAGE_EXTERNAL_STORAGE`, `SYSTEM_ALERT_WINDOW`, `WRITE_SETTINGS`, `DUMP`, `PACKAGE_USAGE_STATS`, `READ_PRIVILEGED_PHONE_STATE` (ignored on unprivileged apps), install/delete packages.

Hardware features in termux-api are all `required="false"` (camera, location, mic, telephony, wifi, **usb.host false**). Copy that pattern.

### 5.4 Why “just compile termux-api into `:app`” is not enough

The helper always targets **package `com.termux.api`**. The merged APK’s `applicationId` must stay **`com.termux`** (bootstrap path `/data/data/com.termux/files`, `RUN_COMMAND`, plugins). An APK cannot have two applicationIds.

Component name if we keep class `com.termux.api.TermuxApiReceiver` inside applicationId `com.termux`:

```
com.termux/com.termux.api.TermuxApiReceiver
```

not

```
com.termux.api/.TermuxApiReceiver   (= com.termux.api/com.termux.api.TermuxApiReceiver)
```

Stock `pkg install termux-api` will keep broadcasting into a package that does not exist (unless the user also installed official Termux:API — which will not share UID with VictorSuite’s signing key, so sockets/UID checks fail).

Therefore embedding **requires a helper retarget**, not only Java sources in the APK.

### 5.5 Recommended embed design

**Library module, not a second APK.**

```
:termux-api-lib     # sources from termux/termux-api (GPLv3), android.library
                    # keep Java package com.termux.api.* to minimise drift
:app                # registers the receiver/services/activities in the merged manifest
```

Prefer a **git submodule** of `termux/termux-api` at a pinned tag (start at `v0.53.0`) plus a thin overlay (toggle, component enablement, helper install). Do not silently fork 37 API classes if we can avoid it.

**Manifest (merged `:app`):**

- Merge termux-api’s `<uses-permission>` and optional `<uses-feature>` (all `required="false"`).
- Register:
  - `com.termux.api.TermuxApiReceiver` (`exported="false"`)
  - `TermuxApiPermissionActivity`, Dialog/Fingerprint/Nfc/SAF/Storage activities
  - `KeepAliveService`, `JobSchedulerAPI$JobSchedulerService`, `NotificationListAPI$NotificationService` (notification listener — only useful when toggle on)
  - `ShareAPI$ContentProvider` authority stays `${TERMUX_PACKAGE_NAME}.sharedfiles` (`com.termux.sharedfiles`) — already Termux-shaped
- **Do not** add a second LAUNCHER activity for Termux:API. Settings toggle is the surface.
- Receiver and related components start **disabled** (`android:enabled="false"`) so a freshly installed APK does not expose SMS/camera until the user opts in.

**Helper retarget (required for `termux-*` to hit this APK):**

Ship a small overlay, installed into `$PREFIX` when the toggle turns **on**:

1. **Preferred:** a patched `termux-api` binary (fork of MIT `termux-api.c`) with:
   - `-n com.termux/com.termux.api.TermuxApiReceiver`
   - listen address `com.termux.api://listen` **kept** if `SocketListener` still binds that name (it is an abstract socket, not a package identity), **or** both sides switched to `com.termux://api-listen`. Keeping `com.termux.api://listen` is less work and still unique on the device because only this UID can bind it.
2. **Fallback:** a `termux-api` **shell shim** prepended on `PATH` that execs `am broadcast -n com.termux/com.termux.api.TermuxApiReceiver …` with the same extras. More fragile than the C helper (stdin/stdout sockets, `SCM_RIGHTS` for `termux-usb`). Only acceptable as a first milestone for the non-FD APIs.

On toggle **on**:

1. `PackageManager.setComponentEnabledSetting(TermuxApiReceiver, ENABLED, DONT_KILL_APP)` (and the permission activity, jobs, notification listener — listener still needs a separate system Settings grant).
2. Start `SocketListener` (today it starts unconditionally from `TermuxAPIApplication.onCreate`; gate it).
3. Copy/patch helper into `$PREFIX/bin/termux-api` (and do not overwrite a newer MIT package blindly — record a sentinel file / version).
4. Prompt: “Android will now ask for permissions as you use each command (camera, location, SMS, …). You can turn this off in Settings.”
5. Do **not** bulk-request every dangerous permission at toggle time. Match upstream: request **per command** in `TermuxApiPermissionActivity`. Optional: a Settings sub-screen “Grant common API permissions” for clipboard-adjacent / notifications only.

On first use of a given command, existing `checkAndRequestPermissions` already returns JSON `{"error":"Please grant …"}` to the shell and launches the permission activity. Keep that.

On toggle **off**:

1. Disable the receiver and API components (`COMPONENT_ENABLED_STATE_DISABLED`). Further `am broadcast` / socket connections fail closed; helper should print a clear “Termux:API is turned off in VictorSuite Settings”.
2. Stop `SocketListener` (interrupt the thread; today it is a never-joined `while (true)` — add a close path).
3. Remove or replace the overlay helper with a stub that exits 1 with the same message, **or** restore the stock `pkg` binary so a user who later installs a real companion is not stuck. Prefer stub while toggle is off.
4. **Revoke runtime grants** where the platform allows:
   - API 33+ (`Tiramisu`): `Context.revokeSelfPermissionsOnKill(perms)` then request a process restart (document that revoke happens after the next kill).
   - API 23–32: an app **cannot** silently revoke its own runtime permissions. Disable components (functional off) and tell the user “Revoke camera/SMS/location under Android Settings → Apps → VictorSuite → Permissions” with a button to `ACTION_APPLICATION_DETAILS_SETTINGS`.
5. Special grants cannot be yanked from the app: Notification Listener, `WRITE_SETTINGS`, `SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE`, battery optimization. The toggle must deep-link to those system screens and state that they stay until the user revokes them.
6. Do not uninstall `pkg`’s MIT scripts; they are harmless without a receiver.

**Gate inside `TermuxApiReceiver`:** even if a component enable race happens, `doWork()` returns immediately with a JSON error when the toggle pref is false. Defense in depth (same idea as RootForge’s bridge `bridgeEnabled` default off).

**Do not keep shipping a hidden `com.termux.api` APK.** One APK is the requirement. `TermuxUtils.isTermuxAPIAppInstalled()` and the Settings “Termux:API” preference must be rewritten to mean “embedded API enabled”, not “package `com.termux.api` present”. `TermuxAPIShellEnvironment` should export `TERMUX_API_APP__VERSION_NAME` from the merged app when the toggle is on.

**Conflict if the user also installs official Termux:API:** different signature → different UID → helper overlay points at `com.termux`, official app is unused. Detect `com.termux.api` and show “Official Termux:API is installed but cannot share data with this app (different signature). Use the in-app toggle instead; you may uninstall the companion.”

### 5.6 Play Store and permission-policy risk

**Plan as sideload / GitHub Releases / (maybe) F-Droid. Do not plan a Play listing for this APK.**

Reasons, stacked:

1. **Termux itself left Play** because of policy around executing downloaded binaries / `REQUEST_INSTALL_PACKAGES` / the `$PREFIX` model. VictorSuite is that model.
2. Declaring **SMS + call log** (`SEND_SMS`, `READ_SMS`, `READ_CALL_LOG`, `CALL_PHONE`) triggers Google Play’s [SMS and Call Log permissions policy](https://support.google.com/googleplay/android-developer/answer/10208820). Exceptions are default SMS/Phone handlers, backup agents, etc. A terminal suite is **not** in the exception list. Play review will reject or demand removal.
3. Camera, microphone, location (including background), body sensors each have prominent-disclosure and use-case rules. A single APK that is also a Magisk/fastboot toolkit makes the “core functionality” story worse, not better.
4. **A Settings toggle does not help Play.** Play reviews **manifest-declared** permissions, not runtime grants. Disabled components still leave `<uses-permission>` in the merged manifest. `maxSdkVersion` tricks and permission-group splitting will not hide SMS.
5. RootForge’s flash/unlock UI plus `REQUEST_INSTALL_PACKAGES` plus accessibility-adjacent notification listener is a malware-heuristic cluster.

VictorSuite already declares `READ_SMS` and `READ_CONTACTS` (ZeroTermux leftover). Embedding Termux:API **adds SEND_SMS and CALL_PHONE**, which is the policy jump.

**If a future “Play-safe” flavour is wanted (open decision):** a Gradle product flavour that **omits** SMS/Call/Camera/Mic/Location APIs and their permissions, keeps clipboard/toast/vibrate/battery/wifi-scan/notification (non-listener) / brightness. That flavour is a different APK id or a clearly named build. It is not the merged “full” APK. Default distribution remains sideload.

F-Droid: possible in principle (Termux:API is already there) but VictorSuite is private, uses a committed keystore, Aliyun Maven, and non-free blobs (scrcpy, QEMU bits, X11 AAR). Treat F-Droid as a later owner decision, not a merge milestone.

### 5.7 Licensing of the embed

- Termux:API **app sources = GPLv3**. Copying them into this GPLv3 APK is **license-compatible**. Keep copyright headers; add Termux:API to `LICENSE.md` acknowledgements; pin the upstream commit.
- `termux-api-package` helper/scripts = **MIT**. A patched helper overlay is MIT-compatible; keep the MIT notice in the overlay sources.
- Combining with RootForge `:core`: the **APK as a whole** is a GPLv3 work (Termux + Termux:API + whatever is linked in). RootForge must grant a GPLv3-compatible license for `:core` before the APK is distributed. See §8 and open decisions.

---

## 6. Overlap and conflicts

| Topic | VictorSuite | RootForge | Merge choice |
|---|---|---|---|
| `applicationId` | `com.termux` (load-bearing) | `ai.rootforge.os` / `.debug` | **Keep `com.termux`.** Bridge action string can stay `ai.rootforge.os.action.BRIDGE` even inside another package. DCA bind-by-package must change. |
| minSdk | 23 | 26 | **23 for the APK** so existing VS devices keep working; RootForge USB/Compose code feature-gated (`Build.VERSION.SDK_INT >= 26`) or run with Compose requiring 26 only on those screens. Confirm Compose UI dependencies on 23 in a spike (milestone 0). If Compose 2024.12 cannot run on 23, raise merged minSdk to **24 or 26** (Termux:API upstream is minSdk 24) — owner decision. |
| targetSdk | 28 | 35 | **Keep 28** for the Termux APK. Termux + Termux:API are both 28 for a reason (scoped storage, background `am`, external dir). RootForge’s 35 is for a Play-shaped toolkit APK that never ships on Play here. Desktop is unaffected. |
| compileSdk | 36 | 35 | 36 (already VS CI). |
| USB host feature | `required=true` (mass storage) | `required=true` (ADB/fastboot/EDL) | **`required=false`.** OTG/toolkit screens degrade; terminal must install everywhere. |
| USB use | libaums mass-storage file copy (`OTGManager`) | Bulk USB for ADB/fastboot/Sahara | **Coexist, don’t share the interface.** Claiming ADB vs MSC is exclusive; UI must not auto-claim every USB device as a flash target (VS currently pops “OTG detected” on any MSC device). Add a chooser: “Use as storage” vs “Use as Android target”. |
| ADB | `libadb.so` + tananaev adblib; wireless ADB to **this** phone | Pure-Kotlin USB ADB to a **second** phone | Different jobs. Keep both. Label VS as “ADB this device / wireless”; RootForge as “USB target phone”. |
| Module “install” | ZeroTermux `InstallModuleClickConfig` → local module zip dialog (Termux modules, not Magisk) | Magisk/KernelSU/APatch **zip builder** + flash on **target** | Different. Separate menu names: “Termux module” vs “Root module builder”. |
| Terminal | Full VT100 `TermuxActivity` | Log panel + command box (`Logs` destination) | Termux remains the real terminal. RootForge “Logs/Terminal” stays a toolkit console for USB operations, not a second Linux environment. |
| Compose vs Views | AppCompat / data binding | Compose Material 3 | **Hybrid.** Do not rewrite zerocore in Compose in this program. Host RootForge UI in a `ComposeView` / `ComponentActivity`. |
| Gradle style | Groovy `build.gradle` | Kotlin DSL + version catalogs-ish | Keep VS Groovy for `:app`; include RootForge’s `android-app` as **composite build** or submodule with its own Gradle. |
| INTERNET | Yes (bootstrap, catalogues, AI) | No (deliberate) | Merged APK has INTERNET (Termux needs it). RootForge toolkit code must not start using it for flashes. |
| droidcommand | Passive `RUN_COMMAND` / looks like Termux (`core-termux`) | Independent node, Messenger bridge (`core-rootforge`) | **Both in one APK** — see §10. |
| Signing | `phone.jks` | CI env keystore | Keep VictorSuite key so existing installs upgrade in place. |
| Public vs private | Private | Public | Do not move Termux sources into the public RootForge repo (GPLv3 is fine to publish, but this fork and catalogues are currently private). Consume RootForge **from** public; publish APK from private. |

---

## 7. Recommended target architecture

### 7.1 Which repo is home

| Deliverable | Home repo | Why |
|---|---|---|
| Merged Android APK | **VictorSuite (this repo)** | GPLv3 Termux sources, `com.termux`, bootstrap, `RUN_COMMAND`, existing users, private catalogues. |
| Kotlin `:core`, Compose toolkit UI, USB/ADB/fastboot/EDL, desktop, Docker | **RootForge OS (public)** | Already structured as a shared JVM core + two clients. PC-first edition lives here. |
| Python CLI, bash scripts, ISO, Termux rootfs | **RootForge OS (public)** | Requirement (2): `git clone` without the APK. |
| Termux:API Java | **VictorSuite** as `:termux-api-lib` (submodule of `termux/termux-api`) | Must live in the `com.termux` APK. |
| Patched `termux-api` helper overlay | **VictorSuite** `scripts/` / assets (MIT, tiny) | Installed into `$PREFIX` on toggle. Optionally upstream a `TERMUX_API_RECEIVER` env later. |

Do **not** make RootForge the APK home: you would have to vendor 89k LOC of Termux, keep `com.termux`, and make a public repo that currently has no license into a GPLv3 Termux fork.

Do **not** copy RootForge’s Python/ISO tree into VictorSuite: that breaks the standalone CLI story and duplicates CI.

### 7.2 Module layout (merged APK, VictorSuite Gradle)

```
VictorSuite (private)
├── :terminal-emulator
├── :terminal-view
├── :termux-shared
├── :termux-api-lib          # submodule pin of termux/termux-api app sources (android.library)
├── :app                     # com.termux APK: TermuxActivity + zerocore + hosts toolkit + API receiver
├── third_party/rootforge-os # git submodule (public), or Gradle includeBuild of a sibling clone
│     └── android-app/
│           :core            # JVM, consumed by :app
│           :app             # today: USB + Compose UI; split as below
│           :desktop         # NOT built by VictorSuite CI
└── docs/MERGE_PLAN.md
```

**Split RootForge `:app` (in the public repo, during the merge program):**

| New module | Contents |
|---|---|
| `:core` (exists) | Protocol, planner, module zip, approvals, library, `LinkState` |
| `:toolkit-ui` | `ai.rootforge.os.ui.*` Compose screens (already compiled into Android **and** desktop) |
| `:toolkit-android` | `adb/`, `fastboot/`, `edl/`, `usb/`, `RootForgeBridgeService`, `MainActivity`/`AppActionsImpl` |
| `:desktop` | Unchanged; depends on `:core` + `:toolkit-ui` |

VictorSuite `:app` depends on `:core`, `:toolkit-ui`, `:toolkit-android`, `:termux-api-lib`.

RootForge CI keeps building **its own** `ai.rootforge.os` APK for a while (toolkit-only, no Termux) as a compatibility artifact until the merged APK is the one the owner ships. Sunset decision is open.

### 7.3 Package name and components

| Item | Value |
|---|---|
| `applicationId` / `sharedUserId` | **`com.termux`** (non-negotiable for PREFIX + plugins + `core-termux`) |
| Launcher label | Keep **VictorSuite** until the owner picks a combined brand (open) |
| RootForge Activities | Not a second LAUNCHER. `RootForgeToolkitActivity` (`singleTask`) started from the drawer |
| Bridge service | Same class, intent action **`ai.rootforge.os.action.BRIDGE`** (stable for DCA). `exported=true`, pairing + toggle, not a signature permission |
| Termux:API receiver | `com.termux.api.TermuxApiReceiver`, disabled by default |
| `RUN_COMMAND` | Unchanged (`com.termux.permission.RUN_COMMAND`) |

Debug applicationId stays `com.termux` (Termux cannot use `.debug` suffix without rewriting PREFIX). RootForge’s `.debug` suffix **does not** apply to the merged APK.

### 7.4 How shared Kotlin core is consumed

```
                    ┌─────────────────┐
                    │  :core (JVM)    │  flash plan, module zip, bridge
                    │  kotlinx.json   │  protocol, approvals, library
                    └────────┬────────┘
               ┌─────────────┼──────────────┐
               v             v              v
        :toolkit-android  :desktop     Docker API (Python
        + :toolkit-ui     + :toolkit-ui  mirrors operation names)
               v             v
        VictorSuite APK   .deb / .exe   rootforge_api.py
        (this repo)       (RF repo)     (RF repo)
```

VictorSuite consumes `:core` via **git submodule + Gradle `includeBuild('third_party/rootforge-os/android-app')`** (or a published Maven coordinate later). Pin a RootForge commit. Do not vendor-copy Kotlin sources into `zerocore` — that forks the desktop client.

Shell/Python tooling is **not** compiled into the APK. The APK may:

- Offer “Install RootForge CLI into this environment” → `git clone` or copy scripts into `$HOME` / a proot (optional convenience, not required).
- Drive USB workflows in Kotlin (`:toolkit-android`) which already duplicate a **subset** of CLI operations (flash, module zip) with the same confirmation phrases. Do not exec `rootforge` from the APK for USB; there is no `/usr/local` Debian in the Termux bootstrap.

Longer term, destructive rules should stay documented as one contract (typed `FLASH <serial>`, vendor refuse list). Kotlin `FlashPlan` and Python `device.py` are **not** the same code today. Milestone 3 is a documented mapping, not a rewrite.

### 7.5 Termux-native CLI distribution (requirement 2)

Keep RootForge public. Document **two** paths in RootForge `README.md` **and** a short `docs/TERMUX_CLI.md` copied or linked from VictorSuite:

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

`common.sh` / `runner.py` already support this. Add a one-line `termux/dev-env.sh` that exports those two variables so users do not have to remember paths. **Do not** require `proot-distro` or a multi-GB rootfs for `doctor` / `module scaffold` / host-side `adb`.

**B. Released Debian rootfs (when `release.yml` actually publishes)**

Existing `install.sh` + `proot-distro install rootforge` / `rootforge-chroot.sh`. Unchanged. Still the path for a full Debian userspace (magiskboot, payload-dumper-go, etc.).

The merged APK must not become a gate for (A) or (B).

---

## 8. Licenses and GPLv3 implications

| Component | License | Effect on merge |
|---|---|---|
| Termux app + ZeroTermux + VictorSuite | **GPLv3 only** | The merged APK is a GPLv3 binary. Corresponding source for everything **linked into it** must be available to recipients under GPLv3. |
| terminal-emulator / terminal-view | Apache-2.0 | Compatible. |
| Termux:API app sources | GPLv3 | Compatible; keep notices. |
| termux-api-package helper | MIT | Compatible; patched overlay OK. |
| RootForge OS | **None today** | **Cannot ship `:core` inside the GPLv3 APK until RootForge has a GPLv3-compatible license.** |
| Compose / AndroidX / kotlinx | Apache-2.0 | Compatible. |
| Various VS third-party (Glide, okhttp, libsu, …) | Mix (mostly Apache/MIT) | Already accepted in VS. Audit any non-GPL-compatible addition from RootForge (none in `:core` today). |

**What GPLv3 does *not* force:**

- The **Python CLI and bash scripts**, distributed as source from a public git clone, can remain a separate work if they are not linked into the APK (they are not). They should still get an explicit license (GPL-3.0-or-later **or** Apache-2.0).
- The **desktop app** links `:core` + Compose UI, **not** Termux. If `:core` is Apache-2.0, the desktop can be Apache-2.0. If `:core` is GPLv3, the desktop is GPLv3 too. Recommendation: license RootForge **GPL-3.0-or-later** for simplicity (one license for ISO scripts + Kotlin), **or** Apache-2.0 for `:core`/desktop and GPL-3.0 for scripts — owner decision. Either is APK-compatible if `:core` is Apache-2.0 or GPL-3.0.
- Private VictorSuite remaining private is allowed under GPLv3 **until you distribute the APK**. Anyone you give the APK to is entitled to the corresponding source of the combined work (including the RootForge Kotlin you linked and Termux:API). Practical approach: keep VictorSuite private but offer source tarballs / access to recipients; or make the merged tree public.

**Do not** relicense Termux or Termux:API. **Do not** keep RootForge unlicensed.

---

## 9. UI / navigation for the merged app

**Terminal stays home.** `TermuxActivity` remains `MAIN`/`LAUNCHER`. This is a Linux environment first; the toolkit is a mode.

```
TermuxActivity (drawer — existing MainMenuConfig)
├── (existing ZeroTermux categories)
└── NEW category CODE_ROOTFORGE = "Root toolkit"
    ├── Dashboard          → start RootForgeToolkitActivity at Destination.Dashboard
    ├── ADB (USB target)
    ├── Fastboot
    ├── EDL
    ├── Root / Patching
    ├── Module builder     (name: "Root module builder")
    ├── Recovery
    └── Logs (USB)

Settings
├── VictorSuiteSettingsActivity  (existing switches)
│     └── NEW: Termux:API  [off by default]
│           ├── explainer + permission status list
│           └── “Open system permission settings”
├── Termux SettingsActivity      (upstream)
│     └── Termux:API row → same toggle (do not hide when companion missing)
└── RootForge Settings screen    (pairing, node id, USB RSA fingerprint)
```

`RootForgeToolkitActivity` hosts `RootForgeUi()` in Compose. Back returns to the terminal session (do not destroy the session). Optional: extra-keys row button “Toolkit”.

USB attach: if the device looks like ADB/fastboot/QCOM 9008 → offer toolkit Connect; if MSC → existing OTG file dialog; never both automatically.

When Termux:API toggle is off, `termux-*` commands fail with a one-line hint pointing at Settings. When on, they behave like official Termux:API.

Do not merge RootForge’s Logs panel with `TermuxActivity` in v1.

---

## 10. droidcommand-ai continuity

Today (from VS README + RootForge `docs/DROIDCOMMAND_INTEGRATION.md`):

| Path | Role | Transport |
|---|---|---|
| `core-termux` | Treats VictorSuite as Termux | `RUN_COMMAND` intent, package `com.termux` |
| `core-rootforge` | Independent node, not a companion | SSH (passive) and Android **Messenger** `ai.rootforge.os.action.BRIDGE` |

After merge, **one APK is both**:

1. **Keep `RUN_COMMAND` byte-for-byte.** `core-termux` continues to work and still cannot distinguish VictorSuite from Termux. That is existing, desired behaviour.
2. **Keep the RootForge bridge JSON** (`BRIDGE_PROTOCOL_MAJOR = 1`, same operations, pairing, typed confirmations). Register `RootForgeBridgeService` in the merged manifest with the **same action**.
3. **DCA change (small, in droidcommand-ai):** bind by **action**, not by `applicationId == ai.rootforge.os`. Try `com.termux` (merged) then `ai.rootforge.os` / `.debug` (toolkit-only APK during transition). Identity remains UID → package + cert hash (already not a JSON field).
4. `rootforge.capabilities.get` should report `node_kind` such as `android-host-app` still, plus `app_id=com.termux` / `app_name=VictorSuite` so DCA can show the right label.
5. **Independence rule unchanged:** turning DCA off, or never pairing, must not disable terminal, toolkit GUI, or Termux:API toggle.
6. Do **not** add a `BIND_ROOTFORGE` signature permission (RootForge docs already forbid sharing a signing key with DCA).
7. ZT TCP 19951 is a **third** control plane (LAN JSON → shell). Out of scope for DCA. Do not wire it to the bridge.

SSH `rootforge bridge` on a Debian/Termux rootfs node is unchanged (CLI lives in RootForge).

---

## 11. Phased migration

### Milestone 0 — Decisions and legal (no APK behaviour change)

- Owner answers open decisions in §13.
- Add a `LICENSE` to RootForge OS (GPLv3-or-later or Apache-2.0 + GPL scripts — see §8).
- Spike: Compose toolkit Activity hosted from a throwaway branch of VictorSuite on minSdk 23 vs 26.
- Spike: `includeBuild` of `rootforge-os/android-app`.
- Document Termux CLI checkout path in RootForge (`termux/dev-env.sh` + README). This unblocks requirement (2) immediately and does not depend on the APK.

**Exit:** licenses chosen; spikes recorded; CLI checkout documented on RootForge `main`.

### Milestone 1 — Composite build, no UX merge

- Submodule or documented sibling clone of RootForge.
- VictorSuite Gradle `includeBuild`; `:app` depends on `:core` only (no UI yet).
- CI: existing `:app:assembleDebug` still green; optionally run `:core:test`.
- USB `required=false` on VictorSuite (standalone bugfix; do it even if the rest slips).

**Exit:** debug APK still behaves as today’s VictorSuite; `:core` compiles on the VS CI agent.

### Milestone 2 — Toolkit UI inside VictorSuite (one APK, two worlds)

- Split RootForge `:app` into `:toolkit-ui` + `:toolkit-android` (public repo PR).
- `RootForgeToolkitActivity` + drawer category.
- Manifest: merge USB device filter, bridge service, `queries` for `ai.droidcommand.app`.
- USB chooser vs OTG MSC.
- Keep RootForge’s own APK building in RF CI until sunset.
- Tests: VS assembleDebug; RF `:core:test`; smoke that TermuxActivity still launches (instrumented if a device exists; otherwise compile-only).

**Exit:** one APK sideloads; terminal works; toolkit screens open; **not** yet claimed on real USB.

### Milestone 3 — Bridge + DCA

- Bridge service live in `com.termux`.
- Follow-up PR on droidcommand-ai: bind by action, accept `com.termux`.
- Capabilities payload includes merged app identity.
- Pairing UI on RootForge Settings screen, default **off** (already).

**Exit:** a debug DCA build can `devices.list` against the merged APK on an emulator or phone; `RUN_COMMAND` still works.

### Milestone 4 — Embedded Termux:API + Settings toggle

- Add `:termux-api-lib` (submodule pin `v0.53.0`).
- Merge permissions/features (`required=false`).
- Components disabled by default; toggle in `VictorSuiteSettingsActivity` + upstream Settings row.
- Receiver gate + SocketListener lifecycle.
- Overlay helper (patched C binary in `jni`/`assets` for the four ABIs, or shim for a subset).
- On: enable components, install helper, per-command runtime permission (existing activity).
- Off: disable components, stub helper, `revokeSelfPermissionsOnKill` on API 33+, deep-link for older APIs and special grants.
- Rewrite `isTermuxAPIAppInstalled()` to the toggle.
- Manual matrix: clipboard, battery, toast, notification (no listener), then camera/location **only if** the tester grants; SMS **opt-in tester**.
- Document Play-store incompatibility in README.

**Exit:** with toggle on, `pkg install termux-api` (MIT scripts) + overlay helper → `termux-battery-status` and `termux-clipboard-get` work without `com.termux.api` installed. Toggle off → commands error and receiver does not run.

### Milestone 5 — Harden and document

- README (both repos): architecture diagram, CLI checkout, APK vs CLI vs desktop.
- `LICENSE.md` update (Termux:API, RootForge).
- Sunset plan for `ai.rootforge.os` APK (or keep as slim toolkit for hosts that must not be Termux).
- Mapping table Kotlin flash/module vs `rootforge` CLI (no silent behaviour drift).
- Do **not** raise `targetSdk` in this program.

### Milestone 6 — PC-first (after the phone APK is real)

Already mostly built as RootForge `:desktop` + Docker (PR #49). Post-merge work is **not** “embed Termux in Compose Desktop”. It is:

- Desktop-specific IA (multi-window, host device list, udev/Windows driver docs — already in `docs/DESKTOP.md`).
- Keep `:toolkit-ui` free of `android.*` (today it uses Android Compose Material3; desktop already compiles those screens with Compose Multiplatform). Avoid Termux Activities in that source set.
- Optional later: desktop talks to a phone running the merged APK over the **bridge** (Messenger does not exist on PC; use the headless HTTP API / SSH node). That is a new feature, not required to “keep PC possible”.

**The merge keeps PC-first possible by not putting Termux, zerocore, or Termux:API on the `:core` / `:toolkit-ui` / `:desktop` class path.**

---

## 12. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| GPLv3 APK linking unlicensed RootForge Kotlin | Legal blocker | License RootForge before Milestone 2 ships an APK to anyone but the owner. |
| `com.termux` + USB host `required=true` | Drops devices | Flip to false in M1. |
| Compose minSdk vs Termux 23 | May force 24/26 | Spike in M0; Termux:API is already 24. |
| USB claim conflict (MSC vs ADB) | Bricked UX / stuck interface | Explicit chooser; never auto-claim. |
| Helper still pointing at `com.termux.api` | `termux-*` silently fail | Overlay helper is part of M4 exit criteria; add a `termux-api --version` / `am` dry-run check in Settings. |
| Play policy / SMS | Store listing impossible | Sideload only; do not pretend the toggle sanitises the manifest. |
| `targetSdk 35` vs Termux 28 | Breaks `am broadcast`, storage, bootstrap | Do not raise targetSdk with RootForge. |
| Dual ADB stacks confuse users | Support load | Naming: “This device” vs “USB target”. |
| `sharedUserId` + extra permissions | Play/OEM scanners, OEM kill | Expected for a Termux fork; sideload. |
| Official Termux:API installed beside VS | UID mismatch, two receivers | Detect and explain; overlay helper wins for our PREFIX. |
| `revokeSelfPermissionsOnKill` not on API 23–32 | Toggle off still “has camera” in system Settings | Honest copy + deep-link; functional disable via disabled receiver. |
| Notification listener / WRITE_SETTINGS survive toggle | Privacy | Document; extra toggles that only deep-link. |
| Private source vs GPLv3 distribution | Recipients’ rights | Offer corresponding source with APKs. |
| RootForge desktop compiles VS-unrelated `:app` USB into nothing | None if split is done | Split modules in M2. |
| CI time (NDK Termux + Compose) | Slow VS workflow | `includeBuild` only `:core` + toolkit modules; do not build `:desktop` on VS CI. |
| ZeroTermux engine APK missing | Features already fail-soft | Unchanged. |
| Real USB / Termux:API hardware untested | Honesty | Keep RF’s Certain/Likely/Guessing language in READMEs. |

---

## 13. Open decisions for the owner

1. **Combined product name** — keep launcher “VictorSuite”, switch to “RootForge”, or a new name? Package stays `com.termux` regardless.
2. **RootForge license** — GPL-3.0-or-later (simplest with Termux) vs Apache-2.0 for Kotlin `:core`/desktop and GPL for scripts/ISO. Must pick before distributing a merged APK.
3. **Sunset `ai.rootforge.os` APK?** — keep a slim toolkit APK for a host phone that should *not* replace Termux, or merged APK only?
4. **minSdk** — stay 23 (spike Compose) or raise to 24 (Termux:API) / 26 (RootForge today)?
5. **Git consumption** — submodule of rootforge-os vs `includeBuild` of a documented sibling directory vs publishing `:core` to Maven?
6. **Brand of the Termux:API toggle** — “Termux:API”, “Device API”, or “Android API commands”? Default **off** is recommended regardless.
7. **SMS / call log / camera in v1** — embed the full Termux:API surface, or ship a reduced set (clipboard, battery, toast, notification, wifi, vibrate, torch, brightness, share) and leave SMS/camera/location behind a second “sensitive APIs” toggle?
8. **Play-safe flavour** — never, or a later stripped flavour? Recommendation: never for the full merge; sideload only.
9. **Corresponding source** — make VictorSuite public, or keep private and attach source archives to APK releases?
10. **DCA bind follow-up** — owner to schedule a droidcommand-ai PR for action-based bind; this repo cannot ship a working Messenger pair without it (self-test via a tiny debug binder is possible).
11. **Keep ZT TCP 19951?** — out of scope, but confirm it stays.
12. **Helper strategy for M4** — invest in patched C `termux-api` for all ABIs up front, or MIT shim first (no `termux-usb` FD passing) then C helper?
13. **Raise VictorSuite `targetSdk` later?** — not in this program; confirm.
14. **F-Droid** — out of scope unless the owner wants a public, reproducible, blob-stripped flavour.

---

## 14. Recommended approach (short)

Keep **VictorSuite as the APK home** (`com.termux`, GPLv3, terminal-first). Consume RootForge’s **Kotlin `:core` + toolkit UI/Android** through a submodule/composite build. Leave RootForge public as the **CLI / ISO / desktop / Docker** home so `git clone` + `PYTHONPATH` never needs the APK. Embed **Termux:API as a library** with a **default-off Settings toggle**, a **retargeted helper**, disabled components, and honest permission revoke (platform-limited). Do not target Play. License RootForge before anyone else receives the APK. Split RootForge’s Android module so desktop never sees Termux. Point droidcommand-ai at the existing bridge **action** inside `com.termux` while leaving `RUN_COMMAND` untouched.
