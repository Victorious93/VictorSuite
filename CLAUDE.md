# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build commands

```sh
# Debug APK (all ABIs)
./gradlew :app:assembleDebug --stacktrace

# Single-module build
./gradlew :termux-shared:assembleDebug

# Unit tests (JVM only — no device required). Only :app has unit tests
# (app/src/test: remote-control, hak5, ZT socket auth, FTP permissions, plus the two
# upstream activity tests). Run in CI after the debug build.
./gradlew :app:testDebugUnitTest

# Instrumented tests (requires a connected device or emulator; termux-shared/src/androidTest)
./gradlew :termux-shared:connectedAndroidTest

# Full clean
./gradlew clean
```

The `TERMUX_PACKAGE_VARIANT` env var selects the bootstrap variant baked into the APK (`apt-android-7` by default, or `apt-android-5`). The NDK version is set in `gradle.properties` (`ndkVersion`) and can be overridden at build time via `JITPACK_NDK_VERSION`.

Checkstyle: `config/android-checkstyle.gradle` defines a `checkstyle` task (`ignoreFailures = true`), but as of 2026-10-15 no build file applies it, so `./gradlew checkstyle` is probably not available (not run to confirm).

SDK levels (`gradle.properties`): minSdk 23, **targetSdk 28 (intentional: keeps Termux exec / `$PREFIX` working; do not raise it)**, compileSdk 36, NDK 29.0.14206865. App version `0.118.3.55` (versionCode 118) in `app/build.gradle`.

CI (`.github/workflows/android.yml`, "Build") runs `:app:assembleDebug --stacktrace` and then `:app:testDebugUnitTest --stacktrace` on push to `main`, PRs targeting `main`, and manual dispatch, using JDK 17, Android SDK platform-36 / build-tools-36.0.0, NDK 29.0.14206865. Lint and instrumented tests are not run in CI. APK upload is `continue-on-error` so artifact quota can't fail the job. Latest `main` runs passed (checked 2026-10-15).

## Architecture overview

Product direction (`docs/SUITE_PLAN.md`, planning only): VictorSuite and RootForge OS (`Victorious93/rootforge-os`) stay **two separate APKs** in one suite (shared Kotlin library, shared JSON command schema, deep-link handoff). `docs/MERGE_PLAN.md` (one-APK plan) is superseded/stub. `docs/AI_CLIS.md` documents `scripts/ai-cli-setup.sh` (not yet run on a real device).

VictorSuite is a Termux fork (keeps `applicationId com.termux` and the `RUN_COMMAND` contract) built on top of an inherited Termux-derived codebase. It is a five-module Android Gradle project:

| Module | Role |
|---|---|
| `terminal-emulator` | Pure-Java/JNI VT100/xterm terminal state machine; no Android UI dependencies. |
| `terminal-view` | Android `View` that renders a `TerminalEmulator` and handles input. Depends on `:terminal-emulator`. |
| `termux-shared` | Shared utilities used by both `:app` and companion Termux apps (file utils, logger, settings, crash handling, JNI helpers, local Unix socket). |
| `termux-api-lib` | Vendored copy of the Termux:API app (`termux/termux-api` v0.53.0, GPLv3) built as a library so it runs inside the `com.termux` APK. See "Embedded Termux:API" below and `termux-api-lib/UPSTREAM.md`. |
| `app` | The main application. Contains all VictorSuite additions under `com.termux.zerocore`. |

### `app` source layout

Under `app/src/main/java/com/termux/`:

- **`app/`** — upstream Termux entry points (`TermuxApplication`, `TermuxActivity`, settings fragments, terminal I/O, broadcast/event handling). Minimal VictorSuite changes here.
- **`zerocore/`** — all inherited and VictorSuite additions. Organised by feature area (see below).
- **`filepicker/`** — file-picker Activity.
- **`test/`** — small utility class used from unit tests.

### `zerocore` feature areas

- **`config/mainmenu/`** — drawer menu registration. `MainMenuConfig.init()` builds `MainMenuCategoryData` lists from individual `*ClickConfig` objects. To add a menu item, create a `MainMenuClickConfig` impl and register it here.
- **`config/ztcommand/`** — `ZTSocketService` runs a loopback-only TCP server on port 19951 that accepts JSON command packets. Every request must start with the per-install token in `~/.termux/zt.token` (`ZTCommandAuth`); the bundled `zt` helper sends it, and older clients that omit it are refused. Commands are dispatched through `ZTConfig` impls (each has a numeric `getId()` and returns a shell command string). `ZTCommandConfigStore` persists command definitions.
- **`zero/engine/`** — `ZeroCoreManage` bridges to an optional external APK (`com.xinhao.zerocoremanage`) via reflection/`createPackageContext`. If that APK is absent the engine calls fail silently; the rest of the app continues working.
- **`plugin/`** — plugin framework (see `PLUGINS.md`). Key files: `PluginRegistry`, `PluginManifest`, `PluginConfigStore` (encrypted SharedPreferences), `PluginLogger` (JSONL audit log), `PluginProcessLauncher`. Enable path: `register → acknowledgeAuthorizedUse → enable`; both registry and launcher enforce the invariant independently.
- **`deepseek/`** — former in-app AI chat (DeepSeek and Claude API clients, SQLite sessions). No longer reachable from the UI: the drawer AI button and Settings AI entry launch DroidCommand AI via `zerocore/droidcommand/DroidCommandAi`. The package is dead code except `markdown/MarkDownAPI`, which `TermuxActivity` still uses; delete the rest once a build can verify it.
- **`back/`** — backup/restore UI (`BackViewModel`, `RestoreViewModel`). Supports `.tar.gz`, `.tar.bz2`, `.tar.xz`, `.tar.Z` archives.
- **`http/`** — `NanoHttpdServer`. **`otg/`** — `OTGManager`. Other dirs not listed here exist too (`shell/`, `zip/`, `developer/`, `keybord/`, `bosybox/`, etc.); inspect before assuming.
- **`ftp/`** — embedded FTP server: `new_ftp/` (Apache FtpServer via `FtpService`). The old SwiFTP-based `FsService` was removed (it was never started and had an always-true guest-login check).
- **`http_service/`** — embedded HTTP/ttyd service.
- **`scrcpy/`** — scrcpy screen-mirroring integration.
- **`utermux_windows/qemu/`** — QEMU/UTermux Windows (x86 VM) integration.
- **`settings/`** — VictorSuite-specific settings activities (X11, timers, online server, install/uninstall).
- **`adb/`** — wireless ADB shell integration.
- **`libsu/`** — root shell integration via `libsu` (topjohnwu).

### Embedded Termux:API

`:termux-api-lib` replaces the separate `com.termux.api` app. Facts that are easy to get wrong:

- Off by default. Every component in its manifest is `android:enabled="false"`. `TermuxApiEmbed.setEnabled()` (`com.termux.api`) stores the setting, enables or disables all `com.termux.api.*` components through `PackageManager`, and starts `SocketListener`. `TermuxApplication.onCreate()` calls `TermuxApiEmbed.init()`.
- The switch is in `VictorSuiteSettingsActivity` ("Termux:API (built in)"). The stock Settings "Termux:API" row still targets the companion app and stays hidden without it; `TermuxUtils.isTermuxAPIAppInstalled()` still means the companion app, not the embed.
- `TermuxApiReceiver` and `SocketListener` refuse requests while the setting is off.
- Upstream's launcher/main/settings activities and `TermuxAPIApplication` were removed. Local changes are marked `// VictorSuite add {@` / `modify {@`; to update from upstream, follow `termux-api-lib/UPSTREAM.md`.
- The manifest merges Termux:API's 33 permissions (SMS send, call, camera, mic, location, all-files, ...) into the APK whether or not the switch is on. Not Play-compatible.
- Helper: the `termux-api` shell helper (`pkg install termux-api`, MIT, separate repo) connects to the socket `com.termux.api://listen` first on Android below 14, and the embedded `SocketListener` binds that name, so it should work there unmodified. On Android 14+ (or when the app process is not running) it falls back to `am broadcast -n com.termux.api/.TermuxApiReceiver`, which does not exist here. That retarget is not done yet.
- Untested on a device as of this writing: only the CI build has run.

### Remote JSON manifests

`remote/` holds three JSON files served via `raw.githubusercontent.com`:

- `remote/plugins/main.json` — plugin catalogue consumed by the plugin framework.
- `remote/repository/main.json` — module/bootstrap repository listing.
- `remote/on_line_sh/main.json` — online script catalogue.

All three follow the same envelope: `{ "code": 200, "data": [...], "ip": "<raw base URL>", ... }`. Edits to these files are the normal path for publishing new plugins, modules, or scripts — no code change needed.

## Code conventions

VictorSuite changes to upstream Termux files are wrapped in comment markers so they are easy to identify and revert:

```java
// VictorSuite add {@
... new code ...
// @}

// VictorSuite modify {@
... changed code ...
// @}
```

The codebase is mixed Java and Kotlin. New `zerocore` features should be written in Kotlin; existing Java files can stay Java unless there is a reason to convert.

`app/build.gradle` bundles `scripts/ai-cli-setup.sh` as an app asset (see `assets.srcDirs`). Changes to `scripts/` are picked up automatically at the next build.

## Gotchas
- `allowBackup` is `false`: Android backup (Auto Backup / `adb backup`) would otherwise copy `$HOME` (SSH keys, `zt.token`). The in-app backup/restore under `zerocore/back/` is unaffected.
- `applicationId` is `com.termux`: VictorSuite cannot be installed alongside upstream Termux.
- Signing: debug builds use the committed `app/victorsuite-debug.keystore` (public `android` passwords, deliberately not a secret, so debug fingerprints are stable across machines/CI). Release builds sign only when `SIGNING_KEYSTORE_FILE` or `SIGNING_KEYSTORE_BASE64` plus `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD` are set in the environment; otherwise release APKs are unsigned. Never commit real release keys or passwords.

## Don's standing rules (apply to every change)
- Work on a branch and open a PR. Never push to `main`.
- Never merge a PR, and never ask to merge one, until every test/check on the PR passes.
- Never commit secrets (API keys, tokens, keystore passwords, `.env` files, credentials).
- Don't claim something works unless it was actually tested; say what was and wasn't run.
