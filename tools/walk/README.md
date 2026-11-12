# Walk-through: a first-time user in a pretend car

Runs the real AutoTune screens on the JVM (Robolectric, no phone, no emulator) with a pretend F-150 in place of the audio
hardware, and lets an agent "hold the phone" from a shell. Used to watch where someone who knows nothing gets confused.

- `CarSim` (app/src/test/.../walk) is the speaker, cabin and mic: boomy 62 Hz, harsh 3 kHz, 120 ms Bluetooth delay, road noise,
  a phone mic weak in the bass. It plays the part of the head unit too, for the "not connected" routes, and of the truck's
  stereo for Live. Whatever the app switches on as its system EQ reaches the mic.
- `MeasurementRunner.simulator` (audio/AudioIo.kt) is the only change in the app: null in the real build.
- `WalkDriver` takes commands from a folder and answers with a 540-wide screenshot. It also writes `hidden/log.jsonl` (every
  command, the texts on screen, whether the tap changed anything) for the person reading the results, not for the agent.

```
ANDROID_HOME=/path/to/sdk                       # or local.properties
ROBO_DIR=/dir/with/android-all-instrumented-15-robolectric-12650502-i7.jar   # optional: offline Robolectric
tools/walk/start.sh /tmp/walk                   # waits for "ready"
tools/walk/walk.sh 0 "why" shot                 # CONFUSION(0-3) "why" COMMAND
tools/walk/walk.sh 0 "why" tap 270 1100
tools/walk/walk.sh 0 "done" quit
```
Commands: `shot`, `tap X Y`, `long X Y`, `swipe X1 Y1 X2 Y2`, `type TEXT`, `back`, `wait SECONDS`, `open`, `quit`.
The driver is excluded from `./gradlew test`; it runs only when `-Dwalk.dir` is given.
