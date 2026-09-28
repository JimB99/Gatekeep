# Architecture



```

app/           Compose UI, enforcement services, workers

core-domain/   Pure Kotlin rule engine (unit tested)

core-data/     Room database, repositories, DataStore

```



## Enforcement flow



1. `ForegroundMonitorAccessibilityService` detects foreground app changes

2. `EnforcementCoordinator` loads profile, limits, usage, pauses, and extension grants

3. `RuleEngine` (`core-domain`) evaluates allow/block with no Android dependencies

4. `BlockOverlayManager` shows block UI; session timer uses an ongoing notification

5. `EnforcementCoordinator` serializes evaluation with a mutex and tracks block generation to avoid stale overlay state



The coordinator maintains foreground package history, session deadlines (daily/hourly/weekly/session), grace periods, and notification copy deduplication. Cross-app instrumentation tests live under `app/src/androidTest/` (see [test-scenarios.md](test-scenarios.md)).



## Database upgrades



Room migrations are explicit — no `fallbackToDestructiveMigration()`. If upgrade fails schema validation (for example an index-name mismatch), Gatekeep logs the error, deletes `gatekeep.db` (and WAL/SHM sidecars), and opens a fresh database **once**. Local enforcement history may reset; restore profiles from Settings JSON backup.



## Battery



- Foreground app changes are event-driven via accessibility

- A 2s UsageEvents poll runs while accessibility is connected to catch package changes missed by accessibility events and keyboard/system-UI noise; poll results are debounced (~500ms) and launcher/system-UI blips within 3s of a monitored app are ignored

- Open-gate friction is suppressed for the current usage session and for 60s after passing (persisted on `session_state.openGatePassedEpochMs`)

- Notification/enforcement loop: **1s** when any active limit deadline is within 5 minutes, **30s** otherwise

- Session timer notification shows seconds only during the 1s loop; during the 30s loop it shows whole minutes (daily/hourly/weekly lines are always minute-only)

- Usage is recorded on app switch, resume detection, and break expiry — not on notification ticks

- Usage sync every 30 min via WorkManager

- Foreground service only when enforcement is enabled and accessibility is unavailable

