# AutoTune backlog

## Phone-mic calibration from the USB mic (asked 2026-10-12)
Make the phone's own mic usable for tuning by calibrating it against the calibrated USB mic.
- Flow: mic stand at the listening spot. Measure the same sweep with the USB mic (calibration applied), then with the phone held exactly where the USB capsule was, pointing the same way. Repeat at 2-3 spots and average.
- Correction = USB response - phone response, 1/6-octave smoothed, level-matched at 250 Hz-2.5 kHz, saved as a calibration file for "Phone microphone" (per phone model and orientation), applied automatically when no USB mic is plugged in.
- Needs the UNPROCESSED source (no AGC or noise suppression), otherwise the curve changes with level. Check that two consecutive phone-mic runs agree within 1 dB before saving.
- Expected accuracy about +/-1-2 dB to roughly 10 kHz; above that the phone mic is directional and depends on how it is held. Say so in the app.

## UI overhaul (asked 2026-11-04): done in 1.21.0
Cards, Simple / Advanced (Simple default, app-wide), five tabs with Tune in the centre, stacked actions with a Suggested pinned action, motion, and the Navy (DevStore) look. Open follow-ups:
- Check the motion and the raised Tune disc on the owner's phone.
- Go through EqTab, TestsTab and the AI screen for more Simple wording; so far mostly explanations moved to Advanced.

## Braun header, latching key, Dynamic EQ, EQ order, icons (asked 2026-11-07): done in 2.2.0
The six held items were built together: AUTOTUNE wordmark and Braun audio header graphics, the tall header only on a fresh open (cold start or 15 minutes in the background, every tab once), the latching push key for every on/off setting, Loud EQ renamed Dynamic EQ (internal names kept), sliders before the graph with a scroll cue for 15 bands, new bass, treble and flavour icons, and the whole icon set redrawn as one family. Open follow-ups:
- Check on the owner's phone: the fresh-open rule after real sleep, the latching key's feel, and whether the 15-band cue is seen.
- The first scroll hinting itself once (from the slider item) was not built; the fade, chevron and bar carry the cue.

## Header: text aligned with the graphic, and the header comes back on scroll (asked 2026-11-07, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute.
- **Align the header text with the graphic.** Today the screen's name sits at the bottom-left while the Braun graphic sits at the top-right, with a gap between them. Line them up: the name and the graphic share one row and one alignment (for example the name centred on the graphic's height, or its baseline on the graphic's bottom edge), so they read as one unit.
- **The header is always reachable by scrolling (owner's clarification of the 2.2.0 rule).**
  - It opens fully expanded only when the app is started (a fresh open, as already built) and a tab is chosen.
  - When switching between tabs, a tab may open with the header hidden (content high).
  - Whenever the user scrolls, the header (name and graphic) re-appears. It is never locked away, whatever way the tab was opened.
- Open points to settle before building: does it re-appear on any scroll up, even in the middle of a long page (the "enter always" behaviour), or only when the page is scrolled back to its top? Does it come back full (tall, with the graphic) or as the slim bar first and full at the top?
- Keep: the `freshOpen` rule (15 minutes in the background), compact opening on tab jumps and Back, the slim bar once scrolled away, and `motionMs()` for the animation.
- When executed: `ScreenTitle`, `CollapsedHeader` and the scroll handling in `ui/Kit.kt` and `ui/AutoTuneScreen.kt`; tests for any pure scroll-direction rule; `DESIGN.md`, a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.

## The logo becomes a tuning fork (asked 2026-11-07, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute.
- The Tune mark (today a ring with a dot and a line, which reads as a webcam) becomes a **tuning fork**, in the Braun line style: two straight tines joined by a round bend, a stem, and the accent on the foot or the tip of one tine (the sketch "A" from the icon options).
- Everywhere the mark appears: `Ic.TUNE` in `ui/Kit.kt` (the round Tune key in the tab row, the Tune main keys, "Continue tuning" and "Start tuning"), the app icon (`res/drawable/ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`), and the notification icon (`res/drawable/ic_stat_autotune.xml`). The owner asked for "the logo", so the app and notification icons follow the tab key.
- Keep: the launcher icon's colours (graphite on light grey, one red element), the white-on-transparent rule for the status bar icon, and the readability at 20 dp. Render the new mark at all its sizes in light and dark before release.
- Open point: should the Tune home screen's speaker grille (the big circle with the round Tune key) keep a plain "Tune" word on its key, or carry the fork too?
- When executed: the three drawings above, a render sheet for the owner, `DESIGN.md` (the mark), a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.
