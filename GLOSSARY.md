# Gatekeep

Android screen-time enforcement: profiles, limits, on-open friction, and overlays.

## Language

**On-open wait**:
The wait deterrent shown before a monitored app is allowed to run for this sitting.
_Avoid_: Open overlay, open gate (in user-facing copy; code may still say open-gate)

**Wait overlay**:
A countdown overlay used as a deterrent: on-open wait, session-limit wait, or period (daily/hourly/weekly) wait.
_Avoid_: Overlay (too broad — includes hard block and extension UI)

**Sitting**:
One stretch of monitored use, ended by leave or device pause of the session continuity grace.
_Avoid_: Session (the session *limit* is the cap inside a sitting)

**Session continuity grace**:
The device-wide duration of leave or device pause after which a new sitting starts (session cap is full again; on-open may show again). Default one minute; settings help may say “session” and “leave” in everyday English (device pause still counts).
_Avoid_: Cooldown, timeout, pause, session grace

**Period limit**:
A daily, hourly, or weekly cap. Usage survives leave.
_Avoid_: Time limit (ambiguous with session)

**Refill**:
Session app-use granted after session wait or math. Default is the session cap.
_Avoid_: Extension (extensions have quota and buttons)

**Continue**:
Period app-use granted after period wait, math, or break. Shared by those three actions.
_Avoid_: Extension, refill (refill is the session-axis grant)

**Leave**:
Home, Recents, or another app becoming the real foreground.
_Avoid_: Pause, background (those include lock, shade, and heads-up)

**Device pause**:
Screen off or lock screen. Distinct from leave: the monitored app may still be underneath.
_Avoid_: Leave, close
