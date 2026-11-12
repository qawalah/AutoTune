# AutoTune: handover

Handover of AutoTune 1.18.0 (versionCode 38), now continued at 1.19.0 (versionCode 40), for development in https://github.com/qawalah/AutoTune with Claude Code. Written 2026-10-30. It replaces the per-version notes kept until now. Those notes are condensed in the "Code notes" appendix at the end.

## What the app does

AutoTune measures a speaker system with a microphone, either a car (the main goal) or a home theatre or soundbar. It compares the reading with a target curve, fits a parametric EQ, applies it and measures again until the sound is close to the target. The first reading of the untouched system ("stock") is kept as a permanent reference. Every later run is saved with the EQ that was in effect and its distance from the target. The owner tests with a Samsung Galaxy S23 Ultra and a USB measurement mic (UMIK-1) in a Ford F-150 over Bluetooth, and with an LG soundbar.

Main features:
- **Tune.** A guided workflow: Fresh start, Set up, Stock reading, Stock review, Apply the suggested EQ, Verify, Result. Each step has labelled Back and Exit buttons. Exit keeps your place and Continue returns to it. Every reading ends on one screen: a short summary, the graph, one recommended action pinned above the tabs, other choices, then Details.
- **Analysis.** A log sweep with start-beep markers and regularised deconvolution. It reads SNR per frequency, clipping, cut-off, clock drift, resonances and T60 ringing, HD2/HD3/THD distortion and the frequency-dependent window. It checks left and right, sub and crossover, and averages several mic spots. A stock review lists findings such as boomy bass, harsh presence, peaks, ringing, hardware limits and measuring warnings.
- **Closed-loop fitting.** Each new suggestion is a complete EQ fitted from the stock reading and corrected by what the last run actually measured, so EQs never stack. `recommendNext` chooses whether to apply the new suggestion, keep this EQ, keep the best run's EQ, or apply the EQ again because it never arrived.
- **Targets.** Car (+5 dB bass), Car Harman-style (+8 dB), Home theatre, Room Harman-style (+6 dB), Flat, and custom curve files. Bass and treble "taste" shelves can be added per test. Defaults are set in Settings.
- **AutoTune EQ.** Android's DynamicsProcessing on the phone's whole output, run from a foreground service, with 31 bands or an optional 61.
  - Preamp modes: Safe, Balanced, Off, or Manual from −15 to 0 dB.
  - Flavours played on top of the tune and never measured: More bass, Rock, Pop, Electronic, Acoustic, Speech, Low volume, Late night.
  - An EQ check measures with the EQ off and on, to prove the EQ reaches the speakers.
- **Speaker response.** An octave-by-octave comparison of what the EQ asked for with what the mic heard. Bass boosts the speakers ignore become a per-test boost floor, and later fits stop asking for them. A "Worth checking" list suggests the next reading: louder stock, 3 spots, the EQ check, or a repeat.
- **AI review (optional).** Claude or Google Gemini, with the user's own API key. The model returns a structured review (findings, one action each, next step) and optional EQ changes. The app clamps those changes to its own limits and predicts the result. Follow-up chat is available, and "Ask AI why" appears on a failed measurement.
- **Outside the app.** Profiles, with automatic switching when a Bluetooth device connects. Home-screen widgets (panel, EQ toggle, auto toggle, profile) run in the background without opening the app. Notification actions: "Switch EQ off" and "Stop auto-detect". Shortcuts and intents work with Samsung Routines. The EQ is restored after a reboot.
- **Export.** ParametricEQ.txt (Equalizer APO, Poweramp), GraphicEQ.txt (Wavelet), a report and a CSV, and test WAVs (sweep, pink noise, sweep plus pink, left only, right only) for head units. Files go to a chosen folder or to Downloads.
- **Live spectrum.** A 1/3-octave RTA with an SPL reading.
- **Looks.** Braun looks only (since 2.8): Classic, Graphite, Black, White and Gradient edge (the default since the owner's request of 2026-11-09), each with a day and a night version (White is always light), all with fader sliders. An accent, and in Advanced a palette (body, deck, ink, accent, hairline), can be set over any look. Gradient edge starts from its own palette (Warm white body, White deck, Slate ink, Braun red accent, Aluminium hairline); the other looks start from their own colours.
- **Send diagnostics.** A zip containing a JSONL log, the last 12 recordings, the tests and the settings.

## Platform and type

An **Android app**, written in native Kotlin with Jetpack Compose (Material 3). Since 2.13 the same APK also runs on Android TV / Google TV as the home-theatre companion (`tv/TvActivity`, leanback launcher), with one applicationId. Since 2.14 there is also **AutoTune for Windows**, a Compose for Desktop app released as an MSI and through DevStore on the PC (see "AutoTune for Windows (2.14.0)" below). No server. Gradle modules:
- `:dsp` is pure Kotlin/JVM signal processing with no Android dependency, and is unit-tested anywhere.
- `:link` is the phone / TV / PC link (pure Kotlin, depends on `:dsp`), shared by `:app` and `:desktop`.
- `:app` is the Android application: UI, audio I/O, system EQ, storage and AI clients.
- `:desktop` is the Windows app. It compiles, tests and renders its screenshots anywhere; only its installer needs Windows (`.github/workflows/windows.yml`).

## Identity

| | |
|---|---|
| applicationId / namespace | `dev.autotune` |
| versionName | `1.19.0` |
| versionCode | `40` |
| min / target / compile SDK | 26 / 35 / 35 |
| Version source | `gradle.properties`: `APP_VERSION_NAME`, `APP_VERSION_CODE` (the single source of truth) |
| APK name | `AutoTune-<versionName>-<buildType>.apk` |

## Build and run

Requirements:
- JDK 17 or later. JDK 21 was used here.
- Android SDK platform 35 and build-tools.
- Gradle 8.14.3 through the wrapper.
- AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01.
- No other repositories or tools.

1. Point Gradle at the SDK. Either set `ANDROID_HOME` or `ANDROID_SDK_ROOT`, or create `local.properties` with `sdk.dir=/path/to/sdk`. `local.properties` is not committed. `settings.gradle.kts` includes `:app` only when one of these exists, so `./gradlew :dsp:test` also works on a machine without the SDK. On GitHub's Ubuntu runners `ANDROID_HOME` is already set.
2. Build and test:
   ```
   ./gradlew :dsp:test :app:testDebugUnitTest :app:assembleDebug
   ```
   The APK lands in `app/build/outputs/apk/debug/AutoTune-<versionName>-debug.apk`.
   - DSP tests: `dsp/src/test`. They simulate rooms with planted resonances and a coloured mic and check the loop converges.
   - App unit tests: `app/src/test`, covering FlowTest, PlayTest, TuningLogicTest, TestStoreTest and the AI tests.
3. Install the APK on a phone. On first launch, grant the microphone permission. Notifications (Android 13 and later) and Bluetooth (for automatic switching) are asked for when needed.

Manual steps and special setup:
- **No release signing is configured.** Every build so far is a debug build, signed with the debug keystore of the machine that built it. An APK signed with a different key (for example by GitHub Actions) cannot be installed over the current one, so the phone has to uninstall first, which **deletes the saved tests**. Before switching, use Send diagnostics to keep a copy of the tests. The app has no import for that zip yet. Then use one release keystore from then on (see Secrets).
- **Releasing.** Bump both values in `gradle.properties`, add a `CHANGELOG.md` entry and tag `vX.Y.Z`. That has been the routine for every version. Tags v0.8.0 to v1.18.0 exist in the previous local git history. That history is not in this zip, which holds files only. The commits carry `Co-Authored-By` and `Claude-Session` trailers.
- **Screenshots.** `tools/screenshots/` holds the Paparazzi harness and is not wired into the build. Its README explains how to use it.
- **Phone settings.** Samsung's own sound effects (SoundAlive, Adapt Sound, Dolby Atmos) cannot be controlled by apps and must be off while measuring. Measure with the engine off in a quiet place, with the mic at the listening position.

## Where data lives

Everything stays on the phone, in app-private storage:
- `filesDir/tests/<id>.txt` holds each test. It is a versioned text format (TestStore, currently v6) with the runs, response curves, the EQ applied and suggested, warnings, calibration name, notes, taste (`taste=b,t`) and `boostfloor=`. Older versions still load.
- `filesDir/targets/` holds custom target curves.
- `filesDir/profiles.txt` holds saved EQ profiles, and `filesDir/rules.txt` maps Bluetooth devices to profiles.
- `filesDir/calibration.txt` is the mic calibration file. Its display name is in prefs.
- `filesDir/before_run.txt` records the EQ state before a run.
- `filesDir/ai/<testId>.json` holds the last AI review per test. It is deleted with the test.
- `filesDir/diag/` holds `log.jsonl`, the event log, and `recordings/` with the last 12 WAVs.
- SharedPreferences `autotune_prefs` holds settings and state, including:
  - `eqWanted`, `eqChain`, `eqPick`, `activeProfile`, `automationOn`, `offOnDisconnect`
  - `flavour`, `preampMode`, `preampManual`, `eqFine`
  - `defTarget`, `defBass`, `defTreble`, `defSpots`, `defGate`
  - `tutorialSeen` (the first-start tutorial was finished or skipped)
  - `look`, `theme`, `accent`, `palBody`, `palDeck`, `palInk`, `palAccent`, `palRule` ("#RRGGBB", absent = the look's own), `smoothing`, `fdwCycles`, `exportTree`, `aiProvider`, `aiModel_<PROVIDER>`, `calibrationName`, `btSeen_<address>`, and similar.
- SharedPreferences `ai_secret` holds the AI API keys, encrypted with AES-GCM under the Android Keystore key alias `autotune_ai_key`.
- `cacheDir` is used for export and share files and the diagnostics zip. A chosen export folder is a persisted Storage Access Framework (SAF) tree permission; otherwise files go to Downloads.
- The manifest has `allowBackup="true"`.

External services: only when the user asks for an AI review or chat.
- `https://api.anthropic.com/v1/messages` (Claude)
- `https://generativelanguage.googleapis.com/v1beta/models/<model>:generateContent` (Gemini)

What is sent: dB-from-target values at third-octave points, each run's EQ, warnings, checks, notes, the phone model and the mic name. Recordings are never sent.

The app also opens a YouTube search for pink noise in the browser.

There is no server and no analytics. Since 2.13, AutoTune on a TV listens on TCP port 47810 (or a free port) on the local network while its screen is open, announced with DNS-SD as `_autotune._tcp`; it only obeys a phone that sent its four-digit code.

Entry points for other apps:
- Intents `dev.autotune.action.APPLY_PROFILE` (extra `profile`), `EQ_OFF`, `TOGGLE_EQ`, `TOGGLE_AUTO`, `NEXT_PROFILE` and `PREV_PROFILE`, handled by `ApplyProfileActivity`.
- The deep link `autotune://profile/<name>`.

## Secrets

- **AI API keys**: `CLAUDE` (Anthropic, `sk-ant-…`) and `GOOGLE` (Gemini, `AIza…`). Each user types their own in Settings › AI assistant. They are stored encrypted on the phone (see above) and are never in the repo or the build.
- **Build secrets**: none today.
- **Release signing**: will need a keystore. Suggested GitHub Actions secret names: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. A `signingConfigs.release` block that reads them still has to be added to `app/build.gradle.kts`. Keystores are git-ignored.
- **Play upload**: if you add it later, it needs a service-account JSON (for example `PLAY_SERVICE_ACCOUNT_JSON`).

## Known bugs, unfinished work and next ideas

Not verified:
- Nothing from 1.13 to 1.18 has been tested on the owner's phone by the developer, only through unit tests and rendered screenshots. The 61-band option, the speaker-response floor, preamp modes and flavours need a real-car check first.
- An AI review with a real key on a phone has not been run. The Gemini model ids (`gemini-3.8-flash`, `gemini-4-argon`, `gemini-3.5-flash-lite`) were taken from ai.google.dev on 2026-10-15 and may change.

Known behaviour, not bugs:
- Wireless Android Auto, and some car or app audio paths, can bypass the phone's EQ. The EQ check detects this, but the app cannot fix it.
- DynamicsProcessing on session 0 is phone-dependent.

Unfinished, from the earlier backlog:
- A before/after Verify screen.
- Saving distortion results with the runs.
- REW-style waterfall, group delay or phase, and the impulse/step response.
- Real-device checks of Samsung Routines, the widgets and phone-mic measurements.
- `BACKLOG.md`: calibrate the phone's own mic against the calibrated USB mic.

Next ideas:
- **From the other build, AutoTune 2.0.0-m5** (another model's rebuild of 1.9.1; the source code is not available; see "Decisions"), worth porting into this codebase:
  - a native Oboe audio engine that detects audio dropouts during a sweep and notices when the device disconnects mid-run;
  - a 3-sweep system EQ path check (whole phone vs per-app sessions);
  - a level-matched A/B player for a song;
  - turning the EQ off when a Bluetooth device disconnects;
  - restoring the EQ after an app update or a service restart.
- **Built-in AI with no user key**: Firebase AI Logic (the app's own Gemini usage) with on-device Gemini Nano where the phone supports it, keeping "bring your own Claude key" for the best reviews. Feed it the app's analysis results, not raw curves.
- **Import** of a diagnostics or backup zip, so tests survive a reinstall or a signing change.
- **Release automation**: a GitHub Actions workflow that runs the tests, builds a signed release on a `v*` tag and attaches the APK to a GitHub Release.

Before publishing to Google Play:
- "Auto-Tune" is a registered trademark of Antares Audio Technologies, so the app name may be rejected. Check the risk, or rename.
- A privacy policy URL is required, because the app records audio.
- A store listing draft exists: copy, 8 portrait screenshots, the icon and the feature graphic. It is in the Audio Apps project as `claude/autotune-store-listing.md` and in a design canvas. The screenshots can be regenerated with `tools/screenshots/StoreShots.kt`.

## Decisions to keep (and why)

- **Choices are drop-downs, never pills or segmented buttons.** The owner asked for this. Use icons, clean Material, minimal scrolling and taps. The one exception, also the owner's (2026-11-12): Set up's equipment as four big picture keys (`ui/EquipmentTiles.kt`).
- **Back returns to where you were (1.19).** A test's results, Live and Export open as pages over the current tab (`Page` in AutoTuneScreen); they never switch tabs. One tap on a test opens its results. The owner asked for this after Back from those screens kept landing on Tests.
- **Every reading ends the same way:** a short summary, then one recommended action pinned above the tabs (`PinAction` / `BigActionBar`), then the other choices, then Details. The graph is always shown after a reading. This came out of several navigation rounds (1.14, 1.15, 1.17) after the flow confused even the owner.
- **The stock run is the permanent reference, and each suggestion is a complete EQ fitted from it** (`EqFitter.fitFromStock`, corrected by the measured-vs-predicted difference). Suggestions never stack on top of each other, so a bad run cannot compound.
- **Gentle boosts.** The fitter allows at most 3 dB per boost, the suggested EQ caps total boost at about +4 dB (`EqChain.capBoost`), and boosts stop below the measured boost floor. Speakers often cannot follow deep bass boosts, and boosts eat headroom.
- **Measurements always use the tuned EQ with the safe preamp; the flavour and preamp mode only affect playback.** `ProfileController.remember()` stores the base EQ only. Otherwise a flavour would be "corrected away" by the next fit, or applied twice.
- **The 1.18 volume drop when boosting bass was AutoTune's own safe preamp, not Android.** That is why preamp modes exist. Android's DynamicsProcessing allows about −30 to +15 dB per band, which is not a practical limit.
- **New built-in targets are appended to `Target.BUILT_IN`**, because saved tests refer to targets by index.
- **TestStore stays a versioned text format with backward-compatible loading.** Never break old test files. Every measurement is saved, and nothing the user measured is silently lost.
- **Mic voice processing is switched off for every recording.** AGC, noise suppression and echo cancellation are disabled, and the `UNPROCESSED` source is preferred when the phone offers it, because those effects tilt a sweep.
- **Clock drift (`driftPpm`) is logged only, with no warning**, following the 1.9.1 audit. Other audit items were deliberately left as they are; see `AUDIT-RECHECK.md` and the appendix.
- **The AI uses plain `HttpURLConnection`, with no SDK.** Reviews force a tool or function call, so the output is structured. The AI's EQ changes are clamped to the app's limits before use, keys stay in the Keystore, and recordings are never sent. The AI is an advisor on top of the app's own deterministic analysis, not a replacement for it.
- **Widget and notification actions go through a non-exported `EqActionReceiver`**, so toggling never opens the app. `ApplyProfileActivity` has an empty `taskAffinity` and remains only for shortcuts and other apps.
- **Switching AutoTune EQ on with nothing picked starts a flat EQ** (`eqWanted` + `eqFlat`) instead of refusing.
- **`:app` is included only when an Android SDK is configured**, so the DSP core builds and tests anywhere.
- **The version lives only in `gradle.properties`.** Each release gets a CHANGELOG entry and a `vX.Y.Z` tag.
- **Every GitHub Release holds the APK (2.14.0).** Obtainium looks at the newest release only (unless "fallback to older releases" is on), so the separate `windows-v2.14.0` pre-release, newest and without an APK, stopped phone updates with "Could not find a suitable release". Other platforms' files go into the same version's Android release as extra files (`AutoTune-X.Y.Z-windows.msi`). Since 2.16.1 there is also DevStore's `windows-vX.Y.Z` release, and Obtainium on the phone and TV filters by `^Android` (the release title, not the tag) (see below).
- **This 1.18 line is the one to continue, not 2.0.0-m5.** 2.0's analysis core is the same as this one; its gains are engineering ones (native audio, Room, Hilt). It lacks everything from 1.10 to 1.18, and its source code is not available. Port its strengths here instead. The comparison is in the Audio Apps project as `claude/autotune-2.0-port-plan.md`.

## Other docs in this repo

- `README.md`: user-facing overview.
- `CHANGELOG.md`: every version since 0.x.
- `DESIGN.md`: visual language.
- `PRODUCT.md`: purpose, users and principles.
- `BACKLOG.md`: future work.
- `AUDIT-RECHECK.md`: the 1.9.1 audit follow-up.
- `CLAUDE.md`: short working rules for Claude Code.
- `FLOW.md`: the process and navigation maps (Mermaid, drawn on GitHub). Also published as the "AutoTune Flow Maps" artifact.

---

## Appendix: code notes

Kept from the per-version handovers, so the reasons behind the code are not lost. File names are under `app/src/main/kotlin/dev/autotune/` unless they start with `dsp`.

### Measure
Phone mic allowed when no USB mic. Phone sweep: 3 start beeps + 1 s gap, 10 s log sweep, lifted by the EQ's loss (up to 12 dB) so it stays audible; because the safe preamp always puts the EQ peak at -0.3 dB, the lift is effectively 2.9 - (-0.3) = about 3.2 dB. External device: test files with the same beeps. Analysis: regularised deconvolution, gate, per-frequency SNR, SweepCheck (cut-off, clipping, noise).

### EQ
Graphic sliders (5/10/15 bands, +/-12 dB) on top; they are a delta layer over the tuned EQ (UiState.sliderLayer) and show the realised response, "Convert for a head unit" does the lossy conversion; profiles grouped per test; Advanced holds exact bands, parametric editor, listen/measure, automatic switching. EQ is always the whole EQ (Delta/Absolute removed). Suggested EQ caps total boost at about +4 dB (EqChain.capBoost) so the preamp stays near -4 dB.

### Applying the EQ
System effect (DynamicsProcessing, foreground service), in-app preview, or exported files. Bluetooth-triggered profile switching, Samsung Routines via shortcuts / intent dev.autotune.action.APPLY_PROFILE (extra "profile") or autotune://profile/NAME. Also TOGGLE_EQ, TOGGLE_AUTO, NEXT_PROFILE, PREV_PROFILE.

### Widgets
Panel (5x1, 5x2 adds profile switcher), EQ toggle, Auto toggle, Profile. Classic RemoteViews in audio/Widgets.kt; taps go through ApplyProfileActivity; widgets refresh from ProfileController and the view model.

### REW-style analysis
Frequency-dependent window (Measure > Advanced: Fixed / FDW 15, 8, 5 cycles), harmonic distortion from the sweep (HD2, HD3, THD on Results, latest sweep only, not saved), Results smoothing (None, 1/24 to 1/3 oct). Not done: waterfall, group delay / phase, impulse / step response.

### The tuning loop (1.9.1)
- `TuningLogic.kt` (pure, unit tested): `suggestedFor(run)` is the one EQ that is shown, graphed, applied, exported and recorded as applied; `appliedFor(...)` picks what a run records per route (equipment = shown chain incl. sliders, in-app = chain played, system EQ = `SystemEq.applied`, i.e. `EqChain.sampledTo` of the 31 bands, repeat = previous applied); `nextSuggestion` keeps the best run; `trebleCollapsed` is the structural check that switches the system EQ off.
- `TuneViewModel.measureRun` fits with `EqFitter.fitFromStock(stock, applied, measured, ...)`: a complete EQ for the stock system, so nothing stacks. `SavedRun.complete` marks runs whose `filters` are that complete EQ; older runs (flag false) stored only the further tweaks and are combined with `applied` by `suggestedFor`.
- TestStore is v5 (per run: warnings, calibration name, complete). v4 files load with calibration "unknown".
- Calibration is stored in filesDir/calibration.txt (name in prefs `calibrationName`). The system EQ is remembered in prefs `eqWanted` + `eqChain` and restored by `SystemEqService` (START_STICKY).
- 1.9.2: `measureRun`'s applied chain comes from the focus (latest ticked) run, i.e. exactly the EQ tab's `exportChain`. `restartSpots()` clears pending mic spots when the apply route changes. BootReceiver restores the EQ when `eqWanted`.
- Audit items deliberately left: A8 (the stop time of a file capture is derived from the same `TestSignals` constants as the file, so they cannot drift unless someone changes one by hand), `Phase.MEASURE/REVIEW` kept as is, analyzer buffers are not pooled across calls (four arrays of at most 2^20 doubles per analysis).

### Tune workflow and diagnostics (1.10.0)
- `UiState.tuneStep` (`TuneStep`: HOME, FRESH, SETUP, STOCK, APPLY, VERIFY, RESULT, DONE) drives ui/TuneTab.kt; the view model moves it (`tuneStart/Fresh/Continue/Setup/Apply/Refine/Repeat/Discard/Keep`, `tuneGo`). `Phase`, `measureWithAppEq`, the New test overlay and the jump to Results after a run are gone.
- Route model: `applyWay` = how the EQ reaches the speakers (AUTOTUNE_EQ, EQUIPMENT, IN_APP) and stays until changed; `repeatNext` = the next run changes nothing (in-app repeat replays the previous applied chain).
- EQ source: `editFilters == null` = suggestion; a profile is `activeProfileId` + equal `editFilters`; otherwise own bands. `resetToSuggestion()` after every saved run; `useSuggested()` / `selectProfile()` from the EQ tab. `eqSource`, `eqLevelDb` are computed in `rebuildActive()`.
- `Diag.kt`: `log.jsonl` (types: measurement, stock_saved, run_saved, system_eq_on/off/update, profile_used/selected, tune_step, tune_done, run_discarded, failure, system_eq_damaged), `recordings/` (last 12 WAV), `export()` zip with info.txt, the log, tests/ and calibration.txt.
- Kit: `DropSelect` replaces `Segmented` (removed); `PulsingButton`, `StepHeader`; `Ic` has domain icons; buttons take `icon =`.
- Screenshot harness: tools/screenshots/ (see its README).

### EQ in use, convergence, marker fallback (1.11.0)
- `SavedRun.appliedParam` (TestStore v6 `run.i.param`, `hasparam`) = the parametric EQ behind the system EQ's 31 realised bands; `SavedRun.eq` = what to show/keep/export. `applied` stays the realised bands (fitFromStock uses them). `converged` per run.
- `nextSuggestion(..., newEq)`: converged when the fit is within 1 dB RMS of an EQ that already measured worse (for v5 runs the suggestion before the run is used as "tried"), or when going back to the best EQ a second time.
- EQ in use: `UiState.eqPick` keys `suggest:<test>`, `run:<test>:<i>`, `profile:<id>`, `own`; `eqChoices` built by `rebuildChoices()`; `selectEq(key)`; `setEdit(list, pick)`. `GroupedPick` in Kit. Profiles are management-only on the EQ tab.
- phoneSweep: marker rejected when the sweep cannot fit after it; a marker result that is fatal or under 30 dB SNR is compared with a whole-recording search and the better one kept (`usedFullSearch` in the log).
- Export: `exportEq(key, format, share)`, `resolveEq(key)`.

### Measuring screen (1.12.0)
- `audio/LiveCapture` (level, last 512 samples, `playStartMs`) is fed from both recording loops. `UiState.measureView` (`MeasureView`, `MeasureStage`, `CheckItem`) is opened by `stockRun`/`measureRun`, filled by `measureOnce` (`startChecks`, `checksFor`) and `acquire` (spot done), finished by `showSaved` or the failure handler. `ui/MeasureOverlay.kt` draws it full screen over the app; the phone route's "what is playing" is derived from `playStartMs` and the known pre-roll, beeps and 10 s sweep.

### Fixes ported from the audited 1.9.1 (1.12.2)
- `Marker.find` is the audited structural detector: one-beep template, three peaks one period apart within 50 % of each other, near-silent correlation in the gaps (< 35 % of the weakest peak), any of the three may be the strongest. Windows of 6 s with overlap; < 40 MB on a 60 s recording. `phoneSweep`'s plausibility check and full-search fallback stay.
- `Measurement.driftPpm` is logged only (the `measurement` diag event); no warning, no check row.
- `SYSTEM_EQ_FIT` and `fitsSystemEq` live in TuningLogic.kt. `applyWithAutoTuneEq` calls `systemRealisableStock()`: with only the stock run, the suggestion in use and no own bands or sliders, it refits the stock run with `SYSTEM_EQ_FIT` and saves it, so shown = applied from run 1.
- `SystemEqService.onStartCommand` restores whenever no chain is running (restore checks `eqWanted`); `refreshNotification` keeps the service up while `eqWanted` is set.
- Compared and left as they are: `graphicSet` (single pass is exact here because the slider layer is additive at the band centre), noise reference, `fitFromStock` residual handling, `sampledTo` overlap compensation, repeat runs recorded as the previous EQ.

### AI review (1.13.0)
- `ai/` package. `AiClient` posts to the Messages API with HttpURLConnection (no new dependency), `anthropic-version: 2023-06-01`; a review forces the `report` tool (`AiPrompt.TOOL`), follow-ups are plain text. `AiModel` lists claude-sonnet-5-5 (default), claude-opus-5-5, claude-haiku-5-5.
- `AiPrompt.context` builds the JSON: setup, last measuring checks/failure, the stock run plus the last 6 runs as dB from the target at 30 third-octave points (1/6-oct smoothed, level-matched like `EqFitter.score`), each run's EQ, notes, warnings, resonances, AutoTune's suggestion for the focus run, the EQ in use. `AiPrompt.SYSTEM` explains the app's loop and limits to the model.
- `AiReview` parses the tool input (unknown severities read as "check") and stores it in `filesDir/ai/<testId>.json` (deleted with the test). `AiEqCheck.build` clamps the AI's bands (`SYSTEM_EQ_FIT` on the phone route), combines them with the suggestion, caps boost, adds the safe preamp, and predicts the focus run (run − EQ it was measured with + new EQ).
- VM: `ai` flow (`AiUi`), `aiOpen/aiExplainFailure/aiReview/aiAsk/aiUseEq/aiSaveKey/aiTestKey`. EQ key `ai:<testId>` in `rebuildChoices`, `selectEq`, `resolveEq`. `KeyVault` keeps the key AES-GCM encrypted with an Android Keystore key.
- UI: `ui/AiScreen.kt` (full-screen, Back closes), `AiKeySetup` also in Settings. Icons `Ic.AI`, `Ic.SEND`.
- Verified: unit tests (parsing, limits, prediction, context, request JSON) and a real call to the API with an invalid key (error mapped). Not verified: a review with a real key, on a phone.

### Gemini, stock analysis, one action, outside controls (1.14.0)
- AI: `AiProvider` (CLAUDE, GOOGLE) and `AiModel.provider`; `AiClient` is abstract with `ClaudeClient` and `GeminiClient` (generateContent, `x-goog-api-key`, forced function call `toolConfig.functionCallingConfig` mode ANY on `report`, thought parts skipped, `maxOutputTokens` 12000 because thinking counts). `KeyVault` keeps one key per provider (Claude's under the old unsuffixed names). Prefs `aiProvider`, `aiModel_<PROVIDER>`. Gemini model ids from ai.google.dev/models on 2026-10-15: gemini-3.8-flash, gemini-4-argon, gemini-3.5-flash-lite.
- Flow: `TuneStep.STOCK_RESULT` between STOCK and APPLY. `analyzeStock` (TuningLogic) → `UiState.stock`; `recommendNext` → `UiState.recommendation` (`NextMove`); `previousStep` + `vm.tuneBack()` for Back and the header arrow. All three are unit tested in FlowTest.
- Outside controls: `EqActionReceiver` (not exported) handles widget taps and notification actions through `ProfileController.handle`; `ApplyProfileActivity` stays for shortcuts and other apps, now with an empty task affinity. `EqBus.changed` tells the open VM to `syncExternal()` (which now also reads `automationOn`). A wanted flat EQ is `eqWanted` + `eqFlat`.
- Maps: the process and navigation maps are in `FLOW.md` (since 2.5.0) and the "AutoTune Flow Maps" artifact; update them when steps or links change.

### UX audit changes (1.15.0)
- `ui/ResultViews.kt`: `ResultSummary(s, showNext)` is the shared summary layer (Stock review, Result, Test results); `DetailsHeading()` starts the diagnostic layer. Test results sections are `Fold`s (Fold now takes `info`); `ReportCard` and the resonances list are Folds too.
- Tune: `StepHeader(..., onBack, names)` draws labelled Back / Exit and `STEP_NAMES`; `stepIndex`, `resumeAt`, `resumePlace`, `RESUMABLE`, `moveLabel` in TuningLogic (tested in FlowTest). `vm.tuneExit()` stores `UiState.resumeStep` for the open test; any move inside the workflow clears it.
- Apply step: `isSuggestion = eqPick.startsWith("suggest:")` picks the title; `vm.useSuggested()` goes back to the suggestion. The test list row opens the test (no inline actions); the delete lives under Manage test.

### Targets, taste, graphs, raw mic (1.16.0)
- dsp `Target`: `HARMAN_CAR`, `HARMAN_ROOM` appended to `BUILT_IN` (old indices unchanged); `withTaste(bassDb, trebleDb)` resamples the curve at 1/6 octave with `bassWeight` (1/(1+(f/110)^2)) and `trebleWeight` (1/(1+(4000/f)^2)). TasteTest covers shape, names and that more bass in the target means less bass cut.
- `SavedTest.bassDb/trebleDb` (file line `taste=b,t`). VM `targetFor(t)` = base target + taste; every fit and score goes through it. `setTargetSound(idx, bass, treble)` rescores all runs, refits the latest suggestion (closed loop for runs with an EQ) and clears `converged`. UI: `TargetSoundFold` in ResultViews.kt; `targetDetail()` gives the one-line description per curve.
- `ResponseChart`: legend toggles (keys `run:<label>`, `target`, `predicted`, `eq`), full-screen Dialog, `chartRange()` (±15 dB minimum, 5 dB steps, ±45 dB cap).
- `MeasurementRunner.switchOffVoiceEffects`: AutomaticGainControl, NoiseSuppressor, AcousticEchoCanceler created on the recording session and disabled; kept until the next recording. `lastMicEffects` feeds the "Microphone (raw sound)" check and the `measurement` log event.

### Streamlining, EQ check, looks, defaults (1.17.0)
- Pinned action: `PinAction(text, onClick, icon, enabled, hint)` (Kit.kt) registers the screen's main action in `LocalPin` (a `PinSlot` owned by AutoTuneScreen); `BigActionBar` draws it above the tab bar. Only the screen on view pins; leaving clears it (owner check).
- EQ check: `vm.eqCheck()` measures with `SystemEq` off, then on with the active chain, and calls `judgeEqCheck` (TuningLogic, tested in FlowTest): least-squares share of the expected change (the 31-band realisation) between 60 Hz and 10 kHz, with a level offset allowed. ≥0.7 arrives, ≥0.3 partly, else not; flat EQs (σ < 1 dB) cannot be checked. `UiState.eqCheck` → `EqCheckCard`.
- Looks: `Look` and `SliderStyle` in Theme.kt; `lookColors(look, dark)`; `AutoTuneTheme(mode, dynamic, look)`. Picking a look other than Studio turns wallpaper colours off. `VSlider` draws ROUND, FADER or LED from `At.c.slider`.
- Defaults: prefs `defTarget` (name), `defBass`, `defTreble`, `defSpots`, `defGate`; a new test's stock fit and saved taste use them. Export folder: `exportTree` (persisted SAF tree permission), written with `DocumentsContract.createDocument`, Downloads as fallback.
- EQ picker: `EqChoice.primary`; `GroupedPick` shows primaries and the selection, with "Show N more" per group.
- AI: `aiOpen` reuses a stored review only when `runCount` and `focusRun` match.

### Preamp, flavour, band resolution, speaker response (1.18.0)
- TuningLogic: `PreampMode`, `Flavour` (filter presets), `playChain(filters, flavour, mode, manualDb)`. `ProfileController.shaped(ctx, filters)` reads prefs `flavour`, `preampMode`, `preampManual`, `eqFine` and is used by every playback path (VM `playChain()`/`replay()`, widgets, Bluetooth watcher, restore). `remember()` still stores the base EQ (no flavour) so it is never added twice.
- Measuring: `measureRun` applies `withSafePreamp(shown)` before reading `SystemEq.applied`; `run {}`'s finally re-applies `playChain()` when AutoTune EQ is on; `eqCheck` restores `playChain()`.
- `SystemEqConfig.fine` (prefs `eqFine`): `SystemEq` uses 61 centres at 1/6 octave (edge 2^(1/12), band Q 8.65) or the 31 RTA centres; `setFine` rebuilds the effect. `SYSTEM_EQ_FIT` is a getter: Q ≤ 2 / 1.4 (31 bands) or ≤ 4 / 2.5 (61 bands).
- `eqFollow(stock, run, applied, grid)`: 1/3-octave smoothed change vs the applied EQ per octave (31.5 Hz-8 kHz), level-matched over 250 Hz-2.5 kHz; `share` from `judgeEqCheck`; `deafBelowHz` = top of the bottom octaves whose boost (≥ 1.5 dB) came out below 30%. Saved as `SavedTest.boostFloorHz` (file line `boostfloor=`), used as `FitConfig.boostMinHz` (fitter skips boosts below it). `suggestChecks()` → `UiState.checkSuggestions`, acted on by `vm.doCheck(action)`. UI: `FlavourAndPreamp`, `FollowAndChecks` in ResultViews.kt. Tests: PlayTest.

### Dyno sheet look, pages, run sheet (1.19.0)
- Theme.kt: `Look.DYNO` is first and the default (`Look.of(null)`); `AtColors` gained `shell`, `onShell`, `onShellMuted` (the navigation band and run-sheet header strip; other looks fall back to their panel colours). `Look.allowsWallpaper` (Dyno and Studio) replaces the old Studio-only check in `setLook`. Fonts: `AtSans` (Barlow) and `AtFigures` (Barlow Condensed), every style with `fontFeatureSettings = "tnum"`; the Plex files are gone (licence in `licenses/Barlow-OFL.txt`).
- Kit.kt: `Panel` is a ruled sheet (1dp border); `RunSheet(title, sub, trailing)` and `ReadingCells(List<Reading>)` are the run-sheet header and its ruled cells, used by `ResultSummary`; `ScreenTitle` draws `SheetRule()` under the title and, when `LocalBack` is set, a back arrow instead of the menu; `StepHeader` is one row (Back, title with "Step n of N · Next: …", Exit) over a slim segmented bar.
- AutoTuneScreen: `Tab.isPage` (LIVE, EXPORT); `go(LIVE/EXPORT)` opens a `Page` over the current tab, any other tab clears the pages. `openResults` opens `Page.RESULTS` (`ResultsPage` in TestsTab.kt, the old TestDetail). Back pops the newest page first. Each tab and page has its own `ScrollState`; a page opens at its top. The nav bar is hidden while a page is open.
- ResponseChart: chart-paper grid (minor lines every 5 dB and at 2..9 of each decade, majors at decades and every 10 dB), the stock curve (`colorIdx == 0`, with other curves present) dashed, the key draws line swatches.

### Fitter fine-tuning from REW (1.20.0)
- Compared with REW 5.31.3 (its bundled help, the code is obfuscated). REW places filters and then optimises gains, Qs and frequencies together; AutoTune only placed them one at a time.
- `EqFitter.fit` now ends with `refine()`: a pattern search over every filter's gain, Q and frequency that keeps a step only when the error in the fit band drops. Rules: a cut stays a cut and a boost a boost, gains within `maxCutDb`/`maxBoostDb`, frequency within 10% of where the search placed it (and inside `fMinHz..fMaxHz`, boosts above `boostMinHz`), Q within the caps below, the summed response may not go further past the boost/cut limit than the search left it, lobes the search gave up on (boost floor, limit reached) carry no weight, and the whole band's error never ends up above the search's. Skipped when `stepScale != 1` (the half-step fit stays a half step) or `FitConfig.refine = false`.
- Q caps are `FitConfig.cutQCap(f)` (`maxCutQ` below 1 kHz, then sliding from `maxCutQHigh` to `maxCutQTop` = 3 at 10 kHz) and `boostQCap(f, gain)` (`maxBoostQ`, and no narrower than a 0.5 s ring: Q <= 0.5*f/(2.2*10^(gain/40))). The ring limit does not bind with the app's caps; it is a safety net. `SYSTEM_EQ_FIT`'s caps are unchanged.
- `FreqGrid.smoothVariable` + `FreqGrid.variableFraction` (REW's variable smoothing). `FitConfig.variableSmoothing` uses it below `smoothAboveHz`, but is **off by default**: it fitted worse on the simulated car comb and the moving-mic loop.
- Simulated results (RefineTest.benchmark, error after EQ, dB RMS): room 0.48 -> 0.30 (app route), 1.04 -> 0.77 (system EQ route); car comb 1.21 -> 1.16 and 1.56 -> 1.38; closed loop (ClosedLoopTest) settles at 0.27 instead of 0.58.

### UI overhaul: five tabs, Simple / Advanced, cards, motion (1.21.0)
- AutoTuneScreen: `Tab.SETTINGS` appended (ordinals stay valid); `Tab.bar` follows `NAV_ORDER` in TuningLogic (Tests, EQ, Tune, Live, Settings). `Tab.isPage` is EXPORT only; `Page.LIVE` stays for saved state. `AtNavBar(tab, busy)` draws the band, a sliding pill (480 ms, `EaseArrive`) and `TuneDisc`, the raised centre button (the only shadow in the app, pulsing while measuring off the Tune tab). The content is a `Transit` keyed on `Dest(tab, page, depth)`: deeper = `riseIn()`, shallower = `dropOut()`, same depth = `slideSideways(navSlide(...))`. The AI screen and the measuring screen use `sheetIn()/sheetOut()`. The pinned bar animates in and out. The old Setup overlay is gone: `SettingsTab` in SetupScreens.kt.
- Kit.kt: motion tokens `EaseArrive` (0.16, 1, 0.3, 1), `EaseLeave` (0.4, 0, 1, 1), `motionMs(ms)` (0 when animations are off) and `Modifier.pressScale`. `Transit` gives only the arriving screen the real `PinSlot`, so a leaving screen's pin cannot flicker back. `TuneTab` wraps its steps in `Transit` with `stepSlide`.
- Simple / Advanced: `UiState.advanced` (pref `advanced`, default false) through `LocalViewMode`; `isAdvanced()`, `say(simple, full)`, `Adv { }` (folds in and out), `Hint(text)` (an explanation, Advanced only) and `ModeSwitch()` (in `ScreenTitle` and `StepHeader`). Use `Hint` for explanations and `Muted` for facts and status.
- Cards: `Panel`, `Group` and `Fold` are cards (`CardShape`, 16dp) unless `LocalInCard` is set, when they draw flat with a hairline. `Actions { }` sets `LocalStacked`: buttons fill the width and `TextAction` becomes a full-width row with a chevron.
- `BigActionBar` shows "Suggested" over the button, the hint only in Advanced or when disabled, and slides the label when the recommendation changes.
- Theme.kt: `Look.NAVY` appended (DevStore / CrossPaths palette).

### EQ picker sheet (1.22.0)
- `EqChoice` gained `db`, `best`, `note`, `groupTitle`, `groupSub` (all defaulted). `rebuildChoices` fills them; the best run's title is now its plain label with `best = true` (no "Best: " prefix).
- `shortTestName(name, target)` and `shortTarget` in TuningLogic turn the automatic "<target> test, <date>[ (n)]" name into the date over the short target; typed names stay.
- `GroupedPick` (Kit.kt) is a field that opens a `ModalBottomSheet` (`EqSheet`): the selected EQ's group open first, other groups collapsed to one line with their best reading; `ChoiceRow` is one line (check, title, BEST, reading in `AtFigures`).


### Loud EQ (1.23.0)
- TuningLogic (pure, `LoudTest`): `LoudMode` (OFF, VOLUME, BUTTON), `loudLimitPct(f)`, `loudCutsFromDistortion(freqs, thd)` (octave cuts, 2/3 of the excess, ≤ 6 dB, Q 1.4), `LOUD_DEFAULT_CUTS`, `loudDelta(play, cuts)` (cuts + 60% of boosts < 150 Hz / > 5 kHz taken back, cuts only), `loudAmount(volume, start)` (smoothstep), `withLoud(chain, delta, amount)` (keeps the preamp), `filtersToText/FromText`, `VolumeGesture` (4 alternating volume steps within the window), `volumeFollows(levels)`.
- audio/LoudEq.kt: `LoudEq` reads prefs `loudMode`, `loudOn` (by hand), `loudStart` (percent), `loudKeys`, `loudCuts` (+ `loudSource`), `volSync_<output>`; `amount()` is 1 by hand, else by volume only in VOLUME mode on an output the volume check passed, in 10% steps. `route()` names the media output (Android Auto when the phone is in car mode). `live` (StateFlow) feeds the card. `VolumeWatcher` listens to `android.media.VOLUME_CHANGED_ACTION` and the Settings.System observer, deduped by the STREAM_MUSIC index. `LoudTile` is the Quick Settings tile.
- `ProfileController.shaped` adds `withLoud(...)`, so every playback path includes it; `reshape(ctx)` re-applies the remembered EQ (`eqChain`/`eqFlat`) when the amount changes. `SystemEq.measuring` (set by the VM's `run {}`) blocks reshape during a measurement. `SystemEqService` runs the `VolumeWatcher` while the EQ is on and the mode is not OFF (`updateVolumeWatch` in `refreshNotification`), switches on the gesture (not during the volume check: `LoudEq.checking`), and shows a third notification action.
- VM: `loudMode/loudStartPct/loudKeys/loudCuts/loudSource/volCheck` in UiState; `setLoudMode/Start/Keys`, `toggleLoud`, `loudFromReading` (uses `distortion` of the latest sweep), `loudUseDefault`, `volumeCheck` (20 s, stops early once two steps are seen), `stopVolumeCheck`. UI: `LoudEqFold` in EqTab.kt, under the AutoTune EQ card. Screenshot cases `l1_loud`, `l2_loud_light` in tools/screenshots.
- Not verified on a phone: whether the volume broadcast reaches the app on the S23 Ultra in the background, how Android Auto reports the volume, and real distortion-based cuts.

### Loud EQ by sound level (1.24.0)
- TuningLogic: `LoudMode.LEVEL` (appended), `LOUD_LEVEL_DEFAULT` (85 dB), `MIC_SILENCED_DBFS`, `micSpl(dbfs, offset)` (dBFS + 112 + `splOffset`, the Live tab's scale), `LevelFollower` (attack 2 s, release 6 s, in dB), `levelAmount(level, loud, 6 dB)`, `steadyAmount` (10% steps, moves of 20% or to 0/1 only). Tests in `LoudTest`.
- `LevelMeter` (audio/LoudEq.kt): its own 16 kHz float `AudioRecord` (UNPROCESSED, else VOICE_RECOGNITION, else MIC; not `MeasurementRunner.buildRecorder`, which would overwrite `lastMicSource`), 100 ms blocks on a daemon thread, reports to `LoudEq.levelHeard` once a second. Closed while `!isMusicActive`, `SystemEq.measuring` or `LevelMeter.paused` (set by the VM while the RTA runs).
- `LoudEq`: `levelAmt` is the amount in LEVEL mode; prefs `loudLevel`; `setLoudLevelNow`, `meterOff`, `appVisible` (MainActivity.onResume asks the service to retry the mic).
- `SystemEqService.notify(..., force)`: the foreground type is specialUse, plus microphone while `wantsMeter()`. `startForeground` is only called when the type changes or on `onStartCommand`'s first notice (otherwise `NotificationManager.notify`); a SecurityException (background start) falls back to specialUse with `micAllowed = false` and the note "Open AutoTune once to start listening". Manifest: `FOREGROUND_SERVICE_MICROPHONE`, service type `specialUse|microphone`.
- Not verified on a phone: background mic on the S23 Ultra (One UI), the level scale in the cabin, and behaviour during Android Auto voice commands.
### Words on the page (1.25.0)
- `Hint` is gone. Explanations are info bubbles: `InfoButton(title, text, small)`, `InfoLabel(label, info)`, and `info =` on `DropSelect`, `GroupedPick`, `Group`, `Fold` and Tune's `CardTitle`. `Alerts(items, title)` folds warnings into one tappable line; `StatusBlock` uses it.
- `ResultSummary`: readings, `Headline` (from `resultHeadline` in TuningLogic, or the first sentence of the stock verdict via `firstSentence`), the stock findings needing work in one line, `Alerts(resultWarnings)`, and `NextLine` (shown on the results page). `FindingRow` and "Worth checking" are titles with bubbles.

### Braun look (1.27.0)
- Theme.kt: `Look.BRAUN` appended and made the default (`Look.of(null)`, `UiState.look`). `AtColors` gained `teak` (edge cheeks; null for other looks) and `alu` (the Tune wheel). Fonts are Inter (light to bold, `res/font/inter_*.ttf`, licence `licenses/Inter-OFL.txt`); Barlow is gone, `AtFigures` = `AtSans`. Shapes are 2/4/4/6/12dp.
- Theme.kt: `AtColors.deck` holds the colours inside a deck; Braun has a dark body (`BraunLight` is dark by design) and light decks by day, dark decks at night. `atScheme` builds the Material colours for either. Kit.kt: `CardShape` 8dp, `deckPlate()` draws the plate with a hairline edge, `DeckColors { }` switches to the deck's colours inside it. `RunSheet` is a display deck with a lettering line instead of a coloured strip; `ReadingCells` sets the first reading large and light; `TuningScale(needleDb, stockDb, bestDb, predicted)` draws the radio scale, its range from `scaleMax` in TuningLogic (tested). `BigActionBar` is the graphite main key with a red pilot light; `PrimaryButton` graphite, `SecondaryButton` key-grey; `PulsingButton` is the record; `PowerToggle` lights green; `ScreenTitle` is the wordmark row.
- AutoTuneScreen: `AtNavBar` is the push-key row with `TuneWheel`; teak cheeks are drawn over the tabs and pages when `At.c.teak` is set.

### Accent, measuring screen, Tune switch (1.28.0)
- `Accent` (TuningLogic) with light/dark colours and their text colours; `contrast()` and a test that every accent reads on its colour (4.5:1) and against both bodies (3:1). `UiState.accent` (pref `accent`), `vm.setAccent`, `AtColors.withAccent`, `AutoTuneTheme(…, accent)`.
- MeasureOverlay: wordmark header, display with `Pilot`, `SweepDial` (log 20 Hz–20 kHz needle), `SignalMeter` as a needle scale, pilot-light `CheckRow`.
- AutoTuneScreen: tab keys 62×42; `TuneSwitch` replaces the wheel; teak cheeks drawn last, over every screen.

### Five-step Tune, quieter results, Tune button (1.29.0)
- TuningLogic: `previousStep`, `resumeAt`, `stepIndex`, `STEP_NAMES` describe five steps. `TuneStep.FRESH` and `STOCK_RESULT` stay in the enum for saved places only: FRESH renders Set up, STOCK_RESULT renders the EQ step and `resumeAt` maps it to APPLY. `tuneStart()` is `tuneFresh()`; a finished stock reading goes to APPLY. FlowTest updated.
- TuneTab: `ApplyStep` opens with `ResultSummary` after the stock reading; a suspicious stock pins "Measure the stock again" with "Use this reading anyway". `FreshStep` and `StockResultStep` are gone.
- ResultViews: no `TuningScale` in summaries (the composable stays in Kit for later use); stock headline is computed ("The EQ should bring it to ≈x dB."); findings join the warnings in one `Alerts` line, which shows only the count.
- AutoTuneScreen: `TuneButton` (round, accent, pressed when chosen) replaces `TuneSwitch`; `AtNavBar(tab, busy, eqOn)` lights a pilot on the EQ key. `ScreenTitle` shows only the wordmark on tabs. AiScreen restyled.

### 2.0.0
- 1.29.0 plus five UI-only review passes made in another session (`UI-REDESIGN-NOTES.md`, `PASS5-REVIEW.md`): LiveTab spectrum first with a standby state (no synthetic bars), EqTab chart moved up, TestsTab overview deck and closer/further labels, results chart heading, MeasureStep pin moved after the context, Settings section headings, graphite New test FAB. No DSP, measurement, EQ, storage or ViewModel changes.

### 2.1.0
- TuningLogic: `PREAMP_MIN`/`PREAMP_MAX` (−15/+6), `canDeleteRun`, `selectionAfterDelete` (tested); `Accent.GRAPHITE`. VM: `deleteRun(index)`; `LiveSignal` defaults to NONE.
- Theme: Braun palettes greyer; `Look.BRAUN_GRAPHITE` and `Look.BRAUN_BLACK` appended.
- Kit: `ScreenTitle` is the One UI header (tall, `HeaderArt(key)` per screen, wordmark or Back + mode key + menu on top); `TitleSlot`/`LocalTitleSlot` and `CollapsedHeader` (drawn per screen in AutoTuneScreen's `Transit`) show a slim bar once the header has scrolled away. `PulsingButton` is the speaker grille with the Tune key.
- AutoTuneScreen: tab labels title case on `onShell`; the EQ key latches (offset + inner shade) when AutoTune EQ is on. TestsTab: per-row ⋮ menu (open, continue, rename, delete) and `RunsDeck` under the graph with per-run delete.
- Resources: new `ic_launcher_foreground`, `ic_launcher_monochrome`, `ic_stat_autotune`; launcher background #E3E3E0. SystemEq notification: the new small icon, Braun red, short wording.

### 2.1.1: no stop before a measurement
- VM: `tuneSetup` starts `stockRun()` (when the mic is allowed); `tuneMeasureStock()` and `tuneMeasure()` (go to STOCK/VERIFY, then measure) are used by Set up, the EQ tab, the Apply step choices, "Measure again", the stock-again actions and the REMEASURE_STOCK check. `tuneRefine()` applies the known way and measures when `refineGoesStraightToMeasure` (TuningLogic, tested) says so, else shows APPLY.
- The `MeasureStep` page (STOCK / VERIFY) is now only the landing after a failed, stopped or closed measurement, and the way in when the mic permission is missing.

### 2.2.0: Braun header, latching key, Dynamic EQ, one icon family
- TuningLogic (pure, `HeaderTest`): `FRESH_OPEN_AFTER_MS` (15 min), `freshOpen(lastBackgroundedAtMs, nowMs, thresholdMs)`, `tallOnStart`, `tallAfterLeaving`; `ScrollCue` and `scrollCue(scrollPx, maxScrollPx, viewportPx)` for the slider row.
- AutoTuneScreen: `tallKeys` (saveable, tab names still to open tall), `backgroundedAt` (set on the activity's ON_STOP with `SystemClock.elapsedRealtime`, checked on ON_START), `freshEpoch`. Leaving a screen removes it from `tallKeys`; the tall/compact choice is remembered per `Dest` as it arrives (so a leaving screen keeps its header), and `shrinkPx` moves a screen's scroll up by the header's lost height when it comes back compact. Title slots are kept per screen key (`titleSlots`). Pages are always compact.
- Kit.kt: `LocalHeaderTall`; `ScreenTitle` draws the compact slim bar or the tall header; `Wordmark()` (AUTOTUNE); `HeaderArt` draws the Braun faces (keys: tests, eq, tune, live, settings, else tape deck). `LatchKey` (the latching push key), `PowerToggle` delegates to it, `SwitchRow(text, checked, onChange)` replaces every Material `Switch`. Icons: `drawIc`/`drawIcAt` (DrawScope, also used by the slider cue), new `Ic` entries BASS, TREBLE, GAIN, VOLUME, NIGHT, SPEECH, DRUM, VOCAL, GUITAR, SYNTH (appended), `flavourIcon`, `loudModeIcon` (`IconTest`).
- EqTab: order is the AutoTune EQ panel, Adjust bands, Frequency response, Dynamic EQ (Advanced), EQ check, the rest. `GraphicSliders` draws the edge fade, chevron and position bar in the draw phase from the scroll state. The Loud EQ is "Dynamic EQ" on screen only: `LoudEq`, `LoudMode`, `LoudTile`, prefs `loud*` and the intents `TOGGLE_LOUD`, `LOUD_ON`, `LOUD_OFF` keep their names. The by-hand switch is a `SwitchRow`.
- Not verified on a phone: the lifecycle timing of the fresh open (only reasoned from the code), and the look of the keys and header art on the S23 Ultra.

### 2.3.0: the header folds into the slim bar, no menu, knob and gear
- TuningLogic (pure, `HeaderTest`): `headerProgress`, `headerPhase` (switches with animations off), `headerFull`, `headerBottomPx`, `ramp`, `HeaderRect`, `slimArtRect`, `artAt`, `compactOpenScroll`, `fitTitlePx`.
- Kit.kt: `ScreenTitle(title, context, contextIcon)` no longer draws anything in the shell: it publishes a `HeaderSpec` to the screen's `TitleSlot` and keeps the tall header's room (`headerTall()` = `HEADER_ROW` 56dp + `headerArea()` + 1dp) at the top of the content. The shell draws `ScreenHeader(slot, scroll)` over that room; `HeaderFace` reads the scroll only in `graphicsLayer`/`drawBehind`/`drawWithContent` lambdas, so the fold never recomposes or relayouts. Outside the shell (no slot, e.g. the screenshot sheets) `ScreenTitle` draws the tall header in place. `CentreRow` keeps the mode key in the exact middle and holds the start slot to the room left of it; `fitWidth()` shrinks the wordmark rather than clip it at a large font scale; the large name is fitted so its longest word never breaks (`fitTitlePx`), header type is held to 1.3x font scale (`cappedSp`). `Modifier.scrollWithHeaderRoom(scroll, roomPx)` makes the content at least the viewport plus the fold range high, so even a short screen can open folded and still reveal the header at its top. Gone: `CollapsedHeader`, `LocalHeaderTall`, `SheetRule`, `TitleSlot.heightPx/collapseAtPx`, `LocalMenu`.
- AutoTuneScreen: `shrinkPx`/`compactPx` are gone (the header's room never changes height, so content keeps its place by itself). On arrival a screen that is not in `tallKeys` gets `compactOpenScroll(scroll, headerRangePx(), hasHeader)`; `hasHeader` is false for a Tune step (no `ScreenTitle`). `MenuButton`/`MenuHeader` removed. `SettingsTab(…, go)` takes `go`.
- Export: an "EQ files" group in Settings (Simple and Advanced) with "Export EQ files" (`go(Tab.EXPORT)`, a page over Settings) and, in Advanced, the folder controls (the old "Saved files" group). On the EQ tab, Advanced shows "Export EQ files" as a row before "More" (it moved out of More › Try it). Send diagnostics stays in Settings › Diagnostics, Advanced only.
- Icons: `Ic.TUNE` redrawn as the tuning knob, `Ic.GEAR` as a gear (enum order unchanged); their geometry is in pure helpers `knobTicks`, `knobEdge`, `gearTeeth` and the `KNOB_*`/`GEAR_BODY_R` constants (`IconTest`). `ic_launcher_foreground`, `ic_launcher_monochrome` and `ic_stat_autotune` are the knob too.
- Screenshots: `phoneAt(name, …, scroll)` in `tools/screenshots/ShotsTest.kt` draws a screen the way the shell does at a fixed scroll offset (`h1`–`h23`: the fold at 0/25/50/75/100%, pages, 360dp, font scale 1.6), `k1`–`k3` the knob, the gear, the key row and the launcher/status icons.
- Not verified on a phone: the feel of the fold under a real finger (fling, overscroll), the arrival scroll on a tab jump during the slide animation, and the fresh-open lifecycle.

### 2.3.2: merge before fine-tuning, dips left alone
- Found from a user's F-150 diagnostics (Harman car, phone system EQ route): `suggestedFor` passes every fit through `Export.combine`, which merges bands within 1/6 octave and clamps the sum to the per-band limit. The search had stacked three +3 dB boosts at 210-215 Hz to refill the hole two Q 2 cuts dug around 200 Hz; the merge kept +3 of +9, so 200 Hz played 10.6 dB under the target.
- `EqFitter.fit` now calls `Export.combine` itself before `refine()`, and `refine()` rejects any step that would make two bands `Export.mergeable`, so the fit is already the EQ that plays (merging it again changes nothing).
- `FitConfig.protectDips` (default on): where the 1/6-octave error is negative, the 1/3-octave boost error is capped at 0, so a narrow dip reads as "leave alone" instead of "too loud".
- Test: `RefineTest.aDipBetweenCutsIsNotDugOutAfterMerging` (fails on 2.3.1: 120-250 Hz -3.3 dB; passes: -0.7 dB).
- The same diagnostics showed the EQ check measuring no effect three times (output over a wireless link, 2.6 s latency): the phone's EQ did not reach the truck during the tests, so the closed loop never saw it.

### 2.5.0: draft Set up, the EQ check decides, preview tunes measured on the phone's EQ
- TuningLogic (pure, FlowTest): `keepsResumePlace(step)` (HOME, FRESH, SETUP keep `resumeStep`); `NextMove` gained `REPEAT`, `SET_ON_EQUIPMENT`, `MEASURE_ON_PHONE_EQ` (appended); `Recommendation.keepIdx`; `recommendNext(runs, eqMayNotBeApplied, eqCheckSeverity, phoneRoute, way)`: with the doubt, a check verdict of 2 gives SET_ON_EQUIPMENT, 0 or 1 REPEAT, 3 the usual move, none CHECK_EQ; a KEEP move on the phone route with `ApplyWay.IN_APP` becomes MEASURE_ON_PHONE_EQ unless `needsPhoneEqRun` finds a run on the phone's EQ (`appliedParam != null`) with the same EQ (`eqDistanceDb` < 0.5 dB). `bypassAfterCheck(set, output, severity)`.
- VM: `tuneFresh()` only goes to SETUP; `tuneSetup` switches AutoTune EQ off, calls `startNewTest` and `resetToSuggestion`. `EqCheckView` carries `testId`/`runCount`; `refreshView` passes the verdict to `recommendNext` only for the run it followed, and `eqCheck()` calls `refreshView()`. `UiState.eqBypassOutputs` (pref string set `eqBypassOutputs`, keyed by `outputName`) makes the EQ step open on equipment. `tuneOnEquipment()`, `tuneOnPhoneEq(idx)` (the run's EQ in use, sliders cleared, `tuneApply(AUTOTUNE_EQ)`, APPLY when the phone's EQ did not come on).
- Fix: `measureRun` on the phone's EQ with `repeatNext` applies `prev.eq` (it applied the new suggestion before), and records it as `appliedParam`.
- Not verified on a phone: the new keys after a real EQ check, and the output name Android Auto reports staying the same between sessions.

### 2.6.0: Set up warns about the EQ's reach, Done offers automatic switching
- TuningLogic (pure, FlowTest): `phoneEqReachWarning(output, carMode, knownBypass)` (a proven bypass wins over the Android Auto guess); `BtRoute(address, name)`; `offerAutoSwitch(route, playsOnPhone, profileId, rules, automationOn)`.
- VM: `UiState.carMode` (`LoudEq.carMode`), `btRoute` (the media route when it is Bluetooth A2DP, API 28+ for the address) and `tunedProfileId` (set by `tuneKeep`), all refreshed with the outputs in `refreshMic`. `autoSwitchToTuned()` calls `setRule` and turns on `automationOn`.
- UI: the warning joins the "Before measuring" `Alerts` in `OutputPicker`; `DoneStep` pins the automatic switch or "Open the EQ", with Back to start and See all results as `TextAction`s.
- Not verified on a phone: that `AudioDeviceInfo.address` reports the car's Bluetooth address on One UI, and what output name wireless Android Auto shows.

### 2.7.0: no Stock or Verify page, Done on the Tune screen, Set up remembered per output
- TuningLogic (pure, FlowTest): `returnAfterMeasuring(from, runCount)`; `SetupMemory` (`toText`/`fromText`, format `1;target;source;spots;gate;bass;treble`); `showsTuned(tunedTestId, tunedRuns, openTestId, runCount)`.
- VM: `UiState.measureFrom` (set by `startMeasuring`, used by `tuneSetup`, `tuneMeasureStock`, `tuneMeasure`); `leaveMeasuring()` from `closeMeasureView` and a cancelled `run {}` returns to `returnAfterMeasuring` and drops pending spots. STOCK and VERIFY stay as `TuneStep` values (they decide `measure()` and the step bar) but have no page: TuneTab draws the step they return to. A failure before the measuring screen opened opens it as FAILED. `micMissing()` + `askMic` (collected in AutoTuneScreen) replace the permission landing. `tuneKeep` sets `tunedTestId`/`tunedRuns`, clears `resumeStep`, goes to HOME. Pref `setup_<outputName>` via `rememberSetup`; `UiState.setupMemory` is read in `refreshMic`; `newTaste` carries the remembered taste into the stock fit and the saved test.
- UI: `MeasureStep`, `DoneStep`, `Failure`, `Ring` are gone; `TunedPanel` (Tune screen), `RunNote` (EQ step and result, Advanced); `LevelResult` is internal and shown on the failed measuring screen with "Check the level". `TuneStep.DONE` renders the Tune screen.
- Not verified on a phone: the measuring screen's Close and Back landing on the right step, and Set up's memory when the output name changes between sessions.

### Braun looks only, palette (unreleased, after 2.7.1)
- Theme.kt: `Look` keeps `BRAUN` (shown as "Classic"), `BRAUN_GRAPHITE` ("Graphite"), `BRAUN_BLACK` ("Black") and appends `WHITE` and `GRADIENT` ("Gradient edge"); the enum names are what pref `look` stores, so they did not change. Dyno, Studio, Console, Hi-fi, VU, Synth and Navy are gone with their palettes, `SliderStyle` (only the fader is left, in `VSlider`), the wallpaper colours (`dynamicColor`, pref `dynamic`) and `Look.allowsWallpaper`. `AtColors.edge` (a gradient for the deck outline, Gradient edge only; `deckPlate()` draws it) and `AtColors.copy`. `withPalette(p)` puts the user's colours in, `themeColors(look, dark, accent, palette)`, `AutoTuneTheme(mode, look, accent, palette)`.
- TuningLogic (pure, tested): `RETIRED_LOOKS`, `resolveLookName` (a retired or unknown name opens `BRAUN`), `PaletteSlot`, `Palette`, `parseHexColour` (#RGB / #RRGGBB, else null), `hexColour`, `paletteFrom`, `PALETTE_NEUTRALS`, `PALETTE_ACCENTS`, `mixArgb`, `readableInk`, `mutedInk`, `onAccent`.
- VM: `UiState.palette`, `setPaletteColour(slot, argb)`, `resetPalette()`; `migrateLooks()` on start rewrites a retired `look` to `BRAUN` and removes `dynamic`.
- UI: Settings, Advanced: the Palette deck (`PaletteEditor`, a `DropSelect` per colour with swatches, "Custom…" opens `HexColourDialog`, "Reset palette"). `DropSelect(swatches = …)` and `Swatch` in Kit.kt.
- Not verified on a phone: the looks and the palette editor were checked by unit tests and mockups only.

### Palette fix, Tune screen, quieter results, graphics, score (unreleased, after 2.8.1)
- Palette root cause: a dark ink (Slate to Black) read 4.5:1 on none of the night surfaces (Classic's night body #121211 and deck #232321, the Graphite and Black night plates), so `readableInk` dropped it everywhere and the pick changed nothing; by day it only reached the decks, where the look's own ink #232321 is already within 1.1-1.3:1 of Charcoal, Near black and Black. The drop-down itself was fine (2.8.1's scrolling menu did not change this). TuningLogic (pure, tested): `InkReach`, `inkReach`, `inkNeedsDay`, `inkNote`. Theme.kt: `usesDaySurfaces`, `surfaces` (a look with decks keeps its night body and takes the day decks; one without takes its day version), `themeColors` uses it, `paletteInkNote` (the status line under Ink), `isNight(mode)`.
- Tune: `TuneHome` has no `ScreenTitle` (no wordmark, mode key, title or speaker art); AutoTuneScreen's `hasHeader` is false for the whole Tune tab. Other tabs keep their header and fold.
- Dial: `dialFace`, `dialDots` (TuningLogic) and `dialColours(AtColors)` (Theme.kt, tested for every look, day and night, with palettes); `PulsingButton` draws a light face, dark holes and a sweep with a rim arc, period `motionMs(2400)`.
- Results: `ResultSummary(s, namesTest)` (no `showNext`): notes are `AlertsIcon` in the `RunSheet` corner; `NextLine` is gone; `resultNext(s)` gives the results page's `PinAction(..., sub = "next: …", hint = why)`; `PinnedAction.sub` replaces "suggested" under the key. The page's other choices moved after the checks. Report card verdict and resonance advice moved into bubbles.
- Graphics: `rememberArrival(key, ms)` (Kit.kt); `HeaderArt` plays one movement per key; the report card's meters fill once.
- Score: `knownScore` (TuningLogic) picks the focus run's `report.overall` or a compared run's score, never a new one; `Reading.score` and `ScoreTag` show it beside the dB (results display, Tune result, EQ step subtitle, tuned panel, runs list).
- Not verified on a phone: the dial's sweep and the arrival movements, the palette on the S23 Ultra at night.

### Results, runs, palette and graphics, round 2 (unreleased, after 2.9.0)
- Palette root cause (found by driving the whole chain in `ThemeTest.chosenInkIsTheTextOnScreenByDayAndByNight`): the drop-down, `setPaletteColour`, the stored `palInk` and `paletteFrom` were all correct. The owner's screenshots are by day (Classic, palette body Warm white, deck White). By day a dark ink never reached Classic's dark body (`readableInk` silently kept the light body ink: titles, tab labels, the main key), and on the light decks it replaced an ink that is already as dark (#232321, or the #1B1B1A fallback on a light body: Charcoal, Near black and Black are within 1.1-1.2:1 of it). 2.9.0's `usesDaySurfaces` only acted at night, and `paletteInkNote` only compared the deck's text, so it said nothing. Now: TuningLogic `isDarkInk`, `InkSurface`, `inkSurface(ink, own, light, dark, fixed)`, `SAME_INK_CONTRAST`, `inkNote(ink, shown, before, moved)`, `PaletteMenu`/`paletteMenu(slot, now)` (the editor's names, swatches and selection); Theme.kt `InkMove`/`inkMove`, `surfaces` (each surface takes its light version for a dark ink or its dark version for a light ink, by day and night; Classic's light body is the day deck's colours; a surface set in the palette stays), `paletteInkNote` compares the text on body and decks with and without the ink. `inkNeedsDay` and `usesDaySurfaces` are gone.
- Graphics: `GRAPHIC_PLAYS` (2), `arrivalLegs`, `returnMs`, `shownOnScreen` (TuningLogic). Kit.kt: `Arrival`, `rememberArrival(key, ms, shown)` waits until the graphic can be seen (`Modifier.arrival(a)`, or `shown` for the header art: the header at least half open), then plays forward on `EaseArrive`, a quick return on `EaseLeave`, forward again; `playLegs`. `LocalReplay` (per screen) restarts every arrival and the dial.
- Pull to replay: `PullReplay(thresholdPx)` (TuningLogic); AutoTuneScreen adds a `NestedScrollConnection` to each screen's scroll column: downward drag left over while the scroll is at 0 (source `UserInput`) adds up, and past 64dp it increments `replays[key]`, provided as `LocalReplay` to the content and the header. `onPreFling` ends the gesture.
- Tune dial: `PulsingButton` pulses from the centre out (`PULSE_MS`, `pulseFront`, `pulseLight`): the holes near the front darken and swell, a faint ring marks the front, the rim darkens as it arrives; twice when shown, again on a pull; none with "Remove animations" or while busy. The infinite sweep is gone.
- Results display: `Reading(note, noteTint)`; `ReadingCells` is a `Layout` that keeps one line when the readings fit at their natural width (`readingsFitOneLine`) and otherwise puts the main reading on its own line and the rest in columns under it (the score box was being squeezed to "52/"). `ResultSummary` with runs shows the latest (score, "−2.0 vs stock" note), the stock (score) and the best (score where known) and has no headline line; `resultHeadline` is in the bubble beside the warning icon. Tints come from `deckColors` (the body's light green and amber were drawn on the light deck). `StockAnalysis.score` keeps the stock review's report score; `knownScore(…, stockScore)` uses it for the stock.
- Runs list: `runColumns()` measures the widest score box ("100/100") and figure ("99.9 dB") at the font scale; the row is tick | name and date | score (fixed, end-aligned, empty without one) | dB (fixed, end-aligned) | delete (48dp, empty for the stock).
- Test results page: `Page.RESULTS` opens tall (header art at its size, where it plays). Charts: `ResponseChart` curves rise from the target line on arrival; the full-screen view is `ChartZoomLayer` (a sheet over the app in the same window, `ChartZoom`/`LocalChartZoom`, Back closes it), with the old `Dialog` kept only outside the shell (screenshot sheets).
- Not verified on a phone: the pull gesture with One UI's stretch overscroll, the arrival timing on a real scroll, the full-screen chart, the palette by day and night.

### First-start tutorial (2.11.0)
- After the CrossPaths tutorial (its `WelcomeScreen` and the "first-start tutorial" rule in its DESIGN.md): full-screen cards you swipe, each with a small picture that restarts when its card becomes current, the picture moving further than the words, Skip always there (but on the last card), shown once and replayable.
- TuningLogic (pure, `TutorialTest`): `TutorialCard`, `TUTORIAL_CARDS` (hello, measure, eq, verify, keep), `TUTORIAL_STEPS`, `tutorialStepLabel`, `tutorialKeyLabel`, `tutorialShowsSkip`, `tutorialBack`, `tutorialPos` (a card's place from the pager's page and offset), `CardMotion` and `tutorialMotion(pos)` (picture 0.45 and words 0.18 page widths on top of the swipe, fade, picture scale down to 0.85; the page lights use its alpha), `playPhase`, `tutorialPlayMs`, the demo system `tutorialCurveDb(x, tuned)`, `tutorialReadingDb` (4.1 to 1.2 dB), `TUTORIAL_FADERS`, `tutorialFaderX`, `tutorialFaderDb`.
- ui/Tutorial.kt: `Tutorial(onDone, start)` is a `HorizontalPager`; every movement reads the pager in `graphicsLayer`/`drawBehind`, so a swipe only redraws. `PageLights` are the dots (tap to jump). `CardArt` runs an `Animatable` from 0 to `tutorialPlayMs` when its card becomes current and drops it back to 0 once the pager settles elsewhere; with "Remove animations" or in `LocalInspectionMode` it holds the last frame. `TutorialArt(key, ms, width, height)` (internal, stateless) draws the five pictures in the deck's colours (`HelloArt`, `MeasureArt`, `EqArt`, `VerifyArt`, `KeepArt`).
- Kit.kt: `MainKey(text, onClick, sub, enabled, description)` is the main key, taken out of `BigActionBar` (which passes `a.sub ?: "suggested"`); the tutorial's Next / Get started is the same key.
- VM: `UiState.showTutorial` (`!tutorialSeen` on start), `tutorialDone()` (sets the pref), `replayTutorial()` (Settings › About, "Replay the tutorial"). AutoTuneScreen draws the tutorial over every screen but the teak cheeks; it fades in, and fades out with a 1.04 scale.
- Screenshots: `g1`-`g6` in tools/screenshots/ShotsTest.kt.
- Not verified on a phone: the swipe and its layering under a real finger, Back on the first card, the fade into the app.

### Gradient edge default (unreleased, after 2.11.0)
- Asked 2026-11-09: the owner's own setting (Gradient edge, accent As the look, palette Warm white / White / Slate / Braun red / Aluminium) is Gradient edge's default palette and Gradient edge is the app's default look.
- TuningLogic (pure, tested): `DEFAULT_LOOK` ("GRADIENT"), `resolveLookName(saved, kept, fallback = DEFAULT_LOOK)`, `GRADIENT_DEFAULT_PALETTE` (built from the `PALETTE_NEUTRALS`/`PALETTE_ACCENTS` presets by name), `lookDefaultPalette(lookName)` (empty for the other looks), `PALETTE_AS_LOOK` ("look", what a slot stores for "As the look" where the look has a default; not a hex colour, so older versions read it as not set), `paletteFrom(defaults) { read }` (missing or bad value takes the default, `look` is the look's own), `paletteStoredValue(slot, argb, defaults)`.
- Theme.kt: `Look.DEFAULT`, `Look.palette`, `Look.of` falls back to `DEFAULT_LOOK`; Classic's detail no longer says "The default", Gradient edge's does; `AutoTuneTheme(lookChoice = Look.DEFAULT, palette = lookChoice.palette)`; `LocalAt`'s fallback is the default look with its palette.
- VM: `UiState.look = Look.DEFAULT`, `UiState.palette = Look.DEFAULT.palette`; `savedLook()`, `savedPalette(look)` (the stored keys over the look's default); `setLook` re-reads the palette for the new look; `setPaletteColour` writes `paletteStoredValue`; `resetPalette()` removes every key and shows the look's default palette; `migrateLooks()` rewrites a retired look to `DEFAULT_LOOK`, wrapped so it never throws. The palette stays one global set of keys (`palBody`…`palRule`); a missing key means "the look's default".
- UI: `PaletteEditor(p, start, …)`: the drop-downs show the palette in effect (so the names, not "As the look"), Reset palette is enabled when it differs from the look's default.
- Who moves: a fresh install and anyone whose `look` pref is missing, retired or unknown opens Gradient edge with its palette (keys they saved still win). A look saved by name stays.
- Not verified on a phone: the default on a fresh install, Gradient edge's palette with Light or dark on System at night (the deck edge keeps its night gradient on the white decks).

### Home theatre with the TV (2.13.0)
- Asked 2026-11-10: an Android TV companion; the phone listens, the TV plays and applies the EQ; home-theatre first; each speaker tested on its own over HDMI ARC; graphics of each speaker playing. Built as one APK (same `applicationId`), so DevStore releases it unchanged and Obtainium on the TV installs it.
- dsp/Theatre.kt (pure, `TheatreTest` with a simulated 5.1 room): `Speaker` (map position, `code` for files), `Layout` (2.0 to 7.1), `PlayMethod` (`DOLBY_51`, `PCM_71`, `PCM_51`, `STEREO`; `slots` in Android channel order; `pick(dolby, rawChannels)`), `ChannelClip` (2.5 s lead for the receiver to lock, start beeps on front left, 6 s sweep on the tested speaker, LFE 10 dB lower), `ChannelAnalysis.read` (sweep arrival after the beeps → `deltaMs`), `TheatreJudge.judge` (levels over 500 Hz-2 kHz, sub over 40-100 Hz plus the target's bass lift; distances against front left so the beep detector's offset cancels; EQ from the stock readings; `downmixed` = same arrival within 0.25 ms and same shape within 0.6 dB as a front; 25 dB below the median = not heard).
- Dolby clips: `app/src/main/assets/theatre/<code>.ac3` (448 kb/s, dialnorm −31), built by `tools/theatre/make-clips.sh` (`:dsp:theatreClips` writes raw PCM from `ChannelClip`, ffmpeg encodes). Rebuild them when `ChannelClip`, `Sweep` or `Marker` change. Checked by decoding: flat to 16 kHz (−0.5 dB at 19 kHz), zero timing error, sub level with the mains once the receiver adds its 10 dB.
- link/Link.kt (pure, `TheatreLinkTest`): one line per message, `type key=value` URL-encoded; `Msg` builders (hello/welcome/denied, play/playing/done/failed, show, eq/eqoff/eqstate, bye); `Msg.chains` clamps what the TV accepts (−15..+6 dB, Q 0.1-20, preamp −15..0). `Pairing` codes.
- theatre/: `LinkClient` (NSD discovery and resolve, socket, one writer thread), `TheatreStore` (`filesDir/theatre/stock.txt` and `latest.txt`, versioned text v1), `theatreKey`, `theatreNextSpeakers`, `TheatreViewModel` (records with `MeasurementRunner.recordOnly`, sends `play` at second 1, stops once the sweep is in or 2.5 s after the TV's `done` with no beeps; the first complete test of a layout becomes the stock; `useEq` sends every speaker's EQ with one shared preamp). Prefs `tvCode_<name>` remember the code per TV.
- tv/: `TvActivity` (leanback launcher, landscape, keeps the screen on, `TvScreen(TvUi, …)` for screenshots), `TvViewModel` (ServerSocket, DNS-SD, pairing, playback, shows the phone's rows), `TvAudio` (probe: HDMI/ARC/eARC device, `isDirectPlaybackSupported` for AC-3 5.1 and PCM 5.1/7.1; plays AC-3 bytes through an `ENCODING_AC3` AudioTrack or interleaved PCM16), `TvEq` + `TvEqService` (DynamicsProcessing on session 0, per channel by `PlayMethod.slots`, prefs `autotune_tv`; held off while a raw clip plays).
- ui/: `SpeakerMap` (shared by phone and TV), `TheatrePage` (a `Page` over the current tab, `Tab.THEATRE`), entry key on the Tune home. Manifest: microphone, touchscreen, bluetooth, leanback and screen orientations not required; `ACCESS_NETWORK_STATE`; `tv_banner`.
- Screenshots: tools/screenshots/TheatreShots.kt (TV pairing, playing, result; phone maps).
- Not verified on real hardware: AC-3 passthrough over ARC on a real TV and soundbar, the TV's direct-playback answers, NSD on the owner's Wi-Fi, the TV EQ (global effects are often refused on TVs), and the leanback banner on Google TV.

### AutoTune for Windows (2.14.0)
- Asked 2026-11-10: a Windows companion; the phone measures, the PC applies the EQ; stereo desktop speakers first, built so a PC feeding a home theatre over HDMI (per channel) can follow. Owner chose Equalizer APO for the EQ and Kotlin (shared code) over C#.
- The PC is a second device on the TV's link: same `_autotune._tcp` service, port 47810, four-digit code, messages. `Msg.welcome` now carries `kind=pc` (or `tv`; a welcome without it is a TV), and the phone words its keys from it (`TheatreUi.device`, `theatreKey(r, device)`). The phone's theatre page drives both; the PC answers `method=STEREO`, so the layout is 2.0.
- `:link`: `Link.kt` moved from `:app` unchanged apart from `kind`; `LinkServer` is the TV-or-PC end (one phone at a time, the first line must be the hello with the code, a wrong code is answered after 1.5 s, a phone that pairs replaces the one before, a stranger never does). `LinkServerTest` runs it over a real socket. Since 2.14.1 the TV uses `LinkServer` too (its own loop sent "done" from the main thread and crashed with NetworkOnMainThreadException after the first clip). `LinkServer.send` only queues: one writer thread sends every line, so it is safe from any thread and never waits on a slow phone (`sendingNeverWaitsOnTheNetwork`).
- desktop/ (package `dev.autotune.pc`):
  - `PcEndpoint` (pure, `PcEndpointTest` over a real socket with a fake Equalizer APO and sound card): pairing, `play` (clip from `ChannelClip.interleaved16(speaker, STEREO)`; AutoTune's EQ written off first and back after, so readings stay raw like the TV's `TvEq.hold`), `show`, `eq` (kept with who sent it and when), `eqoff` (keeps the bands, so the PC's key brings them back), `eqstate` with the reason when the EQ cannot reach the speakers (also sent right after the welcome when something is wrong).
  - `Apo.kt` (pure, `ApoTest`): `ApoText.config` writes `autotune.txt`: `Device: {endpoint guid}`, then per speaker `Channel: L`/`R` (numbers 1-8 in `PlayMethod.slots` order for multichannel), `Preamp`, `Filter n: ON PK …`, then `Channel: all`, `Device: all`. EQ off is the same file with no filters. `withInclude` adds `Include: autotune.txt` at the end of config.txt; `setupScript` is the one administrator step (back up config.txt to `config-before-autotune.txt`, copy the new one in, create `autotune.txt`, `icacls` grant the user's SID modify). `ApoRegistry` parses `reg query`: the config folder (`HKLM\SOFTWARE\EqualizerAPO` ConfigPath, else InstallPath\config), Equalizer APO's class ids (every `AudioEngine\AudioProcessingObjects` entry named like "Equalizer APO", plus `{EACD2258-…}` from memory), and per output (`MMDevices\Audio\Render\{guid}`: name = description + " (" + adapter + ")", `DeviceState`, whether `FxProperties` holds one of those ids). Output names from Java Sound match a device exactly or by a cut prefix (31 characters). States: `MISSING`, `NEEDS_SETUP`, `NOT_ON_OUTPUT`, `UNKNOWN` (cannot tell; writes without blocking), `READY`.
  - `Platform.kt`: `WindowsApo` (reg through `cmd /c chcp 65001`, writes `autotune.txt` in place so its rights stay, setup through PowerShell `Start-Process -Verb RunAs`), `JavaSoundOut` (Java Sound mixers, DirectSound on Windows; 16-bit 48 kHz), `Announcer` (JmDNS, "AutoTune on <PC name>", `kind=pc` TXT), `Net`, `PcStore` (`%APPDATA%\AutoTune\pc.txt`, key=value: code, output, chains in `Msg.chainsText`, eqon, from, when).
  - `ui/Kit.kt`: the phone's Gradient edge look and palette as constants, Inter from `resources/fonts`, decks with the shaded edge, `DropSelect`, `LatchKey`, `InfoButton`, `Alerts`, `ActionKey`, line icons, the knob mark (also `icon.ico`). `ui/PcScreen.kt`: Phone deck (code and address, or the two speakers with the playing one red), Output deck (drop-down, Equalizer APO line, the one key it needs), EQ deck (preamp, key, curve per speaker). `Main.kt`: window plus tray (closing hides; Quit ends), one instance (`running.lock`).
  - Screenshots without a screen: `./gradlew :desktop:shots` writes `desktop/build/shots/pc-*.png` (`ShotTool.kt`).
- Release: `.github/workflows/windows.yml` on windows-latest tests `:dsp :link :desktop` and runs `:desktop:packageMsi` (pull requests keep the MSI as an artifact). On main it waits for DevStore's `android-v<name>+<code>` release and adds `AutoTune-<name>-windows.msi` to it, once per version. 2.14.0 first went out as its own `windows-v2.14.0` pre-release; it was the newest release and had no APK, and Obtainium (which looks at the newest release only unless "fallback to older releases" is on) said "Could not find a suitable release". The workflow deletes that release. Never give the PC its own release. The MSI installs per user (no administrator); `upgradeUuid` must never change. The MSI is not code-signed, so Windows SmartScreen asks once (More info › Run anyway).
- Not verified on Windows: the MSI install, Java Sound output names against the registry's, Equalizer APO's registry entries (the class id lookup and `FxProperties`), the `Device:` line with only the GUID, the administrator step, the firewall prompt on first start, and JmDNS being found by the phone's NSD. Checked here: all tests, the packaged app started on Linux under Xvfb, a scripted phone paired (and a wrong code was refused), sent an EQ and asked for a clip.
- Restarts (2.14.1): `BootReceiver` also takes `MY_PACKAGE_REPLACED` (an Obtainium update stops the app) and starts `TvEqService` when `TvEq.wanted` (prefs `autotune_tv` hold chains); the service restores the EQ. The phone's EQ follows the same receiver as before.
- A phone or tablet as the TV: Settings › Advanced › "TV screen" starts `TvActivity` (it is only in the TV's leanback launcher otherwise). On a tablet `TvAudio.probe` finds no HDMI, so it plays STEREO on its speakers; `TvEq` runs on its session 0 like the phone's EQ. Its keys are touchable; Back finishes it.
- Next: multichannel playback (WASAPI through JNA) for a PC on a receiver over HDMI, which would also let the PC apply each speaker's level and delay (`Preamp` and `Delay` per channel in Equalizer APO).

### Home theatre in two tests, pretend, the room in 3D (2.15.0)
- Asked 2026-11-10 after trying the TV on a tablet: fewer beeps (one set, not one per speaker), the test split into levels (pink noise, gains are per speaker) and a full system test for the EQ (one EQ for all, as a soundbar has), research the best flow, a tilted 3D map (owner picked view C, "from the seat", with rounded cabinets), and the parked "pretend" mode, showing the real system. Research: levels with band-limited pink noise (about 500 Hz to 2 kHz) at the seat, the sub with its own low noise; distances need a timed signal (the sub's acoustic delay in particular); EQ measured with the whole system after levels and distances.
- dsp/TheatrePass.kt (pure, `TheatrePassTest` in a simulated 5.1 room): `Channels.interleave16(planes, method, mixDown)` (a speaker without a channel plays from the front pair at −3 dB: pretend); `LevelNoise` (periodic pink noise built in the frequency domain, 2^17 samples, −20 dBFS RMS, sub −10 dB on the LFE); `LevelMeter` (`bandDb` from Hann windows with the mic calibration off, `change` against front left with the target's bass lift for the sub, 0.5 dB steps, steady = within 0.5 dB for 3 s); `PassClip` (lead 2.5 s, beeps once on front left, each speaker's 6 s sweep with 1 s gaps, then all speakers together; `slots`, `sweepAfterMarker`, `nowPlaying`); `PassAnalysis` (each sweep read in its own window, 0.5 s before to 0.9 s after, through `ChannelAnalysis.read`'s new `sweepAfterSec`/`beforeSec`/`afterSec`); `PassJudge` (per-speaker levels and distances from `TheatreJudge`, one EQ from the whole system's reading with `FIT` 25 Hz to 12 kHz, fitted from the stock pass). `PassReport`, not `SystemReport` (that name is the report card's, in Extras.kt).
- Link: `noise` (speaker, layout) loops until `quiet` (or 2 minutes); `pass` (id, layout, hold) plays the clip and sends `playing` per sweep (`speaker=all` for the last); the welcome carries `caps=noise,pass`, `pretend=1`, `real=`. The phone uses the two tests only when `caps` has both, so a 2.14 TV or PC keeps the per-speaker flow.
- TV: `TvAudio.Clip` (PCM or an AC-3 asset; `speakerClip`, `passClip`, `noiseClip`), `play(…, loop)`. `TvViewModel`: `noise`, `pass` (holds `TvEq` when asked), `setPretend` (prefs `pretend`; only on a stereo output and with no phone connected; the welcome then says `PCM_71`). Dolby assets: `pass-L20/L21/L31/L51.ac3` (384 kb/s), `noise-<code>.ac3` (192 kb/s, three loops = 256 frames), built by tools/theatre/make-clips.sh, which now decodes the passes back and runs `:dsp:theatreCheck` (`CheckTheatreClips`: every speaker and the system heard, distances within 5 cm).
- Phone: `TheatreViewModel.levelTest` (records per speaker with `recordOnly`, reads the last second each second after 3 s of settling, `nextLevel`), `systemTest`/`measurePass` (the first complete pass of a layout is the stock and plays with the TV's EQ held), `judgeSystem`, `showSystem`, `useEq` sends one chain to every speaker; `rejudge` picks the right judge. `systemNext`/`systemKey` (TheatreStore.kt, tested) lead the pinned key. TheatreStore v2: `r=ALL`/`m=ALL` (the system), `pretend`, `real`; v1 loads. A pretend session is never the stock. UI: `TwoTests`, `LevelDeck`, `LevelsSet`, `SystemSummary`, `SystemDetails` in TheatrePage.kt.
- The room: ui/room/Room3d.kt, plain Compose with no Android imports, so `:desktop`'s test source set compiles it and `./gradlew :desktop:shots` renders `room-phone.png` and `room-tv.png`. Camera 31° down, 2.8 room-widths away; cabinets turned to the seat, faces drawn far to near with `rounded` corners; `ROOM_ASPECT` 1.75. `SpeakerMap` wraps it (same parameters, plus `all`).
- Windows: `PcEndpoint` handles `noise` (front left or right, looping) and `pass` (2.0), with `AudioOut.play(…, loop)`.
- 2.15.1: `TvEq` configures DynamicsProcessing with 2 channels (`stereoChains`): Android's output mix is stereo before HDMI, and 6 channels on session 0 failed on the owner's Android box with "AudioEffect: bad parameter value".
- Not verified on hardware: the live meter's readings against a real SPL meter, the Dolby passes and noise on a real ARC soundbar (they decode and check correctly here), the pretend mode on the owner's tablet.

### Three home-theatre tests, named home theatres, the TV shows the test (2.16.0)
- Asked 2026-11-10: levels, distances and EQ fully separate (distances a third test so it is not repeated), named home theatres with every run saved and compared, the subwoofer never heard, the TV showing the phone's graphics and the full result, and speakers and a sofa after Braun's small devices (the owner sent a photo of Braun radios).
- dsp/TheatrePass.kt: `PassKind` (`SYSTEM` = 2.15's pass, `DISTANCES` = each speaker, `EQ` = all together); `PassClip.slots/seconds/planes/nowPlaying` and `PassAnalysis.read` take it. `DistanceJudge` → `DistanceReport` (per speaker, the sub's delay included), `EqJudge.judge(system, seats, target, stock)` → `EqReport` (one EQ from the stock EQ run, `worth`), `EqJudge.reached(off, on, eq)` (the EQ check), `averageSeats` (power average). Theatre.kt: `SUB_EXTRA_DB` (the sub gets 10 dB more room before not heard), `SUB_MISSING` (what to check on the soundbar).
- Link: `caps=noise,distances,eqtest,live`; `pass … kind=distances|eq` (no kind = 2.15's system pass); `live` (level, `waveText` heights in base 36, status, change) about every 150 ms while the phone records; `result` (headline, readings, response in third octaves, target, notes, next) after each test. The phone uses the three tests only when `caps` has noise, distances and eqtest.
- Phone: theatre/TheatreTests.kt — `TheatreTest` (id, name, runs) of `TheatreRun` (kind LEVELS/DISTANCES/EQ/SPEAKERS, layout, device, pretend, real, levels, readings, system, seats, eqInUse, note) in `filesDir/theatre/tests/<id>.txt` ("autotune-theatre-test 1"; tested round trip); `eqStock` = the first heard, non-pretend EQ run; `migrate` turns 2.15's stock/latest into a first test once. `TheatreViewModel`: `levelTest`, `distancesTest`, `eqTest` (seats wait on `nextSeat`), `eqCheck`, `addRun`, `judgeTest`, `sendResult`, `startLive`/`stopLive`; prefs `theatreTest`, `theatreSeats`. `ThreeStep`/`threeNext`/`threeKey` (TheatreStore.kt, tested) lead the pinned key. UI: `ThreeTests`, `TestBar`, `LevelsResult`, `DistancesResult`, `EqResult`, `RunsDetails` in TheatrePage.kt; Tests tab `TheatreList` opens the page on that home theatre (`TheatrePage(pick, openId)`).
- TV: `TvUi.live`/`result`, `TvScreen` with the room and the live deck (`LevelMeter`, waveform) or the result chart on the left, readings, headline, notes and next on the right. Dolby assets: `distances-L*.ac3` and `eq-L*.ac3` (384 kb/s), checked after decoding by `CheckTheatreClips`; `pass-*.ac3` removed (a 2.15 phone asking for the system pass on Dolby is told to update).
- Room3d: `Block`/`drawBlock` (rounded faces), `drawCabinet` (dot grille, a round field on the sub, pilot dot), the sofa drawn in depth order.
- Windows: `PcEndpoint` plays `pass` by `PassKind.of(kind)`.
- 2.16.1: on the owner's box (2.15.1) the second system pass, played with `TvEq` on, heard nothing, while the first (EQ held) was fine; the level noise always held the EQ. Now `TvViewModel.pass` holds the EQ for every Dolby clip, and `pass … pcm=1` (the phone's `eqCheck` on `DOLBY_51`) plays the EQ pass as plain stereo, every speaker folded into the front pair, through the mix where the EQ acts. `EqPilot` on the phone shows the TV's EQ state under the room.
- Not verified on hardware: the TV's live screen and result over a real Wi-Fi, the seat averaging, the EQ check on the box.

### AutoTune in DevStore on the PC (2.16.1)
- Asked 2026-11-10: DevStore's PC app listed AutoTune as Android only. DevStore (github.com/qawalah/devstore, `windows/agent`) installs a Windows app only from a `windows-vX.Y.Z` release (the highest, not draft or pre-release) holding `<name>-X.Y.Z-win-x64.zip` and its `.sha256` ("<hex>  <file>"); the zip's root holds `devstore.json` (name, type `gui`) and `<name>.exe`. Store listing: `store.json`, `store-icon.png`, `store-screenshot-N.png` on the release, else `store/icon.png` and `store/screenshots/` in the repo.
- `windows.yml` builds `:desktop:createDistributable` too, copies the app folder, renames `AutoTune.exe` to `autotune.exe` and `app/AutoTune.cfg` to `app/autotune.cfg` (the jpackage launcher reads `app/<its name>.cfg`), writes `devstore.json` (`updateWhileRunning`: DevStore installs each version in its own folder), zips it and, on main after the Android release exists, creates `windows-v<name>` with `--latest=false`. Owner chose this over the MSI only and set Obtainium's `^Android` (the release title, not the tag) filter on the phone and the TV.
- `store/icon.png` (from `desktop/icon.ico`) and `store/screenshots/windows/` (from `:desktop:shots`) are the store pictures.

### The owner's notes on the home theatre and the PC (2.17.0)
- Asked 2026-11-11 (BACKLOG "owner's notes"): keys inside each test's card, home theatres managed on their own page, a PC showing the TV's results, a big EQ on/off key, EQ editing and 5.1/7.1 on Windows, the Configurator key doing nothing, a landscape PC window, no nagging about levels, and the TV and PC showing the phone's measuring display.
- Shared drawing: `app/.../ui/gauge/Gauges.kt` (plain Compose, no Android: `GaugeColors`, `LevelReading`, `Pilot`, `SweepDial`, `WaveLine`, `SignalMeter`, `MeasureDisplay`, `sweepText`). The phone's `MeasureOverlay` uses it; the TV's live deck and the PC's test deck use `MeasureDisplay`. `:desktop` compiles `ui/room` and `ui/gauge` in its main source set (they must stay free of Android imports). Link: `live … ws=` carries 96 waveform samples (`Msg.sampleText`/`Msg.samples`, base 36, scaled to the peak).
- Phone: `ThreeTests` has no pinned suggestion (`threeNext`/`threeKey`/`ThreeStep` are gone; the result sent to the TV has no "next"); `LevelsResult`/`DistancesResult`/`EqResult` take `keys`; `EqKey` (big latch: `useEq`/`tvEqOff`); `TheatreLine` opens `TheatresPage` (local state with BackHandler). `TheatreTest.device` (`device=` line; older files load with none) and `owner`; `TheatreTests.forDevice`; `TheatreViewModel.openTestFor(name)` on connect. A `welcome` mid-session (the PC changed its speakers) updates the method and layout.
- Windows: `PcScreen` is two columns (window 1180×760, at least 900×560). `PcUi.method` (stereo, 5.1, 7.1; `PC_METHODS`, saved as `method=` in pc.txt), `AudioOut.maxChannels`, `setMethod` re-sends the welcome; `setChains` (EQ edited on the PC, `eqFrom` "this PC", preamp from `EqChain.withSafePreamp`); `PcLive`/`PcResult` from `live`/`result`. Equalizer APO: `ApoStatus.installDir`/`withApo`, `ApoControl.openConfigurator` (Start-Process -Verb RunAs on Configurator.exe), `PcEndpoint.useApoOutput`.
- Not verified on hardware: 6- and 8-channel output through Java Sound on Windows (DirectSound may refuse more than 2 channels; the PC then says the output takes fewer), the Configurator prompt, the new phone page on a phone.

### Round 2 of the owner's notes (2.18.0)
- Room3d: `RoomColors.tag`; each speaker's name and note on a plate, placed in front, else above the cabinet, else lower, never over the sofa's box or another plate (near speakers placed first). `SpeakerMap` floor = the deck's key grey with 7% of the accent (10% at night); the TV draws its room inside `DeckColors`.
- TV: `TvScreen`'s right column is `TvDeck`s (phone/code, playing or result, errors), `TvEqKey` (lit while on; pressing it switches it off), then the output line and the keys.
- Phone: `DeviceDeck` (connected device with Disconnect) at the top of `ThreeTests`; the room's line shows only status or pretend; Details › "EQ runs" lists EQ runs only (the note is on the last EQ run). `ResponseChart` keys its arrival on the curves, so new curves replay it.
- Paparazzi (tools/screenshots, kept out of the build) rendered the TV screens for this release.

### Bluetooth pauses and EQ clicks (2.19.2)
- `wiki/bins-and-bands.html` explains slots against bands for users (interactive, same model as `DpBins`), meant for an in-app wiki later; see `wiki/README.md`.
- The whole investigation (evidence, numbers, Android source references, the owner's questions) is in `DIAGNOSTICS-2026-11-11.md`; `tools/diagnostics/analyse_recordings.py` reruns its checks on any diagnostics zip.
- From the owner's diagnostics (11 Nov, F-150): 5 of 12 recordings had a 15-25 ms pause in playback partway through the sweep. The analyser took the stronger half as the direct sound and gated the rest away. One pause fell between the start beeps instead.
- dsp: `SweepCheck.chunkLagsMs` (0.5 s chunks of the sweep from 2 s, envelope of the correlation, ±80 ms) and `stall`/`stallIn` (a split after which EVERY chunk is ≥ `STALL_MS` = 6 ms away from the median before it; size = largest median step). On the real runs the clean recordings score ~0-2 ms, the stalled ones 9.5-20. `SweepCheck.quietStart` silences the first `MIC_SETTLE_SEC` (0.5 s). `Marker.find` lets a beep come up to `LATE_SEC` (60 ms) late and then returns where the first beep would have been. `Measurement.withFatal`. Tests: `StallTest`.
- Phone: `phoneSweep` measures up to `SWEEP_ATTEMPTS` (2) times when `SweepCheck.stall` finds a pause (log `sweep_stall`, `stallMs`/`stallAtSec`/`attempt` in the measurement event), then fails with the stall's message. `analysePhoneSweep` returns the reading and where the sweep starts. `noteCapture(levelsOf =)` reads the levels after `quietStart`; the kept WAV is the raw recording.
- `SystemEq`: effects are `Fx` (DynamicsProcessing, band edges, current gains, goal), created flat and moved by `fade()` on a single daemon thread (`EqRamp.frames`, 10 steps of 15 ms; a newer fade takes over from the current gains). `off()` and `rebuild()` retire the old effect (fade to flat, then release); a rebuild crossfades, so the sum stays at the EQ. No per-session effects any more (the receiver for `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` is gone). `SystemEqService` rebuilds on an output change, and on a player start only when `rebuildOnPlayback` (TuningLogic: output changed since, or `SystemEq.running()` false).
- DynamicsProcessing's frequency variant (AOSP `DPFrequency`) is an STFT: block = preferred frame duration × rate, rounded up to a power of two, 50% overlap, one gain per bin, bin k from the FIRST band whose `round(cutoff × block / rate)` reaches k. The default 10 ms (512 at 48 kHz, 94 Hz bins) collapsed the bass. `DpBins` (dsp, tested in `DpBinsTest`) models it: `block`, `owners`, `bandGains` (each band gets the mean of the wanted response over its bins), `binGains`, `realisedDb`. `SystemEq` sets `setPreferredFrameDuration(DpBins.FRAME_MS = 80)` (4096 at 44.1 and 48 kHz), fills the bands with `bandGains`, and `applied` = `EqChain.peakingAt` (the old `sampledTo` fit, now shared) of the realised bins at the centres. `SystemEq.sampleRate` comes from `outputRate()` (AudioManager's output rate) in the service and the ViewModel. `TvEq` still uses the default frame.
- Diagnostics: the `measurement` event carries `chunkLagsMs`, `stallMs`/`stallAtSec` (null when clean), `attempt`, `beepLateMs` (`Marker.findTimed`) and `peakOffsetMs` (direct sound against the beeps' sweep start, ~0 normally); `system_eq_rebuild` logs `reason` (output changed / first player after an output change / effect stopped), `wasRunning`, `skipped`; `info.txt` has a "System EQ blocks" line with `SystemEq.blockSize`, `sampleRate` and `frameMsInEffect()` (read back from the running effect).
- Not verified on a phone: the extra ~85 ms of delay with AutoTune EQ on (video lip-sync), whether enabling DynamicsProcessing itself still ticks on the S23 Ultra (the effect may add latency when it starts; the fade cannot help with that), and the stall check's cost on the phone (about 0.1 s per sweep on a desktop JVM).

### After the first diagnostics on 2.19.2 (2.19.4)
- The 11 Nov 23:xx zip is written up in `DIAGNOSTICS-2026-11-11.md` ("The second zip"). Confirmed on the phone: `info.txt` shows 4096 samples at 48 kHz and the effect reporting 80 ms; `SweepCheck.stall` caught a 14 ms pause; `peakOffsetMs` stayed within ±6 ms on the saved readings.
- `AudioManager.registerAudioDeviceCallback` answers a registration with `onAudioDevicesAdded` for EVERY device. `SystemEqService` registers each time it starts (each EQ switch-on), so 2.19.2 saw "output changed" one second after every switch-on and rebuilt (and again at the next player start). A rebuild costs a dropout and a skip: AOSP `DPFrequency::processSamples` pads its output with zeros until a block is ready, and a released effect takes its buffered audio with it.
- Now: `SystemEqService.mediaRoute()` (API 33+: `getAudioDevicesForAttributes` for media, named by `Outputs.nameOf`; older: every output) is snapshotted in `watchRoute()` before registering. `checkRoute` (400 ms after any device callback, and at each player start) rebuilds only when `routeMoved(route, now)` (TuningLogic, tested). `SystemEq.routeChanged` and `rebuildOnPlayback` keep the "first player after an output change" rebuild, now only after a real move.
- `phoneSweep` sets `measureView.message` for the second attempt and `MeasureOverlay` shows a non-blank message while MEASURING. `run {}` logs `measure_cancelled`. `micStopped(code, usbMic)` (TuningLogic) words a failed `AudioRecord.read`.
- Not verified on a phone: that one rebuild after a real output change is enough to keep the EQ on the new output (the old rule's second rebuild at the first player start is kept for that), and whether `getAudioDevicesForAttributes` has settled 400 ms after a Bluetooth connect (the check at the next player start covers a late move).
- Owner: still ticks on switching the EQ on and off. `SystemEq.off()` no longer releases: it fades the effect to flat, sets the limiter to 0 dBFS and marks it `parked`; `apply()` reuses the parked effect (limiter back to −2 dBFS) and fades in. `releaseIfParked()` takes it away when `letGoOfEq(parked, musicPlaying)` (TuningLogic, tested): at the end of a fade, 1.5 s after the last player stops, 400 ms after the notification shows the parked state, or in `onDestroy`. `SystemEq.musicPlaying` is `AudioManager.isMusicActive`, set by the service. The service stays up while parked (notification "EQ off"). A rebuild (real output change) still swaps effects and can tick once.

### Set up asks the equipment first (2.20.0)
- Owner, 2026-11-12: "start by asking the equipment type, then ask if there's a companion app installed, and revise the rest". `Equipment` (TuningLogic: CAR, HOME, PC, OTHER; `companion` is "the TV" / "the PC" / null) is Set up's first drop-down; `companionQuestion(e)` gives the second for HOME and PC. Installed → the only key is "Connect to AutoTune on …" (`rememberEquipment(e, true)` then `go(Tab.THEATRE)`, the page over Tune, so Back returns to Set up). Not installed → the usual draft Set up, and `tuneSetup(..., equipment)` forgets the companion answer for next time.
- What follows the equipment, all pure and tested in FlowTest: `startEquipment` (output memory, last pick, car mode, default target's kind), `equipmentTargets`/`equipmentTarget` (index lists into `Target.ALL`; `BUILT_IN` order untouched), `equipmentRoutes`/`equipmentRoute`/`routeLabel` (a PC starts from COMBINED), `stockChecklist` (the (i) on Set up), `equipmentEqWhere` (Refine's "set these bands" label; a PC also gets Save/Share of the EQ files there in Simple).
- Stored: `SetupMemory` has an optional 8th field (the equipment; older memories read as null), prefs `lastEquipment` and `companion_<EQUIPMENT>`, and `SavedTest.equipment` (`equip=` line, absent in older files; `equipmentOf(saved, targetKind)` fills it from the target). `UiState.equipment` is the open test's.
- The Tune screen's "Connect to the companion app on a TV or PC" key is gone; the widget's companion key and Tests › a home theatre still open the page directly.

### Picture keys, equipment name, Tests › Connect (2.21.0)
- Owner, 2026-11-12: "make the equipment choices big buttons with graphics, then equipment name, then test name; after home theatre, the option to connect to a companion; also a separate way to connect, from the Tests page".
- `ui/EquipmentTiles.kt`: `EquipmentTiles` (2 × 2 `selectable` keys, Role.RadioButton, accent edge on the picked one) and `drawEquipment` (line drawings on a 48 × 26 unit field, 1.5 dp strokes). The only exception to "choices are drop-downs" (CLAUDE.md).
- Set up order: tiles, "Equipment name" (becomes `SavedTest.device` through `newDevice`, ahead of the output name), "Test name (optional)" (`newTestName`: typed, else "<equipment name>, <date>", else "<target> test, <date>"), the companion drop-down, then the rest.
- Connect went through `TuneTab(openCompanion)` (until 2.22 with a new home theatre's name). `TestsTab(openCompanion)` has its own "Connect to AutoTune on a TV or PC" deck at the top.

### Set up, one question per screen (2.22.0)
- Owner, 2026-11-12, on a screenshot of 2.21's Set up: the output and Test sound "should only appear after choosing no companion, and separate in steps, equipment first then companion if applicable; in Simple make Harman the default for cars and hide it; don't overwhelm the user with a lot of options at once".
- `SetupPage` (TuningLogic: EQUIPMENT, COMPANION, NAMES, SOUND) with `setupPages(e)` (COMPANION only when `e.companion != null`), `nextSetupPage`, `previousSetupPage`, all tested. `SetupStep` keeps the page in `rememberSaveable`; its `BackHandler` (enabled past the first screen) and the header's Back go to `previousSetupPage`, else `tuneBack()`.
- `simpleTarget(e)`: HARMAN_CAR for a car, HOME_THEATER otherwise; Simple uses it, Advanced shows the target drop-down (from `equipmentTarget`) on the SOUND screen.
- The 2.21 name hand-over to the home-theatre page (`companionTheatreName`, `TheatrePage(newName)`, `theatreNew`) is gone: the names screen comes after the companion question.

### Prerequisites in order, more below (2.23.0)
- Owner, 2026-11-12, on a failed stock reading (Sound output: Phone speaker; No clipping: red; failed "Bluetooth dropped out"): "there was no Bluetooth involvement ... review all process predecessors and prerequisites; the page should indicate there is a part below".
- `sweepTiming(stalled, clipped, bluetooth, attempt)` (TuningLogic, tested) replaces the unconditional stall retry/fail in `phoneSweep`: clipped → OK (a clipped recording smears `chunkLagsMs`), not Bluetooth → WARN (`stallMessage(ms, false)` added as a warning), Bluetooth → AGAIN then FAIL (`stallMessage(ms, true)`, the old wording). `sweep_stall` logs `bluetooth`, `clipped`, `verdict`. `SweepCheck.Stall.message` in dsp is no longer used by the app.
- `UiState.outputBluetooth` / `outputPhoneSpeaker`, set in `refreshMic` and `routed` (`isBluetooth`: A2DP, SCO, hearing aid, BLE headset/speaker/broadcast; `isPhoneSpeaker`: built-in speaker or earpiece). `phoneSpeakerBlock(equipment, phonePlays, phoneSpeaker)` disables Set up's "Measure the stock system" with its reason as the pin's hint. The "Sound output" check is state 2 for the phone's own speaker.
- `analyzeStock` counts "clipped" and "timing moved" warnings as a bad reading (`suspicious`), so Refine pins "Measure the stock again".
- `MoreBelow(scrollState)` (ui/Kit.kt): a bottom fade and a round chevron key (scrolls 480 dp on) while more than 48 dp is left; in `MeasureOverlay` (its scroll state hoisted, the column in a Box) and over every tab and page in `AutoTuneScreen`.

### Refine with the companion, a new home theatre from Tune (2.24.0)
- Owner, 2026-11-12, on the home-theatre page: "I don't see refine options when connected to companion; also, when connected from the Tune menu I see previous equipment that has no relation to the companion".
- `TheatreUi.targetName`/`bassDb`/`trebleDb` and `target` (`theatreTarget(name, b, t)`, tested): per home theatre in prefs `theatreTarget_<id>` and `theatreTaste_<id>` (no change to the TheatreTests file format), loaded in `openTest`. The view model's `target` (levels, distances, EQ fit, the TV's result) is the UI's. `setTarget`/`setTaste` refit (`judgeTest`) and resend with `useEq()` while the TV's EQ is on. `eqFiles()` writes ParametricEQ/GraphicEQ to the cache for `Sharer.files` (TheatrePage now takes the Sharer).
- `RefineDeck` (TheatrePage, after `EqResult` when the EQ has bands): bands count and preamp, `BandTable(filterLines(...))` behind "Set it by hand on the home theatre", "Share the EQ files"; Advanced: target (room curves via `equipmentTargets(HOME)`), bass, treble.
- Set up: `setupPages(e, viaCompanion)` drops SOUND when the companion answer is yes; the NAMES screen's key is then "Connect to AutoTune on …", calling `openCompanion(companionTheatreName(equipName, testName))` → `AutoTuneScreen.theatreNew` → `TheatrePage(newName)` → `TheatreViewModel.startNew(name)`: a new test at once when connected, else `pendingNew` (and `startingNew`, which hides the old test's results under Connect) used by `doConnect` instead of `openTestFor`. "" names it after the TV or PC.

