# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build commands

```sh
# Debug APK (all ABIs)
./gradlew :app:assembleDebug --stacktrace

# Single-module build
./gradlew :termux-shared:assembleDebug

# Unit tests (JVM only — no device required). Only :app has unit tests
# (app/src/test: TermuxActivityTest, TermuxFileReceiverActivityTest). Not run in CI.
./gradlew :app:test

# Instrumented tests (requires a connected device or emulator; termux-shared/src/androidTest)
./gradlew :termux-shared:connectedAndroidTest

# Full clean
./gradlew clean
```

The `TERMUX_PACKAGE_VARIANT` env var selects the bootstrap variant baked into the APK (`apt-android-7` by default, or `apt-android-5`). The NDK version is set in `gradle.properties` (`ndkVersion`) and can be overridden at build time via `JITPACK_NDK_VERSION`.

Checkstyle: `config/android-checkstyle.gradle` defines a `checkstyle` task (`ignoreFailures = true`), but as of 2026-10-15 no build file applies it, so `./gradlew checkstyle` is probably not available (not run to confirm).

SDK levels (`gradle.properties`): minSdk 23, **targetSdk 28 (intentional: keeps Termux exec / `$PREFIX` working; do not raise it)**, compileSdk 36, NDK 29.0.14206865. App version `0.118.3.54` (versionCode 117) in `app/build.gradle`.

CI (`.github/workflows/android.yml`, "Build") runs only `:app:assembleDebug --stacktrace` on push to `main`, PRs targeting `main`, and manual dispatch, using JDK 17, Android SDK platform-36 / build-tools-36.0.0, NDK 29.0.14206865. Unit tests and lint are not run in CI. APK upload is `continue-on-error` so artifact quota can't fail the job. Latest `main` runs passed (checked 2026-10-15).

## Architecture overview

Product direction (`docs/SUITE_PLAN.md`, planning only): VictorSuite and RootForge OS (`Victorious93/rootforge-os`) stay **two separate APKs** in one suite (shared Kotlin library, shared JSON command schema, deep-link handoff). `docs/MERGE_PLAN.md` (one-APK plan) is superseded/stub. `docs/AI_CLIS.md` documents `scripts/ai-cli-setup.sh` (not yet run on a real device).

VictorSuite is a Termux fork (keeps `applicationId com.termux` and the `RUN_COMMAND` contract) built on top of the ZeroTermux codebase. It is a four-module Android Gradle project:

| Module | Role |
|---|---|
| `terminal-emulator` | Pure-Java/JNI VT100/xterm terminal state machine; no Android UI dependencies. |
| `terminal-view` | Android `View` that renders a `TerminalEmulator` and handles input. Depends on `:terminal-emulator`. |
| `termux-shared` | Shared utilities used by both `:app` and companion Termux apps (file utils, logger, settings, crash handling, JNI helpers, local Unix socket). |
| `app` | The main application. Contains all VictorSuite/ZeroTermux additions under `com.termux.zerocore`. |

### `app` source layout

Under `app/src/main/java/com/termux/`:

- **`app/`** — upstream Termux entry points (`TermuxApplication`, `TermuxActivity`, settings fragments, terminal I/O, broadcast/event handling). Minimal VictorSuite changes here.
- **`zerocore/`** — all ZeroTermux/VictorSuite additions. Organised by feature area (see below).
- **`filepicker/`** — file-picker Activity.
- **`test/`** — small utility class used from unit tests.

### `zerocore` feature areas

- **`config/mainmenu/`** — drawer menu registration. `MainMenuConfig.init()` builds `MainMenuCategoryData` lists from individual `*ClickConfig` objects. To add a menu item, create a `MainMenuClickConfig` impl and register it here.
- **`config/ztcommand/`** — `ZTSocketService` runs a TCP server on port 19951 that accepts JSON command packets. Commands are dispatched through `ZTConfig` impls (each has a numeric `getId()` and returns a shell command string). `ZTCommandConfigStore` persists command definitions.
- **`zero/engine/`** — `ZeroCoreManage` bridges to an optional external APK (`com.xinhao.zerocoremanage`) via reflection/`createPackageContext`. If that APK is absent the engine calls fail silently; the rest of the app continues working.
- **`plugin/`** — plugin framework (see `PLUGINS.md`). Key files: `PluginRegistry`, `PluginManifest`, `PluginConfigStore` (encrypted SharedPreferences), `PluginLogger` (JSONL audit log), `PluginProcessLauncher`. Enable path: `register → acknowledgeAuthorizedUse → enable`; both registry and launcher enforce the invariant independently.
- **`deepseek/`** — in-app AI chat (DeepSeek and Claude API clients via `AiClient` interface). Sessions stored in SQLite via `ChatDatabaseHelper`.
- **`back/`** — backup/restore UI (`BackViewModel`, `RestoreViewModel`). Supports `.tar.gz`, `.tar.bz2`, `.tar.xz`, `.tar.Z` archives.
- **`http/`** — `NanoHttpdServer`. **`otg/`** — `OTGManager`. Other dirs not listed here exist too (`shell/`, `zip/`, `developer/`, `keybord/`, `bosybox/`, etc.); inspect before assuming.
- **`ftp/`** — embedded FTP server (`FsService`/`SwiftFTP`) and a newer implementation under `new_ftp/`.
- **`http_service/`** — embedded HTTP/ttyd service.
- **`scrcpy/`** — scrcpy screen-mirroring integration.
- **`utermux_windows/qemu/`** — QEMU/UTermux Windows (x86 VM) integration.
- **`settings/`** — VictorSuite-specific settings activities (X11, timers, online server, install/uninstall).
- **`adb/`** — wireless ADB shell integration.
- **`libsu/`** — root shell integration via `libsu` (topjohnwu).

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
- `applicationId` is `com.termux`: VictorSuite cannot be installed alongside upstream Termux.
- Signing: `app/build.gradle` signs with the committed `phone.jks`, with `STORE_PASSWORD` from the environment and a fallback default password in the file. Treat this as a debug/dev key only; never add real release keys or passwords to the repo.

## Don's standing rules (apply to every change)
- Work on a branch and open a PR. Never push to `main`.
- Never merge a PR, and never ask to merge one, until every test/check on the PR passes.
- Never commit secrets (API keys, tokens, keystore passwords, `.env` files, credentials).
- Don't claim something works unless it was actually tested; say what was and wasn't run.
