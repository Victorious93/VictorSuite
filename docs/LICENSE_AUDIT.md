# Licence audit: source-less prebuilt libraries

Audit date: 2026-10-28. Scope: the three `app/libs` aars that shipped without source
(`stub.aar`, `xh_lib.aar`, `z_file.aar`) and the EOL `log4j-1.2.17.jar`. VictorSuite is GPL-3.0-only, so §6 means
we have to be able to give out the corresponding source for every GPL object code we ship.

## Summary

| Library | Origin | Licence | Used by VictorSuite | Action |
|---|---|---|---|---|
| `stub.aar` | Termux:X11 `shell-loader/stub` | GPL-3.0 | Yes, indirectly. `termux-x11.aar` `CmdEntryPoint` needs these hidden-API stubs to compile and link | **Done:** replaced with vendored source module `stub/` (PR #79) |
| `z_file.aar` | ZFileManager (zippo88888888, Apache-2.0), modified by the ZeroTermux author (hanxinhao000), with a `com.zp.z_file.zerotermux` package added | Apache-2.0 base + GPL-3.0 ZeroTermux changes, so GPL-3.0 as a whole | Yes, in 21 files | **Recommended:** vendor the source as a module (see below) |
| `xh_lib.aar` | ZeroTermux author's private helper library (`com.example.xh_lib` + `com.blockchain.ub`) | No licence stated; the only published source is inside a GPL-3.0 ZeroTermux copy | Heavily, in about 170 files (`UUtils`, `LogUtils`, `SaveData`, dialogs, `BaseHttpUtils`) | **Don decides:** vendor the close-match source, or rewrite it as VictorSuite-owned code |
| `log4j-1.2.17.jar` | Apache log4j 1.x | Apache-2.0 | Only as the backend of `slf4j-log4j12`, the slf4j binding that Apache FtpServer/MINA log through. No direct imports | **Done:** removed, with slf4j bound to `org.slf4j:slf4j-android` (MIT) (PR #78) |

## stub.aar

- The manifest package is `com.termux.x11.stub`. It contains 7 classes: `android.app.ActivityThread`, `ContextImpl`,
  `IActivityManager`, `android.content.IIntentReceiver` (+`Stub`), `IIntentSender` and `android.content.pm.IPackageManager`.
- It stubs the **hidden Android framework APIs** that `termux-x11.aar`'s `CmdEntryPoint` calls when it runs
  through `app_process`. Each stub method throws `RuntimeException("STUB")`, and the real framework classes replace them at runtime.
- javap signatures are an exact match for https://github.com/termux/termux-x11/tree/6dfb8b4a4cc887bc329284d31eb049d596bd1208/shell-loader/stub (GPL-3.0).
- Fix: the source now lives in `stub/` and is built as `:stub`. It stays an `implementation` dependency, so runtime and R8 behaviour do not change.
  Possible follow-up: make it `compileOnly` with `-dontwarn` rules, as upstream does, after testing X11 on a device.

## z_file.aar

- Upstream base: https://github.com/zippo88888888/ZFileManager (Apache-2.0, per its README badge). The ZeroTermux build
  adds `com.zp.z_file.zerotermux.*` (`ZTConfig`, `StartTarGzListener`), `InstallTarData`, `InstallModuleDialog`,
  backup/restore dialogs and Chinese/English resources.
- Matching source: the `z_file/` module in https://github.com/msmt2017/new_ZeroTermux (commit `f68fd87`, a GPL-3.0
  ZeroTermux copy). Every top-level class in the aar has a source file there. The only exceptions are Kotlin classes declared inside other files
  (`ZFileContent.kt`, `ZFileListener.kt`), which is the expected pattern.
- What VictorSuite uses (21 files): `zerotermux.ZTConfig` (`getCloseListener`, `getZ7Listener`, in TermuxActivity,
  LocalReceiver and BusyBoxManager), `StartTarGzListener`, `util.LogUtils`, `util.ZFileUUtils`, `util.InstallTarData`,
  `ui.dialog.{InstallModuleDialog,SwitchDialog,LoadingDialog}`, `ui.ZFileListFragment`, `listener.ZFileImageListener`,
  `content.{ZFileConfiguration,ZFileBean}`, `common.ZFileManageHelp`, `bean.DataBean`, and `R.drawable.ic_zfile_other`.
  This is the backup/restore file picker and the tar/7z restore flow, so the library is not dead code.
- Note: z_file depends on `com.hzy:un7zip` (p7zip JNI wrapper; LGPL p7zip). Its licence should be listed in the notices too.
- **Recommendation:** vendor `new_ZeroTermux/z_file` as a `:z_file` module, keeping ZFileManager's Apache-2.0 notice and
  crediting ZeroTermux (GPL-3.0). Build it in CI and diff its classes against the aar before deleting the aar. This is a
  medium-sized change (Kotlin plus resources, and it pins un7zip to a fixed version instead of `+`), so it belongs in its own PR.

## xh_lib.aar

- The manifest package is `com.example.xh_lib` (Android Studio's default library name). It also contains `com.blockchain.ub.*`
  classes (city picker, `BaseDialogCentre/Down/Left`, `BaseHttpUtils`, `SaveData`), which look like code copied from the author's other projects.
- No licence text, no upstream repository and no publication. The nearest source is the `xh_lib/` module in
  `msmt2017/new_ZeroTermux`, but it is **not the same revision**. That copy moves the `com.blockchain.ub` dialogs and http utils to
  `com.example.xh_lib.dialog` / `http_utils`, so the package names VictorSuite imports are missing from it.
- What VictorSuite uses: `utils.UUtils` (about 158 references: context, toast, string, dp and file helpers), `utils.LogUtils` (51),
  `utils.SaveData` (prefs), `com.blockchain.ub` `BaseDialogDown`/`BaseDialogCentre` (dialog base classes, 15),
  `BaseHttpUtils`/`HttpResponseListenerBase` (7), `ScanActivity`, `BaseThemeActivity`, `BaseActivity`,
  `StatusBarCompat`, `ListBaseAdapter`/`ViewHolder`, `XHApplication`, and two resources. The city picker and the fingerprint (`zw`) code look unused.
- **Options for Don:**
  1. *Vendor and adapt* the `new_ZeroTermux/xh_lib` source as a module, adding the `com.blockchain.ub` classes back (or
     changing imports to the new packages). It is GPL-3.0 through ZeroTermux, so this fixes compliance quickly, but the provenance is still second-hand.
  2. *Rewrite into VictorSuite* (recommended long-term): a small owned `com.termux.victor.util` with the few helpers
     actually used (`UUtils`, `LogUtils`, `SaveData`, two dialog bases, an OkHttp wrapper) and a mechanical import change across about 170 files.
     This also finishes the debranding, but it is a big change that needs on-device testing.
  3. Ask hanxinhao000 (ZeroTermux author) to publish xh_lib's source and licence it explicitly.

## Decisions for Don

1. xh_lib: option 1 (vendor), 2 (rewrite) or 3 (ask upstream).
2. z_file: approve vendoring `new_ZeroTermux/z_file` as a module.
3. Also still open from THIRD_PARTY_NOTICES: `FHttpServer.aar` licence and `jlan-5.jar` licence.
