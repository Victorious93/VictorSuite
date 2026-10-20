# AI Coding Agents pack

Menu: **ZT features → AI Coding Agents**. The button copies `ai-agents-pack.sh`, `ai-cli-setup.sh`,
`vsphone`, `vstoken` and `vstoken.example.json` to `~/.victorsuite-ai-agents/` and runs the pack
in the terminal, the same way **AI CLIs** and the **Linux Login** distro buttons work
(see [DISTRO_INSTALLERS.md](DISTRO_INSTALLERS.md), [AI_CLIS.md](AI_CLIS.md)).

```sh
bash ~/.victorsuite-ai-agents/ai-agents-pack.sh                 # all four agents
bash ~/.victorsuite-ai-agents/ai-agents-pack.sh --only claude,ollama --ollama remote
bash ~/.victorsuite-ai-agents/ai-agents-pack.sh --sync-context  # refresh ROADMAP.md only
```

Nothing proprietary is bundled in the APK. Every tool is downloaded from its official source at
install time. Paths use `$PREFIX` / `$HOME` (no hardcoded `com.termux` paths).

Confidence labels: **[Certain]** = checked directly against the linked source on 2026-10-20.
**[Likely]** = documented, but I did not exercise that exact detail. **[Guessing]** = inferred and unverified.

## Status: what was tested

| Item | Status |
|---|---|
| `shellcheck` on `ai-agents-pack.sh`, `ai-cli-setup.sh`, `vsphone`, tests | Passed locally (x86_64 Linux) |
| `vsphone` classifier + gate (`tests/ai-agents/test_vsphone_gate.sh`, 38 classify cases + refusal/audit checks) | Passed locally |
| `vstoken` unit tests (`tests/ai-agents/test_vstoken.py`, 12 tests) | Passed locally, with and without tiktoken |
| `:app:assembleDebug`, `:app:testDebugUnitTest` | See the PR description |
| Install on a real aarch64 phone, agent logins, Ollama on device, root/Shizuku backends | **Untested on device** |

## 1. Install method per tool

| Tool | Method | License | Confidence |
|---|---|---|---|
| Claude Code | npm `@anthropic-ai/claude-code` (2.1.304, `engines.node >=22`) inside the proot Ubuntu container `ai-cli` | Proprietary ("SEE LICENSE IN README.md"; Anthropic commercial terms). Downloaded at install; not bundled | [Certain] package/version/engines: [npm](https://www.npmjs.com/package/@anthropic-ai/claude-code). [Likely] no Android build: only linux/darwin/win native packages exist (see AI_CLIS.md) |
| OpenAI Codex CLI | npm `@openai/codex` (0.163.0) in the container. Optional native TUR build via `ai-cli-setup.sh --codex-from-tur` | Apache-2.0 | [Certain] [npm](https://www.npmjs.com/package/@openai/codex). [Certain] upstream closed Android as not planned: [openai/codex#17787](https://github.com/openai/codex/issues/17787) |
| GitHub Copilot CLI | npm `@github/copilot` (1.0.100) in the container. This is the current official CLI. The old `gh copilot` extension repo [github/gh-copilot](https://github.com/github/gh-copilot) is **archived** | Proprietary (GitHub, "SEE LICENSE IN LICENSE.md"); needs a Copilot subscription | [Certain] npm metadata: optional deps are only `linux/linuxmusl/darwin/win32` x64/arm64, no android. [Certain] gh-copilot archived (GitHub API `archived: true`). [Certain] Android request still open: [github/copilot-cli#1257](https://github.com/github/copilot-cli/issues/1257) |
| Ollama (local) | Termux package `ollama` (termux-packages 0.40.6, excluded on `arm`/`i686`), installed natively with `pkg` | MIT | [Certain] [termux-packages/packages/ollama/build.sh](https://github.com/termux/termux-packages/blob/master/packages/ollama/build.sh). [Guessing] usable speed on a given phone; GPU/Vulkan untested |
| Ollama (remote) | No local install: the pack stores `OLLAMA_HOST` in `~/.config/victorsuite/ai-agents/ollama.env` (mode 600), and vstoken's trivial tier uses it | — | [Certain] `OLLAMA_HOST` and `/api/generate` are Ollama's documented client env var and API ([docs](https://github.com/ollama/ollama/blob/main/docs/api.md)) |
| vsphone, vstoken | Shell / Python scripts in this repo, GPLv3 like VictorSuite | GPLv3 | — |

**Known Termux/Android issues**

- bionic vs glibc: Claude Code, Codex (npm) and Copilot ship glibc/musl binaries only, so they run in proot Ubuntu. [Certain] (npm metadata)
- Node in the container: Ubuntu 24.04's apt Node is 18. The installer fetches nodejs.org v22 and checks it against SHASUMS256. [Certain] (see AI_CLIS.md)
- ripgrep: Claude Code and Codex use `rg` for search. In the glibc container the vendored or apt `rg` works. Natively on Termux, `pkg install ripgrep` exists. [Likely]
- proot overhead: file-heavy agent operations are slower under proot. [Likely]
- Native modules: `npm install` in Termux itself fails for packages with prebuilt glibc addons. That is why these agents are not installed natively. [Likely]

## 2. Keys and logins (local only)

- The pack asks for each key **once**, using a hidden prompt read from `/dev/tty`. It writes the key to
  `~/.config/victorsuite/ai-agents/keys/<tool>.env` with umask 077, mode 600, in a mode-700 directory.
  Leave the prompt empty to use the tool's own login flow instead (`claude` sign-in, `codex` ChatGPT
  login, `copilot` `/login`).
- The `$PREFIX/bin/<tool>` wrappers source that file **only if its mode is exactly 600** and forward
  only that tool's variable into the container. Keys are never echoed, logged, written to the repo,
  or shared between agents (Claude's wrapper never sees `OPENAI_API_KEY`, and so on).
- Tool-managed logins live in the container's `/root` (see AI_CLIS.md).

## 3. Shared context (fleet ROADMAP.md)

- **Finding:** [`Victorious93/fleet-manifest`](https://github.com/Victorious93/fleet-manifest) exists
  (public, branch `main`) and contains `ROADMAP.md` ("Victorious Fleet — APK / Status Roadmap",
  as of Oct 20, 2026). [Certain]
- The pack runs a shallow `git clone` into `$VS_FLEET_MANIFEST_DIR`
  (default `~/.local/share/victorsuite/fleet-manifest`) and makes it read-only (`chmod -R a-w`).
  `--sync-context` refreshes it. Override the source with `VS_FLEET_MANIFEST_URL`.
- The pack writes a ~120-token instruction file that points at that path:
  container `/root/.claude/CLAUDE.md` (Claude Code user memory), `/root/.codex/AGENTS.md` (Codex global
  instructions), `/root/.copilot/copilot-instructions.md`, and `~/.config/victorsuite/ai-agents/AGENTS.md`.
  It never overwrites a file it did not write.
  [Likely] Claude and Codex read those global files ([Claude memory](https://docs.anthropic.com/en/docs/claude-code/memory),
  [Codex AGENTS.md](https://github.com/openai/codex/blob/main/docs/config.md)).
  [Guessing] Copilot CLI reads `~/.copilot/copilot-instructions.md`. If it doesn't, put the same text in
  the repo's `.github/copilot-instructions.md`.
- The ROADMAP's own "auto-merge when CI green" policy does **not** apply to this pack's PRs. Don asked
  for manual review.

## 4. vsphone: phone-control bridge

`vsphone pm|am|settings|input|screencap|uidump|pull|push|ls|shell|classify|backend|log`.

- **Backend:** root if `su -c id -u` prints 0, otherwise Shizuku's `rish`, otherwise an error.
  Force one with `VSPHONE_BACKEND=root|shizuku`. Shizuku runs as the `shell` user, so it cannot read
  other apps' private data. Root can.
- **Approval gate (never auto-approved):** a command classified RISKY requires typing a random
  `CONFIRM-xxxx` word read from `/dev/tty`. It is **refused** (exit 3) when stdin or stdout is not a
  TTY, when `SSH_CONNECTION`/`SSH_CLIENT`/`SSH_TTY` is set, or when `VS_REMOTE_TRIGGER`/`VSPHONE_REMOTE`
  is set. This matches the envelope rule that `shell.exec` is destructive and remote triggers are never
  auto-approved ([REMOTE_CONTROL_ENVELOPE.md](REMOTE_CONTROL_ENVELOPE.md)). No flag bypasses it.
  An agent's tool call has no TTY, so risky actions from agents are always refused. The human runs them.
- **RISKY:** `shell` (all raw shell), wipe/flash/dd/format/factory-reset/reboot, anything touching
  `/dev/block`, every `pm uninstall|clear|disable*|hide|suspend|revoke|reset-permissions|…` (conservative:
  not only system apps), `pm install -d`/bypass flags, `am broadcast` of MASTER_CLEAR/FACTORY_RESET/
  REBOOT/SHUTDOWN, `settings reset`, and `settings put|delete` of security keys (adb, verifier,
  unknown sources, lockscreen, accessibility, IME, private DNS, proxy, …). Pushes outside `/sdcard` and
  unknown commands are RISKY too.
- **Audit log:** `~/.local/state/victorsuite/vsphone-audit.log` (mode 600) records time, backend,
  verdict, outcome and arguments. `input text` payloads and `settings put` values are redacted, and
  token-like strings are masked.

## 5. vstoken: token minimizer and router

| Idea | Implemented? | Notes |
|---|---|---|
| Compact context builder | Yes: `vstoken context TASK` | `git ls-files` (honours `.gitignore`) + `.aiignore` + default denylist (keys/keystores/binaries). Ranks files by keyword hits, adds a filtered ROADMAP, and stays under `--max-tokens`, falling back to keyword excerpts |
| Terse prompts / short instruction files | Yes | 121-token AGENTS/CLAUDE file (measured below) instead of a long one |
| Prompt caching | Advice only; the vendor CLIs control their own requests | See below |
| Router (opt-in) | Yes: `vstoken route` | Off until `"router_enabled": true`, or a manual `--tier`/`--model`. Prints and logs every decision |
| Output truncation / log summary | Yes: `vstoken trunc` | Collapses repeats, keeps head, tail and error/warning lines, optional token cap |
| Token and cost tracking + daily budget | Yes: `vstoken track/report` | Prices come only from config (`prices_per_mtok`). Unpriced models are reported as such, never guessed |

**Prompt caching**

- [Certain] Anthropic: `cache_control: {type: "ephemeral"}`, either top-level (automatic) or on blocks.
  The default TTL is 5 min (1 h optional). Reads cost 0.1× base (0.05× on Opus 5.5 and Sonnet 5.5) and
  5-minute writes cost 1.25×. The minimum cacheable prefix is 512 tokens on the current models.
  [Prompt caching docs](https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching).
  [Likely] Claude Code already applies caching itself, so vstoken does not add it.
- [Certain] OpenAI: caching is on by default for supported models. GPT-5.6+ also has explicit
  `prompt_cache_breakpoint`. The minimum is 1,024 tokens, writes cost 1.25× and reads 0.1× (0.05× on gpt-6.1-sol).
  [Prompt caching guide](https://platform.openai.com/docs/guides/prompt-caching).
- [Likely] Ollama keeps the loaded model's KV state between calls with the same prefix (`keep_alive`).
  There is no billing impact.
- What vstoken does about it: it puts stable content first (task header, then filtered ROADMAP, then
  files) and keeps the instruction file byte-stable, so the vendor caches can hit.
  [Guessing] how much that saves in practice; it was not measured (no paid calls were made).

**Router tiers (config-driven, `~/.config/victorsuite/ai-agents/vstoken.json`)**

- Classifier: `hard` = refactor/debug/architecture/design/protocol/… or 3+ files named. `trivial` =
  rename/format/typo/short Q&A. Otherwise `routine`. With `--check "CMD"` (tests, lint), a failed check
  escalates one tier (trivial → routine → hard) and logs `escalated: check failed on <tier>`.
  `--tier`/`--model` override the choice and disable escalation.
- Logged to `~/.local/state/victorsuite/vstoken-routes.jsonl` (mode 600): tier, reasons, provider,
  model, attempt, token counts and check result. **No prompt text, no keys.**
- Hosted tiers call the vendor CLI non-interactively (`claude -p`, `codex exec`, `copilot -p`), so each
  CLI keeps its own auth. [Likely] for `claude -p --model` and `codex exec -m`. [Guessing] for
  `copilot -p --model`; check `copilot --help` on the device.
- The router never runs phone actions. Risky actions still go through `vsphone`'s gate, whatever tier
  produced them.
- Example ids in `vstoken.example.json`, checked 2026-10-20. **They will go stale, so edit them.**
  - [Certain] `claude-sonnet-5-5` ($2/$10 per MTok) and `claude-opus-5-5` ($4/$20), from the
    [Anthropic models overview](https://docs.anthropic.com/en/docs/about-claude/models/overview).
  - [Certain] `gpt-6.1-sol` ($2/$10) and `gpt-6-astra` ($10/$50), from [OpenAI models](https://platform.openai.com/docs/models).
    The cheaper "GPT-6 Luna" model's id was not shown on that page, so it is not listed.
  - [Certain] the `qwen2.5-coder:1.5b` tag exists in the [Ollama library](https://ollama.com/library/qwen2.5-coder/tags).
    [Guessing] it is good enough for "trivial" tasks on a phone.

## 6. Measured savings (real tokenizer, no paid calls)

Method: `tiktoken` `o200k_base` (OpenAI's tokenizer; Anthropic's tokenizer differs, so treat these as
relative numbers). Sample task: *"Add the AiAgentsPackClickConfig menu entry to MainMenuConfig next to
AiCliSetupClickConfig"* on this branch. Script: run `vstoken context … --stats`. The numbers:

| Context strategy | Tokens |
|---|---|
| Whole repo, every tracked text file | 2,147,536 |
| Naive "dump the package dir" (`config/mainmenu/`, 77 files) | 40,732 |
| Repo `CLAUDE.md` (2,448) + full ROADMAP.md (3,291) as standing context | 5,739 |
| Pack's short instruction file | 121 |
| ROADMAP filtered for this task | 976 |
| `vstoken context --max-tokens 6000` (included `AiAgentsPackClickConfig.java`, `AiCliSetupClickConfig.java`, `MainMenuConfig.java`, +2 less relevant) | 5,996 |
| `vstoken context --max-tokens 3000` | 2,996, **but it dropped `MainMenuConfig.java`, the file being edited**. Too tight for this task |

So for this task, 6,000 tokens is 85% fewer than the naive package dump (40,732 → 5,996) and still
contains the files that matter. Standing instructions went from 5,739 to 121 + 976 tokens.
The figure for log truncation on the real Gradle build log is in the PR description.
Not measured: actual billed tokens or cache hits (no API key or Ollama was available in the build sandbox).

## 7. Limits

- Not run on a real phone. Container TUIs, sign-in flows and `ollama serve` on-device are unverified (see AI_CLIS.md).
- The keyword ranker is lexical: no embeddings and no call graph. It can pick less relevant files
  (two of five above) and can miss the right one when the budget is tight.
- The classifier is keyword-based. On 12 real VictorSuite PR titles (dry-run, no model calls) it
  over-escalated "Use official logo (README + launcher icon)" to hard because of the `+`.
- vsphone classifies conservatively. It cannot tell system apps from user apps offline, so every
  uninstall/clear needs confirmation.
