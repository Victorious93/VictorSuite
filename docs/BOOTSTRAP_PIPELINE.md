# Bootstrap rebuild pipeline for `com.victorious.suite`

Status: **design + untested workflow**. Nothing has been built yet (see "Testing status").

## Why
Official bootstraps (`bootstrap-2026.02.22-r1+apt.android-7`, pinned in `app/build.gradle`
`downloadBootstrap(s)`) are compiled with prefix `/data/data/com.termux/files`. The prefix is
baked into binaries (bash, apt, dpkg, proot...), so under `com.victorious.suite` the shell,
`pkg`/`apt`, proot-distro and distro installers break. Bootstraps **and every package in the apt
repo** must be rebuilt for `/data/data/com.victorious.suite/files`. See `docs/APPLICATION_ID.md`.

## Verified facts (termux/termux-packages, master, checked 2026-10-20)
- `scripts/properties.sh` L467: `TERMUX_APP__PACKAGE_NAME="com.termux"` — the current variable.
  `TERMUX_APP_PACKAGE` (L468) is a *deprecated alias* derived from it. Prefix (`TERMUX__PREFIX`,
  alias `TERMUX_PREFIX`) is derived from the package name.
- `scripts/build-bootstraps.sh`: builds bootstraps **from source** for forked apps "without having
  to publish an apt repo first". Options: `--architectures`, `--add`, `--android10`, `-f`.
  Documented usage: `./scripts/run-docker.sh ./scripts/build-bootstraps.sh --architectures aarch64`.
- `scripts/generate-bootstraps.sh`: assembles bootstraps from **already published debs** in an apt
  repo. Official CI (`.github/workflows/bootstrap_archives.yml`) uses this
  (`./scripts/generate-bootstraps.sh --architectures ${{ matrix.arch }}`), matrix over arches, then
  `gh release create` on termux-packages. Recent runs take ~2 min because they only download debs —
  not representative of a from-source build.
- Docker image: `scripts/run-docker.sh` defaults `TERMUX_BUILDER_IMAGE_NAME=ghcr.io/termux/package-builder`.

## Plan
1. **Fork** `termux/termux-packages` → `Victorious93/termux-packages` (Don action).
2. **Config change** in the fork: `TERMUX_APP__PACKAGE_NAME="com.victorious.suite"` in
   `scripts/properties.sh` (one line; keep upstream history for easy rebases).
3. **Bootstraps per arch** (aarch64, arm, i686, x86_64): `build-bootstraps.sh` in docker, via the
   manual `workflow_dispatch` workflow `.github/workflows/bootstraps.yml` (copy into the fork).
   One arch per run keeps cost/disk bounded. It verifies with `strings` that `bin/bash` contains the
   new prefix and not `/data/data/com.termux/`, and emits `SHA256SUMS`. Artifacts only, no release.
4. **Artifact hosting**: Don creates a GitHub Release on the fork (e.g. tag
   `bootstrap-<date>-vs1`) and uploads the four zips + SHA256SUMS.
5. **apt repo** (needed so `pkg install` works after bootstrap): build all packages users need
   with the new prefix (`build-package.sh -a <arch> <pkg>`), then publish with
   [termux-apt-repo](https://github.com/termux/termux-apt-repo) or aptly to static hosting
   (GitHub Pages / Cloudflare R2/Pages). Sign `Release`/`InRelease` with **our own GPG key**:
   - Don generates the key offline, stores the private key + passphrase as GitHub secrets on the
     fork (e.g. `APT_SIGNING_KEY`, `APT_SIGNING_PASSPHRASE`). Never in a repo.
   - Public key must ship in the bootstrap's keyring (the `termux-keyring` package in the fork
     must add it) and the bootstrap `sources.list` must point to our repo URL
     (`TERMUX_REPO_URL`-type settings in the fork's `repo.json`/`scripts`).
   - proot-distro itself downloads distro rootfs from upstream (prefix-independent), but the
     `proot-distro` package must come from our rebuilt repo.
6. **App wiring** (`app/build.gradle`): replace the URL base and the four checksums:
   ```
   // placeholders — fill from SHA256SUMS of the real release
   url = "https://github.com/Victorious93/termux-packages/releases/download/<TAG>/bootstrap-${arch}.zip"
   aarch64: "<SHA256_AARCH64>"   arm: "<SHA256_ARM>"   i686: "<SHA256_I686>"   x86_64: "<SHA256_X86_64>"
   ```
7. **Cadence**: rebuild monthly or on upstream security fixes; rebase fork on upstream first.

## Cost
No real from-source measurement yet. Expect multiple hours and tens of GB disk per arch (NDK +
toolchain + ~60 bootstrap packages); a full apt repo is far larger. The workflow frees runner disk
and has a 6 h timeout. If Actions minutes are capped at $0 on the private fork, run it on a
self-hosted/local docker machine instead (public forks get free minutes).

## Licenses / source offer
Packages are under their own licenses (GPL, LGPL, MIT, ...); termux-packages build scripts are
Apache-2.0. Distributing binaries requires: keep each package's license files (installed under
`$PREFIX/share/doc/<pkg>`), keep the fork public or provide a written source offer, and publish
the exact fork commit + `packages/*/build.sh` sources used for each release (record the commit in
the release notes). Do not strip LICENSE/NOTICE files.

## Testing status
- Not built: the shared box had no docker, ~23 GB free disk and recent load spikes (~42), so no
  local build was attempted. To test locally: docker, ≥40 GB free, then
  `./scripts/run-docker.sh ./scripts/build-bootstraps.sh --architectures aarch64` in the fork
  checkout, and `strings bin/bash | grep com.victorious.suite`.
- Workflow is unrun.
