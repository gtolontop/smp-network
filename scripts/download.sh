#!/bin/bash
# ============================================================
#  Download server JARs (Velocity + Paper x2)
#  Run once or whenever you want to update to latest builds.
# ============================================================

set -e

VELOCITY_VERSION="3.5.0-SNAPSHOT"
MC_VERSION="26.1.2"
PAPER_BUILD="${PAPER_BUILD:-}"

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Downloading server JARs (MC $MC_VERSION) ==="

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

# --- Paper ---
if [ -z "$PAPER_BUILD" ]; then
  echo "[2/3] Resolving latest Paper build for MC $MC_VERSION..."
  PAPER_BUILD=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/$MC_VERSION/builds" | \
    python3 -c "import sys,json; builds=json.load(sys.stdin).get('builds', []); print(builds[-1]['build'] if builds else '')" 2>/dev/null || echo "")
fi

if [ -z "$PAPER_BUILD" ]; then
  echo "  -> ERROR: Could not fetch a Paper build for MC $MC_VERSION"
  echo "     You can force one with: PAPER_BUILD=<build-number> bash scripts/download.sh"
  exit 1
fi

PAPER_JAR="paper-${MC_VERSION}-${PAPER_BUILD}.jar"
PAPER_URL="https://api.papermc.io/v2/projects/paper/versions/$MC_VERSION/builds/$PAPER_BUILD/downloads/$PAPER_JAR"

# --- Paper (Lobby) ---
echo "[2/3] Downloading Paper for lobby (build $PAPER_BUILD)..."
curl -Lo "$BASE_DIR/lobby/paper.jar" "$PAPER_URL"
echo "  -> lobby/paper.jar downloaded"

# --- Paper (Survival) ---
echo "[3/3] Downloading Paper for survival (build $PAPER_BUILD)..."
curl -Lo "$BASE_DIR/survival/paper.jar" "$PAPER_URL"
echo "  -> survival/paper.jar downloaded"

echo ""
echo "=== Done! ==="
echo "JARs saved to:"
echo "  velocity/velocity.jar"
echo "  lobby/paper.jar"
echo "  survival/paper.jar"
echo "Paper build: $PAPER_BUILD"
