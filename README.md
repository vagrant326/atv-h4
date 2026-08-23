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

There is no long press anywhere in the typing path either. Deleting, moving the caret,
switching language and reaching the digits are all *codes*, not keys: they sit in the same
tree as the letters and are reached the same way.

## How it types

| Input | Action |
|---|---|
| `DPAD_UP` / `LEFT` / `RIGHT` / `DOWN` | The four code symbols |
| `DPAD_CENTER` | Submit — whatever the field's own action is |
| `BACK` | Abandons a half-finished character; closes the keyboard when there is nothing to abandon |

The guide above the field shows what each direction leads to **from where you are now**, so
you never have to know a code in advance: press towards the letter you want and read the next
step. Settings → Guide switches between the full cross, one compact line, and off.

That guide is meant to become unnecessary. A Huffman code cannot be guessed from anything you
already know and a TV remote has nothing printed on it, so the first session is slow —
MacKenzie's participants reached about 20 wpm after ten sessions. The whole bet of this
keyboard is that the guide is a transitional cost while a predictive keyboard's
check-the-prediction step never goes away.

### The code table, English

Straight out of `./gradlew :core:codes -Planguage=en`:

```
↑     space
←↑ a   ←← e   ←→ i   ←↓ n
→↑ o   →← r   →→ s   →↓ t
↓↑↑ delete   ↓↑← ,   ↓↑→ .   ↓↑↓ b
↓←↑ c   ↓←← d   ↓←→ f   ↓←↓ g
↓→↑ h   ↓→← l   ↓→→ m   ↓→↓ u
↓↓↑ w   ↓↓← y                       …and four or five presses for the rest
```

Expected **2.248 presses per character** for English, **2.412** for Polish. The full table is
in the app, under Settings → Codes, because on this keyboard the table *is* the interface and
a reference you can reach from the sofa is part of the product.

### Everything is a code

| Function | Cost in English |
|---|---|
| delete | 3 presses |
| caret left / right | 4 presses |
| digit layer | 5 presses in, 3 back |
| language switch | 5 presses |

Rare things pay for being rare, which is the method working as intended. If your remote has
a spare button you can put delete on it (Settings → Buttons) and pay one press instead of
three — worth doing, because deleting is the correction you make most.

### Digits

Digits are in the main tree, at four or five presses each. For a *run* of them — a PIN, a
pairing code, the seven digits of a Downloader shortcode — there is a second layer where
digits cost two presses and punctuation three. A field that declares itself numeric opens in
it; otherwise it is a code away.

### Diacritics

Polish `ą ć ę ł ń ó ś ź ż` are characters with their own codes. There is no ambiguity for
them to inflate here — only a slightly longer code for a rarer character — which is why this
method takes them without an argument.

Everything is typable: proper nouns, film titles, invented words, passwords. There is no
dictionary to be missing from.

## One code table, or one per language

Both Polish and English are supported, and adding a language is one enum entry plus one
frequency table (see [`corpus/README.md`](corpus/README.md)).

By default they **share a single tree**, built from both languages' frequencies together. The
alternative — a tree fitted to each language — is what the design note originally called for,
and it is available under Settings → Code table. The measurement is why the default went the
other way:

| | Polish | English |
|---|---|---|
| One tree per language | 2.412 | 2.248 |
| One tree for both | 2.449 | 2.293 |

Under 2% of a press. And the two per-language trees have only **27% of their codes in common**
— so the cost of the optimum is a second table to memorise and a mode that can be in the
wrong position, where a wrong-language press types a real character and the mistake arrives
looking like a typo. With one tree there is no language to switch, so there is nothing to have
set wrongly.

(27% is with the code assignment pinned so the two trees agree wherever they can, which costs
no presses at all. Ordering by frequency instead — the obvious choice — gives 14%.)

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
