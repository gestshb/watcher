#!/usr/bin/env bash
# Watcher Installation Script for Linux (Run with sudo)
set -e

if [ "$EUID" -ne 0 ]; then
  echo "Error: Please run as root (sudo ./install-linux.sh)"
  exit 1
fi

TARGET_DIR="/var/lib/systemhealth"
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
chown -R root:root "$TARGET_DIR"
chmod 755 "$TARGET_DIR"
chmod 777 "$TARGET_DIR/spool"
chmod 777 "$TARGET_DIR/logs"

# Install XDG Desktop Autostart entry for graphical user session
AUTOSTART_DIR="/etc/xdg/autostart"
mkdir -p "$AUTOSTART_DIR"
cat << EOF > "$AUTOSTART_DIR/syshealth.desktop"
[Desktop Entry]
Type=Application
Name=System Health Monitor
Exec=/usr/bin/java -jar /var/lib/systemhealth/syshealthd.jar
Hidden=false
NoDisplay=true
X-GNOME-Autostart-enabled=true
EOF

chmod 644 "$AUTOSTART_DIR/syshealth.desktop"

echo "=========================================================="
echo "Installation complete!"
echo "Watcher configured to run upon graphical session login."
echo "=========================================================="
