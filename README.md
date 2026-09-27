# Gatekeep

Android screen-time management: profiles, limits, schedules, friction unlocks, and a session HUD timer bar.

**Status:** Personal release builds; actively maintained. Screenshots: add PNGs under `docs/screenshots/` when capturing the policy UI and Session HUD.

---

## Why

Gatekeep enforces limits in real time on the foreground app: accessibility-driven foreground detection, a pure Kotlin rule engine, and overlay/block UX. The design prioritizes testable domain rules (`core-domain`) separate from Room and Android services.

---

## Features

- Select and categorize apps
- Daily, weekly, hourly, and per-session limits with mandatory breaks
- Multiple profiles with optional PIN lock
- Schedule windows (multiple per day)
- Pause 5/15/60 min, focus mode, emergency bypass
- Math / phrase / hold-button / password friction to continue
- Session HUD overlay (Digital Wellbeing-style bottom timer bar)
- Statistics with streaks and charts
- Home screen widget
- Backup/restore profiles (JSON)
- Strict mode and optional device admin deterrent
- Gradual limit tightening

---

## Build

Requirements: JDK 17, Android SDK 35. See **[docs/BUILD.md](docs/BUILD.md)** for signing and workspace toolchain paths.

```bash
./gradlew :core-domain:test :core-data:test :app:testDebugUnitTest
bash scripts/build_apk.sh
```

---

## Permissions

See [docs/PERMISSIONS.md](docs/PERMISSIONS.md).

---

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Scenario-level tests: [docs/test-scenarios.md](docs/test-scenarios.md).

Project conventions: `.cursor/rules/gatekeep-*.mdc`.

---

## Database upgrades

Room migrations are explicit — the app does **not** use `fallbackToDestructiveMigration()`. Recovery behavior is documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Profile JSON backups from Settings preserve configuration across a failed upgrade.

---

## Roadmap

- [ ] **Week timeline view** on the Policy → Schedules tab (visual 7-day grid; list editor remains primary)

---

## License

MIT — see [LICENSE](LICENSE).
