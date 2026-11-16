# Source this in every shell: toolchain for building and testing the Sync for Reddit patches.
#   source sync-for-reddit/env.sh
# Paths are derived from this file's location. Toolchain locations come from the environment when set
# (JAVA_HOME pointing at a JDK 17, ANDROID_HOME), otherwise they are detected.

SYNC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"   # this directory
SYNC_REPO="$(cd "$SYNC_DIR/.." && pwd)"                        # the revanced-patches checkout
export SYNC_DIR SYNC_REPO

# JDK 17 (the patcher and Gradle build need 17; newer JDKs fail).
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"17\.'; then
  if [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  elif command -v brew >/dev/null 2>&1 && [ -d "$(brew --prefix 2>/dev/null)/opt/openjdk@17" ]; then
    JAVA_HOME="$(brew --prefix)/opt/openjdk@17"
  elif [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
  fi
fi
export JAVA_HOME
# JVM flags for newer JDKs (e.g. -XX:+UseCompactObjectHeaders) make JDK 17 refuse to start.
unset JAVA_OPTS

# Android SDK: build-tools (apksigner, aapt2), platform-tools (adb), emulator.
if [ -z "${ANDROID_HOME:-}" ]; then
  for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do [ -d "$d" ] && ANDROID_HOME="$d" && break; done
fi
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)"
export PATH="$JAVA_HOME/bin:$BUILD_TOOLS:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

# Python venv for the e2e tooling (python3 -m venv .venv && .venv/bin/pip install -r requirements.txt).
export SYNC_PY="$SYNC_DIR/.venv/bin/python"
