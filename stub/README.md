# stub

Source for what used to ship as the prebuilt `app/libs/stub.aar`.

- Origin: https://github.com/termux/termux-x11/tree/6dfb8b4a4cc887bc329284d31eb049d596bd1208/shell-loader/stub
- Licence: GPL-3.0 (Termux:X11, © Termux:X11 contributors) — see `/LICENSE.md` and
  https://github.com/termux/termux-x11/blob/master/LICENSE
- Purpose: compile-time stubs of hidden framework APIs (`android.app.ActivityThread`, `ContextImpl`,
  `IActivityManager`, `android.content.IIntentReceiver`, `IIntentSender`, `android.content.pm.IPackageManager`)
  that `termux-x11.aar`'s `CmdEntryPoint` calls. The real framework classes replace them at runtime.
- Unchanged from upstream except for this README and `build.gradle` (uses VictorSuite's SDK properties).
