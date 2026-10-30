# Working on AutoTune

Read `HANDOVER.md` first. It covers what the app is, how it's built, where data lives, and the decisions not to undo.

## Build and test
- `./gradlew :dsp:test :app:testDebugUnitTest :app:assembleDebug`. This needs `ANDROID_HOME` or a `local.properties` with `sdk.dir`; without either, only `:dsp` is included.
- Pure logic lives in `dsp/` and in `app/src/main/kotlin/dev/autotune/TuningLogic.kt`. Add or extend unit tests there with every change.

## Releasing a version
1. Bump both `APP_VERSION_NAME` and `APP_VERSION_CODE` in `gradle.properties`. That file is the only version source.
2. Add an entry at the top of `CHANGELOG.md`.
3. Update the code notes in `HANDOVER.md` when the structure changes.
4. Commit, then tag `vX.Y.Z`.

## DevStore release rules

This app is released through DevStore. Pushing to `main` builds it, and a new
`versionCode` publishes a GitHub Release that installs itself on the user's
phone through Obtainium.

- **Never change `applicationId`.** The phone would treat it as a different app.
- **Bump `versionCode` (+1) and `versionName`** in `gradle.properties` for
  every change the user should receive. No bump means no release (the build
  still runs as a check).
- **Don't add a release `signingConfig`** and don't commit keystores. CI signs
  every release with the shared DevStore key.
- Keep `gradlew`, `gradlew.bat` and `gradle/wrapper/*` committed.
- When a change is done: commit and push to `main`. That push *is* the
  release. If the environment allows it, run `./gradlew assembleRelease` first.
- If the Release workflow fails, read its log in the Actions tab and fix it.
- `HANDOVER.md` (if present) holds history from the original Claude chat.
  Read it before making larger changes.

## UI rules (from the owner)
- Choices are drop-downs, never pills or segmented controls.
- Use line icons from `Ic` in `ui/Kit.kt`.
- Keep to clean Material 3, with minimal scrolling and taps.
- After every reading, show a short summary, then one recommended action pinned above the tabs (`PinAction`), then the other choices, then Details. The graph is always shown.
- Use plain, short wording. Use Barlow (`AtSans`) for text and Barlow Condensed (`AtFigures`) for readings and headings; figures are tabular everywhere.
- Results open with a run sheet (`RunSheet` + `ReadingCells` in `ui/Kit.kt`); charts sit on chart paper with the stock dashed. See `DESIGN.md`.
- A test's results, Live and Export are pages over the current tab (`Page` in `ui/AutoTuneScreen.kt`): Back returns where you were. Keep new detail screens as pages, not tabs.

## Don't break
- **Saved tests** (`TestStore` text format): keep loading every older version.
- **Built-in target order**: append new targets to `Target.BUILT_IN`, never reorder.
- **Measurements**: use the tuned EQ with the safe preamp. Flavour and preamp mode affect playback only.
- **Fitting**: fit from the stock run (`fitFromStock`), so EQs never stack.
- **AI**: AI EQ changes are clamped to the app's limits, keys stay in the Keystore, and recordings are never sent.
