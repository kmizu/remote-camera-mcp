#!/usr/bin/env bash
set -euo pipefail

HOST="${MCP_HOST:-0.0.0.0}"
PORT="${MCP_PORT:-8000}"

exec sbt "run --host $HOST --port $PORT"
