#!/usr/bin/env python3
"""Builds the character frequency table the keyboard ships.

    python3 count.py --language pl

Reads everything in raw/ for the language and writes
app/src/main/assets/frequencies-<language>.bin.

Counts, not a finished tree. The tree is a few dozen nodes and the device builds it at
startup from the same Kotlin the simulator uses, so there is exactly one implementation of
the code construction in the project. Shipping a pre-built tree would add a second one, in
Python, and a disagreement between them would be a keyboard whose measured KSPC belongs to
a different keyboard.

Format, big-endian:

    magic     4 bytes  "H4F1"
    count     u16      number of entries
    reserved  u16      0
    entries   count x (u16 UTF-16 code unit, u64 count)
"""

import argparse
import os
import struct
import sys
from collections import Counter

from alphabet import ALPHABETS

HERE = os.path.dirname(__file__)
DEFAULT_RAW = os.path.join(HERE, "raw")
DEFAULT_ASSETS = os.path.join(HERE, os.pardir, "app", "src", "main", "assets")

MAGIC = b"H4F1"


def count(language: str, raw: str, title_weight: int) -> Counter:
    alphabet = set(ALPHABETS[language])
    sources = sorted(
        os.path.join(raw, name)
        for name in os.listdir(raw)
        if name.endswith(f"-{language}.txt")
    )
    if not sources:
        raise SystemExit(f"no raw text for {language}: run fetch.py first")

    counts = Counter()
    for path in sources:
        # Titles are a few percent of the text but the whole workload. The weight is how the
        # domain mix gets set deliberately rather than by whatever the download contained.
        # startswith, not `in`: "titles-" is a substring of "subtitles-", so a containment
        # test weights the entire corpus uniformly and changes no ratio at all.
        weight = title_weight if os.path.basename(path).startswith("titles-") else 1
        with open(path, encoding="utf-8") as handle:
            for line in handle:
                # No trailing space. A line is one thing somebody types into one field, and a
                # query ends with the centre button, not with a space. Appending one was wrong
                # and expensively so: it lands hardest on short lines, inflating space by 5.8
                # points on titles, which is exactly the domain this keyboard is for.
                for character in line.rstrip("\n"):
                    if character in alphabet:
                        counts[character] += weight

    # Every character in the alphabet has to be typable even if the corpus never produced it.
    # A zero here becomes the longest code, which is the right answer; a missing key would
    # become no code at all, which is not.
    for character in ALPHABETS[language]:
        counts.setdefault(character, 0)

    total = sum(counts.values())
    print(f"{language}: {total} weighted characters from {len(sources)} files", file=sys.stderr)
    return counts


def write(language: str, counts: Counter, out: str) -> str:
    os.makedirs(out, exist_ok=True)
    target = os.path.join(out, f"frequencies-{language}.bin")

    entries = sorted(counts.items())
    with open(target, "wb") as handle:
        handle.write(MAGIC)
        handle.write(struct.pack(">HH", len(entries), 0))
        for character, value in entries:
            handle.write(struct.pack(">HQ", ord(character), value))

    total = sum(counts.values()) or 1
    top = ", ".join(
        f"{character if character != ' ' else '_'} {value / total * 100:.1f}%"
        for character, value in sorted(counts.items(), key=lambda item: -item[1])[:8]
    )
    print(f"  most frequent: {top}", file=sys.stderr)
    print(f"  {os.path.basename(target)}  {os.path.getsize(target)} bytes", file=sys.stderr)
    return target


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", choices=tuple(ALPHABETS), required=True)
    parser.add_argument("--raw", default=DEFAULT_RAW, help="directory of normalised text")
    parser.add_argument("--out", default=DEFAULT_ASSETS, help="where to write the table")
    parser.add_argument(
        "--title-weight",
        type=int,
        default=1,
        help="how many times title text counts, to set the domain mix deliberately",
    )
    arguments = parser.parse_args()

    counts = count(arguments.language, arguments.raw, arguments.title_weight)
    write(arguments.language, counts, arguments.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
