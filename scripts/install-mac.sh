#!/usr/bin/env bash
# Watcher Installation Script for macOS (Run with sudo)
set -e

if [ "$EUID" -ne 0 ]; then
  echo "Error: Please run as root (sudo ./install-mac.sh)"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/syshealthd.jar"

if [ ! -f "$JAR_FILE" ]; then
  if [ -f "$SCRIPT_DIR/../target/syshealthd.jar" ]; then
    JAR_FILE="$SCRIPT_DIR/../target/syshealthd.jar"
  else
    echo "Error: syshealthd.jar was not found in the same directory as this script ($SCRIPT_DIR)."
    exit 1
  fi
fi

TARGET_DIR="/Library/Application Support/SystemHealth"
echo "Creating application directory: $TARGET_DIR"
mkdir -p "$TARGET_DIR/spool"
mkdir -p "$TARGET_DIR/logs"

echo "Copying $JAR_FILE to $TARGET_DIR..."
cp "$JAR_FILE" "$TARGET_DIR/syshealthd.jar"

if [ -f "$SCRIPT_DIR/credentials.json" ]; then
  cp "$SCRIPT_DIR/credentials.json" "$TARGET_DIR/credentials.json"
elif [ -f "$SCRIPT_DIR/../credentials.json" ]; then
  cp "$SCRIPT_DIR/../credentials.json" "$TARGET_DIR/credentials.json"
fi

# Hardening permissions
echo "Hardening file permissions..."
chown -R root:wheel "$TARGET_DIR"
chmod 755 "$TARGET_DIR"
chmod 777 "$TARGET_DIR/spool"
chmod 777 "$TARGET_DIR/logs"

# Install LaunchAgent inline so it runs inside active graphical user session
AGENT_PLIST="/Library/LaunchAgents/com.system.healthagent.plist"
echo "Creating LaunchAgent: $AGENT_PLIST"
cat << 'EOF' > "$AGENT_PLIST"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.system.healthagent</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/bin/java</string>
        <string>-jar</string>
        <string>/Library/Application Support/SystemHealth/syshealthd.jar</string>
    </array>
    <key>WorkingDirectory</key>
    <string>/Library/Application Support/SystemHealth</string>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>/Library/Application Support/SystemHealth/logs/stdout.log</string>
    <key>StandardErrorPath</key>
    <string>/Library/Application Support/SystemHealth/logs/stderr.log</string>
</dict>
</plist>
EOF

chown root:wheel "$AGENT_PLIST"
chmod 644 "$AGENT_PLIST"

echo "=========================================================="
echo "Installation complete!"
echo "NOTE for macOS:"
echo "Please grant Screen Recording permission in:"
echo "System Settings -> Privacy & Security -> Screen Recording"
echo "for /usr/bin/java or your Java runtime."
echo "=========================================================="
