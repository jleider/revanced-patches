#!/usr/bin/env bash
# Feature e2e suite (one scenario per patch + shared-hook side effects). Needs the --debug build.
#   scripts/e2e/features.sh [scenario ...]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
# Start from the saved app-state baseline (settings, onboarding done, Crashlytics off).
[ "${KEEP_STATE:-0}" = 1 ] || "$ROOT/scripts/e2e/app_state.sh" restore >/dev/null 2>&1 || { echo "FAIL: app_state.sh restore (run it by hand to see why)"; exit 1; }
exec "$ROOT/.venv/bin/python" -I "$ROOT/scripts/e2e/features.py" "$@"
