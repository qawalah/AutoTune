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
- **Looks.** Dyno sheet (the default since 1.19: chart paper, shop green, marker orange), Studio, Mixing console, Hi-fi blue meters, Analog VU and Synthwave, each with its own slider style. Light, dark and wallpaper colours are available.
- **Send diagnostics.** A zip containing a JSONL log, the last 12 recordings, the tests and the settings.

## Platform and type

A single **Android app** (phone), written in native Kotlin with Jetpack Compose (Material 3). It has no Windows parts, no server and no companion app. Gradle modules:
- `:dsp` is pure Kotlin/JVM signal processing with no Android dependency, and is unit-tested anywhere.
- `:app` is the Android application: UI, audio I/O, system EQ, storage and AI clients.

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

