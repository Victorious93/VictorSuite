# VictorSuite — architecture

Verified 2026-09-29 against a fresh clone of `main` (single squashed commit
`813d448`, "Merge pull request #7"). Read `CLAUDE.md` first for orientation
and the standing security finding; this file is the component-status
table `CLAUDE.md` points to.

## What this is

A GPL-licensed Android terminal app: ZeroTermux merged with upstream
Termux's own `termux-shared`/`terminal-emulator`/`terminal-view` modules.
Installs as package `com.termux` (per `app/build.gradle`'s
`namespace "com.termux"`, no distinct `applicationId`) — it cannot be
installed alongside real Termux on the same device, only one `com.termux`
app can be present at a time. This is why `Victorious93/droidcommand-AI`
identifies it by `versionName` shape (four numeric parts, e.g.
`0.118.3.54`) rather than by package name when choosing between Termux
and VictorSuite as a terminal target.

## Modules (`settings.gradle`)

| Module | Language | Role |
|---|---|---|
| `app` | Java-primary (307 `.java`), Kotlin layer on top (122 `.kt`) | The application: UI, plugin framework, AI-CLI integration, backup/restore, FTP/HTTP servers, X11/QEMU/VNC helpers, ADB helper, root (`libsu`) |
| `termux-shared` | Java/Kotlin | Shared Termux utilities (file handling, constants, shell env) — upstream-derived |
| `terminal-emulator` | Java | Terminal emulation core — upstream-derived |
| `terminal-view` | Java | Terminal rendering view — upstream-derived |

No dependency graph beyond `app` depending on all three library modules
(`implementation project(":terminal-view")`,
`implementation project(":termux-shared")`, and `terminal-view` itself
depending on `terminal-emulator`). No `core-*` split — everything
app-specific lives under `com.termux.zerocore.*` inside the single `app`
module.

## `app`'s major subsystems (`com.termux.zerocore.*`)

Grouped by directory, not by claimed feature — this reflects what's
actually there, not a design doc:

- **`plugin/`** — the plugin framework (`PluginManifest`, `PluginRegistry`,
  `PluginConfigStore`, `PluginLogger`, `PluginProcessLauncher`) plus its UI
  (`dialog/PluginListDialog.kt`, `PluginConfigDialog.kt`,
  `config/mainmenu/config/PluginManagerClickConfig.kt`). Infrastructure
  only — registers/gates/launches external tools the user installs and
  authorizes themselves; does not bundle, download, or automate any
  specific tool. See `PLUGINS.md` for the full design and the
  authorized-use gate (`register` → `DISABLED` → `acknowledgeAuthorizedUse`
  → `enable`), and `CLAUDE.md`'s correction note for what's actually built
  vs. what that file's own "planned" language still claims.
- **`deepseek/`** — an in-app AI chat feature (`ChatFragment`,
  `DeepSeekClient`, `ClaudeClient`, `AiClient` interface,
  `ChatDatabaseHelper`) — a bundled, tool-specific AI chat UI, a different
  and older concept from the tool-agnostic `plugin/` framework. Not
  audited beyond file presence in this pass.
- **`ftp/`** — a hand-rolled FTP server (`server/Cmd*.java`, ~30 command
  classes implementing a subset of the FTP protocol) plus a newer
  `new_ftp/` variant with its own service/DB/network-monitoring layer. Two
  overlapping FTP implementations coexist; which one is active/default was
  not determined in this pass.
- **`http_service/`** and **`http/`** — an embedded HTTP file-server
  (`HttpServer`, `NanoHttpdServer` — NanoHTTPD-based) for browser-based
  file access to the device.
- **`scrcpy/`** — a bundled scrcpy-protocol client (`Scrcpy.java`,
  `VideoDecoder`, packet models) for screen mirroring.
  **`utermux_windows/qemu/`** — a QEMU-based "run Windows" feature (dialogs
  and activity for managing a QEMU VM).
- **`back/` / `activity/…Backup…`** — backup/restore of the Termux `$HOME`
  environment (`BackupFragment`, `RestoreFragment`, `BackViewModel`).
- **`adb/`**, **`libsu/`** — ADB helper dialog and `libsu`-based root shell
  access (`com.github.topjohnwu.libsu`).
- **`config/mainmenu/`** — the app's main menu system: ~70
  `*ClickConfig` classes (Java/Kotlin), each a self-contained menu-item
  handler (X11, VNC, Headscale, Docker check, module install, font
  settings, particle/snow background toggles, and the AI-CLI/plugin-manager
  entries above). This is the app's primary extension point — a new
  feature usually means one more `*ClickConfig` class registered here.
- **`background/`** — an OpenGL ES particle/firework background renderer,
  cosmetic only.

## AI CLI integration (`scripts/ai-cli-setup.sh`, `docs/AI_CLIS.md`)

Installs four AI CLI tools (`pi` natively in Termux; GitHub Copilot CLI,
OpenAI Codex CLI, and the Hugging Face CLI inside a `proot-distro`
Ubuntu container, since none of the three publish an Android/aarch64
build). Wired into the main menu via
`config/mainmenu/config/AiCliSetupClickConfig.java`. Status: checked with
`shellcheck` and a dry run against stubbed `pkg`/`proot-distro` commands;
**not yet run on a real device** (per the doc's own words).

## Fleet integration points (how this repo is consumed elsewhere)

- **`Victorious93/droidcommand-AI`'s `core-termux` module** identifies and
  drives this app (or real Termux) over `adb`, using the public
  `com.termux.RUN_COMMAND` intent protocol — not any code from this repo.
  `TerminalSelector`/`AdbTermuxExecutor` there tell VictorSuite apart from
  Termux by `versionName` shape (a heuristic, not signing-certificate
  verification — a named follow-up in that repo's own audit).
- The `plugin/` framework is a plausible integration seat for the
  companion-APK pattern droidcommand-AI's `HACKERAI_SOURCE_AUDIT.md`
  describes for HackerAI/Pentest-Swarm-AI (register the companion app's
  entry point as a VictorSuite plugin) — this is an **unevaluated
  possibility**, not a scoped or planned integration; no droidcommand-AI
  doc currently proposes it.

## CI

`.github/workflows/android.yml` exists but its entire body is commented
out (every line prefixed `#`) — there is currently **no active CI** for
this repo. `assembleDebug`/`assembleRelease` are the commands the
commented-out workflow used to run; they are not exercised anywhere today.

## Known gaps (see `CLAUDE.md` and `BUILD_PLAN.md` for action items)

- Committed release-signing keystore + hardcoded fallback passwords in
  `app/build.gradle` (blocking-severity, unresolved as of 2026-09-29).
- No package-management UI for the plugin framework (install/update/
  remove a plugin's binary) — the browsing/enable/configure/launch UI
  exists, acquiring the binary is still entirely manual.
- Zero test coverage for the plugin framework (registry, config store,
  launcher, or UI).
- AI-CLI setup script never run on a real device.
- No active CI.
- Two overlapping FTP server implementations (`ftp/server/` and
  `ftp/new_ftp/`) with no documented reason either is preferred.
