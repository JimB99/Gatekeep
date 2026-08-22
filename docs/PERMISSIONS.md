# Permissions setup (sideload)

Gatekeep requires these permissions for full functionality:

| Permission | Why | How to grant |
|------------|-----|--------------|
| Usage Access | Track app usage time | Settings → Special access → Usage access → Gatekeep |
| Accessibility | Detect foreground app | Settings → Accessibility → Gatekeep |
| Display over apps | Block screen + Session HUD | Settings → Apps → Gatekeep → Display over other apps |
| Notifications | Countdown status | Allow when prompted |
| Battery optimization | Reliable background enforcement | Allow when prompted |

## OEM notes

On Samsung, Xiaomi, and OnePlus devices, also disable battery restrictions and enable autostart for Gatekeep in the manufacturer's settings app.

## Session HUD vs Recents timer

The blue timer bar in Android's Recents (app switcher) is system-only. Gatekeep shows an equivalent bar at the bottom of apps while they are open.
