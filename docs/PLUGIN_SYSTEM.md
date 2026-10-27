# VictorSuite Plugin System — Design (no code yet)

**Status:** DESIGN ONLY. Decisions recorded Oct 27, 2026 (§13). No code changes in this PR.
**Owner:** Don Victor ([Victorious93](https://github.com/Victorious93)).
**Context:** VictorSuite is a GPLv3 Termux fork. `applicationId` stays `com.termux`. Kotlin/Java code moves from `com.termux.zerocore` to `com.termux.victor` in PR #71. This design uses the new package names and assumes #71 lands first.

Related: `PLUGINS.md` (the current framework), `docs/HAK5_COMPANION_PLAN.md` (the approval-gate pattern), `docs/SUITE_PLAN.md` (the two-APK suite), and the author guide `docs/PLUGIN_API.md`.

---

## 1. What exists today (read before designing)

| Piece | Where | Notes |
|---|---|---|
| Menu entry | `config/mainmenu/config/PluginManagerClickConfig.kt` | Opens `PluginListDialog`. |
| List dialog | `dialog/PluginListDialog.kt` | Fetches `VsLinks.FEED_PLUGINS` (`raw.githubusercontent.com/.../remote/plugins/main.json`). |
| Schema | `plugin/PluginManifest.kt` | Envelope `{code,data,home,ip,versionName,serviceName,msg}`. Entries have `id,name,version,description,category,entryPoint,args,configSchema,docsUrl`. No hashes, signatures, or schema version. |
| Registry | `plugin/PluginRegistry.kt`, `PluginState`, `PluginRecord` | Register → acknowledge authorized use → enable. A plugin is DISABLED by default. |
| Settings | `plugin/PluginConfigStore.kt` | EncryptedSharedPreferences for SECRET fields. |
| Audit | `plugin/PluginLogger.kt` | Append-only JSONL. |
| Launch | `plugin/PluginProcessLauncher.kt` | Builds the shell command only. Refuses if not ENABLED or if the entry point is missing. |
| Feed | `remote/plugins/main.json` | Empty `data: []`. |
| Hak5 | `hak5/*` | `Hak5ActionClassifier` + `Hak5ActionConfirm` + `Hak5AuditLog` form the confirm-before-destructive pattern. We reuse it. |
| Legacy intents | `utermux_windows/qemu/RunWindowActivity.java` | Sends `com.utermux.action.vnc` with extra `utermux_as`. `VsInstallActivity` installs the bundled asset `apk/utermux_file_plug.ip` (to be removed; VictorFiles replaces it, §13). |

There is **no** code loader (no `DexClassLoader`), and that stays true: plugins never load code into the VictorSuite process.

Recent history to respect: #62 reverted the application ID change (stay `com.termux`). #66 to #71 are the debrand batches. CLAUDE.md says targetSdk stays 28. Claude Code also commits here, so later PRs should update CLAUDE.md along with the code.

## 2. Goals and non-goals

Goals:
- A small, reviewable way to publish and install add-ons from a catalog Don controls.
- Integrity from a sha256 for every artifact. Authenticity from a signature by Don's offline key. Only signed plugins appear in the official catalog.
- Keep the existing gates (authorized use, the enable step, the audit log) and add a confirmation for destructive actions.

Non-goals:
- In-process code loading, a native plugin ABI, or plugin access to VictorSuite internals.
- Automating offensive tooling. The PLUGINS.md "what it is not" section still applies.
- Any ZeroTermux branding, endpoints, or intent names in new code.

## 3. Plugin types

1. **Package plugin** (`type: "package"`): a `.tar.gz` or `.zip` holding scripts, Termux packages, or module files plus a `plugin.json` manifest. It unpacks to `$PREFIX/var/lib/victor/plugins/<id>/<version>/`, with a `current` symlink. It runs as the Termux user through the existing `PluginProcessLauncher`/`RUN_COMMAND` path. It may declare `apt` dependencies, which are installed **only** after the user confirms the list.
2. **Companion APK** (`type: "apk"`): a separate Android app, for example a VNC viewer or the RootForge handoff. It installs through the system package installer, so Android's own confirmation applies and VictorSuite never installs silently. It talks to VictorSuite only through the declared intents in §7 and the permission `com.termux.victor.permission.PLUGIN`.

## 4. Manifest (`plugin.json`, shipped inside each artifact)

See the full field table in `PLUGIN_API.md`. Summary:

`manifestVersion`, `id` (reverse-DNS, `[a-z0-9._-]`), `name`, `version` (SemVer), `type`, `description`, `category`, `license` (an SPDX ID, required), `sourceUrl` (required for anything GPL), `authors`, `minAppVersionCode`, `abis`, `entryPoint` and `args` (package plugins), `apkPackageName` and `apkSigningCertSha256` (APKs), `configSchema` (same as the current `PluginConfigField`), `permissions` (§6), `actions` (each with a `destructive` flag), `dependencies.apt`, `uninstall.cleanupPaths`.

During install, the catalog entry and the embedded `plugin.json` must agree on `id`, `version`, `type`, and `license`. If they don't, the install is refused.

## 5. Catalog

- **Hosting:** GitHub Pages from this repo's `docs/`, at `https://victorious93.github.io/VictorSuite/plugins/catalog.json` (Pages is live). Artifacts are GitHub Release assets or files under `docs/plugins/artifacts/`. `remote/plugins/main.json` stays as a legacy feed until Phase 4.
- **Format:** see `docs/plugins/catalog.json`. Top level: `schemaVersion` (an integer, currently 1), `generatedAt`, `catalogId`, `signingKeys[]` (public keys only), and `plugins[]`. Each plugin has a `versions[]` list. Each version has `artifacts[]`, and each artifact has `url`, `sha256` (**required**), `size`, `abi`, and optionally `signature`.
- **schemaVersion rules:** the app refuses any catalog with a `schemaVersion` higher than it supports and shows "Update VictorSuite to read this catalog." Unknown fields are ignored. A breaking change bumps the major integer.
- **Signatures (required for the official catalog):** Ed25519 (minisign-compatible) over the raw artifact bytes, plus an optional detached signature over `catalog.json` itself (`catalog.json.minisig`).
  - **Don holds the private key** offline, on his own machine or in a password manager. Don signs by hand. It is **never committed**, **never stored in CI** (no CI secret, no auto-signing), and never placed on the box.
  - **Backup and rotation:** Don keeps an encrypted offline backup of the private key (e.g. password manager plus an offline copy). If the key is lost or compromised, Don generates a new keypair, commits the new public key to `docs/plugins/keys/`, pins it in the next app release (old key kept pinned for one release during rotation only if not compromised; a compromised key is unpinned immediately), and re-signs the current catalog artifacts.
  - Only the **public** key is committed, in `docs/plugins/keys/` and also pinned in the app at build time. A catalog cannot add a trusted key by itself: a key listed in `signingKeys` is trusted only if it matches a pinned key.
- **Verification order:** download → check size → **sha256 must match, or abort** → if a signature is present, verify it against the pinned keys, and a bad signature means abort (a bad signature is worse than none) → if there is no signature, mark the plugin **unsigned**. Unsigned artifacts are rejected from the official catalog; they can only be sideloaded or come from a third-party catalog.
- **Third-party catalogs:** users may add other catalog URLs after a warning dialog. Keys listed by a third-party catalog are **never** trusted as official; its plugins are always treated as unsigned/third-party and get the full unsigned warning and badge.
- HTTPS only. No redirects to a different host unless the host is on an allowlist (`github.com`, `objects.githubusercontent.com`, `victorious93.github.io`).

## 6. Permissions and approval gates

Declared permissions, each shown to the user before install:

| Permission | Meaning |
|---|---|
| `exec` | Run its entry point as the Termux user. Every package plugin has this. |
| `network` | Says it makes network calls. Informational only: Termux can't enforce it. |
| `storage.shared` | Reads or writes `~/storage` (shared storage). |
| `apt` | Installs the listed packages. |
| `run_command` | Sends `RUN_COMMAND` to VictorSuite (companion APKs). |
| `root` | Wants `su`. Allowed. Shows a red banner and needs a confirmation on **every** root use; no "don't ask again". |
| `device.<x>` | Hardware companions such as Hak5. These follow the Hak5 gate. |

Gates, all enforced in code:
1. Register → acknowledge authorized use → enable. This is the existing gate, unchanged.
2. **Destructive confirmation.** Any `action` with `destructive: true`, any `root` use, uninstall with data, any `apt remove`, and any `cleanupPaths` outside the plugin's own directory need an explicit confirmation dialog every time. The dialog names the action and its exact command, and needs a long-press or typed confirmation. This reuses `Hak5ActionClassifier`/`Hak5ActionConfirm` (generalised as `ActionConfirm` in Phase 2). Nothing destructive runs from intents, the catalog, or an update without that dialog. There is no "don't ask again" for destructive actions.
3. Updates never widen permissions silently. A new permission brings the plugin back to the approval screen.
4. Every step is written to the audit log (`PluginLogger`).

## 7. Intents (replacing `com.utermux.*`)

| New | Replaces | Purpose |
|---|---|---|
| `com.termux.victor.action.VNC` | `com.utermux.action.vnc` | Open a VNC viewer companion. The extra `victor_as` replaces `utermux_as`. |
| `com.termux.victor.action.PLUGIN_INSTALLED` / `PLUGIN_REMOVED` | — | Broadcasts. Receivers need `com.termux.victor.permission.PLUGIN`. |
| `com.termux.victor.action.PLUGIN_LAUNCH` | — | Companion asks VictorSuite to launch an enabled plugin. Still goes through the gates. |
| `com.termux.victor.action.PLUGIN_RESULT` | — | Result callback. |
| `com.termux.RUN_COMMAND` | unchanged | Upstream contract, kept as is. |

Aliases: for **two releases** (or until Phase 4), VictorSuite sends both the new and the old action and reads both extras, preferring the new one. Each use of a legacy alias is logged once per session. The new permission is `signature|` with a user-approved fallback (decided default, §13). Using `com.termux.victor.*` keeps the names tied to the package.

## 8. Install, update, uninstall

**Install:** Catalog list → details screen (name, version, licence, source link, size, permissions, signed/unsigned badge) → user taps Install → download and verify (§5) → **unsigned warning** if needed → permission review → unpack to a staging directory → validate `plugin.json` → atomically move it into place → register as DISABLED → authorized-use acknowledgement → enable. For an APK, the system installer runs after verification. Afterwards, VictorSuite checks that the installed package's signing certificate matches `apkSigningCertSha256`. If it doesn't, the plugin stays disabled and the app warns the user.

**Unsigned warning UI:** a full-screen dialog with an amber or red header that says "Unsigned plugin." The body says: "This plugin's file matches the catalog checksum, but nobody has signed it. VictorSuite can't confirm who made it. Only install it if you trust the source: <sourceUrl>." The default button is **Cancel**. Install needs a checkbox ("I understand the risk") before the button becomes active. The plugin gets a permanent "Unsigned" badge in the list. Sideloading is the only way to install an unsigned plugin outside third-party catalogs; a sideloaded file always gets this full-screen warning, plus "checksum not verified" if it has no catalog entry.

**Update:** On catalog refresh, or manually (no automatic background installs in v1), the app compares SemVer. The update screen shows the changelog and any permission changes. The update goes through the same verification. The previous version is kept for one rollback. The plugin stays enabled unless its permissions widened.

**Uninstall:** A confirmation dialog lists exactly what will be deleted: the plugin directory and, optionally, `cleanupPaths` and its config or secrets. "Also delete settings and data" is unchecked by default. `apt` dependencies are **not** removed automatically. The app offers them as a separate confirmed step. For companion APKs, the system uninstall dialog is opened. Everything is logged.

## 9. Sandboxing limits (be honest)

- Package plugins run with the **same UID** as VictorSuite/Termux. Android gives no isolation between them. A malicious package plugin can read `$HOME`, which includes other plugins' files. `EncryptedSharedPreferences` protects secrets at rest, but not from code running as that UID. The `network` permission cannot be enforced.
- What we can do: no in-process code loading, our own gates in every VictorSuite-initiated path, a launch environment limited to that plugin's config, `cwd` set to the plugin directory, a timeout and a kill button, the audit log, and integrity and signature checks.
- Companion APKs do get Android's app sandbox (a separate UID). They are limited by the signature or approved permission.
- Optional later: running a plugin inside a proot distro (see DISTRO_INSTALLERS.md) for extra isolation. This is not a security boundary.
- The UI and docs must say this plainly: "Plugins can access your Termux files."

## 10. Licensing

- `license` (SPDX) is required. Without it, the plugin isn't listed.
- **Bundled** into the VictorSuite APK or its assets: must be GPL-3.0-compatible (for example GPL-3.0-or-later, GPL-2.0-or-later, LGPL, Apache-2.0, MIT, BSD, MPL-2.0). GPL-2.0-only is not allowed. It needs an entry in THIRD_PARTY_NOTICES.md and source available.
- **Catalog-distributed** package plugins: any OSI or FSF free licence. Anything under the GPL family needs `sourceUrl` that points to the exact source for that version. Proprietary or unknown licences are not listed in the official catalog (decided default, §13).
- **Companion APKs** are separate programs that talk over intents, so they can use other licences. If one includes VictorSuite code, it must be GPL-3.0.
- `utermux_file_plug.ip`: dropped. VictorFiles replaces it; Phase 4 removes the asset and all references.

## 11. Migration of existing entries

- `remote/plugins/main.json` has `data: []`, so there are no plugin entries to convert. In Phase 1, a converter maps the old fields (`id,name,version,description,category,entryPoint,args,configSchema,docsUrl`) to the new manifest with `type: package`. The `license` and `sha256` fields must be added, so the converter produces a draft that needs review.
- The `PluginRegistry` records that are already installed keep their state. A one-time migration marks them "legacy, unverified" with the unsigned badge.
- `com.utermux.action.vnc` and `utermux_as` get aliases, as in §7.
- `PluginListDialog` reads the new catalog first and falls back to the legacy feed. In Phase 4 the legacy feed is retired: it becomes a stub `msg` pointing to the new URL.
- `VsLinks` gets `PLUGIN_CATALOG = "https://victorious93.github.io/VictorSuite/plugins/catalog.json"`.
- Hak5 stays a built-in module. Later it can declare `device.hak5` through the same gate classes.

## 12. Phased PR plan

| Phase | PR | Contents |
|---|---|---|
| 0 | this PR | Design docs, example catalog. No code. |
| 1 | `feat/plugin-catalog-model` | Catalog/manifest data classes, `schemaVersion` handling, sha256 verification, JVM unit tests, `VsLinks.PLUGIN_CATALOG`. No UI changes. |
| 2 | `feat/plugin-install-flow` | Download, staging, install, update, uninstall, unsigned warning UI, generalised `ActionConfirm`, permission review screen. |
| 3 | `feat/plugin-signatures` | Ed25519 verification, pinned public key, `docs/plugins/keys/`, `scripts/sign-plugin` for Don to run locally (it reads the key from his machine and never from the repo). |
| 4 | `feat/plugin-intents` | `com.termux.victor.action.*`, permission, legacy aliases, migrate RunWindowActivity, **remove `apk/utermux_file_plug.ip` and all references** (`VsInstallActivity`, docs), pointing users to VictorFiles. |
| 5 | `feat/companion-apk` | APK type, signing-cert pinning, first companion. |
| 6 | cleanup | Remove the legacy feed and the aliases after two releases. Update PLUGINS.md and CLAUDE.md. |

Every PR: CI build plus unit tests, a draft until Don reviews it, no secrets.

## 13. Decisions (Oct 27, 2026)

1. **Signing key custody:** offline only. Don signs by hand; the private key is never in CI or the repo. Backup and rotation as in §5.
2. **Unsigned plugins:** sideload only (or third-party catalogs, see 3). Never in the official catalog. Sideloads always show the full-screen unsigned warning.
3. **Third-party catalogs:** allowed, with a warning when added. Their keys are never trusted as official; their plugins are treated as unsigned/third-party.
4. **Licence policy (decided default):** free (OSI/FSF) licences only in the official catalog; GPL-family needs `sourceUrl`.
5. **Companion permission level (decided default):** `signature|` with a user-approved runtime grant for third-party companions.
6. **Alias lifetime (decided default):** keep `com.utermux.*` aliases for two releases, removed in Phase 6.
7. **`utermux_file_plug.ip`:** dropped; VictorFiles replaces it. Removal is in Phase 4.
8. **Updates (decided default):** manual only in v1; no background installs.
9. **Root plugins:** allowed, with a confirmation on every root use. No "don't ask again".
10. **Artifact hosting (decided default):** GitHub Release assets.
