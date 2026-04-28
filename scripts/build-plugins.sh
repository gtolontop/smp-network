#!/bin/bash
# ============================================================
#  Build both SMPCore plugins and deploy to server folders
# ============================================================

set -e
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="$BASE_DIR/java/jdk-25.0.2+10"
export JAVA_HOME

echo "[1/4] Building core Paper plugin..."
cd "$BASE_DIR/plugins/core-paper"
./gradlew shadowJar

echo "[2/4] Building core Velocity plugin..."
cd "$BASE_DIR/plugins/core-velocity"
./gradlew shadowJar

echo "[3/4] Building AntiCheat Paper plugin..."
cd "$BASE_DIR/plugins/anticheat-paper"
./gradlew shadowJar

echo "[4/4] Building SMPLogger plugin..."
cd "$BASE_DIR/plugins/smp-logger"
./gradlew shadowJar

echo "Deploying jars..."
PAPER_JAR="$BASE_DIR/plugins/core-paper/build/libs/SMPCore-Paper-1.0.0.jar"
VELO_JAR="$BASE_DIR/plugins/core-velocity/build/libs/SMPCore-Velocity-1.0.0.jar"
AC_JAR="$BASE_DIR/plugins/anticheat-paper/build/libs/AntiCheat-Paper-1.0.0.jar"
LOG_JAR="$BASE_DIR/plugins/smp-logger/build/libs/SMPLogger-Paper-1.0.0.jar"

cp "$PAPER_JAR" "$BASE_DIR/lobby/plugins/"
cp "$PAPER_JAR" "$BASE_DIR/survival/plugins/"
cp "$VELO_JAR"  "$BASE_DIR/velocity/plugins/"
# AntiCheat deployed to both servers: survival runs the full suite (anti-xray/ESP/
# movement), lobby runs only client detection (brand + plugin channels + freecam
# heuristic) to block cheat-client users from transferring to survival.
cp "$AC_JAR"    "$BASE_DIR/lobby/plugins/"
cp "$AC_JAR"    "$BASE_DIR/survival/plugins/"
# SMPLogger: full activity log + backups, deployed everywhere.
cp "$LOG_JAR"   "$BASE_DIR/lobby/plugins/"
cp "$LOG_JAR"   "$BASE_DIR/survival/plugins/"

mkdir -p "$BASE_DIR/shared-data/players"
mkdir -p "$BASE_DIR/shared-data/smplogger"

echo "Done:"
echo "  lobby/plugins/    <- SMPCore, AntiCheat (client detection only), SMPLogger"
echo "  survival/plugins/ <- SMPCore, AntiCheat (full), SMPLogger"
echo "  velocity/plugins/ <- SMPCore-Velocity"
echo "  shared-data/{players,smplogger}/ ready"
