# Hak5 Companion — Phase 2 implementation notes

Implements Phase 2 of
[HAK5_COMPANION_PLAN.md](../docs/HAK5_COMPANION_PLAN.md): an authenticated Pineapple
Tetra API session, a live status view, runtime module/script discovery, gated action
execution, and a persistent command log. Builds directly on Phase 1
(branch `feature/hak5-companion-phase1`, PR #22); this branch targets/depends on it.

## Delivered

| Plan item (Phase 2) | Code |
|---|---|
| §1 Authenticated apiToken session (POST `/api/` with `apiToken`; hold session; surface status) | `PineappleSession` (`login`, token validation), `PineappleSessionManager` (in-memory holder, token **not** held here) |
| §2 Live status view (device / firmware / version / connection) | `PineappleSession.DeviceStatus` + `fetchStatus`, surfaced in `Hak5CompanionActivity` |
| §3 Runtime module/script listing (no fixed list) | `PineappleModuleLister` + `ModuleCatalog` / `PineappleModule` / `ModuleActionInfo`; defensive parsing of array/object/map shapes |
| §4 Run a module/action only after per-action confirmation; never auto-run | `PineappleActionRunner` (single choke point; gates via `Hak5ActionConfirm` **before** any network call), `Hak5ActionClassifier` (severity), confirm dialog in `Hak5CompanionActivity` |
| §5 Persistent, redacted, viewable command log | `Hak5CommandLog` (JSONL in app-private `filesDir/hak5_logs/hak5_commands.jsonl`), `Hak5CommandLogActivity` |

## Safety (preserved from Don's locked constraints)

- **Authorized-use gate first.** Every Phase 2 path still runs behind `Hak5AuthGate`.
- **Never auto-attack / never auto-run.** `PineappleActionRunner.run` calls
  `Hak5ActionConfirm.blockReason` first; if blocked it records the attempt and returns
  **without making any network request**. Read-only actions still require authorization
  but not an extra tap; anything `CONFIG` or `POTENTIALLY_INVASIVE` requires an explicit
  per-action confirmation dialog, with a bold warning for invasive actions.
- **No hardcoded module/action names.** Modules and their actions are discovered at
  runtime from the connected device and classified conservatively: anything not clearly a
  read verb defaults to `POTENTIALLY_INVASIVE` (`Hak5ActionClassifier`). Unknown/limited
  firmware yields an empty, limited catalog — we never invent modules.
- **Secrets never logged.** The apiToken is never written anywhere. `Hak5CommandLog`
  redacts per-field and then redacts the whole serialized line (reusing
  `Hak5AuditLog.redact`) before touching disk; params and response snippets are redacted,
  and a token echoed back by the device is scrubbed too.

## Decisions on the deferred questions

- **Module automation depth (open Q1):** runtime discovery + **one manual action per tap**,
  each individually gated. No chaining, scripting, or automation in v1.
- **API token vs SSH (open Q2):** **apiToken only.** SSH stays deferred; no JSch/sshj
  dependency was added.
- **Audit-/command-log storage:** app-private internal storage
  (`filesDir/hak5_logs/`): `hak5_audit.jsonl` (lifecycle) and `hak5_commands.jsonl`
  (actions). Never external/shared storage; both redacted; size-rotated.
- **Drawer placement:** unchanged from Phase 1 — the existing `Hak5 Companion` entry in
  the Common Functions group (`Hak5CompanionClickConfig`). The command log opens from a
  button inside the companion screen.
- **HTTP vs TLS to `172.16.42.1:1471`:** default to what the device serves — cleartext
  **HTTP** on the local link (as Phase 1) — and show an in-UI warning
  (`hak5_cleartext_warning`) that traffic is cleartext and should only be used on a
  trusted local network the user controls.

## Tests (MockWebServer + fixtures, plain JUnit — no Robolectric)

- `PineappleSessionTest` (5): login success / token-rejected / blank-token (no network) /
  unreachable; status fetch parses firmware, model, uptime.
- `PineappleModuleListerTest` (4): object modules+actions; name-only array; limited when
  API shape unconfirmed (no network); limited when firmware unknown.
- `PineappleActionRunnerTest` (4): blocked without confirmation (0 requests); blocked
  without authorization; executes when authorized+confirmed+capability-confirmed;
  executed entry persists and token is redacted on disk.
- `Hak5CommandLogTest` (5): append/read persistence; token redaction in response snippet
  and in params; blocked entries recorded with reason; clear.

New fixtures: `module_list_v1.json`, `module_list_names.json`, `action_run_ok.json`.

## Build / test status

- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL** (debug APKs produced).
- `./gradlew :app:testDebugUnitTest` — **40 tests, 0 failures, 0 errors** (38 in
  `com.termux.zerocore.hak5`: 20 Phase 1 + 18 new Phase 2; plus 2 pre-existing).
- `targetSdk` 28 and package `com.termux` unchanged.

## Not in Phase 2

- SSH path / script-path exec (deferred).
- USB tether or Plunder Bug capture (RootForge / later).
- Action chaining or automation.
