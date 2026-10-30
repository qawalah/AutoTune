# AutoTune

Android app, built car-first, that measures a speaker system (car, or home theatre) with a USB measurement microphone,
corrects for the mic's calibration file, and automatically tunes a parametric EQ until the response matches a target.

## How it works (three tabs and a menu)

- **Tune**: one guided workflow behind a big pulsating Tune button. 1 Fresh start (AutoTune EQ is switched off; set the
  head unit flat), 2 Set up (target, how the sound reaches the speakers, output, mic spots, window, all drop-downs),
  3 Stock reading, 4 Stock review (distance from the target, a verdict, what the EQ should reach, findings such as
  boomy bass, harsh presence, peaks, ringing, hardware limits and measuring warnings), 5 Apply the suggested EQ (three
  plain choices: the phone's EQ, a preview in the app, or your equipment; sliders, exact bands and files under
  Advanced), 6 Verify, 7 Result. Set up asks only for the name, target and route; mic spots and the analysis window are
  under Measurement options. Every measurement ends on one screen: the summary, one recommended action (apply the new
  suggested EQ, keep this EQ, keep the best run's EQ, or apply the EQ again when it never arrived), the other choices,
  then Details (charts and diagnostics, closed). Each step has labelled Back and Exit buttons and a named progress bar;
  Exit keeps the place and Continue returns to it. Finishing saves the EQ as a profile and keeps it on. Left/right and
  subwoofer checks sit under Advanced tools.
- **Tests**: your saved tests; New test starts one. Tap a test for the same summary the Tune screens show, Continue
  tuning, and Details (chart, runs, compare, report card, tonal balance, resonances, distortion, suggested EQ, target,
  notes, manage), each closed until opened.
- **EQ**: AutoTune as the EQ, any time. "EQ in use" is one drop-down grouped by test (car / home theatre icon): the next
  suggestion, the EQ of every run with its result, and saved EQs. The power switch applies it. Locked sliders add to it;
  A/B listening and automatic switching per Bluetooth device are under Advanced.
- **Menu** (three bars), in two groups. Tools: Live spectrum, Export EQ files (which EQ, which app, save or
  share). App: Settings (mic and calibration, AI provider and key, diagnostics, theme, target curves), Send diagnostics.

**AI review** (optional, needs your own API key for Claude or Google Gemini, set in Settings > AI assistant): the AI reads the open test and
lists what limits the result (measurement, setup and speakers, EQ, target), one action for each, and the next step. It
may suggest EQ changes on top of AutoTune's suggestion; the app holds them to its own limits (cuts to 12 dB, +3 dB of
boost, bands the route can realise), predicts the result for the run, and puts them in "EQ in use" as "AI-adjusted EQ"
to measure. Follow-up questions are answered with the test in view. A failed measurement has "Ask AI why". What is sent:
the responses as dB from the target at third-octave points, the EQ of each run, warnings, checks, notes, phone model and
mic name; never recordings. Reached from the Result step, a test's results and the failure screen.

**AutoTune EQ outside the app**: switching it on with no EQ picked starts it flat; the EQ you pick next plays at once.
The notification has "Switch EQ off" and "Stop auto-detect". Widget toggles (EQ, Auto, profile) work in the background
without opening the app; only the widget's Tune chip and tapping the notification open it.

Send diagnostics shares one zip: a log of every measurement (recording level, the EQ in effect and how it was applied,
signal-to-noise, warnings, 1/3-octave response), every EQ change and failure, the last 12 recordings as WAV, the saved
tests and a summary of the settings.

## Build

- Android Studio: open this folder, run `app`.
- CLI: set `sdk.dir` in `local.properties`, then `./gradlew :app:assembleDebug` (needs JDK 17+).
- DSP tests (no Android SDK needed): `gradle :dsp:test`. They simulate a room with resonances and a coloured mic and check
  that the loop converges and finds the planted resonances.

## What plays: preamp and flavour

The EQ tab sets how boosts are kept from clipping (Safe, Balanced, Off, or Manual with a slider) and a flavour on top
of the tune (More bass, Rock, Pop, Electronic, Acoustic, Speech, Low volume, Late night). The flavour only plays;
measurements always use the tuned EQ alone with the safe preamp. Settings > AutoTune EQ chooses 31 or 61 bands.

## Targets and taste

Built in: Car (+5 dB bass), Car Harman-style (+8 dB), Home theatre (+4 dB), Room Harman-style (+6 dB) and Flat, plus
your own curve files and saved sounds. "Target and taste" on the result screens adds a bass shelf (-3 to +12 dB) and a
treble shelf (-6 to +4 dB) on top and refits the EQ at once: tune to the curve, then season to taste.

## Limits to know about

- **Samsung's own EQ (Adapt Sound / SoundAlive) cannot be controlled by third-party apps.** AutoTune applies its EQ to
  audio it plays itself. To use the result elsewhere, type the bands into your car head unit / AVR / player EQ.
- Turn off system sound effects while measuring or they colour the result.
- The EQ is fitted to the *frequency response*. Resonances that ring (long decay) get their level reduced but not their
  decay shortened; that needs placement or damping.
- Peaks are corrected with cuts. Boosts are limited to +3 dB per band (about +4 dB in total where bands overlap) and only used for wide dips, never for narrow nulls.
- Pink-noise mode assumes the noise really is full-range pink; a filtered or band-limited video shows up as a tone error.
  Use the sweep file when you need resonance decay times.
- Not yet tested on real hardware: USB mic routing, Bluetooth output latency, and the clip decoder were compiled but not run on a phone.


## Tests and runs
A test is one system ("Camry front stage", "Living room") with its own target and source. Run 0 is the stock profile
(no EQ), kept as the reference. Add as many later runs as you like; each stores the EQ that was applied and a note.
Tick runs to overlay them in the response chart and RTA bars and to compare them in a table. Recommendations and export
follow the latest ticked run.

## Export
Saved to Downloads/AutoTune: ParametricEQ.txt (Poweramp Equalizer, Equalizer APO), GraphicEQ.txt (Wavelet),
report.txt (all runs, what was applied, notes) and responses.csv.

## Appearance
Setup (gear on the Tests tab): System / Light / Dark, and wallpaper colours on Android 12+.

## Sources
Two choices: connected to the phone (the phone plays and listens), or not connected (another device plays; the phone listens).
For the second, play our sweep file, any pink noise, or the combined pink noise + sweep file. In the combined mode the
sweep gives the response and ringing times and the pink noise gives an independent reading; they are cross-checked.

## System report card
Each run is scored 0-100 on tonal balance, smoothness, bass and treble extension, resonances (and left/right match
when measured), with a verdict that says what EQ can fix and what is hardware or room. It also estimates the score after
the suggested EQ.

## Extra checks
Left/right balance (level and timing), subwoofer crossover / level / polarity, saving a response as a target curve,
averaging 1/3/5 mic positions, headroom warnings on export, and a live RTA with sound-level meter (Live tab).

## AutoTune as the EQ (experimental)
EQ tab: the AutoTune EQ switch. Applies the suggested EQ, or your own band edits, to the phone's audio with
Android's DynamicsProcessing effect (global mix, plus players that announce an audio session), kept alive by a foreground
service. It cannot control Samsung's Adapt Sound / Dolby / SoundAlive, and some phones ignore global effects.
Measure the stock run with it off; later runs with it on.
