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

## Levels, distances and EQ as three separate tests (asked 2026-11-10): done in 2.16.0
2.15.0 split the home theatre into "Set the levels" and "Test the system", but they are still tied: the system test re-reports levels and distances, and a level spread sends the key back to the level test. Make them three independent tests:
- **Levels** (its own entry, its own saved result): pink noise per speaker with the live meter, as now. Its result is the trims per speaker, kept on its own and shown on its own page or deck.
- **EQ** (its own entry, its own saved result): only the whole system playing together (no per-speaker sweeps), several seats averaged, fitted to one EQ for every speaker from its own stock, then verified with the EQ on. It never judges or mentions levels.
- Neither test sends the user to the other; each can be run alone, in any order, as often as wanted.
- **Distances** (decided 2026-11-10: a third test of its own, so it is not repeated with every level or EQ run): one short sweep per speaker after one set of start beeps, giving each speaker's distance against front left, the sub's measured delay included. Its own entry and saved result; run once at setup and again only when a speaker moves. The EQ does not need distances, but wrong ones make the sub and mains cancel, so the order at setup is levels, distances, then EQ.
- Keep the TV/PC link messages (`noise`, `pass`) or add a lighter `system` clip with just the all-together sweep; the Dolby assets follow.

## The TV's EQ on an Android box is stereo (seen 2026-11-10, owner's box over HDMI): done in 2.15.1 and 2.16.0, but the PCM setting
Fixed in 2.15.1 so it no longer fails, but what it can do is limited, and the app should say so:
- Android mixes app sound in stereo before HDMI, so AutoTune's EQ on a TV or box is one stereo EQ (left and right), never one per home-theatre speaker. With the one-EQ-for-every-speaker test that matches; per-speaker EQ belongs in the receiver or soundbar.
- Dolby or DTS passed straight through (most streaming apps, and our own Dolby test clips) skips the EQ entirely. Show this on the TV and the phone when the EQ is on and the output is Dolby: "Applies to sound the box decodes itself; Dolby and DTS from apps pass by it."
- Verify with a measurement: the system test once with the TV's EQ held and once with it on, PCM output, to prove the EQ reaches the speakers (like the phone's EQ check). Offer it from "Use the EQ on the TV".
- Open: check whether the box offers a setting to decode to PCM (stereo or multichannel) and say what that trades (surround over ARC, which carries only two raw channels).

## Home theatre: named tests and saved runs (asked 2026-11-10): done in 2.16.0
The home-theatre page keeps only the stock and the latest pass in `filesDir/theatre/`; there is no name and nothing to look back at, unlike the car tests.
- **Name the test** when it starts ("Living room 5.1"), with the layout and the TV or PC it ran on; several home theatres side by side, listed on the Tests tab with the car tests (home icon).
- **Every run saved** (levels, distances, EQ) with its date, what was applied (levels set, distances, the EQ in use and where: TV, PC, or entered on the receiver) and a note, like a car test's runs. Nothing measured is silently lost.
- **Compare runs**: overlay the system response of any runs against the stock, the score beside each, as the results page does.
- Reuse `TestStore`'s ideas (a versioned text file per test) or extend it with a home-theatre kind; keep loading the current `stock.txt` / `latest.txt` as the first test's runs.

## Home theatre: the subwoofer on its own is never heard (seen 2026-11-10, owner's Android box, Dolby Digital 5.1, 2.1 layout): partly done in 2.16.0
The phone waits for the subwoofer's own sweep and it does not come through (or is not recognised). To find out first, from a diagnostics zip of that test (Settings › Advanced › Send diagnostics: the `theatre_speaker` / `theatre_pass` events and the recordings):
- Whether the soundbar plays the Dolby LFE channel at all when nothing else plays (some soundbars mute or bass-manage the sub, or wake it only on programme bass).
- Whether it is played but read as "too quiet" (the sub's sweep is 10 dB down on the LFE channel, and the 25 dB rule against the mains may drop it), or arrives late (wireless subs add tens of milliseconds) past the window read.
- Whether the LFE channel needs content below 120 Hz only, with the sweep starting lower (20 Hz) and running longer in that range.
2.16.0: the sub gets 10 dB more room before it counts as not heard, the note says what to check on the soundbar, and the passes are one clip, so the phone never waits with no end. Open until a diagnostics zip shows the cause: a sub-specific clip (low sweep or band noise on LFE and, as a fallback, bass on the fronts for soundbars that route it to the sub).

## TV screen: the phone's graphics while testing, and the result in full (asked 2026-11-10): done in 2.16.0, but the sweep's frequency scale
Seen on the owner's Android box: during and after a test the TV shows the room, one headline ("no sound from subwoofer"), the phone's name and lots of empty space.
- **While testing**, the same graphics as the phone's measuring screen, filling the screen: the live level meter (the mic's level, sent from the phone a few times a second), the waveform as it is recorded, the sweep's frequency running along a scale, and for the level test the large "+1.5 dB, turn it up" reading with its meter needle. The room stays, the speaker playing lit with its rings.
- **After a test**, the result summary as the phone shows it: the readings (levels spread, distances, from target, heard), the headline, each speaker's change under it in the room, the system response chart against the target, and the next step. Notes (a speaker not heard, the EQ refused) as one line each.
- The same arrival movements and "plays twice" animations as the phone (header graphics, meters, charts rising), respecting "Remove animations".
- Link: the phone sends a light `live` message (level, a short downsampled waveform, the status) about 10 times a second while recording, and a fuller `show` (readings, chart points at third-octave, notes, next step) after each test. Keep it small enough for a slow Wi-Fi.
- Lay it out for 16:9 at a distance: big readings, the room on the left, meters and the chart on the right, nothing smaller than the TV's body text.

## Home theatre page: owner's notes (2026-11-10): done in 2.17.0, but the checks list on the TV and PC
- **Each test's key inside its own card.** "Set the levels" sits under the Levels card as a separate row; put it inside the card (Levels, Distances, EQ each with its own keys in the same section).
- **Managing home theatres on its own page.** The home-theatre drop-down with New home theatre, Rename and Delete this home theatre sits at the top of the test page and throws the owner off; move the managing to a different page.
- **Bug: a PC showed the TV's results.** Connecting to the Windows PC opened the home theatre last used with the Android TV, so its levels, distances and EQ showed as the PC's. Each TV or PC must open its own home theatre (by the device it ran on), never another device's results.
- **EQ on/off as a big, pronounced key.** The small "tv eq on / off" line with a dot under the room is too weak; make the TV's or PC's EQ state a big on/off button you can't miss.
- **Edit the EQ on the Windows app.** The PC can only switch the phone's EQ on or off today; let it change the EQ itself (bands, gains, preamp) on the PC.
- **Speaker layout on the Windows app.** The PC offers no way to choose the speaker type or count (2.0, 2.1, 5.1, 7.1); it is stereo only today. Let it pick the layout (and its output's channels, e.g. HDMI to a receiver).
- **Bug: "Open APO configuration" did nothing**, and the PC said Equalizer APO is "not on this output". Find why the button opens nothing (Configurator path, elevation) and why the chosen output reads as not set up in APO (device matching against APO's registry), and make the fix one step from the PC app. (2.17.0: the Configurator opens elevated from its install folder and says why when it cannot; the PC lists the outputs Equalizer APO is already on, with one key to switch to it. Not changed: how an output is matched to its device, until a PC shows a mismatch.)
- **Windows app in landscape.** The PC window is laid out tall like a phone; make it a landscape desktop window that uses the width (columns side by side).
- **No nagging about levels.** The page keeps pushing the level test ("Set the levels again" as the pinned key, level notes). Just show the three tests and let the owner choose; no suggested order, no pushing back to levels.
- **TV and Windows show the phone's measuring screen.** While a test runs, the TV and the PC must show the same visuals and movement as the phone's measuring screen (owner's screenshot, stock reading): the large dBFS mic level, the sweep's frequency slider running 20 Hz to 20 kHz with the red needle and "Sweeping 734 Hz", the waveform, the too quiet / good / clipping level slider, and the checks list. Today the TV's live screen does not match, and the PC has none. (2.17.0: all but the checks list, which the phone keeps for its own car measurements.)

## Owner's notes, round 2 (2026-11-11): done in 2.18.0
- **Runs list: EQ runs only.** Details › Runs lists Levels and Distances runs next to the EQ runs; remove them from that list (it is for the EQ runs drawn on the chart). Levels and distances keep their own cards.
- **TV screen: clean it up (owner: "like text was vomited").** No frames or grouping on the right side, nothing emphasised, repeated text. Seen on the owner's TV (2.17.0, light look):
  - The code stays up after the phone connects; hide it once a phone is connected and show the connected phone in a proper, designed way (a deck with the phone's name and a lit pilot).
  - Loose status lines with no frame: "home theatre", "eq on · stereo · dolby from apps passes by it", "hdmi · dolby digital 5.1 · 5.1". Group them into decks (device and output; EQ) like the phone's.
  - Repeated and contradicting text: "5.1" twice in one line and again on a key; "eq on" in a line while the key reads "EQ off" (the key names the action, not the state). Use one big EQ state key as on the phone and PC.
  - No emphasis: pick what matters at a glance (the connected phone, the test running, the EQ state) and make it the large element; the rest small.
  - The room and the chart on the left are fine (the photo was cropped); the clean-up is the right side.
- **The room drawing: labels and look (TV and phone).** On the TV the speaker names and their changes ("+7.0 dB", "level") are not aligned to their speakers and overlap other things (C's "+7.0 dB" runs into the sofa). The phone's is slightly better; fix both. Enhance the TV's room look, and replace the floor's dark grey rug, which the owner finds ugly.
- **The connected device, shown properly, with its Disconnect.** On the phone, Disconnect sits as a loose row between the EQ card and the chart. Show the connected TV or PC in its own deck (its name, output, a lit pilot) with Disconnect in it.
- **Animate the EQ (response) chart.** The phone's home-theatre chart never plays its arrival animation, unlike the other charts.
- **Accent colour on the TV.** The TV screen is all grey and depressing; bring in the accent colour (as the phone does: live, playing, EQ on).

## Owner's notes, round 3 (2026-11-11): roadmap
- **Tune home: Advanced tools in the next step.** The "Advanced tools" card (mic spots, channel, window, left/right, subwoofer) on the Tune home moves to the next step (Set up).
- **Tune home: the dial graphic in the middle** of the screen.
- **Rename the TV/PC key** "Speakers on a TV or PC" to "Connect to the companion app on a TV or PC".
