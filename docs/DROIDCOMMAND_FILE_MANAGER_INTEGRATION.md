# VictorSuite ↔ DroidCommand AI ↔ Victorious File Manager — integration

Verified against source on 2026-11-13 (VictorSuite `db65677`, droidcommand-AI `25c202c`, File-Manager `60f458d`).
**No path below has been run on a device or emulator.** The build host had no `/dev/kvm`, no adb device and no
arm64 runtime. Every status is therefore "source-verified / JVM-tested", never "device-verified".

Naming: user-facing text says **Victorious File Manager**; the repository is
[`Victorious93/File-Manager`](https://github.com/Victorious93/File-Manager). Legacy identifiers
(`VictorFilesPanel`, `victorfiles_*` resources, the `victorfiles_panel` preference file, `ai.victorious.filemanager.*`
package/action/extra strings) are real technical contracts and are intentionally **not** renamed.

## Contract / status table

| # | Path | Production wiring | Identity / permission | Status |
|---|---|---|---|---|
| A | VictorSuite AI button → DroidCommand AI | `DroidCommandAi.launch()` → `getLaunchIntentForPackage` for `ai.droidcommand.app`, then `.debug`; `FLAG_ACTIVITY_NEW_TASK`. Launcher only, no extras, no result. | None needed. Absent app → dialog with Releases link; `ActivityNotFoundException` / `SecurityException` → toast. | Source-verified. "Return" is the system Back/Recents (separate task); nothing calls back. |
| B | DroidCommand terminal tool → `RUN_COMMAND` → VictorSuite → PendingIntent result | DC `AppModule` → `ClientBackedTermuxExecutor` → `AndroidPendingIntentRunCommandClient`: `startForegroundService(com.termux/com.termux.app.RunCommandService)`, `RUN_COMMAND_BACKGROUND=true`, mutable `RUN_COMMAND_PENDING_INTENT` (broadcast). Result `Bundle` under extra `result` (`stdout`, `stderr`, `exitCode`, `err`, `errmsg`). | Manifest permission `com.termux.permission.RUN_COMMAND` (runtime grant by user) **and** `allow-external-apps=true` in `~/.termux/termux.properties`. | Source-verified; **not device-verified**. Timeout/cancel only stop *waiting* (VictorSuite has no kill channel for a running background command). Never retried. |
| C | DroidCommand fleet envelope → VictorSuite dispatcher → correlated response | **Repaired in this change (DC PR).** `FleetModule` → `TermuxFleetTransport` (per-command `TermuxIpcTransport` reading the user's `externalControlEnabled`) → new `AndroidTermuxIpcSender`: one JSON blob in `ai.droidcommand.REMOTE_ENVELOPE_JSON` on `com.termux.RUN_COMMAND`, reply on `ai.droidcommand.REMOTE_RESPONSE_JSON` via a per-request PendingIntent. VictorSuite: `RunCommandService` → `TermuxRemoteEnvelopeBinding` → `RemoteEnvelopeDispatcher`. | Caller = creator package of the result PendingIntent (system-attested), must hold RUN_COMMAND, must have exactly one pairing row **with** a cert SHA-256 that matches; `allow-external-apps=true`; VictorSuite "Fleet remote control" switch (OFF by default); freshness (TTL, 120 s future skew) and nonce replay guard. | Before: DC default sender was `UnwiredTermuxIpcSender` (always rejected; "PENDING"). After: wired and JVM-tested with fakes; **not device-verified**. Destructive ops (`shell.exec` …) and `config.import` apply stay `confirmation_required`; unauthenticated requests are dropped silently by VictorSuite → DC reports a timeout, not success. |
| D | File Manager panel (+ its separate Messenger bridge) | Panel: VictorSuite `VictorFilesPanel.launch` → explicit intent `ai.victorious.filemanager[.debug]` action `ai.victorious.filemanager.action.PANEL`, optional extra `ai.victorious.filemanager.extra.OPEN_PATH`, **no** `NEW_TASK`. File-Manager `panel.PanelActivity`: exported, `singleTop`, excluded from recents. Bridge: File-Manager `FileManagerBridgeService`, action `ai.victorious.filemanager.action.BRIDGE`, allowlist `ai.droidcommand.app[.debug]`, external control OFF by default — **only DroidCommand talks to it, VictorSuite does not.** | Panel: none (grants no file access). Bridge: package allowlist + optional cert pins. | Panel contract rechecked field by field: matches. Bridge `filemanager.list` is still a stub returning empty `entries`; `live:false`, `mode:"stub"`. |
| E | Shared My PC profiles / `OPEN_PC` | **Not implemented in VictorSuite.** `PcProfileProvider`, `PcEnrollService`, `OPEN_PC` exist only in `docs/REMOTE_PC_PLAN.md`. | — | **UNAVAILABLE.** Prerequisite: the PR-series in `REMOTE_PC_PLAN.md` (provider `com.termux.victor.pc`, enroll service, `OPEN_PC` filter). Nothing in the UI may show "My PC connected". |

## File access: what actually authorizes it

* The panel is a *UI hand-off*. `OPEN_PATH` only names a folder; it does not grant anything.
* VictorSuite's `$HOME` (`/data/data/com.termux/files/home`) and `$PREFIX` are private to the `com.termux` uid
  (targetSdk 28, no `FileProvider` or `DocumentsProvider` is exported for them). Victorious File Manager can read
  them only if **the user enabled root or Shizuku inside File Manager** and approved that grant there. Shizuku merely
  *being installed* grants nothing.
* The supported transfer routes that exist today are: File Manager's own storage permissions for shared storage
  (`/sdcard`), `RUN_COMMAND` (path B) to copy files into shared storage, and the Termux share/file-receiver activity.
  There is no VictorSuite-side API that hands the File Manager a private path or a content URI.

## Setup steps (user)

1. Install VictorSuite (`com.termux`) and, optionally, Victorious File Manager (`ai.victorious.filemanager`) and
   DroidCommand AI (`ai.droidcommand.app`). VictorSuite works with none of them.
2. **Panel:** nothing to configure. VictorSuite Settings → "Victorious File Manager panel" switch (default ON) chooses
   sheet vs. built-in browser. Not installed, activity missing, or launch refused → built-in browser, terminal untouched.
3. **Path B (terminal tool):** in VictorSuite run `echo 'allow-external-apps=true' >> ~/.termux/termux.properties`,
   then `termux-reload-settings`. In Android Settings → Apps → DroidCommand AI → Permissions, grant
   "Run commands in VictorSuite" (`com.termux.permission.RUN_COMMAND`).
4. **Path C (fleet envelope), all of:**
   * step 3;
   * VictorSuite Settings → "Fleet remote control" → ON (default OFF), and the same switch in DroidCommand's
     Remote-control settings (default OFF);
   * VictorSuite Settings → "Paired fleet apps": add `ai.droidcommand.app=<sourceNodeRef>,<certSha256>` using
     DroidCommand's real signing certificate SHA-256 (a row without a cert never authenticates). Get it with
     `apksigner verify --print-certs ai.droidcommand.app.apk`.
5. File Manager bridge (path D, DroidCommand-only): enable external control in File Manager and pair in DroidCommand's
   Fleet screen. Not needed for the panel.

## Failure matrix (expected behaviour, JVM-tested where noted)

| Condition | Behaviour |
|---|---|
| File Manager absent | Panel inactive; drawer shows built-in browser; toolbar button shows "not installed" toast + built-in browser (`VictorFilesPanelTest`). |
| Activity missing / `ActivityNotFoundException` | `startGuarded` → `ACTIVITY_NOT_FOUND`; falls back (tested). |
| `SecurityException` (was uncaught in `openFileManager` and in `launch`) | `PERMISSION_DENIED`; falls back (tested). Previously uncaught in `openFileManager` and `launch` (a refused cross-app launch would have propagated out of the terminal activity). |
| Release installed but refusing, debug working | `launchApp` tries the sibling package (tested). |
| DC missing RUN_COMMAND permission | Path B: `err=1` "permission denied"; path C: `unauthorized` with the exact permission named (not tested on device). |
| VictorSuite not paired / wrong cert / control OFF | VictorSuite replies `not_paired` / `unauthorized` / `external_control_disabled` where it can reply (`DispatcherGatingTest`); with no result PendingIntent it drops silently. DC reports a timeout and says why. |
| Expired, replayed, destructive, import-apply | Blocked by DC's `TermuxIpcTransport.gate` before sending, and again by VictorSuite's dispatcher (`ReplayGuardTest`, `DispatcherGatingTest`, `TermuxFleetTransportSenderTest`). |
| Reply never arrives | `TermuxIpcResponseAwaiter` times out (15 s), receiver is always unregistered, late replies cannot be mistaken for another request. Request is **not** retried and may still have executed. |
