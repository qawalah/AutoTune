# AutoTune Braun UI — Pass 5 review

## Change

The Live screen previously displayed a fabricated set of bars matching the target curve whenever the RTA was stopped. Although intended as an idle visual, it looked like measured data. This is a UX accuracy issue in an audio-measurement application.

The spectrum panel now distinguishes:

- **Standby:** no spectrum is drawn; the user is prompted to start measurement.
- **Starting/listening:** no spectrum is drawn until the first real data arrives.
- **Measuring:** the actual RTA bars are displayed against the selected target.

## Regression boundaries

No changes to RTA calculations, target curves, microphone capture, measurement state, audio processing, EQ, data storage, or navigation.

## Remaining release checks

1. Build `:app:assembleDebug` with Gradle 8.14.3 available.
2. Install on a physical Android device and confirm standby → listening → live bars → stop.
3. Confirm microphone permission denial and recovery.
4. Test font scaling, TalkBack labels, and narrow-screen layouts.
5. Regression-test Tune, EQ application, verification, export, and saved results.

This archive is source-only; no APK or device-test result is claimed.
