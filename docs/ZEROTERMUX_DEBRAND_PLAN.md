# Plan: remove ZeroTermux branding from VictorSuite (design only)

Status: **design only, no code in this PR.** Owner: Don (Tetra). Base: `origin/main` @ `0e70f9b`.
Goal: "This is not ZeroTermux. This is VictorSuite." Remove ZeroTermux/UTermux/"zt"/"Zero" branding, move every link, feed and plugin hook to VictorSuite-owned locations, and keep the GPL attribution the licences require.
`applicationId` stays `com.termux` (see #62 / #65). Termux's `RUN_COMMAND` contract stays.

## 1. Inventory (rg -i, whole repo, at 0e70f9b)

| Pattern / category | Files | Hits |
|---|---|---|
| `zerocore` (package `com.termux.zerocore`, imports, manifest, proguard) | 439 | 1462 |
| …of which Java/Kotlin files declaring `package com.termux.zerocore…` | 392 | – |
| `\bzt` (ZT* classes, `zt_` resources/keys, `ztcommand`) | 100 | 1084 |
| `ZT_` constants/keys | 77 | 618 (138 distinct `ZT_*`/`"zt_*"` identifiers) |
| `utermux` (intents `com.utermux.files.action`, `com.utermux.action.vnc`, scripts like `utqemu.sh`) | 23 | 59 |
| `zerotermux` (README, `com.zp.z_file.zerotermux.*` from z_file.aar) | 5 | 11 |
| `零` (zh strings) | 11 | 17 |
| QQ / Telegram (`qq.com`, `t.me/ztcommunity`) | 30 | 75 |
| Resource files named `*zt*`/`*zero*` (e.g. `zero_fun_ico`, `bbs_zero`) | 9 (12 repo-wide) | – |
| `strings.xml` locales with zero/zt keys | 3 (values, values-en, values-zh-rCN) | – |
| `assets/mainmenu/*/zt_menu_config.xml` | 2 | – |
| Bundled aars in `app/libs` | xh_lib, z_file, stub, avnc (+ FHttpServer, qrcodecore, zxing, termux-x11) | – |
| Shared-pref files | `ZeroEngineData`, `xinhao` | – |

Notes: `zero` alone is too noisy (wcwidth `table_zero.py`, `isZero`, etc.) – only branded hits are in scope. `com.zp.z_file.zerotermux.ZTConfig` lives **inside z_file.aar**, so it can't be renamed without rebuilding the aar.

## 2. Rename maps

| Old | New (proposed – Don decides) |
|---|---|
| `com.termux.zerocore.*` | `com.termux.victor.*` (alt: `ai.victorious.suite.*`) |
| `ZT*` classes (`ZTCommandAuth`, `ZTSocketService`, …) | `Vs*` (`VsCommandAuth`, `VsSocketService`) |
| `ZT_*` constants | `VS_*` |
| `zt_*` resources / string keys | `vs_*` |
| `zt_menu_config.xml` | `vs_menu_config.xml` (read old name as fallback for one release) |
| `zero_fun_ico`, `bbs_zero`, `moe_ico` | new VictorSuite icons (`vs_fun_ico`, …); drop anime `moe_ico` art |
| `com.utermux.files.action` / `com.utermux.action.vnc` | `com.termux.victor.action.FILES` / `.VNC` (keep old as alias for 1–2 releases) |
| `utqemu.sh` etc. | `vs-qemu.sh` |
| String values "ZeroTermux", "Zero", "零…" | "VictorSuite" in all locales |

Package renames are mechanical (git mv + sed + manifest + proguard). Manifest components referenced by FQCN from outside (shortcuts, widgets, broadcasts) need `<activity-alias>`/receiver aliases.

## 3. What must stay (licences)

- **ZeroTermux licence (checked `LICENSE.md` and upstream `hanxinhao000/ZeroTermux`): GPLv3-only**, with Apache-2.0 exception for jackpal Terminal Emulator code in `terminal-view`/`terminal-emulator`. Termux app is GPLv3 too.
- VictorSuite is a derivative → must stay **GPLv3**, keep existing copyright headers, and state it is modified (GPLv3 §5a). Removing branding is fine; removing **attribution/copyright notices is not**.
- Add `THIRD_PARTY_NOTICES.md` (or `CREDITS.md`): Termux (GPLv3/Apache parts), ZeroTermux by hanxinhao000 (GPLv3), and each aar/jar with source + licence:
  xh_lib, z_file, stub, avnc (AVNC is GPLv3), FHttpServer, qrcodecore, zxing (Apache-2.0), termux-x11 (GPLv3), Apache FtpServer/MINA/commons-* (Apache-2.0), log4j 1.2.17 (Apache-2.0, **EOL with CVEs – flag**), slf4j (MIT), jlan (GPL/LGPL – verify).
- **xh_lib / z_file / stub have no bundled source or licence in repo.** GPL requires corresponding source for binaries we ship → either locate source (hanxinhao000 repos) and document, or replace them. This is a risk item.
- README: replace "built on ZeroTermux" marketing with one neutral credits line + link to THIRD_PARTY_NOTICES.

## 4. User data & pref migration

Keys/files whose rename **breaks saved settings** if done naively:
- SharedPreferences files `ZeroEngineData`, `xinhao` and all `ZT_*`/`zt_*` keys → one-time `PrefsMigrator` on startup: copy to `victorsuite_prefs` with new keys, set `prefs_migrated_v1`, keep old file one release, then delete.
- `zt_menu_config.xml` user overrides in app files dir → read old path if new missing, rewrite.
- On-disk dirs/files with zt/zero names under `$HOME`/app data (backups, plugin dirs) → move-on-first-run; restore must still accept old backup archive names.
- External callers of `com.utermux.*` intents → keep aliases, log deprecation.
- `ZTSocketService` auth token/port prefs → migrate or users must re-pair.
Package-name change itself doesn't touch data (applicationId unchanged), but Class-name-keyed state (Serializable, WorkManager tags, saved component names in shortcuts) must be checked.

## 5. VictorSuite-owned links & pages

All URLs go into **one file**: `app/src/main/java/com/termux/victor/config/VsLinks.kt` (or `res/values/vs_links.xml` so non-coders can edit). Nothing else hard-codes a URL.

| Purpose | Current | Proposed |
|---|---|---|
| Wiki / help | `https://wiki.termux.com/` (dead) | `https://victorious93.github.io/VictorSuite/help/` (from `docs/` via GitHub Pages) |
| Forum / community (BBS, QQ, `t.me/ztcommunity`) | QQ groups, ZT Telegram | GitHub Discussions `github.com/Victorious93/VictorSuite/discussions` (or Don's own TG/Discord) |
| Source | `github.com/hanxinhao000/ZeroTermux` | `https://github.com/Victorious93/VictorSuite` |
| Issues | Termux issues | `https://github.com/Victorious93/VictorSuite/issues` |
| About page | ZT about | `…github.io/VictorSuite/about/` + in-app credits |
| Update check | `api.github.com/repos/Victorious93/VictorSuite/releases/latest` (already ok) | keep; also expose via VsLinks |
| ZeroCoreManage releases | `github.com/hanxinhao000/ZeroCoreManage` | remove or own fork/release |
| Module feed | `raw.githubusercontent.com/Victorious93/VictorSuite/main/remote/repository/main.json` | `…github.io/VictorSuite/feeds/repository.json` |
| Online scripts feed | `…/remote/on_line_sh/main.json` | `…github.io/VictorSuite/feeds/scripts.json` |
| Plugin feed | `remote/plugins` | `…github.io/VictorSuite/feeds/plugins.json` |
| Release badge (404) | broken badge | `img.shields.io/github/v/release/Victorious93/VictorSuite?include_prereleases` |
| Sister apps | `Victorious93/File-Manager`, `droidcommand-ai`, `rootforge-os`, `android-code-studio` | keep, move into VsLinks |

Third-party project links (filebrowser, swiftp, MyServer, Termux-ADB, termux RUN_COMMAND wiki) stay as credits, also moved into VsLinks.

**Hosting – Don decides:** GitHub Pages from `docs/` (free, recommended) vs a Victorious93/custom domain; community channel (Discussions vs Telegram/Discord); whether feeds served from Pages or a separate `VictorSuite-feeds` repo.

## 6. VictorSuite plugin system

- **Catalog format** `feeds/plugins.json` (same shape for `repository.json`, `scripts.json`):
  `{ "schema":1, "updated":"…", "items":[{ "id":"vs.example", "name", "version", "minApp", "type":"apk|script|module", "url", "sha256", "sig", "size", "description", "author", "license" }] }`
- **Integrity:** mandatory `sha256` check before install/run; optional detached signature (minisign/ed25519) over the feed with public key baked into the app. Key held by Don only, never in repo.
- **Intents:** replace `com.utermux.files.action` (file plug) / `com.utermux.action.vnc` with `com.termux.victor.plugin.action.*`; plugin APKs declare `<meta-data android:name="victorsuite.plugin" …>`; plugin↔app permission `com.termux.victor.permission.PLUGIN`. Old actions kept as aliases one release.
- **docs/PLUGIN_API.md**: manifest fields, intents/extras, permissions, feed submission, example plugin.
- Downloads / online scripts / sources lists all read from VsLinks feed URLs; cached with ETag; offline fallback to bundled copy.

## 7. Risks
- Huge mechanical diff (≈440 files) → conflicts with every open PR (incl. **#66**). Do the package move in one quiet window.
- Classes inside z_file/xh_lib/stub aars reference `zerotermux`/ZT names and may call back into `com.termux.zerocore` by name (reflection/manifest) – renaming can break them at runtime. Must grep aar classes before the move.
- Pref migration bugs = users lose settings/menu config.
- External automation using `com.utermux.*` intents breaks without aliases.
- GPL compliance for binary aars without source.
- No local build (disk) → rely on CI for every batch.

## 8. Overlap with open PR #66 (fix/bug-pass-oct27)
#66 touches AndroidManifest, TermuxActivity, ~20 `com.termux.zerocore` files (adapters, ADB, back/restore, ztcommand/ZTCommandAuth+ZTSocketService, dialogs, hak5, plugin, utils), assets scripts, bashrc and en/zh strings. **Merge #66 before batch 4 (package move)**; batches 1–3 can proceed.

## 9. PR batches (small, in order)
1. **Licence & credits** – THIRD_PARTY_NOTICES.md, README credits rewrite, aar licence table. No code.
2. **Links** – add VsLinks; route all URLs through it; fix dead wiki link, 404 release badge; docs/ site skeleton + GitHub Pages.
3. **Drawer fixes (Oct 27 audit)** – unhide hidden menu entries, fix broken Open Directory intent, remove private LAN IP default, ship example menu group **off**, replace hard-coded labels with strings.
4. **Package move** `com.termux.zerocore` → `com.termux.victor` (+ manifest aliases, proguard). After #66.
5. **Class/const rename** ZT*/ZT_* → Vs*/VS_*.
6. **Resources & strings** – zt_/zero resources → vs_, all locales, 零 strings, icons (zero_fun_ico, bbs_zero, placeholder/brand icons), drop moe_ico.
7. **Pref & file migration** – PrefsMigrator, menu config fallback, tests.
8. **Intents** – new actions + deprecated aliases.
9. **Plugin system** – feed schema + sha256 + signing, PLUGIN_API.md, plugin intents/permission.
10. **Feeds move** – repository/scripts/plugins to Pages; remove QQ/Telegram/ZeroCoreManage leftovers.
11. **aars** – replace or rebuild xh_lib/z_file/stub; upgrade log4j.
12. **Cleanup** – remove deprecated aliases/old prefs after one release.

## 10. Decisions only Don can make
- New package name (`com.termux.victor` vs `ai.victorious.suite`).
- Drop or keep features that depend on ZeroTermux aars (xh_lib, z_file, stub) whose source isn't in repo.
- Hosting: GitHub Pages vs own domain; community channel.
- Plugin signing key ownership; whether unsigned plugins allowed.
- How long to keep `com.utermux.*` aliases and old prefs.
- Replacement icon/art.
