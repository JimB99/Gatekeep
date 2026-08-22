# Architecture

```
app/           Compose UI, enforcement services, workers
core-domain/   Pure Kotlin rule engine (unit tested)
core-data/     Room database, repositories, DataStore
```

## Enforcement flow

1. `ForegroundMonitorAccessibilityService` detects foreground app changes
2. `EnforcementCoordinator` loads profile, limits, usage, pauses
3. `RuleEngine` (core-domain) evaluates allow/block
4. `SessionHudOverlayManager` or `BlockOverlayManager` shows UI

## Battery

- Event-driven via accessibility (no polling loop)
- Usage sync every 30 min via WorkManager
- HUD updates throttled to 10s per app
- Foreground service only when enforcement enabled
