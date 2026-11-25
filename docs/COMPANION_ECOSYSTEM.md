# VictorSuite companion ecosystem

VictorSuite can work with seven optional companion apps, which are Victorious forks of the Termux plugin apps.
**None of them is required.** VictorSuite works fully without them, and each companion is its own app.

| Companion | Fork repo | Upstream | Package id we look for | Shares VictorSuite's user id |
|---|---|---|---|---|
| VictorX11 | `Victorious93/VictorX11` | termux/termux-x11 | `com.termux.x11` | No |
| VictorGUI | `Victorious93/VictorGUI` | termux/termux-gui | `com.termux.gui` | Yes |
| VictorFloat | `Victorious93/VictorFloat` | termux/termux-float | `com.termux.window` | Yes |
| VictorWidget | `Victorious93/VictorWidget` | termux/termux-widget | `com.termux.widget` | Yes |
| VictorBoot | `Victorious93/VictorBoot` | termux/termux-boot | `com.termux.boot` | Yes |
| VictorStyling | `Victorious93/VictorStyling` | termux/termux-styling | `com.termux.styling` | Yes |
| VictorAPI | `Victorious93/VictorAPI` | termux/termux-api | `com.termux.api` | Yes |

VictorTasker isn't on this list. It's built into DroidCommand AI (`core-tasker`) instead of being a Termux:Tasker fork.
It still runs scripts from the Termux:Tasker folder `~/.termux/tasker/` through VictorSuite's `RUN_COMMAND`.

## What's in this repo

- `app/src/main/java/com/termux/victor/companions/CompanionCatalog.kt` lists the seven companions.
- `CompanionDetector` tells, for each companion, whether it's **not installed**, **ready** (same signing key, so the shared-user plugin link works), **signed with a different key**, or **installed standalone** (X11 doesn't need the shared user).
- `AndroidPackageLookup` is the PackageManager-backed lookup.
- `app/src/test/java/com/termux/victor/companions/CompanionDetectorTest.kt` holds JVM unit tests run by CI.

This PR doesn't add a screen. The status logic is meant to be shown later from the main menu or Settings.
That wiring is left out on purpose so it doesn't collide with the in-flight terminals and shell-broker work (draft PR #114 touches `MainMenuConfig`).

## Signing

The Termux plugin model needs a companion that shares `sharedUserId="com.termux"` to be signed with **the same key as VictorSuite**.
A companion signed with another key shows up as *signed with a different key*, and Android refuses to install it next to VictorSuite.
Keys stay out of the repos, as GitHub Actions secrets.

## Status

- None of the seven fork repos existed under `Victorious93` when this was written (Nov 24, 2026). The owner creates and renames the forks.
- The package ids above are the upstream ids. If a fork renames its id, add the new id **first** in that companion's `packageIds`.
- Not yet tested on a device.
