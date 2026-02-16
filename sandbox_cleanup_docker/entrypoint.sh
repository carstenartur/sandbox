#!/bin/sh
# Entrypoint for the Sandbox Cleanup Docker image.
# Starts Xvfb for headless Eclipse operation, then runs the cleanup tool.

set -e

# Start Xvfb in the background
Xvfb :99 -screen 0 1280x1024x24 -nolisten tcp &
XVFB_PID=$!
export DISPLAY=:99

# Wait for Xvfb to start
sleep 1

# Cleanup Xvfb on exit
cleanup() {
    if [ -n "$XVFB_PID" ]; then
        kill "$XVFB_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT INT TERM

# Run the cleanup tool, forwarding all arguments
exec /opt/sandbox-cleanup/bin/sandbox-cleanup "$@"
