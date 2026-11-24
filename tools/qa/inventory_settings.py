"""List static settings declarations with their preference and default expressions."""

import argparse
import csv
import re
from pathlib import Path

CALL = re.compile(
    r'\b(boolPref|intPrefRes|intPref|stringPref)\(\s*'
    r'("(?:[^"\\]|\\.)*"|[\w.]+)\s*,\s*'
    r'("(?:[^"\\]|\\.)*"|[\w.]+)\s*\)',
)
LABEL = re.compile(r'R\.string\.([\w]+)')


def rows(source):
    root = source / "app/src/main/java/dev/bbkb/ime/core/settings/screens"
    for path in sorted(root.rglob("*.kt")):
        body = path.read_text(encoding="utf-8")
        for match in CALL.finditer(body):
            call, key, default = match.groups()
            label = LABEL.search(body, match.end(), min(match.end() + 220, len(body)))
            line = body.count("\n", 0, match.start()) + 1
            yield (
                path.stem, key.strip('"'), call, default, label[1] if label else "",
                path.relative_to(source).as_posix(), line,
            )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source = Path(__file__).resolve().parents[2]
    rows_found = list(rows(source))
    with args.output.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(("screen", "preference_expression", "store", "default_expression", "label_candidate", "file", "line"))
        writer.writerows(rows_found)
    print(f"{len(rows_found)} declarations")


if __name__ == "__main__":
    main()
