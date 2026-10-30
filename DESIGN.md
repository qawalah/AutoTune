---
name: AutoTune
description: A measurement-driven EQ for a car or a room, where every result reads as a dyno run sheet.
colors:
  chart-paper: "#ECEFE8"
  sheet: "#F9FAF6"
  field: "#E3E8DF"
  grid-rule: "#C6D2C3"
  ink: "#141B16"
  ink-muted: "#515E55"
  marker-orange: "#C23E0A"
  on-marker: "#FFFFFF"
  shop-green: "#173A2B"
  on-shop-green: "#F1F6F0"
  on-shop-green-muted: "#A9C4B3"
  good: "#17784A"
  warn: "#855600"
  bad: "#B0123F"
  pen-stock: "#4A524D"
  pen-blue: "#1C5FC4"
  pen-violet: "#7A3DC2"
  pen-teal: "#0B8794"
  pen-magenta: "#B0198F"
  graphite: "#0F1311"
  graphite-sheet: "#161C18"
  graphite-field: "#1F2722"
  graphite-rule: "#2B3730"
  night-ink: "#E6ECE6"
  night-ink-muted: "#97A79C"
  night-marker-orange: "#FF8A4C"
  night-shop-green: "#1B4532"
typography:
  display:
    fontFamily: "Barlow Condensed"
    fontSize: "54sp"
    fontWeight: 500
    lineHeight: "58sp"
    letterSpacing: "-0.5sp"
  headline:
    fontFamily: "Barlow Condensed"
    fontSize: "29sp"
    fontWeight: 600
    lineHeight: "34sp"
    letterSpacing: "-0.2sp"
  reading:
    fontFamily: "Barlow Condensed"
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "30sp"
  title:
    fontFamily: "Barlow"
    fontSize: "17sp"
    fontWeight: 600
    lineHeight: "23sp"
  body:
    fontFamily: "Barlow"
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: "22sp"
  label:
    fontFamily: "Barlow"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "18sp"
rounded:
  xs: "3dp"
  sm: "5dp"
  md: "6dp"
  lg: "8dp"
  dialog: "16dp"
spacing:
  hair: "1dp"
  xs: "4dp"
  sm: "8dp"
  md: "16dp"
  section: "16dp"
components:
  button-primary:
    backgroundColor: "{colors.marker-orange}"
    textColor: "{colors.on-marker}"
    rounded: "{rounded.md}"
    height: "52dp"
  pinned-action:
    backgroundColor: "{colors.marker-orange}"
    textColor: "{colors.on-marker}"
    typography: "{typography.reading}"
    rounded: "{rounded.md}"
    height: "60dp"
  run-sheet-header:
    backgroundColor: "{colors.shop-green}"
    textColor: "{colors.on-shop-green}"
    typography: "{typography.title}"
    padding: "8dp 14dp"
  navigation-band:
    backgroundColor: "{colors.shop-green}"
    textColor: "{colors.on-shop-green}"
    height: "80dp"
  drop-select:
    backgroundColor: "{colors.field}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sm}"
    height: "52dp"
  sheet:
    backgroundColor: "{colors.sheet}"
    rounded: "{rounded.md}"
    padding: "16dp"
---

# Design System: AutoTune

## Overview

**North star: the dyno run sheet.** A tuning shop pins a printout on the wall after every pass: a ruled header naming the car and the run, the figures in condensed type, the baseline dashed under the tuned curve on chart paper. AutoTune is that printout for sound. The stock reading is the permanent baseline, every run is a sheet, and the owner should be able to read how far the system sits from the target, how far it has come, and the one next move at a glance, in a truck cab at night or in hard sun.

The world lends type, palette, density and one signature move (the run sheet). It never replaces Material 3 navigation or controls: a bottom navigation bar for three tabs, drop-downs for choices, standard buttons, switches and dialogs.

Dyno sheet is the default Look (`Look.DYNO` in `ui/Theme.kt`). Studio, Mixing console, Hi-fi blue meters, Analog VU and Synthwave remain selectable; they inherit every rule here except the palette.

## Colors

Restrained ground, committed shell. Chart paper by day (`chart-paper`, sheets in `sheet`), graphite by night (`graphite`, `graphite-sheet`). Dark or light follows the system; both are first-class.

### Primary
- **Marker orange** (`marker-orange`, night `night-marker-orange`): what the user acts on. The EQ curve, the pinned action, primary buttons, text actions, the Tune disc, the current step of the progress bar. Nothing decorative.

### Secondary
- **Shop green** (`shop-green`, night `night-shop-green`): the shell. The navigation band, the run-sheet header strip, the rule under every screen title, finished steps of the progress bar (by day). Text on it uses `on-shop-green` / `on-shop-green-muted`.

### Tertiary
- **Plotter pens** (`pen-*`): measured runs, the same colour in every chart, list and table (`runColor(idx)`). Index 0 is the stock in grey ink (`pen-stock`), drawn dashed whenever other curves share the chart.

### Neutral
- `ink` / `night-ink` for text, `ink-muted` / `night-ink-muted` for secondary text (≥4.5:1 on every ground), `grid-rule` / `graphite-rule` for 1dp rules and the chart grid, `field` / `graphite-field` for drop-down fields.
- State colours `good`, `warn`, `bad` only where they carry meaning, always beside an icon or a word.

### Named Rules
- **One job for orange.** If it is orange, it is the user's EQ or something they can press.
- **Stock is the dashed baseline.** Only the stock run is dashed. The target is a thin faint solid line; a prediction is dotted.

## Typography

Barlow (licence plates and highway signs) for interface text; Barlow Condensed for readings, headings and the pinned action. Every style sets tabular figures (`fontFeatureSettings = "tnum"`), so readings align in columns and never jitter while they update. Sizes are in sp and follow the system font size.

### Hierarchy
- **Headline** (Condensed SemiBold 29/34): screen titles. Step titles use 24/30.
- **Reading** (Condensed SemiBold 24/30): figures in run-sheet cells and the Tests list; the pinned action at 21/26.
- **Title** (Barlow SemiBold 17–20): groups, fold titles, sheet header names.
- **Body** (Barlow 15/22) and **small** (14/20) for prose and muted lines.
- **Label** (Barlow Medium 13–15) for field labels and keys.

### Named Rules
- **Figures are condensed.** A dB, Hz or score reading is set in `AtFigures`; prose around it stays in `AtSans`.
- **No eyebrows.** Nothing sits above a heading; context goes under it.

## Layout

One column, 16dp side margins, 16dp between blocks, content scrolling under a pinned action bar. Three tabs (Tests, Tune, EQ). A test's results, Live and Export open as **pages** over the current tab, with a back arrow in place of the menu and the navigation band hidden; Back always returns to where you were, and each tab keeps its own scroll position. Settings and the AI review are full-screen overlays with a close button.

Every screen title sits over a sheet rule: a 3dp shop-green band and a 1dp hairline.

## Elevation & Depth

Flat. Depth comes from rules, not shadows: sheets have a 1dp `grid-rule` border, the pinned action bar is ruled off from the page with a 1dp line, folds and groups are separated by hairlines. Material dialogs and menus keep their platform elevation.

### Named Rules
- **Rules, not shadows.** Never add a drop shadow to a sheet, a button or a row.

## Shapes

Nearly square, like paper: 3dp (bars, swatches), 5dp (fields), 6dp (sheets, buttons, the pinned action), 8dp large, 16dp dialogs. Circles only for the Tune disc, the measure ring and status dots.

## Components

### Buttons
- **Pinned action** (`BigActionBar` via `PinAction`): one per screen, full width, 60dp, marker orange, Condensed 21sp with a line icon. It is the screen's one recommended move.
- **Primary** (`PrimaryButton`): marker orange, 52dp. **Secondary** (`SecondaryButton`): outlined in muted ink, 48dp. **Text action** (`TextAction`): orange text with a line icon; red for destructive.

### Cards / Containers
- **Sheet** (`Panel`): `sheet` fill, 1dp rule border, 6dp corners, 16dp padding. Used for what was measured and what is set; everything else stays flat.
- No nested sheets, no same-size icon cards.

### Inputs / Fields
- **Drop-down** (`DropSelect`, `PickField`, `GroupedPick`): the current value in a `field` box with a chevron, options in a menu with a check on the selected one. Choices are always drop-downs, never pills or segmented buttons.

### Navigation
- **Navigation band** (`AtNavBar`): shop green full width, three items centred, selected item in `on-shop-green` over a lighter green indicator.
- **Step header** (`StepHeader`): one row of Back, the step title with "Step n of 7 · Next: …" under it, and Exit, over a slim bar of 7 segments (done in shop green by day and muted ink by night, current in marker orange).
- **Screen title** (`ScreenTitle`): headline, context line, menu or back arrow, sheet rule.

### Run sheet (signature)
`RunSheet` + `ReadingCells` in `ui/Kit.kt`, used by `ResultSummary`. A shop-green header strip names the test and the run (on the results page, which is already titled with the test, it names the run), with a status icon at its right. Below it, ruled cells hold the readings in condensed figures with their names under them: stock, latest (green when better), best so far or the change from stock. The target, warnings and verdict follow in body type. The chart-paper response graph (`ResponseChart`) comes next: minor grid lines every 5 dB and at 2..9 of each decade, heavier rules at the decades and every 10 dB, the stock dashed, the latest run heaviest, the EQ in marker orange, and a key that draws each curve's own stroke.

### Tune disc
`PulsingButton`: a marker-orange disc with two rings pulsing out while it waits. The only idle motion in the app; it stops when animations are off.

## Do's and Don'ts

### Do:
- Do open every result with a run sheet, then the graph, then the one pinned action.
- Do set every reading in Barlow Condensed with tabular figures.
- Do keep orange for the user's EQ and what they can press.
- Do open detail screens as pages over the current tab, so Back returns there.
- Do keep the grid inside charts, where it measures something.

### Don't:
- Don't draw anything but the stock run dashed.
- Don't put a label or eyebrow above a heading.
- Don't use shadows, gradients or glows on sheets, buttons or rows.
- Don't use pills or segmented buttons for choices.
- Don't add a second recommended action next to the pinned one.
