# Fleet remote-control envelope v1 (BRIDGE_PROTOCOL 1.1) — VictorSuite node

Status: **implemented on JVM, unit-tested; not yet tried on a device.**

> **Binding change (application ID PR, feature/own-application-id):** VictorSuite now
> uses its own application ID `com.victorious.suite` so it installs alongside real
> Termux. Controllers (Fleet Orchestrator / DroidCommand AI `core-termux`) must update:
>
> | | Before | Now |
> |---|---|---|
> | Package / `targetNodeRef` / backup `packageName` | `com.termux` | `com.victorious.suite` |
> | Intent action | `com.termux.RUN_COMMAND` | `com.victorious.suite.RUN_COMMAND` |
> | Component | `com.termux/.app.RunCommandService` | `com.victorious.suite/com.termux.app.RunCommandService` (Java class name unchanged) |
> | Permission (caller `<uses-permission>`) | `com.termux.permission.RUN_COMMAND` | `com.victorious.suite.permission.RUN_COMMAND` |
> | Result extra | `com.termux.RUN_COMMAND_PENDING_INTENT` | `com.victorious.suite.RUN_COMMAND_PENDING_INTENT` |
> | Other RUN_COMMAND extras | `com.termux.RUN_COMMAND_*` | `com.victorious.suite.RUN_COMMAND_*` |
>
> `ai.droidcommand.REMOTE_ENVELOPE_JSON` / `REMOTE_RESPONSE_JSON` are unchanged.
> Calls to `com.termux` now reach real Termux (if installed), not VictorSuite.

VictorSuite (`com.victorious.suite`) is a **node** in the Victorious fleet. It answers the
frozen fleet envelope over the **TERMUX_IPC** binding: one JSON blob on the
existing `com.victorious.suite.RUN_COMMAND` intent. DroidCommand AI is the controller and
sole authorizer. Every inbound envelope is an **untrusted request**.

Frozen specs this follows (in `Victorious93/droidcommand-AI`):

- `docs/specs/REMOTE_CONTROL.md` — envelope, replay, errors, trust model, Appendix C (TERMUX_IPC)
- `docs/specs/STATUS_TRAY.md` — `node.status.get`
- `docs/specs/UNIFIED_BACKUP.md` — `config.export` / `config.import` (PR #236)

Code: `app/src/main/java/com/termux/zerocore/remotecontrol/` (pure JVM; no Android
imports) and `.../remotecontrol/android/` (thin binding). Tests:
`app/src/test/java/com/termux/zerocore/remotecontrol/`.

---

## 1. Ops

| Op | `kind` | `remoteAllowed` | `requiresConfirm` | What it does here |
|---|---|---|---|---|
| `capabilities.get` | read | true | false | Protocol 1.1 + per-op descriptors + `remote.command` actions |
| `remote.command` | action | true | false* | Runs one `actionRef` on this node (*the action's own descriptor decides confirm) |
| `macro.step` | action | true | false* | Runs one advertised op / actionRef on this node |
| `macro.run` | action | true | false* | Runs `steps[]` sequentially; `dryRun` validates only |
| `node.status.get` | read | true | false | One `NodeStatusSnapshot` (STATUS_TRAY.md) |
| `config.export` | action | **false** | false | One-entry backup bundle (UNIFIED_BACKUP.md) |
| `config.import` | action | **false** | **true** | Dry-run / confirmed import; never enables remote control |

`remote.command` actionRefs shipped in v1:

| actionRef | kind | remoteAllowed | requiresConfirm | Notes |
|---|---|---|---|---|
| `node.ping` | read | true | false | Returns `{ "pong": true }` |
| `shell.exec` | destructive | true | true | Always `confirmation_required`. There is no on-device confirm flow yet, so it never runs. |

`node.status.get`, `config.export` and `config.import` are standalone
operations, not `actionRef`s. `remote.command` with `actionRef` set to a
standalone op name is `invalid_parameters`.

## 2. Envelope fields

The blob is one camelCase JSON object (no BridgeRequest wrapper, no nested `auth`).

| Field | Type | Required | Notes |
|---|---|---|---|
| `operation` | string | recommended | One of the ops above. Unknown value -> `unsupported_op`. VictorSuite addition, see §8. |
| `requestId` | string | yes | Echoed as `request_id` in the response. Max 128 chars. |
| `targetNodeRef` | string | yes | This node's id (`victorsuite-<32 hex>`) or `com.victorious.suite`; anything else -> `not_found`. |
| `actionRef` | string | for `remote.command` | The action to run. For other ops it defaults to the op name. |
| `params` | object | no | Default `{}`. Values must be string / number / boolean (stored as strings). Max 16 entries. `args` (the DroidCommand AI wire name) is accepted as an alias; sending both with different contents is `invalid_parameters`. Unknown keys are ignored. |
| `nonce` | string | yes | 128 bits: 32 hex chars, or base64/base64url that decodes to exactly 16 bytes. |
| `timestampMs` | number | yes | Epoch ms when the request was made. |
| `ttlMs` | number | no | Default `120000`. Max `300000`; larger -> `invalid_parameters`. `<= 0` -> `invalid_parameters`. |
| `destructive` | bool | no | **Advisory only.** Never used for gating. |
| `trigger` | object | no | Telemetry only (e.g. VictorSMS `sourceNumber` / `matchedKeyword` / `rawExtras`). Never used for auth or approval. |

`macro.step` adds:

| Field | Notes |
|---|---|
| `op` | The step op. Must be an advertised non-macro op or a registered `actionRef`. Falls back to `actionRef`. |
| `macroId` | Optional controller macro id (echoed). |
| `stepIndex` | Optional, `>= 0` (echoed). |
| `onError` | `abort` (default) or `continue`. Anything else -> `invalid_parameters`. |

`macro.run` adds:

| Field | Notes |
|---|---|
| `macroId` | Optional (echoed). |
| `steps` | 1..32 of `{ op, params, onError: abort\|continue, timeoutMs? }`. |
| `dryRun` | `true` = validate ops and gates, run nothing. |

Macros cannot nest (`macro.run` / `macro.step` as a step -> `invalid_parameters`).
`abort` stops at the first failed step and reports the partial result. There is no
rollback. If any step needs confirmation, the whole non-dry-run macro is
`confirmation_required` and no step runs.

## 3. Replay and freshness

Checked after caller-id and the external-control switch, before any op runs:

| Condition | Error |
|---|---|
| `now > timestampMs + ttlMs` | `expired` |
| `timestampMs > now + 120000` (future skew) | `expired` |
| `(sourceNodeRef, nonce)` already seen | `replay_detected` |

- `sourceNodeRef` is the **authenticated** caller's pairing id, not a payload field.
  The same nonce from two different sources is two different keys.
- Seen nonces are kept for at least 10 minutes and at least until
  `timestampMs + ttlMs`. Nonces compare case-insensitively.
- The cache is in memory (lost on process death) and caps at 10,000 entries.
  When full it refuses new requests with `internal` instead of forgetting live entries.
- The clock is injectable (`RemoteClock`) so tests control time.

Past validity is TTL-only, as the frozen spec says: `timestampMs = now - 200000`
with `ttlMs = 300000` is accepted. See §8 for why this is not a two-sided ±120 s check.

## 4. Trust pipeline and gating

In this order (REMOTE_CONTROL.md "TRUST MODEL"):

1. **Caller-id** (binding + pairing table). Failure -> `unauthorized` / `not_paired`.
2. **External-control switch**. OFF by default -> `external_control_disabled`. Applies to every op, `capabilities.get` included.
3. **Envelope parse + replay** -> `invalid_parameters` / `unsupported_op` / `expired` / `replay_detected`.
4. **Node policy**:
   - `targetNodeRef` must be this node -> else `not_found`.
   - Op must be advertised -> else `unsupported_op`.
   - `remoteAllowed:false` op (or action) from a remote caller -> `capability_off`. This also applies when the op is a macro step.
   - The node classifies every `actionRef` itself. Names with tokens such as `shell`, `exec`, `su`, `root`, `wipe`, `flash`, `bootloader`, `reboot`, `shutdown`, `lock`, `kill`, `delete`, `rm`, or containing `power_off` / `factory_reset` / `run_command`, are **destructive**. Classification can raise a registered kind but never lower it.
   - Destructive or `requiresConfirm` -> `confirmation_required`. A remote request can never carry the confirmation. Fields like `params.confirmed`, `approved`, `yolo`, `trigger.matchedKeyword` or `destructive:false` change nothing.
   - Unknown `actionRef` -> `not_found`.

Settings: **VictorSuite settings -> "Fleet remote control"** (switch, off by
default) and **"Paired fleet apps"** (one `packageName=sourceNodeRef[,certSha256]` per line).
Both are stored in the private `victorsuite_remote_control` SharedPreferences.

## 5. Binding (TERMUX_IPC)

- Intent: `com.victorious.suite.RUN_COMMAND` to `com.victorious.suite/com.termux.app.RunCommandService` (existing, exported, guarded by `com.victorious.suite.permission.RUN_COMMAND`).
- Request extra: `ai.droidcommand.REMOTE_ENVELOPE_JSON` (String, the blob above).
- Result: the caller **must** attach `com.victorious.suite.RUN_COMMAND_PENDING_INTENT`
  (a PendingIntent it created). The response JSON goes back on it in the extra
  `ai.droidcommand.REMOTE_RESPONSE_JSON`.
- When the envelope extra is present, `RunCommandService` hands the intent to
  `TermuxRemoteEnvelopeBinding` and returns. **The normal command-execution path never runs** for an envelope intent.

Caller identity comes only from the transport:

1. The manifest already requires `com.victorious.suite.permission.RUN_COMMAND`.
2. `allow-external-apps=true` must be set in `~/.termux/termux.properties` (the existing Termux policy).
3. The calling package is the system-reported **creator of the result PendingIntent**
   (`PendingIntent.getCreatorPackage()` / `getCreatorUid()`). It must own that uid and hold RUN_COMMAND.
4. That package must have exactly one pairing row. `sourceNodeRef` is taken from the row.
   If the row has a `certSha256`, the caller's signing cert SHA-256 must match (colons ignored).
   An empty cert means unpinned (fail-open), matching the fleet's "certs TBD" rows.

A request without a result PendingIntent cannot be authenticated. It is logged and dropped.

Response (frozen BridgeResponse shape):

```json
{ "protocol_major": 1, "request_id": "r-1", "node_id": "victorsuite-…", "ok": true, "result": { } }
{ "protocol_major": 1, "request_id": "r-1", "node_id": "victorsuite-…", "ok": false,
  "error": { "category": "confirmation_required", "message": "…" } }
```

## 6. Errors

| Category | When |
|---|---|
| `invalid_parameters` | Bad JSON, missing/oversized fields, bad nonce, `ttlMs` out of range, nested `auth`, bad `onError`, secret/history content in status or backup |
| `not_paired` | Calling package has no pairing row |
| `unauthorized` | Caller could not be authenticated, lacks RUN_COMMAND, `allow-external-apps` off, cert mismatch |
| `external_control_disabled` | Switch is OFF (default) |
| `expired` | TTL passed, or timestamp more than 120 s in the future |
| `replay_detected` | `(sourceNodeRef, nonce)` seen before |
| `not_found` | `targetNodeRef` is not this node, unknown `actionRef`, `args.nodeId` mismatch, no backup entry for `com.victorious.suite` |
| `unsupported_op` | Unknown op, or a macro step op this node does not advertise |
| `capability_off` | Op/action is `remoteAllowed:false` for a remote caller |
| `confirmation_required` | Destructive or confirm-gated op without on-device confirmation |
| `internal` | Anything else; replay cache full; backup decrypt failure (tamper / wrong key) |

## 7. `node.status.get` and `config.*`

### `node.status.get` (STATUS_TRAY.md)

Result is one snapshot:

```json
{
  "nodeId": "victorsuite-0123456789abcdef0123456789abcdef",
  "packageName": "com.victorious.suite",
  "connectionClass": "TERMUX_IPC",
  "updatedAtMs": 1760920000000,
  "domains": [
    { "name": "node_health", "state": "ok", "detail": "bootstrap ok, storage ok" },
    { "name": "other", "state": "ok", "detail": "proot: debian stopped, kali running" }
  ]
}
```

- `node_health`: `down` if the bootstrap (`$PREFIX/bin/sh`) is missing, `degraded` if
  less than 200 MB is free, else `ok`.
- `other`: proot-distro containers under `$PREFIX/var/lib/proot-distro/installed-rootfs`.
  A container counts as running when one of our processes runs `proot` on its rootfs.
  Process command lines are only matched, never reported. Names are reduced to
  `[A-Za-z0-9._-]`, max 32 chars; `detail` is capped at 200 chars.
- `args.nodeId` must equal this node if given. Unknown args are ignored.
- Never contains shell history, environment variables or secrets. The codec refuses
  (`invalid_parameters`) any `detail` matching the forbidden list (password, token,
  secret, api key, bearer, cookie, keystore, private key, history, …).
- Never confirmation-gated; the advisory `destructive` flag is ignored.

### `config.export` / `config.import` (UNIFIED_BACKUP.md)

Both are `remoteAllowed:false`. A remote caller always gets `capability_off`,
also through a macro. They run only through the local dispatcher entry point
(`RemoteEnvelopeDispatcher.handleLocal`). This PR adds no Settings button for them yet.

Bundle: `{ version: 1, createdAtMs, sourceNodeRef, wrapping, entries: [ { packageName: "com.victorious.suite", schemaVersion: 1, ciphertext | clearPayload } ] }`.

VictorSuite entry payload (`schemaVersion` 1):

```json
{
  "termuxProperties": { "bell-character": "vibrate" },
  "colorsProperties": { "background": "#000000" },
  "remoteControl": { "externalControlEnabled": false,
                     "pairings": [ { "sourceNodeRef": "controller", "packageName": "ai.droidcommand.app" } ] }
}
```

Export:

- Reads **only** `~/.termux/termux.properties` and `~/.termux/colors.properties`.
  Shell history, dotfiles (`.bashrc`, `.profile`, …), env files, `~/.ssh/*` and other
  key material are never read. A deny list re-checks the allow-list.
- If a value would carry a secret-named key (`api_key`, `token`, `password`, …),
  history, SSH material, PEM blocks or VictorSpoof Twilio/number-pool names, export
  **refuses** with `invalid_parameters` instead of quietly dropping it.
- `params.clearPayload = "true"` -> `wrapping: "none"` (local-dev only). Otherwise
  AES-256-GCM, 12-byte IV, 128-bit tag, record `v1.<b64url iv>.<b64url ct>`, AAD
  `packageName|schemaVersion|sourceNodeRef`. The key is supplied by the local caller
  and never written to the bundle. Android Keystore wrapping is **not implemented** yet.
- `params.packageName` other than `com.victorious.suite` -> `not_found`.

Import, in order:

1. Parse; `version` must be 1; `wrapping` must be known; duplicate `packageName` fails; exactly one of `ciphertext` / `clearPayload` per entry and it must match `wrapping`.
2. Find the `com.victorious.suite` entry (`not_found` if missing).
3. Decrypt (failure -> `internal`, treated as tampering).
4. Secret / history / SSH scan -> `invalid_parameters`.
5. `dryRun` (`params.dryRun = "true"` or top-level `dryRun: true`): report only, write nothing.
6. Otherwise on-device confirmation is required (`confirmation_required` without it).
7. Apply with `conflictPolicy` = `skip` (default) | `overwrite` | `merge-by-id`. Properties are keyed by property name; pairings by `sourceNodeRef`.
8. **External control is forced OFF.** `allow-external-apps` from a bundle is always ignored (the live value stays). Both are listed under `ignored` in the report.
9. Existing properties files are copied to `<file>.bak-<epochMs>` before being rewritten.

## 8. Spec notes and differences

- **Skew rule.** The task brief asked for a two-sided `|now - timestampMs| > 120000`
  check. The frozen REMOTE_CONTROL.md §2 says the 120 s window is **future-skew only**
  and explicitly says not to apply the two-sided check. The brief allowed "or as spec
  says", so this follows the frozen spec.
- **`params` vs `args`.** The brief names the field `params`; the frozen spec and
  DroidCommand AI's codec use `args`. Both are accepted; `params` wins in encoding.
- **`operation` field in the blob.** Appendix C has no outer BridgeRequest, so the
  frozen blob does not say which op it is. This node reads an optional top-level
  `operation`. Without it, it infers: `steps` present -> `macro.run`; `actionRef` is a
  standalone op name -> that op; `op` present -> `macro.step`; else `remote.command`.
- **Response extra key** `ai.droidcommand.REMOTE_RESPONSE_JSON` is a VictorSuite proposal;
  the spec only names the request key (itself still "controller proposal", §7 item 4).
- **Caller identity.** A started Service cannot see its caller's uid, so identity
  comes from the result PendingIntent's creator. Requests without one are dropped.
- **`allow-external-apps`** must be true in addition to the in-app switch, because
  Appendix C names it as part of the identity check.
- **Backup secret scan** is stricter than the reference: it scans every JSON key for
  the forbidden fragments (not only exact quoted names) and adds history / SSH
  fragments for this node.
- **`config.export` result** is a full one-entry bundle signed with this node's id as
  `sourceNodeRef`, so it can be imported back here or stitched by the controller.

## 9. Examples

`capabilities.get`:

```json
{ "operation": "capabilities.get", "requestId": "r-1",
  "targetNodeRef": "victorsuite-0123456789abcdef0123456789abcdef",
  "nonce": "8c1f0a4e2b7d4f6a9e3c5b1d7a2f4e6c", "timestampMs": 1760920000000 }
```

`node.status.get`:

```json
{ "operation": "node.status.get", "requestId": "r-2", "targetNodeRef": "com.victorious.suite",
  "params": {}, "nonce": "0f9e8d7c6b5a49382716150413121110", "timestampMs": 1760920000000, "ttlMs": 60000 }
```

`remote.command` that is refused (destructive, whatever the flag says):

```json
{ "operation": "remote.command", "requestId": "r-3", "targetNodeRef": "com.victorious.suite",
  "actionRef": "shell.exec", "params": { "cmd": "id" }, "destructive": false,
  "nonce": "11112222333344445555666677778888", "timestampMs": 1760920000000,
  "trigger": { "sourceNumber": "+15555550100", "matchedKeyword": true, "rawExtras": {} } }
```
-> `{ "ok": false, "error": { "category": "confirmation_required", … } }`

`macro.run` dry run:

```json
{ "operation": "macro.run", "requestId": "r-4", "targetNodeRef": "com.victorious.suite",
  "macroId": "morning", "dryRun": true,
  "steps": [ { "op": "node.ping" }, { "op": "node.status.get", "onError": "continue" } ],
  "nonce": "aaaabbbbccccddddeeeeffff00001111", "timestampMs": 1760920000000 }
```

Sending from a controller app (Kotlin sketch):

```kotlin
val reply = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, ReplyReceiver::class.java), PendingIntent.FLAG_MUTABLE)
ctx.startForegroundService(Intent("com.victorious.suite.RUN_COMMAND").setClassName("com.victorious.suite", "com.termux.app.RunCommandService")
    .putExtra("ai.droidcommand.REMOTE_ENVELOPE_JSON", blobJson)
    .putExtra("com.victorious.suite.RUN_COMMAND_PENDING_INTENT", reply))
// ReplyReceiver reads intent.getStringExtra("ai.droidcommand.REMOTE_RESPONSE_JSON")
```

## 10. Not in this PR

- On-device confirmation flow (so destructive actions and remote-triggered imports never run).
- A shell executor behind `shell.exec`.
- Settings buttons for local `config.export` / `config.import`, and Android Keystore key wrapping.
- Persistent replay cache.
- Device testing.
