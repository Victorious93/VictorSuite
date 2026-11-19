# VictorSuite's role in the SET/Metasploit backend (2026-11-19 audit)

Scoped by the project owner's "DroidCommand SET/Metasploit backend" master prompt. The
actual new backend implementation lives in VictorLinux (`secbackend.py` et al.) and the
new Linux-backend client lives in droidcommand-AI (`core-security-backend`,
`LinuxBackendMetasploitExecutor`/`LinuxBackendSetExecutor`) — see those repos' own docs
(`VictorLinux/docs/security-backend.md`, `droidcommand-AI/docs/ARCHITECTURE.md` §22) for
the real, verified work. This file records what was checked here and why nothing in this
repo needed to change for that work.

## What was checked

Per the master prompt's explicit instruction ("Preserve and repair the existing
RUN_COMMAND path instead of introducing a competing phone daemon by default"):

- `app/src/main/java/com/termux/app/RunCommandService.java` is the unmodified upstream
  Termux `RUN_COMMAND_SERVICE` handler — no VictorSuite-specific changes exist in it beyond
  the standard `TermuxRemoteEnvelopeBinding` import already present. It still forwards
  `ACTION_RUN_COMMAND` intents to `TermuxService` for execution exactly as upstream Termux
  does.
- The manifest declares `${TERMUX_PACKAGE_NAME}.permission.RUN_COMMAND` and the
  `RUN_COMMAND` intent-filter on the service, matching droidcommand-AI's
  `AndroidPendingIntentRunCommandClient`/`TermuxExecutor` expectations.
  `com.termux.RUN_COMMAND_PENDING_INTENT` result delivery is unchanged.
- No second, competing execution daemon exists anywhere in `victor/` — `VsSocketService`
  (the loopback `zt` command port, `victor/config/ztcommand/`) is VictorSuite's own
  pre-existing remote-command surface for its own `zt` CLI/plugin ecosystem, unrelated to
  and not touched by this work; `RUN_COMMAND` remains the only path droidcommand-AI's
  Metasploit/SET tools use.
- `VsEngineManage` (`victor/engine/`) is confirmed to be the pre-existing no-op stub for an
  optional, unshipped engine add-on; it has nothing to do with Metasploit/SET execution and
  was not touched.

**Conclusion: no code change was needed in this repo.** The RUN_COMMAND path
droidcommand-AI's `TermuxMetasploitExecutor`/`TermuxSetExecutor` already depend on is
intact, unmodified, and was not at risk from the new Linux-backend work, which is a
completely independent second path (see the other two repos' docs). "Finish the existing
paths before duplicating them" is satisfied by leaving this one alone rather than
rewriting something that was not broken.

## What is still not verified (pre-existing gap, not introduced or closed here)

- Nothing in this repo's RUN_COMMAND handling has ever been exercised against a real,
  installed `msfconsole`/`setoolkit` inside its own Kali proot from a real device — that
  gap is droidcommand-AI's own documented status for `TermuxMetasploitExecutor`/
  `TermuxSetExecutor`, not something this audit closes.
- `core-termux.VictorSuiteCapabilitiesProbe` (droidcommand-AI) gained a `kali_setoolkit`
  presence check in this same work (alongside its existing `kali_msfconsole` one) — that is
  a droidcommand-AI-side change, listed here only because it is the first time this repo's
  RUN_COMMAND path is asked to prove `setoolkit`'s presence specifically, not just
  Metasploit's. No VictorSuite code was needed for it: the probe is `bash -lc "..."` sent
  over the existing RUN_COMMAND path, the same way `AdbTermuxExecutor`'s/
  `TermuxMetasploitExecutor`'s own commands already are.

## Not in scope here

- VictorLinux as a `victorpc` v1 pairing target (`docs/VICTORLINUX_INTEGRATION_PLAN.md`) is
  a separate, pre-existing, design-only plan about SSH/SFTP PC pairing — unrelated to the
  new `secbackend.py` security-tool execution service, which is a different port, a
  different protocol, and a different trust boundary. Do not conflate the two.
