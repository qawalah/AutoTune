# AutoTune Braun / Dieter Rams UI pass

Baseline: `android-v1.29.0+50` source.

## Implemented in this pass

- Tests home now has a restrained instrument-style overview when systems exist: current system, system count, and total measurement readings. The existing test rows remain the primary navigation surface.
- The New test floating action key is now graphite with a signal-red indicator, rather than a full red button. This follows the Braun rule that red marks the active/action indicator, while the main key remains dark.
- Existing Braun palette, typography, fader style, navigation push-keys, and edge cheeks are retained. They were already implemented in the supplied source and are not duplicated or replaced.

## Preserved

No changes were made to DSP, measurement, EQ application, persistence, export, calibration, permissions, or ViewModel logic.

## Build status

A debug build was attempted with `./gradlew :app:assembleDebug --no-daemon`, but the Gradle wrapper could not download Gradle 8.14.3 because this environment has no network access (`UnknownHostException: services.gradle.org`). No APK is claimed as built or tested. Build locally with:

```bash
./gradlew :app:assembleDebug
```

The generated APK is named `AutoTune-<version>-debug.apk` under `app/build/outputs/apk/debug/`.

## Second pass — EQ, Live and Settings hierarchy

- EQ: moved the response chart directly below power/profile selection and made it the primary instrument readout; renamed the manual band section to “Adjust bands”; renamed the primary verification action to “Verify this EQ”. No EQ calculations or application behaviour changed.
- Live: promoted the spectrum to the first instrument panel after Start/Stop; retained level, clipping and SPL information immediately below it.
- Settings: introduced quiet section dividers for Equipment, Tuning defaults, Playback & processing, and Data & support; clarified the Appearance section purpose. Existing settings and callbacks remain intact.
- Validation: source-level edits reviewed; Gradle build remains blocked because Gradle 8.14.3 is not cached and network access to services.gradle.org is unavailable. No APK is claimed as built or tested.

## Third pass — results and test-list feedback

- Test rows now label readings explicitly as dB from target and show whether the latest result is closer to, further from, or about the same as stock. The comparison uses the existing stock/latest RMS values and the same 0.3 dB distinction used by `resultHeadline`; no new score or heuristic was introduced.
- The results page response plot now has a clear instrument-style heading, a concise curve description, a slightly larger plotting area, and a short legend-use hint. Existing curve visibility, target toggle and full-screen graph behaviour are unchanged.
- No measurement, DSP, EQ, persistence or export code was changed.

## Fourth pass — measurement workflow clarity

- Reordered the stock/verification measurement screen so its title, context, microphone/output checks and status are presented before the primary Measure action. Previously the primary action appeared above the step title, before users had read the measurement context.
- Added explicit action hints: the stock reading is the comparison reference; verification must retain the stock mic position and playback volume.
- Promoted the unchanged-reference-condition reminder into a quiet Braun-style panel on verification, rather than leaving it as a standalone note competing with other content.
- No measurement sequencing, state transitions, audio routing, DSP, persistence or ViewModel logic changed.

## Fifth pass — truthful Live idle state and final consistency review

- Live no longer renders a synthetic spectrum shaped exactly like the target while the analyzer is stopped. That placeholder could be mistaken for a real microphone measurement and falsely imply a good response.
- The spectrum area now has three explicit states: live bars after the first reading, a waiting state while the analyzer starts, and a quiet standby state before measurement. Actual RTA rendering and measurement logic are unchanged.
- This is a presentation-only correction; no DSP, microphone, audio routing, persistence, or ViewModel code changed.
- Validation: inspected the modified Compose branch and verified the project archive contents. Full Android compilation/device validation remains unavailable because the required Gradle 8.14.3 distribution is not cached and network access is unavailable.
