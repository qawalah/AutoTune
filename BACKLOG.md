# AutoTune backlog

## Phone-mic calibration from the USB mic (asked 2026-10-12)
Make the phone's own mic usable for tuning by calibrating it against the calibrated USB mic.
- Flow: mic stand at the listening spot. Measure the same sweep with the USB mic (calibration applied), then with the phone held exactly where the USB capsule was, pointing the same way. Repeat at 2-3 spots and average.
- Correction = USB response - phone response, 1/6-octave smoothed, level-matched at 250 Hz-2.5 kHz, saved as a calibration file for "Phone microphone" (per phone model and orientation), applied automatically when no USB mic is plugged in.
- Needs the UNPROCESSED source (no AGC or noise suppression), otherwise the curve changes with level. Check that two consecutive phone-mic runs agree within 1 dB before saving.
- Expected accuracy about +/-1-2 dB to roughly 10 kHz; above that the phone mic is directional and depends on how it is held. Say so in the app.

## UI overhaul: card-style sections (asked 2026-11-04, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute.
- Group each section of a screen in its own Material 3 card, so sections are told apart at a glance (summary, `PinAction`, other choices, Details, graph).
- Keep the existing UI rules: drop-downs for choices, `Ic` line icons, Barlow / Barlow Condensed, tabular figures, run sheet and chart paper, pages over the current tab, minimal scrolling and taps.
- Open points to settle with the owner before building: which screens go first, card elevation vs outline, and how this fits the "dyno sheet" look in `DESIGN.md`.
- When executed: add a shared card component in `ui/Kit.kt`, update `DESIGN.md`, add a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.
