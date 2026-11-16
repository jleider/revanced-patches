#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"   # sync-for-reddit/
source "$ROOT/env.sh"
# Isolated mode excludes the script directory: add only this trusted helper directory.
exec "$ROOT/.venv/bin/python" -I -c 'import runpy,sys; sys.path.insert(0,sys.argv.pop(1)); runpy.run_module("login",run_name="__main__")' "$ROOT/scripts/e2e"
