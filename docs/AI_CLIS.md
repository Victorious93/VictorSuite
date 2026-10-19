# Running AI CLIs in VictorSuite

`scripts/ai-cli-setup.sh` installs six AI command-line tools so they can be
run from the VictorSuite terminal:

| Tool | Command | Where it runs | Package |
|---|---|---|---|
| pi | `pi` | Natively in Termux | npm `@earendil-works/pi-coding-agent` |
| GitHub Copilot CLI | `copilot` | Ubuntu container (proot-distro) | npm `@github/copilot` |
| OpenAI Codex CLI | `codex` | Ubuntu container, or natively via `--codex-from-tur` | npm `@openai/codex` / TUR `codex` |
| Claude Code | `claude` | Ubuntu container | npm `@anthropic-ai/claude-code` |
| Ollama | `ollama` | Natively in Termux | Termux package `ollama` |
| Hugging Face CLI | `hf` | Ubuntu container | PyPI `huggingface_hub` |

## Quick start

In the app, open the menu and tap **AI CLIs** in the ZT features section.
That copies the bundled script to `~/ai-cli-setup.sh` and runs it in the
terminal. You can also run it from the VictorSuite shell yourself (as the
normal user, not root):

```sh
bash scripts/ai-cli-setup.sh                 # all six
bash scripts/ai-cli-setup.sh --only pi,hf    # a subset
bash scripts/ai-cli-setup.sh --help
```

The script runs `pkg upgrade` first, because the app bundles a 2022 bootstrap.
It then prints each tool's `--version` and exits non-zero if any tool failed.
You can run it again safely. It reuses the existing container, and it never
overwrites a `$PREFIX/bin` file that it did not create itself.

## Why some tools need a container

- **Copilot**: the npm launcher chooses a per-platform binary package
  (`@github/copilot-linux-arm64`, `-linuxmusl-arm64`, …). Termux's Node
  reports `process.platform === "android"`, so no package matches. Android
  support is requested upstream in
  [github/copilot-cli#1257](https://github.com/github/copilot-cli/issues/1257),
  which is still open.
- **Codex**: upstream closed Android support as not planned
  ([openai/codex#17787](https://github.com/openai/codex/issues/17787)).
  termux-packages declined to add it
  ([termux/termux-packages#27014](https://github.com/termux/termux-packages/issues/27014)).
  TUR does build it from source, but its version lags upstream (0.122.0 in TUR
  vs 0.157.1 on npm when this was written).
- **Claude Code**: the npm package installs a per-platform native binary
  (`@anthropic-ai/claude-code-linux-arm64`, `-linux-x64`, the `-musl` variants,
  macOS and Windows). There is no Android or 32-bit ARM build, and it needs
  Node 22 or newer, which the container provides. Checked against the npm
  registry metadata for version 2.1.303.
- **Ollama** is the exception: termux-packages builds it (`packages/ollama`),
  but only for aarch64 and x86_64, so it is installed natively with `pkg` and
  needs no container. It is a server plus CLI: start it with `ollama serve &`,
  then `ollama pull <model>`. Models are large; a phone's RAM and storage limit
  which ones are usable, and the Termux build's GPU (Vulkan) support is untested
  here.
- **hf**: on aarch64, `huggingface_hub` requires `hf-xet`. That package ships
  manylinux/musllinux wheels but no Android wheel.

The container is `ubuntu:24.04`, installed with `proot-distro` under the name
`ai-cli` (change it with `--container`). Ubuntu's own Node.js is v18, and
Copilot's launcher needs a newer version. For that reason the script installs
the official nodejs.org v22 build and checks it against `SHASUMS256.txt`.

The wrappers in `$PREFIX/bin` run each tool with `proot-distro login`, using
your current directory as the working directory. In proot-distro's default
mode the Termux app directory (including `$HOME`) and `/sdcard` are bound into
the container, so projects in either place are visible. The wrappers forward
only these variables, and only when they are set: `TERM`, `COLORTERM`,
`GH_TOKEN`, `GITHUB_TOKEN`, `OPENAI_API_KEY` and `HF_TOKEN`.

## Signing in

| Tool | Interactive | Token / env var |
|---|---|---|
| claude | run `claude` and sign in | `ANTHROPIC_API_KEY` (also `ANTHROPIC_BASE_URL` is forwarded) |
| ollama | none: `ollama serve &`, then `ollama pull <model>` | none |
| copilot | run `copilot`, then `/login` | `GH_TOKEN` or `GITHUB_TOKEN`: a fine-grained PAT with the "Copilot Requests" permission. Requires an active Copilot subscription. |
| codex | run `codex`, sign in with ChatGPT | `OPENAI_API_KEY` |
| hf | `hf auth login` | `HF_TOKEN` |
| pi | run `pi` and follow its provider setup | see pi's quickstart |

Credentials saved by copilot, codex and hf go into the container's `/root`,
not into your Termux home.

## Status

The script has been checked with `shellcheck` and dry-run against stubbed
`pkg`/`proot-distro` commands. **It has not yet been run on a real device.**
Things to confirm on the first real run:

- Copilot's, Codex's and Claude Code's TUIs render and accept input correctly
  under proot, and Claude Code's sign-in completes from inside the container.
- `pkg install ollama` finds the package for your CPU, `ollama serve` starts,
  and a small model can be pulled and run. (Version and package index were not
  checked from the build sandbox.)
- Copilot's `/login` device flow and Codex's ChatGPT sign-in complete from
  inside the container.
- `pkg upgrade` works cleanly from VictorSuite's old bootstrap.
- proot-distro is version 5 or later. Older versions do not accept the
  `ubuntu:24.04` image form, so if you pass `--skip-upgrade`, run
  `pkg upgrade proot-distro` yourself first.
