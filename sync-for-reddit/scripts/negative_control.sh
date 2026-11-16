#!/usr/bin/env bash
# Prove that e2e scenarios catch a regression: build an older commit of the patches, install it,
# and require every named scenario to FAIL there. Then reinstall the current debug build.
#   scripts/negative_control.sh <git-ref> <scenario> [scenario ...]
# Example (MP4 audio fix):  scripts/negative_control.sh d1b55db19 reddit_mp4_audio
# The ref is exported with `git archive` into out/negctl/ (no worktree, the main tree is untouched)
# and must be a build that predates the fix the scenarios cover. Run after verify_patch.sh --debug.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
sync_require_emulator
cd "$ROOT"; source secrets.env
[ $# -ge 2 ] || { echo "usage: $0 <git-ref> <scenario>..."; exit 2; }
REF=$1; shift
SHA=$(git -C "$SYNC_REPO" rev-parse --verify "$REF^{commit}")
# Negative controls must use the selected device, never the shard pool.
unset SYNC_EMULATORS
export SYNC_FEATURE_WORKER=1
CUR=out/sync-patched-debug.apk
[ -f "$CUR" ] || { echo "FAIL: $CUR missing; run scripts/verify_patch.sh --debug first"; exit 1; }
SRC=out/negctl/src; APK=out/negctl/cache/$SHA.apk
mkdir -p out/negctl/cache
# Serialize the archive, patch log and cache publication.
NEGLOCK=out/negctl/.lock
mkdir "$NEGLOCK" 2>/dev/null || { echo "FAIL: another negative control is running"; exit 1; }
trap 'rmdir "$NEGLOCK"' EXIT
if [ ! -s "$APK" ]; then
rm -rf "$SRC"; mkdir -p "$SRC"
git -C "$SYNC_REPO" archive "$SHA" | tar -x -C "$SRC"
echo "== build $REF"
( cd "$SRC" && \
  ORG_GRADLE_PROJECT_githubPackagesUsername=$(gh api user -q .login) \
  ORG_GRADLE_PROJECT_githubPackagesPassword=$(gh auth token) \
  ./gradlew :patches:build -x test -q ) > out/negctl/build.log 2>&1 || { echo "FAIL: gradle build of $REF (out/negctl/build.log)"; exit 1; }
RVP=$(ls "$SRC"/patches/build/libs/patches-*.rvp | grep -v sources | head -1)
java -jar tools/revanced-cli-6.0.0-all.jar patch -p "$RVP" -b \
  -e "Spoof client" -O "OAuth client ID=$REDDIT_CLIENT_ID" -e "Enable debug logging" \
  --keystore out/sync.keystore -o "$APK.tmp.apk" --purge apk/sync-23.06.30.apk > out/negctl/patch.log 2>&1 \
  || { echo "FAIL: patching with $REF (out/negctl/patch.log)"; exit 1; }
mv "$APK.tmp.apk" "$APK"
else
  echo "== cached $SHA"
fi
restore() {
  echo "== reinstall current build"
  adb install -r "$CUR" >/dev/null || echo "WARN: reinstall of $CUR failed"   # -r keeps app data/prefs
}
trap 'restore; rmdir "$NEGLOCK"' EXIT
echo "== install $REF"
adb install -r "$APK" >/dev/null
fail=0
for s in "$@"; do
  # Only a scenario assertion failure ("FAIL <scenario>: ...") counts as detecting the regression.
  # Setup problems (unknown scenario, no network, adb/state errors, crashes in the harness) are inconclusive.
  if "$ROOT/scripts/e2e/features.sh" "$s" > "out/negctl/$s.log" 2>&1; then
    echo "FAIL $s passed on $REF: the scenario does not detect the regression"; fail=1
  elif grep -q "^FAIL $s: " "out/negctl/$s.log" && ! grep -qE "^Traceback|unknown scenario|^FAIL: " "out/negctl/$s.log"; then
    echo "ok   $s fails on $REF: $(grep -m1 "^FAIL $s: " "out/negctl/$s.log" | cut -c1-160)"
  else
    echo "FAIL $s inconclusive on $REF (setup error, see out/negctl/$s.log): $(grep -m1 -E 'FAIL|Error|Traceback|unknown' "out/negctl/$s.log" | cut -c1-160)"; fail=1
  fi
done
[ $fail = 0 ] && echo "PASS: every scenario caught $REF"
exit $fail
