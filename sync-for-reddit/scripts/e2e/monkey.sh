#!/usr/bin/env bash
# Random-input fuzz of the installed (debug) build with host capture.
#   scripts/e2e/monkey.sh [events=5000] [seed=4242]
# Fails on: a Sync crash/ANR in logcat, the process not surviving, or any connection to a dead or
# telemetry host from scripts/e2e/features.py's DEAD_HOSTS (app-measurement.com is excluded there
# because the capture is device-wide and Play services uploads other apps' analytics).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
PKG=com.laurencedawson.reddit_sync
EVENTS=${1:-5000}; SEED=${2:-4242}
HOSTS=$(mktemp "${TMPDIR:-/tmp}/sync-monkey-hosts.XXXXXX")
"$ROOT/scripts/e2e/netcap.sh" start "$HOSTS" >/dev/null
# Monkey taps through Settings at random; put the baseline back afterwards so later suites are stable.
"$ROOT/scripts/e2e/app_state.sh" restore >/dev/null 2>&1
# Monkey also reaches the quick-settings shade and toggles airplane mode, Do Not Disturb, Wi-Fi.
reset_device() {
  adb shell cmd statusbar collapse >/dev/null 2>&1 || true
  adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true
  adb shell cmd notification set_dnd off >/dev/null 2>&1 || true
  adb shell svc wifi enable >/dev/null 2>&1 || true
  adb shell svc data enable >/dev/null 2>&1 || true
  adb shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
  adb shell settings put system user_rotation 0 >/dev/null 2>&1 || true
}
trap 'reset_device; "$ROOT/scripts/e2e/netcap.sh" stop; "$ROOT/scripts/e2e/app_state.sh" restore >/dev/null 2>&1' EXIT
adb logcat -c; adb logcat -b crash -c
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.ui.activities.HomeActivity.Default >/dev/null
sleep 5
# --pct-syskeys 0: don't send HOME/BACK storms that leave the app; keep the fuzz inside Sync.
adb shell monkey -p $PKG -s "$SEED" --throttle 150 --pct-syskeys 0 --ignore-security-exceptions \
  -v "$EVENTS" > "${TMPDIR:-/tmp}/sync-monkey.log" 2>&1 || true
grep -E "Events injected" "${TMPDIR:-/tmp}/sync-monkey.log" || true
fail=0
crash=$(adb logcat -d -b crash | grep -F "$PKG" || true)
anr=$(adb logcat -d | grep -E "ANR in $PKG" || true)
mcrash=$(grep -E "// CRASH: $PKG|// NOT RESPONDING: $PKG" "${TMPDIR:-/tmp}/sync-monkey.log" || true)
[ -n "$crash$anr$mcrash" ] && { echo "FAIL crash/ANR:"; printf '%s\n%s\n%s\n' "$crash" "$anr" "$mcrash" | head -20; fail=1; }
dead=$("$ROOT/.venv/bin/python" -I - "$HOSTS" "$ROOT/scripts/e2e/features.py" <<'EOF'
import sys, re, pathlib
src = pathlib.Path(sys.argv[2]).read_text()
dead = eval(re.search(r"DEAD_HOSTS = (\[.*?\])", src, re.S).group(1))
hosts = {l.split()[2].rsplit(":", 1)[0] for l in open(sys.argv[1]) if len(l.split()) > 2}
print(" ".join(sorted(h for h in hosts if any(d in h for d in dead))))
EOF
)
[ -n "$dead" ] && { echo "FAIL dead/telemetry hosts: $dead"; fail=1; }
[ $fail = 0 ] && echo "PASS monkey ($EVENTS events, seed $SEED)"
exit $fail
