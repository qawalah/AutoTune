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

## Header fold, mode key in the middle, no menu, Tune knob and Settings gear (asked 2026-11-07): done in 2.3.0
The three held entries were built together: the name centred on the header graphic, the graphic shrinking and gliding into the slim bar's right corner with the scroll (and back at the very top, on every screen), the Simple / Advanced key fixed in the middle of the top line, the three-bar menu removed (Export EQ files in Settings for Simple and Advanced and on the EQ tab in Advanced; Send diagnostics stays in Advanced), Tune drawn as a Braun tuning knob everywhere including the app and notification icons, and Settings as a gear. The Tune speaker keeps "Tune" on its key. Open follow-ups:
- Check on the owner's phone: the feel of the fold under a finger and with a fling, and the folded opening after a tab jump.

## Flow review (9 Nov 2026): 1-3 done in 2.5.0, 4 and 6 in 2.6.0, leaner flow in 2.7.0, the rest open
- **Next mic spot starts by itself** (held, asked 9 Nov): a countdown after each spot instead of "Measure spot n"; would halve the taps in a session.
From the flow maps in `FLOW.md`. Done: Set up is a draft, the EQ check decides the next move, preview tunes are measured on the phone's EQ before keeping.
- **Level check before the stock sweep.** A short level probe as the first check on the measuring screen (reuse `levelCheck`), stopping with "turn it up/down a step" and Try again. No extra screen. (Since 2.7.0 "Check the level" is on the failed measuring screen.)
- **Route rules out of the screens.** `onEquipment` in EqTab and the `equipment` default in ApplyStep into one pure `applyRoute(...)` in TuningLogic with FlowTest cases; later split `TuneViewModel` (tune flow, measuring) into their own classes.
- **Back up and restore tests.** Settings › Data (Advanced), reusing the diagnostics zip layout and TestStore's loader.

## Palette, Tune screen, results and graphics (asked 2026-11-09): built, not released
Done (unreleased, on the working branch; see HANDOVER "Palette fix, Tune screen…"):
- **Palette: the dark colour is still not editable.** Cause: a dark ink read on no night surface, so it was dropped everywhere (and by day it was nearly the look's own ink). A dark ink at night now brings the light decks with it; the Ink field says so in one line.
- **Tune screen: remove the top part.** The Tune screen starts with the dial; other tabs keep their header.
- **Tune dial: clearer animation, never dark.** Light face and dark dots in every look; a sweep with a rim pointer goes round the circle; still with "Remove animations".
- **Test results: simplify and reduce text.** Notes are a warning icon in the display's corner; the next move is the second line of the pinned key; the other choices sit at the bottom; verdict and resonance advice moved into bubbles.
- **Animate the graphics once when shown.** The header graphics (the reels on the results page and the others) and the report card's meters move once, then stay still.
- **Show the score next to the dB score** where it is already known: results display, Tune result, EQ step, tuned panel, runs list.
Open follow-ups:
- Check on the owner's phone: the dial's sweep, the arrival movements, the palette at night.
- The Tests list rows show no score: it is not worked out for tests that are not open, and computing it there would be new work.

## Results, runs, palette and animation fixes (asked 2026-11-09, round 2): built, not released
Done on the working branch (see HANDOVER "Results, runs, palette and graphics, round 2"):
- **Graphics play twice.** Done. The header graphics, the Tune dial, the report card's meters and the charts play their movement twice (a quick return between), then stay still; still with "Remove animations".
- **Replay on pull.** Done. Pulling a screen down past its top plays its graphics again, twice.
- **Tune dial pulses inside out.** Done. A pulse from the centre out to the rim replaces the sweep round the rim; twice, and again on a pull.
- **Palette: the dark colour.** Done. Cause: by day a dark ink never reached Classic's dark body and was as dark as the decks' own ink, so nothing changed and nothing said why (2.9.0 only acted at night). The ink is now the text everywhere: a surface it cannot read on takes its light (or dark) version by day and night, and the Ink field says when the pick looks the same as the look's ink.
- **Score beside every reading.** Done. The latest, the stock and the best each show their score box where it is already worked out (the stock's from the stock review's report card); no box where none is known. The display takes two lines when the boxes would not fit.
- **Remove the redundant line.** Done. The "x dB closer to the target than stock…" line is gone from the results display; its words are in the bubble beside the warning icon.
- **Runs list alignment.** Done. Fixed columns: tick, name and date, score, dB, delete.
- **Test details: graphics at their size, expand, animate.** Done. The results page opens with its header tall; graphics play when they come into view (they used to play on composition, before they were scrolled to); charts rise into place; the full-screen chart is a sheet over the app in the same window instead of a dialog window.
Open follow-ups:
- Check on the owner's phone: the pull gesture with One UI's overscroll, the full-screen chart, the dark ink by day and at night.
- CLAUDE.md still says every result opens with a one-line headline; the owner asked for it to go. Update CLAUDE.md if the owner agrees.

## Gradient edge default (asked 2026-11-09): done, not released
Done on the working branch (see HANDOVER "Gradient edge default"):
- **Gradient edge's default palette is the owner's setting.** Done. Body Warm white, Deck White, Ink Slate, Accent Braun red, Hairline Aluminium; the palette editor shows those names, a colour you save wins, Reset palette returns to them. "As the look" still means the look's own colours.
- **Gradient edge is the app's default look.** Done. A fresh install, and a saved look that is missing, retired or unknown, opens Gradient edge; a look saved by name (Classic, Graphite, Black, White) stays.
Open follow-ups:
- CLAUDE.md still says the look is `Look.BRAUN`, "the default" (UI rules). Update it if the owner agrees.
- Check on the owner's phone: a fresh install, and the palette at night with Light or dark on System.

## Tablet as a pretend home theatre (asked 2026-11-10): done in 2.15.0
A test mode for a phone or tablet used as the TV (Settings › Advanced › "Use this device as the TV"), so the whole 5.1 / 7.1 flow can be walked without a TV.
- In Advanced on the TV screen: a "Pretend" drop-down (off, 2.1, 3.1, 5.1, 7.1). The device then answers the phone with that layout and plays every speaker's test sound through its own two speakers.
- Always show what the system really is, on both screens: for example "pretend 5.1 · really this tablet's speakers, 2.0". The phone's result carries the same line, and such a session is never kept as a stock reference.
- Readings from a pretend layout are for trying the flow only: levels and distances will all come out the same.

## Levels, distances and EQ as three separate tests (asked 2026-11-10): roadmap
2.15.0 split the home theatre into "Set the levels" and "Test the system", but they are still tied: the system test re-reports levels and distances, and a level spread sends the key back to the level test. Make them three independent tests:
- **Levels** (its own entry, its own saved result): pink noise per speaker with the live meter, as now. Its result is the trims per speaker, kept on its own and shown on its own page or deck.
- **EQ** (its own entry, its own saved result): only the whole system playing together (no per-speaker sweeps), several seats averaged, fitted to one EQ for every speaker from its own stock, then verified with the EQ on. It never judges or mentions levels.
- Neither test sends the user to the other; each can be run alone, in any order, as often as wanted.
- **Distances** (decided 2026-11-10: a third test of its own, so it is not repeated with every level or EQ run): one short sweep per speaker after one set of start beeps, giving each speaker's distance against front left, the sub's measured delay included. Its own entry and saved result; run once at setup and again only when a speaker moves. The EQ does not need distances, but wrong ones make the sub and mains cancel, so the order at setup is levels, distances, then EQ.
- Keep the TV/PC link messages (`noise`, `pass`) or add a lighter `system` clip with just the all-together sweep; the Dolby assets follow.
