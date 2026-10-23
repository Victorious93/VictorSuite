# RustDesk-based custom-server remote desktop plugin — Design Plan (VictorSuite)

**Status:** design only. No implementation code is in this PR. Nothing here is built or tested yet.
**Owner:** Don Victor (GitHub [Victorious93](https://github.com/Victorious93)).
**Repo:** private `Victorious93/VictorSuite` (Termux fork, package `com.termux`, `targetSdk` **28**, GPLv3-only — see `LICENSE.md`).
**Goal of this PR:** agree on scope, licensing, architecture, and build approach for a remote desktop tool based on RustDesk that can **only** talk to servers we host ourselves, before any code is written.

All upstream facts below were read from the upstream repositories on 2026-10-20 and are pinned to these commits so the line references stay valid:

| Upstream | Commit read | License |
|---|---|---|
| [rustdesk/rustdesk](https://github.com/rustdesk/rustdesk) (client, `master`, `Cargo.toml` version `1.5.1`; latest release tag `1.5.0`, published 2026-09-30) | `53996e3c404ea5083df0775057e4149e49102084` | AGPL-3.0 ([`LICENCE`](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/LICENCE)) |
| [rustdesk/hbb_common](https://github.com/rustdesk/hbb_common) (shared config/protocol crate; git submodule at `libs/hbb_common` per the client's [`.gitmodules`](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/.gitmodules)) | `5d52530b636fb296a324260b074780cf559e0093` | (part of the AGPL client build) |
| [rustdesk/rustdesk-server](https://github.com/rustdesk/rustdesk-server) (OSS `hbbs` / `hbbr`, latest release `1.1.16`, published 2026-07-20) | `873230e35a8805de7fd057e7eb763c46f7a174ef` | AGPL-3.0 ([`LICENSE`](https://github.com/rustdesk/rustdesk-server/blob/873230e35a8805de7fd057e7eb763c46f7a174ef/LICENSE)) |
| [rustdesk/doc.rustdesk.com](https://github.com/rustdesk/doc.rustdesk.com) (official docs) | `56f9310ca2c91f8e94b6a460290d1e94f62e0482` | — |

---

## 0. What we are building (plain words)

A remote desktop app for Don's own devices that:

1. Is a **fork of RustDesk** (Flutter UI + Rust core).
2. Has **no way to use RustDesk's public servers**. It connects only to a rendezvous (ID) server and relay server that Don hosts (`hbbs` / `hbbr` from `rustdesk-server`), using that server's public key.
3. Carries its own name and icon (rebranded), so it is never confused with, or updated as, stock RustDesk.
4. Is reachable from VictorSuite, but ships as a **separate companion APK** (recommendation, §4).

Out of scope: RustDesk Server **Pro** features (custom client generator, web console, account login API on `21114`), iOS, and any change to how VictorSuite itself is licensed or signed.

This is a tool for accessing devices Don owns or is authorized to administer. Upstream's README carries the same expectation in its [Misuse Disclaimer](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/README.md).

---

## 1. How RustDesk picks a server today (why a fork is needed)

### 1.1 Hardcoded public defaults

In `hbb_common` [`src/config.rs` L117–L123](https://github.com/rustdesk/hbb_common/blob/5d52530b636fb296a324260b074780cf559e0093/src/config.rs#L117-L123):

- `RENDEZVOUS_SERVERS = ["rs-ny.rustdesk.com"]`
- `RS_PUB_KEY = "OeVuKk5n…"` (RustDesk's public server key)
- `RENDEZVOUS_PORT = 21116`, `RELAY_PORT = 21117`, `WS_RENDEZVOUS_PORT = 21118`, `WS_RELAY_PORT = 21119`

### 1.2 Resolution order

`Config::get_rendezvous_server()` / `get_rendezvous_servers()` ([L910–L958](https://github.com/rustdesk/hbb_common/blob/5d52530b636fb296a324260b074780cf559e0093/src/config.rs#L910-L958)) try, in order:

1. `EXE_RENDEZVOUS_SERVER` (set from the executable name on Windows),
2. the user option `custom-rendezvous-server`,
3. `PROD_RENDEZVOUS_SERVER` (a `RwLock<String>`, empty by default — [L70](https://github.com/rustdesk/hbb_common/blob/5d52530b636fb296a324260b074780cf559e0093/src/config.rs#L70)),
4. a server list pushed into config (`rendezvous-servers`, only when the stored serial is newer),
5. **fallback: the public `RENDEZVOUS_SERVERS`.**

So a stock client with an empty or cleared custom server silently falls back to `rs-ny.rustdesk.com`.

### 1.3 Other public endpoints in the client

- **Key fallback:** `get_key()` in [`src/common.rs` ~L2105–L2125](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/src/common.rs#L2105-L2125) falls back to `config::RS_PUB_KEY` when the `key` option is empty.
- **API server fallback:** `get_api_server_()` ([~L1240–L1260](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/src/common.rs#L1240-L1260)) returns `https://admin.rustdesk.com` when neither an API server nor a custom rendezvous server is set.
- **Update check:** `check_software_update()` ([~L1013](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/src/common.rs#L1013)) contacts RustDesk's version endpoint (comment at ~L1130 names `https://api.rustdesk.com/version/latest`) **unless** `is_custom_client()` is true. `is_custom_client()` ([~L2657](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/src/common.rs#L2657-L2659)) is simply `get_app_name() != "RustDesk"` — renaming the app turns the update check off.
- **`is_public()`** ([~L1262–L1273](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/src/common.rs#L1262-L1273)) already identifies `rustdesk.com` / `*.rustdesk.com` hosts. Useful as a guard (§3.2).
- **UI links:** the Flutter UI links to `https://rustdesk.com/`, `https://rustdesk.com/privacy.html` and docs pages (for example in `flutter/lib/mobile/pages/settings_page.dart` and `flutter/lib/common.dart`). These are links, not server traffic, but should be repointed or removed when rebranding.

### 1.4 What the official docs offer instead

The [client configuration docs](https://github.com/rustdesk/doc.rustdesk.com/blob/56f9310ca2c91f8e94b6a460290d1e94f62e0482/content/self-host/client-configuration/_index.en.md) list: the **Custom client generator (Pro only)**, manual entry of ID/Relay/API/Key in Settings → Network, import/export of a config string, and the `--config` command-line option. With OSS server only, everything except the generator is manual per-device setup, and a user can still clear the fields and land on the public server (§1.2). That is why this plan forks the client instead of just configuring it.

---

## 2. Licensing — AGPL-3.0 obligations

Both the client and the OSS server are **GNU AGPL v3** (table above). VictorSuite itself is **GPLv3-only** (`LICENSE.md`). What that means for us:

### 2.1 Obligations for the forked client (any APK we hand to anyone, including Don's own devices)

From the [AGPL-3.0 text](https://www.gnu.org/licenses/agpl-3.0.html):

| AGPL section | What we must do |
|---|---|
| §4–§5 (conveying source / modified source) | Keep upstream copyright and license notices; ship `LICENCE`; mark modified files with a prominent "modified by" notice and date (§5a); license the whole fork under AGPL-3.0 (§5c). |
| §6 (conveying object code) | Every APK must come with, or with a written offer of, the **Corresponding Source**: the exact fork commit, the `hbb_common` fork commit, build scripts, and any patches. Simplest: keep the fork in a Git repo, tag each release, and put the source URL + tag in the app's About screen and release notes. |
| §7 | We may not add further restrictions. We may decline to grant trademark rights (§7e) for our own name/logo. |
| §10 | Recipients get the same AGPL rights downstream. |
| §13 (remote network interaction) | If we **modify** a program and let others interact with it over a network, we must offer them its source. For the client this is covered by §6 source offers. For the server, the recommendation (§5) is to run **unmodified** upstream `hbbs`/`hbbr` releases, which keeps this simple; if we ever patch the server, the server must offer its source to users who connect to it. |

The fork repo **may stay private** as long as everyone who receives an APK can get the matching source. If the APK is only ever installed on Don's own devices, Don is the only recipient. If it is ever shared with anyone else, they must get the source too.

### 2.2 Why licensing pushes toward a separate APK

GPLv3 [§13](https://www.gnu.org/licenses/gpl-3.0.html#section13) allows combining GPLv3 code with AGPLv3 code, but the AGPL network-interaction requirement then applies to **the combination as a whole**. Embedding RustDesk inside the VictorSuite APK would put VictorSuite's combined binary under those terms, and every VictorSuite build would carry the AGPL source-offer duties. A separate APK keeps the two licenses in separate programs that only talk over intents (§4).

### 2.3 Name and logo

I could not find a trademark policy file in the `rustdesk/rustdesk` repository (top-level listing and `docs/` checked). Whether "RustDesk" is a registered trademark is **not verified here** (open question Q7). Regardless, the plan is to rebrand fully (§3.1): different app name, icon, and `applicationId`, plus a plain-text "Based on RustDesk (AGPL-3.0)" credit and source link in About. That avoids confusing users and also flips `is_custom_client()` (§1.3).

---

## 3. The fork — what changes

All of this is a **design**; exact patch lists come in the implementation PR.

### 3.1 Rebranding

| Item | Upstream value (source) | Fork |
|---|---|---|
| App name | `APP_NAME = "RustDesk"` ([config.rs L72](https://github.com/rustdesk/hbb_common/blob/5d52530b636fb296a324260b074780cf559e0093/src/config.rs#L72)); `Cargo.toml` `[package.metadata.bundle] name = "RustDesk"` ([L278–L280](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/Cargo.toml#L278-L280)) | Victorious name (Q1). Changing `APP_NAME` makes `is_custom_client()` true. |
| Org / config dir | `ORG = "com.carriez"` ([config.rs L57](https://github.com/rustdesk/hbb_common/blob/5d52530b636fb296a324260b074780cf559e0093/src/config.rs#L57)) | Victorious org string, so config never collides with a stock install. |
| Android `applicationId` / `namespace` | `com.carriez.flutter_hbb` ([`flutter/android/app/build.gradle` L85, L102](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/flutter/android/app/build.gradle#L85-L104)) | New id (Q1), so it installs side by side with stock RustDesk and never receives its updates. |
| Icons, strings, About | RustDesk assets and links | Victorious assets; About screen shows "Based on RustDesk", AGPL-3.0, link to fork source + tag. |

### 3.2 Custom-server-only behaviour

Goal: **there is no code path that reaches a RustDesk-operated server.**

1. **Defaults:** replace `RENDEZVOUS_SERVERS` with an empty list (or Don's server only), and replace `RS_PUB_KEY` with Don's `hbbs` public key (`id_ed25519.pub`) or empty.
2. **Build-time server baking:** set `PROD_RENDEZVOUS_SERVER` (and the key / relay) from build configuration at startup, so a freshly installed app is already pointed at Don's server with no manual setup. Values come from CI configuration (§6), not hand edits.
3. **API fallback:** change the `https://admin.rustdesk.com` fallback in `get_api_server_()` to "no API server". OSS `hbbs` has no account API (`21114` is Pro-only per the [self-host docs](https://github.com/rustdesk/doc.rustdesk.com/blob/56f9310ca2c91f8e94b6a460290d1e94f62e0482/content/self-host/_index.en.md)).
4. **Guard:** before connecting, refuse any rendezvous, relay, or API host where `is_public()` is true, and refuse to connect at all when no server is configured. Show a clear "No server configured" message instead of falling back.
5. **Settings UI:** keep the Network screen so Don can change servers, but validate input with the same guard. Option: lock the fields entirely (Q4).
6. **Update check:** off via rebranding (§1.3); confirm no other startup call goes to `*.rustdesk.com` by grepping the fork and by a network capture during testing (§7).
7. **Server-side enforcement:** run `hbbs` with a key (its default loads or generates `id_ed25519`), and run `hbbr` with the **same non-empty key**. `hbbr`'s default key is empty, which accepts clients without a matching key ([environment-variables.md](https://github.com/rustdesk/rustdesk-server/blob/873230e35a8805de7fd057e7eb763c46f7a174ef/docs/environment-variables.md)).

### 3.3 What stays upstream

Protocol, codecs, Android screen capture and input (`MainService` with `mediaProjection` foreground-service type, `InputService` accessibility service, `FloatingWindowService` — see the upstream [`AndroidManifest.xml`](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/flutter/android/app/src/main/AndroidManifest.xml)). The fork should stay a small, rebasable patch set on top of upstream.

---

## 4. Integration options with VictorSuite

| Option | How | Pros | Cons | Verdict |
|---|---|---|---|---|
| **A. Companion APK** | Separate fork repo builds its own APK; VictorSuite registers it as a plugin entry and opens it with an explicit intent / deep link (same pattern as the VictorSuite and RootForge handoff in `docs/SUITE_PLAN.md`). | Keeps AGPL out of the GPLv3-only VictorSuite APK (§2.2). Lets the fork use upstream's modern Android setup (`targetSdk` 36, `minSdk` 22) while VictorSuite stays on `targetSdk` 28. Upstream rebases stay simple. VictorSuite build time and size unaffected. | Two APKs to install and sign. | **Recommended.** |
| B. Embed in VictorSuite | Add the Flutter engine + Rust `.so` + RustDesk Android services into the VictorSuite APK. | One install. | Puts the whole VictorSuite binary under AGPL network terms (§2.2). Requires adding Flutter to a Termux build. Upstream's Android app targets SDK 36 and uses `FOREGROUND_SERVICE_MEDIA_PROJECTION`; VictorSuite is pinned to 28 for Termux exec (`docs/SUITE_PLAN.md`). Large APK growth. | Rejected. |
| C. Linux client inside a proot desktop | Build/install the Linux RustDesk fork inside a VictorSuite proot distro and run it over VictorSuite's X11. | No new APK; good for controlling other machines from a proot desktop. | Cannot capture or control the Android device itself (no MediaProjection inside proot). Untested on Termux X11. | Optional later add-on, not the main path. |
| D. `hbbs`/`hbbr` inside VictorSuite proot | Run the server binaries in a proot distro on the phone. | Handy for LAN testing. | A phone behind carrier NAT is a poor always-on server; battery and background limits. Untested. | LAN test aid only (§5). |

Plugin wiring for option A uses the existing plugin framework (`PLUGINS.md`): a manifest entry describing the companion, gated by the authorized-use acknowledgement, with launches written to the audit log. Whether a non-shell "launch an APK" entry type is needed is open question Q5, since today `PluginProcessLauncher` composes shell commands.

---

## 5. Self-hosting `hbbs` / `hbbr`

Facts from the [self-host overview](https://github.com/rustdesk/doc.rustdesk.com/blob/56f9310ca2c91f8e94b6a460290d1e94f62e0482/content/self-host/_index.en.md), the [`rustdesk-server` README](https://github.com/rustdesk/rustdesk-server/blob/873230e35a8805de7fd057e7eb763c46f7a174ef/README.md) and [Docker guide](https://github.com/rustdesk/doc.rustdesk.com/blob/56f9310ca2c91f8e94b6a460290d1e94f62e0482/content/self-host/rustdesk-server-oss/Docker/_index.en.md):

- `hbbs` is the ID/rendezvous server; `hbbr` is the relay. `cargo build --release` produces both plus `rustdesk-utils`. Prebuilt releases and a `rustdesk/rustdesk-server` Docker image exist.
- Ports: TCP `21115` (NAT type test), TCP+UDP `21116` (registration, hole punching), TCP `21117` (relay) are the **minimum**. TCP `21118`/`21119` are WebSocket, only needed for the web client / WSS. TCP `21114` is Pro-only.
- Docker Compose with `network_mode: "host"` is the documented simplest Linux setup, with `./data` mounted to keep the key pair.
- `hbbs` generates `id_ed25519` / `id_ed25519.pub` on first run; the `.pub` value is the client `Key`.
- `-r` / `RELAY-SERVERS` tells `hbbs` where the relay lives; `ALWAYS_USE_RELAY=Y` disables direct connections.
- The Docker guide warns that with WebSocket enabled, `X-Real-IP` / `X-Forwarded-For` are trusted unvalidated, so `21118`/`21119` should not be exposed directly.

**Plan:** run **unmodified** upstream server releases (keeps AGPL §13 simple), on an always-on host Don controls (Q2), with key validation on both services (§3.2 step 7), `21118`/`21119` closed unless a WSS reverse proxy is added, and the data directory backed up (losing the key pair means re-provisioning every client).

---

## 6. Build and CI (fork repo)

Upstream's Android CI job is `build-rustdesk-android` in [`.github/workflows/flutter-build.yml`](https://github.com/rustdesk/rustdesk/blob/53996e3c404ea5083df0775057e4149e49102084/.github/workflows/flutter-build.yml). As read at the pinned commit it uses:

- `ANDROID_FLUTTER_VERSION: 3.24.5`, `RUST_VERSION: 1.75`, `NDK_VERSION: r28c`, `CARGO_NDK_VERSION: 3.1.2`;
- vcpkg (via `lukka/run-vcpkg`) and `flutter/build_android_deps.sh` for native deps;
- a `generate-bridge` job (flutter_rust_bridge) that Android depends on;
- `flutter/ndk_arm64.sh` / `ndk_arm.sh` to build the Rust library, then `flutter build apk --split-per-abi` per ABI (aarch64, armv7, x86_64, …);
- a step that **switches release builds to the debug signing config** (`sed … signingConfigs.debug`), commented "temporary use debug sign config".

**Plan for the fork:**

1. New private repo, e.g. `Victorious93/<name>` (Q1), forked from `rustdesk/rustdesk`, plus a fork of `rustdesk/hbb_common` with `.gitmodules` repointed. The VictorSuite repo only gains the plugin manifest entry and docs.
2. A trimmed workflow: `generate-bridge` + `build-rustdesk-android` for **arm64 only** at first; drop desktop/iOS jobs. Debug APK as a workflow artifact on every PR.
3. Server address and public key come from **repository variables** (they are not secrets, but keeping them out of source avoids publishing infra hostnames in commit history). Nothing secret in the repo.
4. Release signing: a new keystore stored only as CI secrets, **created and approved by Don** (per the standing rule on signing keys). Upstream's debug-sign shortcut is not used for releases.
5. Each release tag records the upstream commit it is based on, for the AGPL source offer (§2.1) and for rebasing.
6. Upstream sync: periodic rebase onto upstream tags; the patch set stays small (§3).

The VictorSuite repo's own CI is unaffected.

---

## 7. Test plan (for the implementation PR)

- Fresh install with no manual setup registers with Don's `hbbs` and gets an ID.
- Clearing the server fields shows "No server configured" and makes **no** connection attempt.
- Entering `rs-ny.rustdesk.com` (or any `*.rustdesk.com`) is rejected.
- Packet capture during install, startup, idle, and a session shows **no** DNS lookups or connections to `*.rustdesk.com`.
- Control session phone ↔ desktop works both direct and through `hbbr` (`ALWAYS_USE_RELAY=Y`).
- A client with the wrong key is refused by both `hbbs` and `hbbr`.
- Installs alongside stock RustDesk without conflict; VictorSuite plugin entry launches it.
- About screen shows the AGPL notice and the correct source tag.

---

## 8. Open questions for Don

| # | Question | Default if no answer |
|---|---|---|
| Q1 | App name, `applicationId`, and fork repo name. | Hold until decided; nothing ships without it. |
| Q2 | Where does `hbbs`/`hbbr` run (VPS, home server, other)? Public DNS name? | Small Linux VPS with Docker Compose, host networking. |
| Q3 | Force relay only (`ALWAYS_USE_RELAY=Y`) or allow direct peer-to-peer? | Allow direct, relay as fallback (upstream default). |
| Q4 | Can the user edit server settings in the app, or are they locked to the baked values? | Editable, but `*.rustdesk.com` rejected. |
| Q5 | Add a "launch Android app" entry type to the plugin framework, or launch via a shell `am start` entry? | Small new entry type, reviewed in its own PR. |
| Q6 | Release signing keystore: new dedicated key, or same publisher lineage as the suite? | Needs Don's approval; no key work until then. |
| Q7 | Confirm whether RustDesk has a trademark policy that affects a fork's name. | Full rebrand regardless (§2.3). |
| Q8 | Desktop builds (Linux/Windows) of the fork too, or Android only? | Android arm64 first. |
| Q9 | Will the APK ever go to anyone other than Don? (Changes who must get the source offer.) | Assume yes and ship the source link in About. |

---

## 9. Proposed phases

1. **This PR:** design doc only.
2. **Phase 1:** fork repos, rebrand, custom-server-only patches, arm64 debug APK from CI, test plan §7 against a test `hbbs`/`hbbr`.
3. **Phase 2:** VictorSuite plugin entry + launch path (Q5).
4. **Phase 3:** signed release (after Q6 approval), production server, optional proot Linux client (option C).
