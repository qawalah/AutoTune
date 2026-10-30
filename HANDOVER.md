# AutoTune: handover

Handover of AutoTune 1.18.0 (versionCode 38) for development in https://github.com/qawalah/AutoTune with Claude Code. Written 2026-10-30. It replaces the per-version notes kept until now. Those notes are condensed in the "Code notes" appendix at the end.

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
- **Looks.** Studio, Mixing console, Hi-fi blue meters, Analog VU and Synthwave, each with its own slider style. Light, dark and wallpaper colours are available.
- **Send diagnostics.** A zip containing a JSONL log, the last 12 recordings, the tests and the settings.

## Platform and type

A single **Android app** (phone), written in native Kotlin with Jetpack Compose (Material 3). It has no Windows parts, no server and no companion app. Gradle modules:
- `:dsp` is pure Kotlin/JVM signal processing with no Android dependency, and is unit-tested anywhere.
- `:app` is the Android application: UI, audio I/O, system EQ, storage and AI clients.

## Identity

| | |
|---|---|
| applicationId / namespace | `dev.autotune` |
| versionName | `1.18.0` |
| versionCode | `38` |
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
   The APK lands in `app/build/outputs/apk/debug/AutoTune-1.18.0-debug.apk`.
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
  - `look`, `theme`, `dynamic`, `smoothing`, `fdwCycles`, `exportTree`, `aiProvider`, `aiModel_<PROVIDER>`, `calibrationName`, `btSeen_<address>`, and similar.
- SharedPreferences `ai_secret` holds the AI API keys, encrypted with AES-GCM under the Android Keystore key alias `autotune_ai_key`.
- `cacheDir` is used for export and share files and the diagnostics zip. A chosen export folder is a persisted Storage Access Framework (SAF) tree permission; otherwise files go to Downloads.
- The manifest has `allowBackup="true"`.

External services: only when the user asks for an AI review or chat.
- `https://api.anthropic.com/v1/messages` (Claude)
- `https://generativelanguage.googleapis.com/v1beta/models/<model>:generateContent` (Gemini)

What is sent: dB-from-target values at third-octave points, each run's EQ, warnings, checks, notes, the phone model and the mic name. Recordings are never sent.

The app also opens a YouTube search for pink noise in the browser.

There is no server, no open ports and no analytics.

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

- **Choices are drop-downs, never pills or segmented buttons.** The owner asked for this. Use icons, clean Material, minimal scrolling and taps.
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
- **This 1.18 line is the one to continue, not 2.0.0-m5.** 2.0's analysis core is the same as this one; its gains are engineering ones (native audio, Room, Hilt). It lacks everything from 1.10 to 1.18, and its source code is not available. Port its strengths here instead. The comparison is in the Audio Apps project as `claude/autotune-2.0-port-plan.md`.

## Other docs in this repo

- `README.md`: user-facing overview.
- `CHANGELOG.md`: every version since 0.x.
- `DESIGN.md`: visual language.
- `PRODUCT.md`: purpose, users and principles.
- `BACKLOG.md`: future work.
- `AUDIT-RECHECK.md`: the 1.9.1 audit follow-up.
- `CLAUDE.md`: short working rules for Claude Code.

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
- Maps: the process and navigation maps were published as an artifact ("AutoTune Flow Maps", last updated for 1.17.0); regenerate them when steps or links change.

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
