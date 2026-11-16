#!/usr/bin/env bash
# Emulator smoke test for a patched Sync build. Needs a debug build (scripts/verify_patch.sh --debug)
# so Sync's internal logger reaches logcat.
#
#   scripts/smoke.sh [scenario ...]      # default: all scenarios
#
# Scenarios open real content through deep links (no screen-coordinate taps), then check:
#   - the app process is still alive and nothing for the package hit the crash buffer
#   - Sync logged at least one request URL
#   - no Reddit block page (HTML 403) or "Could not load gallery" appeared in Sync's logs
# Post IDs come from tests/fixtures (refreshed by `scripts/probe_endpoints.py --save`).
# Screenshots: $SHOTS/smoke-<scenario>.png
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
PKG=com.laurencedawson.reddit_sync
SHOTS=${SHOTS:-${TMPDIR:-/tmp}/sync-shots}
mkdir -p "$SHOTS"

fix() { python3 -I -c "import json,sys;d=json.load(open('$ROOT/tests/fixtures/$1'));print($2)"; }
GALLERY_ID=$(fix gallery_comments_endpoint.json "d[0]['data']['children'][0]['data']['id']")
GALLERY_SUB=$(fix gallery_comments_endpoint.json "d[0]['data']['children'][0]['data']['subreddit']")
THREAD_ID=$(fix more_stub.json "d['link_id']")

declare -A URLS=(
  [frontpage]=""
  [thread]="https://www.reddit.com/comments/$THREAD_ID"
  [gallery]="https://www.reddit.com/r/$GALLERY_SUB/comments/$GALLERY_ID?is_gallery=true"
  [video]="${SMOKE_VIDEO_URL:-https://v.redd.it/h3m7jsx8es1i1}"
)
# Optional log line (regex) a scenario must produce to pass, beyond "no errors".
declare -A EXPECT=(
  [video]="MP4 loaded. Starting MP4 playback"
)
# UI element (regex over text/desc/id, via scripts/e2e/ui.py) that must be on screen to pass.
# Guards against false passes where the intent silently went nowhere.
declare -A EXPECT_UI=(
  [thread]="comment_row_content"
  [gallery]="^[0-9]+ / [0-9]+$"   # may resume at a saved position
)
# Foreground activity (regex) required to pass. Used where autoplaying video stops
# uiautomator from producing a dump.
declare -A EXPECT_ACTIVITY=(
  [frontpage]="reddit_sync/\\.ui\\.activities\\.(MainActivity|HomeActivity)"
  [gallery]="MultiImageActivity"
  [video]="SingleImageActivity"
)
ORDER=(frontpage thread gallery video)
[ $# -gt 0 ] && ORDER=("$@")

adb get-state >/dev/null 2>&1 || { echo "FAIL: no emulator/device"; exit 1; }
[ "${KEEP_STATE:-0}" = 1 ] || "$ROOT/scripts/e2e/app_state.sh" restore >/dev/null 2>&1 || { echo "FAIL: app_state.sh restore (run it by hand to see why)"; exit 1; }
pass=0; fail=0
# Don't leave a looping video (software decode) burning emulator CPU after the run.
trap 'adb shell am force-stop $PKG >/dev/null 2>&1 || true' EXIT
for s in "${ORDER[@]}"; do
  adb logcat -c; adb logcat -b crash -c
  adb shell am force-stop $PKG     # every scenario starts cold so intents can't land on a stale screen
  if [ -z "${URLS[$s]}" ]; then
    adb shell am start -W -n $PKG/.ui.activities.HomeActivity.Default >/dev/null
  else
    adb shell am start -W -a android.intent.action.VIEW -d "'${URLS[$s]}'" -p $PKG >/dev/null
  fi
  sleep 8
  # Close Sync's "Reddit servers are having issues" sheet (reddit.statuspage.io incident).
  python3 -I "$ROOT/scripts/e2e/ui.py" wait "servers are having issues" 1 && python3 -I "$ROOT/scripts/e2e/ui.py" tap "^Close$" 2 && sleep 1
  adb exec-out screencap -p > "$SHOTS/smoke-$s.png"
  log=$(adb logcat -d -s reddit_sync:D URL:D RedditRequest:D)
  problems=()
  adb shell pidof $PKG >/dev/null || problems+=("process died")
  adb logcat -d -b crash | grep -q "$PKG" && problems+=("crash: $(adb logcat -d -b crash | grep -m1 -A2 'FATAL' | tr '\n' ' ' | cut -c1-200)")
  urls=$(grep -c " URL: " <<<"$log")
  # The feed can be served from Sync's DB cache, so only require network activity for deep links.
  if [ -n "${URLS[$s]}" ] && [ "$urls" -eq 0 ]; then problems+=("no request URLs logged (is this a --debug build?)"); fi
  grep -q "theme-beta" <<<"$log" && problems+=("Reddit block page (403) in a response")
  grep -q "Could not load gallery" <<<"$log" && problems+=("gallery load failed")
  top=$(adb shell dumpsys activity activities | grep -m1 topResumedActivity)
  if [ -n "${EXPECT_ACTIVITY[$s]:-}" ] && ! grep -qE "${EXPECT_ACTIVITY[$s]}" <<<"$top"; then problems+=("wrong screen: $(sed 's/.*reddit_sync//;s/ t[0-9]*}//' <<<"$top")"); fi
  if [ -n "${EXPECT_UI[$s]:-}" ] && ! python3 -I "$ROOT/scripts/e2e/ui.py" wait "${EXPECT_UI[$s]}" 5; then problems+=("expected UI not found: ${EXPECT_UI[$s]}"); fi
  if [ -n "${EXPECT[$s]:-}" ] && ! grep -qE "${EXPECT[$s]}" <<<"$log"; then problems+=("expected log not seen: ${EXPECT[$s]}"); fi
  if [ ${#problems[@]} -eq 0 ]; then
    echo "PASS $s ($urls requests)"; pass=$((pass+1))
  else
    echo "FAIL $s:"; printf '     - %s\n' "${problems[@]}"; fail=$((fail+1))
    grep " URL: " <<<"$log" | sed 's/.* URL: /     url: /' | tail -5
  fi
  echo "     screenshot: $SHOTS/smoke-$s.png"
done
echo "$pass passed, $fail failed"
[ $fail = 0 ]
