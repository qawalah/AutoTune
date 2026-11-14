# Lessons learned in this app

What this app taught us, so it is not repeated here or in the next app. The shared list for every app is in
DevStore: https://raw.githubusercontent.com/qawalah/devstore/main/docs/lessons/LESSONS.md

Add a lesson in the same change that fixes the mistake: the rule first, then what went wrong, with the date.
Mark a lesson that would help other apps with `→ DevStore`; once it has been added to DevStore's list, change the
mark to `→ DevStore (sent <date>)`. See DevStore `docs/lessons/README.md`.

From `AUDIT-2026-11-13.md` (all three parts) and the 2.36.0 work that answered it.

## Back, Exit and leaving

- **Back goes one screen back.** *Back from the Connect page dropped the user at the first Set up question and lost what they had typed.* (2026-11-13) → DevStore (sent 2026-11-14)
- **Back never throws work away without asking.** *Back during the second of three mic spots silently cancelled the reading and the spot already measured.* (2026-11-14) → DevStore (sent 2026-11-14)
- **The top screen owns Back.** *Back during the countdown between spots moved Set up's question screen hidden under the measuring screen, because Set up's handler was registered later.* (2026-11-14) → DevStore (sent 2026-11-14)
- **One key leaves a flow; Back steps back.** *Back and Exit on Refine and Result had become the same "Leave this tune?" question.* (2026-11-14) → DevStore (sent 2026-11-14)
- **Leaving is the same whichever way you leave; better, save as you go and never ask.** *Back and Exit asked "Leave this tune?", the tabs left without a word.* (2026-11-14) → DevStore (sent 2026-11-14)
- **After cancel, return to where the key was pressed.** *A cancelled reading landed on "What are you tuning?", not on the sound screen.* (2026-11-14) → DevStore (sent 2026-11-14)

## What the app creates, it cleans up

- **Deleting a thing deletes what the app made for it, and switches it off if in use.** *Deleting a finished test left its kept EQ saved, playing, and called "Your own bands", with the test's curves still on the EQ graph.* (2026-11-14) → DevStore (sent 2026-11-14)
- **Tie things together by id, never by name.** *Renaming a test orphaned its kept EQ; finishing again made a second one.* (2026-11-14) → DevStore (sent 2026-11-14)
- **After a delete, nothing of it stays, here or on the other device.** *A deleted home theatre's EQ stayed on the TV and in the widget, and the page then opened another device's home theatre.* (2026-11-14) → DevStore (sent 2026-11-14)
- **Moving from name links to id links: tie the old records once by name and write the id; never match by name again.** *Matching by name at every load would hand an old kept EQ to a new test that happens to take the deleted test's name.* (2026-11-14) → DevStore (sent 2026-11-14)

## Names and words

- **One thing, one name, everywhere.** *One EQ was "Car", "Car · tuned (Run 1)" and "Your own bands".* (2026-11-14) → DevStore (sent 2026-11-14)
- **Never name something by its date or a setting.** *A test with no names was "13 Nov 20:26" everywhere.* (2026-11-13) → DevStore (sent 2026-11-14)
- **One word per idea.** *"Profile" and "saved EQ" for the same thing; "Home theatre" for a PC's desk speakers.* (2026-11-13) → DevStore (sent 2026-11-14)
- **Say a thing once.** *The EQ's target line was in the reading, the headline and a sentence under it.* (2026-11-13) → DevStore (sent 2026-11-14)

## Keys and signs

- **One sign, one meaning, set in the shared components.** *">" ended rows that opened pages, unfolded in place, and measured at once.* (2026-11-14) → DevStore (sent 2026-11-14)
- **No repeated keys.** *Refine listed "Use the phone's EQ and measure" and also pinned it.* (2026-11-14) → DevStore (sent 2026-11-14)
- **A key that needs permission does its job once allowed.** *Measure asked for the mic, then did nothing.* (2026-11-13) → DevStore (sent 2026-11-14)
- **Remove what does nothing useful.** *"Try it: with EQ / without" repeated the EQ's own switch.* (2026-11-14) → DevStore (sent 2026-11-14)
- **No decks inside decks.** *"More" folded Exact bands, Try it and Automatic switching inside a fold.* (2026-11-14) → DevStore (sent 2026-11-14)
- **A destructive key offers Undo or asks.** *Flat wiped the EQ in one press once unlocked.* (2026-11-14) → DevStore (sent 2026-11-14)

## Flow

- **Count the presses to the first result; offer "as last time" with Change.** *A car used before still took Tune, Car, Measure.* (2026-11-14) → DevStore (sent 2026-11-14)
- **Do not ask what is known.** *"Is AutoTune on the TV?" was asked every time after the TV was remembered.* (2026-11-14) → DevStore (sent 2026-11-14)
- **Every dead end offers a way on.** *"Nothing found yet" on Connect, with the address field Advanced only and nothing saying where to get AutoTune for the TV.* (2026-11-13) → DevStore (sent 2026-11-14)
- **Show only what the first step needs.** *Connected, the home-theatre page had ten sections and nothing marked as the start.* (2026-11-14) → DevStore (sent 2026-11-14)
- **A finished task looks finished, after a restart too.** *Tune offered Continue after Keep.* (2026-11-13) → DevStore (sent 2026-11-14)
- **Lists show only what belongs to the current context.** *On the PC the home-theatre list offered the TV's results.* (2026-11-14) → DevStore (sent 2026-11-14)

## Links between devices

- **Stop reaches the other side; a vanished side stops the other.** *Stop on the phone left the TV playing; the TV kept looping noise after the phone went.* (2026-11-13) → DevStore (sent 2026-11-14)
- **A dropped link says "connection lost".** *A drop mid-test read as "turn the volume up".* (2026-11-13) → DevStore (sent 2026-11-14)
- **Both sides notice a dead link (ping, answer, silence timeout).** (2026-11-13) → DevStore (sent 2026-11-14)
- **Report a send that failed.** *The EQ was logged as sent with the link closed.* (2026-11-13) → DevStore (sent 2026-11-14)
- **Limit wrong pairing codes across all connections.** (2026-11-13) → DevStore (sent 2026-11-14)
- **Serialise reconnects.** *The page, the retry and the widget cut each other's link.* (2026-11-13) → DevStore (sent 2026-11-14)

## Auditing and testing

- **Run the real screens, in every mode, and do the destructive things.** *Two audits walked Simple only, never deleted anything and never pressed Back while measuring.* (2026-11-14) → DevStore (sent 2026-11-14)
- **Time a wait on screen by the UI's own clock (its delays or frame time), not the wall clock.** *Connect's "offer the address after 6 s" read `System.currentTimeMillis()`, so in the walk harness, whose clock runs faster than the wall, the field never came; on a phone the two agree, so nobody noticed.* (2026-11-14) → DevStore (sent 2026-11-14)

## This app only

- **The walk harness (`tools/walk/`)**: menus and dialogs open in their own window, so their position on the screenshot is not where they are on a phone; `wait N` advances the phone's clock by N s but real time by about a quarter of that, so anything timed by the wall clock (the countdown between spots) runs slow there; `type` adds to what a field holds. (2026-11-14)
- **One Gradle command at a time.** Two at once corrupt the build cache. (2026-11-13)
- **Equalizer APO** works on one output at a time: the PC app checks the output it is installed on (`notOnOutput`) and says so in one line with one key. (2026-11-14)
- **Android TV**: the TV's link lives in the TV screen's view model, so it is reachable only while that screen is open; its EQ switches back on with the bands it had (`TvEq.kept`). (2026-11-14)
- **Obtainium filters by the release title** ("Android X.Y.Z"), so the filter is `^Android`, not `^android-`. (2026-11-11)
