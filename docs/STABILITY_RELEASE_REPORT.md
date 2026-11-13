# VictorSuite stability & release report — 0.118.3.63 (125)

**Verdict: NOT a validated release candidate.** Source, unit-test and build checks pass; every runtime check
(install, upgrade, cold starts, sessions, companions on a device, arm64 execution) is **BLOCKED** because this
build host had no device, no `adb`, no `/dev/kvm` (so no emulator) and no arm64 runtime. The signed release APK can
only be produced by CI (signing secrets exist only there) and was not produced here.

| | |
|---|---|
| VictorSuite source SHA | `776a6ebb15602f295b67f1fd5cd0339f74f06119` (branch `ccr-badc049d-5tuhyj`, parent `db6567740d12974dd379ea7ec1ee0543317c0954`) |
| DroidCommand AI source SHA | `271f4fb68bcf469b556777ec06c1257bdff58ae8` (parent `25c202ca0c108e4d6abd0e8be0e8c7a4a45bb99a`) |
| File-Manager SHA (unchanged) | `60f458d2852b15da8263239fb9ba4a216d9a3e48` |
| Latest distributed release | `v0.118.3.62` (124), pre-release, published 2026-11-10, built from `287b36f` |
| Version in this change | `0.118.3.63` / `125` (README, CLAUDE.md, SUITE_PLAN updated) |
| applicationId / targetSdk / prefix | `com.termux` / 28 / unchanged |
| Toolchain | JDK 21 (CI uses 17), Gradle 9.2.1, AGP per repo, Android SDK platform-36, build-tools 36.0.0, NDK 29.0.14206865 |

## Delivered artifacts (local, untracked, in `dist/`)

`termux-app_apt-android-7-debug_arm64-v8a.apk` — sha256 `6a96ce7633e1ef0f31cab73ee32665dbfd6077d720d4007eaa47f28ea77146c7`
`termux-app_apt-android-7-debug_universal.apk` — sha256 `b543a016acc9428515617dcc16174fec46323be70b299a71c46bb172589d50e0`
(`dist/SHA256SUMS`). Both: id `com.termux`, versionName 0.118.3.63, versionCode 125, targetSdk 28; arm64 APK has
`lib/arm64-v8a` (10 `.so`).

**These are debug-signed** with the committed public debug keystore (cert SHA-256
`6717c55abe9e0df8c603c9a0f3f367b6cabdb01f3e583a4ff644e5df3d4c31d3`). The production release cert of `v0.118.3.62`
is `8cbf09aa5d0d246b5a97c95d2e70dc0c5f5bd3338c399a97889109a10dce1980` (CN=VictorSuite, OU=Release). **A debug APK
cannot install over a release install** (signature mismatch) and installing it would require uninstalling the real
app, which wipes `$HOME`/`$PREFIX`, SSH keys and credentials — do not do that on a device with data. Use it only on a
disposable device or a fresh profile. The upgrade-capable artifact is the CI-signed release built from this commit
after merge (CI step "Verify release APKs and write SHA256SUMS" now produces `SHA256SUMS` and checks the signature).

## Results

| Area | Result | Evidence |
|---|---|---|
| VictorSuite unit tests | **PASS** | `./gradlew :app:testDebugUnitTest` → 39 files, **268 tests**, 0 failures/errors/skips (261 existing + 7 new `VictorFilesPanelTest`) |
| VictorSuite `:app:assembleDebug` | **PASS** | 5 APKs (arm64-v8a, armeabi-v7a, x86, x86_64, universal); `scripts/ci/verify_apk.sh` checks id/version/targetSdk/ABI libs/cert → ok |
| DroidCommand `core-remotecontrol` | **PASS** | 179 tests (incl. 5 new `TermuxIpcResponseAwaiterTest`) |
| DroidCommand `core-security`, `core-termux` | **PASS** | 305 / 80 tests |
| DroidCommand `:app:testDebugUnitTest` (`-PincludeAndroid=true`) | **PASS** | 280 tests (incl. 3 new `TermuxFleetTransportSenderTest`); `:app:compileDebugKotlin` ok |
| Lint, `termux-shared` instrumented tests | **NOT RUN** | Not run by CI either; no device |
| Clean install on disposable target | **BLOCKED** | no device/emulator |
| In-place upgrade from v0.118.3.62 + saved-data preservation | **BLOCKED** | no device; also needs the CI-signed APK (see above) |
| 10 cold starts, background/resume, recreation, sessions, keyboard, drawers, settings | **BLOCKED** | no device |
| Crash / ANR / native-crash evidence | **BLOCKED** | no device logs exist to capture; no crash was reproduced. Findings below are from static review |
| arm64 execution | **BLOCKED** | arm64 APK built and inspected only; an x86 emulator would not prove it anyway |
| Offline / revoked-permission behaviour, proot + backup/restore fixtures, soak | **BLOCKED** | no device |
| Quick-SSH security fixes (host-key pinning, encrypted passwords, redaction, migration) | **PRESERVED** | untouched; their existing tests (`QuickSsh*`, `SSHKeyUtilsTest`) are inside the 268 passing |
| Companions present/absent/error states | **PARTIAL** | JVM-level only (Robolectric `VictorFilesPanelTest`, fake-sender DC tests). Real cross-app launch/IPC: **BLOCKED** |

## Defects found and fixed (static review, no runtime reproduction)

1. **Uncaught cross-app launch failures.** `VictorFilesPanel.launch` caught only `ActivityNotFoundException`;
   `TermuxActivity.openFileManager` and `OpenPathClickConfig` started the File Manager launcher with no guard
   (the latter caught `Exception` but swallowed it with `printStackTrace` and stopped at the first package). A
   `SecurityException` from a refused/unexported component would propagate out of the terminal activity. Now
   `VictorFilesPanel.startGuarded` maps `ActivityNotFoundException`, `SecurityException` and other `RuntimeException`
   to a result, `launchApp()` falls through release → debug package, and every caller falls back to the built-in
   browser. Regression tests: `VictorFilesPanelTest`. *Not reproduced on a device; whether it could actually crash
   depends on the other app's manifest, so treat as hardening.*
2. **Stale user-facing names** ("VictorFiles") in en and zh-CN strings → "Victorious File Manager" (+ File-Manager
   link in the not-installed message). Technical ids and the `victorfiles_panel` preference file are unchanged
   (tested). The repository link `VsLinks.FILE_MANAGER_REPO` was already correct (`Victorious93/File-Manager`).
3. **Fleet-envelope path was a no-op on the DroidCommand side** (`UnwiredTermuxIpcSender`). Repaired in the
   DroidCommand change; see the integration doc. VictorSuite's side (`TermuxRemoteEnvelopeBinding`) needed no change:
   it already runs `runStartForeground()` before handling, replies on a worker thread and catches `Throwable`.
4. **Release pipeline.** The `release` job had no dependency on `build`, so a signed pre-release could publish while
   tests were failing or still running. Now `needs: build`. Gradle exiting 0 was the only test gate; now
   `scripts/ci/verify_test_results.py` fails on: no result files, unparseable (crashed-runner) files, zero tests,
   fewer than 250 tests, any failure/error, all-skipped (each negative case exercised locally). APKs are verified
   (id, versionName/Code vs `app/build.gradle`, targetSdk 28, ABI libs, release signature) and `SHA256SUMS` is
   attached to the release. Release notes no longer imply anything beyond "unit tests and APK checks passed".

## Not changed on purpose

applicationId, `$PREFIX`/HOME layout, targetSdk 28, signing identity, external-control default OFF, pairing/cert
requirements, replay/freshness checks, approvals, DroidCommand inference policy and cloud-consent. File-Manager
needed no change (panel contract matches; the Messenger bridge's `filemanager.list` remains a documented stub).
My PC / `OPEN_PC` remains unimplemented and is reported as **unavailable**, not connected.

## What remains before this can be called a release candidate

1. Merge VictorSuite + DroidCommand PRs (not done here); CI builds and signs the release for the merged SHA.
2. On a disposable device: clean install, then in-place upgrade from `v0.118.3.62` with a populated `$HOME`
   (SSH keys, `known_hosts`, a proot container, backups, plugins) and confirm all survive.
3. Ten cold starts + background/resume + rotation with logcat captured (look for ANR/native tombstones).
4. Path B/C round trips with DroidCommand installed and paired; panel open/Back/terminal-survives with
   Victorious File Manager release and debug builds.
5. An arm64 run of the arm64 APK.

## Environment notes (for reproducibility)

Built with `ANDROID_HOME=/opt/android-sdk`. The sandbox proxy blocks `gcp.mirrors.maven.org` (Maven Central's redirect
target for some artifacts) and returned HTTP 429 under concurrent Gradle runs; a user-level Gradle init script
(`~/.gradle/init.d/mirror.gradle`, not in any repo) added Google's Central mirror and pointed Robolectric at
pre-fetched `android-all-instrumented` jars for SDK 28 and 33 (`/opt/robolectric-deps`). Neither affects CI.
