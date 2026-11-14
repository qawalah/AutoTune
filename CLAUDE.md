# Working on AutoTune

Read `HANDOVER.md` first. It covers what the app is, how it's built, where data lives, and the decisions not to undo.

## Build and test
- `./gradlew :dsp:test :link:test :desktop:test :app:testDebugUnitTest :app:assembleDebug`. This needs `ANDROID_HOME` or a `local.properties` with `sdk.dir`; without either, `:app` is left out (`:dsp`, `:link` and `:desktop` build anywhere).
- The Windows app is `desktop/` (Compose for Desktop). `./gradlew :desktop:shots` renders its window to PNG without a screen; `.github/workflows/windows.yml` adds its MSI to the same version's Android release (`AutoTune-X.Y.Z-windows.msi`) and makes DevStore's `windows-vX.Y.Z` release (`autotune-X.Y.Z-win-x64.zip` with `autotune.exe` and `devstore.json`, its `.sha256`, store files), never marked latest, so DevStore on the PC installs and updates it.
- Pure logic lives in `dsp/`, `link/`, `desktop/` (`Apo.kt`, `PcEndpoint.kt`) and in `app/src/main/kotlin/dev/autotune/TuningLogic.kt`. Add or extend unit tests there with every change.

## Releasing a version
1. Bump both `APP_VERSION_NAME` and `APP_VERSION_CODE` in `gradle.properties`. That file is the only version source.
2. Add an entry at the top of `CHANGELOG.md`.
3. Update the code notes in `HANDOVER.md` when the structure changes.
   Update `FLOW.md` when a Tune step, a pinned action or a link between screens changes.
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
- **Obtainium must filter AutoTune's releases by `^Android`** (on the phone and
  on the TV). Obtainium's "Filter release titles by regex" reads the release
  *title*, which is "Android X.Y.Z" (and "Windows X.Y.Z"), not the tag; `^android-`
  matches nothing and says "Could not find a suitable release" (seen 2026-11-11). Without it Obtainium looks at the newest release only, and
  a release without an APK stops every update with "Could not find a suitable
  release" (it happened with `windows-v2.14.0`). Only `android-v…` and
  `windows-v…` releases, nothing else.
- When a change is done: commit and push to `main`. That push *is* the
  release. If the environment allows it, run `./gradlew assembleRelease` first.
- If the Release workflow fails, read its log in the Actions tab and fix it.
- `HANDOVER.md` (if present) holds history from the original Claude chat.
  Read it before making larger changes.

## UI rules (from the owner)
- Choices are drop-downs, never pills or segmented controls. Exceptions, asked by the owner (2026-11-12): Set up's equipment is four big picture keys (`EquipmentTiles`), and "Is AutoTune on the TV?" is two equal picture keys in the middle of the screen with nothing pinned (`PictureKey`).
- Use line icons from `Ic` in `ui/Kit.kt`.
- Keep to clean Material 3, with minimal scrolling and taps.
- After every reading, show a short summary, then one recommended action pinned above the tabs (`PinAction`), then the other choices, then Details. The graph is always shown.
- The look is a Braun device by Dieter Rams; the default is Gradient edge (`Look.GRADIENT`), the others are kept (Settings › Look); see `DESIGN.md`. Red is only for what is live or acted on.
- Use plain, short wording. Inter is the only typeface (`AtSans`; `AtFigures` is the same family): readings large and light, labels small and lowercase; figures are tabular everywhere.
- Results open with the display (`RunSheet` + `ReadingCells` in `ui/Kit.kt`) and one folded "notes" line; the stock is drawn dashed in charts. Don't repeat target names on lists or titles; show test names through `shortTestName`.
- Tuning is five steps (Set up, Stock, Refine, Verify, Result); keep it that short. Never put a screen between a decision and its measurement: the key that decides starts the measuring (`tuneSetup`, `tuneMeasure`, `tuneMeasureStock`).
- Five tabs: Tests, EQ, Tune (raised in the centre), Live, Settings. A test's results and Export are pages over the current tab (`Page` in `ui/AutoTuneScreen.kt`): Back returns where you were. Keep new detail screens as pages, not tabs.
- Simple / Advanced is app-wide and Simple is the default. Advanced adds controls and sections (`Adv { }`), never paragraphs. Simple shows only each tab's core job (see the 1.26.0 entry in `CHANGELOG.md`); gate the rest with `isAdvanced()`.
- No explanatory text on the page, in either mode: explanations go in info bubbles (`InfoButton`, `info =` on `DropSelect`, `GroupedPick`, `Group`, `Fold`, `InfoLabel`). Warnings go through `Alerts(list)`, one line that opens the full list. A line of text under a control is a fact or a status, at most one line.
- Every result opens with the readings, the findings or alerts behind one notes icon, and the next step. No headline line under the readings (owner, 2026-11-09): its words are in the info bubble (`resultHeadline`); only a reading that looks wrong says so in a line.
- Each section is a deck (`Panel`, `Group`, `Fold`: a light plate with a hairline outline on the dark body, no shadow; never nested). Stack actions one per row with `Actions { }`. Motion uses `EaseArrive`/`EaseLeave` and `motionMs()`, so "Remove animations" is respected.
- Back (2.33): one screen back, and never throws a measurement away without asking (the measuring screen asks before dropping measured spots). In Tune, one step back (Result › Refine › Tune screen; Set up question by question); Exit is the only key that leaves a tune and never asks: the test is kept and Continue returns to it. Discarding a test is on its menu on Tests and in the results page's Manage test. Back on a tab's top screen goes to Tests; on Tests it leaves the app. Pages, sheets, the full-screen graph, menus and dialogs close first. A tab's own `BackHandler` stands down under `LocalCovered`.
- Arrows (2.33), one meaning each, through the shared components in `ui/Kit.kt`: ">" only on a row that opens a page, a sheet or another screen (`TextAction(opens = true)`, list rows); the down arrow on folds and drop-downs, turning up when open; no arrow on a key that acts at once (`TextAction` default).

## Don't break
- **Saved tests** (`TestStore` text format): keep loading every older version.
- **Built-in target order**: append new targets to `Target.BUILT_IN`, never reorder.
- **Measurements**: use the tuned EQ with the safe preamp. Flavour and preamp mode affect playback only.
- **Fitting**: fit from the stock run (`fitFromStock`), so EQs never stack.
- **AI**: AI EQ changes are clamped to the app's limits, keys stay in the Keystore, and recordings are never sent.

## Lessons learned
- **Before changing screens, flow or links between devices**, read the shared lessons:
  `https://raw.githubusercontent.com/qawalah/devstore/main/docs/lessons/LESSONS.md`
  (or `docs/lessons/LESSONS.md` in a DevStore checkout). Follow them unless this file says otherwise.
- **Capture.** When a bug, an audit or the owner's feedback shows something a rule would have prevented, add it to
  this repo's `LESSONS.md` in the same change: the rule, then what went wrong, with the date.
- **Feed back.** Mark a lesson that would help other apps with `→ DevStore`. If this session can reach
  `qawalah/devstore` (`add_repo` with push access), add it to DevStore's `docs/lessons/LESSONS.md` under the right
  heading with this app's name and date, push, and change the mark to `→ DevStore (sent <date>)`. If it cannot,
  leave the mark; a DevStore session collects it later.
