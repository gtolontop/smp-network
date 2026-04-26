#!/bin/bash
# ============================================================
#  Build both SMPCore plugins and deploy to server folders
# ============================================================

set -e
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="$BASE_DIR/java/jdk-25.0.2+10"
export JAVA_HOME

echo "[1/3] Building core Paper plugin..."
cd "$BASE_DIR/plugins/core-paper"
./gradlew shadowJar

echo "[2/3] Building core Velocity plugin..."
cd "$BASE_DIR/plugins/core-velocity"
./gradlew shadowJar

echo "[3/3] Building AntiCheat Paper plugin..."
cd "$BASE_DIR/plugins/anticheat-paper"
./gradlew shadowJar

echo "Deploying jars..."
PAPER_JAR="$BASE_DIR/plugins/core-paper/build/libs/SMPCore-Paper-1.0.0.jar"
VELO_JAR="$BASE_DIR/plugins/core-velocity/build/libs/SMPCore-Velocity-1.0.0.jar"
AC_JAR="$BASE_DIR/plugins/anticheat-paper/build/libs/AntiCheat-Paper-1.0.0.jar"

cp "$PAPER_JAR" "$BASE_DIR/lobby/plugins/"
cp "$PAPER_JAR" "$BASE_DIR/survival/plugins/"
cp "$VELO_JAR"  "$BASE_DIR/velocity/plugins/"
# AntiCheat deployed to both servers: survival runs the full suite (anti-xray/ESP/
# movement), lobby runs only client detection (brand + plugin channels + freecam
# heuristic) to block cheat-client users from transferring to survival.
cp "$AC_JAR"    "$BASE_DIR/lobby/plugins/"
cp "$AC_JAR"    "$BASE_DIR/survival/plugins/"

mkdir -p "$BASE_DIR/shared-data/players"

echo "Done:"
echo "  lobby/plugins/    <- SMPCore-Paper, AntiCheat-Paper (client detection only)"
echo "  survival/plugins/ <- SMPCore-Paper, AntiCheat-Paper (full)"
echo "  velocity/plugins/ <- SMPCore-Velocity"
echo "  shared-data/players/ ready"
