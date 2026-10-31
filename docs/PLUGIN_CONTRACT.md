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

Existing entries: RootForge (`RootForgeOpenClickConfig`), Victorious ANDRAX (`AndraxOpenClickConfig`).
DroidCommand AI uses the same launch logic from a fixed button (`DroidCommandAi.launch`).

## 2. What the plugin app must provide

- **Launch:** an exported activity with `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`.
  VictorSuite calls `PackageManager.getLaunchIntentForPackage(<package>)` and starts it with
  `FLAG_ACTIVITY_NEW_TASK`. `AndraxOpenClickConfig` also falls back to an explicit
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

## 8. Victorious ANDRAX (current entry)

| Item | Value |
|---|---|
| Package | `com.victorious.andrax` |
| Activity | `com.victorious.andrax.MainActivity`, exported, `MAIN` + `LAUNCHER` (already true in victorious-andrax `main`) |
| Intent action VictorSuite sends | `android.intent.action.MAIN` (launcher intent), no extras |
| Meta-data keys | none required |
| Icon | none required; drawer uses VictorSuite's `link_ico` |
| Deep links | none |
| Signature | not checked |
| If missing | dialog → `VsLinks.ANDRAX_RELEASES` (`https://github.com/Victorious93/victorious-andrax/releases`, placeholder: repo is private and has no release yet) |

It is not in `docs/plugins/catalog.json`: catalog entries need a published artifact with a real
`sha256`, and the official catalog lists only plugins signed with Don's key. Add it once a release exists.
