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
- The screen header gets a Braun-like design with minimal graphics (in the spirit of the One UI header already in 2.1.0, but quieter and more Braun: fewer shapes, plainer lettering).
- The app name is written in capitals: **AUTOTUNE** (the wordmark on the header's top line, and anywhere else the name appears as a mark).
- Open point to settle with the owner: the request said "Sam Braun like design". Read as "Samsung One UI-style layout with Braun-style minimal graphics". Confirm, or say what "Sam" meant.
- When executed: update `ScreenTitle` and `HeaderArt` in `ui/Kit.kt`, the wordmark in `ui/MeasureOverlay.kt` and `ui/AiScreen.kt`, `DESIGN.md`, a `CHANGELOG.md` entry, and bump the version in `gradle.properties`.
