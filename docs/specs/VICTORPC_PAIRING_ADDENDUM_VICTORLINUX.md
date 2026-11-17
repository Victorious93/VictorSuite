# victorpc pairing — additive addendum: VictorLinux host

Status: **ADDITIVE DESIGN** (not implemented). This file does **not** thaw
[`VICTORPC_PAIRING.md`](VICTORPC_PAIRING.md) (frozen v1). It adds one optional,
ignorable QR hint and describes how a VictorLinux machine fills existing v1 fields.

Words: **MUST**, **MUST NOT**, **SHOULD** as in RFC 2119.

VictorLinux facts below are **[reported]** by Chief of Staff from VictorLinux docs
and have **not** been verified in this repository.

The design discussion, Don's open questions, and UI notes live in
[`docs/VICTORLINUX_INTEGRATION_PLAN.md`](../VICTORLINUX_INTEGRATION_PLAN.md).

The six open questions in the plan were **resolved Nov 16, 2026**: Don chose
option A for all of them. This addendum is unchanged in its wire rules; only
the question-5 pointer in §E is updated to record that decision.

---

## A. What is unchanged

Every v1 field keeps its meaning, type, and requirement: `v`, `id`, `n`, `u`, `h`,
`p`, `hk`, `pp`, `t`, `x`, `cap`. The enrollment transcript (§3), stored profile
(§4), `PcProfileProvider` columns (§5), `PcEnrollService` (§6), intents (§7),
consumer pins (§8), Tailscale notes (§9), and security invariants (§10) are
unchanged.

**Ubuntu PC pairing stays unchanged.** An Ubuntu QR does not include the hint in
§B. A v1 parser that ignores unknown keys already accepts a QR that does include it.

VictorSuite MUST work fully when VictorLinux is absent. VictorLinux MUST work fully
when VictorSuite is absent.

## B. Optional hint (unknown keys are ignored)

v1 already says: unknown keys MUST be ignored; duplicated keys → refuse.

A VictorLinux setup MAY add:

| Key | Required | Type | Rules |
|---|---|---|---|
| `src` | no | string | The only value defined here is `victorlinux`. Any other value, or a missing key, means "not opted in". v1 parsers ignore the key. |

Example (throwaway values, same vector host as frozen v1 §2, with the hint added and
`u=victor`):

```
victorpc://pair?v=1&id=7Q2M4KX9TB5N&n=don-victorlinux&u=victor&h=192.168.1.42,don-victorlinux.local&p=22&hk=ssh-ed25519:SHA256:gHC80Ray5GnftiotQW/JWkdaUZrT2zhJoAgvVkpna9w&pp=47022&t=AAECAwQFBgcICQoLDA0ODw&x=1762755600&cap=sftp&src=victorlinux
```

`src` is a hint to opt into a later `/victor/health` probe. It is **not** identity
(identity is `hk`) and **not** sufficient to show a "VictorLinux" tag.

No other new QR keys are defined here. In particular, a gateway Bearer token MUST
NOT be placed in the QR.

Do **not** overload `cap` with a type name (`cap=victorlinux` is not a capability).
Do **not** overload `n` or `u` as a type tag. `u` remains the login user.

## C. Host profile

When the host is VictorLinux **[reported]**:

- `u` MUST be `victor` (`AllowUsers victor`).
- `cap` SHOULD be `sftp` only if SFTP is actually offered; otherwise omit `cap` or
  leave it empty. Do not advertise `adb` unless that capability is really present.
- `h` MUST only list addresses the sshd already binds to (user-picked address
  **[reported]**; plus a tailnet address if one exists). It is still a hint list;
  order is still preferred connect order.
- SSH is key-only **[reported]**. Enrollment still appends one
  `restrict,pty[,…] <pub> victorpc:<dev>:<app>` line to `~victor/.ssh/authorized_keys`
  as in frozen v1 §3.
- SSH is off by default and binds only to an address the user picks **[reported]**.
  A VL-side setup MUST NOT enable SSH on `0.0.0.0` / `::`, and MUST NOT widen the
  bind as a side effect of pairing. The pairing listener MUST bind only the
  addresses in `h`.
- Phone keys remain `ecdsa-sha2-nistp256` (v1). The host SHOULD accept that
  algorithm (`PubkeyAcceptedAlgorithms +ecdsa-sha2-nistp256` or equivalent).

VictorLinux has no pairing protocol yet **[reported]**. The intended path is that
VL later speaks this same v1 listener/QR, so VictorSuite does not grow a second
enrollment protocol. Until then this addendum is a mapping, not an implementation
contract on the VL side.

## D. Optional "VictorLinux" tag and gateway health

VictorSuite MAY show a "VictorLinux" tag on a profile when:

1. `src=victorlinux` was present at pairing,
2. a host in `h` is reachable over SSH with the pinned `hk`,
3. a gateway Bearer token is stored for the profile (Keystore-encrypted; never in
   the QR, never in a provider row, never logged),
4. `GET /victor/health` on that same host, port **8080** **[reported]**, completes
   within a short timeout (recommended: 2 s connect, 3 s overall, one attempt) and
   returns **200** or **503** **[reported]**.

Otherwise the tag is simply absent. Failure, timeout, or a missing VictorLinux
install MUST NOT break pairing, SSH, SFTP, or an Ubuntu profile.

`PcProfileProvider` v1 columns are unchanged. This increment does not add a `tags`
column; the label is VictorSuite-local until a later additive change is approved.

## E. Inference shortcut (out of band for the wire spec)

A Termux OpenAI-compatible client using model `victor-local` is a UI shortcut on a
tagged profile only. It is not a v1 QR/provider field. Its base URL is not specified
here because it is **[unverified]**. Don chose plan question 5 option A on
Nov 16, 2026: confirm the base URL against VictorLinux docs before any feature
code; if VL documents one (for example a path on the `:8080` gateway), use that
with the stored Bearer token; do not invent a path in frozen v1.
