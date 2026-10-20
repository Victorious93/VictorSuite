# VictorSuite manual

This manual covers features that other fleet apps can drive. It starts with the
fleet remote-control envelope. Protocol details live in
[`REMOTE_CONTROL_ENVELOPE.md`](REMOTE_CONTROL_ENVELOPE.md).

## Fleet remote control

VictorSuite can take requests from paired fleet apps (for example DroidCommand AI)
through the existing `com.termux.RUN_COMMAND` intent. It is **off by default**.

### Turning it on

You need all of these:

1. VictorSuite settings -> **Fleet remote control** -> on.
2. VictorSuite settings -> **Paired fleet apps** -> add one line per app:
   `packageName=sourceNodeRef` or `packageName=sourceNodeRef,CERT_SHA256`.
   Example: `ai.droidcommand.app=controller`. With a cert, only an app signed with
   that cert is accepted.
3. `allow-external-apps=true` in `~/.termux/termux.properties` (standard Termux setting).
4. The calling app holds the `com.termux.permission.RUN_COMMAND` permission
   (Android settings -> Apps -> the app -> Permissions -> "Run commands in Termux environment").

Turning the switch off blocks every remote request at once, including status reads.
Importing a backup always turns it back off.

### What a remote app can and cannot do

- It can read this node's capabilities and status, and run safe actions.
- It can **never** run shell commands or other destructive actions. Those always
  come back as `confirmation_required`, whatever the request says.
- It can **never** export or import your settings (`capability_off`).
- Every request expires (2 minutes by default, 5 at most) and can be used only once.

### Ops

#### `capabilities.get`
Lists the protocol version (1.1) and every op with `kind` (read / action /
destructive), `remoteAllowed` and `requiresConfirm`, plus the `remote.command`
actions this node knows. Read-only.

#### `remote.command`
Runs one named action (`actionRef`) on this node.

| actionRef | What it does | Remote use |
|---|---|---|
| `node.ping` | Answers `{ "pong": true }` | Allowed |
| `shell.exec` | Would run a shell command | Always refused with `confirmation_required` (no on-device confirm yet) |

Any action whose name looks like shell, exec, su, root, wipe, flash, bootloader,
reboot, shutdown, lock, kill, delete or rm is treated as destructive, even if the
request says `destructive: false`.

#### `macro.step`
Runs one step of a controller macro on this node: `op` (an op or action above),
`params`, optional `macroId`, `stepIndex`, `onError` (`abort` or `continue`).

#### `macro.run`
Runs a list of steps in order. `onError: abort` stops at the first failure and
returns what ran so far (no undo). `continue` keeps going. `dryRun: true` checks
every step without running any. If any step needs confirmation, nothing runs and
the answer is `confirmation_required`. Macros cannot contain macros.

#### `node.status.get`
Returns this node's status-tray row:

- `node_health` — `ok`, `degraded` (under 200 MB free) or `down` (Termux bootstrap missing).
- `other` — proot / Kali containers: which are installed and which are running.

It never includes shell history, environment variables, passwords or keys.
Optional `nodeId` must be this node's id.

#### `config.export` (on-device only)
Builds a backup of `termux.properties`, `colors.properties` and the remote-control
pairings. It never includes shell history, dotfiles such as `.bashrc`, env files,
SSH keys or anything secret-named; if one of the allowed files contains a
secret-named setting, export stops with `invalid_parameters` instead. Backups are
AES-256-GCM encrypted unless a local-dev clear copy is asked for.
Not callable remotely. No Settings button yet.

#### `config.import` (on-device only)
Restores a backup. Always try `dryRun` first; it shows what would be added, skipped
or overwritten and writes nothing. A real import needs on-device confirmation.
Conflict policy: `skip` (default, keep what you have), `overwrite`, or
`merge-by-id`. An import **never** turns on Fleet remote control and never changes
`allow-external-apps`. Old properties files are kept as `.bak-<time>` copies.
Not callable remotely. No Settings button yet.

### Error codes

| Code | Meaning |
|---|---|
| `external_control_disabled` | Fleet remote control is off |
| `not_paired` | The calling app is not in Paired fleet apps |
| `unauthorized` | Caller could not be verified, lacks the permission, cert mismatch, or `allow-external-apps` is off |
| `expired` | Request too old, or its clock is more than 2 minutes ahead |
| `replay_detected` | The same request was sent twice |
| `invalid_parameters` | Malformed request, or secret content found |
| `not_found` | Wrong target node, unknown action, or no matching backup entry |
| `unsupported_op` | This node does not know that op |
| `capability_off` | That op is not allowed remotely |
| `confirmation_required` | Needs on-device confirmation; never granted remotely |
| `internal` | Something else went wrong (also: backup could not be decrypted) |
