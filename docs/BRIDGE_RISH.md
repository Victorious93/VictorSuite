# Using Victorious Bridge from the terminal (`rish`)

Victorious Bridge (repo `Victorious93/ShizukuV`, package `ai.victorious.bridge`) is the local privileged-access
service. `rish` is a shell that runs *through* it, so commands run as **ADB shell** or **root**, not as this
terminal app. VictorSuite itself is an ordinary app (`com.termux`); it gains no privilege by installing the Bridge.

**Status: unverified on a device.** The script and its fake-`rish` test run on any machine
(`bash tests/ai-agents/test_bridge_rish_doctor.sh`); no VictorSuite build has been driven against a running
Bridge in this repository's CI.

## Setup

1. In Victorious Bridge: *Status → Use in terminal apps*, export `rish` and `rish_shizuku.dex` into `~/bridge`
   (keep them together; the dex embeds the Bridge's package id, so re-export after a Bridge update that changes it).
2. In the Bridge **Apps** screen, open this terminal app and choose **Legacy full access**. `rish` is a legacy
   client: it can run any command as the service user, which is why the Bridge labels it that way and warns first.
3. Run `scripts/bridge-rish-doctor`. It derives the terminal's package from `$PREFIX` (it does not assume
   `com.termux`), checks the files, makes the dex read-only (Android 14+ refuses a writable dex), and runs a real
   probe. It prints the privilege in use: `root`, `ADB (shell user)`, or `ordinary app (rish not working)`.

## Troubleshooting (what the doctor tells you)

| Symptom | Meaning | Fix |
|---|---|---|
| `Permission denied` | Bridge has not approved this terminal | Apps → this app → Legacy full access |
| `Request timeout` | no answer from the Bridge | start the service; disable battery optimisation for both apps |
| `Server is not running` | service down | start it; *Diagnostics* shows the last start failure and what to do |
| `RISH_APPLICATION_ID` problems | package id missing or not this app | `export RISH_APPLICATION_ID=<this terminal's package>` |
| dex writable | Android 14+ will not load it | the doctor `chmod 400`s it; else copy into private storage |

`rish` prints one line to stderr naming the privilege and your access level. Set `RISH_QUIET=1` to hide it in
scripts that parse stderr.

## Limits (stated, not hidden)

- ADB mode cannot read other apps' private data or change protected settings; root is subject to SELinux/OEM limits.
- Revoking the terminal's access in the Bridge stops the `rish` shell it started. Processes a command detached
  into the background may survive; the Bridge cannot see or stop those.
- `vsphone` keeps its own approval gate for risky actions; the Bridge's activity log does **not** record what a
  legacy full-access client does.
- Installing VictorSuite and upstream Termux together is impossible (same package id); that is unrelated to the
  Bridge, which installs alongside upstream Shizuku because its identity is different.
