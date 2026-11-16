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
cd "$ROOT"; source secrets.env
[ $# -ge 2 ] || { echo "usage: $0 <git-ref> <scenario>..."; exit 2; }
REF=$1; shift
CUR=out/sync-patched-debug.apk
[ -f "$CUR" ] || { echo "FAIL: $CUR missing; run scripts/verify_patch.sh --debug first"; exit 1; }
SRC=out/negctl/src; APK=out/negctl/sync-negctl-debug.apk
rm -rf out/negctl; mkdir -p "$SRC"
git -C "$SYNC_REPO" archive "$REF" | tar -x -C "$SRC"
echo "== build $REF"
( cd "$SRC" && \
  ORG_GRADLE_PROJECT_githubPackagesUsername=$(gh api user -q .login) \
  ORG_GRADLE_PROJECT_githubPackagesPassword=$(gh auth token) \
  ./gradlew :patches:build -x test -q ) > out/negctl/build.log 2>&1 || { echo "FAIL: gradle build of $REF (out/negctl/build.log)"; exit 1; }
RVP=$(ls "$SRC"/patches/build/libs/patches-*.rvp | grep -v sources | head -1)
java -jar tools/revanced-cli-6.0.0-all.jar patch -p "$RVP" -b \
  -e "Spoof client" -O "OAuth client ID=$REDDIT_CLIENT_ID" -e "Enable debug logging" \
  --keystore out/sync.keystore -o "$APK" --purge apk/sync-23.06.30.apk > out/negctl/patch.log 2>&1 \
  || { echo "FAIL: patching with $REF (out/negctl/patch.log)"; exit 1; }
restore() {
  echo "== reinstall current build"
  adb install -r "$CUR" >/dev/null || echo "WARN: reinstall of $CUR failed"   # -r keeps app data/prefs
}
trap restore EXIT
echo "== install $REF"
adb install -r "$APK" >/dev/null
fail=0
for s in "$@"; do
  if "$ROOT/scripts/e2e/features.sh" "$s" > "out/negctl/$s.log" 2>&1; then
    echo "FAIL $s passed on $REF: the scenario does not detect the regression"; fail=1
  else
    echo "ok   $s fails on $REF: $(grep -m1 '^FAIL' "out/negctl/$s.log" | cut -c1-160)"
  fi
done
[ $fail = 0 ] && echo "PASS: every scenario caught $REF"
exit $fail
