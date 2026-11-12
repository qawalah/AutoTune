# Dave's walkthrough of AutoTune (screens 000-130)

## 1. What I think this app does
It listens to my truck speakers with my phone's mic, makes a list of how the sound is off from a "target" sound, and then makes an EQ (a set of sound settings) that fixes it. I then turn that on and the truck sounds better. I think it's for the truck, which is good. I still don't know what the numbers mean, but the score going from 63 to 84 out of 100 looked like it got better.

## 2. Where I got confused
1. 001-004 (intro): "fits an EQ", "sweep", "target curve", "close the gap". I had no idea what these were. Pictures helped, words did not. Severity 2.
2. 003 "Verify it": "1.3 dB from target". What is a dB? Severity 2.
3. 005 / 006 Tests screen: "No tests yet", "New test starts one", and "Connect to AutoTune on a TV or PC" all sounded like I had to do something I don't understand. I went straight for the red Tune button. Severity 1.
4. 006 Tune screen: "USB microphone: Phone microphone" when I have no USB mic. Severity 2.
5. 008/009 Set up "What are you tuning?" - Car was already highlighted. Fine. Severity 0.
6. 011 "Equipment name" - I didn't know what "equipment" meant here. Severity 1.
7. 012 "Play the test sound" - "This phone plays it (Bluetooth, aux or USB)" was right for me, but "Plays through: Same as music" and a separate "Test sound" row both confused me. Severity 2.
8. 012/013 "Measure the stock system" - I did not know what "stock" meant. Severity 2.
9. 015 "Spot 1 of 3 done. Move the mic a hand's width to the next spot" - I had to guess what a hand's width meant and whether I had to move the phone myself at my head. Severity 2.
10. 014 "-21 dbfs · mic level". No idea. Severity 2.
11. 020 Refine: "63/100 stock · from target" and "The EQ should bring it to ≈1.2 dB". I didn't know if 63 was good or bad. The warning triangle at the top right had no words next to it. Severity 2.
12. 020 chart: stock, target, suggested EQ lines. Too many lines, I ignored it. Severity 2.
13. 026 Result: "1.3 dB, 84/100 run 1 from target" and the red "Suggested EQ after Run 1 (not applied yet)" line. Severity 2.
14. 028 "Continue Ford truck, 12 Nov 20:11" button looked faded and was partly hidden behind the down-arrow. My tap sent me back to the result page instead of onward. Severity 2.
15. 029 Warning triangle: "Averaged 3 mic positions." Fine once I read it, but I had to guess the triangle was clickable. Severity 1.
16. 032 "Another round with the new suggested EQ" right after the app said "Keep this EQ and finish". Which one should I press? I picked another round. Severity 2.
17. 038 "Tuned: 1.3 dB, 84/100 tuned" and "Continue" faded again. Severity 2.
18. 039/040 Tests list says the result is "2.9 -> 1.4, 1.5 dB closer", but the Tuned screen said 1.3. Different numbers for the same truck. Severity 2.
19. 043 Runs info bubble: "The EQ and the export follow the latest ticked run... the score rates the system". Run 2 has 87 and run 1 has 84, but the app kept run 1 as "best". I don't understand which one is in the car. Severity 3.
20. 045 EQ page: "EQ in use: Ford truck, 1.4 dB from target, 12 Nov 20:13" - so Run 2 is in use? Earlier it said best was Run 1 at 1.3. Severity 3.
21. 045 "Flavour: As tuned" - I didn't know this word. Severity 2.
22. 048 flavour list "Rock", "Pop", "Electronic / hip-hop" etc. are fine, but "Exactly the tuned sound" for "As tuned" didn't tell me what tuned meant. Severity 1.
23. 051 "About this EQ: If your EQ app has no preamp setting, lower its output level by 3.3 dB, or boosts can clip." I don't have an EQ app. Severity 2.
24. 052-054 Lock icon: I couldn't tell if the lock locks the sliders. Severity 2.
25. 054 I dragged the 31.5 slider by mistake while trying to scroll. It jumped to +11.5 dB and the warning said "The EQ boosts by up to 11.5 dB". This frightened me. Severity 3.
26. 056 Same thing again: the 1k slider jumped to +12 dB while I scrolled. Severity 3.
27. 066 Live: "teal is within 2 dB of target tick, yellow within 4 dB, red further away". The bars were green, not teal. Severity 2.
28. 063 "-32.9 ... No dB SPL with the phone mic" - no idea what dB SPL is. Severity 2.
29. 065 "Play from the phone: Off / Pink noise / Sweep". I didn't know if pink noise was dangerous or loud. Severity 2.
30. 068/069 After Stop, the box still said "Pink noise" and I had to set it back to Off myself. Severity 2.
31. 071 Settings: "No calibration loaded / Import calibration". Calibration: no idea. Severity 2.
32. 072 Mode info bubble was helpful. Severity 0.
33. 084 "Where the key comes from": paste a Claude API key from a website, "requests are charged to that key's account". I was worried about being charged, so I skipped it. Severity 3 (it is a money worry).
34. 087 Diagnostics: "Keep the last 12 recordings", "Send diagnostics", "Clear". The info bubble was clear that it stays on the phone. Severity 1.
35. 097 "Replay the tutorial" just replayed the intro with no way to tell it was the same tutorial. Severity 1.
36. 105/106 Three dots menu: "Open results", "Continue tuning", "Rename", "Delete" is fine. Severity 0.
37. 108 New test just starts a new set up with no warning that it will not change the existing test. Severity 1.
38. 111 "Home t..." title is cut off. "Find the TV or PC, looking on this wi-fi, nothing found yet". I have no TV or PC. Severity 1.
39. 115 Preamp: Safe / Balanced / Off / Manual. "Lowers everything by the biggest boost: never clips, but boosts sound quieter". Hard to choose. Severity 2.
40. 117 "Save as a profile" - profile vs. test vs. EQ, I didn't know which one I was saving. Severity 2.
41. 128/129 Export: "Poweramp, Equalizer APO", "Wavelet", "ParametricEQ.txt", "GraphicEQ.txt". I have no idea what these are. Severity 3.
42. 113 EQ page in advanced: "9 bands" in one line, "10 bands" in the drop-down. Severity 2.
43. 109 Tune page now says "EQ currently in use: Ford truck, Run 2" under a "Tuned 1.3 (run 1)" card. Severity 2.

## 3. Words I didn't understand
EQ, dB, dBFS, dB SPL, sweep, pink noise, target curve, target, stock, spot, flavour, preamp, boost, band, octave, calibration, profile, Harman (in target list), Poweramp, Equalizer APO, Wavelet, ParametricEQ.txt, GraphicEQ.txt, Analysis window, Mic spots per reading, Band resolution, Palette, Body, Deck, Ink, Hairline, Accent, Diagnostics, API key, Model, Claude Sonnet, "teal", "tick", "run", "best", "suggested", "score", "Remove my slider changes", "Predicted with EQ", "Leq", "Peak", "AutoTune 2.26.1", "Inter", "SIL Open Font License".

## 4. Taps that did nothing / wrong turns / places I went back
- 028: tapped the faded "Continue ..." button, went back to the result page instead of moving on.
- 054 / 056: scrolling on the EQ page moved sliders by accident (+11.5 dB and +12.2 dB). Recovered with "Remove my slider changes".
- 068/069: Stop did not reset the "Play from the phone" choice; I had to set it to Off myself.
- 032: picked "Another round" after the app suggested "Keep this EQ and finish". Took about 5 more minutes.
- 040-043: went into the test and found Runs info. Went back out.
- 097: Replay the tutorial, skipped straight away.
- 106: Rename opened and I cancelled.
- 108: New test opened a set up, I pressed Exit.
- 111: Connect to TV or PC, nothing to do, backed out.
- 117: Save as a profile, cancelled.

## 5. Moments I would have quit
- After 026/028: I didn't know if I was finished. The big button said "Keep this EQ and finish", then I got a screen with "Continue ..." faded. I nearly quit here.
- 054 / 056: sliders jumped to +11 / +12 dB with a warning about the EQ boosting. I thought I had broken my speakers. This would have made me quit.
- 084: asking for an API key with "charged to that key's account" made me stop on the AI part.
- 128: export file names meant nothing to me.

## 6. Things I never found or never understood, and features I used
Used:
- Intro (4 screens + Get started).
- Tests tab: New test, the one test row, three-dot menu (Open results, Continue tuning, Rename [cancelled], Delete [not pressed]), Connect to AutoTune on a TV or PC (opened, backed out).
- Test detail: result, graph, Runs list with ticks, info bubble, Open the EQ, AI review (not opened), Manage test (not opened), Continue tuning.
- Tune: Set up (what are you tuning, name, play the test sound, measure), stock reading with 3 spots, refine, EQ run 1 (3 spots), result, another round (run 2, 3 spots), keep and finish, tuned card.
- EQ tab: AutoTune EQ on/off switch (tested both ways), EQ in use, Flavour (opened list, chose More bass), Preamp (opened list, kept Safe), Adjust bands sliders (moved by accident, restored), padlock, Flat (not pressed), Save as a profile (cancelled), frequency response graph, top-right flavour shortcut.
- Live tab: Start, level meter, bar chart, Play from the phone (Pink noise tried, then Off), Stop.
- Settings tab: Mode (info, switched to Advanced), Microphone (info), Import calibration (not pressed), EQ files (Export EQ files opened; For list opened; Save to Downloads and Share not pressed), Defaults for new tests (info, Target curve list opened, kept Car), Band resolution, AI assistant (info only, no key), Diagnostics (info only; Save, Send, Clear not pressed), TV screen (not used), Appearance (Look changed to Black and back to Gradient edge; Light or dark seen), Palette (seen, not changed), Target curves (seen), About (seen), Replay the tutorial (opened and skipped).
Never found or never understood: what "Stock" and "Target" really are; the difference between a test, a run, a profile and an EQ file; Flat; Analysis window; Mic spots; Calibration; Share; AI review; Manage test options; the faded Continue button; what "Predicted with EQ" means; what "Remove my slider changes" leaves behind; Sweep as a playback choice.

## 7. What would have helped me
1. Say the plain thing first. "Tune your speakers" is good, then "We play a noise, we listen, we fix the sound". Avoid "fits an EQ", "target curve", "stock" in the first screens, or put one plain line under each.
2. Use one number everywhere. The "best" run was 84 on one screen, 87 on another, and the EQ in use said Run 2. Pick one, and say "This is the one in your car" in plain words.
3. Make the next step obvious. After "Keep this EQ and finish", the faded "Continue" button should not appear. Either finish with a clear "Done" or send me back to Tests with a message.
4. Lock the sliders on the EQ page, or stop a scroll from moving them. Two accidental +12 dB moves frightened me. A warning should ask "Really boost 1k by 12 dB?".
5. Explain words in one line on the screen where they appear, not in a bubble I have to open: "dB = how loud", "stock = the sound before tuning", "target = the sound AutoTune aims for".
6. Say what happens on Stop, for example "Stops the noise and puts Play from the phone back to Off".
7. Put the API key and the money part in plain words at the start: "You do not need this. It is optional. Costs money if you use it."
8. Replace the export file names with a question: "Which app do you use to play EQ? Pick one: Poweramp / Wavelet / Not sure."
9. Put "Save as a profile" with a line: "Save this EQ so you can use it again."
10. Tell me once, after tuning, what it means: "Your truck now sounds closer to the target. Your score went from 63 to 84 out of 100."
