#!/usr/bin/env bash
# Hermetic app state for e2e runs (rooted emulator).
#   scripts/e2e/app_state.sh fresh     # pm clear, run onboarding with Crashlytics OFF, save baseline
#   scripts/e2e/app_state.sh save      # save the current /data/data state as the baseline
#   scripts/e2e/app_state.sh restore   # put the baseline back (features.sh and monkey.sh do this)
# Why: fuzzing (monkey.sh) and manual poking change Sync's settings (e.g. post layout card → list),
# and UI scenarios depend on them. Baseline: out/appstate/baseline.tgz (local only, no tokens: logged out).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
PKG=com.laurencedawson.reddit_sync
BASE=$ROOT/out/appstate/baseline.tgz
UI="python3 -I $ROOT/scripts/e2e/ui.py"
PREFS=/data/data/$PKG/shared_prefs/${PKG}_preferences.xml
mkdir -p "$(dirname "$BASE")"

save() {
  adb shell am force-stop $PKG
  adb shell su 0 tar -czf /data/local/tmp/sync-baseline.tgz -C /data/data $PKG --exclude="$PKG/cache" --exclude="$PKG/code_cache"
  adb pull /data/local/tmp/sync-baseline.tgz "$BASE" >/dev/null
  adb shell rm -f /data/local/tmp/sync-baseline.tgz
  echo "saved $BASE"
}

restore() {
  [ -f "$BASE" ] || { echo "no baseline; run: $0 fresh"; exit 1; }
  adb shell am force-stop $PKG
  local uid; uid=$(adb shell stat -c %u /data/data/$PKG)
  adb push "$BASE" /data/local/tmp/sync-baseline.tgz >/dev/null
  adb shell su 0 sh -c "'rm -rf /data/data/$PKG/* && tar -xzf /data/local/tmp/sync-baseline.tgz -C /data/data && chown -R $uid:$uid /data/data/$PKG && restorecon -R /data/data/$PKG'"
  adb shell rm -f /data/local/tmp/sync-baseline.tgz
  adb shell su 0 grep -q "'name=\"tracking_fabric\" value=\"false\"'" "$PREFS" || { echo "FAIL: baseline has Crashlytics on"; exit 1; }
  echo "restored $BASE"
}

fresh() {
  adb shell pm clear $PKG >/dev/null
  adb shell am start -W -n $PKG/.ui.activities.HomeActivity.Default >/dev/null
  sleep 6
  for _ in $(seq 1 12); do
    if $UI wait "^Crashlytics$" 1; then
      # Onboarding settings page: switch Crashlytics off before leaving it (project rule).
      $UI tap "^Crashlytics$" 2; sleep 1
    fi
    $UI tap "^(Next|Accept|Finish)$" 3 || break
    sleep 2
  done
  adb shell am force-stop $PKG
  # Belt and braces: the onboarding switch writes this pref; make sure it is false.
  if ! adb shell su 0 grep -q "'name=\"tracking_fabric\" value=\"false\"'" "$PREFS"; then
    adb shell su 0 sed -i "'/tracking_fabric/d; s#<map>#<map>\n    <boolean name=\"tracking_fabric\" value=\"false\" />#'" "$PREFS"
  fi
  adb shell su 0 grep "tracking_fabric" "$PREFS"
  save
}

case "${1:-}" in
  save) save;; restore) restore;; fresh) fresh;;
  *) echo "usage: $0 fresh|save|restore"; exit 2;;
esac
