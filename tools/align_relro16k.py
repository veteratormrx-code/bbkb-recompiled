"""Shrink PT_GNU_RELRO so its end is a multiple of 16 KiB.

Android's 16 KiB linker derives a library's alignment from that end. An end
whose largest power-of-two factor is 8 KiB is rejected even when every LOAD
segment is already 16 KiB aligned. The bytes stay mapped; they just are not
frozen read-only.
"""
import struct
import sys
from pathlib import Path

PT_GNU_RELRO = 0x6474E552
PAGE = 16384


def patch(path: Path) -> bool:
    data = bytearray(path.read_bytes())
    if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
        return False
    e_phoff = struct.unpack_from("<Q", data, 32)[0]
    e_phentsize, e_phnum = struct.unpack_from("<HH", data, 54)
    changed = False
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from("<I", data, off)[0]
        if p_type != PT_GNU_RELRO:
            continue
        p_filesz, p_memsz = struct.unpack_from("<QQ", data, off + 32)
        p_vaddr = struct.unpack_from("<Q", data, off + 16)[0]
        end = p_vaddr + p_memsz
        if end % PAGE == 0:
            continue
        new_end = end - (end % PAGE)
        if new_end <= p_vaddr:
            # The segment is smaller than the gap to the previous boundary.
            # Drop RELRO rather than leave an end the 16 KiB linker rejects.
            struct.pack_into("<I", data, off, 0)
            print(f"{path.name}: dropped RELRO ending at 0x{end:x}")
            changed = True
            continue
        new_memsz = new_end - p_vaddr
        new_filesz = min(p_filesz, new_memsz)
        struct.pack_into("<QQ", data, off + 32, new_filesz, new_memsz)
        print(f"{path.name}: RELRO end 0x{end:x} -> 0x{new_end:x}")
        changed = True
    if changed:
        path.write_bytes(data)
    return changed


def main() -> None:
    root = Path(sys.argv[1])
    for so in sorted(root.rglob("*.so")):
        patch(so)


if __name__ == "__main__":
    main()
