# Fleet theme client (VictorSuite)

VictorSuite reads the fleet palette that VictorLaunch publishes. It never writes it and never
offers a second picker. Contract: [`FLEET_THEME.md`](https://github.com/Victorious93/victorlaunch/blob/main/docs/specs/FLEET_THEME.md)
(schemaVersion 1).

## What is themed

View/XML chrome only:

| Surface | How |
|---|---|
| Left drawer (`layout_menu_all.xml`) and right drawer (`include_right_menu.xml`) | `TermuxActivity` → `FleetThemeBinding`, re-applied whenever a drawer opens |
| `ZtSettingsActivity`, `VictorSuiteSettingsActivity` | whole content view |
| Stock `SettingsActivity` | action bar only (background `surface`, title `onBackground`); preference rows keep the app theme |

Terminal colours (`colors.properties`, Monet background, extra keys) are **not** touched.

The XML uses fixed colours, so `FleetThemeRoleMap` maps each built-in colour to a role:

| Built-in colour | Role | Palette key |
|---|---|---|
| `#2B2B2B` page/drawer background | `background` | `background` |
| `#55000000` cards | `surfaceVariant` | `tile` |
| `#FFFFFF` text | `onBackground` | `onBackground` |
| `#48BAF3` link text | `primary` | `primary` |
| `#9E9E9E` secondary text | `onSurfaceVariant` | `muted` |

Any other colour (status red, icons) is left alone. A missing or invalid key keeps that view's
built-in colour; it is never replaced by another key.

## Trust

1. `PackageManager.resolveContentProvider("com.victorious.launch.theme")` must be owned by
   `com.victorious.launch`.
2. That package must have exactly one signer whose SHA-256 is a **current** (or, once published,
   **next**) pin and not **revoked** (`FleetThemePins`, values copied from the spec).
3. Query, then repeat 1–2 so a package swapped in mid-query is not trusted.
4. `FleetThemeParser` rejects `schemaVersion != 1` (column or JSON), malformed JSON and wrong types.

Any failure, including a `SecurityException` from the provider, means the built-in theme.

## Live updates

- `ContentObserver` on `content://com.victorious.launch.theme/current`.
- Runtime-registered, **exported** receiver for `com.victorious.launch.action.FLEET_THEME_CHANGED`
  (the spec sends it explicitly to our package from another UID, and defines no sender permission
  usable across signers). It is only a wake-up: extras are ignored, the client re-queries and
  re-verifies. On API 34+ a broadcast whose platform-reported sender is not `com.victorious.launch`
  is dropped.
- Both are registered only while a themed screen is started.

## Prerequisite on VictorLaunch

`com.termux` is **not** in the spec's "Pinned theme consumers" table, so until Don adds
VictorSuite's package + cert to `FleetThemeConsumers` (or uses *Trust installed fleet share apps*),
the provider answers with `SecurityException` and VictorSuite keeps its built-in theme.
