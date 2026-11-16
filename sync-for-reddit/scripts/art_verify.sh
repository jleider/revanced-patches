#!/usr/bin/env bash
# Run ART's bytecode verifier (dex2oat --compiler-filter=verify) on the original and patched APK on the
# rooted emulator, then compare per-class verification status with oatdump. Fails if any class that
# verified in the original no longer does, or if any added class (extensions) fails verification.
# Catches bad register types / uninitialised registers / bad invokes from smali injections, even in
# code no test reaches.
#   scripts/art_verify.sh [patched.apk]     (default out/sync-patched-debug.apk)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
PATCHED=${1:-$ROOT/out/sync-patched-debug.apk}
adb root >/dev/null 2>&1; adb wait-for-device
T=/data/local/tmp/artverify; adb shell "rm -rf $T; mkdir -p $T"
adb push "$ROOT/apk/sync-23.06.30.apk" $T/o.apk >/dev/null
adb push "$PATCHED" $T/p.apk >/dev/null
WORK=$(mktemp -d)
for a in o p; do
  adb shell "/apex/com.android.art/bin/dex2oat64 --dex-file=$T/$a.apk --dex-location=$T/$a.apk --oat-file=$T/$a.odex \
    --compiler-filter=verify --instruction-set=arm64 -j4 --runtime-arg -Xms64m --runtime-arg -Xmx512m >/dev/null 2>&1; echo \$?" \
    | grep -qx 0 || { echo "FAIL: dex2oat on $a.apk"; exit 1; }
  adb shell "/apex/com.android.art/bin/oatdump --oat-file=$T/$a.odex --no-disassemble --list-classes 2>/dev/null" > "$WORK/$a.txt"
done
adb shell "rm -rf $T"
python3 -I - "$WORK" <<'PY'
import re, sys, collections
w = sys.argv[1]
OK = {"Verified", "VerifiedNeedsAccessChecks"}
def load(a):
    d = {}
    for line in open(f"{w}/{a}.txt"):
        m = re.match(r"\d+: (L[^;]+;) .*?\((\w+)\) \(\w+\)\s*$", line.strip())
        if m: d[m.group(1)] = m.group(2)
    return d
o, p = load("o"), load("p")
if len(o) < 1000 or len(p) < 1000: sys.exit("FAIL: oatdump class list looks empty")
bad = [(c, o.get(c, "(added)"), s) for c, s in p.items() if s not in OK and o.get(c) != s]
print(f"  {len(p)} classes: {dict(collections.Counter(p.values()))}")
for c, before, after in bad: print(f"  FAIL {c}: {before} -> {after}")
sys.exit(1 if bad else 0)
PY
