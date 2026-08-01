# Plugin framework

`com.termux.zerocore.plugin` is a small, tool-agnostic framework for registering,
gating, and launching external command-line/GUI tools that the user installs and
authorizes themselves. It is infrastructure only: this codebase does not implement,
bundle, download, or automate any specific tool through it, security-related or
otherwise.

## What it is not

- Not an exploit framework, C2, or attack-automation system.
- Not a downloader — nothing in this package fetches or installs a plugin's actual
  executable. `PluginManifestEntry.entryPoint` names a file the user is expected to
  place under the plugin's install directory themselves (via `pkg`/`apt`, manual
  build, or any other means); the framework only looks for it there and refuses to
  run anything that isn't already present on disk.
- Not tool-specific. There are no interfaces or stubs modeled after any particular
  application. A plugin is just: an id, a display name/version/description, a
  category string for grouping in the UI, a path to an entry point, launch args, and
  an optional settings schema.

## Core pieces (this PR)

| File | Responsibility |
|---|---|
| `PluginManifest.kt` | Wire schema for `remote/plugins/main.json` (`PluginManifest` → `PluginManifestEntry` → `PluginConfigField`). |
| `PluginState.kt` | `DISABLED` / `ENABLED`. |
| `PluginRecord.kt` | Persisted bookkeeping for a registered plugin (no secrets). |
| `PluginRegistry.kt` | Register/list/enable/disable/unregister, with the authorized-use gate below. |
| `PluginConfigStore.kt` | Encrypted-at-rest (AndroidX Security `EncryptedSharedPreferences`) storage for plugin-supplied settings — API keys, endpoints, etc. |
| `PluginLogger.kt` | Append-only JSONL audit log of registry and launch events. |
| `PluginProcessLauncher.kt` | Composes the shell command to run an enabled plugin's entry point; runs nothing itself. |

Planned in later PRs (not in this one): package-management UI, a terminal/GUI shell
for browsing and launching registered plugins, and a settings screen backed by
`PluginConfigField`/`PluginConfigStore`.

## The authorized-use gate

Enabling a plugin is a deliberate two-step process, enforced in code, not just by
convention:

```
register(entry)                    -> always DISABLED, never runnable
acknowledgeAuthorizedUse(id)        -> user records they're authorized to use it here
enable(id)                          -> only succeeds if acknowledged first
```

`PluginProcessLauncher.resolveCommand` independently refuses to build a launch
command for anything that isn't in the `ENABLED` state, and separately refuses if
the entry point file doesn't exist on disk yet. Both checks exist so the invariant
holds even if a caller skips the registry's own gate.

Every registration, acknowledgment, enable/disable, and launch is written to
`PluginLogger`'s audit trail.

## Adding a manifest entry

`remote/plugins/main.json` follows the same pattern as `remote/repository/main.json`
and `remote/on_line_sh/main.json` — served via `raw.githubusercontent.com` so it's
reviewed and version-controlled like any other change. An entry looks like:

```json
{
  "id": "example-tool",
  "name": "Example Tool",
  "version": "1.0.0",
  "description": "One-line description of what the user is launching.",
  "category": "utility",
  "entryPoint": "bin/run.sh",
  "args": [],
  "configSchema": [
    { "key": "endpoint", "label": "Server URL", "type": "TEXT", "default": "" },
    { "key": "apiKey", "label": "API Key", "type": "SECRET" }
  ],
  "docsUrl": "https://example.com/docs"
}
```

`entryPoint` is resolved relative to that plugin's install directory at launch time
— add the manifest entry, then separately install the real tool yourself under that
path. The framework never does that step for you.
