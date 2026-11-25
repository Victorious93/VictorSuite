# Interactive-only hook for the ?? assistant. A bare ?? is also a glob for
# two-character filenames, so an alias runs `set -f` (noglob) before the
# helper sees the words, then the function turns globbing back on.
if [[ $- == *i* ]]; then
  __vs_ask() {
    local helper="${PREFIX:-/data/data/com.termux/files/usr}/libexec/victorsuite/vs-ask"
    local rc=0
    if [ -x "$helper" ]; then
      "$helper" "$@"
      rc=$?
    else
      printf '%s\n' "vs-ask is not installed. Open VictorSuite once to set it up." >&2
      rc=127
    fi
    set +f
    return "$rc"
  }
  unalias '??' 2>/dev/null || true
  alias '??'='set -f; __vs_ask'
fi
