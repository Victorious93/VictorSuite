# VictorSuite — orientation and phase tracker

No `CLAUDE.md`/`CODEX.md` existed before this file (2026-09-27). Read this
first; update the **PROJECT STATE** section at the end of every session so
the next one doesn't have to re-derive what's here.

## What this repo is

A GPL-licensed Android app: a merge of the ZeroTermux codebase with Termux
components (`termux-shared`, `terminal-emulator`, `terminal-view`), per
`README.md` — "a community-driven, non-profit enhancement of the Termux
ecosystem." 4 Gradle modules (`app`, `termux-shared`, `terminal-emulator`,
`terminal-view`; see `settings.gradle`). `app` is Java-primary (307 `.java`
files) with a newer Kotlin layer (122 `.kt` files) added on top — verified
by direct count, 2026-09-27.

**CONFIRMED SECURITY ISSUE — real signing key exposed, needs owner action.**
Checked 2026-09-27. `app/build.gradle`'s `signingConfigs.release` (the
config actually used for the `release` build type) points `storeFile` at
`rootProject.file("phone.jks")` — a file committed to this **public**
repo — with `keyAlias`/`keyPassword`/`storePassword` hardcoded as fallback
defaults in that same file, used whenever the corresponding
`KEY_ALIAS`/`KEY_PASSWORD`/`STORE_PASSWORD` env vars aren't set (see that
block directly for the literal values — deliberately not repeated here).
Opening `phone.jks` with that hardcoded storePassword succeeds: it's a
real 2048-bit RSA key (self-signed cert, issued 2019-04-16, valid until
2044-04-09) — a live release-signing private key, sitting in plaintext in
a public repository, with its password also committed in plaintext in the
same repo.

**This needs the project owner's direct attention, not just a doc note:**
anyone can already sign an update to this app's package that Android would
accept as coming from the same developer. If this key has ever signed a
build distributed anywhere (Play Store, F-Droid, GitHub Releases, direct
APK), treat it as compromised: it can't be "fixed" after the fact for
existing installs signed with it (Android ties app updates to the original
signing key), and the real mitigation is Play App Signing enrollment (if
distributed there) or accepting that future releases need a new key
(existing installs won't auto-update from the new one). At minimum, stop
committing the real keystore/password to git going forward — generate a
throwaway keystore for anything that needs to stay in-repo, and keep the
real one plus its password outside version control (local
`gradle.properties`, an env var, or a secrets manager), matching what the
`(commented-out)` debug config above this one in the same file already
does correctly (its own dummy password is clearly a placeholder, not a
real secret). `app/dev_keystore.jks` was not checked further given the
finding above already meets the bar for action.

## Current phase

- [x] Base app — ZeroTermux + Termux merge, existing/inherited codebase
- [x] Plugin framework (`com.termux.zerocore.plugin`, Kotlin) — **core
      pieces implemented**, per `PLUGINS.md` ("Core pieces (this PR)"),
      confirmed by direct file listing: `PluginManifest.kt`,
      `PluginState.kt`, `PluginRecord.kt`, `PluginRegistry.kt`,
      `PluginConfigStore.kt` (encrypted-at-rest via AndroidX Security),
      `PluginLogger.kt` (audit trail), `PluginProcessLauncher.kt`. Design
      is infrastructure-only and tool-agnostic: it registers/gates/launches
      external tools the user installs themselves; it does not bundle,
      download, or automate any specific tool. Two-step authorized-use
      gate (`register` → always `DISABLED` → `acknowledgeAuthorizedUse` →
      `enable`) enforced independently in both the registry and the
      launcher.
  - [ ] **Explicitly PLANNED, not yet built** (per `PLUGINS.md` itself):
        package-management UI, a terminal/GUI shell for browsing/launching
        registered plugins, a settings screen backed by
        `PluginConfigField`/`PluginConfigStore`.
- [x] AI CLI setup script (`scripts/ai-cli-setup.sh`, `docs/AI_CLIS.md`) —
      installs `pi`, GitHub Copilot CLI, OpenAI Codex CLI, and the Hugging
      Face CLI (three of the four via an Ubuntu `proot-distro` container,
      since none of copilot/codex/hf ship an Android/aarch64 build path
      today — see that doc for the exact upstream-issue citations).
      **Status per the doc's own words: checked with `shellcheck` and
      dry-run against stubbed `pkg`/`proot-distro` commands. Has NOT yet
      been run on a real device.** Explicit open items before calling it
      done: Copilot/Codex TUI rendering under proot, both tools' sign-in
      flows completing inside the container, `pkg upgrade` from
      VictorSuite's (old, 2022) bootstrap, and requiring `proot-distro` v5+.

## Repo history note

Most recent commit merges PR #1 (`claude/zerotermux-victorsuite-push-bhkuc6`,
2026-09-26) — from a `--depth 1` clone this looks like the initial
ZeroTermux→VictorSuite import landing in the same PR as the plugin
framework and AI-CLI docs above; not independently verified against full
history. If that matters to a future session, fetch deeper history rather
than assume.

## What to do next

Nothing scheduled beyond what's marked planned above. Before starting any
of it:
1. **Highest priority, blocking-severity:** act on the signing-key exposure
   above — decide whether the key is already burned (any real distributed
   build?) and either rotate it (accepting the update-path break) or, at
   minimum, stop it from being committed going forward.
2. If continuing the plugin framework: package-management UI is the next
   unbuilt piece named in `PLUGINS.md`.
3. If continuing the AI CLI work: get it onto a real device and verify the
   specific open items `docs/AI_CLIS.md` lists — don't mark it done on the
   strength of the dry-run alone.

## Source of truth

When this file and the code disagree, the code wins — update this file,
don't trust it uncritically. Re-verify file counts/module list against
`settings.gradle` and `find` before trusting the numbers above too far
into the future.
