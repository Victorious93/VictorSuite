# VictorLinux integration — plan

> **Status: DESIGN ONLY.** No app code, no version bump, no pairing-protocol change.
> Do not merge without Don's approval.
>
> Written Nov 16, 2026 against `main` @ `40b2609` (v0.118.3.64). Recent main includes
> Remote PC plan + frozen victorpc v1 (#99), the quick-SSH security fix (#101, plus lint
> follow-up #104), and CI release-pruning (#106). Remote PC implementation PRs 2–9 are
> **not** on main yet; this plan does not depend on them landing first, but the features
> it describes would ship after (or with) that series.
>
> **Decisions (Nov 16, 2026):** Don chose **option A** for all six open questions in
> §9. Those questions are marked resolved below. This file was updated after #107
> landed on `main` (`b836e21`; `main` is now `9138004` including #108). The frozen
> v1 spec is unchanged.
>
> Shared wire formats: [`docs/specs/VICTORPC_PAIRING.md`](specs/VICTORPC_PAIRING.md)
> (frozen v1). Additive host profile:
> [`docs/specs/VICTORPC_PAIRING_ADDENDUM_VICTORLINUX.md`](specs/VICTORPC_PAIRING_ADDENDUM_VICTORLINUX.md).
> **[reported]** = taken from Chief of Staff's summary of VictorLinux docs, not verified
> in this repo. **[unverified]** = not confirmed against VictorLinux source or a device.

## 0. Goal and standing rule

Let a VictorLinux machine be a **victorpc v1 pairing target** — same QR, same enrollment
transcript, same host-key pin, same one-tap terminal / SFTP — without changing Ubuntu PC
pairing at all.

**Standalone in both directions:** VictorSuite MUST work fully without VictorLinux
(Ubuntu PCs, LAN, Tailscale, My PC, File Manager, DroidCommand). VictorLinux MUST work
fully without VictorSuite (its own SSH, gateway, and local inference). Neither install
is a prerequisite for the other. If VictorLinux is absent, there is no tag, no health
probe, and no inference shortcut.

## 1. Priority / size

| # | Item | Priority | Size | This PR |
|---|---|---|---|---|
| 1 | Additive note: VictorLinux as a victorpc v1 host (`u=victor`, `cap=sftp`, PC-side setup mapped onto key-only SSH) | High | S | Spec addendum + this plan. No feature code. |
| 2 | Optional "VictorLinux" profile tag, read from `GET /victor/health` only when reachable; Bearer token handling; timeouts; tag simply absent otherwise | Med | S | Design only. |
| 3 | Shortcut to the Termux inference client (OpenAI-compatible, model `victor-local`) | Low | S | Design only. |

Ubuntu PC pairing stays unchanged in every item.

## 2. Reported VictorLinux facts (not verified here)

Chief of Staff reported these from VictorLinux's docs. Treat them as **[reported]** until
a VictorLinux owner or a read of that repo confirms them:

- SSH is **key-only**, with `AllowUsers victor`.
- SSH is **off by default** and binds **only to an address the user picks**.
- A gateway listens on **`:8080`** with `GET /victor/health` returning **200** (ok) or
  **503**, and **Bearer** authentication.
- VictorLinux has **no pairing protocol yet**.

Anything beyond that list (exact health JSON, inference base URL, token file path, SFTP
subsystem, HTTPS vs HTTP) is **[unverified]** and MUST NOT be baked into v1 field
meanings.

## 3. Ubuntu PC pairing stays unchanged

The frozen v1 QR, enrollment listener, `PcProfileProvider` columns, `PcEnrollService`,
`OPEN_PC`, host-key hard stop, and `pc/victor-pc-setup.sh` behaviour for Ubuntu 24.04
(primary) / 22.04 (best effort) do **not** change meaning.

- An Ubuntu QR does **not** carry the optional VictorLinux hint.
- VictorSuite MUST NOT probe `:8080` on a profile that lacks that hint.
- The "VictorLinux" tag is simply missing.
- The inference shortcut is not shown.
- A v1 parser that has never heard of VictorLinux still accepts a future VictorLinux QR,
  because any new hint is an unknown key and v1 already says unknown keys MUST be ignored.

## 4. Item 1 — VictorLinux as a victorpc v1 host (High / S)

VictorLinux is a **host**, not a new protocol. The phone still scans `victorpc://pair?v=1&…`
and runs the §3 enrollment transcript.

### 4.1 How v1 fields are filled (no meaning changes)

| v1 field | VictorLinux use | Notes |
|---|---|---|
| `v` | `1` | Unchanged. |
| `id` | 12-char profile id from the VL-side setup | Same generator rules. |
| `n` | Display name (hostname or a name Don chooses) | Not a type tag; do not overload `n` with "VictorLinux". |
| `u` | **`victor`** | Fits the existing POSIX name rule. The VL-side setup SHOULD refuse to emit a QR with any other user. |
| `h` | The address(es) SSH already binds to, preferred order (LAN first, then tailnet if present) | Hints only; identity is still `hk`. MUST NOT list addresses SSH does not listen on. MUST NOT imply a bind to `0.0.0.0` / `::`. |
| `p` | sshd port (normally 22) | Unchanged. |
| `hk` | Host-key list in the frozen form `<keytype>:SHA256:<b64-no-pad>` | Unchanged. |
| `pp`, `t`, `x` | Pairing listener port, one-time token, expiry | Same rules, once VL has a listener (question 1). |
| `cap` | **`sftp`** when SFTP is actually offered; omit rather than guess | Unknown cap values are already ignored. Do **not** overload `cap` with a type name like `victorlinux`. Do not set `adb` unless a later, verified Android container exists on that host. |
| unknown keys | optional `src=victorlinux` | See §4.3. Ignorable by v1 parsers. |

After a successful pair, the stored profile looks like any other: user `victor`,
`caps` containing `sftp` when advertised, pinned `host_keys`. Terminal and SFTP then
use the existing plan (OpenSSH + Keystore agent, or sshlib for SFTP).

### 4.2 Mapping `pc/victor-pc-setup.sh` onto VictorLinux SSH

The Ubuntu script turns on `sshd`, drops in `PasswordAuthentication no`, sets
`AllowUsers <user>`, allows `ecdsa-sha2-nistp256`, scopes ufw to LAN/tailnet, and
runs a one-shot HMAC listener. VictorLinux already covers part of that **[reported]**.
The VL-side setup (a wrapper, a documented mode of the same script, or a VL-owned
equivalent — question 1) MUST map as follows:

| Ubuntu script step | VictorLinux mapping |
|---|---|
| Install / enable OpenSSH | SSH already exists but is **off by default**. The setup MUST NOT enable SSH or widen its bind as a side effect of "just print a QR". Recommended: refuse to pair until Don has already enabled SSH on a chosen address, or enable it only on that already-chosen address after an explicit confirmation. |
| Bind / listen address | MUST keep the user-picked address. NEVER bind SSH or the pairing listener to `0.0.0.0` / `::`. The listener binds only the addresses listed in `h`. |
| `PasswordAuthentication no` | Already key-only **[reported]**. Do not turn password auth on. Do not loosen existing hardening. |
| `AllowUsers victor` | Already set **[reported]**. Keep it. QR `u` is `victor`. |
| `PubkeyAcceptedAlgorithms +ecdsa-sha2-nistp256` | Still required: phone keys in v1 are Android Keystore P-256 (`ecdsa-sha2-nistp256`). The listener also accepts `ssh-ed25519` for future consumers. Confirm this on VL before a feature PR **[unverified]**. |
| `authorized_keys` line | Same as frozen v1: `restrict,pty[,…] <pub> victorpc:<dev>:<app>` (mode 0600, dir 0700, owned by `victor`). |
| Firewall | Same invariant: no router forwards, no UPnP, no internet exposure of SSH, the pairing port, or `:8080`. Scope to LAN and tailnet only. |
| Lockout guard | Still applies: do not change sshd in a way that drops the last working console/key path. |
| Avahi / `_victorpc._tcp` | Optional. If VL has no avahi, the phone uses the addresses in `h` and the existing subnet-scan fallback. Identity remains the host-key pin. |

Until VictorLinux has a pairing protocol **[reported]**, this is a **target mapping only**.
VictorSuite SHOULD NOT grow a second, VL-only enrollment path (recommended answer to
question 1).

### 4.3 Optional QR hint (`src`)

Add one optional query key, ignorable by any v1 parser:

```
src=victorlinux
```

- Not required. Missing on Ubuntu QRs.
- Duplicated keys still → refuse (existing rule).
- The hint is **not** proof that the host is VictorLinux. It only opts the phone into
  a later health probe (§5). The tag is shown only after `/victor/health` succeeds.
- No other new QR keys in this increment. In particular, the gateway **Bearer token
  MUST NOT** appear in the QR (see §5.2).

## 5. Item 2 — optional "VictorLinux" profile tag (Med / S)

The tag is a **UI label** on a My PC profile ("VictorLinux"). It is not a new v1
identity field and is not required for SSH, SFTP, or terminal.

### 5.1 When to probe

**Recommended:** probe `GET /victor/health` only when all of these are true:

1. The profile carries `src=victorlinux` (from the QR, stored with the public profile).
2. A host from `h` is already reachable for SSH (same preferred order: LAN first).
3. A gateway Bearer token is already stored for that profile (§5.2).
4. My PC is in the foreground (no persistent background service).

If any check fails, the tag is simply **absent**. No error banner on Ubuntu profiles,
and no `:8080` scan of the LAN.

**Timeouts (recommended):** 2 s connect, 3 s overall, **one** attempt, no retry storm.
The probe MUST NOT block pairing save, profile list rendering, or an SSH/SFTP tap.
Run it after the list is shown; if it times out or fails, leave the tag off and
optionally retry on the next foreground visit.

Default URL, using the same host that answered SSH: `http://<host>:8080/victor/health`.
A non-default gateway port or path is **[unverified]** and is an open question; do not
invent a required QR field for it in this increment. Talk only to addresses already in
`h`. Do not follow redirects off those hosts. `:8080` is **[reported]** as a LAN
gateway; treat it like SSH — never expose it to the internet.

### 5.2 Bearer token

`/victor/health` is Bearer-authenticated **[reported]**. Rules:

- MUST NOT put the token in the QR in plaintext. A QR is photographed, can sit in a
  screenshot or chat, and is a pairing *one-shot*, not a long-lived gateway secret.
- MUST NOT write the token to a repo, a log (including logcat), a provider column, a
  backup/export, or the clipboard (VictorSuite MUST NOT copy it there).
- Store it Keystore-encrypted (AES-256-GCM, non-exportable key), in the same spirit as
  the quick-SSH `passwordEnc` work from #101. Suggested alias:
  `victorsuite.victorlinux.gw.<profileId>`. That prefix is **outside** the reserved
  `victorpc.` SSH-key alias space (`KeystoreAPI` already refuses `victorpc.*`).
- If encryption fails, do not store the token in plaintext; skip the tag.
- The HTTP client MUST redact `Authorization` and the token from every log, crash
  report, and error message.

**How Don first provides the token (recommended, question 2):** after a successful
SSH pair (host key already pinned), offer an optional step: paste the gateway token
once, or fetch it over that pinned SSH session from a path VictorLinux documents.
Skipping the step is fine: SSH/SFTP still work, the tag stays off, the inference
shortcut stays hidden.

### 5.3 How to interpret the response

Exact JSON is **[unverified]**. Conservative rules:

| Result | Tag |
|---|---|
| Timeout, connection refused, wrong host, TLS/HTTP error | Absent |
| No stored token (do not call the endpoint) | Absent |
| HTTP 401/403 | Absent (do not retry in a loop; do not log the token) |
| Body that does not look like the reported health endpoint | Absent (never tag a random service on `:8080`) |
| HTTP 200 | Show "VictorLinux" |
| HTTP 503 | Still VictorLinux, but unhealthy: show the tag in a muted state (question 4) |

A 200 or 503 is enough to set the tag. Do not parse undocumented fields into v1
profile columns.

### 5.4 Where the tag lives

**Recommended (question 6):** keep the tag **internal to VictorSuite** (My PC UI and
local public profile extras) in this increment. Do not add a `PcProfileProvider`
column yet. File Manager and DroidCommand already work with the frozen columns once
Remote PC PRs 8+ exist; they do not need this label to open terminal or SFTP.

An additive optional `tags` column MAY be proposed later. It MUST NOT overload
`name`, `user`, or `caps`.

## 6. Item 3 — inference shortcut (Low / S)

Only when the VictorLinux tag is present, My PC MAY show a shortcut that opens a
**Termux inference client** aimed at an OpenAI-compatible endpoint with model
**`victor-local`**.

- This is a **local** shortcut, not a new cloud AI and not DeepSeek. DroidCommand AI
  remains the fleet AI app.
- Auth uses the same Keystore-encrypted Bearer token as §5. Never log it. Never put
  it on a command line (same lesson as the #101 `sshpass -p` fix).
- The exact base URL / path is **[unverified]** (question 5). Do not invent a required
  path in frozen v1. Confirm against VictorLinux docs before any feature PR. A
  reasonable guess, not to be coded yet, is a path on the same `:8080` gateway.
- If the endpoint is down, timed out (same budget as §5.1), or returns an error: a
  short English message, no crash, no retry loop.
- If the tag is absent (Ubuntu, unreachable health, no token): the shortcut is not
  shown. VictorSuite and the Termux session remain usable.

## 7. Security invariants (additions, not replacements)

All of frozen v1 §10 still applies. In addition:

- No secret in the QR except the existing one-shot pairing token `t` (already
  specified, short-lived, single-use).
- Gateway Bearer token: Keystore-encrypted, never logged, never in the provider.
- Do not enable or rebind VictorLinux SSH except as §4.2 allows.
- Do not expose SSH, the pairing port, or `:8080` to the internet.
- Host-key change remains a hard stop with no override; fix is re-pair.
- Feature code (later PRs) MUST NOT ship until Don approves it, and MUST NOT merge
  without a later yes.

## 8. Risks

- **No VL pairing protocol yet [reported].** If we invent a side path, the fleet ends
  up with two enrollment stories. Prefer one v1 listener on the VL side.
- **Mis-tagging** a random `:8080` service: avoided by requiring the `src` hint, a
  stored token, and a conservative health check.
- **Token leakage** via QR, logcat, or `OPEN_PC` extras: forbidden in §5.2.
- **Cleartext HTTP on LAN** **[unverified]:** only to addresses in `h`, no off-host
  redirects; same exposure model as SSH on a LAN. HTTPS, if VL later offers it, is
  welcome but not assumed.
- **Probe latency** on the My PC list: avoided by not probing Ubuntu profiles and by
  keeping the probe off the UI thread with a tight timeout.
- **SFTP advertised but missing** **[unverified]:** only set `cap=sftp` when VL
  actually offers it; otherwise leave `cap` empty and terminal still works.
- **P-256 algorithm not accepted** on VL sshd **[unverified]:** confirm before a
  feature PR; without it, enrollment cannot complete with a Keystore key.
- **Standalone broken by accident:** a failed health check MUST NOT block SSH; a
  missing VictorSuite MUST NOT break VL.

## 9. Open questions for Don (resolved)

**Resolved Nov 16, 2026:** Don chose **option A** for all six questions. The
original wording is kept for the record; each chosen option is marked
**Chosen**. First option was the recommendation in every case.

First option is the recommendation.

1. **How should the first VictorLinux pair work, given VL has no pairing protocol yet?**
   - **A (recommended) — Chosen Nov 16, 2026:** VictorLinux later implements the same v1 HMAC listener and QR.
     VictorSuite stays on one protocol. No feature code until that VL work is approved.
   - **B:** A temporary out-of-band path (paste the phone's pubkey, or an `ssh-copy-id`
     helper over a console session) until VL adds the listener.
   - **C:** Do not treat VictorLinux as a pairing target until VL ships a listener;
     keep this document as a mapping only.

2. **How should VictorSuite get the gateway Bearer token?**
   - **A (recommended) — Chosen Nov 16, 2026:** Never in the QR. After a successful SSH pair, optional paste
     once, or fetch over the pinned SSH session from a path VL documents. Stored
     Keystore-encrypted. Skipping it means no tag and no inference shortcut; SSH still
     works.
   - **B:** Put a token in the QR (not recommended: QRs get photographed and forwarded).
   - **C:** Do not handle a gateway token in VictorSuite at all for now; tag and
     inference wait for a later VL story.

3. **When should the phone call `/victor/health`?**
   - **A (recommended) — Chosen Nov 16, 2026:** Only if the optional `src=victorlinux` hint is present, a
     token is stored, and SSH to that host already succeeded. Never probe Ubuntu
     profiles.
   - **B:** Probe `:8080` on every reachable profile and tag only if the response looks
     like VictorLinux (more latency, some mis-tag risk).
   - **C:** No automatic probe; a manual "This is VictorLinux" toggle on the profile,
     then probe.

4. **If `/victor/health` returns 503 (present but unhealthy)?**
   - **A (recommended) — Chosen Nov 16, 2026:** Still show the VictorLinux tag, in a muted "unavailable"
     state.
   - **B:** Treat 503 like unreachable (tag absent).
   - **C:** Show the tag and a blocking error that interrupts SSH/SFTP.

5. **What is the OpenAI-compatible base URL for model `victor-local`?**
   - **A (recommended) — Chosen Nov 16, 2026:** Confirm with VictorLinux docs before any feature code. If VL
     documents a base URL (for example a path on the `:8080` gateway), use that with
     the stored Bearer token. Do not invent a path in frozen v1.
   - **B:** Hard-code a guessed path in the design now (risky; the path is unverified).
   - **C:** The shortcut only opens a Termux shell with a placeholder command Don can
     edit.

6. **Should File Manager or DroidCommand see the tag through `PcProfileProvider`?**
   - **A (recommended for keeping v1 frozen) — Chosen Nov 16, 2026:** Not in this increment. The tag stays
     internal to VictorSuite's My PC UI. An additive optional `tags` column can be a
     later PR if those apps need it.
   - **B:** Add an optional `tags` column in the addendum now (additive, ignorable by
     existing consumers).
   - **C:** Encode the tag by overloading `name` or `caps` (rejected here: that would
     change the meaning of a v1 field).

## 10. UI note

English UI. Fleet chrome: background `#0B0F1A`, accent `#00E5FF`, highlight `#FF2BD6`.
Reuse `FleetThemeApplier` on My PC when that screen exists (Remote PC plan §3).

- The tag is a small cyan chip on the profile row, label **VictorLinux**. It is not
  shown at all when absent.
- A 503 / muted state uses a dimmer cyan, not a red alarm, and MUST NOT block
  Terminal or SFTP.
- The inference shortcut, only when the tag is present, is a clearly named control:
  **Local AI (victor-local)**.
- Host-key change keeps the existing hard-stop screen (no "accept anyway").
- No ZeroTermux traces, no "powered by" clutter, no new cloud-AI brand.
- Later feature PRs should add `en` strings first and follow the existing `zh-rCN`
  pattern; this plan's copy is English.

## 11. Suggested later work (not in this PR)

Docs only today. Don approved the recommendations (option A for Q1–Q6) on
Nov 16, 2026. Later PRs (each their own review, no merge without a later yes)
would be:

1. VictorLinux-side v1 listener / QR (owned by VictorLinux, not this repo).
2. After Remote PC PRs that land pairing on the phone: store `src`, encrypted gateway
   token, health probe, tag chip.
3. Inference shortcut, once the base URL is confirmed.

No version bump for this docs PR.
