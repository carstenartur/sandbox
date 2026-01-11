#!/bin/bash
# Entrypoint script for Sandbox Cleanup GitHub Action

set -e

echo "=== Sandbox Cleanup Application GitHub Action ==="
echo "Configuration file: ${INPUT_CONFIG_FILE}"
echo "Source directory: ${INPUT_SOURCE_DIR}"
echo "Verbose: ${INPUT_VERBOSE}"

# Start Xvfb
Xvfb :99 -screen 0 1280x1024x24 &
XVFB_PID=$!
sleep 2

# Prepare workspace
WORKSPACE_DIR="/tmp/cleanup-workspace"
mkdir -p "${WORKSPACE_DIR}"

# Use GITHUB_WORKSPACE directly
SOURCE_PATH="/github/workspace/${INPUT_SOURCE_DIR}"

# Build cleanup arguments
CLEANUP_ARGS=()
CLEANUP_ARGS+=("-data" "${WORKSPACE_DIR}")
CLEANUP_ARGS+=("-config" "/github/workspace/${INPUT_CONFIG_FILE}")

if [ "${INPUT_VERBOSE}" == "true" ]; then
    CLEANUP_ARGS+=("-verbose")
elif [ "${INPUT_QUIET}" == "true" ]; then
    CLEANUP_ARGS+=("-quiet")
fi

# Add source path
CLEANUP_ARGS+=("${SOURCE_PATH}")

# Run cleanup
echo "Running cleanup with Eclipse..."
echo "Command: ${ECLIPSE_HOME}/eclipse -nosplash -application org.sandbox.jdt.core.JavaCleanup ${CLEANUP_ARGS[@]}"

"${ECLIPSE_HOME}/eclipse" -nosplash \
    -application org.sandbox.jdt.core.JavaCleanup \
    "${CLEANUP_ARGS[@]}" || {
    EXIT_CODE=$?
    echo "::warning::Cleanup completed with warnings (exit code: ${EXIT_CODE})"
    echo "This may happen if some files are outside workspace or have compilation errors"
}

# Cleanup Xvfb - check if process exists before killing
if [ -n "$XVFB_PID" ]; then
    if ps -p $XVFB_PID > /dev/null 2>&1; then
        kill $XVFB_PID 2>/dev/null || true
    fi
fi

echo "=== Cleanup completed ==="
