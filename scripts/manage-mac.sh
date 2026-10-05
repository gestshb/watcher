#!/usr/bin/env bash
# Watcher Management Script for macOS
set -e

ACTION="${1:-status}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

find_jar() {
    if [ -f "$SCRIPT_DIR/syshealthd.jar" ]; then
        echo "$SCRIPT_DIR/syshealthd.jar"
    elif [ -f "$SCRIPT_DIR/../target/syshealthd.jar" ]; then
        echo "$(cd "$SCRIPT_DIR/.." && pwd)/target/syshealthd.jar"
    elif [ -f "/Library/Application Support/SystemHealth/syshealthd.jar" ]; then
        echo "/Library/Application Support/SystemHealth/syshealthd.jar"
    else
        echo ""
    fi
}

find_java() {
    if command -v java >/dev/null 2>&1; then
        echo "java"
    elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        echo "$JAVA_HOME/bin/java"
    elif [ -x "/usr/bin/java" ]; then
        echo "/usr/bin/java"
    else
        echo ""
    fi
}

start_app() {
    JAR_PATH="$(find_jar)"
    if [ -z "$JAR_PATH" ]; then
        echo "Error: syshealthd.jar not found."
        echo "Please place syshealthd.jar in the same directory as this script ($SCRIPT_DIR)."
        exit 1
    fi

    JAVA_CMD="$(find_java)"
    if [ -z "$JAVA_CMD" ]; then
        echo "Error: Java 17+ is required but was not found in PATH."
        exit 1
    fi

    EXISTING_PIDS=$(pgrep -f "syshealthd.jar" 2>/dev/null || true)
    if [ -n "$EXISTING_PIDS" ]; then
        echo "Watcher is already RUNNING under PID(s):"
        echo "$EXISTING_PIDS"
        exit 0
    fi

    echo "Starting Watcher manually in background..."
    echo "JAR: $JAR_PATH"
    if [[ "$JAR_PATH" == */target/syshealthd.jar ]]; then
        WORKING_DIR="$(dirname "$(dirname "$JAR_PATH")")"
    else
        WORKING_DIR="$(dirname "$JAR_PATH")"
    fi
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
}

status_app() {
    echo "Checking Watcher status..."
    PIDS=$(pgrep -f "syshealthd.jar" 2>/dev/null || true)

    if [ -n "$PIDS" ]; then
        echo "=========================================="
        echo " Watcher is RUNNING"
        echo "=========================================="
        PID_CSV=$(pgrep -d, -f "syshealthd.jar" 2>/dev/null || true)
        if [ -n "$PID_CSV" ]; then
            ps -p "$PID_CSV" -o pid,ppid,%cpu,%mem,time,command 2>/dev/null || pgrep -fl "syshealthd.jar"
        fi
        echo "=========================================="
        exit 0
    else
        echo "=========================================="
        echo " Watcher is NOT running."
        echo "=========================================="
        exit 1
    fi
}

stop_app() {
    echo "Stopping Watcher manually..."

    # 1. Terminate Watchdog supervisor first
    WATCHDOG_PIDS=$(pgrep -f "com.watcher.protection.Watchdog" 2>/dev/null || true)
    if [ -n "$WATCHDOG_PIDS" ]; then
        echo "Stopping Watchdog process(es): $WATCHDOG_PIDS"
        kill $WATCHDOG_PIDS 2>/dev/null || true
    fi

    # 2. Terminate main worker processes
    WORKER_PIDS=$(pgrep -f "syshealthd.jar" 2>/dev/null || true)
    if [ -n "$WORKER_PIDS" ]; then
        echo "Stopping Watcher process(es): $WORKER_PIDS"
        kill $WORKER_PIDS 2>/dev/null || true
    fi

    sleep 1.5

    # Force kill any lingering processes
    REMAINING_PIDS=$(pgrep -f "syshealthd.jar" 2>/dev/null || true)
    if [ -n "$REMAINING_PIDS" ]; then
        echo "Force stopping lingering process(es): $REMAINING_PIDS"
        kill -9 $REMAINING_PIDS 2>/dev/null || true
        sleep 0.5
    fi

    # Verify
    FINAL_CHECK=$(pgrep -f "syshealthd.jar" 2>/dev/null || true)
    if [ -z "$FINAL_CHECK" ]; then
        echo "=========================================="
        echo " Watcher stopped successfully."
        echo "=========================================="
        exit 0
    else
        echo "Error: Unable to stop process(es): $FINAL_CHECK"
        exit 1
    fi
}

case "$ACTION" in
    start)
        start_app
        ;;
    status|test|check)
        status_app
        ;;
    stop)
        stop_app
        ;;
    *)
        echo "Usage: $0 {start|status|stop}"
        echo "  start  : Launch Watcher in background"
        echo "  status : Test and check if Watcher is running"
        echo "  stop   : Stop Watcher and its watchdog supervisor"
        exit 1
        ;;
esac
