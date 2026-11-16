#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
exec "$ROOT/.venv/bin/python" -I -c 'import runpy,sys; sys.path.insert(0,sys.argv.pop(1)); runpy.run_module("gallery_position",run_name="__main__")' "$ROOT/scripts/e2e"
