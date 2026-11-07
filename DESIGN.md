---
name: AutoTune
description: A measurement-driven EQ for a car or a room, designed like a Braun device by Dieter Rams.
colors:
  body: "#F3F2EE"
  deck: "#E2E0DA"
  key: "#EDECE7"
  rule: "#C8C6BF"
  ink: "#232321"
  ink-muted: "#66645E"
  red: "#C8401A"
  pilot-green: "#3D7F3A"
  warn: "#8A6200"
  bad: "#B3261E"
  teak: "#B07446"
  aluminium: "#B9BAB8"
  vinyl: "#1C1C1B"
  night-body: "#1A1A19"
  night-deck: "#262624"
  night-key: "#30302D"
  night-rule: "#3C3C38"
  night-ink: "#ECEAE4"
  night-ink-muted: "#A3A19A"
  night-red: "#F0663A"
  night-teak: "#8E5A33"
typography:
  display:
    fontFamily: "Inter"
    fontSize: "42sp"
    fontWeight: 300
    lineHeight: "48sp"
  headline:
    fontFamily: "Inter"
    fontSize: "21sp"
    fontWeight: 500
    lineHeight: "28sp"
  title:
    fontFamily: "Inter"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "22sp"
  body:
    fontFamily: "Inter"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "21sp"
  label:
    fontFamily: "Inter"
    fontSize: "11sp"
    fontWeight: 500
    lineHeight: "15sp"
    letterSpacing: "0.6sp"
rounded:
  key: "3dp"
  field: "4dp"
  deck: "6dp"
  dialog: "12dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "16dp"
components:
  main-key:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.body}"
    pilot: "{colors.red}"
    rounded: "{rounded.field}"
    height: "58dp"
  push-key:
    backgroundColor: "{colors.key}"
    pressed: "{colors.ink}"
    rounded: "{rounded.key}"
    size: "50x28dp"
  deck:
    backgroundColor: "{colors.deck}"
    rounded: "{rounded.deck}"
    padding: "14dp"
---

# Design System: AutoTune

## Overview

**North star: a Braun device by Dieter Rams.** Less, but better. The app is one instrument: a white body between two teak cheeks (SK 4, audio 1), a grey deck for what matters, a row of push keys, an aluminium tuning wheel, and one red for the things that are live: the record label, the needle, the pilot light and the EQ. Everything else is white, grey and graphite. The owner should read how far the system is from the target, and the one next move, at a glance, in a truck cab at night or in hard sun.

The world lends material, type and a few signature objects. It never replaces Material 3 behaviour: drop-downs for choices, standard switches and dialogs, five tabs.

Braun is the default look (`Look.BRAUN` in `ui/Theme.kt`), with two siblings: Braun graphite (greys, dark grey accent) and Braun black (black and aluminium, orange accent). Dyno sheet, Studio, Mixing console, Hi-fi blue meters, Analog VU, Synthwave and Navy remain selectable as colour variants of the same shapes.

## Colours

- **Body** (`bg`): warm white by day, Braun black by night. **Deck** (`panel`): the grey plate that holds a section. **Key** (`panelHigh`): fields, push keys and action rows. **Rule** (`line`): hairlines and key edges.
- **Accent** (`signal`, red by default, chosen in Settings: red, orange, yellow, green, blue): only what is live or acted on: the record label (Tune), the needle on the tuning scale, the pilot light of the main key, the EQ curve and EQ fader caps. If everything is red, nothing is.
- **Pilot green** (`good`): on states and improvements. **Warn** and **bad**: cautions, always beside an icon.
- **Teak** (`teak`): the cheeks down both screen edges. Never elsewhere.
- **Runs**: the stock is grey ink, drawn dashed; later runs are inks (blue, green, violet…), the same colour everywhere.

## Typography

Inter, one family, as Braun used one grotesk. Weight carries the hierarchy, and readings are large and **light**, like a display. Every style uses tabular figures. Labels are small, lowercase and slightly tracked ("stock · from target", "tests", "suggested").

- **Display** (Light 42): the main reading. **Headline** (Medium 21–26): step titles. **Title** (Medium 14–18). **Body** (14). **Label** (Medium 11, +0.6 tracking).
- **The header follows Samsung One UI**: a tall space at the top (about a quarter of the screen) holds the screen's name in large type and a Bauhaus composition of its own (a list for Tests, faders for EQ, a speaker for Tune, a spectrum for Live, circle-square-triangle elsewhere), so the content starts lower, nearer the thumb. Its top line carries the small "autotune" wordmark (Back on a page), the Simple / Advanced key and the menu. Scrolled away, a slim bar with the name takes over (`CollapsedHeader`).

## Layout

One column, 16dp margins, 16dp between sections. Five tabs: Tests, EQ, **Tune** (the centre wheel), Live, Settings. A test's results and Export open as pages over the current tab; Back returns there.

**Simple / Advanced.** A small push-key drop-down at the top of every screen switches the whole app. Simple (the default) shows only each tab's core job; Advanced adds controls and sections (`Adv { }`, `isAdvanced()`), never paragraphs.

**Words on the page.** Labels, readings and one-line facts only. Explanations live in info bubbles (`InfoButton`, `info =` on drop-downs, groups and folds). Cautions fold into one `Alerts` line.

**Results open with the answer.** The display (big light reading, the change from stock, the best), one short headline, every warning and finding folded into one "N notes" line, and "Next: …". Test names made by the app show as their date; target names are not repeated on lists.

**Tuning is five steps**: Set up · Stock · EQ (the stock review on top) · Verify · Result.

## Surfaces

Flat. A section is a **deck** (`Panel`, `Group`, `Fold`): the grey plate, 6dp corners, no border, no shadow. Inside a deck, a nested `Panel`/`Group`/`Fold` draws flat under a hairline (`LocalInCard`). The screen header and the key row are ruled off with 1dp hairlines. Nothing has a drop shadow.

## Components

- **Main key** (`BigActionBar` via `PinAction`): one per screen, full width, graphite, the action centred in large type with "suggested" under it, a pilot light in the accent at the start. Disabled: grey key, unlit pilot. The reason shows only in Advanced or when it is disabled.
- **Push keys** (`AtNavBar`): the tabs. Rectangular keys (62×42dp) with the tab's line icon, the pressed one graphite; a lowercase label under each. The key moves down 1dp when chosen.
- **Tab labels** are title case under each key ("EQ" in capitals). **EQ on** latches the EQ key down, shaded from the rim like a pressed push key; no text, no dot.
- **Tune button**: the centre of the key row, a round push button in the accent with the Tune mark (Bauhaus: a plain circle). Chosen, it is pressed in (graphite, a step lower); a ring pulses round it while a measurement runs on another tab.
- **Measuring screen**: the live level large and light with a pilot light, the sweep on a radio dial (20 Hz–20 kHz, needle follows the tone), the waveform in ink, the level on a needle meter, checks as pilot lights.
- **The speaker** (`PulsingButton`, Tune home): a Braun speaker front, the T 3's perforated circle in an aluminium rim, with the round Tune key in the accent at its centre. While waiting, a soft wave runs out through the holes.
- **Display** (`RunSheet`, `ReadingCells`): the result's deck has a lettering line (test, run, date), then the readings: the main one large and light, the change from stock and the best beside it.
- **Buttons**: `PrimaryButton` a graphite key; `SecondaryButton` a key-grey key with a hairline edge; `TextAction` in `Actions { }` a full-width key-grey row with icon and chevron.
- **Drop-downs** (`DropSelect`, `PickField`, `GroupedPick`): key-grey fields with a chevron.
- **Switch** (`PowerToggle`): a slide switch; on, the track is graphite and the knob is the green pilot.
- **EQ sliders**: faders (`SliderStyle.FADER`), a slot and a square cap.
- **Step header** (`StepHeader`): Back, the step title on one line, Exit; "Step n of 7 · Next: …" with the mode switch; a slim bar (done grey, current red).
- **Info bubbles**: a small (i) next to the thing it explains.

## Motion

Short and mechanical. Arrivals ease out (`EaseArrive`), exits are quick (`EaseLeave`). Tabs slide from the side of the key you pressed; pages rise and drop; keys press in to 0.97; the wheel turns; the needle glides. No bounce, glow or gradient for show. `motionMs()` and `animationsOn()` follow "Remove animations".

## Do's and Don'ts

### Do:
- Do keep red for what is live or acted on.
- Do put each section on a deck, and stack its actions one per row.
- Do set readings large and light, with tabular figures, and labels small and lowercase.
- Do open every result with the display and one short headline.

### Don't:
- Don't add borders or shadows to decks, buttons or rows.
- Don't use teak anywhere but the two edge cheeks.
- Don't put explanations on the page; use a bubble.
- Don't use pills or segmented buttons for choices.
- Don't add a second recommended action next to the main key.
