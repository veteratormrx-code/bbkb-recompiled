#!/usr/bin/env bash
# Guards against ELF symbol interposition: global lookup gives the blob priority, so a symbol that
# libkb.so calls through the PLT (JUMP_SLOT/GLOB_DAT), defines itself, and the patched blob still
# defines lands in the blob's copy.
# Usage: check_interposition.sh [libkb.so] [libnative-lib.so]     (exit 1 if any symbol is trapped)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$HERE/../.."
KB="${1:-}"
BLOB="${2:-$ROOT/app/src/main/jniLibs/arm64-v8a/libnative-lib.so}"

if [ -z "$KB" ]; then
  APK=$(ls "$ROOT"/app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1 || true)
  [ -n "$APK" ] || { echo "SKIP: no debug APK built; nothing to check."; exit 0; }
  TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
  unzip -p "$APK" lib/arm64-v8a/libkb.so > "$TMP/libkb.so" 2>/dev/null || {
    echo "SKIP: APK has no libkb.so (a -Pxt9Diff build?)."; exit 0; }
  KB="$TMP/libkb.so"
fi
[ -f "$BLOB" ] || { echo "SKIP: blob not found at $BLOB"; exit 0; }

nm_d()      { llvm-nm -D --defined-only "$1" 2>/dev/null || nm -D --defined-only "$1"; }

# Symbol names this object resolves through the PLT / GOT.
# On an x86_64 host objdump happily "decodes" the aarch64 libkb.so, exits 0 and prints no
# relocations at all, so a zero exit status proves nothing: fall back to readelf (architecture
# independent) whenever objdump yields no rows.
reloc_imports() {
  local f="$1" rows
  rows=$(objdump -R "$f" 2>/dev/null | awk '$2 ~ /JUMP_SLOT/ || $2 ~ /GLOB_DAT/ {print $3}')
  if [ -z "$rows" ]; then
    rows=$(readelf -rW "$f" 2>/dev/null \
           | awk '$3 ~ /JUMP_SLOT/ || $3 ~ /GLOB_DAT/ {print $5}' | sed 's/@.*//')
  fi
  printf '%s\n' "$rows" | awk 'NF' | sort -u
}

imports=$(reloc_imports "$KB") || { echo "FAIL: cannot read the relocations of $KB"; exit 1; }
[ -n "$imports" ] || { echo "FAIL: $KB imports nothing; the relocation reader found no PLT/GOT symbols"; exit 1; }
kbdef=$(nm_d "$KB"   | awk '{print $3}' | sort -u)
blobdef=$(nm_d "$BLOB" | awk '{print $3}' | sort -u)

trap_set=$(comm -12 <(echo "$imports") <(echo "$kbdef") | comm -12 - <(echo "$blobdef"))

if [ -n "$trap_set" ]; then
  echo "FAIL: symbol-interposition trap — libkb.so calls these through the PLT, defines them"
  echo "      itself, and the blob ALSO defines them, so the blob's copy wins at runtime:"
  echo "$trap_set" | sed 's/^/        /'
  echo
  echo "  Fix: call a static internal implementation instead of the exported name"
  echo "  (see process_trace_impl in kdb_trace.c), or add the symbol to elf_cutover.py's CONTRACT."
  exit 1
fi
echo "==== interposition guard: no trapped symbols ===="
