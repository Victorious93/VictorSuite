# Hak5 Device Companion — Design Plan (VictorSuite)

**Status:** planning only. This document does **not** implement features. Revised 2026-10-15 after Don's review (see Decisions).  
**Owner:** Don Victor (GitHub [Victorious93](https://github.com/Victorious93)).  
**Repo:** private `Victorious93/VictorSuite` (Termux / ZeroTermux fork, package `com.termux`, `targetSdk` **28**).  
**Goal of this PR:** agree on scope, architecture, and safety before any feature code is written.

---

## 0. What we are building (plain words)

A **companion** inside VictorSuite that helps Don work with two Hak5 devices he owns (or is authorized to test):

1. **WiFi Pineapple Tetra** — a real network appliance with a web UI and a programmable JSON API. **VictorSuite** discovers it over Wi-Fi/LAN, authenticates, shows status, lists and runs modules/scripts, and keeps a command log.
2. **Plunder Bug** — a **passive** USB-C Ethernet tap with **no control API**. The companion role is **capture control** (start/stop `tcpdump` / pcap on the host that sees the tap interface) and **pcap viewing**, **not** remote command-and-control of the Bug itself. **This work lives in RootForge**, not VictorSuite (see Decisions).

This is for **authorized testing only**. Nothing in this plan auto-attacks, auto-runs offensive modules, or pretends the Plunder Bug is controllable like the Pineapple.

Related existing pieces in this repo (reuse, do not reinvent):

- Plugin framework (`com.termux.zerocore.plugin`) with an authorized-use gate and `PluginLogger` JSONL audit trail — see `PLUGINS.md`.
- Suite / two-APK strategy and `targetSdk` 28 rationale — see `docs/SUITE_PLAN.md`.
- OkHttp already on the classpath (`app/build.gradle`).
- `RUN_COMMAND` / `RunCommandService` for driving Termux-side scripts from the app.

---

## Decisions (locked by Don 2026-10-15)

| # | Decision | Effect on this plan |
|---|---|---|
| D1 | **Hardware is a WiFi Pineapple Tetra** (6th-gen NANO/TETRA `/api/`). | Target the 6th-gen POST `/api/` + `apiToken` model only. Mark 7 REST is dropped from scope. **Caveat stays:** the exact firmware version still has to be confirmed on the real device before any module/action names are written into code. |
| D2 | **Phase 1 connectivity is Wi-Fi/LAN management only.** | Phase 1 talks to the Pineapple over its management AP or LAN (default `172.16.42.1:1471`). **USB tether to the Pineapple is moved to a later enhancement**, not Phase 1. |
| D3 | **Ownership split: VictorSuite owns the Pineapple HTTP companion. RootForge owns USB and the Plunder Bug tap.** | VictorSuite (`com.termux`, `targetSdk` 28) builds the Pineapple screens, HTTP client, safety gate and audit log. RootForge (`ai.rootforge.os`, modern `targetSdk`, already owns USB/device control) builds Plunder Bug detection, capture and pcap viewing, plus any future USB tether. The two apps link to each other with a deep-link handoff. |

---

## 1. Target devices — how they actually work

### 1.1 WiFi Pineapple Tetra (confirmed target, 6th-gen API) — owned by VictorSuite

**What it is.** A portable wireless auditing appliance. On a typical management network it exposes an access point / management IP commonly documented as **`172.16.42.1`**, with the web UI / API on **port `1471`**. SSH to the device is also commonly available (exact port/user defaults are **firmware-dependent** — confirm on Don’s unit).

**API shape (verified against Hak5 6th-gen NANO/TETRA docs).**  
Unlike older monolithic UIs, the backend is decoupled. Client requests are **HTTP POSTs to `/api/`** with a JSON body.

Required keys:

| Key | Role |
|---|---|
| First key must be either `system` **or** `module` | `system` = core (login/setup/notifications, etc.). `module` = value is the module name (built-in or user module), e.g. `"module": "RandomRoll"`. |
| `action` | The action to perform, e.g. `"listNotifications"`, `"addNotification"`. |
| Other keys | Optional, module/action-specific. |

Documented examples (Hak5):

```json
{"system": "notifications", "action": "listNotifications"}
```

```json
{
  "system": "notifications",
  "action": "addNotification",
  "message": "Hello World!"
}
```

**Auth (verified).** Two models exist on 6th-gen:

1. **Browser session style (awkward for apps):** `PHPSESSID` cookie + `X-XSRF-TOKEN` header that must match the session’s XSRF token. Hak5 themselves describe this as clumsy for programmatic clients.
2. **API tokens (preferred for a companion):** generate a token via the **API Tokens** module on the Pineapple (shipped with **no** valid tokens by default). Then include `"apiToken": "<token>"` in the JSON body of each request. Invalid token → error / not routed.

Sources (public Hak5 docs; do not invent beyond these):

- [Module API — Introduction](https://documentation.hak5.org/wifi-pineapple-6th-gen-nano-tetra/development/wifi-pineapple-nano-tetra-module-api-introduction)
- [Module API — Authentication](https://documentation.hak5.org/wifi-pineapple-6th-gen-nano-tetra/development/wifi-pineapple-nano-tetra-module-api-authentication)
- [Creating modules](https://documentation.hak5.org/wifi-pineapple-6th-gen-nano-tetra/development/creating-wifi-pineapple-nano-tetra-modules) (modules live under `/pineapple/modules/`, expose actions via `module.php`)

**What this means for VictorSuite.** A mobile companion should:

1. Discover reachability of `http://172.16.42.1:1471/` (and allow manual IP override).
2. Store an **API token** (and optionally SSH credentials) in encrypted prefs — same pattern as `PluginConfigStore` / `EncryptedSharedPreferences`.
3. Speak the POST `/api/` JSON protocol via OkHttp.
4. Treat **module names and action names for “status”, “list modules”, “run script” as firmware-specific.** Many useful actions are **not** listed in the short public intro pages. **Do not invent endpoint names.** Phase 2 must probe Don’s actual firmware (or scrape the on-device module `module.php` / UI) and document a small allowlist of confirmed `(module|system, action)` pairs. Until then, mark candidates as **“to be confirmed against a real device/firmware version.”**

**SSH (secondary path).** SSH is useful for status scripts, package ops, or when the web API is insufficient. Exact shell layout, host key, and default credentials are **not** assumed here — confirm on device. Prefer key-based auth stored in encrypted prefs; never hardcode passwords in the APK.

**USB tether (deferred, per D2).** Putting the phone on the Pineapple's management network over USB is **not** part of Phase 1. If it is picked up later, it belongs to **RootForge** (per D3), because USB host work is easier on a modern `targetSdk`. How the Tetra presents itself over USB is unverified and would need testing on the real device first.

**Firmware caveat (still open).** The hardware is confirmed as a Tetra (D1), but the exact firmware version is not. Module names, action names and response fields can differ between firmware releases, so they must be checked on Don's unit before implementation. Mark 7 and newer Pineapples use a different REST API (`POST /api/login` then Bearer token) and are **out of scope**.

### 1.2 Plunder Bug — owned by RootForge

**What it is (verified).** A pocket-sized **LAN tap**: two RJ45 ports go inline on a link; a **USB-C** port connects to a capture host. The host sees a new USB Ethernet NIC (documented as **ASIX AX88772C**). Traffic between the two Ethernet ports is **mirrored** to the USB tap for tools like Wireshark / `tcpdump`.

Source: [Tapping an Ethernet link](https://documentation.hak5.org/plunder-bug/getting-started/master).

**Modes (verified).** By default the USB tap can be **active/unmuted** (host can also transmit onto the LAN). Hak5 documents switching to **passive/muted** so the host only receives the mirror and cannot transmit downstream — desirable for pure observation. Mode switching is done with **OS-specific mute scripts** on the host, not via a device API.

Source: [About Mode Switching](https://documentation.hak5.org/plunder-bug/active-and-passive-modes/about-mode-switching).

**Critical limitation — no control API.** The Plunder Bug has **no** REST/SSH/“modules” surface like the Pineapple. It is hardware that presents a network interface. A companion **cannot** “command” the Bug. Realistic VictorSuite roles:

| Realistic | Not realistic |
|---|---|
| Detect the USB Ethernet interface when plugged in | Remote C2 of the Bug |
| Prefer muted/passive mode (run mute script if available on Termux/Android) | “List modules” / “run payload” on the Bug |
| Start/stop `tcpdump -i <iface> -w …` (or similar) on the **phone / Termux host** | Claiming PoE pass-through or gigabit (Bug is documented as 10/100-class tap; PoE behavior is environment-specific — confirm) |
| Browse / summarize saved pcaps in-app or via Termux tools | Inventing a Plunder Bug HTTP API |

**Where it lives (D3).** Plunder Bug support is built in **RootForge**, not VictorSuite. RootForge already owns USB and device control and stays on a modern `targetSdk`, so it is the right home for USB permission prompts and USB Ethernet handling. VictorSuite only shows a "Plunder Bug capture → open in RootForge" entry that deep-links across (and offers the RootForge sideload if it is missing).

**Android caveat (still applies inside RootForge).** USB host Ethernet plus `tcpdump` on Android is **harder** than on a laptop. It depends on kernel support, USB host permission, whether the ASIX driver is present, and whether capture needs root. Phase 3 must be validated on Don's phone. If the phone cannot claim the NIC, RootForge falls back to "guide the user / drive capture on a linked Linux host" instead of pretending capture works.

---

## 2. Phased plan

### Phase 1 — Discovery + connection / status screen

**Goal.** In VictorSuite, find the Tetra over **Wi-Fi/LAN only** (D2), show "reachable / not reachable," and open a connection settings sheet. No offensive actions. No USB.

**What gets built (plan-level):**

- In-app **Hak5 Companion** entry (drawer / tools screen) behind the **authorized-use gate** (§4).
- Reachability check: HTTP GET/HEAD or light POST to `http://<host>:1471/` (default `172.16.42.1`), with manual IP/hostname field and timeout.
- Optional mDNS / ARP / “scan known management subnets” — **nice-to-have**; default is manual IP + last-known-good. Do not scan arbitrary networks without the gate and a clear warning.
- Status UI: online/offline, last checked time, firmware string **if** a confirmed read-only API action exists (otherwise “unknown — confirm on device”).
- Token / SSH credential fields stored via encrypted prefs (reuse `PluginConfigStore` patterns or a sibling `Hak5ConfigStore`).
- A "Plunder Bug (RootForge)" tile that only deep-links to RootForge; no USB detection in VictorSuite.

**How it’s tested:**

- Unit-test URL builders and timeout handling with mocked OkHttp.
- Manual: phone on Pineapple AP / LAN; wrong IP shows clear failure; correct IP shows online.
- Manual: RootForge not installed → handoff tile offers sideload instead of crashing.

**Rough dependencies:** OkHttp (already present); Android network permission already typical for Termux. No USB permission in VictorSuite.

---

### Phase 2 — Pineapple authenticated session, status, modules/scripts, command log

**Goal.** Hold an authenticated API session (API token preferred), show read-only status, list modules, run **explicitly confirmed** common module actions / scripts, and append every call to a persistent command log.

**What gets built (plan-level):**

- `PineappleClient` (OkHttp): POST `/api/` JSON with `apiToken`; map errors to human messages.
- Token setup UX: “paste token from API Tokens module” (and optional helper text that tokens are created **on the Pineapple**, not by inventing endpoints in-app).
- **Confirmed allowlist** of `(system|module, action)` pairs — populated only after checking Don’s firmware. Candidates to **probe / confirm** (not asserted as real until verified):
  - notifications list / add (documented examples exist)
  - listing installed modules — **to be confirmed against a real device/firmware version**
  - dashboard / system status fields — **to be confirmed…**
  - starting/stopping specific named modules or scripts — **to be confirmed…**; each run requires per-action confirmation (§4)
- Script runner path: either API actions that exec scripts **if confirmed**, or SSH-exec of user-supplied script paths under a sandboxed prefix — prefer API when possible.
- **Persistent command / audit log** (JSONL, same spirit as `PluginLogger`): timestamp, device id, request summary (module/system + action), success/fail, user confirmation id. Never log full secrets (redact `apiToken`).

**How it’s tested:**

- Mock server fixtures replaying real captured JSON from Don’s Tetra (golden files in `docs/` or test assets — no secrets).
- Manual matrix: valid token, invalid token, offline, module missing.
- Audit log survives process death; exportable for review.

**Rough dependencies:** Phase 1; EncryptedSharedPreferences; optional JSch/sshj **only if** SSH path is approved (new dependency — call out in implementation PR). Prefer keeping SSH optional.

---

### Phase 3 — Plunder Bug capture + pcap viewing (in RootForge)

**Goal.** In **RootForge** (D3): when a Plunder Bug USB NIC is present (or the user points at an interface name), start/stop capture and review pcaps. **No** Bug C2. VictorSuite's only part is the deep-link handoff.

**What gets built (plan-level):**

- RootForge USB permission flow + interface picker (from `/sys/class/net` / `ip link`, or a Termux helper if RootForge shells out to Termux).
- Mute/unmute helper: ship or document the Hak5 mute approach adapted for Android/Termux **if feasible**; otherwise instruct “prefer muted mode from a companion Linux host.”
- Capture control UI in RootForge: start → `tcpdump -i … -w <capture dir>/….pcap` (exact flags and storage location TBD); stop; show file size / duration.
- Simple pcap list + open-with Termux packet tools or export via Storage Access Framework.
- Big warning that unmuted mode can inject traffic onto the tapped LAN.

**How it’s tested:**

- On a device with USB host + ASIX support: plug Bug, see iface, capture loopback-style test traffic, open pcap.
- On unsupported phones: graceful “capture not available here; use linked host” messaging — no fake success.

**Rough dependencies:** RootForge app (`ai.rootforge.os`) and its USB stack; a `tcpdump` binary (user-installed, or via Termux); possibly root; the shared suite deep-link handoff from `SUITE_PLAN.md`. Work happens in the `rootforge-os` repo; this doc only records the plan.

### Later enhancement — USB tether to the Pineapple (deferred, per D2)

Not scheduled. If Don wants it later, it is built in **RootForge** (USB owner). It must first be checked on the real Tetra that USB tether gives a usable management interface on Android. VictorSuite would keep talking HTTP to whatever address the tether exposes.

---

### Phase 4 — Cross-cutting safety layer

**Goal.** Safety is not a bolt-on at the end — Phases 1–3 all call into this layer. Listed last because it is **cross-cutting**, but it ships **with** Phase 1 at minimum.

**What gets built (plan-level):**

- One-time (or per-profile) **Authorized Use** acknowledgment: explicit text that the user owns the network/device or has **written permission** to test it. Mirror the plugin framework’s `acknowledgeAuthorizedUse` → then enable pattern.
- Per-action confirmation dialogs for anything that **changes state** on the Pineapple or starts a capture (especially unmuted / active tap).
- Severity tags on actions: `read-only` / `config` / `potentially-invasive`. Invasive actions never batch-run without individual confirms. **Never** auto-chain “recon → attack” modules.
- Audit log UI (filter, export, clear-with-confirm).
- Kill-switch: disable companion / revoke stored tokens from Settings.
- Copy that this is for **authorized testing only**; misuse is on the operator.

**How it’s tested:**

- Automated tests that invasive actions refuse without acknowledgment + confirmation.
- Manual review of every new module action mapped to a severity before merge.

**Rough dependencies:** `PluginRegistry`-style gate patterns; `PluginLogger`-style JSONL; Settings screen.

---

## 3. Architecture fit inside a Termux fork

### 3.1 Where the companion lives

**Split per D3:**

| App | Owns |
|---|---|
| **VictorSuite** (`com.termux`, `targetSdk` 28) | Pineapple Tetra companion over Wi-Fi/LAN HTTP: discovery, token auth, status, allowlisted modules/scripts, safety gate, audit log. |
| **RootForge** (`ai.rootforge.os`, modern `targetSdk`) | Everything USB: Plunder Bug detection, capture, pcap viewing; any future Pineapple USB tether. |

**Handoff / deep link.** VictorSuite shows a "Plunder Bug capture (RootForge)" entry that launches RootForge's capture screen through the suite deep-link / "Open in RootForge" mechanism already planned in `docs/SUITE_PLAN.md`. If RootForge is not installed, VictorSuite offers the sideload instead. RootForge can link back to VictorSuite's Pineapple screen the same way. No shared `sharedUserId`; each app keeps its own audit log, with the same JSONL format so both can be exported side by side.

**VictorSuite side (recommended):** first-party **in-app screens** under something like `com.termux.zerocore.hak5` (or `…companion.hak5`), launched from the existing tools / drawer UX, the same pattern as other VictorSuite suite features. Reasons:

- Needs Android UI, encrypted prefs, and OkHttp without fighting Termux `$PREFIX` packaging.
- Can still shell out to Termux for an optional SSH client via existing process APIs or `RUN_COMMAND`.
- Keeps the authorized-use gate next to the plugin gate Don already has.

**Secondary (optional later):** a Termux-side `hak5-companion` script package for power users who prefer CLI — driven by the app or standalone. Not required for Phase 1.

**Not recommended as the only surface:** a pure “plugin manifest entry” that only launches an opaque binary. Plugins are tool-agnostic infrastructure (`PLUGINS.md`); the Pineapple client is first-party product logic with strong safety UX.

### 3.2 How it talks to devices

| Path | Use |
|---|---|
| **HTTP (OkHttp)** | Primary for Pineapple POST `/api/` + `apiToken`. Cleartext HTTP to a link-local management IP is expected on the Pineapple AP; document cleartext use and prefer staying on the management network only. |
| **SSH** | Optional admin / script path; confirm before adding a crypto SSH dependency. |
| **USB** | **Not in VictorSuite.** Plunder Bug NIC and any future Pineapple USB tether live in RootForge (D3). |
| **Deep link** | VictorSuite ↔ RootForge handoff for Plunder Bug capture. |
| **RUN_COMMAND / Termux process** | Optional `ssh` CLI or helper scripts on the VictorSuite side, if packages are installed. |

### 3.3 Reuse of existing VictorSuite / Termux pieces

- **OkHttp 4.9.3** — already in `app/build.gradle`.
- **`androidx.security:security-crypto`** — already used by `PluginConfigStore`.
- **`PluginLogger` / authorized-use gate** — pattern to copy or share for Hak5 acknowledgments and audit JSONL.
- **`RunCommandService` / `RUN_COMMAND`** — for optional Termux-side helpers (e.g. SSH) without reinventing process launching.
- **Suite deep link / "Open in RootForge"** (`docs/SUITE_PLAN.md`) — for the Plunder Bug handoff.
- **Package `com.termux`, `targetSdk` 28, `minSdk` 23** (`gradle.properties`) — keep companion code compatible. Do not raise `targetSdk` in this feature (see `SUITE_PLAN.md`: Termux exec / `$PREFIX` constraints). Anything needing a modern target (USB) goes to RootForge.

### 3.4 Suggested module sketch (documentation only — not code in this PR)

```
VictorSuite
  UI (CompanionActivity / fragments)
    → Hak5AuthGate (ack + confirm)
    → PineappleClient (OkHttp /api/, Wi-Fi/LAN)
    → Hak5AuditLog (JSONL)
    → Hak5ConfigStore (EncryptedSharedPreferences)
    → "Open in RootForge" handoff tile

RootForge (separate repo)
  → USB permission + Plunder Bug NIC detection
  → PlunderCaptureController (tcpdump)
  → pcap list / viewer
  → same-format JSONL audit log + safety gate
```

No feature classes are added in this PR.

---

## 4. Safety and ethics (prominent)

**Authorized use only.** This companion is for networks and devices Don owns or has **written permission** to test. Unauthorized interception or offensive wireless activity is illegal and out of scope.

**Required product rules:**

1. **One-time explicit gate** before the companion unlocks (in **both** apps: VictorSuite for the Pineapple, RootForge for the Plunder Bug): wording along the lines of *“I own this network/device or have written permission to test it.”* Stored acknowledgment; companion features stay locked until set. Same spirit as `acknowledgeAuthorizedUse` in the plugin framework.
2. **Big, clear warnings** before anything invasive (module start that affects clients, unmuted tap, SSH as root, etc.).
3. **Never auto-attack.** No “run playbook,” no silent chaining of offensive Pineapple modules, no background recon. Every state-changing or potentially invasive action needs **explicit per-action confirmation**.
4. **Persistent command / audit log** of API calls and capture start/stop (secrets redacted).
5. **Default to least privilege:** prefer read-only status; prefer muted Plunder Bug mode; prefer API tokens scoped by the operator on the device.
6. **No bundling of third-party attack payloads** in the APK. Modules live on the Pineapple; capture tools are user-installed Termux packages.

If a future PR tries to add auto-run offense, it should be rejected against this document.

---

## 5. Open questions for Don

Answered on 2026-10-15 and moved to **Decisions**: hardware (Tetra), Phase 1 connectivity (Wi-Fi/LAN only), app split (VictorSuite = Pineapple HTTP, RootForge = USB/Plunder Bug).

Still open:

1. What **exact firmware version** is on the Tetra? Needed before writing any module/action names into code.
2. How deep should **module automation** go in v1: read-only status plus a small allowlist of confirmed actions, or a generic "pick any module/action" console (still with per-action confirm)?
3. **API token only**, or also first-class **SSH** in Phase 2?
4. Which **must-have modules/scripts** on your Tetra should we probe first (the ones you use day-to-day)?
5. Where should the **audit log** live long-term: app-private storage only, or also exportable to Termux `$HOME` (and from RootForge) for review?
6. **Drawer placement** in VictorSuite: under existing **Tools**, a new **Hak5** section, or behind the plugin browser?
7. Are you OK with **cleartext HTTP** to `172.16.42.1:1471` on the management network (as Hak5 documents), with a warning, or do you want a TLS / SSH-only policy?
8. For the RootForge side: is capture **on the phone itself** required, or is a **linked Linux host** fallback acceptable if the phone lacks ASIX/USB-host support or root?
9. Do you want the **USB tether** enhancement scheduled at all, or parked indefinitely?

---

## 6. Assumptions and honesty flags

| Claim | Status |
|---|---|
| Pineapple management IP often `172.16.42.1:1471` | Documented by Hak5 for 6th-gen; still confirm on your unit |
| POST `/api/` with `system`/`module` + `action` | Verified in Hak5 NANO/TETRA docs |
| `apiToken` in JSON body | Verified; tokens created via API Tokens module; none shipped by default |
| Specific module/action names for “list modules”, “run script X” | **Unconfirmed** — probe firmware; do not invent |
| Hardware is a Tetra | **Confirmed by Don** (D1) |
| Exact Tetra firmware version | **Unconfirmed**, check on device |
| Mark 7 API | Different; **out of scope** |
| Plunder Bug has no control API | Verified by product behavior / docs — companion = capture host UX |
| Plunder Bug USB NIC = ASIX AX88772C | Documented by Hak5; Android driver support varies |
| Mute scripts work identically under Termux | **Uncertain** — validate Phase 3 |
| Raising `targetSdk` for USB | **Not needed**: USB work lives in RootForge (D3) |
| Pineapple USB tether works on Android | **Unverified**, deferred (D2) |

---

## 7. Out of scope for this planning PR

- Any Java/Kotlin feature implementation, layouts, or Gradle dependency bumps beyond what already exists.
- Bundling Hak5 firmware, modules, or attack scripts.
- Support for unrelated Hak5 products (Key Croc, Rubber Ducky, etc.) unless Don expands scope later.
- Merging this branch — review only.

---

## 8. Suggested acceptance criteria for a future implementation PR

- [ ] Authorized-use gate blocks all companion actions until acknowledged.
- [ ] Pineapple reachability + token auth work against Don’s real Tetra (or documented mock).
- [ ] Only allowlisted, confirmed API actions are exposed; unknowns labeled / hidden.
- [ ] Every invasive action requires a separate confirm; none auto-fire.
- [ ] Audit log records actions with redacted secrets.
- [ ] VictorSuite has no USB code; Plunder Bug tile deep-links to RootForge (or offers sideload).
- [ ] (RootForge PR) Plunder Bug UI never claims C2; capture path degrades honestly on unsupported hardware.
- [ ] `targetSdk` remains 28; no Termux exec regressions.

---

*End of plan. Comments welcome on the PR.*
