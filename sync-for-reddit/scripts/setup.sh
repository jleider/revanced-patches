#!/usr/bin/env bash
# One-time setup of the local (gitignored) working files. Safe to re-run.
#   scripts/setup.sh
# Needs: apk/sync-23.06.30.apk (your copy), JDK 17, Android SDK, Python >= 3.12, apktool; jadx optional.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
cd "$ROOT"
APK=apk/sync-23.06.30.apk
SHA=6469a8dcc8e2a097892f84e8962e94965713be327b18db6a3110ed96f7fa4190
fail() { echo "FAIL: $*"; exit 1; }

[ -f "$APK" ] || fail "put your copy of Sync v23.06.30-13:39 (universal APK) at $APK"
echo "$SHA  $APK" | shasum -a 256 -c --status - || echo "WARN: $APK differs from the tested APK (SHA-256 $SHA)"
"$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"17\.' || fail "JDK 17 not found (set JAVA_HOME)"
[ -d "${ANDROID_HOME:-}" ] || fail "Android SDK not found (set ANDROID_HOME)"
command -v apktool >/dev/null || fail "apktool not found (https://apktool.org, or: brew install apktool)"
[ -f tools/revanced-cli-6.0.0-all.jar ] || fail "download revanced-cli-6.0.0-all.jar into tools/"
[ -f secrets.env ] || { cp secrets.env.example secrets.env; chmod 600 secrets.env; echo "created secrets.env: add your REDDIT_CLIENT_ID"; }

# Python venv for the e2e tools (mitmproxy 12 needs Python >= 3.12).
PY=$(command -v python3.14 || command -v python3.13 || command -v python3.12 || command -v python3)
"$PY" -c 'import sys; sys.exit(sys.version_info < (3, 12))' || fail "Python >= 3.12 needed for the e2e tools ($PY is older)"
[ -x .venv/bin/python ] || "$PY" -m venv .venv
.venv/bin/pip install -q -r requirements.txt

# Original smali: the class allowlist and reachability gates compare the patched app against it.
if [ ! -d decompiled/sync-23.06.30-smali ]; then
  echo "== apktool (original smali)"
  apktool d -f -r -o decompiled/sync-23.06.30-smali "$APK" >/dev/null
fi
# Readable Java with original class names (for reading only; optional).
if command -v jadx >/dev/null && [ ! -d decompiled/sync-23.06.30-readable ]; then
  echo "== jadx (readable sources)"
  jadx --deobf --use-source-name-as-class-name-alias always -d decompiled/sync-23.06.30-readable "$APK" >/dev/null 2>&1 || true
fi
echo "setup done. Next: source env.sh && scripts/verify_patch.sh --no-install"
