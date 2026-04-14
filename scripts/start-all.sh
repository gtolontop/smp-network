#!/bin/bash
# ============================================================
#  Start all servers: Velocity + Lobby + Survival
#  Uses bundled Java 25 (required for MC 26.1+)
# ============================================================

set -e
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA="$BASE_DIR/java/jdk-25.0.2+10/bin/java"

if [ ! -f "$JAVA" ]; then
  echo "ERROR: Java 25 not found at $JAVA"
  echo "Run scripts/download.sh to install it, or download from https://adoptium.net/"
  exit 1
fi

echo "============================================"
echo "  Starting SMP Network (MC 26.1.2 / 1.21.11)"
echo "  Java: $($JAVA -version 2>&1 | head -1)"
echo "============================================"

# --- Velocity ---
echo "[1/3] Starting Velocity proxy..."
cd "$BASE_DIR/velocity"
"$JAVA" -Xms512M -Xmx512M -jar velocity.jar &
VELOCITY_PID=$!
echo "  -> Velocity PID: $VELOCITY_PID"

sleep 5

# --- Lobby ---
echo "[2/3] Starting Lobby server..."
cd "$BASE_DIR/lobby"
"$JAVA" -Xms1G -Xmx2G -jar paper.jar --nogui &
LOBBY_PID=$!
echo "  -> Lobby PID: $LOBBY_PID"

sleep 5

# --- Survival ---
echo "[3/3] Starting Survival server..."
cd "$BASE_DIR/survival"
"$JAVA" -Xms8G -Xmx16G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -jar folia.jar --nogui &
SURVIVAL_PID=$!
echo "  -> Survival PID: $SURVIVAL_PID"

echo ""
echo "============================================"
echo "  All servers started!"
echo "  Velocity:  localhost:25565 (PID: $VELOCITY_PID)"
echo "  Lobby:     localhost:25566 (PID: $LOBBY_PID)"
echo "  Survival:  localhost:25567 (PID: $SURVIVAL_PID)"
echo "============================================"

echo "$VELOCITY_PID" > "$BASE_DIR/scripts/.velocity.pid"
echo "$LOBBY_PID" > "$BASE_DIR/scripts/.lobby.pid"
echo "$SURVIVAL_PID" > "$BASE_DIR/scripts/.survival.pid"

wait
