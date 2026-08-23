# Corpus and frequency table

Builds the character frequency table the keyboard ships. Everything here runs in the dev
container; nothing here runs on the device.

```bash
docker compose -f docker/compose.yaml run --rm -w /work/atv-h4/corpus dev \
  python3 fetch.py --language pl --megabytes 12

docker compose -f docker/compose.yaml run --rm -w /work/atv-h4/corpus dev \
  python3 count.py --language pl
```

`fetch.py` writes to `raw/`, which is gitignored. `count.py` writes
`app/src/main/assets/frequencies-<language>.bin`, which **is** committed: it is under a
kilobyte, and committing it means CI can build a working keyboard without downloading
anything.

## Counts, not a tree

The asset holds character counts. The tree is built on the device, at startup, by the same
Kotlin the simulator calls — see `core/CodeTree.kt`. That is deliberate: a base-4 Huffman
tree over fifty-odd symbols is nothing to build, and having exactly one implementation of
the construction is what stops a measured KSPC and a typed KSPC from being two different
keyboards. A pre-built tree in the asset would put a second implementation in Python.

## Adding a language

1. `fetch.py --language xx` — or supply `raw/anything-xx.txt` yourself, normalised.
2. Add `"xx"` to `ALPHABETS` in `alphabet.py` with that language's letters.
3. `count.py --language xx`.
4. One entry in the `Language` enum in `app/.../model/Languages.kt`.

There is no layout, no partition and no per-language rule to write. Under a Huffman code the
frequency table *is* the layout, which is the one place where this method is markedly less
work than a keypad-based one.

## Sources, and why two of them

**OpenSubtitles** (OPUS mono files) supplies running speech. That is what sets the letter
frequencies, and subtitles match the domain — film and television dialogue — far better than
general web text would.

**Wikidata** supplies film titles, series titles, actor and musician names. That is the
workload the keyboard actually faces, and it is where statistics from running text generalise
worst. It is also the only place digits show up in any quantity: subtitles contain almost
none, film titles are full of them.

12 MB per language is generous. Single-character frequencies over fifty symbols settle after
a few hundred thousand characters; the budget is that size only so the same raw text could
feed a higher-order model if one is ever wanted.

## Normalisation keeps diacritics, punctuation and digits

Wider than the keypad keyboards' alphabet, and deliberately so. Under a base-4 code every
character is a code of its own, so punctuation and digits cost a slightly longer code and
nothing else — there is no ambiguous group for them to inflate.

Polish diacritics are kept. Stripping them is the obvious cleanup and it would leave `ó`
with no code at all.

Characters outside the alphabet become a space rather than vanishing, and a leading dash is
dropped: subtitles mark a change of speaker with one, and counted as ordinary text it would
hand `-` a code far shorter than anyone typing a film title deserves to pay for.

## Format

```
magic     4 bytes  "H4F1"
count     u16      number of entries
reserved  u16      0
entries   count x (u16 UTF-16 code unit, u64 count)
```

Big-endian, so the Kotlin side reads it with a plain `DataInputStream`. Pinned from both
ends: `FrequencyTableTest` writes and reads the same bytes this script writes, because a
silent disagreement would produce a keyboard whose codes are assigned from nonsense while
looking entirely healthy.

## Measuring

```bash
docker compose -f docker/compose.yaml run --rm -w /work/atv-h4 dev ./gradlew :core:bench
docker compose -f docker/compose.yaml run --rm -w /work/atv-h4 dev ./gradlew :core:codes -Planguage=en
```

`bench` reports KSPC over `bench/queries-v1.tsv` for the three tree configurations, and how
many codes the Polish and English trees have in common. `codes` prints the table a user would
have to learn.
