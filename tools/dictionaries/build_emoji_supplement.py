"""Convert newer Emoji and older people variants to the keyboard's EBC1 format."""

import argparse
import hashlib
from collections import defaultdict
from pathlib import Path

GROUPS = (
    "Smileys & Emotion", "People & Body", "Component", "Animals & Nature",
    "Food & Drink", "Travel & Places", "Activities", "Objects", "Symbols", "Flags",
)
VERSIONS = {"E17.0", "E18.0"}


def generate(source):
    raw = source.read_bytes()
    rows = {}
    base_for_name = {}
    group = None
    for line in raw.decode("utf-8").splitlines():
        if line.startswith("# group: "):
            group = GROUPS.index(line.removeprefix("# group: "))
        data, _, description = line.partition("#")
        codepoints, _, status = data.partition(";")
        if status.strip() != "fully-qualified":
            continue
        codes = tuple(int(codepoint, 16) for codepoint in codepoints.split())
        emoji, version, name = description.strip().split(None, 2)
        toned = any(0x1F3FB <= code <= 0x1F3FF for code in codes)
        rows[emoji] = codes, version, name, group, toned
        if not toned:
            base_for_name[name] = emoji

    affected = set()
    skins = defaultdict(list)
    for emoji, (_, version, name, row_group, toned) in rows.items():
        if version not in VERSIONS and row_group != GROUPS.index("People & Body"):
            continue
        if toned:
            base = base_for_name.get(name.partition(":")[0])
            if base is None:
                continue
            skins[base].append(emoji)
            affected.add(base)
        else:
            affected.add(emoji)

    identity = f"EBC1\tUnicode Emoji 18.0 emoji-test.txt sha256={hashlib.sha256(raw).hexdigest()}"
    yield identity
    for order, (emoji, (codes, version, name, group, _)) in enumerate(rows.items()):
        if emoji not in affected:
            continue
        fields = [
            emoji, "-".join(f"{code:X}" for code in codes), name, "", "",
            str(group), "0", version[1:], str(order), "", "", "", "\x1f".join(skins[emoji]),
        ]
        yield "\t".join(fields)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("emoji_test", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(generate(args.emoji_test)) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
