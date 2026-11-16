#!/usr/bin/env bash
# Own a separate headless emulator pool; never adopt an already running device.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT/env.sh"
exec "$SYNC_PY" -I "$ROOT/scripts/e2e/emulators.py" "$@"
