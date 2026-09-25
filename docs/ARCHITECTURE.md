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
4. `BlockOverlayManager` shows block UI; session timer uses an ongoing notification

## Battery

- Foreground app changes are event-driven via accessibility
- A 2s UsageEvents poll runs while accessibility is connected to catch package changes missed by accessibility events and keyboard/system-UI noise
- Notification/enforcement loop: **1s** when any active limit deadline is within 5 minutes, **30s** otherwise
- Session timer notification shows seconds only during the 1s loop; during the 30s loop it shows whole minutes (daily/hourly/weekly lines are always minute-only)
- Usage is recorded on app switch, resume detection, and break expiry — not on notification ticks
- Usage sync every 30 min via WorkManager
- Foreground service only when enforcement is enabled and accessibility is unavailable
