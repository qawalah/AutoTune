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
