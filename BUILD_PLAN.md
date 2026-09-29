# VictorSuite — phased build plan

Grounded in `ARCHITECTURE.md`'s verified component inventory (2026-09-29).
This is a documentation/planning artifact — no implementation has been
done against it. Environment constraint that applies to every phase
below: **this environment has no Android SDK/AGP**, so nothing here has
been build- or instrumented-test-verified; that must happen in a session
with a real SDK before any phase is marked done.

## Phase 1 — Signing-key remediation (blocking, do first)

**Depends on:** nothing. **Blocks:** any release build anyone trusts.

The committed `phone.jks` plus hardcoded `storePassword`/`keyPassword`
fallbacks in `app/build.gradle`'s `signingConfigs.release` (unchanged as
of 2026-09-29 — see `CLAUDE.md`) mean anyone can sign an update Android
will accept as coming from the same developer, for any device that
already trusts this key.

Work:
1. Owner decision: has any build signed with this key ever been
   distributed (Play Store, F-Droid, GitHub Releases, direct APK)? This
   determines whether the key is already burned.
2. If burned: plan a new signing key and accept that existing installs
   won't auto-update from it (or enroll in Play App Signing if
   distributing there, which decouples the upload key from installs).
3. Either way: stop committing the real keystore and passwords going
   forward — generate a throwaway debug-only keystore for anything that
   must stay in-repo; move the real keystore/password to local
   `gradle.properties`, an env var, or a secrets manager, matching the
   pattern the commented-out debug config in the same file already uses
   correctly.
4. Add a CI or pre-commit check that fails the build if a `.jks` file is
   staged, to prevent recurrence.

**Acceptance criteria:**
- `git log` shows `phone.jks` removed from the tree (or replaced with a
  clearly-labeled dummy keystore) in a commit the owner reviewed.
- `app/build.gradle` has no hardcoded non-placeholder password literal.
- Owner has stated, in writing, whether the old key is treated as
  compromised and what the rotation/Play-signing decision was.

**This is a decision and a git-history action, not a coding task** — it
does not need a build to verify, which is why it can and should happen
before Phase 2.

## Phase 2 — Plugin framework: package management + test coverage

**Depends on:** Phase 1 not required but recommended first (don't add new
release-relevant code while the signing issue is open).

The framework (`plugin/`) and its browsing/configure/launch UI
(`dialog/PluginListDialog.kt`, `PluginConfigDialog.kt`,
`PluginManagerClickConfig.kt`) are real and wired in — see
`ARCHITECTURE.md`'s correction to the earlier "planned, not built" claim.
Two real gaps remain:

1. **Package management.** `PluginProcessLauncher.resolveCommand` only
   looks for an entry point the user already placed on disk; nothing
   installs, updates, or removes one. Scope: an install/update/remove
   flow driven from `PluginListDialog`, using whatever the manifest entry
   names as its acquisition method (`pkg`/`apt` inside Termux's own
   prefix is the natural fit, matching this app's existing package
   ecosystem) — this needs its own design pass (what `PluginManifestEntry`
   fields does it need, does it shell out to `pkg` via `libsu`/`ExeCommand`,
   how are install failures surfaced) before writing code; don't guess the
   design here.
2. **Test coverage.** Zero tests exist for `PluginRegistry`,
   `PluginConfigStore`, `PluginProcessLauncher`, or the authorized-use
   gate's two-step invariant. `PluginRegistry`/`PluginProcessLauncher`'s
   logic (state transitions, entry-point-exists check) is largely
   Android-`Context`-free except for `SharedPreferences`/
   `EncryptedSharedPreferences` access, so most of it is a reasonable fit
   for local JVM unit tests under `app/src/test` (Robolectric, or a thin
   interface seam over the preferences store) rather than needing a full
   instrumented/emulator run — instrumented tests are still the right
   place for the two dialog classes.

**Acceptance criteria:**
- A plugin manifest entry can go from registered to "binary installed,
  ready to enable" without the user manually copying a file into the
  plugin's install directory, for at least one real manifest entry.
- Unit tests exist and pass for: the two-step authorized-use gate
  (register→acknowledge→enable ordering, including the "enable without
  acknowledging fails" case), `PluginProcessLauncher`'s
  not-enabled/entry-point-missing failure paths, and
  `PluginConfigStore`'s round-trip of a `BOOL`/`SECRET`/`NUMBER` field.
- `./gradlew :app:testDebugUnitTest` passes, run in a session with a
  working Android SDK (not available in this session — flag as unverified
  until then).

## Phase 3 — AI-CLI real-device verification

**Depends on:** nothing (independent of Phases 1–2); needs a real Android
device or emulator, which this session does not have.

`docs/AI_CLIS.md`'s own status is "checked with shellcheck and dry-run
against stubbed commands, not yet run on a real device." Named open items
to close before calling this done: Copilot/Codex TUI rendering under
`proot-distro`, both tools' sign-in flows completing inside the container,
`pkg upgrade` succeeding from VictorSuite's 2022-vintage bootstrap, and
requiring `proot-distro` v5+.

**Acceptance criteria:**
- All four tools (`pi`, `copilot`, `codex`, `hf`) run `--version`
  successfully on a real device after running
  `scripts/ai-cli-setup.sh`.
- At minimum one sign-in flow (whichever is fastest to test, e.g. `hf
  login`) verified to complete inside the `proot-distro` container.
- `docs/AI_CLIS.md` updated to move these from "open items" to verified,
  with the device/Android version tested against.

## Phase 4 — CI reactivation

**Depends on:** Phase 1 (don't wire up automated release builds while the
signing key is compromised) at minimum for any release-build job; a
debug-only build/test job has no such dependency and could move earlier
if desired.

`.github/workflows/android.yml` is fully commented out today — there is
no active CI. Reactivating it as-is would also re-introduce a workflow
that assembles `assembleRelease` using the compromised signing config, so
this phase is explicitly sequenced after Phase 1's remediation, not
before.

**Acceptance criteria:**
- A CI job runs `./gradlew assembleDebug` (and, once Phase 2 lands,
  `testDebugUnitTest`) on every PR.
- A release job, if reactivated, uses only the post-remediation signing
  configuration from Phase 1 — never the current hardcoded fallback.

## Explicitly not in scope for this plan

- Rewriting or consolidating the two overlapping FTP server
  implementations (`ftp/server/` vs `ftp/new_ftp/`) — noted as a gap in
  `ARCHITECTURE.md`, but no owner decision exists yet on which to keep;
  needs that decision before it becomes a phase.
- Any droidcommand-AI-side integration work (e.g. a `core-termux`
  enhancement, or wiring the plugin framework to host a HackerAI/
  Pentest-Swarm-AI companion entry point) — that work, if pursued,
  belongs in `Victorious93/droidcommand-AI`'s own roadmap docs, not here;
  this repo's plan covers only what's inside `Victorious93/VictorSuite`.
- Implementation of any phase above. This document is planning only, per
  this run's scope.

## Next build phase

**Phase 1 (signing-key remediation).** It is the only phase with no
dependencies, is unresolved since at least 2026-09-27, and every other
phase either explicitly depends on it (Phase 4) or is safer to do after
it (Phase 2's new code shouldn't land on top of an open critical-severity
finding without the owner having made a call on it first).
