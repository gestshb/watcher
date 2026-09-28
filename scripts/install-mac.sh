#!/usr/bin/env bash
# Watcher Installation Script for macOS (Run with sudo)
set -e

if [ "$EUID" -ne 0 ]; then
  echo "Error: Please run as root (sudo ./install-mac.sh)"
  exit 1
fi

TARGET_DIR="/Library/Application Support/SystemHealth"
echo "Creating application directory: $TARGET_DIR"
mkdir -p "$TARGET_DIR/spool"
mkdir -p "$TARGET_DIR/logs"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cp "$SCRIPT_DIR/../target/syshealthd.jar" "$TARGET_DIR/syshealthd.jar"

if [ -f "$SCRIPT_DIR/../credentials.json" ]; then
    cp "$SCRIPT_DIR/../credentials.json" "$TARGET_DIR/credentials.json"
fi

# Hardening permissions
echo "Hardening file permissions..."
chown -R root:wheel "$TARGET_DIR"
chmod 755 "$TARGET_DIR"
chmod 777 "$TARGET_DIR/spool"
chmod 777 "$TARGET_DIR/logs"

# Install LaunchAgent so it runs inside active graphical user session
AGENT_PLIST="/Library/LaunchAgents/com.system.healthagent.plist"
cp "$SCRIPT_DIR/../src/main/resources/com.system.healthagent.plist" "$AGENT_PLIST"
chown root:wheel "$AGENT_PLIST"
chmod 644 "$AGENT_PLIST"

echo "=========================================================="
echo "Installation complete!"
echo "NOTE for macOS:"
echo "Please grant Screen Recording permission in:"
echo "System Settings -> Privacy & Security -> Screen Recording"
echo "for /usr/bin/java or your Java runtime."
echo "=========================================================="
