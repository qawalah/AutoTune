# AutoTune backlog

## Phone-mic calibration from the USB mic (asked 2026-10-12)
Make the phone's own mic usable for tuning by calibrating it against the calibrated USB mic.
- Flow: mic stand at the listening spot. Measure the same sweep with the USB mic (calibration applied), then with the phone held exactly where the USB capsule was, pointing the same way. Repeat at 2-3 spots and average.
- Correction = USB response - phone response, 1/6-octave smoothed, level-matched at 250 Hz-2.5 kHz, saved as a calibration file for "Phone microphone" (per phone model and orientation), applied automatically when no USB mic is plugged in.
- Needs the UNPROCESSED source (no AGC or noise suppression), otherwise the curve changes with level. Check that two consecutive phone-mic runs agree within 1 dB before saving.
- Expected accuracy about +/-1-2 dB to roughly 10 kHz; above that the phone mic is directional and depends on how it is held. Say so in the app.

## Road noise: measure it, then lift the music over it (asked 2026-11-08)
Masking-based, not phase cancellation. A phone cannot do real noise cancelling: it needs about 2-3 ms end to end, and Android audio is 10-40 ms (hundreds over Bluetooth). This version needs no tight latency and cannot make anything worse.
- **Road-noise measurement.** Record the cabin noise spectrum while driving (UNPROCESSED mic source, no AGC or noise suppression), average over a few seconds, and draw it as a noise floor under the EQ graph. Save it with the test or as its own reading, per car and per speed band.
- **Noise-aware EQ and loudness.** Lift the music in the bands where the noise masks it, and let the level follow speed or measured noise (like Bose Audio Pilot or speed-dependent volume). Clamp the lift to the app's EQ limits and the safe preamp; fit from the stock run as usual so EQs never stack. Likely a new mode next to Dynamic EQ.
- Mic placement matters: use the listening spot or the USB mic, and say so in the app.
- Open questions: take speed from GPS or from the noise level alone; how fast the correction may move so it never pumps; whether it is a Tune step or a Live-tab control.

## Later, hardware: real active noise cancelling for the car (asked 2026-11-08)
Not an app feature. A separate controller running the real-time loop, with the phone as setup and display.
- Controller: Teensy 4.1 (or Bela) with a multichannel I2S/TDM codec, FxLMS adaptive filter, hard limiter and divergence detector.
- Sensors: 2-4 accelerometers on the suspension as reference, one error mic per front seat at the headrest. Output through its own amp and headrest speakers, not the head unit.
- Phone role: measure each speaker-to-mic response (the secondary path) with the existing sweep and mic code, send filter settings over USB or BLE, show the result.
- Expect 3-6 dB below about 300 Hz at best. Prototype on a bench with one speaker and one mic first.

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
