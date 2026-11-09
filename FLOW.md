# AutoTune flow maps (2.5.0)

How a tuning session flows and how you move around the app, drawn from the code. Update this file whenever a Tune step, a pinned action or a link between screens changes (see CLAUDE.md). Code names in brackets point at where each decision lives.

## Process map: one tuning session

```mermaid
flowchart TD
  K(["Tune key, or New test on Tests"]) --> S["1 Set up (a draft)<br/>name, target, route, output<br/>open test and EQ untouched"]
  S -->|"Back"| H(["Tune screen: Continue still there"])
  CT(["Continue: Tune screen, results, Tests ⋮"]) --> RA{"Saved place still valid?<br/>(resumeAt)"}
  RA -->|"yes"| E
  RA -->|"no: by run count"| E
  S -->|"key: Measure the stock system<br/>EQ off, new test (tuneSetup)"| M0[["Measuring screen<br/>checks, spots 1..n"]]
  M0 -->|"failed: Try again"| M0
  M0 -->|"failed: Close"| SP["Stock page<br/>check level, test sound"]
  SP -->|"Measure"| M0
  M0 -->|"failed: Ask AI why"| AI[["AI review"]]
  M0 -->|"saved"| Q{"Stock reading suspicious?<br/>(analyzeStock)"}
  Q -->|"yes: Measure the stock again"| M0
  Q -->|"no, or Use this reading anyway"| E["3 EQ<br/>stock summary, suggested EQ<br/>How do you want to apply it?<br/>(equipment first on a known bypass output)"]
  E -->|"Use the phone's EQ and measure"| M1
  E -->|"Preview in the app and measure"| M1
  E -->|"Set it on my equipment, It's set, measure"| M1[["4 Verify: measuring screen"]]
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
  KP --> D(["Done: Back to start"])
  R -.->|"Measure again, nothing changed"| M1
  R -.->|"Discard this run"| E
  R -.->|"Ask AI to review, Measure it"| E
```

Every measurement starts the moment its key is pressed. The Stock and Verify pages are only where a failed or closed measurement lands. Back follows `previousStep`; Exit remembers the step for Continue.

## After every reading

| Screen | The one key | Other choices |
|---|---|---|
| Measuring | See the result (closes itself when all checks pass), or Try again | Measure next spot, Ask AI why, Close |
| EQ, after stock | Use the phone's EQ and measure; Measure the stock again when the stock looks wrong; It's set, measure on equipment | Preview in the app, Set it on my equipment; Advanced: target, sliders, bands, files, AI |
| Result | Apply the new suggested EQ, Keep this EQ and finish, Keep the EQ of run n, Check the EQ reaches the speakers, Measure again, Set it on my equipment, or Switch on the phone's EQ and measure | The other moves, Measure again, Ask AI, All results, Discard |
| Done | Back to start | Open the EQ, See all results |
| Test results page | Continue tuning | Open the EQ, AI review |

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
  W["Widgets, notification actions,<br/>Quick Settings tile"] -->|"EqActionReceiver"| BG(("background:<br/>app stays closed"))
  SH["Shortcuts, routines,<br/>autotune://profile/name"] -->|"ApplyProfileActivity"| BG
  BT["Bluetooth device connects"] -->|"rules.txt"| BG
```

Back closes the newest page first, then returns a tab to Tests. Inside Tune, Back walks the steps instead.

Open improvements found while drawing these maps are in `BACKLOG.md` ("Flow review, 9 Nov 2026").
