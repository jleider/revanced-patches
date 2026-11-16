#!/usr/bin/env bash
# Offline JVM unit checks for the Sync extension helpers (no emulator, no network).
# Compiles extensions/syncforreddit main sources + src/test harnesses with plain javac against
# android.jar, org.json and OkHttp, with the JVM stubs in src/test shadowing android.jar.
#   scripts/unit_tests.sh            # run all harnesses; non-zero exit on any failure
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
EXT=$SYNC_REPO/extensions/syncforreddit/src
OUT=${TMPDIR:-/tmp}/sync-unit-tests
rm -rf "$OUT"; mkdir -p "$OUT"

# Pinned test dependencies from Maven Central, cached in tools/lib (gitignored), checked by SHA-256.
LIB="$ROOT/tools/lib"; mkdir -p "$LIB"
fetch() {   # group/artifact/version sha256
  local path=$1 sum=$2 name jar
  name=$(basename "$(dirname "$path")")-$(basename "$path").jar; jar="$LIB/$name"
  if [ ! -f "$jar" ]; then
    curl -fsSL -o "$jar.tmp" "https://repo1.maven.org/maven2/$path/$name" || { echo "FAIL: download $path"; exit 1; }
    mv "$jar.tmp" "$jar"
  fi
  echo "$sum  $jar" | shasum -a 256 -c --status - || { echo "FAIL: checksum $jar"; rm -f "$jar"; exit 1; }
  echo "$jar"
}
JSON=$(fetch org/json/json/20260719 c243f45f9590c12694a4142ed3f07fc70dfb71e4daebd05ae234bf92a2da92a6)
OKHTTP=$(fetch com/squareup/okhttp3/okhttp-jvm/5.3.2 c771f48075b763f6c322055e21129a94b7de4c1faf3f8f58ace320b0c9517a30)
OKIO=$(fetch com/squareup/okio/okio-jvm/3.16.4 2196b993cd34dbbd919e7e01f57a4781b58bee80f86106163e287c20343a96a7)
KOTLIN=$(fetch org/jetbrains/kotlin/kotlin-stdlib/2.3.10 f61662c6d3a2f8ef5bd34362a02d877772c39f393cd394feb259dfaf7f4d8437)
ANDROID=$(ls -d "$ANDROID_HOME"/platforms/android-3[4-9]/android.jar 2>/dev/null | head -1)
for j in "$JSON" "$OKHTTP" "$OKIO" "$KOTLIN" "$ANDROID"; do [ -f "$j" ] || { echo "FAIL: missing jar ($j)"; exit 1; }; done
# Stubs and org.json first so they shadow android.jar's "Stub!" implementations.
CP="$OUT/classes:$JSON:$OKHTTP:$OKIO:$KOTLIN:$ANDROID"

# Upstream extensions (s-links, redgifs, video downloads) need the shared extension library; skip them.
SRCS=$(find "$EXT/test/java" "$EXT/main/java" -name '*.java' \
  ! -name FixRedditVideoDownloadPatch.java ! -name FixSLinksPatch.java ! -name FixRedgifsApiPatch.java)
javac -nowarn -encoding UTF-8 -d "$OUT/classes" -cp "$CP" $SRCS 2>&1 | grep -vE "^Note:" || true
[ -d "$OUT/classes/app" ] || { echo "FAIL: compile"; exit 1; }

fail=0
run() { echo "== $1"; java -cp "$CP" "app.revanced.extension.syncforreddit.$1" "$ROOT/tests/fixtures" || fail=1; }
run ExtensionHarness
run MoreCommentsHarness
run GfycatHarness
[ $fail = 0 ] && echo "PASS: unit tests" || { echo "FAIL: unit tests"; exit 1; }
