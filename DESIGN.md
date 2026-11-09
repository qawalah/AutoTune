---
name: AutoTune
description: A measurement-driven EQ for a car or a room, designed like a Braun device by Dieter Rams.
colors:
  # Classic (Look.BRAUN), by day: a dark body with warm white decks
  body: "#2B2B29"
  body-ink: "#ECEAE4"
  body-ink-muted: "#A3A19A"
  body-red: "#F0663A"
  deck: "#F3F2EE"
  key: "#E4E2DC"
  rule: "#C9C7C0"
  ink: "#232321"
  ink-muted: "#5E5C57"
  red: "#BF3C18"
  pilot-green: "#3D7F3A"
  warn: "#805C00"
  bad: "#B3261E"
  teak: "#B07446"
  aluminium: "#B9BAB8"
  vinyl: "#1C1C1B"
  # Classic at night: a near-black body with graphite decks
  night-body: "#121211"
  night-deck: "#232321"
  night-key: "#2E2E2B"
  night-rule: "#45453F"
  night-ink: "#ECEAE4"
  night-ink-muted: "#A3A19A"
  night-red: "#F0663A"
  night-teak: "#8E5A33"
  # White (Look.WHITE), the same by day and night
  white-body: "#FFFFFF"
  white-deck: "#F4F4F2"
  white-key: "#E8E8E5"
  white-rule: "#C4C4BF"
  white-ink: "#1B1B1A"
  white-ink-muted: "#575753"
  # Gradient edge (Look.GRADIENT): Classic's colours, the deck outline shaded top to bottom
  edge-top: "#5E5C57"
  edge-bottom: "#E4E2DC"
  night-edge-top: "#2E2E2B"
  night-edge-bottom: "#A3A19A"
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
    backgroundColor: "{colors.body-ink}"
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

Every look is a Braun look (`Look` in `ui/Theme.kt`), each with a day and a night version: **Classic** (`Look.BRAUN`, the default: dark body, light decks, teak cheeks, one red), **Graphite** (greys, dark grey accent), **Black** (black and aluminium, orange accent), **White** (a true white body with pale grey decks, always light) and **Gradient edge** (Classic with each deck outline shaded from ink grey at the top to a light tone at the bottom, `AtColors.edge`). The enum names are what the setting stores and stay as they are. The older colour looks (Dyno sheet, Studio, Mixing console, Hi-fi, Analog VU, Synthwave, Navy) and the wallpaper colours were removed in 2.8; a saved setting that names one opens Classic.

## Colours

- **Body** (`bg`): dark graphite by day (#2B2B29), near-black by night (#121211). Titles, the main key and the key row sit on it in light ink. **Deck** (`panel` of `AtColors.deck`): the plate that holds a section, warm white by day (#F3F2EE) and graphite at night (#232321) so nothing glares in a car; everything inside a deck draws with the deck's own colours (`DeckColors`). **Key** (`panelHigh`): fields, push keys and action rows. **Rule** (`line`): hairlines and key edges.
- **Accent** (`signal`, red by default, chosen in Settings: red, orange, yellow, green, blue): only what is live or acted on: the record label (Tune), the needle on the tuning scale, the pilot light of the main key, the EQ curve and EQ fader caps. If everything is red, nothing is.
- **Pilot green** (`good`): on states and improvements. **Warn** and **bad**: cautions, always beside an icon.
- **Teak** (`teak`): the cheeks down both screen edges. Never elsewhere.
- **Runs**: the stock is grey ink, drawn dashed; later runs are inks (blue, green, violet…), the same colour everywhere.
- **Palette** (Settings, Advanced): the user can set body, deck, ink, accent and hairline over any look, each a drop-down of Braun colours, the look's own, or a typed hex colour (`Palette`, `withPalette`). Text that would not read on the chosen colours falls back to an ink that does (`readableInk`, 4.5:1). Reset palette returns to the look's own.

## Typography

Inter, one family, as Braun used one grotesk. Weight carries the hierarchy, and readings are large and **light**, like a display. Every style uses tabular figures. Labels are small, lowercase and slightly tracked ("stock · from target", "tests", "suggested").

- **Display** (Light 42): the main reading. **Headline** (Medium 21–26): step titles. **Title** (Medium 14–18). **Body** (14). **Label** (Medium 11, +0.6 tracking).
- **The wordmark** is the name as a mark: **AUTOTUNE**, bold capitals, spaced (`Wordmark`), on the header, the measuring screen and the AI review.
- **The header keeps one Samsung One UI feature, and only that one**: a tall space at the top (about a quarter of the screen) holds the screen's name in large type beside the face of a Braun hi-fi component (`HeaderArt`), so the content starts lower, nearer the thumb. The name is centred on the graphic's height, so the two read as one unit. Tests is a record player (platter, record, tonearm), EQ a fader strip, Tune the round speaker grille, Live a radio tuning scale with its needle over a slot grille of level bars, Settings a row of push keys and knobs, anything else a tape deck's reels. Flat, deck greys and aluminium, the accent only on one pilot light, needle or key.
- **The top line** is the same in every form: the wordmark at the start (Back on a page), the Simple / Advanced key exactly in the middle (`CentreRow`), and nothing else. The key never moves when the header changes shape. There is no menu.
- **It folds with the scroll** (`ScreenHeader`, `HeaderFace`): as the content moves up, the graphic shrinks and glides into the right corner of the slim bar as a small version of itself (28dp high, the key's height), and the large name moves and scales into the bar's start; the wordmark makes way before the name arrives. Scrolling back to the very top grows it all back out. Everything follows the scroll offset in the draw phase. With "Remove animations" the tall parts scroll away with the content and the slim name and small graphic switch on once they have gone.
- **When it is tall**: the header's room is part of every screen's content, so the full header always comes back when the content is scrolled **all the way to the top**, and only then; part-way down the slim bar stays. A screen opens tall on a fresh open only (a cold start, or a return after 15 minutes or more in the background, `freshOpen`), every tab once. Jumping between tabs, Back to a tab, and pages open with the header folded and the content high (`compactOpenScroll`), or where they were left if that is further down.

## Layout

One column, 16dp margins, 16dp between sections. Five tabs: Tests, EQ, **Tune** (the centre wheel), Live, Settings. A test's results and Export open as pages over the current tab; Back returns there.

**Simple / Advanced.** A small push-key drop-down at the top of every screen switches the whole app. Simple (the default) shows only each tab's core job; Advanced adds controls and sections (`Adv { }`, `isAdvanced()`), never paragraphs.

**Words on the page.** Labels, readings and one-line facts only. Explanations live in info bubbles (`InfoButton`, `info =` on drop-downs, groups and folds). Cautions fold into one `Alerts` line.

**Results open with the answer.** The display (big light reading, the change from stock, the best), one short headline, every warning and finding folded into one "N notes" line, and "Next: …". Test names made by the app show as their date; target names are not repeated on lists.

**Tuning is five steps**: Set up · Stock · EQ (the stock review on top) · Verify · Result.

## Surfaces

Flat. A section is a **deck** (`Panel`, `Group`, `Fold`, `RunSheet`): a light plate on the dark body, 8dp corners, a 1dp hairline edge in the deck's rule colour (`deckPlate`; in Gradient edge a vertical gradient from dark to light), no shadow. The header graphic is drawn in the deck's colours, a light device on the dark body. Inside a deck, a nested `Panel`/`Group`/`Fold` draws flat under a hairline (`LocalInCard`). The screen header and the key row are ruled off with 1dp hairlines. Nothing has a drop shadow.

## Components

- **Main key** (`BigActionBar` via `PinAction`): one per screen, full width, graphite, the action centred in large type with "suggested" under it, a pilot light in the accent at the start. Disabled: grey key, unlit pilot. The reason shows only in Advanced or when it is disabled.
- **Push keys** (`AtNavBar`): the tabs. Rectangular keys (62×42dp) with the tab's line icon, the pressed one graphite; a lowercase label under each. The key moves down 1dp when chosen.
- **Tab labels** are title case under each key ("EQ" in capitals). **EQ on** latches the EQ key down, shaded from the rim like a pressed push key; no text, no dot.
- **Tune button**: the centre of the key row, a round push button in the accent with the Tune mark, the tuning knob. Chosen, it is pressed in (graphite, a step lower); a ring pulses round it while a measurement runs on another tab.
- **Measuring screen**: the live level large and light with a pilot light, the sweep on a radio dial (20 Hz–20 kHz, needle follows the tone), the waveform in ink, the level on a needle meter, checks as pilot lights.
- **The speaker** (`PulsingButton`, Tune home): a Braun speaker front, the T 3's perforated circle in an aluminium rim, with the round Tune key in the accent at its centre. While waiting, a soft wave runs out through the holes.
- **Display** (`RunSheet`, `ReadingCells`): the result's deck has a lettering line (test, run, date), then the readings: the main one large and light, the change from stock and the best beside it.
- **Buttons**: `PrimaryButton` a graphite key; `SecondaryButton` a key-grey key with a hairline edge; `TextAction` in `Actions { }` a full-width key-grey row with icon and chevron.
- **Drop-downs** (`DropSelect`, `PickField`, `GroupedPick`): key-grey fields with a chevron.
- **Switch** (`LatchKey`, `PowerToggle`, `SwitchRow`): a Braun latching push key, 56×34dp in a 64×48dp target. Off it stands up, key grey with a hairline edge, the pilot unlit, lettered "off". On it latches 2dp down, its face shaded from the rim like the latched EQ key in the tab row, the pilot lit in the accent, lettered "on". Every on/off setting in the app uses it; there are no Material switches.
- **EQ sliders**: faders, a slot and a square cap. On the EQ tab they sit right under the AutoTune EQ key and the EQ in use, before the response graph. When the row has more bands than fit (15), the deck's grey fades over the edge where more wait, with a small chevron, and a thin position bar runs under the row (`scrollCue`); both go when everything is in view.
- **Step header** (`StepHeader`): Back, the step title on one line, Exit; "Step n of 7 · Next: …" with the mode switch; a slim bar (done grey, current red).
- **Info bubbles**: a small (i) next to the thing it explains.

## Icons

**The mark is a tuning knob**: the round, ridged knob of a Braun radio with its pointer cut in, set towards the upper right, and a short scale of five ticks over its upper half (as on the T 3 and SK 4 dials). It is Tune's icon (`Ic.TUNE`) on the round Tune key, the Tune main keys and "Start / Continue tuning", the app icon (graphite knob with a lighter face and a **red pointer**, the one red element, graphite scale, on #E3E3E0), the themed icon and the status bar icon (white on transparent). The speaker grille on the Tune home screen keeps the word "Tune" on its key. **Settings is a gear** (`Ic.GEAR`): a round hub with its hole and eight square teeth, drawn as one outline. Knob and gear must never look alike: the knob is filled and nearly round, the gear an outline with deep square teeth.

One family (`Ic`, drawn in `drawIc` in `ui/Kit.kt`), in the language of the launcher and notification icons:
- A 24-unit grid, one stroke weight (1.8 units), readable at 20dp.
- Plain geometry from Braun controls and speakers: circles, square keys, slots, a record, a knob, a cassette, a needle meter.
- Straight lines end square with sharp corners; round ends only on round things (waves, curves, sticks).
- Filled shapes only for knobs, fader caps, pivots and pilot lights.
- Each icon says what it is: bass a slow wave, treble a tight one, each flavour its own (drum for rock, a singer's mic for pop, a square wave for electronic, a guitar for acoustic, a speech bubble, the volume ramp, a moon).
- New icons are appended to `Ic`, never reordered. The whole set renders on one sheet in light and dark (`z1_icons_light`, `z2_icons_dark` in `tools/screenshots/ShotsTest.kt`).

## Motion

Short and mechanical. Arrivals ease out (`EaseArrive`), exits are quick (`EaseLeave`). Tabs slide from the side of the key you pressed; pages rise and drop; keys press in to 0.97; the wheel turns; the needle glides. No bounce, glow or gradient for show. `motionMs()` and `animationsOn()` follow "Remove animations".

## Do's and Don'ts

### Do:
- Do keep red for what is live or acted on.
- Do put each section on a deck, and stack its actions one per row.
- Do set readings large and light, with tabular figures, and labels small and lowercase.
- Do open every result with the display and one short headline.

### Don't:
- Don't add shadows anywhere, or borders beyond the deck's hairline edge and the keys' edges.
- Don't use teak anywhere but the two edge cheeks.
- Don't put explanations on the page; use a bubble.
- Don't use pills or segmented buttons for choices.
- Don't add a second recommended action next to the main key.
