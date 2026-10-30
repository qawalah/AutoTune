# Design

World: a studio instrument at night. Graphite-blue surfaces, one signal amber, measurements in cool tones.

- Amber = what the user acts on (their EQ, the primary action). Cool colours = what was measured. Grey = stock reference.
- Type: IBM Plex Sans for interface text, IBM Plex Mono only for readings (frequencies, dB, scores).
- Structure: bottom navigation (Tests, Tune, EQ), everything else in the three-bar menu. Tuning is a step-by-step workflow with a step header (dots, "Step n of 6", close). Flat sections separated by space and hairlines; filled panels only for instruments (graph, meter, measure control).
- Memorable element: the response graph with the amber EQ curve, the pulsating Tune button, and the circular Measure control whose ring is the sweep progress.
- Choices are always drop-downs (current value in a field, options in a menu), never pills or segmented buttons. Buttons and menu items carry a line icon from the app's own set (Kit.kt `Ic`).
- Motion: only in response to action (ring progress, panel expand), plus the Tune button's slow pulse while it waits. Respect remove-animations.
