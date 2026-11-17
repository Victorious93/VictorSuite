# Terminal assistant (`??`) and optional modern BusyBox — plan

> **Status: DESIGN ONLY.** No app code, no version bump, no bundled BusyBox
> binary, and no `??` interceptor in this PR.
> Do not merge without Don's approval.
>
> Written Nov 16, 2026 against `main` @ `9138004` (v0.118.3.64, after #107
> and #108). Confidence labels: **[Certain]**, **[Likely]**, **[Guessing]**.

## 0. Provenance and non-copy rule

This plan is **inspired by ideas** seen in ObsidianBox Modern. That product's
main application is proprietary. **VictorSuite must not copy its code, text,
assets, layouts, prompts, or intent tables.** The sections below are original
design for this GPLv3 Termux fork. If a later implementer has also seen that
app, they still start from this document and write new code.

## 1. Goal and standing rules

Give the terminal two optional, standalone capabilities:

1. A plain-English **`??` prefix** that turns a short request (`?? cpu temp`,
   `?? magisk modules`) into a concrete command, **offline first**, then
   (optionally) a local LLM. The resolved command is always shown before it
   runs. Anything destructive requires confirmation.
2. An **optional bundled modern BusyBox** for root users: upstream **1.36.x**
   built with this repo's NDK, shipped with correct **GPL-2.0** corresponding
   source, **off by default**.

Standing rules that this design must not break:

- Fleet UI: English, dark neon (`#0B0F1A` / `#00E5FF` / `#FF2BD6`).
- No ZeroTermux traces in new paths, copy, or file names.
- **DroidCommand AI is VictorSuite's only AI path.** In-app DeepSeek was
  removed on main in #96 **[Certain]**. No new cloud AI, and no cloud by
  default.
- Every app works standalone, including without VictorLinux and without
  DroidCommand AI. The matcher must work with no other fleet app installed.
- No secrets in the repo or in logs. Queries must not be sent or logged
  off-device.
- Work on a branch and a PR. No merge, no push to `main`, no release, no
  repo-settings change without Don's yes.

Don's day-to-day device is a Moto G Play on Android 16, so the
**arm64-v8a** APK is the first binary that has to work **[Certain]** (fleet
working rule).

## 2. Recent main (reviewed before writing)

Reviewed `main` on Nov 16, 2026 (US/Eastern). Notable for this plan:

- `#108` (`9138004`) README polish; `#107` (`b836e21`) VictorLinux
  integration plan (now on main). A follow-up in this same PR records that
  Don chose option A for all six #107 questions on Nov 16, 2026.
- `#106` / `#105`: release pruning keeps the previous release; current
  pre-release is **v0.118.3.64** (`versionCode` 126).
- `#104`, `#103`, `#102`: lint and stability work, including Claude Code
  session merges. Implementers should re-read `main` before coding.
- `#101`: quick-SSH secrets stay off argv and logcat. The same rule applies
  to assistant queries, LLM prompts, and any BusyBox build credentials.
- `#96`: in-app DeepSeek removed; DroidCommand AI is the AI entry in the
  drawer and Settings **[Certain]** (also stated in the README).

## 3. Item A — plain-English `??` prefix

### 3.1 Behaviour

A line that begins with `??` (optional space) is an assistant query, not a
literal shell command. Examples: `?? cpu temp`, `?? magisk modules`.

Resolution order **[Certain]** (this is the product rule, not an
implementation detail):

1. **Built-in offline matcher** (intent table, keywords, fuzzy match).
2. If the matcher does not produce a high-enough match **and** the user has
   opted into an optional local LLM, ask that LLM. **No cloud by default.**
3. If nothing matches, print a short English "I don't know" with closest
   suggestions. Do not invent a command and do not run anything.

The feature **always shows the exact command** (argv / script text that
would be executed) **before running it**. Non-destructive commands still get
that preview; the user confirms with a single explicit accept (Enter / a
primary button). Anything the destructive classifier flags **requires a
second, clearly labelled confirmation**. It never auto-runs on a fuzzy
guess.

The matcher must offer **root and non-root variants** of the same intent.
If the chosen variant needs root and `su` is not available, show the
command and explain that it needs root; do not silently run a weaker
command, and do not silently escalate.

### 3.2 Intent table

**[Likely]** The table is a versioned JSON document, not hard-coded Java.
Each entry should include:

- `id`, English `title`, `keywords`, optional alias phrases
- `command_user` and `command_root` (or a single command plus a `needs_root`
  flag)
- Android-version notes (especially Android 16 caveats)
- `destructive` hint (the classifier in §3.4 is still the authority)
- `confidence` / match hints, and a short `explain` string shown with the
  preview

Starter intents (illustrative, not a copied list):

| Intent | Non-root variant **[Likely]** | Root variant **[Likely]** | Android 16 caveat |
|---|---|---|---|
| CPU / battery temperature | `dumpsys thermalservice` or `cmd thermalservice` | Read `/sys/class/thermal/thermal_zone*/type,temp` when policy allows | Thermal sysfs is often SELinux- or permission-restricted for the untrusted app and even for the unprivileged shell **[Likely]**. Prefer the dumpsys/cmd path first; say so if sysfs is unreadable. Zone-to-CPU mapping is device-specific **[Guessing]** on the Moto G Play. |
| Magisk modules | Explain that listing modules needs root; do not fake a list | `magisk --list-modules` and/or `ls /data/adb/modules` | Only when Magisk (or a compatible manager) is actually present. KernelSU / APatch have different module paths **[Likely]**. |
| Disk / memory / battery | `df -h`, `free -h`, `dumpsys battery` via Termux/API or `cmd` | Same, plus paths under `/data` or `/sys` if needed | No change beyond ordinary Android 16 permission limits. |

The built-in table ships in the APK. **[Likely]** install location after
first run: `$PREFIX/share/victorsuite/assistant/intents.json` (a
VictorSuite-owned path, not a `zt*` name). User and plugin overlays live in
a separate directory, for example `$PREFIX/share/victorsuite/assistant/overlays.d/`,
and can only add or override by `id` after install. A later app update must
not silently wipe user overlays.

Fuzzy match **[Likely]**: normalize case and punctuation, token overlap, then
a small edit-distance pass. Below a threshold, show the top candidates
instead of picking one. Exact keywords win over fuzzy hits.

### 3.3 How the `??` prefix is intercepted

Two realistic hooks, plus a hybrid. This is **open question Q2**.

#### Option A (recommended) — shell function in the bootstrap profile

Install a `??` function (and only that function) into the existing
VictorSuite block of `$PREFIX/usr/etc/bash.bashrc`.
**[Certain]** `BashFileUtils` already writes `# VictorSuite[Start]` …
`# VictorSuite[End]` into that file.

The function forwards the rest of the line to a small helper
(`$PREFIX/libexec/victorsuite/vs-ask` or similar). The helper runs the
matcher, prints the preview, and for destructive commands starts a native
confirm Activity and waits for the result on a local socket or a
`TermuxService` bind (same idea as other Termux helpers). Non-interactive
shells must not define the function (check `$-` or `PS1`).

Pros: works in any interactive Termux bash session, including SSH-into-
Termux and sessions started outside the UI; uses an existing injection
point; is visible and removable; does not hook the emulator.

Cons: depends on bashrc surviving user edits; zsh/fish need a sibling
snippet if those shells are later first-class; a native dialog needs a
small app helper, not just `read -p`; a user can delete the function.

#### Option B — hook terminal input in the view / session

Intercept a line in `TerminalView` / the session **before** it is written
to the PTY, if it matches `^\?\?\s?`.

Pros: consistent native preview and confirm dialog; independent of
bashrc; harder to accidentally disable.

Cons: misses SSH-in, `RUN_COMMAND`, scripts, and pasted multi-line input
unless each path is hooked; more emulator coupling; easier to get wrong
with IME composition and history.

#### Option C — both

A shell function for non-UI sessions and a view hook for the app window.
Most complete, most code to keep in sync. Not recommended as the first
cut.

Recommended default (Q2.A): **bashrc function + helper**, with the helper
owning preview text and the native confirm dialog.

### 3.4 Destructive classifier

The classifier is a deliberate denylist plus path/operator heuristics, not
an LLM judgement. If it is unsure, it **confirms**. It is evaluated on the
**resolved command**, not on the English query.

Treat as destructive (non-exhaustive, all **[Certain]** as policy):

- File destruction / wipe: `rm`, `rmdir` of non-empty or `/` paths,
  `shred`, `truncate` of existing files, `unlink`
- Raw disk and filesystems: `dd`, `mkfs`, `mkfs.*`, `fdisk`, `parted`,
  `sgdisk`, `wipefs`, `format`
- Writes under `/system`, `/vendor`, `/product`, `/boot`, `/dev/block`,
  or other read-only OS partitions (including `cp`/`mv`/`install`/`tee`
  into those paths, and remount-`rw` of the same)
- Power and firmware: `reboot`, `shutdown`, `halt`, `fastboot`,
  `bootloader`, `recovery`, `flash`, `lpmake`, `avbctl`
- Privilege chains: `su`, `sudo`, `ksu`, `daemonsu`, or any pipe/semicolon
  chain that reaches them (a matcher-produced `su -c '…'` is still
  destructive if the inner command is, and the `su` itself always
  confirms)
- Package / userdata loss: `pm uninstall`, `pm clear`, `recovery --wipe`,
  factory reset, `fastboot erase`, `rm -rf /data`
- Process kill of system or all-user processes: `kill`, `killall`,
  `pkill` aimed at `system_server`, `zygote`, or `-9 -1`
- Broad firewall or policy flush: `iptables -F`, `setenforce 0` is
  **sensitive** and should confirm, even if it is not data-destructive

This list must stay aligned with the fleet envelope already documented in
[`MANUAL.md`](MANUAL.md): names that look like shell, exec, su, root, wipe,
flash, bootloader, reboot, shutdown, lock, kill, delete, or rm are
destructive there **[Certain]**. The `??` classifier can be stricter, not
looser.

Confirm UI (native, English, fleet chrome):

- Background `#0B0F1A`, primary text and borders `#00E5FF`, the confirm-
  destroy action `#FF2BD6` (not a casual green "OK")
- Title **Confirm destructive command**, the exact command in a monospace
  block, a one-line reason ("This would write to `/system`", "This would
  run `dd`"), buttons **Cancel** (default) and **Run anyway**
- No "don't ask again" for the destroy class
- Must work with a hardware or IME keyboard; do not require a mouse

Preview of a *non-destructive* command can be a lighter in-terminal prompt
or the same dialog without the magenta destroy styling. Either is fine;
the destroy path must be the dialog.

### 3.5 Optional local LLM (no cloud by default)

The matcher is sufficient for a useful v1. An optional LLM is a later,
opt-in step.

Fleet constraint **[Certain]**: DroidCommand AI is the only AI path, and
DeepSeek is gone. Raising a new in-app cloud chat would violate that.

**Open question Q1** (recommended first):

- **A.** Send unmatched queries to the **DroidCommand AI** app (on-device
  or whatever local policy that app already uses). VictorSuite does not
  grow its own model runtime. The request stays on-device unless the user
  has already opted into some cloud path *inside DroidCommand AI*;
  VictorSuite itself still defaults to no cloud and does not add a new
  endpoint.
- **B.** Bundle or download an on-device model inside VictorSuite. Larger
  APK, new licence and performance work, and it duplicates the fleet AI
  app.
- **C.** Drop the optional LLM. Matcher only.

Whatever Don chooses, VictorSuite must not send `??` queries to a
VictorSuite-owned cloud API, and must not revive DeepSeek.

### 3.6 Privacy

- Matcher and classifier run on-device. **No logging of queries off-device**
  **[Certain]**.
- Do not write the raw query or resolved command to logcat. Debug builds
  may log intent *ids* and match scores, not the user's words or argv.
- Do not include queries in crash reports or fleet envelope telemetry.
- No network permission use for this feature in the default (matcher-only)
  configuration.
- If Q1.A is chosen, the DroidCommand AI hand-off is the only extra path
  and must follow that app's own consent rules. A refused or missing
  DroidCommand AI install falls back to "no match", never to the internet.

### 3.7 Plugin extensibility

**[Likely]** reuse the direction of [`PLUGIN_SYSTEM.md`](PLUGIN_SYSTEM.md):
a plugin may ship an overlay JSON in `overlays.d/` (or a documented
plugin type, once that system exists). Overlays cannot disable the
destructive classifier, cannot mark a destroy command as safe, and cannot
register a cloud endpoint. Malformed overlays are ignored.

### 3.8 Tests (later feature PR, not this one)

Unit tests, no device and no network:

- Prefix parsing (`??cpu`, `?? cpu temp`, leading spaces, not `echo ??`)
- Keyword and fuzzy match for the starter intents
- Root vs non-root variant selection, including "root required but absent"
- Destructive classifier positives (`rm -rf /`, `dd of=/dev/block/…`,
  `mkfs`, write to `/system`, `reboot`, `su -c …`) and obvious negatives
  (`ls`, `df -h`, `dumpsys thermalservice`)
- Low-confidence path returns suggestions and does not produce a runnable
  command
- Overlay cannot weaken the classifier
- A test fixture asserts the matcher has **zero** network calls

Do not claim a build is green until CI or a local run has actually been
observed.

### 3.9 Planned `MANUAL.md` additions (not written in this PR)

When a feature PR lands, add two short user-facing sections to
[`MANUAL.md`](MANUAL.md), in English:

**Terminal assistant (`??`)**

- What `??` does, that it is offline-first, and how to turn it off
- It always shows the command; destructive commands need a confirm dialog
- Optional LLM: only if Don picked Q1.A or Q1.B, and only when the user
  enables it; no cloud by default; DroidCommand AI if that is the path
- Privacy: queries stay on the device
- Examples: `?? cpu temp`, `?? magisk modules`, plus the Android 16
  thermal caveat

**Optional BusyBox (root)**

- Off by default; who it is for; where the binary lives
- How to enable, how to run applets (`busybox ls` vs optional symlinks)
- How it interacts with Termux coreutils and Android toybox
- How to uninstall and how to check the checksum
- Licence: GPL-2.0, where to get the exact source, config, and patches
- Pointer to About / `THIRD_PARTY_NOTICES.md`

Until that feature PR, `MANUAL.md` should not describe these as if they
already shipped.

## 4. Item B — optional bundled modern BusyBox (root users)

### 4.1 What already ships (do not confuse with this item)

**[Certain]** VictorSuite already unpacks BusyBox-like binaries today:

- `app/src/main/assets/zipcommand/busybox_arm.7z` (~908 KiB) and
  `busybox_arm64.7z` (~919 KiB)
- `BusyBoxManager` (`com.termux.victor.bosybox`, typo in the package name)
  auto-extracts on app start to `…/files/ztbusybox/` (`ztbusybox_arm64.7z`
  → `busybox_arm64`)
- Backup / Restore / Switch also write a `busybox` under
  `/data/data/com.termux/busybox` from other assets

Those copies are used as an archive helper (tar / 7z), are named with a
`zt` prefix in the extract path, auto-install, and **are not listed in
`THIRD_PARTY_NOTICES.md`** **[Certain]**. Their exact upstream version was
not identified in this planning pass **[unverified]**. That is a
**pre-existing GPL compliance gap**. This plan does not fix it in this
docs PR. The new optional BusyBox must not repeat it, and must not reuse
the `ztbusybox` path or the auto-install-on-boot behaviour.

**Open question Q9** covers whether a later PR replaces or retires those
old binaries.

### 4.2 Upstream and toolchain

- Target series: **BusyBox 1.36.x**, as requested.
- **[Certain]** (busybox.net, checked Nov 16, 2026): **1.36.1** (19 May
  2023) is the latest **stable** release in that series. **1.37.0**
  (27 Sep 2024) and **1.38.0** (13 May 2026) are published as
  **unstable**. A static musl 1.36.0 build is about 1.0 MiB of text;
  1.38.0 is about 1.02 MiB. See open question **Q4**.
- **[Certain]** this repo pins `ndkVersion=29.0.14206865` in
  `gradle.properties`, and `.github/workflows/android.yml` installs
  `ndk;29.0.14206865`. The BusyBox build must use that same NDK (r29),
  overridable with `JITPACK_NDK_VERSION` only if the app build does.
- Licence: BusyBox is **GPL-2.0-only** **[Certain]** (not GPLv3, not
  "or later"). Shipping a binary without corresponding source (or a valid
  written offer), config, and any patches would be a violation. The
  Software Freedom Conservancy acts as a GPL enforcement agent for
  various BusyBox copyright holders.

### 4.3 Build in CI (reproducible, per-ABI, arm64 first)

Later feature work, not this PR:

- A documented script (for example `scripts/busybox/build.sh`) that,
  given a pinned tarball SHA-256, a committed `.config`, and the r29
  NDK, produces one static (or NDK-libc) binary per ABI:
  **arm64-v8a first**, then `armeabi-v7a`, `x86_64`, `x86`.
  **[Certain]** the app already splits those four ABIs and also builds a
  universal APK.
- CI should be reproducible: same inputs → same SHA-256. Record the
  exact configure flags, NDK clang, API level, and `CONFIG_*` in the
  repo. Do not download an unaudited third-party binary.
- PR CI does not have to rebuild every ABI on every docs or Java change.
  **[Likely]** rebuild BusyBox when the script, config, patches, or
  tarball pin change; always build **arm64-v8a** in that case; build all
  ABIs on release.
- Each shipped binary is accompanied by a SHA-256 in
  `scripts/busybox/SHA256SUMS` (or equivalent) and checked at install
  time.

### 4.4 GPL-2.0 compliance (must-haves)

Before any binary ships in an APK or a release asset:

1. The **exact corresponding source** (the 1.36.x tarball that was built)
   is either in this repo or published as a release asset next to the
   APK. A hash of that tarball is committed.
2. The **`.config`** and **every patch** used to build it live in the
   repo (for example `third_party/busybox/`).
3. Licence notices travel with the binary (a `COPYING` / `LICENSE` file
   in the install directory and a mention in About).
4. `THIRD_PARTY_NOTICES.md` and About / `vs_source_code_credits` credit
   BusyBox, GPL-2.0, the upstream URL, the exact version, and where to
   get the source, config, and patches.
5. Prefer **Q8.A**: ship the tarball (repo or release asset), not only a
   written offer. A written offer is a legal fallback, not the preferred
   UX for a public GPLv3 app that already publishes source.

Do not place the source tarball *inside* every APK just to satisfy the
licence if it would bloat the phone install; a release asset plus a
committed hash and an in-app "Source" link is enough **[Likely]**, as
long as the offer remains valid for the GPL-required period. Putting the
tarball in the git repo is the simplest audit trail.

### 4.5 Off by default; install path; PATH; uninstall

Off by default **[Certain]** (product rule). It is not extracted on app
start, unlike the existing `ztbusybox` helper.

**Open question Q5** (install path):

- **A (recommended):** App-private / prefix location, for example
  `$PREFIX/opt/victorsuite/busybox-1.36.1/busybox`. No `/system` write.
  Easy to checksum and to delete.
- **B:** Also offer a Magisk (or KernelSU / APatch) module for users who
  want applets outside Termux. More moving parts, still reversible.
- **C:** Install into `/system`. Hard to uninstall, fights Android 16
  and treble, easy to brick a slot. Rejected as the default.

**Open question Q6** (PATH and applets):

- **A (recommended):** Ship **one** multi-call binary. Users run
  `busybox ls`. An optional symlink directory may be **appended** (not
  prepended) to `PATH`, so Termux `coreutils` / `toybox` already on
  `PATH` keep winning. Document conflicts (`ls`, `cp`, `tar`, `sed`,
  `awk`, `nc`, …).
- **B:** Prepend the symlink directory (BusyBox shadows Termux
  coreutils). Convenient, surprising, and a support hazard.
- **C:** Replace `/system/bin` applets. Dangerous; same as Q5.C.

**Open question Q7** (who can enable it):

- **A (recommended):** Settings toggle is visible but disabled until
  root is detected (Magisk / KernelSU / APatch / working `su`). Matches
  "for root users".
- **B:** Any user may extract it into the prefix (BusyBox is useful
  without root, but broader than the brief).
- **C:** Always extract the binary; the toggle only changes `PATH`.

Uninstall: the toggle off removes the symlink directory from `PATH` and
deletes the opt directory (and a Magisk module, if Q5.B was used). It
must **not** delete the legacy `ztbusybox` helper or
`/data/data/com.termux/busybox` until Q9 says those have a replacement.
A reinstall re-extracts from the APK and re-checks the SHA-256.

### 4.6 APK size impact

**[Likely]** a stripped NDK-built BusyBox 1.36.1 for one Android ABI is
on the order of **1–1.5 MiB**. Existing split APKs already isolate ABIs,
so the **arm64-v8a** APK Don sideloads should grow by roughly that
amount, plus a small amount of Java/settings. A universal APK would add
about four times that **[Likely]**.

**[Guessing]** gzip/xz inside the APK might cut the on-disk APK growth
to ~0.5–0.8 MiB per ABI. Do not treat that as a measurement.

Do not also pack the full source tarball into the APK if we can publish
it beside the release (see §4.4). The existing `zipcommand/busybox_*.7z`
assets already add ~1.8 MiB to the APK **[Certain]**; this plan does not
remove them yet (Q9).

### 4.7 Tests (later feature PR)

- SHA-256 of the unpacked binary matches `SHA256SUMS`
- `busybox` is absent from `PATH` when the toggle is off
- Enable + disable + uninstall is idempotent and leaves Termux
  `coreutils` ahead of any symlink dir (if Q6.A)
- Applets that exist in both BusyBox and Termux `coreutils` / toybox are
  listed in a checked-in conflict table
- A licence check test fails the build if notices, `.config`, or the
  source hash are missing
- No root, `/system`, or Magisk flash happens in unit tests

## 5. Open questions for Don

First option is the recommendation; the rest are best to worst.

**Q1. Where should the optional LLM go?**

- **A (recommended):** Through the **DroidCommand AI** app, opt-in, still
  no cloud by default on the VictorSuite side. Honours the fleet rule
  that DroidCommand AI is the only AI path.
- **B:** An on-device model inside VictorSuite (new runtime, size, and
  licence work; duplicates the fleet AI app).
- **C:** Drop the optional LLM. Ship the offline matcher only.

**Q2. How should `??` be intercepted?**

- **A (recommended):** A shell function in the bootstrap
  `bash.bashrc` VictorSuite block that calls a `vs-ask` helper. The
  helper always prints the command and opens a native confirm dialog for
  destructive commands.
- **B:** Hook terminal input in `TerminalView` / the session before it
  reaches the PTY.
- **C:** Both at once (more complete, more to maintain).

**Q3. Where does the intent table live, and how do plugins extend it?**

- **A (recommended):** Versioned JSON in app assets, installed to
  `$PREFIX/share/victorsuite/assistant/`, with plugin/user overlays in
  `overlays.d/`. Overlays cannot weaken the destructive classifier.
- **B:** Hard-coded in Kotlin (easier to miss for translators and
  plugins).
- **C:** Only a user-edited file in `$HOME` (harder to update with the
  app).

**Q4. Which BusyBox upstream version should we pin?**

- **A (recommended):** **1.36.1**, the latest stable in the requested
  1.36.x series.
- **B:** **1.38.0**, the current upstream *unstable* as of 13 May 2026.
- **C:** "Whatever is newest when we implement" (not reproducible).

**Q5. Where should the optional BusyBox be installed?**

- **A (recommended):** App-private / `$PREFIX/opt/victorsuite/busybox-…/`,
  off `PATH` by default. No `/system` write.
- **B:** Also offer a Magisk (or KernelSU / APatch) module for
  system-wide applets.
- **C:** Write into `/system`.

**Q6. How should applets meet Termux coreutils and Android toybox?**

- **A (recommended):** One multi-call binary; applets via
  `busybox <applet>`; optional symlink dir **appended** to `PATH`.
- **B:** Prepend the symlink dir (BusyBox wins; surprising conflicts).
- **C:** Replace `/system/bin` applets.

**Q7. Who is allowed to enable the bundled BusyBox?**

- **A (recommended):** Off by default; the Settings toggle works only
  when root is detected.
- **B:** Any user may install it into the Termux prefix.
- **C:** Always extract the binary; the toggle only changes `PATH`.

**Q8. How do we fulfil BusyBox's GPL-2.0 corresponding-source rule?**

- **A (recommended):** Ship the exact tarball plus `.config` and patches
  in this repo and/or as a release asset published with the APK; keep
  notices; credit in About and `THIRD_PARTY_NOTICES.md`.
- **B:** A written offer for source (legal, weaker UX for a public repo).
- **C:** A link to upstream only (not enough if we configure or patch).

**Q9. What happens to the existing `ztbusybox` / backup BusyBox
binaries?**

- **A (recommended):** Leave them in place for this feature. The new
  BusyBox is a separate, optional, correctly licensed artifact. A later
  PR may retire the old assets once backup/restore uses the new binary
  (and once the pre-existing notices gap is closed).
- **B:** Replace the old assets with the new build in the first BusyBox
  feature PR (risk to backup/restore).
- **C:** Delete the old binaries now with no replacement path.

**Q10. Which ABIs carry the new binary?**

- **A (recommended):** Continue the existing per-ABI splits; **arm64-v8a
  first**; each split APK carries only its own binary.
- **B:** arm64-v8a only (smaller, drops 32-bit and x86 emulators).
- **C:** Only a fat/universal APK with all four ABIs (larger download
  for Don's phone).

**Q11. Is the `??` matcher on by default?**

- **A (recommended):** Matcher **on** by default, with a Settings
  toggle to disable it. Preview always; destructive confirm always.
  Optional LLM **off** until the user opts in (and only if Q1 is not C).
- **B:** Matcher off until enabled in Settings.
- **C:** Matcher on and auto-run non-destructive commands without a
  preview (rejected: it breaks the "always show the command" rule).

## 6. Phased milestones (later PRs, each needs Don's yes)

Docs only today. Suggested cuts, each its own branch and PR, no merge
without a later yes, no version bump until a feature actually ships:

| Phase | What | Notes |
|---|---|---|
| 0 | This plan | Docs only. |
| 1 | Offline `??` matcher + preview + destructive classifier + neon confirm dialog | No LLM. Intent table + unit tests. `MANUAL.md` section for `??`. |
| 2 | Settings toggle, overlays / plugin hook, privacy tests (no logcat, no network) | Still matcher-only. |
| 3 | Optional LLM, only after Q1 | If A: DroidCommand AI hand-off. If C: skip this phase. |
| 4 | BusyBox CI build, r29, arm64 first, `.config`, source tarball, notices, checksums | No install UI yet; do not ship an unlicensed binary. |
| 5 | Optional install / uninstall / PATH (per Q5–Q7), conflict table, `MANUAL.md` BusyBox section, About credit | Off by default. Do not touch `feature/scrcpy` or pairing code. |
| 6 | Optional follow-up: retire or re-license the legacy `ztbusybox` helpers (Q9) | Separate PR. |

CI for this docs PR is the existing `.github/workflows/android.yml`
(`:app:assembleDebug` and `:app:testDebugUnitTest` on pull_request). No
new workflow is added here.

## 7. Non-goals (this plan and the first feature cuts)

- No copied ObsidianBox Modern code, text, or assets
- No cloud assistant, no DeepSeek, no new AI brand
- No push to `main`, no merge, no release, no versionName bump in the
  docs PR
- No `/system` write as a default install
- No change to victorpc pairing, VictorLinux, or the `feature/scrcpy`
  branch
- No claim that the existing bundled BusyBox is GPL-clean; that is a
  separate follow-up (Q9)

