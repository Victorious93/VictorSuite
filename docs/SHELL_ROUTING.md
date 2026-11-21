# Shell routing: every product shell action goes through VictorSuite

Status of this document: **inventory + design + what is implemented** (2026-11-21). Read the last section first for what
has and has not been built or run.

Scope: *product* features (terminal buttons, one-shot command actions, fleet dispatchers). The underlying Linux console,
boot process and build tools keep their native paths and are out of scope.

## 1. Inventory (inspected source, not guesses)

"Final executor" is what actually spawns the process. **Compliant** = final executor is VictorSuite's own terminal runtime
(`TermuxService` / `TerminalSession`, package `com.termux`).

### VictorSuite (Android, `com.termux`)

| Entry | Caller | Target | Privilege | Approval today | Final executor | Verdict |
|---|---|---|---|---|---|---|
| Local terminal UI (`TermuxActivity`) | owner at the keyboard | this phone | user | none needed (owner is the caller) | `TerminalSession` PTY | compliant |
| `com.termux.RUN_COMMAND` -> `RunCommandService` | any app holding `com.termux.permission.RUN_COMMAND` and `allow-external-apps=true` | this phone | user | OS permission + allow-list | `TermuxService` | compliant; **public contract preserved unchanged** |
| Fleet envelope `remote.command` / `shell.exec` (`RemoteEnvelopeDispatcher`) | paired app, cert-pinned (`PairingTable`) | this node | user (root/Shizuku only with a separate owner grant) | was: always `confirmation_required`, no flow. **Now: on-device approval queue** (section 3) | `ShellRunner` (Android implementation: see section 5) | compliant after this change |
| `zt` loopback socket (`VsSocketService`, port 19951, per-install token) | on-device `zt` helper | this phone | user | token | `ExeCommand`/`TermuxService` | VictorSuite-internal |
| Plugin launcher, `ADB.kt`, `RunShell.kt`, `libsu` root shell, `MyPcActivity` | VictorSuite UI | this phone / PC (guidance only) | user/root | owner action in UI | VictorSuite process | VictorSuite-internal (these *are* VictorSuite) |

### DroidCommand-AI (`ai.droidcommand.app` and JVM modules)

| Entry | Caller | Target | Privilege | Approval | Final executor | Verdict |
|---|---|---|---|---|---|---|
| `core-termux` `TermuxExecutionTarget` -> `AndroidPendingIntentRunCommandClient` | DCA tool executor | phone | user | `SecureToolExecutor` + `ApprovalFlow` | VictorSuite via `RUN_COMMAND` | **the compliant route** |
| `core-shell` `ProcessBuilderShellExecutor`, `LocalProcessExecutionTarget`, `ShellTool` | DCA tool executor | DCA's own app sandbox | user | `ShellSecurityPolicy` allow-list + approval | DCA process | **bypass**: not VictorSuite. Must be unreachable from product features on a phone; stays valid for the JVM CLI host |
| `app/.../automation/AutomationTools.kt` `AllowlistShellExecutor` | automation keys | DCA sandbox | user | allow-list read on every run | `ProcessBuilderShellExecutor` | **bypass** (allow-listed executables only) |
| `core-shizuku` `PrivilegedProcessRunner`, `BoundPrivilegedShell`, `EmbeddedShizukuStarter`, `ToolboxRuntime` | DCA tools | phone | shizuku | `ApprovalPrompt` before first privileged op | Shizuku user service | **bypass** of VictorSuite for privilege |
| `core-root` `AdbRootExecutor`, `MagiskProvider`, `ProcessRunner`, `PolicyEnforcingRootExecutor` | DCA tools | rooted phone (on device) or PC-over-adb | root | `PolicyEnforcingRootExecutor` + `GrantStore` | `su` / adb | **bypass** of VictorSuite for root |
| `core-tools-android` `AdbDeviceController`, `core-apk-lifecycle` `AdbApkLifecycleExecutor`, `core-flash` `Transport` | DCA device tools | adb-attached device | user/shell | tool policy | host `adb` process | host-side tools (PC); not a phone product shell |
| `core-tools-metasploit`, `core-tools-setoolkit` (`ShellBacked*`/`Termux*Executor`) | DCA security tools | via `ShellExecutor` or Termux | user | tool policy | `core-shell` or VictorSuite | Termux variant compliant; ShellBacked variant is a bypass |
| `core-fleet-worker` (phone worker for VictorLinux) | coordinator assignment | this phone | n/a | DCA device policy + on-device approval | no shell op exists today | nothing to close; terminal op is new (section 4) |

### rustdeskV

| Entry | Notes | Verdict |
|---|---|---|
| `src/server/terminal_service.rs` | `#[cfg(not(any(target_os = "android", target_os = "ios")))]` in `src/server.rs`: **compiled out on Android/iOS**. On desktop it spawns the user's shell | desktop host terminal; correct for a PC host |
| `flutter/lib/mobile/pages/terminal_page.dart` (816 lines) and desktop counterpart | **client** of the terminal service | needs the Android-host path (section 4) |

### VictorLinux

| Entry | Verdict |
|---|---|
| `victor-priv` sudo helper, Control Center `subprocess` calls, fleet CLI | fixed argv, no user-supplied shell text; PC administration, not a phone shell |
| Openbox/Xfce menu entries (`konsole`, `btop`...) | the native Linux console: out of scope |
| Fleet ops (`hash_files`, `apk.*`, `message.draft`, ...) | none executes shell text; `terminal.open` is advertised capability only |

### VictorSMS

No shell action found (`grep -i shell|ProcessBuilder|Runtime.exec` over `app/src/main`: no matches). The bridge ops (`sms.*`,
`relay.*`) cannot start a process. Incoming SMS, mail, Matrix, bots and notifications are data and never reach the
approval queue (the queue only accepts a caller authenticated by `PairingTable`).

## 2. Closure plan for the bypasses (not yet done: see section 6)

1. Add a `ShellRoute` seam in `core-security`: product shell features must obtain a `TermuxExecutionTarget`; the
   `LocalProcessExecutionTarget` / `AllowlistShellExecutor` / `ShellBackedMetasploitExecutor` registrations in `:app`'s DI
   (`AppModule.kt`) are removed for phone builds (they remain for the JVM `cli`).
2. Root and Shizuku keep their own policy but are surfaced through VictorSuite's privileged grant (section 3), so the owner
   sees one place that says "root shell: on/off".
3. A CI test in DCA fails if `:app` wires `ProcessBuilderShellExecutor` or `AdbRootExecutor` into a tool registry.

## 3. Implemented: on-device approval for one-shot `shell.exec` (VictorSuite, JVM-tested)

`remotecontrol/ShellApprovals.kt` + `RemoteEnvelopeDispatcher` (additive; with no queue configured the old behaviour,
`confirmation_required` and never executes, is unchanged).

* Remote proposal: `remote.command` `actionRef=shell.exec`, `params.argv` (JSON array), `params.target` (one named node,
  required), optional `params.privilege` (`user` default). Never runs; replies `confirmation_required` with
  `error.details = {approvalId, expiresMs, privilege}` (additive field).
* The approval is bound to **authenticated peer + target + argv + privilege** by a SHA-256 that the approval screen must echo;
  a mismatch runs nothing. Single use. 120 s expiry. At most 8 pending.
* Privileged runs need a separate owner switch per privilege and are re-checked at approval time.
* `actionRef=command.result` returns `state`, `exitCode`, `stdout`, `stderr`, `timedOut` **only to the proposing peer**.
* `revokePeer()` kills the peer's pending approvals and cancels running ones (output withheld).
* Macros cannot carry `shell.exec` (still blocked).
* Shell input is never placed in a job queue or retried: approvals live in memory only and die with the process.

## 4. Designed, not built: interactive terminal

Requirements are in the task brief; the design that fits the code is:

* **Android -> PC** (VictorSuite Remote shell or rustdeskV Terminal): both open VictorSuite's terminal view and attach it to the
  existing rustdeskV terminal connection (`AuthConnType::Terminal`) to the exact peer id; the shell runs under the authorized
  user on the PC (`terminal_service.rs`, unchanged). Needs a VictorSuite `TerminalSession` backed by a rustdesk FFI stream.
* **PC -> Android phone**: coordinator op `terminal.open` for setup/status only; live bytes travel on an authenticated stream
  (rustdesk terminal channel with the Android host path, which does not exist yet). The phone's VictorSuite owns the PTY.
* Host enforcement: the phone refuses a terminal channel unless a live approval binds (peer, target, session, privilege);
  an older client that does not send the new capability bit is refused, never downgraded to `RUN_COMMAND`.

## 5. What the Android layer still has to supply

`ShellRunner` (runs argv through `TermuxService`), the approval `Activity` (show peer, target, argv, privilege; Approve/Deny),
the privilege switches in settings, and wiring `ShellApprovalQueue` into `TermuxRemoteEnvelopeBinding.dispatcher()`.

## 6. Status (honest)

| Item | State |
|---|---|
| Inventory above | done from source; not exhaustive for third-party plugins |
| Approval queue + dispatcher gating + tests | implemented; see test run in the PR description |
| Android `ShellRunner`, approval Activity, settings | **not implemented** |
| Interactive PTY bridge, rustdeskV Android host terminal, VictorLinux -> phone terminal | **not implemented** |
| DCA bypass closure | **not implemented** (plan only) |
| Anything on a physical device | **not run** |
