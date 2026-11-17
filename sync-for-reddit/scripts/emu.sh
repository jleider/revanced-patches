#!/usr/bin/env bash
# Emulator helpers: emu.sh shot <name> | ui | tap <x> <y> | text <str> | key <code> | find <regex>
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
sync_require_emulator
S="${SHOTS:-${TMPDIR:-/tmp}/sync-shots}"; mkdir -p "$S"
case "$1" in
  shot) adb exec-out screencap -p > "$S/$2.png" && echo "$S/$2.png";;
  ui)   adb shell uiautomator dump /sdcard/ui.xml >/dev/null && adb shell cat /sdcard/ui.xml | python3 -c '
import sys,re
for m in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    n=m.group(0); g=lambda k:(re.search(k+r"=\"([^\"]*)\"",n) or [None,""])[1]
    t,d,r,b=g("text"),g("content-desc"),g("resource-id").split("/")[-1],g("bounds")
    if t or d or g("clickable")=="true":
        x1,y1,x2,y2=map(int,re.findall(r"\d+",b)); print(f"({(x1+x2)//2},{(y1+y2)//2}) id={r} text={t!r} desc={d!r}")';;
  tap)  adb shell input tap "$2" "$3";;
  text) adb shell input text "$2";;
  key)  adb shell input keyevent "$2";;
  *) echo "usage: shot|ui|tap|text|key";;
esac
