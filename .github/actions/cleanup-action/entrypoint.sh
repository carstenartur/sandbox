#!/bin/bash
# Entrypoint script for Sandbox Cleanup GitHub Action

set -e

echo "=== Sandbox Cleanup Application GitHub Action ==="
echo "Configuration file: ${INPUT_CONFIG_FILE}"
echo "Source directory: ${INPUT_SOURCE_DIR}"
echo "Verbose: ${INPUT_VERBOSE}"
echo "Quiet: ${INPUT_QUIET}"

# Start Xvfb
Xvfb :99 -screen 0 1280x1024x24 &
XVFB_PID=$!
sleep 2

# Prepare workspace
WORKSPACE_DIR="/tmp/cleanup-workspace"
mkdir -p "${WORKSPACE_DIR}"

# Define and validate workspace paths - prevent path traversal attacks
WORKSPACE_ROOT="/github/workspace"

# Resolve and normalize config and source paths
CONFIG_PATH=$(realpath -m "${WORKSPACE_ROOT}/${INPUT_CONFIG_FILE}")
SOURCE_PATH=$(realpath -m "${WORKSPACE_ROOT}/${INPUT_SOURCE_DIR}")

# Ensure resolved paths stay within the workspace root
case "${CONFIG_PATH}" in
    "${WORKSPACE_ROOT}"/*) ;;
    *)
        echo "::error::CONFIG_FILE resolves outside of workspace: ${CONFIG_PATH}"
        exit 1
        ;;
esac

case "${SOURCE_PATH}" in
    "${WORKSPACE_ROOT}"/*) ;;
    *)
        echo "::error::SOURCE_DIR resolves outside of workspace: ${SOURCE_PATH}"
        exit 1
        ;;
esac

echo "Validated config path: ${CONFIG_PATH}"
echo "Validated source path: ${SOURCE_PATH}"

# Build cleanup arguments
CLEANUP_ARGS=()
CLEANUP_ARGS+=("-data" "${WORKSPACE_DIR}")
CLEANUP_ARGS+=("-config" "${CONFIG_PATH}")

if [ "${INPUT_VERBOSE}" == "true" ]; then
    CLEANUP_ARGS+=("-verbose")
elif [ "${INPUT_QUIET}" == "true" ]; then
    CLEANUP_ARGS+=("-quiet")
fi

# Add source path
CLEANUP_ARGS+=("${SOURCE_PATH}")

# Run cleanup
echo "Running cleanup with Eclipse..."
echo "Command: ${ECLIPSE_HOME}/eclipse -nosplash -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup ${CLEANUP_ARGS[@]}"

# Run cleanup and capture exit code
set +e
"${ECLIPSE_HOME}/eclipse" -nosplash \
    -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
    "${CLEANUP_ARGS[@]}"
CLEANUP_EXIT_CODE=$?
set -e

# Handle exit code appropriately
if [ "${CLEANUP_EXIT_CODE}" -eq 0 ]; then
    echo "Cleanup completed successfully"
else
    echo "::warning::Cleanup completed with exit code: ${CLEANUP_EXIT_CODE}"
    echo "This may happen if some files are outside workspace or have compilation errors"
    echo "Review the cleanup logs above for details"
fi

# Cleanup Xvfb
if [ -n "$XVFB_PID" ] && ps -p $XVFB_PID > /dev/null 2>&1; then
    kill $XVFB_PID 2>/dev/null || true
fi

echo "=== Cleanup completed ==="
exit 0
