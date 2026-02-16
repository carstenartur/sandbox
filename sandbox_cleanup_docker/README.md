# Sandbox Cleanup Docker Image

Docker image for running Eclipse JDT cleanup transformations in containers.

## Quick Start

```bash
# Build (after building the CLI distribution)
docker build -t sandbox-cleanup -f sandbox_cleanup_docker/Dockerfile sandbox_cleanup_docker/

# Run check mode
docker run -v /path/to/project:/workspace sandbox-cleanup \
    --config /workspace/cleanup.properties --mode check --source /workspace

# Run apply mode
docker run -v /path/to/project:/workspace sandbox-cleanup \
    --config /workspace/cleanup.properties --mode apply --source /workspace

# Run diff mode
docker run -v /path/to/project:/workspace sandbox-cleanup \
    --config /workspace/cleanup.properties --mode diff --source /workspace
```

## GitHub Actions Usage

```yaml
- name: Run cleanup check
  run: |
    docker run --rm \
      -v ${{ github.workspace }}:/workspace \
      ghcr.io/carstenartur/sandbox-cleanup:latest \
      --config /workspace/.github/cleanup-profiles/standard.properties \
      --mode check \
      --source /workspace
```

## Building the Image

1. Build the CLI distribution first:
   ```bash
   mvn -Pproduct verify
   cd sandbox_cleanup_cli_dist && mvn package
   ```

2. Copy the distribution into the Docker context:
   ```bash
   cp -r sandbox_cleanup_cli_dist/target/sandbox-cleanup-cli-*/sandbox-cleanup-cli-*/* \
       sandbox_cleanup_docker/dist/
   ```

3. Build the Docker image:
   ```bash
   docker build -t sandbox-cleanup sandbox_cleanup_docker/
   ```

## Exit Codes

| Code | Meaning |
|------|---------|
| 0    | Success |
| 1    | Error |
| 2    | Changes detected (check/diff mode) |

## Security

- Runs as non-root user (`sandbox`)
- Workspace is mounted read-write only for apply mode
- No network access needed during cleanup execution
