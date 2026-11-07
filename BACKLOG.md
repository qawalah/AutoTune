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

## Header: text aligned with the graphic, and the header comes back at the top like One UI (asked 2026-11-07, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute.
- **Align the header text with the graphic.** Today the screen's name sits at the bottom-left while the Braun graphic sits at the top-right, with a gap between them. Line them up: the name and the graphic share one row and one alignment (for example the name centred on the graphic's height, or its baseline on the graphic's bottom edge), so they read as one unit.
- **The header comes back when you scroll all the way up, as in One UI (owner's clarification of the 2.2.0 rule).**
  - It opens fully expanded only when the app is started (a fresh open, as already built) and a tab is chosen.
  - When switching between tabs, a tab may open with the header hidden (content high).
  - Scrolling back **all the way to the top** brings the full header (name and graphic) back, in every case. It does not come back part-way down the page; until the top is reached, the slim bar with the name stays.
- Keep: the `freshOpen` rule (15 minutes in the background), compact opening on tab jumps and Back, the slim bar once scrolled away, and `motionMs()` for the animation.
- When executed: `ScreenTitle`, `CollapsedHeader` and the scroll handling in `ui/Kit.kt` and `ui/AutoTuneScreen.kt` (a tab that opens compact must still be able to scroll up to the full header, so its top has to reveal the header's space); tests for any pure rule; `DESIGN.md`, a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.

## Tune becomes a tuning knob; Settings goes back to a gear (asked 2026-11-07, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute. The owner first asked for a tuning fork, then discarded it: **no fork.**
- **Tune** (today a ring with a dot and a line, which reads as a webcam) becomes a **tuning knob**: the round, ridged knob of a Braun radio, with a pointer line, in the Braun line style. A short scale of ticks around its upper half would say "tuning" (as on the T 3 and SK 4 dials). Add it as a new `Ic` entry or redraw `Ic.TUNE` in `ui/Kit.kt`.
- **Settings** (today a Braun knob with a dot scale, `Ic.GEAR`) becomes a **gear** again, drawn in the same plain geometric language: a round hub, evenly spaced square teeth, one stroke weight. The two icons must not look alike once Tune is a knob.
- Where Tune's mark appears: the round Tune key in the tab row, the Tune main keys, "Continue tuning" and "Start tuning", and, because the owner asked for "the logo", the app icon (`res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`) and the notification icon (`res/drawable/ic_stat_autotune.xml`). Settings' gear appears in the tab row and the Settings header graphic's key row stays as it is.
- Keep: the launcher icon's colours (graphite on light grey, one red element), the white-on-transparent rule for the status bar icon, readability at 20 dp. Render both icons at all their sizes, light and dark, before release.
- Open point: should the Tune home screen's speaker grille keep the word "Tune" on its centre key, or carry the knob?
- When executed: the drawings above, a render sheet for the owner, `DESIGN.md` (the mark and the icon rules), a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.

## Header: the graphic minimises into the corner, the mode key stays in the middle, the three-bar menu goes (asked 2026-11-07, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute. Works with the two header entries above (text aligned with the graphic; the header returns at the top like One UI).
- **The graphic transitions into the header.** As the screen scrolls, the tab's Braun graphic does not just vanish and let a separate slim bar take over: it shrinks and glides into the **right corner** of the slim header as a small version of itself, while the screen's name settles at the left. Scrolling back to the top grows it back out to the full graphic. Smooth, tied to the scroll position, with `motionMs()` and "Remove animations" respected (then it simply switches).
- **The Simple / Advanced switch stays in the middle of the header, always**: in the tall header and in the slim one, at the same place, so it never moves when the header changes shape.
- **The three-bar menu is removed** (the screenshot's circled button): it is not needed. Its two entries move into Settings:
  - **Export EQ files**: a row in Settings that opens the Export page over Settings (Back returns to Settings).
  - **Send diagnostics**: already in Settings under Diagnostics (Advanced); make it reachable in Simple too, or leave it in Advanced.
- Open points to settle before building: should Export EQ files also stay reachable from the EQ tab or a test's results (it is the only way in today, so it should not get harder to find)? Is Send diagnostics needed in Simple?
- Keep: the wordmark AUTOTUNE, the One UI fresh-open and scroll-to-top rules from the entries above, 48 dp touch targets, and Back from the Export page returning to where it was opened.
- When executed: `ScreenTitle`, `CollapsedHeader`, `HeaderArt` (the scale and position follow the scroll offset) in `ui/Kit.kt`; remove `MenuButton` and `LocalMenu` from `ui/AutoTuneScreen.kt` and the title rows; add the Export row to `SettingsTab` in `ui/SetupScreens.kt`; `DESIGN.md`, a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.
