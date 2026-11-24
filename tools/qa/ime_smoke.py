"""Verify actual IME tap sequences in a focused, empty synthetic test editor."""

import argparse
import json
import os
import re
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree


def coordinate(value):
    try:
        x, y = (int(part) for part in value.split(","))
    except ValueError as exc:
        raise argparse.ArgumentTypeError("expected x,y in screen pixels") from exc
    if x < 0 or y < 0:
        raise argparse.ArgumentTypeError("coordinates must be nonnegative")
    return x, y


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sdk = os.environ.get("ANDROID_HOME")
    parser.add_argument("--adb", default=str(Path(sdk, "platform-tools", "adb.exe")) if sdk else "adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--ime", required=True)
    parser.add_argument("--editor-package", required=True)
    parser.add_argument("--focus-hint")
    parser.add_argument("--key-b", type=coordinate, required=True)
    parser.add_argument("--key-backspace", type=coordinate, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    def adb(*command, allowed=(0,)):
        result = subprocess.run(
            [args.adb, "-s", args.serial, *command], capture_output=True, check=False, timeout=35,
        )
        if result.returncode not in allowed:
            raise RuntimeError(f"adb {' '.join(command)}: {result.stderr.decode(errors='replace')}")
        return result.stdout

    def active_ime():
        return adb("shell", "settings", "get", "secure", "default_input_method").decode().strip()

    def capture_editor(label):
        xml = "/data/local/tmp/bbkb-smoke.xml"
        adb("shell", "rm", "-f", xml)
        adb("shell", "uiautomator", "dump", xml)
        tree = adb("exec-out", "cat", xml)
        (args.output / f"{label}.xml").write_bytes(tree)
        (args.output / f"{label}.png").write_bytes(adb("exec-out", "screencap", "-p"))
        return [
            node for node in ElementTree.fromstring(tree).iter("node")
            if node.get("package") == args.editor_package and node.get("class") == "android.widget.EditText"
        ]

    def focused_editor(label):
        for node in capture_editor(label):
            if node.get("focused") == "true":
                return node.get("text")
        raise RuntimeError(f"Focused EditText missing from {label}.xml")

    if active_ime() != args.ime:
        parser.error("the requested IME must be selected before running smoke")
    if args.focus_hint:
        fields = [node for node in capture_editor("editor") if node.get("text") == args.focus_hint]
        if len(fields) != 1:
            raise RuntimeError(f"Expected one visible, empty editor with hint {args.focus_hint!r}")
        bounds = re.findall(r"\d+", fields[0].get("bounds", ""))
        left, top, right, bottom = map(int, bounds)
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
        time.sleep(1)
    focused_editor("before")
    results = []
    steps = (
        ("type-bb", [args.key_b, args.key_b], "bb"),
        ("type-bbb", [args.key_b], "bbb"),
        ("backspace", [args.key_backspace], "bb"),
    )
    for name, taps, expected in steps:
        for x, y in taps:
            adb("shell", "input", "tap", str(x), str(y))
        actual = focused_editor(name)
        result = {
            "scenario": name, "expected": expected, "actual": actual,
            "status": "PASS" if actual == expected and active_ime() == args.ime else "FAIL",
            "evidence": [f"{name}.xml", f"{name}.png"],
        }
        results.append(result)
        if result["status"] == "FAIL":
            break
    package = args.ime.split("/", 1)[0]
    log = adb("logcat", "-d", "-b", "crash")
    (args.output / "logcat-crash.txt").write_bytes(log)
    memory = adb("shell", "dumpsys", "meminfo", package)
    (args.output / "meminfo.txt").write_bytes(memory)
    report = {"ime": args.ime, "serial": args.serial, "results": results,
              "ime_running": bool(adb("shell", "pidof", package, allowed=(0, 1)).strip())}
    (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))
    return int(not report["ime_running"] or any(row["status"] == "FAIL" for row in results))


if __name__ == "__main__":
    raise SystemExit(main())
