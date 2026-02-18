# Sandbox Cleanup Docker Image

Docker image for running Eclipse JDT cleanup transformations in containers.

**Image Registry:** GitHub Container Registry (GHCR)  
**Image:** `ghcr.io/carstenartur/sandbox-cleanup:latest`

This image is automatically built and published by the [publish-cleanup-image.yml](../.github/workflows/publish-cleanup-image.yml) workflow when changes are pushed to the main branch.

## Quick Start

```bash
# Pull the latest image
docker pull ghcr.io/carstenartur/sandbox-cleanup:latest

# Run check mode (read-only)
docker run --rm -v /path/to/project:/workspace \
    ghcr.io/carstenartur/sandbox-cleanup:latest \
    --config /workspace/cleanup.properties --mode check --source /workspace

# Run apply mode (requires write permissions)
# The container runs as user 'sandbox' (non-root), so match host UID/GID for write access
docker run --rm --user $(id -u):$(id -g) \
    -v /path/to/project:/workspace \
    ghcr.io/carstenartur/sandbox-cleanup:latest \
    --config /workspace/cleanup.properties --mode apply --source /workspace

# Run diff mode (read-only)
docker run --rm -v /path/to/project:/workspace \
    ghcr.io/carstenartur/sandbox-cleanup:latest \
    --config /workspace/cleanup.properties --mode diff --source /workspace
```

**Important:** For `apply` mode, use `--user $(id -u):$(id -g)` to match the host user's UID/GID, ensuring the container can modify files in the mounted workspace.

## GitHub Actions Usage

The image is used by the [pr-auto-cleanup.yml](../.github/workflows/pr-auto-cleanup.yml) workflow to automatically apply cleanups to pull requests:

```yaml
- name: Run Sandbox Cleanup
  run: |
    docker pull ghcr.io/carstenartur/sandbox-cleanup:latest
    docker run --rm \
      --user $(id -u):$(id -g) \
      -v ${{ github.workspace }}:/workspace \
      ghcr.io/carstenartur/sandbox-cleanup:latest \
      --config /workspace/.github/cleanup-profiles/standard.properties \
      --mode apply \
      --source /workspace \
      -verbose
```

## Building the Image Locally

If you need to build the image locally for testing:

1. Build the Eclipse product first:
   ```bash
   xvfb-run --auto-servernum mvn -Pproduct clean verify -DskipTests
   ```

2. Prepare the Docker context:
   
   The workflow in [publish-cleanup-image.yml](../.github/workflows/publish-cleanup-image.yml) handles this automatically. For manual builds, you'll need to replicate those steps:
   
   ```bash
   # Create a fresh Docker build context directory
   rm -rf docker-context
   mkdir -p docker-context/dist
   
   # Extract the Linux product archive
   PRODUCT_DIR="sandbox_product/target/products"
   PRODUCT_ARCHIVE=$(find "$PRODUCT_DIR" -maxdepth 1 -type f -name "*linux*.tar.gz" | head -1)
   
   TEMP_EXTRACT="/tmp/product-extract"
   mkdir -p "$TEMP_EXTRACT"
   tar -xzf "$PRODUCT_ARCHIVE" -C "$TEMP_EXTRACT"
   
   # Copy product components to Docker context
   cp -r "$TEMP_EXTRACT/client/plugins" "docker-context/dist/"
   cp -r "$TEMP_EXTRACT/client/configuration" "docker-context/dist/"
   
   # Create launcher script (see workflow for full script content)
   mkdir -p "docker-context/dist/bin"
   # ... (copy launcher script creation from publish-cleanup-image.yml)
   
   # Copy Dockerfile and entrypoint
   cp sandbox_cleanup_docker/Dockerfile docker-context/
   cp sandbox_cleanup_docker/entrypoint.sh docker-context/
   chmod +x docker-context/entrypoint.sh
   ```
   
   For the complete and up-to-date procedure, refer to the "Extract product and prepare Docker context" step in [publish-cleanup-image.yml](../.github/workflows/publish-cleanup-image.yml).

3. Build the Docker image:
   ```bash
   docker build -t sandbox-cleanup docker-context/
   ```

**Note:** In production, the image is built automatically by GitHub Actions. Manual builds are only needed for local development and testing.

## Image Details

- **Base Image:** `eclipse-temurin:21-jre-noble`
- **User:** Runs as non-root user `sandbox` (UID/GID assigned by system)
- **Working Directory:** `/workspace`
- **Entrypoint:** Starts Xvfb and runs the cleanup tool
- **Cleanup Application:** `/opt/sandbox-cleanup/bin/sandbox-cleanup`

## Exit Codes

| Code | Meaning |
|------|---------|
| 0    | Success - No changes needed or changes applied successfully |
| 1    | Error - Cleanup failed |
| 2    | Changes detected (check/diff mode only) |

## Security

- Runs as non-root user (`sandbox`)
- Workspace is mounted read-write only as needed (use read-only mount for check/diff modes if desired)
- No network access needed during cleanup execution
- Minimal dependencies for reduced attack surface
