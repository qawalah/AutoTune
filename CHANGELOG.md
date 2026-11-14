# Changelog

Format: [Semantic Versioning](https://semver.org). Version lives in `gradle.properties`
(`APP_VERSION_NAME`, `APP_VERSION_CODE`); release by bumping both, adding an entry here, committing and tagging `vX.Y.Z`.
Everything below is untested on a real device and microphone unless stated.

## [2.34.0] - 2026-11-14
### Added
- **Two more tutorial cards**, after "Keep it":
  - **Tune a TV or PC too**: AutoTune on an Android TV or a Windows PC. In the picture the TV shows its pairing code, the phone links to it, each speaker plays in turn, and the TV's EQ comes on.
  - **Listen live**: the Live tab. In the picture the spectrum rises with the music, each bar leaves its held peak, and the level reads beside them.
  - The tutorial is now seven cards. It still shows once; Settings › About › "Replay the tutorial" shows the new cards on a phone that has already seen it.

## [2.33.0] - 2026-11-14
### Added
- **Tone generator on Live.** "Play from the phone" gains **Tone**: one steady sine from 20 Hz to 20 kHz on a log slider, with ± one-semitone keys and the pitch in large figures. It follows the slider without clicks (a short output buffer and a gliding phase), plays quietly (−20 dBFS; the phone's volume sets how loud) and is kept for next time. The spectrum marks the bar the tone sits in. `Tone`, `ToneSynth` in `:dsp`, `TonePlayer` in `AudioIo.kt`; `LiveSignal.TONE` is appended.
- **Hold peaks on Live.** A switch under the spectrum keeps the highest each bar has reached as a white cap, and the loudest peak on the level bar and its reading ("Peak hold"), until **Reset peaks** or Start. A held peak that touched clipping keeps the warning up until reset. `PeakHold` in `:dsp`.
- **Live turns sideways.** With the phone's auto-rotate on, Live fills the width: the spectrum on the left, the level, hold and sound beside it. The spectrum has a full-screen key like the other graphs.
### Changed
- **The live scale fits what is on screen.** The bars were cut off at ±15 dB, so a bass-heavy room, music, or a pure tone showed flat-topped bars that hid how far off they were. The scale is now ±15 dB while the bars stay close to the target, widens at once to ±30, ±45 or ±60 when a bar, a held peak or the target would be cut off, and narrows one step at a time once they settle (`rtaHalfRange`). A tone is shown against its own top bar on a fixed 0 to −60 dB scale. The level bar (−80 to 0 dBFS) and the readings were checked and are unchanged.
- **Turning.** The app stays portrait, either way up whatever the phone's auto-rotate says (mic on top). It turns sideways only on a graph opened full screen (always) and on the EQ and Live tabs when auto-rotate is on; before, the EQ tab turned even with auto-rotate off. `turnFor`, `rememberAutoRotate`.

## [2.32.0] - 2026-11-14
### Changed
From the second part of the audit of 13 Nov (`AUDIT-2026-11-13.md`, "Full audit of every screen and key"). Checked on the real screens with the walk-through harness (pretend car).
- **A test with no name is called after the equipment**: "Car", then "Car 2", "Home theatre", "PC speakers", "Speakers" (or the equipment name typed in Advanced), never a date or the target. Saved tests keep their names. Refine and Result titles use the short name.
- **Simple drops the names screen from Set up**: one screen fewer. Rename stays on Tests and the results page; Advanced keeps the screen, with "Equipment name (optional)". With AutoTune on the TV or PC, Simple's "Yes" connects at once.
- **Tune screen**: with an unfinished test open, Continue is the big key and New test the small one; a finished tune shows its Tuned card and no Continue. The open test, its finished tune and where Continue returns are kept when the app is closed and opened again.
- **Finished tests say "Tune further"** in the Tests ⋮ menu too. The open test's light is labelled "open".
- **The microphone is asked for by the measuring key itself** ("Allow the mic and measure"), and the measurement starts as soon as access is given. While access is missing the screen shows one amber line and one key.
- **The next mic spot starts by itself** after a 5-second countdown shown large on the display, with Pause and "Measure spot n now". Set up says "3 mic spots · each starts by itself" in Simple.
- **Measuring screen**: "Spot 1 of 3 done" once; no bare dash (the mic while it opens, the countdown between spots); no cross: each stage ends with one key that says what it keeps ("Close and drop spot 1", "Close, nothing is saved"); in Simple all-green checks are one line that opens the list.
- **Refine**: the three ways to apply in one list, the recommended one first (and pinned), each with a one-line fact; the bands and preamp figures are Advanced only; the duplicate "The EQ should bring it to…" line is gone (the reading says it; the why is in the bubble). Back from Refine asks the same as Exit.
- **Stock is drawn dashed on every chart**, alone too. A hidden curve's name is greyed, never struck through.
- **Result** no longer draws the next suggested EQ when the advice is to keep and finish.
- **EQ tab**: one "on"; no "EQ updated: n bands" status in Simple; the lock says "Locked"/"Unlocked"; keys that cannot be pressed look off; the deck is "Bands"; saved EQs are called saved EQs everywhere (no "profile"); the note about another EQ app's preamp only for EQs set on other equipment; the graph shows the stock under the run and no "Predicted" curve lying on the run.
- **Menus** (the mode key, ⋮, every drop-down) are the app's light plate with its hairline, every option readable. Dialog keys use the app's key style.
- **Notes**: "Averaged 3 mic positions." is a fact, never a warning; each note shows once; "Bass fades out below 100 Hz." (it said "above", with no full stop).
- **Wording**: "1 system", "dB from target"; "suggested" only under a key with other choices beside it; the route list without "(best)" or "sweep + pink"; "Test sound" has no page arrow; one output to play through is a status line, not a one-entry drop-down.
- **Connect page**: titled "PC speakers" or "Home theatre" by what is connected (long titles drawn smaller rather than cut); in Simple the address field appears after 6 seconds with nothing found; Back returns to the Set up screen it came from with its answers.
- **Live**: the standby plate says what Start does; the level deck shows once there is a level, so the Sound deck sits right under the spectrum.
- **Settings**: no doubled "Mode"; no calibration warning for the phone's own mic; the AI key and Send diagnostics are reachable in Simple.
- **Tutorial** ends on the Tune tab, with one counter (its lights). Empty Tests no longer shows the "more below" key.
- **Set up's four picture keys are one height.**
- **TV**: its EQ key switches the EQ back on with the bands it had (kept when switched off, as the PC does); older phones work as before. The widget says "TV or PC". The home theatre page has the Speakers choice right under the device deck.
- **Windows**: "not on this output" is one line and one key (use the output Equalizer APO is on, else open its Configurator, then Check again); the EQ deck sits above the output once that is set, so it shows at the start size.

## [2.31.0] - 2026-11-13
### Fixed
From the audit of 13 Nov (`AUDIT-2026-11-13.md`). The link stays compatible both ways: older phones, TVs and PCs ignore what is new.
- **Stop stops the TV or PC too**, for every home-theatre test (distances, EQ, EQ check, speaker by speaker), and the EQ held off for the test comes back at once.
- **A link that drops during a test ends it at once** with "The connection to the TV was lost", not "turn the volume up". The level test no longer listens to silence for minutes.
- **A dead link is noticed within half a minute, both ways.** The TV or PC answers the phone's ping, so the phone drops a link that went silent and connects again by itself; the TV or PC drops a phone that went silent, so a vanished phone no longer shows as connected (and no longer blocks Pretend).
- **The TV and PC stop playing when the phone goes**, and their EQ is back on.
- **The phone's screen stays on** while a home-theatre test runs and while the measuring screen measures (also between the spots of a car reading).
- **Guessing the code is slow.** Wrong codes are answered one at a time, whatever the number of connections, and the pause grows after three in a row (up to a minute). Only a few connections may wait unpaired, and an over-long line is refused. The four-digit code and remembered pairings are unchanged.
- **The TV and PC are found again when their network comes up or changes**: the address on their screen follows and they announce themselves again. A failed announcement on the TV is tried again.
- **Connect by address takes a port** ("192.168.1.20:47811"); the TV and PC show the port next to the address when it is not the usual one.
- **Connecting from the page, the widget and the retry no longer collide.**
- **EQ on or off that cannot be sent says so** in one line (after trying to connect again), and a good report from the TV or PC clears an old EQ error.
- **The widget says plainly that AutoTune must be open on the TV** when the TV does not answer.
- **Windows:** Allow AutoTune also lets the phone in through the firewall on private networks (not yet tried on a real PC). The EQ key no longer says "press for on" when there is no EQ.
### Changed
- **The home theatre's three tests are numbered: 1 Levels, 2 Distances, 3 EQ**, so a first-time user sees an order. Nothing is pinned or pushed.
- **Set up shows one counter**: the step line; its screens no longer add "2 of 4".
- **Stock and Verify light on the step bar** while the measuring screen measures them.
- Docs: FLOW.md, HANDOVER.md (identity, signing, code notes) and CLAUDE.md (the default look is Gradient edge) brought up to date.

## [2.30.1] - 2026-11-13
### Fixed
Found by a first-time-user walk-through on the JVM with a pretend car (`tools/walk/`), then checked against the code.
- **The Tuned card shows the run that is in use.** After "Keep this EQ and finish" on a later round it showed the run with the lowest dB, while the EQ on (and the Tests list) was the kept run.
- **The band sliders move only when selected, then dragged.** A tap selects a fader (its cap turns to the accent) and moves nothing; only the selected fader follows a drag, and by how far the finger moves, not by jumping to it. Touching one in passing, or scrolling over the sliders, changes nothing. The selection lets go after 4 seconds, and when the sliders are locked. An unselected fader no longer takes a tap as a new value.
- **A finished test no longer asks to be continued.** Its main key is "Open the EQ", and "Tune further" is one of the other choices. An unfinished test keeps "Continue tuning" as its main key.
- **Exit halfway asks: save or discard.** With readings taken and no EQ kept yet, Exit offers "Save and leave" (Continue brings you back to the same step), "Discard the test" (the test and its readings are deleted) and "Keep tuning". Set up, and a test already finished once, leave without asking.
- **A finished test opens on its best run.** The test's results page led with the latest run ("Run 2 vs stock") even when an earlier run was closer in dB and score. It now opens on the best run, the graph draws stock and that run, and ticking another run still moves the lead. While tuning, the Result step still shows the latest.
- **Finishing keeps the closer run.** The 0.3 dB margin only decides whether another round is worth it. With Run 1 at 3.8 dB and Run 2 at 4.0 dB the pinned key said "Keep this EQ and finish" for Run 2, which was worse in dB and in score; it now says "Keep the EQ of Run 1 and finish".
- **Continue is no longer left under the "more below" fade.** With a finished tune on the Tune screen the dial gives way, so the card and Continue fit.
- **The Tune screen says "Phone microphone" before the microphone is allowed**, not "USB microphone: Phone microphone", and offers Allow microphone under it.
- **Live: Stop puts "Play from the phone" back to Off**, so the next Start is silent until a sound is picked again.
- **The spectrum's legend says "green"** (the bars are green), not "teal".
### Added
- `tools/walk/`: a first-time-user walk-through. The real screens run on the JVM (Robolectric) with a pretend car (speaker, cabin, mic, head unit) behind `MeasurementRunner.simulator`, driven from a shell. Not part of `./gradlew test`.

## [2.30.0] - 2026-11-13
### Changed
- **The Tune key is the app's mark, as on Windows**: a graphite ring, a light face with its scale and the dot in the accent. Press it and the knob turns up its scale. Tune's icon is the same mark; the Tests icon is a clearer record with its tonearm.
- **Graphs stay still** and are drawn on the plate again, not on a black screen. Touching a graph no longer reads out the suggested EQ, and the reading never runs off the edge.
- **Accent works again in Simple**: picking an Accent now wins over an accent set in the palette. The row of colour dots under Look is gone.
- **Concept look, reviewed screen by screen**: the header graphics are small plastic devices (no outlined boxes), menus are soft plates as wide as their field, text fields, equipment keys and badges, the notes line and the "more below" key match the plastic, the live level sits on the LCD while measuring and on Live, and the icons have round ends.

## [2.29.0] - 2026-11-12
### Added
- **A new look, Concept** (Settings › Look), after a family of small off-white devices. The plates and keys are soft plastic: rounded corners, lighter at the top than at the foot, with a soft shadow. Fields and drop-downs are pressed into the plate. The round keys (Tune in the tab row and on the dial) are domed orange. The readings of a result sit on a grey LCD in a dark frame, and the response graph is drawn on a dark screen. The tab keys are raised and the chosen one is pressed in; with the AutoTune EQ on, its key carries a lit pilot. The switches are a slot pressed into the plate with a plastic knob, lit orange when on. At night the plastic is graphite and the LCD dimmed. The accent and the palette work over it as over any look. The other looks are unchanged.

## [2.28.1] - 2026-11-12
### Changed
- **The rest of the screens arrive too:** every text field on a page, each EQ band and profile row, the band sliders, AI issue rows, findings, result headlines and the "Details" heading now settle in like everything else.

## [2.28.0] - 2026-11-12
### Changed
- **Everything that appears now arrives, not only a page opening.** Set up's screens (equipment, TV or PC, names, sound) slide in from the side they come from and Back slides them the other way, each with its items arriving one after the other. Anything else that shows up later (an answer, a folded section, a result after a measurement, the pinned key) arrives the same way, on every screen, dialogs included. A burst of arrivals starts again after 0.4 s of quiet.

## [2.27.3] - 2026-11-12
### Changed
- **Arrivals are app-wide.** Besides decks, keys, buttons and actions, now each reading, drop-down, switch row, note line, alert line, status, step header, scale, response graph, the measure key and each test row arrives on its own, in reading order, on every screen.

## [2.27.2] - 2026-11-12
### Changed
- **Every item arrives on its own, not just the deck.** Inside a deck, each picture key (Set up's four equipment keys, the Yes/No keys), each button and each stacked action rises, grows and fades in after the one before it, in reading order.

## [2.27.1] - 2026-11-12
### Changed
- **The motion is now easy to see.** Decks rise 64dp from 90% size and arrive 90 ms apart (the Tests lists and every Group included); pages come up from 14% of the screen at 90%, and tabs slide in from a third of the width while the old one slides away. 2.27.0 was too small to notice.

## [2.27.0] - 2026-11-12
### Changed
- **Screens settle into place.** When a screen opens, each deck rises and fades in after the one above it, instead of the whole page fading at once; pages also come up with a little depth (they grow in from 97% while what is under them eases back). Decks that appear later (unfolded, scrolled in) are simply there.
- **Graphics play once, never twice.** The header art, the Tune dial's pulse, the report meters and the response graph move once and then stay still.
### Added
- **Press a graphic to play it again:** tap the header drawing, the response graph (full screen too), the report meters or the Tune dial. Pressing the tab you are already on replays its graphics as well. The pull-down replay stays.

## [2.26.1] - 2026-11-12
### Changed
- **The Tests list draws what each test tunes,** as Set up does: a car, a TV between tower speakers, a monitor between desk speakers, or a portable speaker, each in a small box with a hairline edge. Home theatres show the TV. Tests made before 2.20 take theirs from their target (a car curve is a car, a room curve a home theatre).

## [2.26.0] - 2026-11-12
### Changed
- **No key that repeats what is on the screen.** "Next: car" (and the rest) is gone from "What are you tuning?": a picture key picks and moves on. Also gone: "Open the EQ" on the Tune screen after a finished tune (the EQ tab is right under it) and "Back to the EQ" on an empty Result (the back arrow does it).
- **Every header drawing sits in an outlined box,** so it reads as a device on the light looks too: the record player, the faders, the radio, the controls, and the round speaker in a cabinet.
- **The tape deck is a cassette:** its shell, the window the reels turn in, the tape over the head.
### Added
- **The home-theatre page has its own drawing:** a TV between two tower speakers with a soundbar; as it appears the level bars rise on the screen and the sound spreads from the towers.

## [2.25.1] - 2026-11-12
### Changed
- **"Is AutoTune on the TV?" is two equal choices in the middle of the screen:** "Yes, AutoTune is on the TV" (with the TV or PC drawn) and "No, use this phone only" (a phone), side by side, the same size. Nothing is pinned or suggested below them any more.
- The "TV or PC" widget shows a live preview in the widget list (Android 12 and later). It is in the list from 2.25.0 on; Settings › About shows the version installed.

## [2.25.0] - 2026-11-12
### Changed
- **The link to AutoTune on the TV or PC stays up.** It belongs to the app now, not to the home-theatre page, so leaving the page no longer drops it. The phone sends a small keep-alive every 15 seconds, so a dead link is noticed, and when it drops (not by Disconnect) the phone connects again by itself: after 2, 4, 8, 16 s, then every 30 s for ten minutes. It remembers the TV or PC, its address and its code: opening the home-theatre page connects again at once, and finds it by name on the Wi-Fi when its address changed. "connecting to …" shows while it does. Disconnect stops the reconnecting.
- **Flavour in Simple on the EQ tab** (the preamp stays in Advanced).
### Added
- **Flavour for the TV or PC.** A flavour drop-down under the EQ key on the home-theatre page plays on top of the EQ there (More bass, Rock, Pop and the rest) and is sent at once while the EQ is on. It is never measured: a test that plays through the TV's EQ gets the plain EQ, and the flavour comes back after it.
- **A "TV or PC" widget:** the TV or PC by name and whether it is connected (opens the home-theatre page), its EQ on or off, and the flavour (each tap the next). The keys work with the app closed: they connect to the remembered TV or PC first, and send the home theatre's last EQ with the flavour.

## [2.24.1] - 2026-11-12
### Changed
- **The home theatre's target is on its EQ card, in Simple too**, before the first EQ test as well, so the first EQ is fitted to it. It was only in Advanced, in the Refine card, which appears after an EQ test. Bass and treble to taste stay in Refine (Advanced).

## [2.24.0] - 2026-11-12
### Added
- **Refine on the home-theatre page.** After an EQ test, a Refine card shows the suggested EQ (bands and preamp, and whether it is on the TV or PC), "Set it by hand on the home theatre" with the bands, and "Share the EQ files" (ParametricEQ.txt and GraphicEQ.txt). Advanced adds the target (the room curves) and bass and treble to taste, kept for each home theatre; changing them refits the EQ and resends it when it is on.
### Fixed
- **Connecting from Tune starts a new home theatre** instead of opening the last one measured on that TV or PC, which could be another system. Set up now goes: equipment, "Is AutoTune on it?", the names, then "Connect to AutoTune on the TV" (or the PC), which makes a home theatre with those names once it connects (named after the TV or PC when no name was typed). While it connects, the last home theatre's results stay out of sight. Tests › Connect to AutoTune on a TV or PC still opens that device's own last home theatre.
- The home theatre's charts draw its own target.

## [2.23.0] - 2026-11-12
From the owner's failed stock reading: the phone speaker played the sweep, the mic clipped, and the reading failed as "Bluetooth dropped out".
### Fixed
- **Only Bluetooth is blamed for a pause in the sweep, and only over Bluetooth.** On any other output a shift in the sweep's timing is noted on the reading ("The timing moved 30 ms partway through the sweep"), not failed and not measured again.
- **Clipping comes first.** A recording that clipped cannot be timed (the overload smears it), so the timing check is skipped and the reading says it is too loud, as it did before 2.19.2.
- **A clipped stock reading, or one whose timing moved, is measured again first:** it is every run's reference, so Refine pins "Measure the stock again" before any EQ is fitted to it.
### Changed
- **Set up checks the test sound reaches the equipment.** When this phone plays it, "Measure the stock system" stays off while the sound goes to the phone's own speaker, with the reason above the key ("connect the phone to the car first"). The measuring screen's "Sound output" check is yellow for the phone's own speaker.
- **Screens say when there is more below:** a fade at the bottom edge and a small round key with a down arrow that scrolls on, on the measuring screen and on every tab and page.

## [2.22.0] - 2026-11-12
### Changed
- **Set up is one question per screen** (Back walks them):
  1. **What are you tuning?** The four picture keys; tapping one picks it and moves on.
  2. **Is AutoTune on the TV?** (or the PC), only for a home theatre or PC speakers. "Yes, connect to it" opens the home-theatre page; "Use this phone only" goes on. The answer given last time is the pinned one.
  3. **Name it:** the equipment's name and the test's.
  4. **Play the test sound:** how the sound reaches the speakers, where it plays and Test sound, then Measure the stock system. Only reached without AutoTune on the TV or PC.
- **Simple no longer asks the target.** A car gets Harman car; a home theatre, a PC and other speakers get Home theatre. Advanced asks it on the last screen.
### Removed
- Connect no longer names a new home theatre from Set up (the names come after the question now); the page opens on the last one, where New names one.

## [2.21.0] - 2026-11-12
### Changed
- **The equipment is four big keys with a drawing each:** a car, a TV between tower speakers, a monitor between desk speakers, and a portable speaker. The one picked has the accent edge.
- **Set up asks in this order:** the equipment, its name, the test's name, then (for a home theatre or a PC) whether AutoTune is on the TV or the PC, then the rest.
- **The equipment's name names the EQs** and, when no test name is typed, the test ("F-150, 12 Nov"). Left empty, the output the phone plays to is used as before.
- **Connect to AutoTune on the TV or PC starts a new home theatre** with the names from Set up ("LG soundbar, sofa"); with no name typed, the page opens on the last one.
### Added
- **Tests › Connect to AutoTune on a TV or PC:** a second way to the home-theatre page, beside Tune › Set up.

## [2.20.0] - 2026-11-12
### Changed
- **Set up starts with the equipment.** Its first question is what you are tuning: Car, Home theatre or soundbar, PC speakers, or Other speakers. Everything after it follows from the answer.
- **Then it asks whether AutoTune is on it**, for a home theatre (AutoTune on the TV) and PC speakers (AutoTune on the PC). Installed: the key is "Connect to AutoTune on the TV" (or the PC), which opens the home-theatre page, where it plays each speaker and puts the EQ in use there. Not installed: Set up goes on as before, from this phone alone. A car and other speakers are not asked.
- **The rest of Set up follows the equipment:** the target list holds only the curves for it (car curves for a car, room curves for a room, Flat and your own curves always); the ways the sound can reach the speakers name what plays it ("The head unit plays our sweep file", "The TV plays pink noise") and a PC starts from our file played on the PC; the checklist in the (i) names the equipment's own controls (head unit, TV and home theatre, Equalizer APO).
- **Refine says where the bands go** on the equipment: the head unit or DSP, the home theatre's EQ, or Equalizer APO on a PC, which also gets Save and Share for the EQ files right there.
- Set up remembers the equipment per output and the last one picked, and whether AutoTune was installed on the TV or the PC.
- The Tune screen no longer has "Connect to the companion app on a TV or PC": it is the second question on Set up (Tune › Home theatre or PC speakers › Installed). The widget's companion key still opens the page directly.
### Added
- Tests keep their equipment (`equip=` in the test file); tests saved before take it from their target.

## [2.19.4] - 2026-11-12
From the owner's diagnostics after 2.19.2 (F-150, two tunes): the 4096-sample blocks were taken, about twice as much of the bass EQ's shape now arrives, the smear around 80 Hz is gone, and the pause check caught a real 14 ms Bluetooth pause. It also showed three faults.
### Fixed
- **AutoTune EQ is no longer rebuilt a second after it is switched on.** Android lists every device again whenever the EQ's service starts watching for device changes, and 2.19.2 took that for a new output: 45 rebuilds in an hour, each one a short dropout and a skip with the long blocks. The EQ is now rebuilt only when the music really moves to another output; a USB mic coming or going does not count.
- **No tick when AutoTune EQ is switched on or off while music plays.** With the long blocks, taking the effect away (or adding a new one) mid-song left a gap of silence. Off now fades the EQ to flat and keeps it in place, flat, until the music stops; on fades back in on the same effect. While it waits the notification says "Off · let go when the music stops", and sound still comes about 85 ms late. The first switch-on while music plays can still tick once.
- **The second sweep after a Bluetooth pause says why it plays** ("Bluetooth paused the sound. Measuring once more."). The screen used to start over without a word, and the re-measure was stopped.
- **A microphone that stops mid-measurement is named** ("The USB microphone stopped ... its plug may have come loose"), instead of "Microphone read failed (code -3)".
### Added
- Diagnostics: every device that comes or goes (`audio_devices`, named, inputs too), every move of the media route (`media_route`), the route on each `system_eq_rebuild`, and stopped measurements (`measure_cancelled`).

## [2.19.3] - 2026-11-11
### Fixed
- **Settings › Defaults for new tests take effect.** A new test starts from the defaults (target, bass and treble to taste, mic spots, analysis window); before, the last test's choices on the same output won. Only the way the sound reaches the speakers is still remembered per output.
### Added
- **A cursor on the charts:** touch or drag across a chart to place a line; a bubble reads the frequency and each shown curve's level there, in its colour. Tap it again to clear it.
- **The full-screen chart turns sideways** with the phone.

## [2.19.2] - 2026-11-11
From the owner's diagnostics of 11 Nov (F-150 over Bluetooth): Runs 1, 2 and 5 scored far worse than stock, and a repeat with the same EQ scored 4 points better, because the car's Bluetooth paused the sound for 15-25 ms partway through the sweep.
### Fixed
- **A sweep the Bluetooth paused is measured again.** Each half second of the sweep is timed against the recording; when everything after one point arrives late (or early), the sound paused (or skipped), and the reading would have holes of 15-20 dB. The phone measures once more by itself, and only then says "The sound paused for 20 ms during the sweep (Bluetooth dropped out)" instead of saving a wrong reading. Logged as `sweep_stall`.
- **The start beeps are found when one comes late.** A pause between the beeps (the third came 19 ms late) made the beeps "not there"; the search over the whole recording then took the USB mic's switch-on click for the sound and said "barely heard", blaming AutoTune EQ. A beep may now be up to 60 ms late.
- **The USB mic's switch-on click is ignored.** The first half second of a phone recording (nothing plays yet) is silenced before it is read, and the diagnostics' recording peak no longer reads 0 dBFS every time.
- **No more clicks from AutoTune EQ.** Switching it on or off, changing the EQ or the Dynamic EQ amount, and rebuilding it now fade over about 150 ms instead of jumping. A new song no longer rebuilds the EQ (it played a moment without it, then clicked back): only the first player on a new output, or an EQ that stopped, does, and then the old one fades out as the new one fades in.
- **The bass on AutoTune EQ is applied as tuned, and no longer sounds robotic.** Android's EQ works in blocks, one gain per frequency slot, and at its default of 10 ms the slots were 94 Hz apart: everything below about 140 Hz was two slots, so the 45 Hz band's gain ran from 47 to 140 Hz (seen in the F-150 runs: 63-125 Hz was cut 3.4-5.1 dB where 0.9-3.6 was asked) and the bass sweep came out smeared. AutoTune now asks for 80 ms blocks (4096 samples, slots about 12 Hz apart), gives each band the gain its slots need, and records what the slots really do as the applied EQ. The phone's sound comes about 85 ms later with AutoTune EQ on.
- **The EQ is applied once.** Players that announce their audio session got a second copy of the EQ on top of the phone-wide one, so music was EQ'd twice while the sweep was EQ'd once.
### Added
- **More in the diagnostics:** every phone sweep logs where each half second of the sweep arrived (`chunkLagsMs`), the pause check's verdict, how late the start beeps came (`beepLateMs`) and how far the direct sound was taken from where the beeps put it (`peakOffsetMs`, about 0 normally). Rebuilds of AutoTune EQ are logged with their reason (`system_eq_rebuild`), and `info.txt` gives the EQ's block size, sample rate and the frame length the running effect reports.

## [2.19.1] - 2026-11-11
### Fixed
- **Landscape no longer breaks the app.** Only the EQ tab turns sideways (its graph and faders fill the width); every other screen stays upright, either way up so the mic can point up. Sideways, nothing sits under the system's side buttons or the camera cut-out.
- **Save diagnostics says where it saved** ("Saved to Downloads · …zip"), under the key and as a short message.
### Changed
- **No pinned key on the EQ tab:** "Verify this EQ" is the first row of its Actions deck (in Simple too).
- Settings: the first deck is "Mode" (Simple or Advanced), not "Show".

## [2.19.0] - 2026-11-11
### Changed
- **The tab bar on every screen**, pages included (a test's results, Export, the home theatre); a tab closes the pages over it.
- **The EQ step is now "Refine"**: Set up, Stock, Refine, Verify, Result.
- **Scores everywhere:** every run in the Runs list has its /100 score, and so does every reading on the results card (latest, stock, best). On the card each score sits at the start of the line under its figure, so the three line up.
- **Resonances as a table:** Hz, dB, Q and ring time in fixed, right-aligned columns under headings, the (i) in its own column.
- **A new on/off switch:** a slide switch (the knob runs right and the track fills with the accent while on, "on"/"off" beside it) in place of the small latching key.
- **The home-screen widget in the app's look:** a light plate, white keys with a hairline, graphite Tune, the accent only while a key is on. New: a Loud (Dynamic EQ) key, the EQ key names the EQ in use while on, and the tall size adds a TV or PC companion key.
- **The reels in the folded header** are drawn larger, without the tape, head and pilot that turned into stray marks at that size.
- Settings › AI assistant: Check the key and Remove are rows like every other action.
### Added
- **Save diagnostics** next to Send diagnostics: the zip goes to the export folder (or Downloads).

## [2.18.4] - 2026-11-11
### Changed
- **The app turns with the phone, upside down included** (`fullSensor`), so the phone's mic can point up while measuring. Turning it no longer restarts the screen.
- **The EQ tab in landscape:** the graph and the band faders side by side, filling the screen, the faders taller.

## [2.18.3] - 2026-11-11
### Changed
- **EQs are named after their system.** "EQ in use" and the picker show the device the test was tuned on ("F-150", from "Bluetooth: F-150"), or the test's own name, never "Run 4" or the target. The line under it is the run's note when it has one, else how far from the target it measured and when ("3.3 dB from target · 11 Nov 17:44"). Two tests on one device add their date. Tests keep the device they were tuned on (`device=` in the test file; older tests load as before and use their name).

## [2.18.2] - 2026-11-11
### Changed
- **Actions grouped, one look.** On the EQ tab (Advanced) the loose actions are one "Actions" deck: Check the EQ reaches the speakers, Try it inside the app / I set it on my equipment, and Export EQ files. The pinned key carries its reason as its second line ("with autotune eq on") instead of a loose line above it.
- **One style for actions everywhere:** the outlined buttons in action lists (Settings: microphone, calibration, folder, diagnostics, TV screen, curve files; EQ: bands, try it, Bluetooth switching; Tune's set-up tools, Live, Tests, Export, AI) are now the same full-width rows as every other action.

## [2.18.1] - 2026-11-11
### Changed
- **The Tune screen:** the dial sits in the middle of the screen; Advanced tools (mic spots, channel, window, left/right, subwoofer) moved to the Set up step; the TV and PC key now reads "Connect to the companion app on a TV or PC".

## [2.18.0] - 2026-11-11
### Changed
- **The TV screen, cleaned up.** The right side is now decks with one job each: the phone (the code until one pairs, then the phone's name with a lit pilot), what is playing or the last result, one big EQ key ("EQ on" lit, "EQ off"), and the speakers' keys under one line on the output. No more loose lines or repeated "5.1"; the accent colours what is live.
- **The room's labels sit on small plates** by their speakers, never over the sofa or each other (a far speaker's goes above it when the floor in front is taken), on the phone, the TV and the PC.
- **A light, warm rug** in place of the dark floor, drawn in each deck's own colours.
- **The phone shows the connected TV or PC** in a deck at the top: its name and output with a lit pilot, and Disconnect in it.
- **Details › EQ runs** lists only the EQ runs (levels and distances keep their own cards).
- **Charts play their arrival again when their curves change** (another run ticked, a new test), on the home-theatre page as on the others.

## [2.17.0] - 2026-11-11
### Changed
- **The home theatre page shows the three tests and lets you choose.** Levels, Distances and EQ each carry their own key inside their card; nothing is pinned or suggested between tests, and no "set the levels again". A key is pinned only while a test runs (Next speaker, Measure seat, Stop).
- **The TV's or PC's EQ as one big key** under the room: "EQ on" with the red pilot lit, or "EQ off". Pressing it sends the EQ from the last EQ test, or switches it off.
- **Home theatres on their own page.** The page opens with one line naming the home theatre; tapping it opens Home theatres (pick one, New, Rename, Delete), and Back returns.
- **The TV shows the phone's measuring display** while a test runs: the mic level large, the 20 Hz to 20 kHz sweep dial with its red needle and "Sweeping 734 Hz", the waveform itself (the phone now sends it), and the too quiet / good / clipping meter. The phone, the TV and the PC draw it from the same code.
- **AutoTune for Windows, rebuilt wide:** the room and the test on the left (the phone's room drawing and its measuring display, the same as on the phone and TV), the phone, the output and the EQ on the right.
### Added
- **Windows: speakers 2.0, 5.1 or 7.1.** For a PC wired to a receiver or soundbar over HDMI: each speaker plays on its own channel, the EQ is set per channel, and the phone's layouts follow at once. The PC says when the output takes fewer channels.
- **Windows: edit the EQ.** Each band's frequency, gain and Q, for every speaker at once or one at a time, with the preamp set so it never clips. Saved straight into Equalizer APO.
- **Windows: a big EQ on/off key**, as on the phone.
### Fixed
- **Connecting to the PC showed the TV's results.** Each TV or PC now opens its own home theatre (the one its runs were made on, or a new one named after it).
- **Windows: "Open Equalizer APO's Configurator" did nothing.** It now starts the Configurator from Equalizer APO's install folder with an administrator prompt, and says why when it cannot. When Equalizer APO is not on the chosen output, the PC names the outputs it is on, with a key to switch to one.

## [2.16.1] - 2026-11-10
### Added (after release)
- **AutoTune for Windows in DevStore.** The PC app is also released the way DevStore installs and updates Windows apps (`windows-v2.16.1`, with its icon and screenshots), so DevStore on the PC lists it as Android and Windows and keeps it up to date. Obtainium on the phone and TV must filter AutoTune's releases by `^Android` (the release title, not the tag) (source settings), as DevStore's phone setup says for a repo with a Windows part.
### Fixed
- **A second home-theatre test heard nothing** on an Android box with Dolby Digital (the first was fine). The first test plays with the TV's EQ switched off; later ones played with it on, and on that box an EQ on the TV's sound silenced the Dolby clip. Dolby passes by the TV's EQ anyway, so every Dolby test now plays with the EQ off for the moment it plays.
- **"Check the EQ reaches the speakers" on a Dolby TV** now plays plain stereo through the TV's own sound, where its EQ acts, once with the EQ off and once on. Before, it played Dolby, which the EQ never touches.
- **Nothing heard showed green zeros** ("±0.0 dB", "0.00 m"). The distance now reads "—", and the headline says "Nothing heard: is the volume up, and the TV playing?".
### Changed
- **Each test's keys sit right under its result**: Set the levels under Levels, Test the distances under Distances, and Test the EQ, Use the EQ, Check the EQ reaches the speakers and Switch the TV's EQ off under EQ (with the seats, in Advanced). The suggested one stays pinned at the bottom.
### Added
- **Whether the TV's EQ is on**, always in view under the room: "tv eq on" with a lit red pilot dot, or "tv eq off". On a Dolby TV with its EQ on, the EQ result notes that the test plays Dolby, which passes by the EQ.

## [2.16.0] - 2026-11-10
### Added
- **The home theatre in three tests**, each on its own key, run alone and as often as wanted; none sends you to another:
  - **Set the levels**: pink noise per speaker with the live meter, as before. Its result is what is left to set per speaker.
  - **Test the distances**: one set of start beeps, then one sweep per speaker. Each speaker's distance against front left, the sub's own delay included. Run it at setup and again only when a speaker moves.
  - **Test the EQ**: every speaker together, nothing else. One EQ for every speaker, fitted from the first EQ test so EQs never stack; in Advanced it can average 3 or 5 seats (the phone waits at each seat for "Measure seat n"). It never mentions levels.
  - The pinned key suggests levels, distances, then the EQ; after setup it stays on the EQ, and comes back to levels or distances only when their last run left something to set. An EQ worth using is offered as "Use the EQ on the TV" until it is on.
- **Named home theatres with every run saved.** "New home theatre" names one ("Living room 5.1"); each keeps every levels, distances and EQ run with its date, the TV or PC, the EQ in use while it played, and a note. In Details: the runs, a note per run, and up to three EQ runs drawn together on the chart against the first. Listed on the Tests tab (home icon), and readable without a TV connected. The 2.15 stock and latest results become the first home theatre.
- **Check the EQ reaches the speakers** on a TV or PC: the EQ test once with the EQ held and once with it on, and how much of the EQ's shape arrived.
- **The TV shows the test.** While testing, the TV fills the screen like the phone: the room, the mic's live level meter and waveform (sent from the phone ten times a second), and for the level test the large "+1.5 dB, turn it up". After each test: the readings, the headline, notes, the response against the target, and the next step.
- **The room after Braun's small devices.** Each speaker is a small rounded cabinet with a dot grille (the sub a round field of dots) and a pilot dot that lights red while it plays; a sofa at the seat.
### Fixed
- **The subwoofer on its own was read as not heard** on soundbars that keep the sub well down: it now gets 10 dB more room before it counts as silent, and when it is still not heard the note says what to check on the soundbar.
### Changed
- On a TV with its EQ on, the TV says the EQ is stereo and Dolby from apps passes by it.
- The TV's Dolby clips are now the distances and EQ tests for 2.0, 2.1, 3.1 and 5.1 (the 2.15 system test is gone; phone and TV update together).
- The Windows app plays the distances and EQ tests too.

## [2.15.1] - 2026-11-10
### Fixed
- **"This TV does not let an app EQ its sound (AudioEffect: bad parameter value)"** on an Android TV box with Dolby Digital 5.1. The TV's EQ asked for one channel per home-theatre speaker, but Android mixes every app's sound in stereo before HDMI, so it was refused. It now sets the EQ on those two channels (front left's on the left, front right's on the right; with one EQ for every speaker, both the same).

## [2.15.0] - 2026-11-10
### Added
- **The home theatre in two tests**, the way calibration is done (levels, then distances, then the EQ):
  - **Set the levels.** Each speaker plays pink noise between 500 Hz and 2 kHz (the sub its own, lower noise), and the phone reads it at your seat once a second: "+1.5 dB, turn it up". Front left sets the level; turn each other speaker's level on the home theatre until it reads level, and after three seconds there the next one starts by itself (or press Next). No beeps.
  - **Test the system.** One pass: the start beeps once (not once per speaker), each speaker's sweep in turn, then every speaker together. It gives each speaker's level and distance (the sub's measured delay included), and **one EQ for every speaker** from the system as you hear it, fitted from the first full pass so EQs never stack. Run it again with the EQ on to see it work.
  - The key leads: set the levels, test the system, then what the result asks for. TVs and PCs still on 2.14 keep the old speaker-by-speaker test.
- **Pretend 5.1 or 7.1 on a tablet.** On a phone or tablet used as the TV, the TV screen has a Pretend key (off, 2.1, 3.1, 5.1, 7.1): it plays every speaker through its own two, so the whole flow can be tried without a TV. Both screens always say what it really is ("pretend 5.1 · really tablet speakers, stereo only"), and such a test is never kept as the reference.
- **The room in 3D.** The speaker map is now the room seen from just behind the seat, low and straight on: the floor running to the screen, each speaker a small cabinet with rounded edges turned to the seat, the playing one in red with rings spreading over the floor (every speaker lit for the last sweep of the system test). On the phone and the TV.
### Changed
- The Windows app plays the level noise and the system test too (front left and right).
- The TV's Dolby Digital clips include the system test for 2.0, 2.1, 3.1 and 5.1 and each speaker's level noise; the app is about 8 MB larger.

## [2.14.1] - 2026-11-10
### Fixed
- **The TV's EQ comes back after the TV restarts or AutoTune updates.** Before, it stayed off until AutoTune was opened on the TV. The phone's EQ now also comes back after an update, as it already did after a restart.
- **The TV screen closed after the first speaker played** (on a TV, and on a phone or tablet used as the TV). It told the phone "done" from Android's main thread, which Android does not allow for network use. The TV now uses the same link as the PC, which sends every message on its own thread; a phone that pairs replaces the one before, and a wrong code no longer drops the paired phone.

## [2.14.0] - 2026-11-10
### Added
- **AutoTune for Windows.** A small PC app (an MSI installer, `AutoTune-2.14.0-windows.msi`, attached to the Android 2.14.0 release next to the APK) that works like the TV: the phone measures, the PC plays the test sounds and applies the EQ. Start it from Tune › "Speakers on a TV or PC".
  - **Pairing:** the PC shows a four-digit code and its address; the phone finds it on the same Wi-Fi and you type the code once. Nothing leaves your network.
  - **Each speaker on its own:** the PC plays front left, then front right, on the output you pick, with the same clips the TV uses. You get level, distance and an EQ per speaker.
  - **EQ through Equalizer APO** (free, installed once): "Use the EQ on the PC" writes one EQ per channel into Equalizer APO for that output only. It reaches everything the PC plays, with no delay, and stays on when the app is closed. While a test plays it is held off, so readings are of the speakers as they are and EQs never stack.
  - **Setup in the window:** the output drop-down, a status line for Equalizer APO, and one key for what is missing: Get Equalizer APO, Open its Configurator, or Allow AutoTune (one Windows administrator prompt, so later EQs need none).
  - **On the PC:** the two speakers are drawn with the playing one lit red and the sweep's frequency; after a test each shows its level and distance change. The EQ has its latching key and a curve per speaker. Closing the window keeps it in the tray, so the phone can still reach it.
- **A tablet or phone can stand in for the TV** (Settings › Advanced › "Use this device as the TV"). It opens the TV's screen: the code, the speaker map, playing each speaker on its own speakers (left and right; more through HDMI), and the TV's EQ on its own sound. Back returns to Settings. Useful to try the home-theatre link without a TV.
### Changed
- The Tune key "Home theatre with the TV" is now "Speakers on a TV or PC". The page says TV or PC to match what is connected, and shows the PC's warnings (for example Equalizer APO not installed) in one line.
- The phone, TV and PC link moved to its own module (`:link`), shared by the phone app and the PC app. A TV from 2.13 still pairs.

## [2.13.0] - 2026-11-10
### Added
- **Home theatre with the TV.** The same app now installs on Android TV and Google TV (it appears in the TV's app row with its own banner). On the TV it plays a test sound on one speaker at a time; the phone listens at your seat. Start it from Tune › "Home theatre with the TV".
  - **Pairing:** phone and TV on the same Wi-Fi. The phone finds the TV and you type the four-digit code the TV shows, once. Nothing leaves your network.
  - **Each speaker on its own over HDMI ARC:** ARC can't carry raw 5.1, so the TV sends each test as Dolby Digital 5.1 and the home theatre routes it to one speaker. With eARC, or a box plugged into the receiver, it sends raw 5.1 or 7.1. The TV picks the method from what its output reports.
  - **Room map on both screens:** the speaker that is playing lights red with rings going out from it. On the phone the rings grow with what the mic hears. The TV also shows the sweep's frequency as it plays. After a test, every speaker shows its level change and distance change.
  - **Result:** level per speaker (in 0.5 dB steps, the sub against the target's bass), distance per speaker (from when its sound arrives against front left), a response chart for all speakers, and an EQ per speaker fitted from the first full test so EQs never stack. A missing speaker, or one the home theatre folds into the fronts, is named.
  - **Pinned key:** test that speaker again, test again after setting levels or distances, or use the EQ on the TV.
  - **EQ on the TV:** the TV can hold the EQ on its own sound, one per output channel, with one shared preamp so the levels stay matched. Sound the TV passes straight through as Dolby or DTS skips it, and the info bubble says so.
  - **On the TV:** "Play each speaker" from the remote, to check every speaker is wired to the right place, and "EQ off".
- Advanced: test one speaker, connect to a TV by address, start over with a new reference, delays in ms.

## [2.12.0] - 2026-11-10
### Changed
- **Gradient edge is the default look.** A fresh install opens it, and so does any saved look that is missing or no longer exists. Looks you chose by name (Classic, Graphite, Black, White) are kept.
- **Gradient edge's default palette**: warm white body, white decks, slate ink, Braun red accent, aluminium hairline. Reset palette returns to these on Gradient edge. Picking "As the look" on Gradient edge keeps the look's own colours.

## [2.11.0] - 2026-11-09
### Added
- **First-start tutorial.** Five cards you swipe through on the first start: what AutoTune does, then the four steps of a tune (measure the stock sound, get a suggested EQ, verify it, keep it). Each card has a small picture that plays when the card comes up: the knob tunes in, the sweep draws the stock response, the faders settle, the reading counts down to the target, the EQ key latches on when the car connects. As you swipe, the picture moves further than the words. Skip is on every card but the last; the key reads Next, then Get started; Back goes to the previous card. It shows once (existing installs see it once after this update), and Settings › About › "Replay the tutorial" shows it again. With "Remove animations" each picture shows its finished state.

## [2.10.0] - 2026-11-09
### Changed
- **Graphics play twice, then stop.** Header art, the Tune dial, report meters and charts play two passes. They replay when you pull a page down from the top. The Tune dial pulses from the centre outward.
- **Scores beside every reading.** The results top card shows each run's score with its dB figure, and the stock and best run too (a box only where a score is known).
- **Runs list is aligned** in fixed columns: tick, name and date, score, dB, delete.
- **Results open with the header full size**, and the chart's expand opens a full-screen sheet inside the app.
- **The redundant headline line is gone**; its words sit in the info bubble next to the warning icon.
### Fixed
- **Palette dark colours take effect by day as well as night.** Where an ink would not read, that surface switches to a readable version. The Ink field says whether the colour reads.

## [2.9.0] - 2026-11-09
### Changed
- **Tune screen starts with the dial.** The wordmark line, the Simple / Advanced key, the "Tune" title and the speaker art are gone from the Tune tab. Other tabs keep their header.
- **Tune dial.** The face is always light with dark dots, in every look. The animation is a dark arc sweeping round the rim; it stays still with "Remove animations".
- **Test results are simpler.** The notes are a warning icon (tap for the list). "Next" sits under the pinned key. "Open the EQ" and "AI review" are at the bottom.
- **Graphics play once** when a page or result appears, then stay still. Report-card meters fill once.
- **Score next to the dB figure** on the results, the Tune result, the EQ step, the Tune panel and the runs list.
### Fixed
- **Palette dark colours take effect at night.** A dark ink now brings the light decks with it, and the Ink field says whether the chosen colour reads. Slate is still too light for the grey plates; the field says so.

## [2.8.1] - 2026-11-09
### Fixed
- **Drop-down menus scroll.** A long menu, such as the palette's colours, was cut off below the field, so the dark colours (Graphite to Black) could not be picked. Menus now scroll.

## [2.8.0] - 2026-11-09
### Changed
- **Design pass on the Braun look.** Action text and field icons are ink; red is kept for what is live or acted on. The notes line is a key-grey row with only its warning icon in amber. The "New test" key is flat. The test list is one deck with hairlines. Failed-measurement choices are one "Try again" key with the rest stacked below. Empty states have a title, one fact line and an info bubble, with no paragraphs on the page.
- **Looks are Braun only.** Classic (was "Braun", still the default), Graphite (was "Braun graphite") and Black (was "Braun black"). Dyno sheet, Studio, Mixing console, Hi-fi blue meters, Analog VU, Synthwave and Navy are removed, along with the round and LED slider styles and the wallpaper-colour switch. A saved look that no longer exists opens Classic.
### Added
- **White look**: a white body with plates that still read as plates.
- **Gradient edge look**: Classic's colours, with each deck's outline shading from dark to light (darker by day or night as the look suggests).
- **Palette in Settings (Advanced)**: pick the body, deck, ink, accent and hairline colours from presets or type a hex value (#RGB or #RRGGBB). Ink adjusts itself when a palette would make text unreadable. "Reset palette" returns to the look's colours.

## [2.7.1] - 2026-11-09
### Changed
- **Design pass on the Braun look.** Action text and field icons are ink; red is kept for what is live or acted on. The "BEST" badge is a lowercase "best" in green. The EQ picker's top corners are 12dp. Report card categories are square ruled meters, with the explanation in an info bubble. Measuring needles follow "Remove animations".
- Not yet tested on a real device: this build is for checking the design pass.

## [2.7.0] - 2026-11-09
### Changed
- **No Stock or Verify page.** A measurement that fails, is stopped or is closed now goes back to where you started it (Set up, the EQ step or the result), so Tune is three screens plus the measuring screen. The step bar still names five steps. A failure stays on the measuring screen with "Try again", "Check the level", "Ask AI why" and "Close".
- **Done is the Tune screen.** Keeping an EQ returns to the Tune screen, which shows the tuned and stock readings and where the EQ plays, with "Use this EQ whenever <device> connects" (or "Open the EQ") as the main key. One screen and one tap fewer.
- **Set up remembers each output.** The target, taste, route, mic spots and window you chose last time on the same output (your car, the soundbar) come back, with one line saying so. Keeping a tune updates the remembered target and taste.
- The microphone is asked for by the measuring key itself when access is missing.
- The note for a run (Advanced) is on the EQ step and the result, "Note for the next run". Since 2.1.1 it could only be typed after a failed measurement.

## [2.6.0] - 2026-11-09
### Changed
- **Set up warns when the phone's EQ may not reach the speakers.** With Android Auto on, or on an output where an EQ check already found the phone's EQ missing, one line under "Plays through" says so before the stock reading. On a proven output the EQ step starts with your equipment; with Android Auto it only warns, because wired Android Auto has carried the EQ since 2.3.1.
- **Done offers the next useful step.** When the phone is playing through a Bluetooth device and the EQ plays on the phone, the main key is "Use this EQ whenever <device> connects": one tap saves the automatic switch and turns automatic switching on. Otherwise the main key is "Open the EQ". "Back to start" and "See all results" are among the other choices.

## [2.5.0] - 2026-11-09
### Changed
- **The Tune key no longer undoes anything.** Set up is a draft now: the test you have open and AutoTune EQ stay as they are until "Measure the stock system" starts the reading. Back from Set up leaves the EQ playing and Continue still picks up the open test where you left it. Before, one tap on Tune switched the EQ off and closed the test.
- **The EQ check decides the next step.** After a run that sounds like the stock, the result still asks for the EQ check, but the check's verdict now changes the main key: if the EQ arrives, "Measure again" (the run missed it); if nothing arrives, "Set it on my equipment". The output is remembered, so the next tune on it starts with your equipment. Running the check again no longer leads back to the same key.
- **A tune measured in the app's preview is measured on the phone's EQ before it is kept.** The preview plays the app's exact filters, while the phone's EQ uses fixed bands and plays a slightly different curve. Where the result used to say "Keep this EQ and finish", it now says "Switch on the phone's EQ and measure" for such a tune; finishing is still one of the other choices.
### Fixed
- **"Measure again, nothing changed" on the phone's EQ measured the new suggested EQ** instead of the one being repeated. It now plays the previous run's EQ again, as on the other routes.

## [2.4.0] - 2026-11-08
### Changed
- **Dark body, light decks.** The Braun look now has a dark graphite background with warm white sections, each with a thin outline. Titles, the main key and the tab keys sit on the dark body in light ink; the header graphic is a light device on it. At night the background goes near-black and the sections graphite, outlined, so nothing glares in the car. Every look's sections now have the outline.
- **Quieter Simple mode.**
  - **Tune:** when the mic is ready, the mic and EQ status is one small line under the speaker ("umik-1 · eq off") instead of a box. If something needs fixing, the box comes back with its buttons.
  - **EQ and Live:** the graph and the spectrum show without a title. Advanced keeps the titles and their (i).

## [2.3.2] - 2026-11-08
### Fixed
- **Suggested EQs hollowed out the upper bass.** Where a car has narrow peaks with a dip between them (an F-150 had peaks at 125, 160 and 250 Hz and a dip at 200 Hz), the fitter placed wide cuts either side and stacked boosts to refill the dip, and the merge that makes the final EQ then threw two thirds of that refill away. On that F-150 the EQ left 120-250 Hz 5.6 dB under the target and 200 Hz 10.6 dB under it. The fitter now merges first and fine-tunes the merged EQ, so what plays is what was balanced: 120-250 Hz now ends 1.3 dB under the target on the phone's system EQ (0.7 dB with AutoTune's own EQ), with smaller cuts at 135 and 257 Hz. The deep bass stays on the target.
- A narrow dip is never read as too loud any more, so a cut beside it can't sink into it for free.

## [2.3.1] - 2026-11-07
### Fixed
- **EQ not applied in Android Auto until you pause and play**: the system effect is now opened again when the output changes (Android Auto, a car, a dock) and when playback starts, so the EQ takes hold without pausing the song. Direct Bluetooth was already fine.

## [2.3.0] - 2026-11-08
### Changed
- **Header: the name and the graphic are one unit.** In the tall header the screen's name now sits beside its Braun graphic, centred on its height, instead of at the bottom left with the graphic at the top right.
- **The graphic folds into the header, like One UI.** As you scroll, the graphic shrinks and glides into the right corner of the slim bar as a small version of itself, and the name settles at the left. Scroll back to the very top and both grow back out. With "Remove animations" on, the header simply switches.
- **The full header always comes back at the top.** A tab you jump to, or go Back to, still opens with the header folded and the content high, but scrolling all the way up now brings the full header back on every screen, pages included. Part-way down the page the slim bar stays.
- **The Simple / Advanced key stays in the middle of the header**, in the tall and the slim form, so it never moves.
- **The three-bar menu is gone.** Export EQ files is a row in Settings (in Simple and Advanced) that opens Export over Settings; Back returns there. In Advanced the EQ tab also has "Export EQ files" in reach, without opening "More". Send diagnostics stays in Settings › Diagnostics (Advanced).
- **Tune is a tuning knob**: the ridged knob of a Braun radio with its pointer and a short tuning scale, on the Tune key, the Tune main keys, "Start tuning" and "Continue tuning", the app icon (graphite knob, red pointer, on light grey), the themed icon and the EQ notification's icon. The Tune speaker keeps "Tune" on its centre key.
- **Settings is a gear again**, a round hub with square teeth in the same line style, so it no longer looks like a knob.

## [2.2.0] - 2026-11-07
### Changed
- **Header**: the name is a mark in capitals, **AUTOTUNE**, on every screen, the measuring screen and the AI review. Each tab keeps its name and its own graphic in the header, now drawn as the face of a Braun hi-fi component: a record player for Tests, a fader strip for EQ, the round speaker grille for Tune, a radio tuning scale with its needle over a slot grille of level bars for Live, a row of push keys and knobs for Settings (a tape deck elsewhere). Flat, in the deck greys, the accent only on one pilot light, needle or key.
- **The tall header shows only on a fresh open**: a cold start, or coming back after 15 minutes or more in the background. Then every tab opens tall once. Jumping between tabs, going Back to a tab and pages over a tab open compact: the slim bar, content high.
- **On/off switches are Braun latching push keys**: a rectangular key that stays down while on, with a small pilot light in the accent. Used for AutoTune EQ, the Dynamic EQ by hand, keep recordings, wallpaper colours, the noise through my EQ, automatic switching and standard bands.
- **Loud EQ is now called Dynamic EQ** everywhere you see it: the EQ tab, the notification ("Dynamic on" / "Dynamic off", "Equalising · dynamic 40%"), the Quick Settings tile and the info text. "This is loud" is "Set this level", "Loud level" is "Full strength". Shortcuts, routines (`dev.autotune.action.TOGGLE_LOUD`, `LOUD_ON`, `LOUD_OFF`), the tile and saved settings keep working.
- **EQ tab order**: the band sliders sit right under the AutoTune EQ switch and the EQ in use, then the response graph, then the rest.
- **More bands than fit** (15 bands): the edge where more sliders wait fades with a small chevron, and a thin bar under the row shows where you are. The cue goes when everything is in view.
- **Icons redrawn as one Braun family**: one stroke weight on a 24-unit grid, plain geometry from Braun controls and speakers (a record for Tests, a knob for Settings, the speaker ring and key for Tune, a needle meter for measuring, a cassette for saving), square ends on straight lines, filled shapes only for knobs, caps and pilot lights.
- **New icons**: bass (a slow, wide wave) and treble (a tight wave) for "to taste", a fader for the preamp, and one per flavour: the Tune mark for As tuned, bass for More bass, a drum for Rock, a singer's mic for Pop, a square wave for Electronic / hip-hop, a guitar for Acoustic / classical, a speech bubble for Speech / podcasts, the volume ramp for Low volume, a moon for Late night.

## [2.1.1] - 2026-11-07
### Changed
- **No stop before a measurement.** The Stock reading screen (microphone, "Plays through", "Before you start", then a Measure button) is gone from the way through: Set up now ends in "Measure the stock system", which creates the test and starts the reading at once. The same goes for every other measurement: choosing how to apply the EQ ("Use the phone's EQ and measure", "It's set, measure"), "Verify this EQ" on the EQ tab, "Measure again" and "Measure the stock again" all start measuring immediately.
- **Another round skips the Apply step** when the way is already known: on the phone routes, "Apply the new suggested EQ" applies it the way you chose in the first round and measures. Setting bands on your own equipment, or a phone EQ known to damage the sound, still shows the Apply step.
- Set up now carries what the Stock screen carried: the "send the sweep file" buttons for another device, the test sound and the microphone check. Without microphone access the main key is off and says so.
- The Stock and Verify screens remain only as the place a failed or stopped measurement lands, with "Try again".

## [2.1.0] - 2026-11-07
### Added
- **Delete any run** (not only the last): each run on a test's results has its own delete; the stock reading stays. The suggestion is rebuilt from what is left.
- **Quick actions on the Tests list**: ⋮ on each test opens Open results, Continue tuning, Rename and Delete.
- **More Braun looks**: Braun graphite (greys with a dark grey accent, no wood) and Braun black (black and aluminium, orange accent), plus a Graphite accent colour.
- The **preamp** goes up to +6 dB as well as down to −15 dB (Manual), for a quiet system; the limiter catches the peaks.
### Changed
- **Header after One UI**: the screen's name sits large in a tall header with a Bauhaus composition of its own (a list for Tests, faders for EQ, a speaker for Tune, a spectrum for Live, circle-square-triangle for Settings), so the content starts lower, nearer the thumb. Scrolled away, a slim bar with the name takes over.
- **Tune** is a Braun speaker front (the T 3's perforated circle) with the round Tune key at its centre, in place of the record.
- **Tabs**: labels in title case ("EQ" in capitals). With AutoTune EQ on, the EQ key stays latched down like a pressed push key; no on/off text or green dot.
- **Braun colours** are greyer, in light and dark.
- **Live** starts silent (no noise until you choose one), and Start / Stop is the main key at the bottom.
- **Results**: the runs (tick to draw, delete) sit right under the response graph.
- **App icon** redrawn: a graphite speaker with the red Tune key and a fader, on light grey; a matching themed icon. The **EQ notification** uses the same mark and Braun red, with short wording ("EQ on", "Equalising · loud 40%", "EQ off").
- Trimmed the 2.0 additions that brought text back: the Tests overview panel, Settings section headings, the results chart heading and hint, the uppercase labels, the two-line Verify reminder.

## [2.0.0] - 2026-11-07
AutoTune 2.0: the Braun redesign (1.27 to 1.29) plus five UI review passes on top of it (see `UI-REDESIGN-NOTES.md` and `PASS5-REVIEW.md`).
### Changed
- **Live**: the spectrum comes first, and while stopped it no longer draws target-shaped bars that could pass for a reading: it shows "standby" until real data arrives.
- **EQ**: the response chart sits right under AutoTune EQ and the EQ in use; the sliders section is "Adjust bands"; the main key reads "Verify this EQ".
- **Tests**: an overview deck (current system, systems, readings); each row says whether the latest run is closer to the target than stock, further, or about the same.
- **Results**: the response chart has a heading and a short legend hint.
- **Measuring steps**: the main key's hint says what the reading is for (Advanced); Verify reminds you to keep the same mic spot and volume.
- **New test** is a graphite key with a red mark instead of a red button.
- **Settings**: section headings for equipment, tuning defaults, playback and data.

## [1.29.0] - 2026-11-07
### Changed
- **Tuning has five steps instead of seven**: Set up · Stock · EQ · Verify · Result. The Tune button goes straight to Set up (a fresh start; the checklist is in its ⓘ, Continue is on the Tune screen), and the stock review is now the top of the EQ step. A reading that looks wrong is measured again before any EQ is shown. A place saved on the old stock review opens the EQ step.
- **Results say less**: a short headline, every warning and finding folded into one "N notes" line, then the next step. The tuning-scale bar is gone.
- **Tests list**: rows show the test's short name (the date for automatic names) and the reading count, without the target name; the page title has no subtitle.
- **Header**: the "autotune" wordmark alone, without the tab name.
- **Tune key**: a round push button in the accent (Bauhaus), pressed in when chosen; a ring pulses round it while measuring elsewhere.
- **EQ key shows "on"**: a green pilot light on the key and "eq on" under it while AutoTune EQ is on.
- **AI review** in the Braun look: the wordmark header, the review on a deck with its summary and next step in one sentence each (the rest in ⓘ), findings as pilot lights with titles only, EQ changes as a compact list, the prediction as a display, notes folded, and the main actions on the main key.

## [1.28.0] - 2026-11-06
### Added
- **Accent colour** in Settings > Appearance: as the look (Braun red), red, orange, yellow, green or blue. It colours what is live or acted on: the Tune record, the needles, the pilot lights and the EQ. Every choice is checked for contrast in light and dark.
### Changed
- The **measuring screen** now matches the Braun look: the live level large and light with a pilot light, the sweep on a radio dial (20 Hz to 20 kHz, the needle follows the tone), the waveform in ink, the level on a meter scale with a needle, and each check as a pilot light (grey, blinking, then green, yellow or red). Teak cheeks frame every screen, the measuring and AI screens included.
- **Bigger tab keys** (62 × 42 dp, larger icons and labels).
- **Tune is an on/off slide switch** in the tab row: chosen, its knob slides to "on" and lights in the accent; it pulses while a measurement runs on another tab.
- **The main key reads clearer**: the action centred in larger type with "suggested" under it, the pilot light at the start.

## [1.27.0] - 2026-11-06
### Changed
- New look, **Braun**, now the default: the app is designed like a Braun device by Dieter Rams. A white body between teak cheeks, a grey deck for each section (no borders, no shadows), and one red for what is live: the record label, the needle, the pilot light and the EQ. Dark mode is Braun black. The other looks stay in Settings > Appearance as colour variants.
- Type is **Inter** throughout, replacing Barlow: readings large and light, labels small and lowercase, tabular figures.
- The tabs are a row of **push keys** with their icons; Tune in the centre is an **aluminium tuning wheel** with a red index (it turns when chosen and pulses while a measurement runs elsewhere).
- Tune home is a **record** with a red "tune" label.
- Results open with a **display**: the reading large and light, the change from stock, the best run, and a **radio tuning scale** with the stock marked grey, the best green and a red needle at this run (dashed for the suggested EQ's prediction).
- The main button is a **graphite key with a red pilot light** and "suggested" on it. Other buttons are grey keys; the AutoTune EQ switch lights green when on; EQ sliders are faders.
- The header is the small "autotune" wordmark with the tab's name beside it.

## [1.26.0] - 2026-11-06
### Changed
- Simple is simpler on every tab; everything taken out is still in Advanced.
  - **EQ:** AutoTune EQ on/off, the EQ in use, the graph and the sliders. Flavour, preamp, Loud EQ, the EQ check, the band count, saved EQs and the rest move to Advanced.
  - **Live:** Start, the level, the bars and the sound to play. The SPL offset, "through my EQ" and Open EQ move to Advanced.
  - **A test's results:** the summary, the graph, the checks worth doing, Open the EQ, AI review and Manage test. Target and taste, Details, notes and "keep as a target" move to Advanced.
  - **Tune:** the result keeps "Keep this EQ", "Keep the best" and "Discard"; the stock review keeps "Measure the stock again". The findings list moves to Advanced (their names and a bubble stay in the summary).
  - **Settings:** Show, Microphone, Appearance and About. Defaults, AutoTune EQ bands, saved files, AI assistant, diagnostics and target curves move to Advanced.

## [1.25.0] - 2026-11-06
### Changed
- Much less text everywhere, in Simple and Advanced: explanations moved from paragraphs on the page into info bubbles (ⓘ) beside the field, group or finding they explain. The four-line note under the EQ picker is now the ⓘ next to "EQ in use"; flavour and preamp notes sit in their own ⓘ; slider and graph notes are gone.
- Results open with the answer: the readings, then one headline ("4.1 dB closer to the target than stock. Run 1 is still the best."), the findings that need work in one line, the warnings folded into one line ("2 notes: …", tap to read them all), and "Next: …" with its reason in a bubble. The test's results page now shows the next step too.
- Warnings are folded the same way on every screen (measuring, EQ, mic). Findings and "Worth checking" show their titles only; a check that can be run is the button itself.
- Shorter labels: "Off"/"On" for AutoTune EQ, "Best: Run 1", "Run 2 vs stock", "Start fresh?".

## [1.24.0] - 2026-11-06
### Added
- **Loud EQ: Follow the sound level.** A third way for the loud EQ to come in, for outputs whose volume AutoTune can't read (Android Auto, and Bluetooth without absolute volume). While AutoTune EQ is on and music plays, the phone's mic hears how loud the cabin is. The loud EQ fades in over the 6 dB below your loud level and plays in full above it.
- **"This is loud"**: play music where it starts to sound strained and tap it to set the loud level from what the mic hears (or pick 75 to 95 dB). Same dB scale as the Live tab's SPL.
- The level is smoothed (rises over about 2 s, falls over about 6 s) and the EQ only moves in 20% steps, so it doesn't pump with the music. Only the level is kept, never the sound.
- The mic closes when no music plays, while AutoTune measures or runs the Live analyser, and its silence during calls is skipped. Android only lets an app open the mic for the background while it is on screen, so after a reboot or a Bluetooth reconnect, open AutoTune once.
- A failed volume check now offers "Follow the sound level instead".

## [1.23.0] - 2026-11-06
### Added
- **Loud EQ** (EQ tab, new card): a dynamic EQ for high volume. Speakers distort as you turn them up, so the loud EQ comes in at high volume and only cuts: it takes back 60% of the bass boosts (below 150 Hz) and treble boosts (above 5 kHz), and cuts where the speakers distort. It keeps the preamp, so it can never clip. Measurements never use it, like the flavours.
- **What it cuts**: a safe default (−3 dB at 40 Hz, −1.5 dB at 3.5 kHz), or cuts made for your speakers: measure once at your loudest listening volume and tap "Use the last reading's distortion". Each octave whose distortion is over the limit (6% in the bass, 3% in the mids, 2% in the treble) gets a cut of 2/3 of the excess, at most 6 dB.
- **Follow the volume**: the loud EQ fades in from a chosen volume (60 to 90%, default 75%) to all of it at full volume.
- **Volume check**: turn the car's volume knob for a few seconds and AutoTune checks whether the knob moves the phone's volume (Bluetooth with absolute volume usually does; Android Auto often doesn't). The answer is remembered per output, and the loud EQ only follows the volume where it was seen to work.
- **Switch it by hand** when the volume can't be followed: the button on the card, a "Loud EQ on/off" button on the notification, a **Loud EQ tile** in Quick Settings, the **volume keys** (down, up, down, up within 3 seconds; the volume ends where it was), or Routines and other apps (`dev.autotune.action.TOGGLE_LOUD`, `LOUD_ON`, `LOUD_OFF`).

## [1.22.0] - 2026-11-06
### Changed
- New EQ picker (EQ tab and Export). The field shows the EQ in use in one line ("Run 1 · 17.9 dB from target") with its test under it. Tapping it opens a sheet: the test of the EQ in use is open at the top, one line per choice with its reading at the end and a BEST tag on the best run; every other test and the saved EQs are one line each ("best 21.1") that opens when tapped. Replaces the long drop-down with wrapped two-line entries and "Show more" rows.
- Test names the app made ("Car, Harman-style (+8 dB bass) test, 5 Nov 01:52") show in the picker as the date over the short target ("5 Nov 01:52" / "Car, Harman-style"). Names you typed stay as they are.

## [1.21.0] - 2026-11-04
### Changed
- Five tabs: Tests, EQ, **Tune** (a raised disc in the centre of the bar), Live and Settings. Live and Settings are tabs now instead of a page and a full-screen overlay; Export is in the menu and opens as a page. A pill slides to the chosen tab; while a measurement runs on another tab, rings pulse out of the Tune disc.
- Simple / Advanced: a drop-down at the top of every screen (and in Settings > Show). **Simple is the default** and applies to the whole app: shorter wording and the main choices only. Advanced brings back the explanations, extra checks and every option (measurement settings, notes, adjust by hand, details).
- Sections are cards: every group of a screen (what was measured, what is set, the other choices, settings groups, folds) sits in its own card with 16dp corners and a hairline border, no shadows. A card inside a card draws flat, never nested.
- Actions stack: the other choices and button pairs sit one per row, full width, under each other.
- The pinned button is labelled **Suggested**: it is the one recommended move, usually continuing the Tune workflow. Its reason shows in Advanced, or when something blocks it.
- Tune steps are simpler: the Verify step keeps the run, the mic, Plays through and the Suggested button; the loudness note, Test sound, Check the level and Back to the EQ are in Advanced. Step titles stay on one line ("Verify", "Apply the EQ"), so a long title no longer wraps up under the status bar.
- Motion that explains: tabs slide in from the side of the tab you chose, pages rise in and drop away on Back, Tune steps slide forward and back, cards and folds open smoothly, the pinned button slides in when the recommendation changes, buttons press in slightly, and the step bar fills in. All of it follows the system "Remove animations" setting.
### Added
- Navy look in Settings > Appearance: the shared DevStore design language (deep navy, one teal accent), in dark and light.

## [1.20.0] - 2026-11-05
### Changed
- Closer EQ suggestions: after placing its filters one at a time, the fitter now fine-tunes all of them together (gain, Q and frequency), as REW does. Overlapping filters no longer overshoot each other. On the simulated room the error left after EQ fell from 0.48 to 0.30 dB, and on the phone's system EQ from 1.04 to 0.77 dB. All limits stay: 3 dB per boost, the boost floor, the system EQ's Q limits, and the half-step fit.
- Cuts above 1 kHz now get gradually wider towards 10 kHz (Q up to 4 at 1 kHz, 3 at 10 kHz, as in REW), and a boost is never narrow enough to ring for more than half a second.

## [1.19.0] - 2026-10-30
### Changed
- New look, Dyno sheet, now the default: every result reads like a dyno run sheet. Chart-paper ground with a fine grid (graphite at night), a shop-green navigation band, marker orange for what you act on (your EQ, the main button, the Tune disc) and plotter-pen colours for the runs. The other looks (Studio, Mixing console, Hi-fi blue meters, Analog VU, Synthwave) stay in Settings > Appearance; a look you picked before is kept.
- Type is Barlow (text) and Barlow Condensed (readings and headings), with tabular figures so readings line up and don't jitter while they update. It replaces IBM Plex.
- Results open with a run sheet: a header strip naming the test and run, then the readings in ruled cells (stock, latest, best so far or the change from stock), the target, the warnings and the next move.
- The response graph is drawn on chart paper (minor lines at every step, heavier ones at each decade and every 10 dB), and the stock reading is the dashed baseline under the runs. The key draws each curve's stroke, dashed or solid, instead of a dot.
- Back always returns to where you were: a test's results, Live spectrum and Export open as pages over the tab you came from, with a back arrow, instead of switching tabs. Each tab keeps its scroll position.
- One tap on a test in the list opens its results; Continue tuning is pinned there and Delete is under Manage test (replaces the inline actions from 1.17.0).
- The Tune step header is one row: Back, the step's name with "Step n of 7 · Next: …" under it, and Exit, over a slim progress bar.

## [1.18.0] - 2026-10-17
### Added
- Preamp setting on the EQ tab: Safe (lowers everything by the biggest boost, never clips), Balanced (half the boost, the limiter catches the rare peak), Off (full level, limiter only) and Manual with a slider (-15 to 0 dB). The preamp in use is shown, with a reminder that turning the volume up is the normal fix for a quieter EQ.
- Flavour on top of the tune: As tuned, More bass, Rock, Pop, Electronic / hip-hop, Acoustic / classical, Speech / podcasts, Low volume and Late night. It plays on AutoTune EQ (also from widgets, the Bluetooth watcher and restarts) but is never part of a measurement, so tuning stays correct.
- Settings > AutoTune EQ: 31 bands (1/3 octave, the default) or 61 bands (1/6 octave) for finer narrow cuts; later fits use band widths the setting can realise. The info button lists what Android limits and what AutoTune does about each.
- How the speakers followed the EQ: after each run, octave by octave, what the EQ asked for against what the mic heard change, with the share of the EQ that arrived. Boosts at the bottom that did nothing (bass below what the speakers play) are found, and later suggestions stop asking for them (saved with the test).
- Worth checking: further tests suggested from the readings, each with its button: measure the stock again louder after a weak reading, use 3 mic spots in a car, run the EQ check when the sound followed little of the EQ, measure again with nothing changed when a run came out far from what was expected.
### Changed
- Measuring with AutoTune EQ on always uses the EQ alone with the safe preamp (no flavour, no limiter at work), then puts back what was playing.

## [1.17.0] - 2026-10-17
### Added
- EQ check: "Check the EQ reaches the speakers" measures once with AutoTune EQ off and once with the EQ in use on, and compares the change with what the EQ should do. It says whether the EQ arrives, only partly, or not at all (Android Auto, wireless or cable, and some car systems take the sound past the phone's own EQ), with a graph of the measured change against the EQ. On the EQ tab and the Result step; it is the recommended action when a run barely changed. AutoTune EQ is left the way it was.
- Looks: Studio (the original), Mixing console, Hi-fi blue meters, Analog VU and Synthwave, each in dark and light, with their own sliders (fader caps with a scale, or LED columns). Settings > Appearance.
- Settings > Defaults for new tests: target curve, bass and treble taste, mic spots and analysis window. Set up starts from them.
- Settings > Saved files: pick the folder Save writes to (falls back to Downloads/AutoTune if it goes away).
### Changed
- The main action of every screen is pinned as a big button above the tabs, with one line saying why, so it never scrolls away: measure, review the suggested EQ, apply and measure, the recommended move after a run, continue tuning, measure this EQ.
- The graph is always shown after a reading: on the Stock review, the Result step and the test's results (no longer folded under Details).
- The EQ picker shows each test's suggested EQ, best run and kept EQ first; "Show N more" opens the rest of that test only. The open test is listed first.
- Tapping a test in the list shows its actions again (Results, Tune, EQ, Delete).
- AI review left the menu: it is on the results it reviews.
### Fixed
- The AI review showed an older review after a new run. A stored review is used only for the same runs and the same focus run; otherwise the newest run is reviewed at once.

## [1.16.0] - 2026-10-17
### Added
- Two fuller target curves: "Car, Harman-style (+8 dB bass)" and "Room, Harman-style (+6 dB bass)", shaped like the listener-preference curves (bass shelf below about 100 Hz, flat mids, treble falling towards 20 kHz). Approximated from the published shape, not Harman's own data.
- Target and taste: on the Stock review, Apply, Result and Test results screens, pick the target curve and add bass (-3 to +12 dB, a shelf below about 100 Hz) and treble (-6 to +4 dB, a shelf above about 4 kHz) to taste. Every run is scored again and the suggested EQ is refitted at once; the taste is saved with the test. More bass in the target means AutoTune cuts less of the car's own bass.
- Graphs: tap a name in the legend to hide or show that curve (crossed out when hidden), and the corner button opens the graph full screen.
- Each recording now looks for the phone's voice processing (automatic gain control, noise suppression, echo cancelling) and switches off whatever is attached. The measuring checks show it under "Microphone (raw sound)", and the log records it.
- The EQ tab warns when slider changes boost more than 6 dB, and "Remove my slider changes" shows even while the sliders are locked.
### Fixed
- Graphs were cut off at ±15 dB, so a response further from the target drew as a flat line along the edge, which looked like clipping. The range now grows to show every curve (up to ±45 dB).

## [1.15.0] - 2026-10-16
From the 1.14 UX audit (source review). Not tested on a phone.
### Changed
- One result, two layers. The Stock review, the Result step and a test's results page all start with the same summary (distance from the target, stock → latest, best so far, warnings, what to do next). Charts and diagnostics sit under Details, closed until opened.
- Test results lead with the summary and Continue tuning; Open the EQ and AI review are links. Chart, runs, compare, report card, tonal balance, resonances, distortion, suggested EQ, keep as target, notes and managing the test are each a closed section. Tapping a test in the list opens it directly.
- Set up asks for the name, target and route; mic spots and the analysis window moved under Measurement options with the same defaults.
- Every Tune step has labelled Back and Exit buttons and a progress bar with the step names (Start, Set up, Stock, Review, EQ, Verify, Result). Exit keeps your place: Continue returns to that step, or to the latest result when it no longer fits.
- Apply the suggested EQ offers three plain choices (the phone's EQ, preview in the app, set it on my equipment); sliders, exact bands and files are under Advanced. When the EQ picked is not the suggestion, the step is titled "EQ currently in use" and links back to the suggestion.
- Consistent names: "Suggested EQ" for AutoTune's proposal everywhere, "EQ currently in use" on the EQ tab, which also says the phone's EQ affects only this phone's audio and depends on the phone.
- Verify shows the mic-spot reminder instead of folding it away.
- The menu is grouped into Tools (AI review, Live spectrum, Export EQ files) and App (Settings, Send diagnostics). The Tests button is "New test".

## [1.14.0] - 2026-10-15
### Added
- Google Gemini as a second AI for the review, beside Claude: Settings > AI assistant has an AI drop-down (Claude or Google Gemini), a key for each, and each one's models (Gemini 3.8 Flash recommended, Gemini 4 Argon, Gemini 3.5 Flash-Lite). The review and the follow-up questions work the same with either.
- Stock analysis: after the stock reading, a screen says how far it is from the target, what that means, what the suggested EQ is predicted to reach, and what it found: boomy or thin bass, muddy, forward or harsh regions, strong peaks, ringing that EQ cannot shorten, hardware limits from the report card, and measuring warnings. A reading that looks like a measuring problem says so and offers to measure again.
- The notification has "Switch EQ off" and "Stop auto-detect".
- Process map and navigation map of the app (published separately) to judge the flow.
### Changed
- One clear action after every measurement. The Result step works out one recommended move (apply the new suggestion, keep this EQ, keep the best run's EQ, or apply the EQ again when it never reached the speakers), says why, and lists the other choices under it. The measuring view's button reads "See the result".
- Back inside Tune goes to the previous screen instead of leaving the workflow; every step header has a back arrow beside the cross that leaves.
- Continue tuning lands on the stock analysis when only the stock reading exists.
- Test results have one primary button (Continue tuning).
### Fixed
- AutoTune EQ refused to switch on when no EQ was picked. It now switches on flat, and the EQ picked next plays at once.
- Widget toggles opened the app every time. They now run in the background (a broadcast instead of an invisible screen); shortcuts no longer bring the app forward either.
- The app now follows changes made from a widget, the notification or the Bluetooth watcher while it is open, automatic switching included.

## [1.13.0] - 2026-10-15
### Added
- AI review with Claude. On the Result step, a test's results, a failed measurement ("Ask AI why") or the menu, Claude reads the test and says what limits the result: measurement quality (signal, repeatability, an EQ that did not arrive, mixed calibrations), setup and speakers (sub phase or level, a crossover notch, a hot tweeter), the EQ (boosting into nulls, over-correction, converged) and the target. Each finding has a severity, the numbers behind it and one action, then the single next step.
- EQ changes from the review are added to AutoTune's suggestion and held to the app's own limits (cuts to 12 dB, +3 dB of boost in total, Q the route can realise, at most 6 changes). The screen shows the predicted distance from the target with and without them, warns when the app predicts worse, and "Use this EQ" puts it in "EQ in use" as "AI-adjusted EQ", ready to measure. It is also in Export.
- Follow-up questions about the test, answered with the data in view.
- Settings > AI assistant: your Claude API key (encrypted with the phone's keystore, never in diagnostics) and the model (Sonnet, Opus or Haiku, as a drop-down), with a key check.
- The last review of each test is kept and marked when runs were added after it.
### Changed
- The app now has internet permission, used only for the AI review. What is sent is the test's numbers (responses as dB from the target at third-octave points, EQs, warnings, checks, notes, phone model and mic name), never recordings.
- The diagnostics log writes every control character safely.

## [1.12.2] - 2026-10-15
Bug fixes found by comparing with the separately audited 1.9.1 build. Checked against the 12 real LG soundbar recordings; untested on a device.
### Fixed
- The start-beep detector looks for the beeps' structure (three equal beeps 0.24 s apart with silence between them) instead of one strong peak. On one real recording the old detector took a stretch of the sweep for the beeps; the new one finds nothing there, so the reading falls back to the timed search. It finds the beeps on the other 11 recordings within 7 ms of before, and in a new reverberant-room test.
- The "clocks differ" warning and the "Clocks in step" check are gone. On real recordings the estimate swung from -68 to +79 ppm between identical runs, so it was warning about noise. The estimate is still written to the diagnostics log.
- The first AutoTune EQ of a test used narrow bands the phone's 31 fixed bands cannot reproduce, so the first run only asked to cut the same spots again. Before switching AutoTune EQ on for the first time, the stock suggestion is refitted with bands it can realise (unless you already changed the EQ).
- If the phone restarted the EQ service after a reboot or an update, the EQ only came back when Android had killed the service. It now comes back whenever it was left on, and the notice stays up (saying so) if it could not start.

## [1.12.1] - 2026-10-12
### Changed
- The measuring screen keeps its layout but uses the app's own clean look, in light and dark: a progress ring that fills as the sweep climbs with the live level in the middle, a plain waveform line, the signal meter, and the checks as a list with a spinner on the one being worked on. The waveform and meter only show while recording.

## [1.12.0] - 2026-10-12
### Added
- Measuring takes the whole screen, with the live microphone waveform and a progress indicator for the sweep. A line says what is happening right now (waking the speakers, start beeps, "Sweeping 368 Hz", listening to the room ring out, analysing), with the live signal level in dBFS on a meter marked too quiet / good / clipping.
- The checks are listed and ticked as they are done: microphone (and its source), sound output, start beeps, whole sweep heard, no clipping, signal above the background (dB), every frequency heard, clocks in step. Each shows fine, worth a look, or failed, with the number behind it.
- A clean reading closes by itself after a moment with its result; one with warnings stays until Continue. A failure says why and offers Try again; with several mic spots it asks for the next spot. Back or Stop cancels a running measurement.

## [1.11.0] - 2026-10-12
From the first diagnostics (LG soundbar over Bluetooth, Galaxy S23 Ultra). Untested on a real device beyond that log.
### Fixed
- A stock reading failed ("barely heard", signal-to-noise 9 dB) because weak start beeps let a stretch of the sweep pass as the marker. A marker the sweep could not fit after is rejected, and a doubtful reading through the marker is checked against a search of the whole recording; the better one is kept. The same recording now reads at 42 dB signal-to-noise.
- With AutoTune EQ on, a run recorded the phone's 31 fixed bands (Q 4.3) as its EQ, and finishing saved and exported those 22 bands. Each run now also keeps the parametric EQ behind them; that is what is kept, exported and shown. The fixed bands are still used to judge what the mic heard.
- The tuning loop swung between two EQs for seven runs (the best one and a deeper-cut one that measured worse every time). When the next suggestion is an EQ that already measured worse, the run says tuning has converged and offers to keep the best EQ; Refine is hidden then.
- Twin bands at one frequency (for example three +3 dB bands at 38 Hz) are merged in the suggestion.
### Changed
- One place to pick the EQ in use: a drop-down grouped by test, with a car or home-theatre icon, listing the next suggestion, the EQ of every run (with its result, the best one marked) and the saved EQs. The separate profile list is now "Saved EQs" for renaming and deleting only, and "Go back to the tuning EQ" is gone.
- Tuning no longer saves a profile for every step ("LG · Run 3"): each run keeps its own EQ. Those automatic profiles are removed once (only for tests that still exist). Finishing keeps one "tuned" EQ per test and updates it next time.
- The sliders are locked against accidental changes; tap the lock to adjust.
- Export is three choices: which EQ (grouped by test and run), which app, save or share.
- "Keep this sound as a target" moved to the test's results.
- Car, home theatre, flat and custom curves have icons and a line saying what they mean; the test's icon shows above Tune and EQ.

## [1.10.1] - 2026-10-12
### Fixed
- "Measure the stock again" is on the Apply step. When the stock reading looks wrong (more than 12 dB from the target, or whole bands not heard) it is the main button, and the EQ is only offered after "Use this reading anyway". The stock step says it replaces the old reading; Back returns to the EQ.
- A clean run through AutoTune EQ clears the old "damaged the treble on this phone" warning.

## [1.10.0] - 2026-10-12
Untested on a real device.
### Added
- Tune is a step-by-step workflow behind a big pulsating Tune button: fresh start (asks first and switches AutoTune EQ off), set-up, stock reading, apply the EQ, measure with it, result, done. The EQ is shown and applied inside the workflow (graph, bands, sliders on top); a finished run stays in the workflow instead of jumping to another tab.
- Finishing saves the measured EQ as a profile, makes it the one in use and keeps AutoTune EQ on with it (phone route). If an earlier run was better, finishing offers that run's EQ.
- "EQ in use" on the EQ tab: the suggestion from tuning, a profile or your own bands, picked from a drop-down. Choosing one never switches the EQ on by itself.
- Diagnostics: every measurement (recording level, sweep lift, EQ in effect and route, SNR, latency, warnings, 1/3-octave response), every EQ switch and every failure is logged, and the last 12 recordings are kept. Menu > Send diagnostics shares one zip; Settings can stop keeping recordings or clear it all.
- Icons on buttons, menus, the tab bar and the workflow.
### Changed
- Every choice is a drop-down, including the output picker; no pills or segmented buttons anywhere.
- The bottom tab is "Tune"; the Tests button starts tuning. Tests > a test > "Continue tuning" picks up where it stopped.
- Total boost is capped at +3 dB (was +4), so the preamp is at most about -3.3 dB. The measuring sweep keeps its level through the EQ (the start beeps are lifted too), and the step says how much quieter the EQ makes things and that turning the volume up for the reading is fine.
### Fixed
- After a run the EQ used to stay on an old profile: a new run now always shows the new suggestion, and the EQ tab says which EQ is in use.
- "Measure again" after previewing inside the app measured without the EQ (and recorded it as applied). The in-app route now stays in force for every run until another route is chosen, and "nothing changed" repeats exactly the previous EQ.
- With three mic spots the first tap looked like nothing happened; the screen now says "Spot 1 of 3 done, move the mic" and offers to start the spots over.
- The widget's EQ button switches the last EQ back on, not an older profile.

## [1.9.2] - 2026-10-12
Second pass over the 1.9.0 audit: the points 1.9.1 missed or did only in part. Untested on a real device.
### Fixed
- What a run records as applied follows the EQ on screen (the latest ticked run), not always the last run (audit B9).
- Changing how the EQ is applied (AutoTune EQ, equipment, in the app, nothing changed) restarts a half-finished set of mic spots, so one averaged run never mixes two EQ states (B9).
- "Verify with measurement" follows the way the test applies its EQ: after setting it on the head unit it measures the head unit, not an in-app preview. The other way is one tap below it (D).
- Exported files contain the slider changes too (they held only the exact bands when both were in use).
- After a reboot the EQ comes back on if it was on (C3, the boot half).
- The signal-to-noise figure is read from the quiet stretch before the direct sound, not from the cabin's ringing tail, as the per-frequency check already was (A5).
- Start beeps: the three-beep check is now as specified (side peaks within 2 ms, at least half the main one) and the search reuses one set of buffers, so it stays under 40 MB on a 60 s recording (A1, A6). The sweep analysis uses four large arrays instead of six (A6).
- Live: no dB SPL number with the phone microphone, where it would mean nothing (C7).
- Widget buttons that open different screens no longer share one tap target (C6).
### Changed
- "Convert to 5/10/15 bands" sits in Advanced > Exact bands, as the audit asked. The report lists each run's calibration and warnings.
- Stale comments about the removed Absolute/Delta mode are gone; averaging documents that decay times come from the first spot only.

## [1.9.1] - 2026-10-12
Fixes from the 1.9.0 code audit. Untested on a real device.
### Fixed
- The suggested EQ no longer grows from run to run. Each new run is fitted against the stock response and the EQ that was really applied (a closed loop), so measuring the same correction again gives the same EQ, not a doubled one. If the EQ that should be on had almost no effect on the mic (not set on the head unit, ignored by the phone), the run says so and the cut is not stacked. If a run is worse than the best earlier run by more than 0.3 dB, the suggestion goes back to the EQ of the best run.
- What a run records as applied is exactly the EQ that was shown (boost-capped, with the slider changes), the 31 bands the system EQ really realises, or the chain played inside the app. Before, a boost shown as +1.7 dB was stored as +3.0 dB.
- Start beeps: the search is windowed and structural (three beeps with the right spacing), so a sweep without beeps is not mistaken for the marker. This also repairs the claim in 1.4.0 below that old test files without beeps still work: they did not until now.
- A wrong sweep start no longer gives a plausible but meaningless response; the strongest arrival in the whole recording wins and a warning says so.
- Microphone calibration is saved and restored when the app restarts, with a Remove calibration action. Each run records which calibration it used; Results warns when the runs of one test used different ones.
- Measurement warnings stay with their run and show on Results for every run, not only the stock run. A failure message stays until the next measurement starts, also when the tab changes.
- Memory: only the part of a recording around the sweep is transformed (1 s before the beeps to 4 s after the sweep). An out-of-memory error now ends the measurement with a message instead of closing the app.
- System EQ: the fit for the system-EQ route is limited to what 31 fixed bands can realise; the service restarts itself and restores the EQ if Android kills it; widgets show the wanted state. The check that switches the system EQ off now looks for a treble drop of more than 12 dB (2-8 kHz) against the stock run; the old text match never fired.
- The graphic sliders no longer replace the tuned EQ. They add a small layer on top and show what the EQ really does at each frequency. "Convert for a head unit" (Advanced) turns the result into 5, 10 or 15 fixed bands and says how much it differs.
- The microphone source is UNPROCESSED only where the phone supports it, otherwise VOICE_RECOGNITION or MIC, and the run warns which was used. A deleted target curve can no longer crash the app.
### Added
- "Nothing changed, measure again" on the Measure screen: a repeat run that counts the previous EQ as still applied (labelled "Run N (repeat)").
- "Discard latest run" on Results, with confirmation.
- A warning banner on Results when the latest run is about the same as the stock run although an EQ was applied.
- Harmonic-distortion, FDW and clock-drift estimation (warns above 50 ppm), cut smoothing and a Q limit for high-frequency cuts in the fitter (less fitting of position-dependent combs).
### Changed
- New tests default to three mic positions for a car and to the sweep + pink file for another device. Sliders are 48 dp wide.
- Removed the unused TuningSession, the in-app multi-sweep loop, SweepCheck fields nobody read and the old Before-run writer.

## [1.9.0] - 2026-10-11
### Added
- Home-screen widgets: a wide panel (5x1; stretch it to 5x2 for a profile switcher) with Tune, EQ on/off and Auto on/off; plus separate EQ toggle, Auto toggle and Profile (tap for the next profile) widgets. Tune opens the Measure screen. Widgets update when the EQ or automation changes anywhere.
- REW-style analysis: frequency-dependent windowing (Settings in Advanced under Measure: Fixed, FDW 15, 8 or 5 cycles); harmonic distortion read from the sweep (HD2, HD3, THD vs frequency, shown on Results, tested against synthetic distortion); choice of smoothing on the Results graph (none, 1/24, 1/12, 1/6, 1/3 octave).
### Changed
- Bottom bar is three centred tabs (Tests, Measure, EQ). Live, Export and Settings are in a three-bar menu at the top right of every screen.

## [1.8.0] - 2026-10-11
### Added
- The phone's own microphone can be used when no USB mic is plugged in (it was blocked before). The app says so on the Measure, Live and Setup screens; a USB mic is still more accurate.
- Live: a signal generator on the phone, Pink noise, a repeating Sweep (10 s plus a pause) or Off, switchable while the spectrum runs.
### Changed
- Bottom bar is Tests, Measure, EQ, More. More holds Live, Export and Microphone and settings. Measure has a Live spectrum shortcut; Back from Live or Export returns to More.

## [1.7.0] - 2026-10-11
### Changed (Measure v2, from the Navigation & UI/UX review)
- The Measure screen has one job: it names the run ("Stock reading", "Run 2 with AutoTune EQ"), shows the mic and output state, and has one big Measure button. Instructions sit in a "Before you start" fold (open for the stock reading only). Spots, channel, level check, left/right and subwoofer are under "Advanced".
- A finished measurement opens Results by itself. Results offers "Open suggested EQ" (primary) and "Measure again".
- A failed measurement explains why and the big button becomes "Retry".
- The EQ tab's primary action is "Verify with measurement", which applies the EQ the way the test is set up and opens Measure.
- The old Stock / Apply / Measure / Review step cards and the stepper are gone, except the apply instructions (shown when the next step is to apply the EQ).
- Not done yet: the three-tab bottom bar (Tests, Measure, EQ, plus More), the two-stage setup, and a Verify before/after screen.

## [1.6.0] - 2026-10-11
### Changed
- EQ tab: the main control is now a classic graphic equaliser, vertical sliders (+/-12 dB) with 5, 10 or 15 bands. A tuned EQ shows up on the sliders as it is; moving one makes it your own. Flat and Save sit next to it.
- Profiles are grouped: one folder per test ("Saved by hand" for your own), with counts, newest first; the folder holding the profile in use opens itself.
- Everything else (exact bands and the parametric editor, listen / measure, automatic switching) moved into one "Advanced" section.
- Removed Absolute / Delta. The EQ is always the whole EQ; files no longer carry a `_delta` tag.
### Fixed
- Why the preamp reached -12 dB: boosts from run to run piled up. The suggested EQ is now capped at about +4 dB of boost in total (the rest of the correction is done by cuts), so the preamp stays near -4 dB.
- Measuring through an EQ with a negative preamp made the sweep too quiet to analyse. The phone sweep is now raised by what the EQ takes away (up to 12 dB).
- The phone sweep has the same three start beeps as the test files; the analysis looks for the sweep where the beeps say it should be, and measures background noise from the quiet before them.

## [1.5.1] - 2026-10-10
### Changed
- EQ band lists (Export, Tune "Apply", EQ tab) are a real table now: every band of the chosen set on its own row with separators, a bar that runs left for cuts (red, bold) and right for boosts (amber), and the gain. The preamp sits on top as its own "set this first" row. Frequency and Q are on two lines so nothing wraps.
- Compared with the EQ that was set before the last run: changed rows are tinted and say "was +1.0" (or "new" / "removed" for exact filters), unchanged rows are dimmed, and a switch shows all bands or only the changed ones.
- Automatic switching: the device and the profile are drop-down lists. Each device shows its type, the last five characters of its address, when AutoTune last saw it connect and any profile it already uses, so devices with the same name can be told apart.

## [1.5.0] - 2026-10-10
### Changed
- EQ tab simplified: on/off, the graph and Profiles on top; Bands, Try it (listen, measure, export) and Automatic switching are folded away until opened, each with a one-line summary.
- Profiles: saved EQs. Tuning creates one automatically each time its EQ is applied, named after the test and run and saying the device and which run it came from; "Save current" keeps one built by hand. Tap to use (live switch if the EQ is on), rename, update or delete from the menu. Profiles also appear as app shortcuts (long-press the icon).
- Graph colours are far apart in hue (grey, cyan, fuchsia, coral, violet...), the EQ stays amber and the prediction is a dashed white line. The target line can be hidden by tapping "Target" in the legend (hidden by default on the EQ tab, shown in results).
- Tests list: tap a test to expand Results, Tune, EQ and Delete (with confirmation).
### Added
- Automatic switching: choose a paired Bluetooth device and a profile; when the device connects AutoTune switches to it, and can switch the EQ off when it disconnects. Runs as a small foreground service while turned on, and restarts after a reboot.
- Samsung Modes and Routines: a profile can be started from an invisible screen opened by an app shortcut (one per profile, up to four, and "EQ off"), by the intent `dev.autotune.action.APPLY_PROFILE` with extra `profile` (name or id) or `EQ_OFF`, or by the link `autotune://profile/NAME`. Not tested with a real routine.

## [1.4.0] - 2026-10-09
### Changed
- The test files for another device start with a marker: three short rising beeps, then a 1 s pause, then the sweep. The app finds the marker by correlation, so it knows exactly where the sweep starts even with noise or talking before it. Old files without beeps were meant to still work (found by the first sustained sound); that only became true in 1.9.1.
- Scanning is much shorter: the app waits for the beeps (up to 30 s), then stops by itself right after the sweep. Sweep file is about 9.5 s long and a scan about 10 s once it starts (was a fixed 30 s). Sweep + pink file is about 20 s (was 26 s, recorded for 40 s); the pink part is 10 s.
- Existing sweep files you already sent to the other device have no marker. Send the new file once.

## [1.3.0] - 2026-10-09
### Added
- Absolute / Delta switch for every EQ after the first run (EQ tab, Tune "Apply" step, Export tab; remembered). Absolute is the whole EQ to set, replacing what was set before (the old behaviour for the lists and files). Delta is only the change since the last run, to add on top of the bands already set. Delta files carry `_delta` in their name.
- AutoTune EQ, the in-app preview and the prediction chart always use the full EQ, whatever the switch says.

## [1.2.4] - 2026-10-09
### Changed
- Background noise is no longer judged from the quiet before or after the sweep. Like REW and ARTA, the app reads the noise floor from the deconvolved response itself (the stretch after the gate), per frequency, so it holds even when the car is noisy or the Bluetooth start-up muddies the silence.
- The phone sweep is 10 s instead of 6 s: about 2 dB more signal over the noise at every frequency.
### Fixed
- Removed the check that compared each time slice of the recording with the background; it gave false "never rose above the background noise" errors on a loud sweep.

## [1.2.3] - 2026-10-09
### Fixed
- "Part of the sweep never rose above the background noise" appeared while the sound was loud in the car. The check compared total loudness with the background, which in a car is mostly bass rumble, so a sweep that was clearly loud at mids and highs still "lost". Each part of the sweep is now compared with the background in its own frequency band.
- That message no longer stops the run when the recording itself is good (signal-to-noise 25 dB or better); it becomes a warning and the chart shows any real hole.

## [1.2.2] - 2026-10-09
### Changed
- The screen follows the phone upside down (portrait and reverse portrait), so you can hold the phone with the USB mic pointing up.
### Fixed
- AutoTune EQ (the system effect) removed everything above about 2 kHz on a Samsung, while the stock run was fine. When a run with it on loses the treble, AutoTune switches it off, says why, remembers it, and makes "Try it inside the app" the main way to apply the EQ on that phone.
- "Plays through: remote-submix" is no longer shown as the output; it means a screen recorder or cast app is capturing the sound, and the app now says that.

## [1.2.1] - 2026-10-09
### Fixed
- 1.2.0 forced the sweep to the phone's Bluetooth output. When the car plays the phone another way (Android Auto, USB, or Bluetooth that isn't the active media route) nothing came out. The sweep now plays wherever the phone plays music again; a specific output is used only if you pick one.
### Added
- "Plays through" shows where Android is sending sound (Android 13+) and, after a measurement, where it actually went. If it went into the USB mic, the measurement stops and says so.
- Test sound: two seconds of pink noise to check you hear it from the speakers before measuring.
- When a measurement hears nothing while AutoTune EQ is on, the message says to switch it off and retry.

## [1.2.0] - 2026-10-09
Measurements you can trust. Fixes the "too loud" one run, "can barely hear it" the next.
### Fixed
- False "mic clipped": any single full-scale sample anywhere in the recording counted, including the click a USB mic makes when it starts. Now it takes several clipped samples while the sweep plays, and the message names the frequency where it happened.
- Late Bluetooth start cut off the end of the sweep (the high frequencies), which produced readings like 31 dB from target and "low signal-to-noise". The phone now plays 1.5 s of silence first to wake the link, and keeps recording until 3 s after the sound has been sent.
- A sweep that was cut off or partly never reached the mic is rejected (not saved), and the message says which frequencies were missing.
- The test sound could go to the USB mic itself (many measurement mics also register as headphones) instead of the car. AutoTune now picks the output itself: Bluetooth first, never the mic.
### Added
- "Plays through" line with a picker on the Tune cards, and a warning when the phone treats the mic as headphones.
- Level check rewritten: measures the background, the test sound and the peak, and says in words whether to turn up or down. The result stays on screen.

## [1.1.0] - 2026-10-09
The tuning flow now tells you what to do next, every time.
### Changed
- The Measure tab is now **Tune**: a four-step loop (Stock, Apply EQ, Measure, Review) with one card and one main button at a time.
  - After the stock run: see the result and the suggested EQ, then turn on AutoTune EQ in one tap, or set it on your equipment (bands listed, files saved), or try it inside the app only.
  - After each run: stock vs latest side by side, then "Apply the tweaks", "Measure again" or "Export the EQ" when it is tuned.
- Mic status is one line when everything is fine. Settings (mic spots, channel, level check, start over) fold away.
- Opening a test offers "Continue tuning".
### Fixed
- A clipped measurement is no longer saved; the app asks you to lower the volume and measure again.
- Leftover "Tap Next" messages from the old wizard are gone.
### Added
- Discard a run that went wrong. A hint when AutoTune EQ seems to have no effect on this phone.

## [1.0.0] - 2026-10-09
First full release: the app is rebuilt around what it does instead of a seven-step wizard.
### Changed
- Five tabs: Tests, Measure, EQ, Live, Export. The EQ is a main function, available from the start and tied to the results.
- New visual design: dark instrument look with one amber signal colour, IBM Plex type, a Measure ring that shows recording progress, light theme included.
- One Measure button for the stock run and every later run.
- Results (graph, runs, compare, report card, resonances) live in the open test; setup, theme and target curves moved to a Setup screen.
### Added
- EQ works without a test, remembers "My EQ", and shows the predicted result on the measured response.
- "Measure with this EQ": play the sweep through AutoTune's EQ inside the app and save it as a run.
- Export works for a hand-built EQ without any test.
### Fixed
- A run measured with the in-app EQ ("Re-measure with in-app EQ") was never saved, so the second run seemed to lose its data. Every measurement is now saved, with a regression test for save and load of all runs.

## [0.8.0] - 2026-10-09
### Added
- System EQ (experimental): AutoTune applies its EQ to phone audio via DynamicsProcessing, with a manual 31-band editor.
- Versioning: version shown in Appearance dialog, versioned APK names, this changelog, git history and tags.
### Changed
- EQ lists always sorted low to high; toggle between standard bands (with band-set options) and exact frequencies.

## [0.7.0]
### Added
- System report card (score and grade per category, potential after EQ).
- Extra checks: L/R balance, sub/crossover/polarity helper, mic-position averaging, headroom/clipping warnings.
- Save a run as a target curve, import target curves, per-test notes.
- Live RTA and sound level meter.

## [0.6.0]
### Added
- Two source options (connected to phone / not connected) and a combined pink noise + sweep analysis mode with cross-check.
- Downloadable test files (sweep, pink noise, combined, left/right sweeps).

## [0.5.0]
### Fixed
- Fitter no longer stacks repeated boosts at one frequency; duplicate frequencies merged.
### Added
- Collapsible resonance list with explanations of the peak and ring tests.

## [0.4.0]
### Added
- Export as saved files (Equalizer APO/Poweramp parametric text, Wavelet GraphicEQ, CSV, report) instead of copied text.
- Theme selector (system/light/dark, wallpaper colours).

## [0.3.0]
### Added
- Named tests with stock (no-EQ) reference profile, unlimited runs, overlay and result selection, per-run applied-EQ record.
### Fixed
- Stale Home theatre comparison bug (per-test storage and locking); keyboard hiding the name field.

## [0.2.0]
### Added
- Step-page wizard, before/after runs, RTA bars, short descriptions with info bubbles, adaptive launcher icon.

## [0.1.0]
### Added
- Initial app: USB mic (UMIK-1) with calibration import, log-sine-sweep deconvolution, automatic parametric EQ fit, resonance detection.
