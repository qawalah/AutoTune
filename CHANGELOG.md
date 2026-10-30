# Changelog

Format: [Semantic Versioning](https://semver.org). Version lives in `gradle.properties`
(`APP_VERSION_NAME`, `APP_VERSION_CODE`); release by bumping both, adding an entry here, committing and tagging `vX.Y.Z`.
Everything below is untested on a real device and microphone unless stated.

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
