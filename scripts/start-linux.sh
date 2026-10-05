#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Locate syshealthd.jar
if [ -f "$SCRIPT_DIR/syshealthd.jar" ]; then
    JAR_PATH="$SCRIPT_DIR/syshealthd.jar"
elif [ -f "$SCRIPT_DIR/../target/syshealthd.jar" ]; then
    JAR_PATH="$SCRIPT_DIR/../target/syshealthd.jar"
elif [ -f "/var/lib/systemhealth/syshealthd.jar" ]; then
    JAR_PATH="/var/lib/systemhealth/syshealthd.jar"
else
    echo "Error: syshealthd.jar not found."
    echo "Please place syshealthd.jar in the same directory as this script ($SCRIPT_DIR)."
    exit 1
fi

# Locate java
if command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
elif [ -x "/usr/bin/java" ]; then
    JAVA_CMD="/usr/bin/java"
else
    echo "Error: Java 17+ is required but not found in PATH."
    exit 1
fi

# Check if already running
EXISTING_PIDS=$(pgrep -f "syshealthd.jar" 2>/dev/null || true)
if [ -n "$EXISTING_PIDS" ]; then
    echo "Watcher is already RUNNING under PID(s):"
    echo "$EXISTING_PIDS"
    exit 0
fi

echo "Starting Watcher manually in background..."
echo "JAR: $JAR_PATH"

WORKING_DIR="$(dirname "$JAR_PATH")"
cd "$WORKING_DIR"

nohup "$JAVA_CMD" -jar "$JAR_PATH" > /dev/null 2>&1 &
NEW_PID=$!

sleep 1.2

if ps -p "$NEW_PID" > /dev/null 2>&1; then
    echo "=========================================="
    echo " Watcher started successfully!"
    echo " PID: $NEW_PID"
    echo "=========================================="
    exit 0
else
    echo "Error: Failed to start Watcher daemon. Check logs for details."
    exit 1
fi
