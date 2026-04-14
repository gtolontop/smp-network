#!/bin/bash
# ============================================================
#  Download server JARs (Velocity, Paper, Folia)
#  Run once or whenever you want to update to latest builds.
# ============================================================

set -e

VELOCITY_VERSION="3.4.0-SNAPSHOT"
PAPER_VERSION="1.21.11"
FOLIA_VERSION="1.21.11"

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Downloading server JARs ==="

# --- Velocity ---
echo "[1/3] Downloading Velocity..."
VELOCITY_BUILD=$(curl -s "https://api.papermc.io/v2/projects/velocity/versions/$VELOCITY_VERSION/builds" | \
  python3 -c "import sys,json; builds=json.load(sys.stdin)['builds']; print(builds[-1]['build'])" 2>/dev/null || echo "")

if [ -n "$VELOCITY_BUILD" ]; then
  VELOCITY_JAR="velocity-${VELOCITY_VERSION}-${VELOCITY_BUILD}.jar"
  curl -Lo "$BASE_DIR/velocity/velocity.jar" \
    "https://api.papermc.io/v2/projects/velocity/versions/$VELOCITY_VERSION/builds/$VELOCITY_BUILD/downloads/$VELOCITY_JAR"
  echo "  -> velocity.jar downloaded (build $VELOCITY_BUILD)"
else
  echo "  -> WARN: Could not fetch Velocity build. Download manually from https://papermc.io/downloads/velocity"
fi

# --- Paper (Lobby) ---
echo "[2/3] Downloading Paper (lobby)..."
PAPER_BUILD=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/$PAPER_VERSION/builds" | \
  python3 -c "import sys,json; builds=json.load(sys.stdin)['builds']; print(builds[-1]['build'])" 2>/dev/null || echo "")

if [ -n "$PAPER_BUILD" ]; then
  PAPER_JAR="paper-${PAPER_VERSION}-${PAPER_BUILD}.jar"
  curl -Lo "$BASE_DIR/lobby/paper.jar" \
    "https://api.papermc.io/v2/projects/paper/versions/$PAPER_VERSION/builds/$PAPER_BUILD/downloads/$PAPER_JAR"
  echo "  -> paper.jar downloaded (build $PAPER_BUILD)"
else
  echo "  -> WARN: Could not fetch Paper build. Download manually from https://papermc.io/downloads/paper"
fi

# --- Folia (Survival) ---
echo "[3/3] Downloading Folia (survival)..."
FOLIA_BUILD=$(curl -s "https://api.papermc.io/v2/projects/folia/versions/$FOLIA_VERSION/builds" | \
  python3 -c "import sys,json; builds=json.load(sys.stdin)['builds']; print(builds[-1]['build'])" 2>/dev/null || echo "")

if [ -n "$FOLIA_BUILD" ]; then
  FOLIA_JAR="folia-${FOLIA_VERSION}-${FOLIA_BUILD}.jar"
  curl -Lo "$BASE_DIR/survival/folia.jar" \
    "https://api.papermc.io/v2/projects/folia/versions/$FOLIA_VERSION/builds/$FOLIA_BUILD/downloads/$FOLIA_JAR"
  echo "  -> folia.jar downloaded (build $FOLIA_BUILD)"
else
  echo "  -> WARN: Could not fetch Folia build. Download manually from https://papermc.io/downloads/folia"
fi

echo ""
echo "=== Done! ==="
echo "JARs saved to:"
echo "  velocity/velocity.jar"
echo "  lobby/paper.jar"
echo "  survival/folia.jar"
