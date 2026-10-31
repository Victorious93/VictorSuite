# Plugin contract for fleet apps (what VictorSuite expects today)

Status: describes the code on `main` as of Oct 30, 2026. `PLUGIN_SYSTEM.md` and `PLUGIN_API.md` are the
design for later phases; anything below marked **not implemented** is from that design and is not
read by the app yet. Do not rely on it.

## 1. How a fleet app gets into the side drawer

There is no runtime discovery. VictorSuite does **not** query an intent action, does **not** read any
`<meta-data>` keys, and does **not** scan installed packages for plugins. Each fleet app is a
hard-coded drawer entry:

| Piece | Where |
|---|---|
| Click handler (`BaseMenuClickConfig`) | `app/src/main/java/com/termux/victor/config/mainmenu/config/<App>OpenClickConfig.kt` |
| Registration | `MainMenuConfig.init()` (own category, `CODE_SUITE`) |
| Default menu entry | `assets/mainmenu/{en,cn}/zt_menu_config.xml`, `click="java:<class>"`; `MenuDefaultsMerger` adds it once to existing users' menu files |
| Package visibility | `<queries><package android:name="…"/></queries>` in `AndroidManifest.xml` (inert at targetSdk 28, kept for a later bump) |
| Download link | a constant in `config/VsLinks.kt` |
| Strings | `values/strings.xml` and `values-zh-rCN/strings.xml` |

Existing entries: RootForge (`RootForgeOpenClickConfig`), VictorDrax (`VictorDraxOpenClickConfig`).
DroidCommand AI uses the same launch logic from a fixed button (`DroidCommandAi.launch`).

## 2. What the plugin app must provide

- **Launch:** an exported activity with `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`.
  VictorSuite calls `PackageManager.getLaunchIntentForPackage(<package>)` and starts it with
  `FLAG_ACTIVITY_NEW_TASK`. `VictorDraxOpenClickConfig` also falls back to an explicit
  `ACTION_MAIN`/`CATEGORY_LAUNCHER` intent for the known component if it resolves.
- **No extras** are sent. No result is read back.
- **Package name:** fixed and listed in the click config and in `<queries>`. A `.debug` package may be
  listed after the release one (RootForge and DroidCommand do this).
- **Not installed:** VictorSuite shows a dialog with an "Open download page" button that opens the
  `VsLinks` releases URL in a browser. Nothing is installed by VictorSuite.

## 3. Icon

The drawer does not load the plugin's own icon. Every fleet entry uses VictorSuite's
`R.mipmap.link_ico`. The label comes from VictorSuite's string resources, not the plugin's label.

## 4. Deep links / URI scheme

None. VictorSuite opens no custom scheme or deep link into a plugin. (`docs/SUITE_PLAN.md` plans a
deep-link handoff; not implemented.)

## 5. Signature / unsigned warning

Not checked for drawer launches. VictorSuite launches whatever app is installed under the package
name; Android's own install-time signature rules are the only check. The only signature verification
in the app today is for the VictorLaunch theme provider (`FleetThemeProviderVerifier`, pinned certs in
`FleetThemePins`), which is unrelated to drawer entries.

## 6. Other existing contracts a fleet app may use

- `com.termux.RUN_COMMAND` (upstream Termux contract, needs `com.termux.permission.RUN_COMMAND` and
  `allow-external-apps=true`).
- Remote-control envelopes over RUN_COMMAND (`docs/REMOTE_CONTROL_ENVELOPE.md`), off by default and
  gated by pairing.

## 7. Not implemented (design only, `PLUGIN_SYSTEM.md` §7)

`com.termux.victor.permission.PLUGIN`, `com.termux.victor.action.PLUGIN_LAUNCH` / `PLUGIN_RESULT` /
`PLUGIN_INSTALLED` / `PLUGIN_REMOVED`, `apkSigningCertSha256` pinning, the full-screen unsigned warning,
and `type: "apk"` catalog installs.

## 8. VictorDrax (current entry)

| Item | Value |
|---|---|
| Package | `com.victorious.victordrax` |
| Activity | `com.victorious.victordrax.MainActivity` (`com.victorious.victordrax/.MainActivity`), exported, `MAIN` + `LAUNCHER` |
| Intent action VictorSuite sends | `android.intent.action.MAIN` (launcher intent), no extras |
| Meta-data keys | none required |
| Icon | none required; drawer uses VictorSuite's `link_ico` |
| Deep links | none |
| Signature | not checked |
| If missing | dialog → `VsLinks.VICTORDRAX_RELEASES` (`https://github.com/Victorious93/victordrax/releases`, placeholder: repo is private and has no release yet) |

It is not in `docs/plugins/catalog.json`: catalog entries need a published artifact with a real
`sha256`, and the official catalog lists only plugins signed with Don's key. Add it once a release exists.

## 9. VictorDrax → VictorSuite: open a terminal session with a command (`RUN_COMMAND`)

VictorDrax uses VictorSuite as its terminal. To enter its chroot (or any other command), VictorDrax
sends the **existing** Termux `RUN_COMMAND` service intent. There is **no**
`com.victorious.suite.action.RUN_COMMAND` and none was added: the upstream contract already covers a
foreground session, a session title (`EXTRA_SHELL_NAME`), and working directory. Confirmed against
`app/.../RunCommandService.java` and `TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE`.

### 9.1 Exact names (applicationId stays `com.termux`)

| Item | Value |
|---|---|
| Service | `com.termux.app.RunCommandService` (exported) |
| Action | `com.termux.RUN_COMMAND` |
| Permission the caller must hold | `com.termux.permission.RUN_COMMAND` (protectionLevel `dangerous`) |
| Manifest gate on the service | `android:permission="com.termux.permission.RUN_COMMAND"` |

Extras (all String unless noted; keys are the full strings below):

| Extra | Type | Role |
|---|---|---|
| `com.termux.RUN_COMMAND_PATH` | String, **required** | Absolute path to the executable (must exist as a regular file under Termux paths after canonicalization) |
| `com.termux.RUN_COMMAND_ARGUMENTS` | String[] | Arguments |
| `com.termux.RUN_COMMAND_WORKDIR` | String | Working directory |
| `com.termux.RUN_COMMAND_STDIN` | String | stdin |
| `com.termux.RUN_COMMAND_BACKGROUND` | boolean | `false` (default) → terminal session; `true` → background app shell |
| `com.termux.RUN_COMMAND_RUNNER` | String | Overrides BACKGROUND: `terminal-session` or `app-shell` |
| `com.termux.RUN_COMMAND_SESSION_ACTION` | String (int as text) | See §9.2. Default `0` |
| `com.termux.RUN_COMMAND_SHELL_NAME` | String | Session title / name (shown in the session list). Also used with `SHELL_CREATE_MODE` |
| `com.termux.RUN_COMMAND_SHELL_CREATE_MODE` | String | `always` (new session) or `no-shell-with-name` (reuse existing session with that `SHELL_NAME`) |
| `com.termux.RUN_COMMAND_COMMAND_LABEL` | String | Label for logs/notifications (default `"RUN_COMMAND Execution Intent Command"`) |

### 9.2 Session actions (foreground)

From `TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_*` (passed as a stringified int in
`EXTRA_SESSION_ACTION`):

| Value | Behaviour |
|---|---|
| `0` | Switch to the new session **and** open TermuxActivity (default; use this for VictorDrax) |
| `1` | Keep the current session selected, still open the activity |
| `2` | Switch to the new session, do not open the activity |
| `3` | Keep the current session, do not open the activity |

There is no separate "session title" extra: set `com.termux.RUN_COMMAND_SHELL_NAME` (e.g. `"VictorDrax"`).

On Android 10+, bringing TermuxActivity to the foreground from a background service also needs the
user to have granted VictorSuite **Display over other apps**; otherwise the session is created but
stays behind the notification until the user taps it.

### 9.3 Enabling external apps (required or the intent is refused)

1. In VictorSuite, set in `~/.termux/termux.properties` (or the app's properties UI if present):
   ```
   allow-external-apps=true
   ```
   Property name: `TermuxConstants.PROP_ALLOW_EXTERNAL_APPS` (`"allow-external-apps"`). Default is not
   true. If it is not `"true"`, `RunCommandService` fails the command and forces an error notification.
2. Grant `com.termux.permission.RUN_COMMAND` to VictorDrax at install / runtime (dangerous permission).
3. Restart VictorSuite / reload properties after editing `termux.properties`.

### 9.4 Sample Kotlin intent (VictorDrax side)

```kotlin
// Package: com.victorious.victordrax. Requires <uses-permission android:name="com.termux.permission.RUN_COMMAND" />
// and a <queries> entry for package "com.termux" on targetSdk 30+.
fun openVictorDraxChrootInVictorSuite(context: Context, chrootEntryScript: String) {
    val intent = Intent("com.termux.RUN_COMMAND").apply {
        setPackage("com.termux")
        putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
        putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(chrootEntryScript))
        putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
        putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0") // new session + open activity
        putExtra("com.termux.RUN_COMMAND_SHELL_NAME", "VictorDrax")
        putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "VictorDrax chroot")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // harmless on a service start; kept for clarity
    }
    context.startService(intent) // or ContextCompat.startForegroundService on API 26+ if needed
}
```

`RUN_COMMAND_PATH` must point at a real file VictorSuite can execute; replace the sample paths with the
actual VictorDrax entry script once it ships under `$PREFIX`. Do **not** put a remote-control envelope
extra (`ai.droidcommand.REMOTE_ENVELOPE_JSON`) on this intent: that path is handled separately and
never falls through to command execution (`TermuxRemoteEnvelopeBinding`).

### 9.5 Gap check (why nothing new was added)

| Need | Already present? |
|---|---|
| Open a foreground session with a command | Yes: `BACKGROUND=false` + `SESSION_ACTION=0` |
| Session title / name | Yes: `EXTRA_SHELL_NAME` |
| Working directory | Yes: `EXTRA_WORKDIR` |
| Permission + user opt-in | Yes: `RUN_COMMAND` permission + `allow-external-apps=true` |

No new action, extra, or permission was added for VictorDrax.
