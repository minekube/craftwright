#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CRAFTLESS_BIN="${CRAFTLESS_CI_SMOKE_BIN:-"$ROOT/build/docker/craftless/bin/craftless"}"
SMOKE_ROOT="${CRAFTLESS_CI_SMOKE_ROOT:-"$ROOT/build/craftless-ci-smoke"}"
WORKSPACE="$SMOKE_ROOT/workspace"
ARTIFACTS="$SMOKE_ROOT/artifacts"
LOG="$ARTIFACTS/craftless-daemon.log"
METADATA="$ARTIFACTS/daemon-metadata.json"

rm -rf "$SMOKE_ROOT"
mkdir -p "$WORKSPACE" "$ARTIFACTS"

if [ ! -x "$CRAFTLESS_BIN" ]; then
  echo "Craftless CLI is not executable: $CRAFTLESS_BIN" >&2
  exit 1
fi

"$CRAFTLESS_BIN" --help | grep -q "daemon start"

"$CRAFTLESS_BIN" daemon start --port 0 --workspace "$WORKSPACE" > "$LOG" 2>&1 &
DAEMON_PID="$!"

cleanup() {
  if kill -0 "$DAEMON_PID" 2>/dev/null; then
    kill "$DAEMON_PID" 2>/dev/null || true
    wait "$DAEMON_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

for _ in $(seq 1 120); do
  if [ -s "$LOG" ] && head -n 1 "$LOG" | grep -q '"url"'; then
    head -n 1 "$LOG" > "$METADATA"
    break
  fi
  if ! kill -0 "$DAEMON_PID" 2>/dev/null; then
    cat "$LOG" >&2 || true
    exit 1
  fi
  sleep 0.5
done

if [ ! -s "$METADATA" ]; then
  echo "Timed out waiting for Craftless daemon metadata" >&2
  cat "$LOG" >&2 || true
  exit 1
fi

API_URL="$(
  python3 - "$METADATA" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as metadata_file:
    print(json.load(metadata_file)["url"])
PY
)"

for _ in $(seq 1 60); do
  if curl -fsS "$API_URL/openapi.json" -o "$ARTIFACTS/openapi.json"; then
    break
  fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    cat "$LOG" >&2 || true
    exit 1
  fi
  sleep 0.5
done

curl -fsS "$API_URL/version" -o "$ARTIFACTS/version.json"
curl -fsS "$API_URL/clients" -o "$ARTIFACTS/clients.json"

grep -q '"openapi"' "$ARTIFACTS/openapi.json"
grep -q '"/clients"' "$ARTIFACTS/openapi.json"
grep -q '"driverVersion"' "$ARTIFACTS/version.json"

printf 'Craftless CI smoke passed: %s\n' "$API_URL"
