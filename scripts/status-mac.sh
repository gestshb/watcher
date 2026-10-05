#!/usr/bin/env bash

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
