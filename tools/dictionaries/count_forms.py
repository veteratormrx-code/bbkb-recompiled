"""Validate RUL1 lexicons and count surface forms."""

import argparse
import hashlib
import json
import struct
import zlib
from pathlib import Path
from zipfile import ZipFile

PACKS = (
    "ru-common.bbd",
    "ru-conversational.bbd",
    "ru-opencorpora.bbd",
    "ru-spelling.bbd",
    "ru-unimorph.bbd",
)


def normalize(word):
    return word.lower().replace("ё", "е")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def inspect_pack(data):
    require(len(data) >= 8 and data[:4] == b"RUL1", "invalid RUL1 header")
    sections = []
    prefixes = set()
    count = struct.unpack_from(">I", data, 4)[0]
    require(0 < count <= (len(data) - 8) // 12, "invalid section count")
    cursor = 8
    for _ in range(count):
        length = struct.unpack_from(">H", data, cursor)[0]
        cursor += 2
        require(cursor + length + 8 <= len(data), "truncated section header")
        prefix = data[cursor:cursor + length].decode("utf-8")
        require(len(prefix) in (2, 3) and prefix == normalize(prefix), f"invalid prefix {prefix!r}")
        require(prefix not in prefixes, f"duplicate prefix {prefix!r}")
        prefixes.add(prefix)
        cursor += length
        offset, packed_size = struct.unpack_from(">II", data, cursor)
        cursor += 8
        sections.append((offset, packed_size, prefix))

    forms = set()
    rows = zero_frequency = raw_bytes = 0
    min_length, max_length = len(data), 0
    for offset, size, prefix in sorted(sections):
        require(offset == cursor and 0 < size <= len(data) - offset, f"invalid block range {prefix!r}")
        cursor += size
        decoder = zlib.decompressobj()
        unpacked = decoder.decompress(data[offset:cursor]) + decoder.flush()
        require(decoder.eof and not decoder.unused_data and unpacked, f"invalid zlib block {prefix!r}")
        raw_bytes += len(unpacked)
        for line in unpacked.decode("utf-8").splitlines():
            word, frequency = line.split("\t")
            require(word and normalize(word).startswith(prefix), f"invalid word {word!r} in {prefix!r}")
            require(frequency.isascii() and frequency.isdecimal() and int(frequency) <= 2_147_483_647,
                    f"invalid frequency for {word!r}")
            forms.add(word)
            rows += 1
            zero_frequency += int(frequency) == 0
            min_length = min(min_length, len(word))
            max_length = max(max_length, len(word))
    require(cursor == len(data), "trailing bytes after last block")
    return forms, {
        "blocks": count, "rows": rows, "unique": len(forms),
        "duplicate_rows": rows - len(forms), "zero_frequency_rows": zero_frequency,
        "uncompressed_bytes": raw_bytes, "min_word_length": min_length, "max_word_length": max_length,
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def inspect(root, reference):
    all_forms = set()
    packs = {}
    for name in PACKS:
        try:
            data = (root / name).read_bytes()
            forms, stats = inspect_pack(data)
            if reference is not None:
                stats["matches_reference_apk"] = data == reference.read(f"assets/bbmodern/{name}")
        except (ValueError, IndexError, struct.error, zlib.error) as exc:
            raise ValueError(f"{name}: {exc}") from exc
        packs[name] = stats
        all_forms.update(forms)
    return {
        "packs": packs,
        "unique": len(all_forms),
        "unique_normalized": len({normalize(word) for word in all_forms}),
        "duplicates_across_packs": sum(pack["unique"] for pack in packs.values()) - len(all_forms),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("--reference-apk", type=Path)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    if args.reference_apk:
        with ZipFile(args.reference_apk) as reference:
            result = inspect(args.root, reference)
    else:
        result = inspect(args.root, None)
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        for name, stats in result["packs"].items():
            print(f"{name}\trows={stats['rows']}\tunique={stats['unique']}\tblocks={stats['blocks']}")
        print(f"ALL\tunique={result['unique']}\tnormalized={result['unique_normalized']}")


if __name__ == "__main__":
    main()
