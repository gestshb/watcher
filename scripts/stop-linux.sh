#!/usr/bin/env bash

echo "Stopping Watcher manually..."

# 1. Terminate Watchdog supervisor first so it doesn't resurrect worker
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

# Give processes time to exit cleanly
sleep 1.5

# Force kill any lingering processes if still alive
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
