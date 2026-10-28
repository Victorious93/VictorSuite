<!-- victorious-fleet-readme-refresh:start -->
<p align="center">
  <img src="assets/logo.png" alt="VictorSuite logo" width="200">
</p>

# VictorSuite

Android Linux environment suite for the Victorious fleet (host/shell tooling via TERMUX_IPC).

**Package id:** `com.termux`

> **Package note:** From `app/build.gradle` / manifest (`TERMUX_PACKAGE_NAME` / `namespace`). Display name is VictorSuite — do not refer to this app as Termux.

**Status:** CI green / not device-tested *(from [fleet-manifest ROADMAP](https://github.com/Victorious93/fleet-manifest/blob/main/ROADMAP.md))*

## Install / build

Prebuilt, signed APKs are attached to [GitHub Releases](https://github.com/Victorious93/VictorSuite/releases). Pick the `arm64-v8a` APK for most phones (`universal` only if needed). Releases are built by CI and are marked pre-release because they have not been device-tested.

To build from source: Gradle Android app under `app/`. JDK 17; see `gradle.properties` for SDK/NDK. Typical: `./gradlew :app:assembleDebug`.

_Steps above are taken from this repo’s build files / existing docs; anything not exercised in this refresh is unlabeled as verified on a device._

## Victorious fleet

- Monorepo: [https://github.com/Victorious93/victorious-fleet](https://github.com/Victorious93/victorious-fleet)
- Fleet site: [https://victorious-framework.pages.dev](https://victorious-framework.pages.dev)

<!-- victorious-fleet-readme-refresh:end -->

<div align="center">

# VictorSuite

**An Android Linux environment and toolkit — containers, backup/restore, plugins, X11, AI coding agents and automation.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE.md)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/language-Java%20%2F%20Kotlin-orange)
![Status](https://img.shields.io/badge/status-private%20fork-lightgrey)

</div>

VictorSuite is an Android Linux environment maintained by Victorious93. It keeps Termux’s `applicationId` (`com.termux`) and the public `RUN_COMMAND` contract so existing Termux tooling and intents still work.

> **Credits:** VictorSuite is a modified version of [Termux](https://github.com/termux/termux-app) and [ZeroTermux](https://github.com/hanxinhao000/ZeroTermux) by hanxinhao000 (both GPLv3). Full credits and bundled-library licences: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

It is intended for personal learning, experimentation, and technical collaboration — not as a commercial product.

> **Fleet note:** VictorSuite is the third companion Android app in [Victorious93/droidcommand-AI](https://github.com/Victorious93/droidcommand-AI)’s app fleet (alongside hackeraiETC and Pentest-Swarm-AI). Because VictorSuite keeps `com.termux` and `RUN_COMMAND` unchanged, droidcommand-AI’s `core-termux` module already reaches it — and cannot tell it apart from upstream Termux. **VictorSuite and genuine Termux cannot be installed on the same device.** See that repo’s `docs/ARCHITECTURE.md` and `docs/AUDIT_2026-09-05.md`.

## Features

Documented and present in the codebase (VictorSuite features, under the `com.termux.victor` package):

- **Linux containers** — install and switch between proot-based distributions (catalogue includes Ubuntu, Kali, Fedora, BlackArch, Kali NetHunter Rootless, and others via online-script / repository JSON).
- **Backup and restore** — archive Termux environments and user data (`.tar.gz`, `.tar.bz2`, `.tar.xz`, `.tar.Z`).
- **One-command / menu-driven setup** — drawer menu and online-script catalogue for provisioning workflows.
- **Runtime repository selection** — choose package sources for the environment.
- **Plugin framework** — register, authorize, enable, and launch user-installed tools with an audit log and encrypted settings store (see [PLUGINS.md](PLUGINS.md)). The framework does **not** download or implement specific tools.
- **Embedded services** — FTP server, HTTP/ttyd-style service, file browser integrations.
- **X11 support** — separately distributed internal-channel X11 AAR; Termux:X11–style desktop workflows.
- **scrcpy / QEMU / wireless ADB / root shell** — integrations under `victor` (scrcpy, UTermux/QEMU windows, wireless ADB, libsu).
- **In-app AI chat** — DeepSeek and Claude API clients with SQLite session storage (`victor/deepseek`).
- **AI CLI setup script** — bundled `scripts/ai-cli-setup.sh` installs pi, Copilot CLI, Codex CLI, and Hugging Face CLI (some via an Ubuntu proot container). **Not yet run on a real device** — see [docs/AI_CLIS.md](docs/AI_CLIS.md).
- **AI Coding Agents pack** — menu **ZT features → AI Coding Agents** installs Claude Code, Codex CLI, Copilot CLI, and Ollama (local or remote) via `ai-agents-pack.sh`, with `vsphone` (root/Shizuku phone-control bridge + approval gates) and `vstoken` (token minimizer / model routing). See [docs/AI_CODING_AGENTS.md](docs/AI_CODING_AGENTS.md). **Shellchecked + unit-tested locally; not device-tested.**
- **GitHub Releases** — CI publishes a signed pre-release APK after a successful signed build on `main` (pre-release because not device-tested).
- **Package id remains `com.termux`** — a brief attempt to move to `com.victorious.suite` was reverted (#62) so Termux IPC / `RUN_COMMAND` stay compatible. VictorSuite and upstream Termux still cannot share a device.
- **Prebuilt APK releases** and shared signing-key compatibility with UTermux (as noted historically in this project).

Upstream Termux capabilities (terminal emulator, packages via bootstrap, `RUN_COMMAND`) remain the foundation.

## Screenshots

> Placeholders — add device captures when you publish or document a release.

| Terminal | Drawer / main menu | Container manager | Backup/restore | Plugin list | X11 session |
|---|---|---|---|---|---|
| *TODO* | *TODO* | *TODO* | *TODO* | *TODO* | *TODO* |

## Requirements

- Android device with **API 23+** (`minSdkVersion` 23; `targetSdkVersion` 28; `compileSdkVersion` 36)
- JDK **17** for building (CI uses Temurin 17)
- Android SDK platform 36, build-tools 36, NDK **29.0.14206865** (see `gradle.properties`)
- Note: package id is `com.termux` — uninstall upstream Termux first if both would conflict

## Build

```sh
git clone https://github.com/Victorious93/VictorSuite.git
cd VictorSuite

# Debug APK (all ABIs)
./gradlew :app:assembleDebug --stacktrace

# Unit tests (JVM)
./gradlew :app:test :termux-shared:test

# Checkstyle (ignoreFailures = true in this project)
./gradlew checkstyle
```

`TERMUX_PACKAGE_VARIANT` selects the bootstrap variant baked into the APK (`apt-android-7` by default, or `apt-android-5`). NDK version is set in `gradle.properties` (`ndkVersion`) and can be overridden via `JITPACK_NDK_VERSION`.

CI (`.github/workflows/android.yml`) runs `:app:assembleDebug` on pushes/PRs to `main`.

Current `versionName` / `versionCode` (from `app/build.gradle`): **0.118.3.55** / **118**.

## Usage

1. Install a debug or release APK (sideload).
2. Open VictorSuite — first launch follows Termux-style bootstrap / setup.
3. Use the drawer menu for VictorSuite features (containers, backup, FTP, X11 settings, online scripts, AI CLIs, etc.).
4. For plugins: register from the remote catalogue → acknowledge authorized use → enable → place the entry-point binary yourself. See [PLUGINS.md](PLUGINS.md).
5. For AI CLIs from the terminal: menu **AI CLIs**, or `bash ~/ai-cli-setup.sh` after the asset is copied — details in [docs/AI_CLIS.md](docs/AI_CLIS.md).

Remote JSON catalogues (served via `raw.githubusercontent.com` from this repo):

- `remote/plugins/main.json` — plugin catalogue
- `remote/repository/main.json` — module/bootstrap listing
- `remote/on_line_sh/main.json` — online script catalogue

## Project structure

```text
app/                    Main application (Termux entry points + com.termux.victor.*)
terminal-emulator/      VT100/xterm state machine (Java/JNI)
terminal-view/          Android View rendering the emulator
termux-shared/          Shared Termux utilities
remote/                 Plugin, repository, and online-script JSON manifests
scripts/                ai-cli-setup.sh (bundled as an app asset)
docs/                   AI_CLIS.md and related notes
art/                    Icon / banner generation helpers
```

VictorSuite changes in upstream Termux files are wrapped in `// VictorSuite add {@` / `// VictorSuite modify {@` markers. Prefer **Kotlin** for new `victor` code.

## Contributing

This repository is private. If you have access:

1. Prefer small, reviewable PRs.
2. Keep Termux `applicationId` / `RUN_COMMAND` behaviour intact unless the change is intentional and documented.
3. Do not commit secrets; treat keystores and API keys as sensitive.
4. Plugin framework PRs must preserve the authorized-use gate (register → acknowledge → enable).

## Licence

VictorSuite is released under the **GNU General Public License v3** (see [LICENSE.md](LICENSE.md)), following the Termux / ZeroTermux lineage. Credits and bundled-library licences, including open compliance items, are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Exceptions called out in `LICENSE.md` include:

- Terminal Emulator for Android code — Apache-2.0 (`terminal-view`, `terminal-emulator`)
- libcore/ojluni file utilities — GPLv2 with Classpath exception
- libsuperuser `StreamGobbler` — Apache-2.0

Application icons are credited to the Alibaba Iconfont library in the historical project README.

Users are responsible for any recovery packages, modules, or third-party software they install. Do not use the project for unlawful activities.

## Acknowledgements

Built on the work of Termux, Termux:Tasker, Termux:API, Termux:Styling, Termux Packages, ZeroTermux, and many libraries listed in prior project acknowledgements (ImagePicker, VShell, AgentWeb, XXPermissions, libaums, Glide, ttyd, File Browser, Swiftp, ImmersionBar, CodeEditor, and others). Please support upstream maintainers directly when you can.

## Release retention

Each push to `main` that bumps `versionName` auto-publishes a signed pre-release. After it publishes, CI deletes all older GitHub releases (including stale drafts) so only the latest remains. Git tags are never deleted, so every past build stays traceable to its exact source (GPL).
