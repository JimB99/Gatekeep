# Session continuity grace is a live device-wide duration

Session continuity grace was a hardcoded one minute. It is now one Enforcement-settings duration (15 seconds to 15 minutes, default one minute), applied live on the next leave or return — not per profile, not deferred to the next sitting.

One knob is the sitting boundary (leave and lock already share it). A longer value is stricter on the session cap (the sitting continues) and easier on on-open (it stays skipped); the settings screen does not explain that trade-off. The control is labelled **Session continuity grace**; help is “How long you have to leave before a new session starts.” Overlays stay unchanged unless they already name this duration, in which case they must show the current value. No other surface shows it.

**Considered:** per-profile; apply on next leave or next sitting; allow 0 or a 1-hour ceiling; HUD remaining-away; spell out the longer/shorter trade-off in help.
