# AutoTune 1.9.0 audit, rechecked against 1.9.1 / 1.9.2

Date: 2026-10-12. Every finding (A–E) and agent prompt (1–12) was checked against the source. "1.9.1" = done in the first pass, "1.9.2" = missing or partial in 1.9.1 and fixed in this pass. Tests: 47 `:dsp` and 13 app unit tests pass. Nothing was verified on a phone.

| Item | Status | Where |
|---|---|---|
| A1 / P1 Marker false-triggers on the sweep | 1.9.1 windowed structural search; **1.9.2** side peaks now ±2 ms and ≥ 50 % as specified | `Marker.find`, ExternalTest `beepLessSweep…`, `quietBeeps…` |
| A2 / P2 wrong `expectedStart` | 1.9.1 global-peak check, `peakFromMarker`, warning | `Analyzer.analyze`, SweepCheckTest |
| A3 / P6 warnings invisible after run 1 | 1.9.1 `SavedRun.warnings`, shown at top of Results | `TestDetail`, TestStore v5 |
| A4 / P9 clock drift, FDW capped at gate | 1.9.1 `driftPpm` + warning > 50 ppm; FDW up to 1 s; copy updated | `clockDriftPpm`, `fdwDb`, TuneTab |
| A5 / P9 noise read from the ringing tail | 1.9.1 per-frequency check; **1.9.2** broadband SNR too | `Analyzer.analyze` |
| A6 / P7 memory | 1.9.1 windowed marker, locate→trim→analyse, catch Throwable; **1.9.2** shared marker buffers (< 40 MB test), 4 arrays instead of 6 in the analysis | External.kt, Analyzer.kt, `run {}` |
| A7 / P11 UNPROCESSED without a check | 1.9.1 property check, VOICE_RECOGNITION → MIC fallback, warning names the source | `MeasurementRunner.buildRecorder` |
| A8 stop time vs file constants | Left: both come from `TestSignals`; the dead `SweepCheck` work is removed | — |
| A9 default signal Pink | 1.9.1 defaults to the combined file | SetupScreens |
| B1 / P3 open loop, `TuningSession` dead | 1.9.1 `fitFromStock` closed loop in `measureRun`, best-run fallback, `TuningSession` removed | `measureRun`, `nextSuggestion`, ClosedLoopTest, TuningLogicTest |
| B2 ignored EQ doubles the cut | 1.9.1 ineffective EQ detected, not stacked; "Nothing changed" mode | `fitFromStock`, `measureUnchanged` |
| B3 / P4 applied ≠ shown | 1.9.1 one `suggestedFor`; **1.9.2** applied follows the ticked (shown) run | TuningLogic.kt, `measureRun` |
| B4 merge without clamp/cap | 1.9.1 single `Export.combine` clamps and caps | Export.kt |
| B5 / P8 31-band realisation, dead Samsung branch | 1.9.1 `sampledTo` as applied, low-Q fit for system EQ, structural treble check | SystemEq.kt, `trebleCollapsed` |
| B6 / P9 combs | 1.9.1 1/3-oct cut smoothing above 500 Hz, Q ≤ 4 above 1 kHz, 3 spots default for Car | FitConfig, `startNewTest` |
| B7 / P10 sliders replace the tuned EQ | 1.9.1 delta layer, realised values, explicit convert; **1.9.2** convert moved into Advanced > Exact bands; export includes the layer | EqTab, `graphicSet`, `convertToBands` |
| B8 / P11 `Target.ALL` out of range | 1.9.1 `targetAt`, `reloadTargets` clamps | TuneViewModel |
| B9 applied from last run; spots mix states | **1.9.2** applied from focus run; spots restart when the route changes | `measureRun`, `restartSpots` |
| C1 / P5 calibration not persisted | 1.9.1 saved, restored, Remove, per-run name, mixed warning; **1.9.2** also in the report | TuneViewModel, SetupScreens, TestStore |
| C2 / P4 no discard | 1.9.1 "Discard latest run" with confirmation | TestDetail |
| C3 / P8 EQ service not sticky | 1.9.1 START_STICKY + restore; **1.9.2** restore after reboot | SystemEqService, BootReceiver |
| C4 / P6 status cleared on tab change | 1.9.1 "Failed:" kept until the next measurement | `onTabChanged` |
| C5 catch Exception only | 1.9.1 Throwable, out-of-memory message | `run {}` |
| C6 widget state / shared request code | 1.9.1 widgets read `eqWanted`; **1.9.2** request code per screen | Widgets.kt |
| C7 SPL with the phone mic | **1.9.2** no SPL number with the phone mic | LiveTab |
| D Results without warnings / no repeat / Verify uses in-app / 36 dp sliders / spots 1 / FDW copy | 1.9.1 all but Verify; **1.9.2** Verify follows the test's apply route | EqTab |
| E dead code | 1.9.1 removed; **1.9.2** last Absolute/Delta comments; `Phase.MEASURE/REVIEW` kept | — |
| E averaged IR = first spot | **1.9.2** documented | `Averaging.average` |
| E docs (CHANGELOG 1.4.0, README +3/+4 dB, HANDOVER 3.2 dB) | 1.9.1 | root .md |
| E `docs/` copies, `.kotlin/errors`, `.apk` in source | 1.9.1 `.gitignore`, log untracked; no `docs/` in the tree. The delivery zip still carries the APK beside the source on purpose | .gitignore |
| P12 test that `measureRun` uses the closed loop | 1.9.1 | TuningLogicTest |
