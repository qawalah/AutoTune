# F-150 diagnostics, 11 Nov 2026: what happened and what changed (2.19.2)

The owner sent `autotune-diagnostics-20261111-2134.zip` (AutoTune 2.19.0, Galaxy S23 Ultra SM-S918B on Android 17,
USB mic iMM-6C with `cmm30892.cal`, output "Bluetooth: Ford F-150", target "Car, Harman-style (+8 dB bass)").
They asked about the 17:44 tune, then about faint clicks, then about bass that "sounds robotic" at the start of
the sweep. All of it was fixed in 2.19.2 (PR #26). To check a new zip the same way, run
`tools/diagnostics/analyse_recordings.py` (see the end of this file).

## Short version
1. **The car's Bluetooth paused playback for 15-25 ms partway through 5 of the 12 sweeps.** The app did not notice,
   so Runs 1, 2 and 5 were saved with fake holes of 15-20 dB, and Refine built Run 2's EQ from Run 1's broken reading.
   The final EQ (Run 4, 3.34) was clean, and so were its repeats (Runs 6 and 7: 3.66 and 3.71).
2. **One pause fell between the start beeps.** The beep detector wanted exact spacing, so it gave up. The fallback
   search then took the USB mic's switch-on click for the sound and failed with "barely heard", wrongly blaming
   AutoTune EQ. That sweep was clean.
3. **Clicks on the EQ** came from the code. Each change jumped straight to the new value with no fade, every player
   start (every song) tore the effect down and rebuilt it, and players that announce a session got a second copy.
4. **"Robotic" bass:** the truck hardly plays 20-30 Hz (only overtones and rattle are left), and Android's EQ ran at
   its default 10 ms frame, which gives 94 Hz bins. So everything below about 140 Hz was two bins: the bass was
   smeared, and the 45 Hz band's gain ran from 47 to 140 Hz.

## The 17:44 session (test later renamed "F-150")

| Run | Score (dB from target) | Recording | What the recording shows |
|---|---|---|---|
| Stock | 5.12 | `174440` | clean |
| Run 1 | 6.09 | `174508` | sound ~20 ms late from ~4 s into the sweep (~100 Hz) |
| Run 2 | 9.46 | `174530` | ~15-20 ms late from ~7 s (~2.5 kHz) |
| Run 3 (repeat of Run 2) | 5.13 | `174610` | ~22 ms late from ~3 s (~65 Hz) |
| Run 4 | 3.34 | `174640` | clean; this EQ was kept |
| failed | – | `174705` | beep 3 19 ms late; the sweep itself clean |
| Run 5 | 8.41 | `174741` | ~15 ms late from ~7 s |
| Run 6, Run 7 | 3.66, 3.71 | `174823`, `174855` | clean (Run 4's EQ again) |

The 16:33 run (`163346`) was late by ~24 ms over the WHOLE sweep (the pause came before it), which is harmless: the
analyser searches ±200 ms around the beeps. Run 5's EQ was never judged fairly, since its only reading was broken.

### How it was found
- **Same sound, different score.** Runs 2 and 3 used the same EQ. Their audio along the sweep track matches within
  ~1 dB at every frequency, yet they scored 9.46 and 5.13, so the analysis was at fault, not the speakers.
- **The bad readings picked the direct sound 15-21 ms late:** 2615-2621 ms in the cut window instead of 2600
  (`Analyzer` replayed on the WAVs). Their impulse responses hold a weaker copy at 0 ms and the main peak at
  +15..24 ms.
- **The delay steps up partway through.** Cross-correlating 0.5 s pieces of the sweep with the recording shows a
  delay of ~0 that jumps and then stays up (the "chunk lags" in the tool). A pause, not clock drift: drift would
  ramp. `driftPpm` was null in exactly the bad runs, a symptom only.
- **Why it ruins the reading:** `Analyzer` takes the strongest IR sample as the direct sound, starts the window
  2 ms before it and keeps 80 ms. When the later part wins, everything played before the pause falls outside the
  window, which leaves holes at those frequencies (Run 2: 315 Hz-2.5 kHz down 15-20 dB).
- **The failed run:** beeps at 1.896, 2.136 and 2.395 s, so the third came 19 ms late. `Marker.find` allowed
  ±3 ms per beep. The full-recording search then locked onto the mic click at 113 ms (SNR 10 dB → fatal), and
  `checkUsable` added "AutoTune EQ ... may be muting audio".
- **Mic click:** every recording starts with two loud transients at 0 and ~0.08 s (about −10 dB RMS, ~150 clipped
  samples). That is the USB mic switching on; nothing plays before 1.8 s. It is why `recPeakDbfs` read 0 dBFS
  every time.
- **Faint ticks:** groups of 3-4 ticks 80 ms apart in about half the recordings. They don't match anything the app
  plays, so their source is unknown (electrical?). They don't matter: Run 6 (about 20 ticks) and Run 7 (none) agree
  within 1 dB.

## Clicks on the EQ (code, 2.19.0)
- `SystemEq.create` turned DynamicsProcessing on at full gains, and `off()` simply released it, so the sound jumped
  by the preamp (−3.3 dB) and by bass cuts of up to −9 dB.
- `SystemEqService.playbackCallback` rebuilt the effect 600 ms after ANY player start: release, then create. Music
  played a moment without the EQ, then with it again.
- `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` put a second DynamicsProcessing on each announcing player's session.
  The output-mix effect is required (`apply` fails without it) and is what the sweep goes through, so those players
  got the EQ twice.

## "Robotic" bass at the start of the sweep
- **The truck:** at 20-26 Hz the tone arrives ~35 dB below 50 Hz and its overtones are louder than the tone (THD over
  100%), with or without the EQ. EQs that lift the deep bass (Run 4: +2.5 dB at 30 Hz), plus the sweep's lift
  (+3.2 dB to offset the preamp), push the speakers harder (Run 1 at 26 Hz: THD 297%, against 12-22% without EQ).
- **Android's EQ** (`DynamicsProcessing`, variant FAVOR_FREQUENCY_RESOLUTION) is an STFT in the AOSP source:
  - `DynamicsProcessing.java`: `CONFIG_PREFERRED_FRAME_DURATION_MS = 10.0f` is the default, and the app never set it.
  - `EffectDynamicsProcessing.cpp` `DP_configureVariant`: block = frame duration × rate, rounded up to a power of
    two, with 50% overlap.
  - `DPFrequency.cpp`: one gain per FFT bin (`mPreEqFactorVector`). `computeBinStartStop` gives bin k to the FIRST
    band whose `round(cutoff × block / rate)` reaches k; a band narrower than a bin gets none.
  - At 10 ms and 48 kHz: 512-sample blocks, 94 Hz bins. Below ~140 Hz the only bins are 0 Hz (taken by the 20 Hz band)
    and 94 Hz (taken by the 44.9 Hz band). The measurements agree: Run 4 against stock was cut 3.4-5.1 dB at
    63-125 Hz where the app's model expected 0.9-3.6 dB.
  - With the EQ on, the recordings show energy between the bass overtones (~15 dB above the cabin's background),
    and the bass tone wobbles more (8-12% against 6-7.5%). Consistent with coarse block processing; not proven to be
    the only cause.
- **Bins against bands:** a 1/6-octave band is ~11.6% of its frequency wide (5 Hz at 40 Hz, 12 Hz at 100 Hz,
  116 Hz at 1 kHz). A band only works when it is wider than a bin. 94 Hz bins collapse the 61-band layout below
  ~800 Hz (31 bands: below ~400 Hz). 4096 samples (~12 Hz bins) works down to ~100 Hz (61 bands) or ~50 Hz
  (31 bands). 8192 would work down to ~50 Hz for 61 bands, at ~170 ms of delay. The owner chose 4096.

An interactive version of the bins-against-bands picture is `wiki/bins-and-bands.html`.

## Questions answered on the way
- **Is a pause needed before the sweep for the mic and the EQ?** No, there's one already. The mic starts at 0 s,
  playback at 0.3 s with 1.5 s of silence, the beeps (0.6 s) at ~1.8 s, then 1 s of silence, then the sweep at
  ~3.4 s. The click is over by 0.1 s, the EQ is set ~1 s before recording starts (and fades in 0.15 s since 2.19.2),
  and the car's amp wakes during the silence. The Bluetooth pauses came 3-7 s INTO the sweep, so a longer pause
  would not have helped.
- **Must there be three beeps?** Keep them. Bluetooth delay varies (0-0.3 s here), so the sound must be found in the
  recording, and one beep is easily faked by a click, a voice, or the sweep passing 1.5-3 kHz. Three equal beeps
  with silent gaps are hard to mistake. The test files, the TV and the PC use the same marker. They cost 0.6 s.

## What 2.19.2 changed (see `HANDOVER.md`, "Bluetooth pauses and EQ clicks")
- `SweepCheck.chunkLagsMs` / `stall` / `stallIn`: a pause or skip that every later chunk keeps (≥ 6 ms) makes
  `phoneSweep` measure again (`SWEEP_ATTEMPTS` = 2), then fail with the stall's message; logged as `sweep_stall`.
  Clean runs score ~0-2 ms, stalled ones 9.5-20.
- `Marker.find`: a beep may be up to 60 ms late (`LATE_SEC`); the start is then given where beep 1 would have been.
- `SweepCheck.quietStart`: the first 0.5 s of a phone recording is silenced before analysis and before the logged
  levels (the kept WAV is raw).
- `checkUsable` adds the "AutoTune EQ may be muting" hint only when the sound was not heard.
- `SystemEq`: changes fade over 10 × 15 ms (`EqRamp`), rebuilds crossfade, there are no per-session effects, and a
  rebuild on a player start happens only after an output change or when the effect stopped (`rebuildOnPlayback`).
- `DpBins` + `setPreferredFrameDuration(80)` give 4096-sample blocks; band gains are set per bin; `applied` is what
  the bins really do (`EqChain.peakingAt`). Cost: ~85 ms more output delay with AutoTune EQ on.

- Diagnostics: `chunkLagsMs`, `stallMs`, `beepLateMs` and `peakOffsetMs` on every phone sweep, `system_eq_rebuild` with its reason, and a "System EQ blocks" line in `info.txt` (the frame length the running effect reports confirms 80 ms took).

## Not verified on the phone (as of 2.19.2; see the second zip below for what was confirmed)
- Video lip-sync with the extra ~85 ms.
- Whether switching DynamicsProcessing on still ticks (the effect may add latency when it starts; a fade can't help).
- That music in the owner's player sounds like Run 4 now that the double EQ is gone (it may sound weaker than before).
- How long the stall check takes on the phone (~0.1 s per sweep on a desktop JVM).
- `TvEq` (the TV/tablet companion) still uses the default 10 ms frame.

## The second zip: 11 Nov 23:xx on 2.19.2 (`autotune-diagnostics-20261112-0000.zip`)
Two tunes on the F-150 over Bluetooth, "F150 HD" (stock 5.00 → Runs 3.11, 3.16, 3.02, 3.01) and "F150 Taste"
(stock 5.00 → 2.89, 3.01; with bass +12 / treble −6 taste: 3.24, 2.80). No outlier runs this time.

**What worked**
- `info.txt`: "System EQ blocks: 4096 samples at 48000 Hz (asked for 80.0 ms frames; the effect reports 80.0 ms)".
- **Bass EQ arrives:** Run minus stock against the wanted EQ, 40-250 Hz, third octaves. The share of the wanted shape
  that arrived (least-squares slope) was 16-32% on the five clean 512-block runs and 38-77% on the eight 4096-block runs
  (six of them 58-77%); the mean error fell from 1.5-1.8 dB to 1.0-1.7 dB. A single spot and the 80 ms gate limit how
  close this can get.
- **Smear gone:** the energy between the overtones at 80 Hz was 31-36 dB below the tone on the old EQ runs (about 15 dB
  over the cabin's background) and is 40-46 dB below on the new ones, level with the no-EQ runs.
- **Pause check:** at 23:43:20 a stock sweep arrived 14-17 ms late from 3 s on (`peakOffsetMs` 16.6); logged as
  `sweep_stall` and not saved. Smaller slips of 2-6 ms (whole sweep early or late, `beepLateMs` 2-5) are common on
  this Bluetooth link and harmless.

**What went wrong (fixed in 2.19.4)**
- **45 EQ rebuilds in an hour.** One second after every `system_eq_on` came `system_eq_rebuild` "output changed",
  often followed by "first player after an output change". Cause: Android answers `registerAudioDeviceCallback`
  with every device, and the service registers each time it starts. With 4096-sample blocks each rebuild is a
  dropout (the new effect outputs zeros until its first block is ready) and a skip (the old effect's buffer is lost).
- **The automatic re-measure was stopped** 3 s in (23:43:23): the screen went back to "Opening the microphone" and
  played the beeps again without saying why.
- **"Microphone read failed (code -3)"** at 23:48:58, with a device change logged 12 s earlier and again 4 s later:
  most likely the USB mic's plug (its headphone output counts as an output device). The same error is in the first
  zip at 16:28:56.

**Also seen**
- The first stock reading of "F150 HD" (23:22:59, 8.4) was about 5 dB quieter than the repeat (5.0), with about 6 dB
  more bass relative to the mids and a 9 dB hole at 1-1.25 kHz: another volume (the head unit likely lifts the bass
  at low volume) and mic position, not a pause. Repeated stock readings at one volume still differ by ±4 dB in single
  third octaves between 1 and 2 kHz, which is what one mic spot gives.

## Rerunning the checks on a new zip
Unzip it into its own empty folder, then:

```
python3 -I tools/diagnostics/analyse_recordings.py <folder>
```

It needs numpy. Per recording it prints:
- the run and its score;
- where the beeps were found and how late beeps 2 and 3 came;
- the mic click level;
- the chunk lags and the stall verdict (the same rule as `SweepCheck.stall`);
- the bass tone level and THD at the start of the sweep, and the bass wobble;
- from 2.19.2 logs, what the app itself logged: `peakOffsetMs`, `beepLateMs` and `stallMs`.

The recordings exist only when "keep recordings" was on. They are the owner's car audio, so they are not committed.
