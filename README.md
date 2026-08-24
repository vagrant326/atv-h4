# atv-h4

A replacement keyboard for Android TV that types with **four d-pad directions and nothing
else**. Every character is a short run of presses — one for space, two for the common
letters, three or four for the rest.

Grid keyboards — Gboard TV, LeanKeyboard, the in-app keyboards in Netflix and YouTube — all
cost roughly **10 keystrokes per character**, because every letter means travelling a cursor
there and confirming. This costs about **2.3**, and it is deterministic: a finished code
produces exactly one character. Nothing is predicted, so there is nothing to check and
nothing to correct.

This is MacKenzie's H4-Writer (*1 Thumb, 4 Buttons, 20 Words Per Minute*, UIST 2011): a
base-4 Huffman code over character frequencies.

**Status: builds and runs.** Not yet tried on a TV for long enough to say anything about the
learning curve, which is this method's real risk.

---

## Hardware requirement

**A d-pad, a centre button, and BACK.** That is the whole list, and every Android TV remote
in existence has them — including Chromecast-with-Google-TV and Shield-class remotes, which
have no number keys and cannot run the companion project
[`atv-letterwise`](https://github.com/vagrant326/atv-letterwise) at all.

There is no long press in the typing path either — the only hold in the keyboard is the
word jump inside the edit mode, where nothing is being composed and a repeat cannot commit a
character. Deleting, moving the caret,
switching language and reaching the digits all live on the d-pad: two of them in the reserved
`UP` branch, two inside the edit mode. Every assignable button is optional, including the digit
one — and a field that asks for numbers switches to the digit layer by itself, so nothing about
digits depends on having a spare key.

`BACK` is the one non-d-pad key that is load-bearing, for exactly two things: abandoning a
half-finished character and closing the keyboard. Abandoning **cannot** be a code — you are
mid-code, so every further press leads to some leaf, never to a cancel — so it needs a key
outside the tree. The Android TV compatibility definition requires `BACK` on every remote, so
this is safe, but it is worth stating rather than hiding inside the claim of d-pad-only.

## How it types

| Input | Action |
|---|---|
| `DPAD_LEFT` / `RIGHT` / `DOWN` | The code symbols — every character is a run of these |
| `DPAD_UP` | Reserved: space, delete, digits, edit mode |
| `DPAD_CENTER` | Submit — whatever the field's own action is |
| `BACK` | In order: drop a half-finished character, leave a mode, close the keyboard |

The guide above the field shows what each direction leads to **from where you are now**, so you
never have to know a code in advance: press towards the letter you want and read the next step.
Settings → Guide switches between the full cross, one compact line, and off.

Each direction gets two rows — what **one more press finishes**, and what is **further down**:

```
↑  _ · [del] · [123] · [edit]
←  a · e                    →  i · n · r      ↓  l · o · s · t
   bcdghjkmpquvwxyz.,-'&:/     byfk
```

Sorted alphabetically, not by frequency, because the question you are asking is "is my letter in
here" and frequency order puts the answer somewhere unpredictable. The second row lists
**everything** rather than a sample: "not in the twelve I showed you" is not an answer. The top
row is separated by middots rather than spaces — four letters in alphabetical order read as a
word often enough to matter, and `l o s t` was the one that made the point.

**Nothing here assumes you will memorise the codes.** MacKenzie's participants reached about 20
wpm after ten sessions, and that may well happen, but it is not designed for: the guide is
treated as permanently on, it is sized so the first press is never a guess, and every trade-off
in the tree is settled on presses rather than on what would be easier to remember. If your
thumb does learn it, you turn the guide off and get the screen space back.

### `UP` is reserved

The first press is not a code symbol. `UP` opens a fixed branch, and it never moves:

```
↑↑ space     ↑↓ delete     ↑← digits     ↑→ edit mode
```

Four functions, two presses each, in the same place forever. The reason is not comfort. **None
of delete, the digit layer or the edit mode has a frequency in any corpus** — no text records
how often somebody fixes a typo or enters a PIN — so letting Huffman place them means placing
them from numbers that were invented. Reserving a branch replaces three fabricated weights with
one structural decision.

It costs the letters a quarter of their two-press codes, measured at **+4.5% of all presses**,
and buys a second thing besides honesty: the long tail used to be piled into one direction
while the other three resolved in two presses. Now it is spread over three comparable branches.

### The code table, English

The other three directions carry the characters. Straight out of `./gradlew :core:codes
-Planguage=en`:

```
←→ a   ←↓ e   →← i   →→ n   →↓ r
↓↑ l   ↓← o   ↓→ s   ↓↓ t          …and three to six presses for the rest
```

Functions are written `[del]`, `[123]`, `[edit]` — bracketed and in their own colour everywhere
they appear. In a branch preview the characters run together with no separator to fit a dozen in
one cell, so a bare `DEL` reads as the letters d, e and l, which is the wrong answer to the only
question the guide is asked.

Expected **2.424 presses per character** for English, **2.528** for Polish, deepest code **6**
in both. The full table is in the app, under Settings → Codes, because on this keyboard the
table *is* the interface and a reference you can reach from the sofa is part of the product.

The branches are lopsided — `↓` holds four letters and `←` holds twenty-two — and that is the
shape of the language, not a flaw in the construction. A skewed distribution means the cheap
symbols are few and the expensive ones are many, so evening out the branch sizes means moving
frequent letters deeper. Priced exactly: a tree with every character at the same depth, which is
the only way the three branches come out equal, costs **+22% for English and +18% for Polish**
(2.424 → 2.949, 2.528 → 2.996). `./gradlew :core:bench` prints that row.

Space gets two presses, not one, and that is the corpus talking. At 18.9% — its share in
running speech — Huffman gives it one press and a quarter of the whole code space. But a TV
query is one to three words: space is **8.9%** of the characters in the real query corpus, and
seven of nineteen queries contain no space at all. Once the tables were fitted to that, space
stopped earning a one-press code on its own — which is what freed `UP` to be reserved at all.
Details in `docs/20-h4writer.md §8`.

### The edit mode

Caret movement is not a code. As a code it cost four presses **per character moved**, so
walking back five characters was twenty presses — and moving the caret is inherently
repetitive. So `↑→` enters a mode where a direction does one thing for one press:

| In edit mode | |
|---|---|
| `←` / `→` | move the caret one character |
| **hold `←` / `→`** | move a whole word |
| `↑` | delete |
| **hold `↑`** | delete the rest of the word |
| `↓` | language list |
| `BACK` | back to typing |

Walking back five characters and deleting: **8 presses instead of 21**. The language switch
lives here rather than in the reserved branch because you change language once a session and
fix a typo once a word.

Both holds move by a **word, not by a rate**, and that is the only reason they are safe. Android
repeats a held key about twenty times a second after a 400 ms delay, and a TV query averages
eleven characters — an accelerating caret crosses the whole field before your thumb reacts,
while any rate slow enough to aim is no faster than just pressing. One hold is one jump, later
repeats are swallowed, and the jump stops at a word boundary, so overshoot is impossible rather
than merely unlikely.

These are the only two holds in the keyboard, and there will not be a third without a
measurement. Making every direction press-or-hold would double the leaf space and cut the code
from 2.42 actions per character to about 1.62 — but a hold costs roughly two to two and a half
presses in dwell time, which is exactly the break-even band, and nobody has measured `t_hold` on
a real remote yet. Meanwhile it would put *timing* inside the code, so too short a hold would
type a different character, and it would double what the guide has to show at every step.
`docs/20-h4writer.md §8` has the arithmetic. The rule it leaves behind: **a hold earns its dwell
time only when it replaces three presses or more**, which is why both of these replace five or
six, and why neither is a character.

### Digits

Digits are **not** in the main tree at all. The second layer holds the ten digits plus a full
stop and a comma — twelve symbols in exactly twelve two-press slots, so **everything there costs
two presses and no direction pair leads nowhere**. `↑←` gets you there, and `↑←` gets you back:
the reserved branch is the same four positions in both layers.

The layer is **sticky**: it stays until you press `BACK` or type a space, and a space inside it
both types itself and leaves — so `blade runner 2049 remastered` pays for going in and nothing
for coming back.

Punctuation stays with the letters, not with the digits. There was briefly a setting that moved
it to the layer in exchange for shorter letter codes, and it measured as a dead heat on held-out
titles — 2.361 against 2.363 for English — because the layer trips cost what the shorter letters
saved. A setting that rebuilds every code for nothing is worse than no setting.

A seven-digit sideload code therefore costs 2 + 14 = **16 presses**. A field that declares
itself numeric opens in the layer by itself, and a button for it is optional like every other
button here.

### Diacritics

Polish `ą ć ę ł ń ó ś ź ż` are characters with their own codes. There is no ambiguity for
them to inflate here — only a slightly longer code for a rarer character — which is why this
method takes them without an argument.

Everything is typable: proper nouns, film titles, invented words, passwords. There is no
dictionary to be missing from.

## One code table per language

Both Polish and English are supported, and adding a language is one enum entry plus one
frequency table (see [`corpus/README.md`](corpus/README.md)).

Each gets **its own tree**, fitted to its own letter frequencies. Measured on 2000 held-out
titles per language:

| | Polish | English |
|---|---|---|
| One tree per language | **2.395** | **2.363** |
| One tree for both | 2.395 | 2.388 |

The merged tree is available under Settings → Code table, and there is a real case for it — but
it is a case about *memorised* typing: one table to learn, and no language mode that can sit in
the wrong position emitting valid-but-wrong characters. Since nothing here assumes memorisation,
and a user reading the guide cannot make a mode error at all, the decision falls to presses.

For the record, the two per-language trees have **32% of their codes in common** — the reserved
branch is identical by construction, and the rest is pinned so the languages agree wherever
their code lengths allow, which costs no presses at all and beats ordering by frequency (19%).
If you do stop reading the guide, switch to the merged tree; the argument reverses at that
point.

## Netflix and YouTube need one thing set up

They never focus a text field, so nothing raises the keyboard for you there. Assign a
**trigger button** (Settings → Buttons) and press it on their search screen — the keyboard
comes up and types.

The trigger is the one function that cannot be a code, because the keyboard is not on screen
when it is needed. It is the only key listened for while hidden, and it is unassigned by
default: a component that intercepts keys ahead of the foreground app is exactly what once
left this TV unnavigable, and this keyboard's code symbols *are* the d-pad, so the risk is
larger here than anywhere else in the programme.

Everywhere that does ask Android for text — system and launcher search, Wi-Fi setup, browsers,
app logins, the Downloader address bar — it appears on its own.

## Updating

Settings → Check for updates. It compares the installed version against the latest release,
downloads the APK and hands it to the system installer. The first time, Android will ask you
to allow this app to install packages; the screen links straight there.

Nothing checks on its own — no background job, no boot receiver, no poll on keyboard start.

## The `INTERNET` and `REQUEST_INSTALL_PACKAGES` permissions, and why a keyboard has them

**A keyboard that can reach the network and install packages is a legitimate thing to be
suspicious of.** An IME sees every password and every card number typed on the device. This
one holds both permissions because it is distributed by sideloading rather than through a
store, so it has no other way to update itself.

What they are allowed to do here:

- **The IME process never opens a socket and never installs anything.** All of it lives in a
  separate process (`:updater`): one activity, which downloads the APK and streams it into a
  `PackageInstaller` session. The component handling your keystrokes contains none of that
  code, and there is no exported provider — nothing else on the device can reach the file.
- **Nothing runs unless you press a button.** No background job, no boot receiver, no
  periodic poll, no check when the keyboard starts.
- **Two requests, no payload.** One `GET` for the latest release tag, one for the APK. No
  device identifier, no version histogram, no analytics, no crash reporting.
- **Nothing you type ever leaves the device.** There is no telemetry path in this codebase
  and there will not be one.
- **The APK is fetched from this repository's releases and installed through the system
  installer**, which shows you what is being installed and by whom.

Both come out if the project ever gets a store listing. Until then the code is small enough
to check by reading it, which is the point.

## Building

Everything runs in the dev container — no JDK, Android SDK or Gradle cache on the host:

```bash
docker compose -f docker/compose.yaml run --rm -w /work/atv-h4 dev ./gradlew check
```

The `core` module is plain Kotlin with no Android dependencies, on purpose: the simulator and
the shipped IME build the code tree with the same code, so a KSPC measured on a laptop is the
KSPC that ships. On a deterministic method that is not an approximation — it is the same
arithmetic.

```bash
docker compose -f docker/compose.yaml run --rm -w /work/atv-h4 dev ./gradlew :core:bench
docker compose -f docker/compose.yaml run --rm -w /work/atv-h4 dev ./gradlew :core:codes -Planguage=pl
```

## Branching and the two channels

| Branch | What runs | Result |
|---|---|---|
| `feature/**`, `fix/**`, pull requests | CI — tests, lint, both debug APKs | artifacts only |
| `develop` | Release dev | `dev-x.y.z`, installs as **atv-h4 dev** |
| `main` | Release | `vx.y.z`, installs as **atv-h4** |

**The dev build is a separate application**, not just a separate file: it carries its own
`applicationId`, so it installs alongside the released one and both appear in the keyboard
picker. That is the point — an experiment that misbehaves does not take the working keyboard
with it, and this project has already lost a TV's navigation to one.

Day to day: work on `develop`, which publishes a dev build on every push. To ship, open a
pull request from `develop` to `main` and merge it. **Do not delete `develop`** — it is
long-lived. After merging, bring it back in line:

```bash
git switch develop && git merge --ff-only main && git push
```

Releases need four repository secrets and a keystore of this repository's own — see
`docs/release-and-signing.md` in the programme notes. The key must never change: a different
one means Android refuses the update and the only way out is an uninstall, which takes the
user's settings with it.

## Installing

Once a release exists, either address serves the newest build:

```
https://github.com/vagrant326/atv-h4/releases/download/latest/atv-h4.apk
https://github.com/vagrant326/atv-h4/releases/latest/download/atv-h4.apk
```

The first is a rolling `latest` release that each build recreates. The second is resolved by
GitHub itself from the newest versioned release. The asset name deliberately carries no
version number, which is what keeps the URL stable.

Then Settings → System → Keyboard, select it, and enable it. Android requires that step
manually for every IME.

**If the keyboard ever leaves the TV unnavigable**, press `HOME` — an IME cannot intercept it
— and switch keyboards or uninstall from there. A USB mouse also always works, because
pointer events never reach the keyboard's key handling.

## Licence

MIT. See [LICENSE](LICENSE).

Frequency tables are built from OpenSubtitles (OPUS) and Wikidata; the corpus itself is
fetched by script rather than vendored, and attribution lives with the fetch tooling.
