# Screenshot harness (Paparazzi)

These tests render real app screens on the JVM, without a phone, using fixed sample data.
- `ShotsTest.kt` covers every screen and state, for reviewing UI changes.
- `StoreShots.kt` renders the eight Play Store screenshots at 1080 × 2400 with the app's tab bar and pinned action.

They are kept out of the main build: Paparazzi downloads layoutlib and slows down the normal unit tests.

## To run them
1. In `settings.gradle.kts`, under `pluginManagement { plugins { … } }`, add:
   `id("app.cash.paparazzi") version "1.3.5"`
2. In `app/build.gradle.kts`, under `plugins { … }`, add:
   `id("app.cash.paparazzi")`
3. Copy the two `.kt` files to `app/src/test/kotlin/dev/autotune/ui/`.
4. Run `./gradlew :app:recordPaparazziDebug --tests '*ShotsTest*'`, or `'*StoreShots.s*'` for the store set.
5. The images land in `app/src/test/snapshots/images/`.

`StoreShots` passes `useDeviceResolution = true`. Without it, Paparazzi scales every image down to fit within 1000 px.

Undo steps 1 to 3 before committing, unless you decide to keep Paparazzi in the build for good.
