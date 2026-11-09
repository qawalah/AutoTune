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

## Flow review (9 Nov 2026): 1-3 done in 2.5.0, the rest open
From the flow maps in `FLOW.md`. Done: Set up is a draft, the EQ check decides the next move, preview tunes are measured on the phone's EQ before keeping.
- **Warn about bypass outputs in Set up.** When the output is Android Auto, car mode (`LoudEq.carMode`) or in `eqBypassOutputs`, one `Alerts` line and the EQ step pinned to equipment.
- **Level check before the stock sweep.** A short level probe as the first check on the measuring screen (reuse `levelCheck`), stopping with "turn it up/down a step" and Try again. No extra screen.
- **Done pins something useful.** With a Bluetooth device connected, pin "Use this EQ whenever <device> connects" (a `rules.txt` rule); otherwise "Open the EQ".
- **Route rules out of the screens.** `onEquipment` in EqTab and the `equipment` default in ApplyStep into one pure `applyRoute(...)` in TuningLogic with FlowTest cases; later split `TuneViewModel` (tune flow, measuring) into their own classes.
- **Back up and restore tests.** Settings › Data (Advanced), reusing the diagnostics zip layout and TestStore's loader.
