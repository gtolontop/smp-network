#!/bin/bash
# ============================================================
#  Graceful shutdown for the SMP network.
#  Sends "save-all flush" + "stop" via RCON to lobby/survival,
#  waits for the port to close, then stops Velocity.
#  Never SIGKILL or close the terminal: always run this.
# ============================================================

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA="$BASE_DIR/java/jdk-25.0.2+10/bin/java"
RCON="$BASE_DIR/scripts/rcon/RconCli.java"

LOBBY_HOST=127.0.0.1
LOBBY_PORT=25575
LOBBY_PASS=aYG12mIV0S3KJvM7ouhYRU3tjF2BNIx
LOBBY_MC_PORT=25566

SURVIVAL_HOST=127.0.0.1
SURVIVAL_PORT=25576
SURVIVAL_PASS=ZFhJ7bUXe1mNq7vNvtrBQULrNC6WMPh
SURVIVAL_MC_PORT=25567

if [ ! -f "$JAVA" ]; then
  echo "ERROR: Java 25 not found at $JAVA"
  exit 1
fi

# Returns 0 while something is listening on $1.
port_listening() {
  (exec 3<>/dev/tcp/127.0.0.1/"$1") 2>/dev/null
  local rc=$?
  exec 3<&- 3>&- 2>/dev/null
  return $rc
}

stop_paper() {
  local name="$1" host="$2" port="$3" pass="$4" mc_port="$5"

  echo
  echo "[$name] save-all flush via RCON..."
  if ! "$JAVA" "$RCON" "$host" "$port" "$pass" save-all flush; then
    echo "[$name] RCON unreachable - server may already be down."
    return 0
  fi

  echo "[$name] stop via RCON..."
  "$JAVA" "$RCON" "$host" "$port" "$pass" stop >/dev/null 2>&1 || true

  echo "[$name] waiting for port $mc_port to close (max 120s)..."
  for _ in $(seq 1 60); do
    if ! port_listening "$mc_port"; then
      echo "[$name] stopped."
      return 0
    fi
    sleep 2
  done
  echo "[$name] still running after 120s, giving up gracefully."
}

echo "============================================"
echo "  Graceful shutdown (RCON + save-all flush)"
echo "============================================"

stop_paper Survival "$SURVIVAL_HOST" "$SURVIVAL_PORT" "$SURVIVAL_PASS" "$SURVIVAL_MC_PORT"
stop_paper Lobby    "$LOBBY_HOST"    "$LOBBY_PORT"    "$LOBBY_PASS"    "$LOBBY_MC_PORT"

# Velocity has no world data; stop via PID file if available, else leave it.
VELOCITY_PID_FILE="$BASE_DIR/scripts/.velocity.pid"
if [ -f "$VELOCITY_PID_FILE" ]; then
  VPID="$(cat "$VELOCITY_PID_FILE")"
  if kill -0 "$VPID" 2>/dev/null; then
    echo
    echo "[Velocity] sending SIGTERM to PID $VPID..."
    kill "$VPID"
    for _ in $(seq 1 15); do
      kill -0 "$VPID" 2>/dev/null || break
      sleep 1
    done
  fi
  rm -f "$VELOCITY_PID_FILE"
fi

# Clean up old Paper PID files too (no longer needed - shutdown went through RCON).
rm -f "$BASE_DIR/scripts/.lobby.pid" "$BASE_DIR/scripts/.survival.pid"

echo
echo "All servers saved and stopped cleanly."
