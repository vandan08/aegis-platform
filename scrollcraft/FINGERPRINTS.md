# Fingerprints

Every site you build with **scrollcraft** gets one row here, appended after it
ships. The registry exists so your next build can prove it is a different page
rather than a re-skin of one you already made.

This file is **yours**. It starts empty on purpose: the gate is about not
repeating *yourself*, so it has nothing to say until you have built something.

The rules and the gate live in the skill's
`references/uniqueness.md`. Short version:

**A new build must differ from EVERY row below on at least 4 of the 6
dimensions.** Four against each row individually, not four on average across the
table. If a planned build fails, change the plan. Never edit a row to make room
for it.

The six dimensions are: **grammar**, **nav treatment**, **hero device**,
**act-sequence shape**, **close pattern**, **signature move**.

Dimension 6 is free, because a signature move is unique by definition. So the
gate really asks for three more out of the remaining five, and a build that
changes only grammar and world will fail it.

---

## The registry

| Build | Grammar | Nav treatment | Hero device | Act-sequence shape | Close pattern | Signature move | World | Port |
|---|---|---|---|---|---|---|---|---|

*(empty: your first build has nothing to clear, so build whatever the interview
points at. From the second onwards, this table is the constraint.)*

---

## What is taken

Add a bullet here whenever a build claims something a later build should avoid
reusing: a grammar, a nav treatment, a close pattern, a signature move, an
act-count-and-length band. The shared columns are what the next build inherits
as a constraint, so writing them down is the whole point.

Nothing is taken yet.

---

## Appending a row

After shipping, add one line to the table and one bullet to **What is taken** if
the build claimed something new. Fill every column. Say what the build shares
with existing rows.

Rows are append-only. A build that has been superseded stays in the table,
because the space it occupies is still occupied.

---

## Worked example

The skill's author kept a registry of twelve builds across eight page grammars.
If you want to see what a filled-in table looks like, and which shapes tend to
collide, read `EXAMPLES.md` in the scrollcraft repository. Treat it as
illustration only: those rows are somebody else's builds and they do **not**
constrain yours.

---

| # | Build | Grammar | Nav treatment | Hero device | Act-sequence shape | Close pattern | Signature move | World | Port |
|---|---|---|---|---|---|---|---|---|---|
| 1 | **aegis** | Continuous world (worldflight) | Waypoint map, fixed left rail, clickable, driven by `sc:waypoint`. No bar, no wordmark-plus-CTA. | Establishing position inside the world at s=0.115, copy in the fixed layer on a `hero` window. No scrub act, no greet cue. | 6 legs, one continuous camera, 11.30vh of weight + 1 = **12.30vh** track. Weights 1.74/1.74/1.74/**2.60**/1.74/1.74 at one pace (0.216vh per second of film). | Arrival at a place in the same canvas: the camera returns to the whole schematic with the console plate prominent, CTA as a real link in the finale copy window. Nothing fades out. | **The page refuses you.** At 0.60 of the track a 403 panel takes the frame, scroll is held by refusing wheel/touch/key events, and it lists the reader's own evaluated attributes (viewport, pointer, UTC hour, language, reduced-motion, referrer) read locally and sent nowhere. Dismissed by button or Escape, once per session. | Cold technical schematic, 16000x9000, deep-zoom. Real product screenshots as figure plates. Camera rendered frame-by-frame by headless Chrome, not generated. | 4510 |

**Shared with prior rows:** nothing. First build in this registry.

**Taken by this row, for the next build to avoid:** the continuous-world grammar; the
clickable waypoint-map nav; a camera rendered from a DOM scene rather than generated; a
modal that holds the reader at the peak.
