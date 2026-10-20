#!/data/data/com.victorious.suite/files/usr/bin/bash
# Install AI coding/model CLIs inside VictorSuite (or any Termux-compatible app).
#
#   pi       - runs natively in Termux (npm, per pi's own Termux guide)
#   copilot  - GitHub Copilot CLI; runs inside a proot-distro Ubuntu container
#   codex    - OpenAI Codex CLI;   runs inside the same Ubuntu container
#   claude   - Claude Code;        runs inside the same Ubuntu container
#   ollama   - Ollama (local LLM server and CLI); native Termux package
#   hf       - Hugging Face CLI;   runs inside the same Ubuntu container
#
# Copilot, Codex and Claude Code ship prebuilt Linux (glibc/musl) binaries
# only; none publishes an Android build, so they cannot run directly on
# Termux's bionic libc. hf depends on hf-xet, which has no Android wheel
# either. Running them in a glibc Ubuntu container avoids that. Thin wrappers
# are written to $PREFIX/bin so `copilot`, `codex`, `claude` and `hf` work from
# the normal Termux prompt. Ollama is a Termux package and needs no container.
# It is built for 64-bit ARM and x86_64 only. Models are large: check free
# storage and RAM before pulling one.
#
# Usage: ai-cli-setup.sh [--only LIST] [--container NAME] [--codex-from-tur]
#                        [--skip-upgrade] [-h|--help]
#   --only LIST        comma-separated subset of: pi,copilot,codex,claude,ollama,hf
#                      (default: all six)
#   --container NAME   proot-distro container name (default: ai-cli)
#   --codex-from-tur   install Codex natively from the Termux User Repository
#                      instead of the container (TUR's build can lag upstream)
#   --skip-upgrade     do not run `pkg upgrade` first

set -euo pipefail

CONTAINER="ai-cli"
UBUNTU_IMAGE="ubuntu:24.04"
NODE_LINE="latest-v22.x"
PI_MIN_NODE="22.19.0"
WRAPPER_MARKER="# managed-by: victorsuite ai-cli-setup"
ONLY="pi,copilot,codex,claude,ollama,hf"
CODEX_FROM_TUR=0
SKIP_UPGRADE=0

log() { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

usage() { sed -n '2,/^$/{s/^# \{0,1\}//;p}' "$0"; }

while [ $# -gt 0 ]; do
    case "$1" in
        --only) [ $# -ge 2 ] || die "--only needs a value"; ONLY="$2"; shift 2 ;;
        --container) [ $# -ge 2 ] || die "--container needs a value"; CONTAINER="$2"; shift 2 ;;
        --codex-from-tur) CODEX_FROM_TUR=1; shift ;;
        --skip-upgrade) SKIP_UPGRADE=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "unknown option: $1 (see --help)" ;;
    esac
done

wants() { case ",$ONLY," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }

for tool in ${ONLY//,/ }; do
    case "$tool" in pi|copilot|codex|claude|ollama|hf) ;; *) die "unknown tool in --only: $tool" ;; esac
done
case "$CONTAINER" in
    ''|[!A-Za-z0-9]*|*[!A-Za-z0-9_.-]*) die "invalid container name: $CONTAINER" ;;
esac

# --- preconditions --------------------------------------------------------

if [ -z "${PREFIX:-}" ] || ! command -v pkg >/dev/null 2>&1; then
    die "run this from a Termux/VictorSuite shell (PREFIX and pkg not found)"
fi
[ "$(id -u)" != "0" ] || die "do not run as root; run it as the normal Termux user"

case "$(uname -m)" in
    aarch64|arm64) NODE_ARCH="arm64" ;;
    x86_64) NODE_ARCH="x64" ;;
    *)
        NODE_ARCH=""
        if [ "$ONLY" != "pi" ]; then
            wants pi || die "$(uname -m) has no build of copilot/codex/claude/ollama/hf; only pi is supported here (--only pi)"
            warn "$(uname -m) has no build of copilot/codex/claude/ollama/hf; only pi will be installed"
            ONLY="pi"
        fi
        ;;
esac

NEED_CONTAINER=0
if wants copilot || wants claude || wants hf || { wants codex && [ "$CODEX_FROM_TUR" = 0 ]; }; then
    NEED_CONTAINER=1
fi

# --- Termux side ----------------------------------------------------------

APT_OPTS=(-y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold)

if [ "$SKIP_UPGRADE" = 0 ]; then
    log "Upgrading Termux packages (the app's bundled bootstrap is old)"
    pkg upgrade "${APT_OPTS[@]}"
fi

TERMUX_PKGS=()
wants pi && TERMUX_PKGS+=(nodejs git)
wants ollama && TERMUX_PKGS+=(ollama)
[ "$NEED_CONTAINER" = 1 ] && TERMUX_PKGS+=(proot-distro)
if [ ${#TERMUX_PKGS[@]} -gt 0 ]; then
    log "Installing Termux packages: ${TERMUX_PKGS[*]}"
    pkg install "${APT_OPTS[@]}" "${TERMUX_PKGS[@]}"
fi

version_ge() { [ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -n1)" = "$2" ]; }

if wants pi; then
    node_version="$(node --version 2>/dev/null | sed 's/^v//' || true)"
    version_ge "$node_version" "$PI_MIN_NODE" \
        || die "pi needs Node >= $PI_MIN_NODE; Termux has ${node_version:-none}. Try: pkg upgrade nodejs"
    log "Installing pi (native)"
    npm install -g --ignore-scripts @earendil-works/pi-coding-agent
fi

if wants codex && [ "$CODEX_FROM_TUR" = 1 ]; then
    log "Installing Codex natively from the Termux User Repository"
    pkg install "${APT_OPTS[@]}" tur-repo
    pkg install "${APT_OPTS[@]}" codex
fi

# --- Ubuntu container side ------------------------------------------------

in_container() { proot-distro login "$CONTAINER" -- "$@"; }

if [ "$NEED_CONTAINER" = 1 ]; then
    if in_container true >/dev/null 2>&1; then
        log "Reusing existing proot-distro container '$CONTAINER'"
    else
        log "Creating proot-distro container '$CONTAINER' from $UBUNTU_IMAGE"
        proot-distro install --name "$CONTAINER" "$UBUNTU_IMAGE"
    fi

    CONTAINER_PKGS="ca-certificates curl xz-utils git"
    wants hf && CONTAINER_PKGS="$CONTAINER_PKGS python3 python3-venv"

    log "Installing base packages in the container"
    in_container env DEBIAN_FRONTEND=noninteractive bash -euc "
        apt-get update
        apt-get install -y --no-install-recommends $CONTAINER_PKGS
    "

    NPM_PKGS=""
    wants copilot && NPM_PKGS="$NPM_PKGS @github/copilot"
    wants codex && [ "$CODEX_FROM_TUR" = 0 ] && NPM_PKGS="$NPM_PKGS @openai/codex"
    wants claude && NPM_PKGS="$NPM_PKGS @anthropic-ai/claude-code"

    if [ -n "$NPM_PKGS" ]; then
        # Ubuntu 24.04's apt Node is 18; Copilot's launcher needs newer, so use
        # the official nodejs.org build and check it against SHASUMS256.txt.
        log "Installing Node.js ($NODE_LINE, official build) in the container"
        in_container bash -euc "
            base=https://nodejs.org/dist/$NODE_LINE
            tmp=\$(mktemp -d)
            trap 'rm -rf \"\$tmp\"' EXIT
            cd \"\$tmp\"
            curl -fsSLO \"\$base/SHASUMS256.txt\"
            file=\$(grep -oE 'node-v[0-9.]+-linux-$NODE_ARCH\.tar\.xz' SHASUMS256.txt | head -n1)
            [ -n \"\$file\" ] || { echo 'no linux-$NODE_ARCH build listed' >&2; exit 1; }
            curl -fsSLO \"\$base/\$file\"
            grep \" \$file\$\" SHASUMS256.txt | sha256sum -c -
            rm -rf /opt/node && mkdir -p /opt/node
            tar -xJf \"\$file\" -C /opt/node --strip-components=1
            ln -sf /opt/node/bin/node /opt/node/bin/npm /opt/node/bin/npx /usr/local/bin/
            node --version
        "

        log "Installing npm CLIs in the container:$NPM_PKGS"
        # shellcheck disable=SC2086 # NPM_PKGS is a fixed, space-separated list
        in_container npm install -g --prefix /usr/local $NPM_PKGS
    fi

    if wants hf; then
        log "Installing Hugging Face CLI in the container"
        in_container bash -euc "
            python3 -m venv /opt/hf
            /opt/hf/bin/pip install --upgrade pip huggingface_hub
            ln -sf /opt/hf/bin/hf /usr/local/bin/hf
        "
    fi
fi

# --- wrappers -------------------------------------------------------------

# Write $PREFIX/bin/<name> so the tool runs in the container from any Termux
# directory. The Termux app dir (incl. $HOME) and /sdcard are bound into the
# container by proot-distro's default mode, so $PWD resolves to the same path.
write_wrapper() {
    local name="$1" envs="$2" target="$PREFIX/bin/$1"
    if [ -e "$target" ] && ! grep -qF "$WRAPPER_MARKER" "$target"; then
        warn "$target exists and was not made by this script; leaving it alone"
        return
    fi
    cat > "$target" <<EOF
#!$PREFIX/bin/bash
$WRAPPER_MARKER
# Runs '$name' inside the proot-distro container '$CONTAINER'.
set -e
args=(--work-dir "\$PWD")
for v in TERM COLORTERM $envs; do
    [ -n "\${!v:-}" ] && args+=(--env "\$v=\${!v}")
done
exec proot-distro login "\${args[@]}" "$CONTAINER" -- /usr/local/bin/$name "\$@"
EOF
    chmod 755 "$target"
    log "Wrote wrapper $target"
}

wants copilot && write_wrapper copilot "GH_TOKEN GITHUB_TOKEN"
wants codex && [ "$CODEX_FROM_TUR" = 0 ] && write_wrapper codex "OPENAI_API_KEY"
wants claude && write_wrapper claude "ANTHROPIC_API_KEY ANTHROPIC_BASE_URL"
wants hf && write_wrapper hf "HF_TOKEN"

# --- verify ---------------------------------------------------------------

log "Checking installed versions"
status=0
for tool in ${ONLY//,/ }; do
    if out="$("$tool" --version 2>&1 | tail -n1)"; then
        printf '  %-8s %s\n' "$tool" "$out"
    else
        printf '  %-8s FAILED: %s\n' "$tool" "$out"
        status=1
    fi
done

echo
echo "Sign in before first use:"
# shellcheck disable=SC2016 # backticks are literal text in these hints
for tool in ${ONLY//,/ }; do
    case "$tool" in
        copilot)
            echo '  copilot  run `copilot` and type /login, or export GH_TOKEN (fine-grained'
            echo '           token with the "Copilot Requests" permission)' ;;
        codex) echo '  codex    run `codex` and sign in with ChatGPT, or export OPENAI_API_KEY' ;;
        claude) echo '  claude   run `claude` and sign in, or export ANTHROPIC_API_KEY' ;;
        ollama)
            echo '  ollama   no sign-in: start the server with `ollama serve &`, then'
            echo '           `ollama pull <model>` (models are large; check storage and RAM)' ;;
        hf) echo '  hf       `hf auth login`, or export HF_TOKEN' ;;
        pi) echo '  pi       run `pi` and follow its model/provider setup' ;;
    esac
done
exit "$status"
