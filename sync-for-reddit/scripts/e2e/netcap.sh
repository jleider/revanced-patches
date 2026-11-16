#!/usr/bin/env bash
# Route the emulator through a local passthrough proxy and log every host Sync connects to.
#   scripts/e2e/netcap.sh start [logfile]   # starts mitmdump on the serial-specific port and sets the emulator's global proxy
#   scripts/e2e/netcap.sh stop              # clears the proxy and stops mitmdump
# Nothing is decrypted: TLS is passed through and only CONNECT host names are recorded.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
sync_require_emulator
PORT=${SYNC_NETCAP_PORT:-$((8899 + (${ANDROID_SERIAL#emulator-} - 5554) / 2))}
PIDF=${TMPDIR:-/tmp}/sync-netcap-${ANDROID_SERIAL}.pid
case "${1:-}" in
  start)
    if [ -f "$PIDF" ] && kill -0 "$(cat "$PIDF")" 2>/dev/null; then
      echo "FAIL: capture already running for $ANDROID_SERIAL"; exit 1
    fi
    LOGF=${2:-${TMPDIR:-/tmp}/sync-hosts-${ANDROID_SERIAL}.log}; : > "$LOGF"
    HOSTLOG="$LOGF" "$ROOT/.venv/bin/mitmdump" -q --listen-host 127.0.0.1 --listen-port $PORT \
      --ignore-hosts '.*' -s "$ROOT/scripts/e2e/hostlog.py" >/dev/null 2>&1 &
    proxy_pid=$!; echo "$proxy_pid" > "$PIDF"
    trap 'kill "$proxy_pid" 2>/dev/null || true; rm -f "$PIDF"' EXIT
    # Wait for the socket, and reject bind failures before routing the device.
    "$SYNC_PY" -I - "$PORT" "$proxy_pid" <<'PYWAIT'
import os, socket, sys, time
port, pid = map(int, sys.argv[1:])
for _ in range(100):
    os.kill(pid, 0)
    try:
        with socket.create_connection(('127.0.0.1', port), timeout=.1):
            break
    except OSError:
        time.sleep(.1)
else:
    sys.exit('FAIL: netcap did not listen')
PYWAIT
    adb shell settings put global http_proxy 10.0.2.2:$PORT
    trap - EXIT
    echo "$LOGF";;
  stop)
    adb shell settings put global http_proxy :0 >/dev/null 2>&1 || true
    adb shell settings delete global http_proxy >/dev/null 2>&1 || true
    if [ -f "$PIDF" ]; then
      pid=$(cat "$PIDF")
      if ps -p "$pid" -o command= | grep -q -- "mitmdump.*--listen-port $PORT"; then
        kill "$pid" 2>/dev/null || true
      fi
      rm -f "$PIDF"
    fi
    ;;
  *) echo "usage: $0 start [logfile] | stop"; exit 2;;
esac
