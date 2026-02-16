#!/bin/sh
# Entrypoint for the Sandbox Cleanup Docker image.
# Starts Xvfb for headless Eclipse operation, then runs the cleanup tool.

set -e

# Start Xvfb in the background
Xvfb :99 -screen 0 1280x1024x24 -nolisten tcp &
XVFB_PID=$!
export DISPLAY=:99

# Cleanup Xvfb on exit
cleanup() {
    if [ -n "$XVFB_PID" ]; then
        kill "$XVFB_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT INT TERM

# Wait for Xvfb to be ready (poll for display socket)
RETRIES=10
while [ $RETRIES -gt 0 ]; do
    if xdpyinfo -display :99 >/dev/null 2>&1; then
        break
    fi
    RETRIES=$((RETRIES - 1))
    sleep 0.2
done

if [ $RETRIES -eq 0 ]; then
    echo "Warning: Xvfb may not be ready, proceeding anyway" >&2
fi

# Run the cleanup tool, forwarding all arguments
exec /opt/sandbox-cleanup/bin/sandbox-cleanup "$@"
