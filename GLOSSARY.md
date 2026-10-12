# Gatekeep

Android screen-time enforcement: profiles, limits, on-open friction, and overlays.

## Language

**On-open wait**:
The wait deterrent shown before a monitored app is allowed to run for this session.
_Avoid_: Open overlay, open gate (in user-facing copy; code may still say open-gate)

**Wait overlay**:
A countdown overlay used as a deterrent: on-open wait, session-limit wait, or period (daily/hourly) wait.
_Avoid_: Overlay (too broad — includes hard block and extension UI)

**Session continuity grace**:
One minute away from a monitored app after on-open wait has already been passed, during which on-open friction is not shown again.
_Avoid_: Cooldown, timeout, pause

**Leave**:
Home, Recents, or another app becoming the real foreground.
_Avoid_: Pause, background (those include lock, shade, and heads-up)

**Device pause**:
Screen off or lock screen. Distinct from leave: the monitored app may still be underneath.
_Avoid_: Leave, close
