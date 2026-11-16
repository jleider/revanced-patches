#!/usr/bin/env bash
# Route the emulator through a local passthrough proxy and log every host Sync connects to.
#   scripts/e2e/netcap.sh start [logfile]   # starts mitmdump on :8899 and sets the emulator's global proxy
#   scripts/e2e/netcap.sh stop              # clears the proxy and stops mitmdump
# Nothing is decrypted: TLS is passed through and only CONNECT host names are recorded.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
PORT=8899
PIDF=${TMPDIR:-/tmp}/sync-netcap.pid
case "${1:-}" in
  start)
    LOGF=${2:-${TMPDIR:-/tmp}/sync-hosts.log}; : > "$LOGF"
    pkill -f "mitmdump.*--listen-port $PORT" 2>/dev/null || true
    HOSTLOG="$LOGF" "$ROOT/.venv/bin/mitmdump" -q --listen-host 127.0.0.1 --listen-port $PORT \
      --ignore-hosts '.*' -s "$ROOT/scripts/e2e/hostlog.py" >/dev/null 2>&1 &
    echo $! > "$PIDF"; sleep 2
    adb shell settings put global http_proxy 10.0.2.2:$PORT
    echo "$LOGF";;
  stop)
    adb shell settings put global http_proxy :0 >/dev/null 2>&1 || true
    adb shell settings delete global http_proxy >/dev/null 2>&1 || true
    [ -f "$PIDF" ] && kill "$(cat "$PIDF")" 2>/dev/null || true; rm -f "$PIDF"
    pkill -f "mitmdump.*--listen-port $PORT" 2>/dev/null || true;;
  *) echo "usage: $0 start [logfile] | stop"; exit 2;;
esac
