# VictorSuite application ID: `com.victorious.suite`

Owner rule: no APK may carry "Termux" in its name or ID, and VictorSuite must
install alongside the real Termux and Termux:API without interfering.

## What changed

| Item | Before | Now |
|---|---|---|
| `applicationId` (app/build.gradle) | implicit `com.termux` (from `namespace`) | `com.victorious.suite` |
| Java/R `namespace` | `com.termux` | unchanged |
| `TermuxConstants.TERMUX_PACKAGE_NAME` / `manifestPlaceholders.TERMUX_PACKAGE_NAME` | `com.termux` | `com.victorious.suite` (all three must match) |
| `android:sharedUserId` | `com.termux` | `com.victorious.suite` (unique) |
| Providers | `com.termux.documents`, `com.termux.files`, `com.termux.sharedfiles` | `com.victorious.suite.documents`, `.files`, `.sharedfiles` |
| Permissions | `com.termux.permission.RUN_COMMAND`, `com.termux.sharedfiles.READ_WRITE` | `com.victorious.suite.permission.RUN_COMMAND`, `com.victorious.suite.sharedfiles.READ_WRITE` |
| RUN_COMMAND action / extras | `com.termux.RUN_COMMAND[_*]` | `com.victorious.suite.RUN_COMMAND[_*]` |
| Plugin labels | `Termux:API/Boot/Float/Styling/Tasker/Widget` | `VictorSuite:*` |
| Prefix | `/data/data/com.termux/files/usr` | `/data/data/com.victorious.suite/files/usr` |
| Embedded termux-api helper target | `com.termux/com.termux.api.TermuxApiReceiver` | `com.victorious.suite/com.termux.api.TermuxApiReceiver` |

Hardcoded `/data/data/com.termux/...` paths in app code, assets, scripts and
distro installers were moved to the new prefix.

**sharedUserId:** changing or removing `sharedUserId` breaks in-place updates.
That does not matter here: the application ID changes too, so this is a new
app and a fresh install. Old `com.termux` VictorSuite installs are not
upgraded. Back up data and reinstall. Official Termux plugins (`com.termux.*`,
signed with the Termux key) never shared a UID with VictorSuite, and now they
cannot try.

The fleet RUN_COMMAND binding changed. See the table at the top of
[REMOTE_CONTROL_ENVELOPE.md](REMOTE_CONTROL_ENVELOPE.md).

## Bootstrap / `$PREFIX`: runtime blocker

`app/build.gradle` `downloadBootstraps` still fetches the **upstream**
`termux/termux-packages` `bootstrap-<arch>.zip` releases, pinned by checksum.
Those binaries are compiled for `/data/data/com.termux/files/usr`. That path
appears in ELF RUNPATHs, shebangs, `dpkg`/`apt` config, `libtermux-exec` and
`login`. Under `com.victorious.suite` that directory does not exist or is not
accessible, so **the terminal shell, `pkg`/`apt`, proot-distro and the distro
installers will fail on a device until new bootstraps are built.** The app
code now points at `/data/data/com.victorious.suite/files`. Only the bootstrap
artifacts need replacing.

No in-repo bootstrap rebuild pipeline exists (there is no `docs/OWNED_SOURCES.md`
on `main`), so this needs follow-up work:

1. Fork `termux/termux-packages` and build with the package name set:
   ```sh
   # scripts/properties.sh: TERMUX_APP__PACKAGE_NAME="com.victorious.suite"
   # (older trees: TERMUX_APP_PACKAGE="com.victorious.suite")
   ./scripts/run-docker.sh ./scripts/build-bootstraps.sh --architectures aarch64,arm,i686,x86_64
   ```
   This also sets `TERMUX_PREFIX=/data/data/com.victorious.suite/files/usr`.
2. Host the four `bootstrap-*.zip` (for example as a VictorSuite release), then point
   `downloadBootstrap()`'s `remoteUrl` and the checksums in `downloadBootstraps` at them.
3. Upstream apt repos (`packages.termux.dev`) serve `com.termux`-prefixed
   `.deb`s. `pkg install` needs a package repo built for the same prefix
   (same termux-packages build, published as an apt repo), and the bootstrap
   `sources.list` must point at it.
4. The `termux-api` package's helper and any proot-distro / distro rootfs
   scripts fetched at runtime from upstream assume `com.termux`. Rebuild or
   patch them in the same pipeline.

Until then this change is build-verified only. It is **not** device-verified.
