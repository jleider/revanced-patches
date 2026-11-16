#!/usr/bin/env bash
# Build the patch bundle, patch Sync, and verify the result.
#
#   scripts/verify_patch.sh [--debug] [--no-build]
#
# Checks (any failure exits non-zero):
#   1. Gradle build of revanced-patches succeeds.
#   2. Every patch in $EXPECTED_PATCHES reports "succeeded"; none report failure.
#   3. Only classes listed in tests/expected-changes.txt differ from the original APK's smali
#      (catches a fingerprint that matched the wrong method). Unexpected changes are printed.
#   0. scripts/unit_tests.sh (offline extension checks) passes.
#   5. With an emulator: ART verifies every class (scripts/art_verify.sh).
#   4. The patched APK verifies with apksigner and installs on the emulator (if one is running).
#
# Output: out/sync-patched.apk (or out/sync-patched-debug.apk with --debug)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
cd "$ROOT"
source secrets.env

# Serialize runs: concurrent builds share out/ and corrupt each other's APKs.
LOCK=out/.verify.lock
mkdir -p out
# The lock dir holds the owner's PID; a lock whose owner is gone (killed run) is taken over.
got=0
for i in $(seq 1 600); do
  if mkdir "$LOCK" 2>/dev/null; then got=1; break; fi
  owner=$(cat "$LOCK/pid" 2>/dev/null || true)
  if [ -n "$owner" ] && ! kill -0 "$owner" 2>/dev/null; then
    echo "removing stale lock (pid $owner gone)"; rm -rf "$LOCK"; continue
  fi
  [ $i = 1 ] && echo "waiting for another verify_patch.sh (pid ${owner:-?}, lock $LOCK)"
  sleep 2
done
[ $got = 1 ] || { echo "FAIL: timed out waiting for $LOCK"; exit 1; }   # never touch a lock we don't own
echo $$ > "$LOCK/pid"
trap 'rm -rf "$LOCK"' EXIT
DEBUG=0; BUILD=1; INSTALL=1
for a in "$@"; do case $a in --debug) DEBUG=1;; --no-build) BUILD=0;; --no-install) INSTALL=0;; esac; done

RVP=$(ls "$SYNC_REPO"/patches/build/libs/patches-*.rvp 2>/dev/null | grep -v -e sources -e javadoc | head -1)
OUT=out/sync-patched$([ $DEBUG = 1 ] && echo -debug || true).apk
LOG=out/patch.log
mkdir -p out

echo "== unit"
"$ROOT/scripts/unit_tests.sh" >out/unit.log 2>&1 && grep -E "^PASS" out/unit.log | sed 's/^/  /' \
  || { echo "FAIL: unit tests (see out/unit.log)"; grep -E "FAIL|Exception|error" out/unit.log | head -10; exit 1; }

if [ $BUILD = 1 ]; then
  echo "== build"
  ( cd "$SYNC_REPO" && \
    ORG_GRADLE_PROJECT_githubPackagesUsername=$(gh api user -q .login) \
    ORG_GRADLE_PROJECT_githubPackagesPassword=$(gh auth token) \
    ./gradlew :patches:build -x test -q ) || { echo "FAIL: gradle build"; exit 1; }
fi

# Patches we expect to apply. Keep in sync with what we ship.
EXPECTED_PATCHES=(
  "Spoof client" "Disable ads" "Disable Sync for Lemmy bottom sheet" "Fix /s/ links"
  "Fix Redgifs API" "Fix post thumbnails" "Fix video downloads"
  "Fix login redirect" "Fix galleries" "Fix Imgur" "Fix load more comments" "Disable telemetry" "Fix combined subreddit feeds" "Fix other discussions" "Fix random subreddit"
  "Fix DeviantArt embeds" "Hide restore comment" "Fix link previews"
  "Fix Gfycat" "Fix Reddit image URLs" "Upgrade connections to HTTPS"
)
EXTRA=()
[ -n "${EXTRA_PATCHES:-}" ] && IFS=',' read -ra EXTRA <<< "$EXTRA_PATCHES"
[ $DEBUG = 1 ] && EXTRA+=("Enable debug logging")
ENABLE_ARGS=()
for p in "${EXTRA[@]}"; do ENABLE_ARGS+=(-e "$p"); EXPECTED_PATCHES+=("$p"); done
OPT_ARGS=(-O "OAuth client ID=$REDDIT_CLIENT_ID")

echo "== patch"
# NB: in revanced-cli 6, -O options bind to the -e that precedes them.
java -jar tools/revanced-cli-6.0.0-all.jar patch -p "$RVP" -b \
  -e "Spoof client" "${OPT_ARGS[@]}" "${ENABLE_ARGS[@]}" \
  --keystore out/sync.keystore -o "$OUT" --purge apk/sync-23.06.30.apk >"$LOG" 2>&1 \
  || { echo "FAIL: revanced-cli exited non-zero (see $LOG)"; tail -20 "$LOG"; exit 1; }

fail=0
for p in "${EXPECTED_PATCHES[@]}"; do
  if grep -qF "\"$p\" succeeded" "$LOG"; then echo "  ok   $p"; else echo "  FAIL $p (not applied)"; fail=1; fi
done
if grep -qE "SEVERE|failed|Exception" "$LOG"; then
  echo "  FAIL patcher reported errors:"; grep -E "SEVERE|failed|Exception" "$LOG" | head -10; fail=1
fi

echo "== smali diff"
rm -rf out/patched-smali
apktool d -f -r -o out/patched-smali "$OUT" >/dev/null 2>&1
ORIG=decompiled/sync-23.06.30-smali
# Compare by class path, ignoring which classesN.dex a class landed in.
python3 -I - "$ORIG" out/patched-smali tests/expected-changes.txt <<'EOF' || fail=1
import sys, pathlib, hashlib, fnmatch, re
orig, new, allow_file = map(pathlib.Path, sys.argv[1:4])
def index(root):
    m = {}
    for f in root.glob("smali*/**/*.smali"):
        rel = str(f.relative_to(root)).split("/", 1)[1]
        # Normalise re-dexing noise: the patcher drops redundant static-field default
        # values (`= false`, `= 0x0`, `= null`) when it rewrites a dex.
        t = re.sub(r"^(\.field [^\n]*?) = (false|0x0|0x0L|0x0t|0x0s|null|0\.0f|0\.0)$", r"\1", f.read_text(), flags=re.M)
        m[rel] = hashlib.sha1(t.encode()).hexdigest()
    return m
a, b = index(orig), index(new)
changed = sorted(k for k in a.keys() & b.keys() if a[k] != b[k])
added = sorted(b.keys() - a.keys())
removed = sorted(a.keys() - b.keys())
allow = [l.split("#")[0].strip() for l in allow_file.read_text().splitlines()] if allow_file.exists() else []
allow = [l for l in allow if l]
ok = lambda k: any(fnmatch.fnmatch(k, pat) for pat in allow)
unexpected = [("changed", k) for k in changed if not ok(k)] + [("added", k) for k in added if not ok(k)] + [("removed", k) for k in removed]
print(f"  {len(changed)} changed, {len(added)} added, {len(removed)} removed classes")
for kind, k in unexpected:
    print(f"  UNEXPECTED {kind}: {k}")
pathlib.Path("out/changed-classes.txt").write_text("\n".join([f"changed {k}" for k in changed] + [f"added {k}" for k in added]) + "\n")
sys.exit(1 if unexpected else 0)
EOF

echo "== reachability"
python3 -I "$ROOT/scripts/check_reachability.py" out/patched-smali || fail=1

echo "== extension api"
# The extension compiles against upstream OkHttp; Sync ships a minified 5.0.0-alpha.8 (okio renamed).
python3 -I "$ROOT/scripts/test_check_extension_api.py" >/dev/null || { echo "  FAIL: checker self-test"; fail=1; }
python3 -I "$ROOT/scripts/check_extension_api.py" out/patched-smali || fail=1

echo "== manifest"
AAPT2=$(ls -d "$ANDROID_HOME"/build-tools/* | tail -1)/aapt2
MANIFEST=$("$AAPT2" dump xmltree --file AndroidManifest.xml "$OUT")
for flag in firebase_crashlytics_collection_enabled=false firebase_analytics_collection_deactivated=true; do
  name=${flag%=*}; want=${flag#*=}
  if grep -A1 "\"$name\"" <<<"$MANIFEST" | grep -q "value(0x01010024)=$want"; then echo "  ok   $flag"
  else echo "  FAIL $flag missing (Disable telemetry)"; fail=1; fi
done

echo "== signature"
apksigner verify "$OUT" >/dev/null 2>&1 && echo "  ok   apksigner" || { echo "  FAIL apksigner"; fail=1; }

if [ $INSTALL = 1 ] && adb get-state >/dev/null 2>&1; then
  echo "== install"
  adb install -r "$OUT" >/dev/null && echo "  ok   installed on $(adb get-serialno)" || { echo "  FAIL install"; fail=1; }
  echo "== art verifier"
  "$ROOT/scripts/art_verify.sh" "$OUT" || { echo "  FAIL ART verification (see above)"; fail=1; }
fi

[ $fail = 0 ] && echo "PASS: $OUT" || { echo "FAIL (details: $LOG, out/changed-classes.txt)"; exit 1; }
