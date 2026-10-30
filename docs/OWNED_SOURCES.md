# Owned sources

VictorSuite should only point users at sources the owner (Victorious93) controls,
or at official upstream project infrastructure (Termux, proot-distro, distro mirrors).
This file lists every external source the app touched from the inherited upstream code,
and what happened to it.

## Replacements

| Where | Old | New | Why |
|---|---|---|---|
| `ZTConstantConfig.URL.ZT_GITHUB_URL` | `https://github.com/hanxinhao000/VictorSuite/releases` | `https://github.com/Victorious93/VictorSuite/releases` | Owner repo |
| `GitHubClickConfig`, `VsAboutActivity` (GitHub card) | `https://github.com/hanxinhao000/VictorSuite` | `https://github.com/Victorious93/VictorSuite` | Owner repo |
| `HTTPIP.GITHUB_VERSION` (update check) | upstream repo | `https://api.github.com/repos/Victorious93/VictorSuite/releases/latest` | Owner release channel |
| `HTTPIP.ZERO_BBS` (forum) | `https://termbbs.ixcm.org/` | `https://github.com/Victorious93/VictorSuite/issues` | Third-party forum, not owner-controlled |
| `HTTPIP.IP` (remote scripts) | — | `https://raw.githubusercontent.com/Victorious93/VictorSuite/main/remote` (unchanged, already owned) | Owner repo |

## Removed / disabled

| Feature | Old source | Action | Why |
|---|---|---|---|
| Telegram group link (main drawer) | `https://t.me/ztcommunity` | Constant deleted, view hidden (`GONE`), click handler removed | Upstream community, not owner-controlled |
| VsEngineManage add-on prompts (File browser, Key data, x86 Alpine menu items) | `https://github.com/hanxinhao000/VsEngineManage/releases/tag/0.1.20221116` | No longer opens the download page; shows the existing "not installed" message only | Third-party APK from a personal repo; no trusted replacement |
| VsEngineManage card in About | `https://github.com/hanxinhao000/VsEngineManage` | Card hidden | Same as above |
| `assets/linux/termux_toolx_2.sh` | `curl gitee.com/yudezeng/yutools/... \| bash` | Script prints "disabled" and exits 1 | Pipes a script from a personal gitee repo into bash |
| `CodeString.runMoeSh` ("Moe" Linux installer) | `curl -L gitee.com/mo2/linux/raw/2/2 \| bash` | Removed entirely (dead code) | Personal gitee mirror |

## Kept (trusted)

- `github.com/termux/*`, `termux.dev`, `wiki.termux.com`, `f-droid.org` — official Termux.
- proot-distro sources — official.
- `mirrors.tuna.tsinghua.edu.cn`, `mirrors.bfsu.edu.cn`, `mirror.nju.edu.cn` (in
  `assets/linux/termux_toolx.sh`, `assets/qemu/utqemu.sh`) — university-run mirrors of
  official Debian/Ubuntu/Termux/Alpine package repos, used as optional mirror choices. Institutional,
  not personal; packages are still signed by the distro. Can be swapped for the official
  mirrors later if wanted.
- `download.qemu.org`, `kali.org`, `blackarch.org`, `pkgs.tailscale.com`, `sourceforge.net` — official project hosts.

## Update check behaviour

`VsGitHubVersion` calls `releases/latest` without authentication. While
`Victorious93/VictorSuite` is **private**, GitHub returns 404. Any non-200 response,
network error or JSON parse failure keeps the "new version" badge hidden — no crash,
just "no update info". The badge shows only when the latest release `name` starts with
`VictorSuite` and differs from `VictorSuite-<versionName>`.

## Run your own update channel

1. Bump `versionName` in `app/build.gradle`, build a signed release APK.
2. Create a GitHub Release on `Victorious93/VictorSuite` named `VictorSuite-<versionName>`
   (e.g. `VictorSuite-0.4.0`) and attach the APK(s).
3. Mark it "latest". Clients compare the release name with their own version.

Because the repo is private, unauthenticated clients cannot see releases. Options
(none of these have been applied — owner's choice):

- **Make the repo public.** Simplest; the existing URL starts working.
- **Separate public releases repo** (e.g. `Victorious93/VictorSuite-releases`) holding only
  release assets; point `HTTPIP.GITHUB_VERSION` and `ZT_GITHUB_URL` at it.
- **GitHub Pages update JSON**: publish a small `{"name": "VictorSuite-x.y.z", "url": "..."}`
  file on a public Pages site and point `HTTPIP.GITHUB_VERSION` at it (the parser only reads `name`).

Do not embed a GitHub token in the app to read a private repo.
