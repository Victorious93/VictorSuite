# Writing a VictorSuite plugin (author guide — draft)

Status: draft that goes with `PLUGIN_SYSTEM.md`. The APIs aren't implemented yet.

## 1. Choose a type
- **package**: scripts, binaries, or modules run by Termux. Ship a `.tar.gz` with `plugin.json` at the root.
- **apk**: a separate Android app that talks to VictorSuite through intents.

## 2. `plugin.json`

| Field | Req | Notes |
|---|---|---|
| `manifestVersion` | yes | `1` |
| `id` | yes | Reverse-DNS, lowercase, for example `io.github.you.mytool`. It never changes. |
| `name`, `description` | yes | Plain text. Don't use other projects' branding. |
| `version` | yes | SemVer. |
| `type` | yes | `package` or `apk` |
| `category` | yes | For example `utility`, `network`, `dev`, `device`. |
| `license` | yes | SPDX ID. |
| `sourceUrl` | GPL: yes | The exact source for this version. |
| `authors` | yes | `["Name <email>"]` |
| `minAppVersionCode` | no | VictorSuite `versionCode`. |
| `abis` | no | `["aarch64","arm","x86_64","i686"]` or `["all"]` |
| `entryPoint`, `args` | package | Path relative to the plugin directory. |
| `apkPackageName`, `apkSigningCertSha256` | apk | Used to check the installed app. |
| `configSchema` | no | `[{key,label,type: TEXT|SECRET|BOOLEAN|NUMBER,default}]`. SECRET fields are stored encrypted. |
| `permissions` | yes | Subset of `exec, network, storage.shared, apt, run_command, root, device.*` |
| `actions` | no | `[{id,label,command,destructive}]`. Destructive actions always need a confirmation. |
| `dependencies.apt` | no | Package names. The user confirms them. |
| `uninstall.cleanupPaths` | no | Outside your plugin directory, these need a confirmation. |

## 3. Runtime environment (package)
- `cwd` = `$PREFIX/var/lib/victor/plugins/<id>/current`
- Environment variables: `VICTOR_PLUGIN_ID`, `VICTOR_PLUGIN_VERSION`, `VICTOR_PLUGIN_DIR`, `VICTOR_PLUGIN_DATA` (`~/.local/share/victor-plugins/<id>`), and each config value as `VICTOR_CFG_<KEY>`.
- Exit code 0 means success. stdout and stderr go to the terminal session and the audit log (secrets are redacted).
- Don't self-update, don't run destructive actions without declaring them, and don't write outside your data directory unless you've declared it.

## 4. Intents (apk)
Requires `com.termux.victor.permission.PLUGIN`.
- `com.termux.victor.action.PLUGIN_LAUNCH` with extras `plugin_id` and `action_id`. The user's gates still apply.
- `com.termux.victor.action.PLUGIN_RESULT`, `PLUGIN_INSTALLED`, `PLUGIN_REMOVED`.
- `com.termux.victor.action.VNC` with extra `victor_as`.
- `com.termux.RUN_COMMAND` keeps the upstream contract.
- `com.utermux.*` names are deprecated aliases. Don't use them.

## 5. Publishing
1. Build the artifact and compute `sha256sum`.
2. Open a PR adding your entry to `docs/plugins/catalog.json` (served at https://victorious93.github.io/VictorSuite/plugins/catalog.json).
3. Don may sign it with the project key. Without his signature, the plugin is shown to users as **Unsigned** with a warning.
4. Licensing: anything bundled into VictorSuite must be GPL-3.0-compatible. GPL-family plugins must link their source.
