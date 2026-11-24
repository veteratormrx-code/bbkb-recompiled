"""Aggregate bigram and trigram next-word tables from 4pda phrase shards.

Input line: context<TAB>next:count<TAB>next:count
context may be one word (bigram) or two words (trigram). Counts for the same
context are summed across files. The first-seen row does not win.
"""
import sys
from collections import defaultdict
from pathlib import Path


def norm(text: str) -> str:
    return " ".join(text.strip().lower().replace("ё", "е").split())


def main(src: Path, dest: Path) -> None:
    counts = defaultdict(lambda: defaultdict(int))
    rows = 0
    for path in sorted(src.glob("*.tsv")):
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            if "\t" not in line:
                continue
            context, rest = line.split("\t", 1)
            key = norm(context)
            if len(key) < 2 or len(key.split()) > 2:
                continue
            rows += 1
            bucket = counts[key]
            for piece in rest.split("\t"):
                if ":" not in piece:
                    continue
                word, raw = piece.rsplit(":", 1)
                word = word.strip()
                if not word:
                    continue
                try:
                    bucket[word] += int(raw)
                except ValueError:
                    bucket[word] += 1
    dest.parent.mkdir(parents=True, exist_ok=True)
    lines = []
    for key in sorted(counts):
        ranked = sorted(counts[key].items(), key=lambda item: (-item[1], item[0]))[:8]
        if not ranked:
            continue
        payload = "|".join(f"{word}:{count}" for word, count in ranked)
        lines.append(f"{key}\t{payload}\n")
    dest.write_text("".join(lines), encoding="utf-8")
    print(f"{dest.name}: {len(lines)} contexts from {rows} rows")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
