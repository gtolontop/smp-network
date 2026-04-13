#!/bin/bash
# ============================================================
#  Stop all servers gracefully
# ============================================================

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "Stopping servers..."

for SERVER in survival lobby velocity; do
  PID_FILE="$BASE_DIR/scripts/.$SERVER.pid"
  if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      echo "  Stopping $SERVER (PID: $PID)..."
      kill "$PID"
    fi
    rm -f "$PID_FILE"
  fi
done

echo "All servers stopped."
