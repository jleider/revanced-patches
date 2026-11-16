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

if [ -n "${ANDROID_SERIAL:-}" ] && [ "$ANDROID_SERIAL" != "${SYNC_AUTO_SERIAL:-}" ]; then
  export SYNC_EXPLICIT_SERIAL=1
fi

# Test device: the e2e/verify scripts clear app data, restore state and toggle device settings, so they
# must only ever touch an emulator. Default to the first running emulator even when a phone is plugged in.
if [ -z "${ANDROID_SERIAL:-}" ] && command -v adb >/dev/null 2>&1; then
  ANDROID_SERIAL="$(adb devices 2>/dev/null | awk '/^emulator-[0-9]+[[:space:]]+device$/ {print $1; exit}')"
  export SYNC_AUTO_SERIAL="$ANDROID_SERIAL"
fi
[ -n "${ANDROID_SERIAL:-}" ] && export ANDROID_SERIAL
# Call before anything that wipes or changes device state: refuses real phones.
sync_require_emulator() {
  case "${ANDROID_SERIAL:-}" in
    emulator-[0-9]*) ;;
    *) echo "FAIL: set ANDROID_SERIAL to an emulator before changing device state"; exit 1;;
  esac
  if [ "$(adb shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" != "1" ] \
     && [ "$(adb shell getprop ro.boot.qemu 2>/dev/null | tr -d '\r')" != "1" ]; then
    echo "FAIL: ${ANDROID_SERIAL:-the selected device} is not an emulator; these tests clear app data. Start the sync34 emulator or set ANDROID_SERIAL=emulator-NNNN."
    exit 1
  fi
}
