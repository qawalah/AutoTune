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

## UI overhaul: visual skills, transitions and animations (asked 2026-11-04, HOLD until the owner says "execute")
Status: planned only. Part of the card-style overhaul above; do not start until told to execute.
- Use the visual design skills (`impeccable`, plus the other design skills available) to review and polish the whole app: hierarchy, spacing, typography, colour, motion and states.
- Add transitions and animations: page open and Back (`Page` in `ui/AutoTuneScreen.kt`), card appear and expand, `PinAction` changes, graph and reading updates, and loading and measuring states. Keep them short and subtle, and respect the system "remove animations" setting.
- Follow the owner's design language from the shared DevStore repo where it applies. That repo is not attached to this session, so it must be added (or its design notes pasted) before this starts.
- Constraints still hold: the UI rules in `CLAUDE.md`, the look in `DESIGN.md`, and no change to `applicationId`.
- When executed: update `DESIGN.md` (motion section), add a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.

## UI overhaul: five-tab navigation bar (asked 2026-11-04, HOLD until the owner says "execute")
Status: planned only. Part of the UI overhaul above; do not start until told to execute.
- Today the bar has 3 tabs (Tests, Tune, EQ); Live and Export open as pages over the current tab (`Tab` and `Page` in `ui/AutoTuneScreen.kt`).
- Change to 5 tabs, with **Tune in the centre**: Tests, EQ, **Tune**, Live, Settings (suggested order; the owner only fixed Tune in the centre, plus a Live tab and a Settings tab).
- Live becomes a real tab instead of a page. Settings becomes a tab that gathers the settings now spread around the app.
- Open points to settle with the owner: where Export goes (a page opened from Tests or a test's results, or inside Settings), the exact order of the other four tabs, and which settings move into the Settings tab.
- Keep: Back from a non-start tab returns to the start tab; the Tune workflow's own Back steps; each tab keeps its scroll place; widgets that open a tab (`LaunchRequest`) still land correctly; the rule that detail screens stay pages, not tabs.
- When executed: update the `Tab` enum and nav bar, tests for any changed navigation logic, `DESIGN.md`, a `CHANGELOG.md` entry, and the version bump.
