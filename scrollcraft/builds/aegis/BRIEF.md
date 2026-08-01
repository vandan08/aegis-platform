# BRIEF — Aegis landing page

Interviewed 2026-08-31. Answers are the user's own selections, recorded verbatim as chosen.

---

## The eight answers

**1. Vibe, three to five words.**
> "Cold, precise, inevitable."

Reference register offered and accepted with it: the opening titles of a Fincher film; a Swiss
technical manual. Nothing decorative survives.

**2. The scroll journey, section by section.**
Not dictated beat-by-beat by the user. They chose the *shape* — one unbroken world — and the
destination — the refusal. The beat list in **The journey** below was drafted from those two
answers plus the product, and is shown to the user for correction before assets are finalised.

**3. The energy curve.**
Implied by the vibe rather than stated: "inevitable" is not loud. Cold and level for most of the
flight, one hard spike at the refusal, then a controlled landing. Drafted in full below.

**4. How they should feel, and the ONE moment.**
> The one moment: **"The refusal."** The camera reaches the policy engine and a request is denied
> on screen, with the exact reasoning visible. The page's whole argument lands in one frame.

**5. One thing this site should do that no site they have seen does.**
> **"The page refuses you."** At the peak the page itself denies the reader: scroll stops
> responding, a 403 panel takes over carrying the reader's own evaluated attributes, and they have
> to acknowledge it to continue. The site enforces its own policy on its reader.

**6. How far from premium-minimal.**
> **Editorial.** Typographic, magazine-like. Big type, strong grid, generous space, restrained
> colour. Reads senior and confident without shouting.

**7. One unbroken world, or distinct scenes?**
> **One unbroken world.** Chosen knowingly against the alternative.

**8. What assets do they already have?**
> **The real product.** No `KIE_AI_API_KEY` is set, and the user chose not to add one. Assets are
> real screenshots captured from the platform running locally, plus vector work authored in code.
> The brand's palette and shield mark already exist in the repository
> (`aegis-auth-server/src/main/resources/static/css/aegis.css`, `templates/fragments/ui.html`).

### Resolved tension

Worldflight is a video mode: one fixed stage, mp4 legs, continuity carried by footage that matches
at every seam. "One unbroken world" and "no generated video" cannot both hold as stated. Offered
three resolutions; the user chose:

> **Render the flight myself.** Build the world as one large composition from real product
> screenshots and authored vector art, fly a camera through it frame by frame with headless Chrome,
> encode the legs with ffmpeg. The seam law is satisfied by construction because the camera path is
> continuous and every leg's last frame is the next leg's first frame.

This is a stronger fit than a bought photographic world would have been: the world *is* the system
the page is arguing about.

### Additional answers (Step 1)

- **What is this, and who is it for?** A product page for Aegis, a zero-trust identity and access
  platform. For technical evaluators and prospective clients arriving from an Upwork profile.
- **What must the visitor believe by the end?** > **"This is real, not a toy."** Someone actually
  built a working zero-trust platform, and the visitor just watched it refuse them.
- **What does the visitor do next?** > **Open the live console.** One action, one label, used
  everywhere: **Open the live console**.

---

## The tell-someone sentence

> "It's the site where **the page itself refuses you, and shows you exactly what it judged you on**."

Not a device name. An experience.

---

## The journey

Six beats. Each is a shift in what the visitor knows or feels, and each is one leg of the flight.

| # | Beat | What changes |
|---|---|---|
| 1 | **Arrival** | The system at rest, seen whole. Every request here is a stranger. |
| 2 | **Approach** | A request enters. Something is being checked, and it is not the network it came from. |
| 3 | **Identity** | Into the token itself: the claims, the signature, the five-minute life. |
| 4 | **The refusal** | The policy engine denies it, and then denies *the reader*. **PEAK.** |
| 5 | **Depth** | Pull back: the service behind never saw the request. The refusal happened at the edge. |
| 6 | **Commitment** | The whole system visible again, and one action. |

Six legs deliberately, at a total track outside the 13.6–13.8vh band flagged in the skill.

---

## The feeling curve

Written before the score table. One line per act: the emotion, then what on screen causes it.

| # | Beat | Feeling | Caused by |
|---|---|---|---|
| 1 | Arrival | **Stillness, slight unease** | A whole system rendered cold and symmetrical, no people in it, nothing moving but the camera. |
| 2 | Approach | **Focus narrowing** | The frame closes on one checkpoint; the wide context drops away and cannot be recovered by scrolling faster. |
| 3 | Identity | **Clinical intimacy** | Close enough to read individual claims. A number counting down. Quiet — this is the held breath before the peak. |
| 4 | The refusal | **Rejection, personally** | The world stops. The page denies the reader by name and lists what it judged them on. |
| 5 | Depth | **Comprehension, cold relief** | The camera pulls back to show the service behind, untouched. The refusal already happened, upstream. |
| 6 | Commitment | **Resolve** | The system whole again, one line of type, one action. Nothing fades out. |

No two adjacent acts share a feeling. Act 3 is deliberately the quietest on the page so that act 4
lands hardest — the silence before the peak is authored, not dead scroll.

### The peak

**Act 4, "The refusal."** It gets the largest weight on the track by a visible margin, the
signature move, and the only moment on the page where scroll stops answering the reader.

> The sentence a visitor would say to a friend:
> *"I scrolled onto a security site and the site itself locked me out, then showed me exactly which
> of my attributes it judged — I had to dismiss a 403 to keep reading."*

### Authored silence

- **Act 3 in full.** Quiet by design, so the peak has something to be loud against. The verification
  pass must not read this as dead scroll: the camera is still advancing and the token's countdown
  is still changing, it is the *copy* that goes quiet.
- **The half-beat immediately before the refusal**, where the camera has arrived but the deny panel
  has not yet fired.

---

## Non-negotiables carried from the product

- **No invented statistics.** Every number on the page is one this platform actually produces, or
  it does not appear. The repository forbids fabricated figures, and a page arguing "this is real"
  cannot open with a made-up one.
- **No token is ever shown that is not decodable.** If a JWT appears on screen it is a real one.
- **Demo credentials shown on the page are the documented sandbox account only.**

---

## Feel check (run cold, after the build, before reading this file back)

| # | Beat | Intended | Felt | Verdict |
|---|---|---|---|---|
| 1 | Arrival | Stillness, slight unease | Stillness, cold competence | **Partial.** The unease is carried by the copy ("Every request here is a stranger"), not by the frame. The schematic reads calm and technical rather than uneasy. Accepted: the vibe answer was "cold, precise, inevitable", and unease was my inference, not the user's word. |
| 2 | Approach | Focus narrowing | Focus narrowing | Matches. |
| 3 | Identity | Clinical intimacy, quiet | Clinical, quiet | Matches. The countdown claim is copy rather than a live number here, since the page holds no token. |
| 4 | The refusal | Rejection, personally | Rejection, personally | **Matches, and it is the peak.** The panel names the reader's own attributes, which is what makes it land rather than read as a graphic. |
| 5 | Aftermath | Comprehension, cold relief | Comprehension | Matches. |
| 6 | Commitment | Resolve | Resolve | Matches. The close arrives on a place and holds; it does not fade. |

**Changed as a result of the check:** every copy block had been anchored `lead`, which made
six different positions in the world read as one recurring caption. Anchors now vary
(lead, trail, lead, centre, trail, lead) so arriving at a waypoint feels like a new position.

**Not changed, and why:** the mid-zoom legs (2 and 5) are visually sparse, because the world
between blocks is mostly empty ground. Filling it would mean inventing content the product
does not have, which is the opposite of what this page is for.
