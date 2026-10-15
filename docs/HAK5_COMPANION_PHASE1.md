# Hak5 Companion — Phase 1 implementation notes

Implements Phase 1 (+ the Phase 4 safety gate that ships with it) of
[HAK5_COMPANION_PLAN.md](../docs/HAK5_COMPANION_PLAN.md) (branch `docs/hak5-companion-plan`).

## Delivered

| Plan item | Code |
|---|---|
| Authorized-use gate | `Hak5AuthGate` + UI in `Hak5CompanionActivity` |
| Wi-Fi/LAN discovery (default `172.16.42.1:1471`) | `PineappleClient.discover` |
| Firmware detect + limited mode | `FirmwareProbe` / `CapabilityMap` |
| Connection/status screen | `Hak5CompanionActivity` + drawer entry |
| Plunder Bug → RootForge tile | `RootForgeHandoff` (no USB in VictorSuite) |
| Audit log JSONL + secret redaction | `Hak5AuditLog` |
| Per-action confirm scaffold | `Hak5ActionConfirm` (Phase 2 wires real actions) |
| Encrypted host/token prefs | `Hak5ConfigStore` |

## Package

`com.termux.zerocore.hak5` — first-party in-app screens, OkHttp, `targetSdk` 28 unchanged.

## Not in Phase 1

- Authenticated module run / script runner (Phase 2)
- USB tether or Plunder Bug capture (RootForge / later)
- Mark 7 API
