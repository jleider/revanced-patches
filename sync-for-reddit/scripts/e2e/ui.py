#!/usr/bin/env python3
"""Tiny uiautomator driver for e2e scripts. Elements are matched by regex against
text, content-desc or resource-id, never by hard-coded coordinates.

  ui.py wait  <regex> [timeout_s]   exit 0 when a matching node is on screen
  ui.py tap   <regex> [timeout_s]   wait for the node, tap its centre
  ui.py gone  <regex> [timeout_s]   exit 0 when no matching node is on screen
  ui.py dump                        print visible nodes
"""
import os
import re
import shutil
import subprocess
import sys
import time

ADB = shutil.which("adb") or os.path.join(os.environ.get("ANDROID_HOME", ""), "platform-tools", "adb")

SERIAL = os.environ.get('ANDROID_SERIAL', '')
if not re.fullmatch(r'emulator-\d+', SERIAL):
    sys.exit('FAIL: ANDROID_SERIAL must explicitly select an emulator')
if not any(subprocess.run([ADB, '-s', SERIAL, 'shell', 'getprop', prop],
                          capture_output=True, text=True, check=True).stdout.strip() == '1'
           for prop in ('ro.kernel.qemu', 'ro.boot.qemu')):
    sys.exit('FAIL: selected device is not an emulator')


def nodes():
    for _ in range(3):
        subprocess.run([ADB, "-s", SERIAL, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
        xml = subprocess.run([ADB, "-s", SERIAL, "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout
        if "<node" in xml:
            break
        time.sleep(1)
    out = []
    for m in re.finditer(r"<node [^>]*>", xml):
        n = m.group(0)
        g = lambda k: (re.search(k + r'="([^"]*)"', n) or [None, ""])[1]
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", g("bounds")))
        out.append({"text": g("text"), "desc": g("content-desc"), "id": g("resource-id"),
                    "x": (x1 + x2) // 2, "y": (y1 + y2) // 2, "w": x2 - x1, "h": y2 - y1})
    return out


def find(rx):
    r = re.compile(rx, re.I)
    for n in nodes():
        if n["w"] > 0 and n["h"] > 0 and (r.search(n["text"]) or r.search(n["desc"]) or r.search(n["id"])):
            return n
    return None


def wait(rx, timeout):
    end = time.time() + timeout
    while time.time() < end:
        n = find(rx)
        if n:
            return n
        time.sleep(1)
    return None


def main():
    cmd, *args = sys.argv[1:] or ["dump"]
    if cmd == "dump":
        for n in nodes():
            if n["text"] or n["desc"]:
                print(f"({n['x']},{n['y']}) id={n['id'].split('/')[-1]} text={n['text']!r} desc={n['desc']!r}")
        return
    rx, timeout = args[0], float(args[1]) if len(args) > 1 else 15
    if cmd == "wait":
        sys.exit(0 if wait(rx, timeout) else 1)
    if cmd == "tap":
        n = wait(rx, timeout)
        if not n:
            print(f"ui.py: no node matching {rx!r}", file=sys.stderr)
            sys.exit(1)
        subprocess.run([ADB, "-s", SERIAL, "shell", "input", "tap", str(n["x"]), str(n["y"])])
        return
    if cmd == "gone":
        end = time.time() + timeout
        while time.time() < end:
            if not find(rx):
                return
            time.sleep(1)
        sys.exit(1)
    sys.exit(f"unknown command {cmd}")


if __name__ == "__main__":
    main()
