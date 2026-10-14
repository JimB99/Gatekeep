# Period wait, math, and break are a repeating toll

Period wait/math/break grant **continue** app-use when the overlay finishes, then the same overlay can return when that chunk runs out, for the rest of the hour/day/week. Only period hard block (and Limit + extend after quota is gone) is a real maximum.

We chose a repeating tax over “one continue then hard block” because Jim wants those actions to still allow time after a period cap, not to collapse into leftover hard block. Wait and break stay distinct on the clock (leave restarts wait; break deadline keeps running). Continue inherits the session cap by default so a daily cap does not silently double itself.

Silent wait/break length 0 is auto-refill of continue forever — a deliberate footgun equivalent to “the number is only a timer for the next free chunk,” not notify-only (which skips the chunk entirely).
