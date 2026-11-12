# AutoTune flow maps (2.24.0)

How a tuning session flows and how you move around the app, drawn from the code. Update this file whenever a Tune step, a pinned action or a link between screens changes (see CLAUDE.md). Code names in brackets point at where each decision lives.

## Process map: one tuning session

```mermaid
flowchart TD
  K(["Tune key, or New test on Tests"]) --> EQP{"1 Set up, screen 1: What are you tuning?<br/>four picture keys: Car, Home theatre or soundbar,<br/>PC speakers, Other speakers<br/>(a key picks and moves on)"}
  EQP -->|"Home theatre or PC speakers"| CQ{"screen 2: Is AutoTune on the TV / the PC?<br/>(companionQuestion)"}
  CQ -->|"Yes, AutoTune is on it"| NC["screen: Name it<br/>equipment name, test name"]
  NC -->|"Connect to AutoTune on the TV or PC<br/>(a new home theatre with these names, made once it connects)"| HTP(["Home theatre page<br/>(see the navigation map)"])
  CQ -->|"No: Use this phone only"| NM
  EQP -->|"Car or Other speakers"| NM["screen: Name it<br/>equipment name, test name"]
  NM -->|"Next"| S["screen: Play the test sound (a draft)<br/>route named for the equipment, output, Test sound<br/>target only in Advanced (Simple: Harman car / Home theatre)<br/>warns when the phone EQ may not reach it<br/>key off while it plays on the phone's own speaker<br/>open test and EQ untouched"]
  S -->|"Back"| H(["Tune screen: Continue still there"])
  MEM["remembered per output: equipment, route<br/>and the last equipment, companion installed"] -.-> EQP
  CT(["Continue: Tune screen, results, Tests ⋮"]) --> RA{"Saved place still valid?<br/>(resumeAt)"}
  RA -->|"yes"| E
  RA -->|"no: by run count"| E
  S -->|"key: Measure the stock system<br/>EQ off, new test (tuneSetup)"| M0[["Measuring screen<br/>checks, spots 1..n"]]
  M0 -->|"failed: Try again, Check the level<br/>(causes in order: output, beeps, cut off, clipping,<br/>background, then timing: Bluetooth only; sweepTiming)"| M0
  M0 -->|"closed, stopped or failed: Close<br/>(returnAfterMeasuring)"| S
  M0 -->|"failed: Ask AI why"| AI[["AI review"]]
  M1 -->|"closed, stopped or failed: back where it started"| E
  M0 -->|"saved"| Q{"Stock reading suspicious?<br/>(analyzeStock)"}
  Q -->|"yes (far off, too weak, clipped, timing moved): Measure the stock again"| M0
  Q -->|"no, or Use this reading anyway"| E["3 EQ<br/>stock summary, suggested EQ<br/>How do you want to apply it?<br/>(equipment first on a known bypass output)"]
  E -->|"Use the phone's EQ and measure"| M1
  E -->|"Preview in the app and measure"| M1
  E -->|"Set it on my equipment (named for it; a PC saves the EQ files), It's set, measure"| M1[["4 Verify: measuring screen"]]
  M1 -->|"saved"| R["5 Result<br/>summary, graph, one move"]
  R --> N{"recommendNext"}
  N -->|"REFINE: Apply the new suggested EQ"| RG{"Way already chosen on a phone route?<br/>(refineGoesStraightToMeasure)"}
  RG -->|"yes: apply and measure"| M1
  RG -->|"no"| E
  N -->|"CHECK_EQ, phone route"| EC["EQ check: EQ off, EQ on"]
  EC --> V{"Verdict (judgeEqCheck)"}
  V -->|"arrives: REPEAT, Measure again"| M1
  V -->|"nothing: SET_ON_EQUIPMENT<br/>output remembered"| E
  V -->|"too flat to tell: usual move"| N
  N -->|"CHECK_EQ, other device: Apply again"| E
  N -->|"KEEP, preview tune: MEASURE_ON_PHONE_EQ<br/>(needsPhoneEqRun)"| M1
  N -->|"KEEP_LATEST or KEEP_BEST"| KP["Keep: save profile,<br/>phone EQ on unless equipment"]
  KP --> D(["Tune screen shows the tuned reading<br/>key: Use this EQ whenever the car connects<br/>(offerAutoSwitch), else Open the EQ"])
  R -.->|"Measure again, nothing changed"| M1
  R -.->|"Discard this run"| E
  R -.->|"Ask AI to review, Measure it"| E
```

Every measurement starts the moment its key is pressed. Stock and Verify are measuring phases with no page: a closed, stopped or failed measurement returns to the step it started from. Tune is three screens (Set up, EQ, Result) plus the measuring screen. Back follows `previousStep`; Exit remembers the step for Continue.

## After every reading

| Screen | The one key | Other choices |
|---|---|---|
| Set up, one question per screen | What are you tuning: Next (a picture key also moves on); Is AutoTune on it: the answer given last time (Connect to AutoTune on the TV/PC, or Use this phone only); Name it: Next; Play the test sound: Measure the stock system | The other answer on the AutoTune screen; route, output, Test sound; Advanced: target, measurement options, tools |
| Measuring | See the result (closes itself when all checks pass), or Try again | Measure next spot, Check the level, Ask AI why, Close |
| EQ, after stock | Use the phone's EQ and measure; Measure the stock again when the stock looks wrong; It's set, measure on equipment | Preview in the app, Set it on my equipment; Advanced: target, sliders, bands, files, AI |
| Result | Apply the new suggested EQ, Keep this EQ and finish, Keep the EQ of run n, Check the EQ reaches the speakers, Measure again, Set it on my equipment, or Switch on the phone's EQ and measure | The other moves, Measure again, Ask AI, All results, Discard |
| Tune screen after Keep | Use this EQ whenever <device> connects (phone EQ, Bluetooth route), else Open the EQ | Tune (new test), Continue |
| Home theatre page | Connect; then nothing is pinned between tests: Set the levels, Test the distances and Test the EQ each sit in their own card; Next speaker / Measure seat n / Stop while a test runs | The big EQ on/off key (sends the last EQ or switches it off), Check the EQ reaches the speakers, Disconnect; after an EQ test a Refine card (the bands to set by hand, Share the EQ files; Advanced: target, bass and treble, kept per home theatre); the home theatre line opens Home theatres (pick, New, Rename, Delete); Advanced: EQ test seats. A TV or PC from 2.14: speaker by speaker |
| Test results page | Continue tuning, with the move it leads to on its second line ("next: …") | Open the EQ, AI review (at the bottom, above Details) |

## Navigation map

```mermaid
flowchart LR
  subgraph Bar["Tab bar"]
    T["Tests"]
    EQ["EQ"]
    TU(("Tune"))
    L["Live"]
    ST["Settings"]
  end
  T -->|"tap a test"| RP["Results page<br/>over the current tab"]
  T -->|"⋮ Continue tuning"| TU
  T -->|"New test"| TU
  RP -->|"Continue tuning"| TU
  RP -->|"Open the EQ"| EQ
  RP -->|"AI review"| AI[["AI review sheet"]]
  EQ -->|"Verify this EQ: measures at once"| TU
  EQ -->|"Check the EQ reaches the speakers"| MV[["Measuring screen"]]
  EQ -->|"Advanced: Export EQ files"| X["Export page<br/>over the current tab"]
  ST -->|"Export EQ files"| X
  TU --> MV
  TU -->|"Ask AI"| AI
  AI -->|"Measure it"| TU
  L -->|"Advanced: Open EQ"| EQ
  TU -->|"Set up: Home theatre or PC speakers,<br/>AutoTune installed: Connect"| HT["Home theatre page<br/>over the current tab"]
  HT -->|"Set the levels: pink noise per speaker,<br/>live meter, next after 3 s level"| TV(("AutoTune on the TV"))
  HT -->|"Test the distances: one sweep per speaker"| TV
  HT -->|"Test the EQ: every speaker together, 1-5 seats,<br/>then Use the EQ (one for every speaker)"| TV
  T -->|"a home theatre, or Connect to AutoTune on a TV or PC"| HT
  TV -.->|"TV or PC from 2.14: speaker by speaker"| HT
  HT -->|"Test every speaker: the PC plays left and right;<br/>Use the EQ on the PC: Equalizer APO"| PC(("AutoTune on Windows"))
  ST -->|"Advanced: Use this device as the TV"| TVS["TV screen on this phone or tablet<br/>(TvActivity; Back returns;<br/>Pretend: off, 2.1, 3.1, 5.1, 7.1)"]
  HT -.->|"from another phone"| TVS
  W["Widgets, notification actions,<br/>Quick Settings tile"] -->|"EqActionReceiver"| BG(("background:<br/>app stays closed"))
  SH["Shortcuts, routines,<br/>autotune://profile/name"] -->|"ApplyProfileActivity"| BG
  BT["Bluetooth device connects"] -->|"rules.txt"| BG
  FS(["First start"]) -->|"once (tutorialSeen)"| TUT["Tutorial over every screen<br/>5 cards: Skip, Next, Get started"]
  TUT -->|"Skip, Get started, Back on the first card"| T
  ST -->|"About: Replay the tutorial"| TUT
```

Back closes the newest page first, then returns a tab to Tests. Inside Tune, Back walks the steps instead.

Open improvements found while drawing these maps are in `BACKLOG.md` ("Flow review, 9 Nov 2026").
