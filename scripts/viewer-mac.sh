#!/usr/bin/env bash
set -e

PASSWORD="${1:-SecretParent123}"
PORT="${2:-8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -f "$SCRIPT_DIR/syshealthd.jar" ]; then
    JAR_PATH="$SCRIPT_DIR/syshealthd.jar"
elif [ -f "$SCRIPT_DIR/../target/syshealthd.jar" ]; then
    JAR_PATH="$SCRIPT_DIR/../target/syshealthd.jar"
elif [ -f "/Library/Application Support/SystemHealth/syshealthd.jar" ]; then
    JAR_PATH="/Library/Application Support/SystemHealth/syshealthd.jar"
else
    echo "Error: syshealthd.jar not found."
    exit 1
fi

if [[ "$JAR_PATH" == */target/syshealthd.jar ]]; then
    WORKING_DIR="$(dirname "$(dirname "$JAR_PATH")")"
else
    WORKING_DIR="$(dirname "$JAR_PATH")"
fi
cd "$WORKING_DIR"
java -jar "$JAR_PATH" -p "$PASSWORD" --viewer --viewer-port "$PORT"
