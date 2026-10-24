#!/usr/bin/env bash
# AI Coding Agents pack: Claude Code, OpenAI Codex CLI, GitHub Copilot CLI, Ollama,
# plus vsphone (phone-control bridge) and vstoken (token minimizer).
#
# Usage: ai-agents-pack.sh [--only LIST] [--ollama local|remote|skip] [--no-keys]
#                          [--skip-upgrade] [--sync-context] [-h|--help]
#   --only LIST        subset of claude,codex,copilot,ollama (default: all four)
#   --ollama MODE      local = Termux `ollama` package; remote = use OLLAMA_HOST only;
#                      skip = no Ollama. Default: ask.
#   --no-keys          do not prompt for API keys (use each tool's own login instead)
#   --sync-context     only refresh the read-only fleet-manifest checkout and exit
#
# Keys are stored ONLY in $HOME/.config/victorsuite/ai-agents/keys/<tool>.env (mode 600).
# They are never printed, logged, or passed to other tools/agents.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CFG="$HOME/.config/victorsuite/ai-agents"
KEYS="$CFG/keys"
FLEET_REPO="${VS_FLEET_MANIFEST_URL:-https://github.com/Victorious93/fleet-manifest.git}"
FLEET_DIR="${VS_FLEET_MANIFEST_DIR:-$HOME/.local/share/victorsuite/fleet-manifest}"
CONTAINER="${VS_AI_CONTAINER:-ai-cli}"
ONLY="claude,codex,copilot,ollama"; OLLAMA_MODE=""; ASK_KEYS=1; SKIP_UPGRADE=0; SYNC_ONLY=0

log() { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

while [ $# -gt 0 ]; do
    case "$1" in
        --only) ONLY="${2:?}"; shift 2 ;;
        --ollama) OLLAMA_MODE="${2:?}"; shift 2 ;;
        --no-keys) ASK_KEYS=0; shift ;;
        --skip-upgrade) SKIP_UPGRADE=1; shift ;;
        --sync-context) SYNC_ONLY=1; shift ;;
        -h|--help) sed -n '2,/^$/{s/^# \{0,1\}//;p}' "$0"; exit 0 ;;
        *) die "unknown option: $1" ;;
    esac
done
wants() { case ",$ONLY," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }
[ -n "${PREFIX:-}" ] || die "run this from a VictorSuite/Termux shell (PREFIX not set)"

sync_context() {
    command -v git >/dev/null 2>&1 || pkg install -y git
    if [ -d "$FLEET_DIR/.git" ]; then
        chmod -R u+w "$FLEET_DIR"
        if ! { git -C "$FLEET_DIR" fetch -q --depth 1 origin && git -C "$FLEET_DIR" reset -q --hard FETCH_HEAD; }; then
            warn "could not refresh $FLEET_DIR (offline?); keeping the old copy"
        fi
    else
        mkdir -p "$(dirname "$FLEET_DIR")"
        git clone -q --depth 1 "$FLEET_REPO" "$FLEET_DIR" || { warn "could not clone $FLEET_REPO; set VS_FLEET_MANIFEST_DIR to a local copy"; return 0; }
    fi
    chmod -R a-w "$FLEET_DIR"   # read-only for agents: they must not edit the shared roadmap
    if [ -f "$FLEET_DIR/ROADMAP.md" ]; then log "Shared context: $FLEET_DIR/ROADMAP.md"; else warn "ROADMAP.md not found in $FLEET_DIR"; fi
}

if [ "$SYNC_ONLY" = 1 ]; then sync_context; exit 0; fi

# --- 1. Ollama mode -------------------------------------------------------
if wants ollama && [ -z "$OLLAMA_MODE" ]; then
    echo "Ollama: [l]ocal on this phone (Termux package, aarch64/x86_64 only), [r]emote host via OLLAMA_HOST, [s]kip?"
    read -r ans; case "$ans" in r*|R*) OLLAMA_MODE=remote ;; s*|S*) OLLAMA_MODE=skip ;; *) OLLAMA_MODE=local ;; esac
fi
CLI_ONLY=""
for t in claude codex copilot; do wants "$t" && CLI_ONLY="$CLI_ONLY,$t"; done
wants ollama && [ "$OLLAMA_MODE" = local ] && CLI_ONLY="$CLI_ONLY,ollama"
CLI_ONLY="${CLI_ONLY#,}"

# --- 2. install the CLIs (reuses the verified ai-cli-setup.sh) ------------
if [ -n "$CLI_ONLY" ]; then
    extra=(); [ "$SKIP_UPGRADE" = 1 ] && extra+=(--skip-upgrade)
    bash "$HERE/ai-cli-setup.sh" --only "$CLI_ONLY" --container "$CONTAINER" "${extra[@]}" \
        || warn "some tools failed to install; see the output above"
fi
pkg install -y python git >/dev/null 2>&1 || warn "could not install python/git (vstoken needs python)"

# --- 3. helpers into $PREFIX/bin -------------------------------------------
for f in vsphone vstoken; do install -m 755 "$HERE/$f" "$PREFIX/bin/$f"; done
mkdir -p "$CFG"; chmod 700 "$CFG"
[ -f "$CFG/vstoken.json" ] || install -m 600 "$HERE/vstoken.example.json" "$CFG/vstoken.json"
log "Installed vsphone and vstoken; router config: $CFG/vstoken.json (router is OFF until you enable it)"

# --- 4. keys (local only, mode 600) ----------------------------------------
store_key() { # tool VAR prompt
    local tool="$1" var="$2" prompt="$3" val=""
    printf '%s (leave empty to use %s'"'"'s own login instead): ' "$prompt" "$tool" > /dev/tty
    IFS= read -rs val < /dev/tty || val=""; echo > /dev/tty
    [ -n "$val" ] || return 0
    mkdir -p "$KEYS"; chmod 700 "$KEYS"
    ( umask 077; printf '%s=%q\n' "$var" "$val" > "$KEYS/$tool.env" )
    chmod 600 "$KEYS/$tool.env"; unset val
    log "Saved $var for $tool to $KEYS/$tool.env (mode 600)"
}
if [ "$ASK_KEYS" = 1 ] && [ -t 0 ]; then
    wants claude && store_key claude ANTHROPIC_API_KEY "Anthropic API key for Claude Code"
    wants codex && store_key codex OPENAI_API_KEY "OpenAI API key for Codex"
    wants copilot && store_key copilot GH_TOKEN "GitHub fine-grained token with 'Copilot Requests'"
fi
if wants ollama && [ "$OLLAMA_MODE" = remote ]; then
    printf 'Remote Ollama host (e.g. http://192.168.1.20:11434): ' > /dev/tty; IFS= read -r host < /dev/tty || host=""
    if [ -n "$host" ]; then
        ( umask 077; printf 'OLLAMA_HOST=%q\n' "$host" > "$CFG/ollama.env" )
        python3 - "$CFG/vstoken.json" "$host" <<'PY'
import json,sys; p,h=sys.argv[1:]; c=json.load(open(p)); c.setdefault("tiers",{}).setdefault("trivial",{})["host"]=h; json.dump(c,open(p,"w"),indent=2)
PY
        log "Saved OLLAMA_HOST to $CFG/ollama.env; vstoken's trivial tier now uses it"
    fi
fi

# --- 5. shared context + short instruction files ---------------------------
sync_context
ROADMAP="$FLEET_DIR/ROADMAP.md"
BODY="# VictorSuite agent rules (short by design)
- Shared fleet context: $ROADMAP (read-only; status/planning, not install truth). Read it only when the task concerns the fleet.
- Phone control: use \`vsphone\`. Risky actions need Don's typed confirmation; never try to bypass the gate.
- Before pasting long logs into the conversation, pipe them through \`vstoken trunc\`.
- Never print, log or commit secrets. Keys live in ~/.config/victorsuite/ai-agents/keys (mode 600).
- Be terse."
write_ctx() { # path  (inside container rootfs or $HOME)
    mkdir -p "$(dirname "$1")"
    if [ -f "$1" ] && ! grep -q 'VictorSuite agent rules' "$1"; then warn "$1 exists; not overwriting"; return; fi
    printf '%s\n' "$BODY" > "$1"; log "Wrote $1"
}
ROOTFS="$PREFIX/var/lib/proot-distro/installed-rootfs/$CONTAINER/root"
[ -d "$ROOTFS" ] || ROOTFS="$HOME"
wants claude && write_ctx "$ROOTFS/.claude/CLAUDE.md"
wants codex && write_ctx "$ROOTFS/.codex/AGENTS.md"
wants copilot && write_ctx "$ROOTFS/.copilot/copilot-instructions.md"
write_ctx "$CFG/AGENTS.md"   # generic copy; Ollama/vstoken prepend it via `vstoken context`

echo
log "Done. Try: claude | codex | copilot | vstoken context \"<task>\" --stats | vsphone backend"
