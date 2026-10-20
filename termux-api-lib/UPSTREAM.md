# termux-api-lib

Vendored copy of the Termux:API app, built as a library so it runs inside the VictorSuite APK
(`applicationId` `com.termux`) instead of as the separate `com.termux.api` app.

- Upstream: https://github.com/termux/termux-api
- Tag / commit: `v0.53.0` / `6c8eaa223bcc387e6b28146d340acb20712aa3e7`
- License: GPLv3 (upstream states GPLv3 in its README; it has no LICENSE file)

## What differs from upstream

Every change is marked `// VictorSuite add {@ ... // @}` or `// VictorSuite modify {@ ... // @}`, except
the items below that are files removed or added as a whole.

- Removed: `TermuxAPIMainActivity`, the whole `settings` package, `TermuxAPIApplication`, the launcher
  alias, and the matching layouts, menu, preference XML and launcher icons. VictorSuite Settings is the UI.
- Added: `TermuxApiEmbed` (on/off switch, off by default).
- `AndroidManifest.xml`: rewritten. Every component is `android:enabled="false"`; `TermuxApiEmbed`
  enables them at runtime. `ReportActivity` entries are left to the app manifest.
- `SocketListener` and `TermuxApiReceiver` refuse requests while the setting is off.

## Updating

Re-copy `app/src/main/java/com/termux/api` and `app/src/main/res` from the new upstream tag, then redo the
changes above (search for `VictorSuite`). Update the tag and commit in this file.

The `termux-api` shell helper (MIT, https://github.com/termux/termux-api-package) is not vendored.
