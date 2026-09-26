# remote

This directory is the app's module/expansion-pack and online-script manifest
source, served via `raw.githubusercontent.com` instead of a third-party
server. Anything the app fetches at runtime through `HTTPIP.IP` resolves to
a file under this directory, so all remotely-fetched content is reviewed and
version-controlled the same way as the rest of the codebase.

- `repository/main.json` — module/expansion-pack manifest (`ZDYDataBean` schema)
- `on_line_sh/main.json` — online shell-script manifest (`OnLineShBean` schema)
- `plugins/main.json` — plugin manifest (`PluginManifest` schema, see `/PLUGINS.md`)

All currently ship empty (`"data": []`) — add entries here, via a reviewed
PR, when there's real content to publish.
