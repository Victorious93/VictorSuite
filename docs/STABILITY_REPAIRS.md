# Stability repairs after the 0.118.3.63 review

Baseline: `7e1b90593d9c5a21e314b2e9981924bfaa864b96`, version `0.118.3.63` / `125`.
Work is on a repair branch; no production release is published by this work.

## Changes

- Embedded Termux:API helper generation is now a dependency of the app variants' JNI source merge.
  Ordinary debug assembly produces and packages the helper for arm64-v8a, armeabi-v7a, x86, and
  x86_64, including all four inside the universal APK. SDK/NDK inputs participate in task invalidation.
- APK validation requires the exact embedded helper and core native library paths rather than an
  arbitrary nonzero `.so` count. The universal artifact must contain every supported architecture.
- `vstoken` excludes dotenv variants and symlink aliases, opens repository files without following
  directory/file links, applies Git ignore rules to non-Git folders, and enforces complete output
  budgets. See `AI_CODING_AGENTS.md` for platform requirements and the intentionally strict template policy.
- The terminal color test now distinguishes a combined-color sequence from one with a trailing
  empty/default reset parameter. Terminal production parsing is unchanged.
- The terminal URL assertion calls the current `TermuxUrlUtils.extractUrls()` implementation.
  The obsolete file-receiver URL test is replaced with four actual share/view URI-resolution cases.
- The vsphone test uses its own temporary XDG state directory and cleans up after itself.
- CI includes both JVM suites and the Python/shell regressions, gates their result files, and checks
  helper packaging. The signed-release job remains dependent on the build job for the same SHA.

## Local validation

| Check | Result |
|---|---|
| App JVM/Robolectric suite | 271 tests, 0 failures/errors/skips |
| Terminal-emulator JVM suite | 138 tests, 0 failures/errors/skips |
| Python helper suite | 33 tests passed, including 21 new boundary tests |
| Standalone APK probe regressions | 5 tests passed, including empty/missing-library rejection |
| vsphone classifier/approval suite | Passed, including with inherited XDG state configuration |
| Ordinary `:app:assembleDebug` | Passed; five debug APKs built |
| APK identity, targetSdk, signer, and core/helper library paths | Passed for all five APKs |
| Independent helper ZIP probe | Passed for each per-ABI APK and every universal ABI |

The baseline probes failed before the repairs: missing helper in distributed APKs, a failed terminal
fixture, context disclosure/budget violations, and an environment-dependent shell test. These repairs
were verified locally with JDK 21.0.13, Gradle 9.2.1, platform 36, build-tools 35/36, and NDK 29.0.14206865.
CI uses the existing JDK 17 configuration and must validate its own PR revision.

## Remaining verification

This is not a runtime stability sign-off. Lint could not finish because the environment blocked an
online dependency fetch and the offline cache lacked lint dependencies (including commons-io 2.2).
No blanket suppression or weakened lint policy was added. Lint remains outside the existing CI job;
provision its dependencies and triage its actual findings before making it a required release gate.

The available emulator service rejected both baseline release and CI-debug x86_64 artifacts with
`INSTALL_PARSE_FAILED_NO_CERTIFICATES`, despite successful local release signature verification.
The cause is unresolved. There is no claim that these repairs have been installed or launched there.

Still required: actual Android 14+ embedded API execution, clean install, same-signer upgrade/data
preservation, repeated cold starts, keyboard/session/recreation flows, companion hand-offs, and an
ARM64 runtime test. Diagnostics/menu enhancements from the broader review are separate follow-up work.

applicationId, targetSdk 28, signing configuration, HOME/PREFIX, external-control defaults, pairing,
certificate checks, approvals, and existing remote-command policy are preserved. Version remains
0.118.3.63 in this branch; choose a new version before publishing a subsequent production release.

Debug APKs use the committed debug certificate. They cannot update a production release signed with
the release certificate. Never uninstall an installation with real terminal data to work around that
mismatch; use a disposable device/profile for debug testing.

Victorious
