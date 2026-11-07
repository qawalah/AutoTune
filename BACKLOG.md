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

## Header redesign: Braun-like with minimal graphics, name in capitals (asked 2026-11-07, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute.
- **Each tab keeps its name and its own graphic in the header space** (owner: what is there now is good). The graphics are redrawn as **Braun audio graphics**, not abstract Bauhaus shapes: drawn like the faces of Braun hi-fi (the SK 4, T 3, audio 1, TG 60 from the owner's reference board). Per tab, for example: Tests as a record-and-tonearm or tape-reel deck, EQ as the fader strip, Tune as the round speaker grille, Live as a radio tuning scale with a red needle or a slot grille of level bars, Settings as a row of small push keys and knobs. Flat, in the deck greys with the accent only on a needle, a pilot light or one key. No Samsung look beyond the one feature below.
- The app name is written in capitals: **AUTOTUNE** (the wordmark on the top line, and anywhere else the name appears as a mark).
- **The only One UI feature to keep:** the tall space at the top that shows the screen's name and pushes the content down, so the buttons are within reach of the thumb, with a slim bar taking over once scrolled.
- **When it shows (owner's clarification):** each tab opens with the tall header only when the app is first used, such as coming back from sleep or a cold start. When jumping between tabs the screen opens in its compact form (slim bar, content high), not tall.
- **Decided by the owner (2026-11-07):**
  - "From sleep" means a cold start, or returning to the app after it has been in the background for **15 minutes or more**.
  - Going **Back** to a tab counts as jumping: it opens compact.
  - When the app wakes, **every tab** shows the tall header again, not only the tab you land on.
- When executed: `ScreenTitle`, `HeaderArt` and `CollapsedHeader` in `ui/Kit.kt`; the wordmark in `ui/MeasureOverlay.kt` and `ui/AiScreen.kt`; app-level state for "fresh open" in `ui/AutoTuneScreen.kt` (reset when the app returns after a pause); tests for the pure part (when a fresh open counts); `DESIGN.md`, a `CHANGELOG.md` entry, and the version bump in `gradle.properties`.

## EQ on/off switch in Braun audio style (asked 2026-11-07, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute.
- The AutoTune EQ on/off control on the EQ tab (`PowerToggle` in `ui/Kit.kt`, a rounded slide switch) does not follow Braun audio style. Redraw it as a Braun control.
- Ideas, from the reference board and the Braun hi-fi of the 1960s: a rectangular push key that latches down when on (like the SK 4's and audio 1's rows of keys, and the latched EQ key in the tab row), with a small pilot light; or a flat slide switch with a square cap on a recessed slot (like the T 3 and TG 60 controls). Pick one with the owner.
- **Decided by the owner (2026-11-07):** the same control is used for the other switches in the app (Loud EQ, diagnostics, wallpaper colours). Still to pick: the latching push key or the flat slide switch.
- Keep: the 48dp touch target, the "AutoTune EQ" accessibility label and switch role, the accent only on the lit part, and motion that respects "Remove animations".
- When executed: update `PowerToggle` (and the Material `Switch` uses in `ui/SetupScreens.kt` and `ui/LiveTab.kt` if they should match), `DESIGN.md`, a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.

## Loud EQ renamed "Dynamic EQ" (asked 2026-11-07, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute.
- **Decided by the owner (2026-11-07):** the Loud EQ is called **Dynamic EQ** wherever the user sees it: the card on the EQ tab, the notification and its actions ("Dynamic on" / "Dynamic off"), the Quick Settings tile, the Settings and info text, the changelog and docs. The word "loud" goes from the labels.
- Keep the internal names (`LoudEq`, `LoudMode`, `dev.autotune.action.TOGGLE_LOUD`, `LOUD_ON`, `LOUD_OFF`, the tile's id), so shortcuts, routines and saved settings keep working.
- The mode names ("Follow the volume", "Follow the sound level") and the "This is loud" button are about the loudness of the music: reword them where they would read oddly under the new name (for example "This is loud" to "Set this level"), and settle the wording with the owner when building.

## EQ sliders right after the selected profile (asked 2026-11-07, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute.
- On the EQ tab the band sliders ("Adjust bands") move up to sit directly under the AutoTune EQ switch and the selected profile ("EQ in use"), above the frequency response graph.
- Order then: AutoTune EQ switch and EQ in use, the sliders, the response graph, then the rest (Advanced items, Verify key).
- **Decided by the owner (2026-11-07):** the response graph goes after the sliders.
- **Scroll cue for many bands:** with 15 bands (and any set of more than 10) the sliders do not all fit the width and the row scrolls sideways (`horizontalScroll` in `GraphicSliders`). Show that more sliders are out of view: for example a fade at the edge where more bands wait, with a small chevron, a thin position bar under the sliders, and the first scroll hinting itself once. The cue disappears when the whole set is in view or the row is scrolled to its end.
- When executed: reorder in `EqTab` and add the scroll cue to `GraphicSliders` (`ui/EqTab.kt`), `DESIGN.md` if the layout rule changes, a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.

## New bass and treble icons (asked 2026-11-07, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute.
- The icons for "Bass to taste" and "Treble to taste" (today a speaker and a curve, `Ic.SPEAKER` and `Ic.CURVE`, in `ui/ResultViews.kt` and `ui/SetupScreens.kt`) read as wrong; replace them with icons that say bass and treble.
- Ideas, drawn in the app's line style (24-unit grid, 1.8 stroke, round caps): bass as a large round woofer cone or low wave with a few wide ripples, treble as a small tweeter dome or a tight high-frequency wave. Add them to `Ic` in `ui/Kit.kt` (append, so saved state is not affected).
- **Decided by the owner (2026-11-07):** the Flavour list icons are updated too (More bass, Rock, Pop, Electronic / hip-hop, Acoustic / classical, Speech / podcasts, Low volume, Late night, As tuned): each flavour gets an icon that says what it is, not a generic curve or speaker.
- When executed: new `Ic.BASS` and `Ic.TREBLE` with their drawings, the two fields, `DESIGN.md` if the icon rules change, a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.

## Revise all app icons into one Braun design language (asked 2026-11-07, HOLD until the owner says "execute")
Status: planned only. Do not start until told to execute.
- Redraw the whole `Ic` set in `ui/Kit.kt` (tabs, actions, status, target kinds, flavours, the new bass and treble) so they read as one family in the Braun audio language: plain geometry, the shapes of Braun controls and speakers (circles, squares, slots, perforations), one stroke weight, no ornament. Today they are generic line icons drawn one by one.
- Rules for the set: 24-unit grid, one stroke weight, round caps only where the object is round, filled shapes only for knobs and pilot lights, recognisable at 20 dp, and the same drawing language as the launcher icon and the notification icon (speaker ring, round key, fader).
- Audit first: list every icon and where it is used, mark the ones that say the wrong thing, then redraw in one pass so the set is consistent. Render the whole set on one sheet in light and dark for review before release.
- Keep: append new entries to `Ic` rather than reordering; icons keep their accessibility labels at the call sites.
- When executed: the `Ic` drawings, a one-sheet render for the owner, `DESIGN.md` (the icon rules), a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.
