<div align="center">

<img src="docs/logo-512.png" alt="VictorSuite logo" width="200">

# VictorSuite

**A Termux-based Android Linux environment with management tools: containers, backup/restore, plugins, X11, and automation.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE.md)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Min API](https://img.shields.io/badge/minSdk-23-brightgreen)
![Language](https://img.shields.io/badge/language-Java%20%2F%20Kotlin-orange)

</div>

## Overview

VictorSuite is a community-driven enhancement of the Termux ecosystem, built on Termux's terminal stack (see Credits). It keeps Termux's `applicationId` (`com.termux`) and the public `RUN_COMMAND` contract, so existing Termux tooling and intents still work.

> **Note:** because the package id is `com.termux`, VictorSuite and upstream Termux cannot be installed on the same device. Uninstall one before installing the other.

It is intended for personal learning, experimentation, and technical collaboration, not as a commercial product.

## Features

VictorSuite additions live under `com.termux.zerocore`:

- **Linux containers** — install and switch between proot-based distributions (catalogue includes Ubuntu, Kali, Fedora, BlackArch, Kali NetHunter Rootless, and others via online-script / repository JSON).
- **Backup and restore** — archive Termux environments and user data (`.tar.gz`, `.tar.bz2`, `.tar.xz`, `.tar.Z`).
- **One-command / menu-driven setup** — drawer menu and online-script catalogue for provisioning workflows.
- **Runtime repository selection** — choose package sources for the environment.
- **Plugin framework** — register, authorize, enable, and launch user-installed tools with an audit log and encrypted settings store (see [PLUGINS.md](PLUGINS.md)). The framework does **not** download or implement specific tools.
- **Embedded services** — FTP server, HTTP/ttyd-style service, file browser integrations.
- **X11 support** — separately distributed internal-channel X11 AAR; Termux:X11–style desktop workflows.
- **scrcpy / QEMU / wireless ADB / root shell** — integrations under `zerocore` (scrcpy, UTermux/QEMU windows, wireless ADB, libsu).
- **AI via DroidCommand AI** — the drawer's AI button and the Settings AI entry launch the separate [DroidCommand AI](https://github.com/Victorious93/droidcommand-ai) app (`ai.droidcommand.app`). The old in-app DeepSeek/Claude chat UI is no longer reachable; its code under `zerocore/deepseek` is unreferenced and still present (`MarkDownAPI` there is used by `TermuxActivity`).
- **AI CLI setup script** — bundled `scripts/ai-cli-setup.sh` installs pi, Copilot CLI, Codex CLI, and Hugging Face CLI (some via an Ubuntu proot container). **Not yet run on a real device** — see [docs/AI_CLIS.md](docs/AI_CLIS.md).

Upstream Termux capabilities (terminal emulator, packages via bootstrap, `RUN_COMMAND`) remain the foundation.

## Screenshots

Screenshots coming soon.

## Requirements

- Android device with **API 23+** (`minSdkVersion` 23; `targetSdkVersion` 28; `compileSdkVersion` 36)
- JDK **17** for building (CI uses Temurin 17)
- Android SDK platform 36, build-tools 36, NDK **29.0.14206865** (see `gradle.properties`)

## Install

No tagged releases are published on GitHub yet. To install:

1. Build a debug APK from source (see [Build from source](#build-from-source)); it is written under `app/build/outputs/apk/debug/`.
2. Sideload it onto your device (for example `adb install <apk>`).
3. Uninstall upstream Termux first if it is present (same package id).

## Build from source

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

Current `versionName` / `versionCode` (from `app/build.gradle`): **0.118.3.54** / **117**.

## Usage

1. Install the APK (see [Install](#install)).
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
app/                    Main application (Termux entry points + com.termux.zerocore.*)
terminal-emulator/      VT100/xterm state machine (Java/JNI)
terminal-view/          Android View rendering the emulator
termux-shared/          Shared Termux utilities
remote/                 Plugin, repository, and online-script JSON manifests
scripts/                ai-cli-setup.sh (bundled as an app asset)
docs/                   AI_CLIS.md and related notes
art/                    Icon / banner generation helpers
```

VictorSuite changes in upstream Termux files are wrapped in `// VictorSuite add {@` / `// VictorSuite modify {@` markers. Prefer **Kotlin** for new `zerocore` code.

## Contributing

This repository is private. If you have access:

1. Prefer small, reviewable PRs.
2. Keep Termux `applicationId` / `RUN_COMMAND` behaviour intact unless the change is intentional and documented.
3. Do not commit secrets; treat keystores and API keys as sensitive.
4. Plugin framework PRs must preserve the authorized-use gate (register → acknowledge → enable).

## Licence

VictorSuite is released under the **GNU General Public License v3** (see [LICENSE.md](LICENSE.md)), following the licenses of the projects it builds on (see Credits).

Exceptions called out in `LICENSE.md` include:

- Terminal Emulator for Android code — Apache-2.0 (`terminal-view`, `terminal-emulator`)
- libcore/ojluni file utilities — GPLv2 with Classpath exception
- libsuperuser `StreamGobbler` — Apache-2.0

Application icons are credited to the Alibaba Iconfont library in the historical project README.

Users are responsible for any recovery packages, modules, or third-party software they install. Do not use the project for unlawful activities.

## Acknowledgements

Built on the work of Termux, Termux:Tasker, Termux:API, Termux:Styling, Termux Packages, ZeroTermux, and many libraries listed in prior project acknowledgements (ImagePicker, VShell, AgentWeb, XXPermissions, libaums, Glide, ttyd, File Browser, Swiftp, ImmersionBar, CodeEditor, and others). Please support upstream maintainers directly when you can.
