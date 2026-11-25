# Gatekeep

Android screen-time management: profiles, limits, schedules, friction unlocks, and a session HUD timer bar.

**Status:** Personal release builds; actively maintained. Screenshots: add PNGs under `docs/screenshots/` when capturing the policy UI and Session HUD.

---

## Why

Gatekeep enforces limits in real time on the foreground app: accessibility-driven foreground detection, a pure Kotlin rule engine, and overlay/block UX. The design prioritizes testable domain rules (`core-domain`) separate from Room and Android services.

---

## Features

- Select apps to monitor per profile
- Daily, weekly, hourly, and per-session limits with mandatory breaks (where configured)
- Multiple profiles with optional per-profile PIN lock
- Schedule windows (multiple per day) with per-window policy modes
- Pause 5/15/60 min, focus block, emergency bypass
- Math, timed wait, or app password to continue past blockers; on-open math or wait optional
- Session HUD overlay (Digital Wellbeing-style bottom timer bar)
- Statistics with streaks and charts
- Home screen widget
- Strict mode and optional device admin deterrent
- Weekly usage report (optional)

---

## Build

Requirements: JDK 17, Android SDK 36. See **[docs/BUILD.md](docs/BUILD.md)** for signing and workspace toolchain paths. Play listing (en/de/es) and graphics: **`docs/play-store/`**.

```bash
./gradlew :core-domain:test :core-data:test :app:testDebugUnitTest
bash scripts/build_apk.sh
```

---

## Permissions

See [docs/PERMISSIONS.md](docs/PERMISSIONS.md).

---

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Scenario catalog: [docs/test-scenarios.md](docs/test-scenarios.md). Instrumented process (yield gate, ship bar): [docs/instrumented-testing.md](docs/instrumented-testing.md).

Project conventions: `.cursor/rules/gatekeep-*.mdc`.

---

## Database upgrades

Room migrations are explicit — the app does **not** use `fallbackToDestructiveMigration()`. Recovery behavior is documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Roadmap

- [ ] **Week timeline view** on the Policy → Schedules tab (visual 7-day grid; list editor remains primary)

---

## License

MIT — see [LICENSE](LICENSE).
