#!/bin/bash
# ============================================================
#  Start all servers: Velocity + Lobby + Survival + PTR
#  Uses bundled Java 25 (required for MC 26.1.2)
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
echo "  Starting SMP Network (MC 26.1.2)"
echo "  All servers running Paper"
echo "  Java: $($JAVA -version 2>&1 | head -1)"
echo "============================================"

# --- Velocity ---
echo "[1/4] Starting Velocity proxy..."
cd "$BASE_DIR/velocity"
"$JAVA" --enable-native-access=ALL-UNNAMED -Xms512M -Xmx512M -jar velocity.jar &
VELOCITY_PID=$!
echo "  -> Velocity PID: $VELOCITY_PID"

sleep 5

# --- Lobby (Paper) ---
echo "[2/4] Starting Lobby server (Paper)..."
cd "$BASE_DIR/lobby"
"$JAVA" --enable-native-access=ALL-UNNAMED -Xms1G -Xmx2G -jar paper.jar --nogui &
LOBBY_PID=$!
echo "  -> Lobby PID: $LOBBY_PID"

sleep 5

# --- Survival (Paper) ---
echo "[3/4] Starting Survival server (Paper)..."
cd "$BASE_DIR/survival"
"$JAVA" --enable-native-access=ALL-UNNAMED \
  -Xms4G -Xmx16G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=16M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=20 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -XX:+UseStringDeduplication \
  -Daikars.new.flags=true \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -jar paper.jar --nogui &
SURVIVAL_PID=$!
echo "  -> Survival PID: $SURVIVAL_PID"

sleep 5

# --- PTR (Paper) ---
echo "[4/4] Starting PTR server (Paper)..."
cd "$BASE_DIR/ptr"
"$JAVA" --enable-native-access=ALL-UNNAMED \
  -Dsmp.server.type=ptr \
  -Xms1G -Xmx2G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=100 \
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
  -XX:+UseStringDeduplication \
  -jar paper.jar --nogui &
PTR_PID=$!
echo "  -> PTR PID: $PTR_PID"

echo ""
echo "============================================"
echo "  All servers started! (All Paper)"
echo "  Velocity:  localhost:25565 (PID: $VELOCITY_PID)"
echo "  Lobby:     localhost:25566 (PID: $LOBBY_PID)"
echo "  Survival:  localhost:25567 (PID: $SURVIVAL_PID)"
echo "  PTR:       localhost:25568 (PID: $PTR_PID)"
echo "--------------------------------------------"
echo "  TO STOP: run scripts/stop-all.sh"
echo "  DO NOT SIGKILL - risks world rollback."
echo "============================================"

echo "$VELOCITY_PID" > "$BASE_DIR/scripts/.velocity.pid"
echo "$LOBBY_PID" > "$BASE_DIR/scripts/.lobby.pid"
echo "$SURVIVAL_PID" > "$BASE_DIR/scripts/.survival.pid"
echo "$PTR_PID" > "$BASE_DIR/scripts/.ptr.pid"

wait
