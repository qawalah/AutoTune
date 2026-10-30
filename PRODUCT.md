# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack
Existing: native Kotlin, Jetpack Compose (Material 3), Gradle. Two modules: `:dsp` (pure Kotlin signal processing) and `:app`.

## Users
One owner-builder (Abu) tuning aftermarket and stock car audio, sometimes a home theatre. Usually sitting in the car with the phone mounted or in hand, USB-C measurement mic (UMIK-1) plugged in, often at night or in strong sun, wanting to finish a tune in one sitting.

## Product Purpose
Measure speakers with a log-sine-sweep (or pink noise), fit a parametric EQ automatically, compare runs against a stock reference, and apply the EQ either on the user's own equipment (export files) or directly on the phone's audio (AutoTune as the EQ). Success: a measured, repeatable improvement the user can see and hear, with every run saved.

## Positioning
A measurement-driven EQ that lives in the phone: measure, correct, apply and re-measure in one app, with the stock system kept as a permanent reference.

## Operating Context
Car cabin, engine off, mic at the driver's head position; or a room with a TV/PC feeding a receiver. Samsung's own EQ (Adapt Sound, SoundAlive, Dolby) cannot be controlled by apps and must be off while measuring.

## Capabilities and Constraints
- Named tests; stock run is the reference; unlimited further runs, each saved with what was applied and a note.
- Two routes: sound system connected to the phone (phone plays sweep) or not connected (other device plays our file or pink noise).
- System EQ via DynamicsProcessing (experimental; phone-dependent), manual band editor.
- Export: Equalizer APO/Poweramp parametric text, Wavelet GraphicEQ, report, CSV.
- Live RTA and sound level; report card; L/R, sub/crossover, mic-position averaging.
- No verification on a real device or mic has happened yet.

## Product Principles
- The EQ is a main function, not the last step: reachable any time, tied to the open test's results.
- Every measurement is saved. Nothing the user measured is ever silently lost.
- Say what is measured, what is predicted and what is estimated.
- Glanceable in a car: big targets, few words, no typing mid-task.

## Accessibility & Inclusion
48dp touch targets, text follows system font size, contrast at least 4.5:1, dark and light both first-class.
