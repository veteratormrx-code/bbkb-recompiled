"""Count unique surface forms in each RUL1 lexicon."""
import sys
import zlib
from pathlib import Path

def count(path: Path):
    data = path.read_bytes()
    magic, n = int.from_bytes(data[0:4], "big"), int.from_bytes(data[4:8], "big")
    assert magic == 0x52554C31, path
    pos = 8
    sections = []
    for _ in range(n):
        plen = int.from_bytes(data[pos:pos + 2], "big")
        pos += 2 + plen
        offset = int.from_bytes(data[pos:pos + 4], "big")
        length = int.from_bytes(data[pos + 4:pos + 8], "big")
        pos += 8
        sections.append((offset, length))
    forms = set()
    rows = 0
    for offset, length in sections:
        text = zlib.decompress(data[offset:offset + length]).decode("utf-8")
        for line in text.splitlines():
            if not line:
                continue
            word = line.split("\t", 1)[0]
            forms.add(word)
            rows += 1
    print(f"{path.name}\trows={rows}\tunique={len(forms)}")
    return forms

if __name__ == "__main__":
    root = Path(sys.argv[1])
    all_forms = set()
    for path in sorted(root.glob("*.bbd")):
        all_forms |= count(path)
    print(f"ALL\tunique={len(all_forms)}")
