# Sandbox Cleanup Docker Image

Docker image for running Eclipse JDT cleanup transformations in containers,
CI pipelines, and GitHub Actions.

## Quick Start

```bash
# Run cleanup check
docker run --rm \
    -v /path/to/project:/workspace \
    ghcr.io/carstenartur/sandbox-cleanup:latest \
    --config /workspace/cleanup.properties \
    --mode check \
    --source /workspace
```

## Available Tags

| Tag       | Description                    |
|-----------|--------------------------------|
| `latest`  | Latest stable release          |
| `x.y.z`   | Specific version (immutable)   |

## Usage

### Check Mode (CI)

```bash
docker run --rm \
    -v $(pwd):/workspace \
    ghcr.io/carstenartur/sandbox-cleanup:1.2.6 \
    --config /workspace/.github/cleanup-profiles/standard.properties \
    --mode check \
    --report /workspace/cleanup-report.json \
    --source /workspace
```

### Apply Mode

```bash
docker run --rm \
    -v $(pwd):/workspace \
    ghcr.io/carstenartur/sandbox-cleanup:1.2.6 \
    --config /workspace/cleanup.properties \
    --mode apply \
    --source /workspace
```

### Diff Mode

```bash
docker run --rm \
    -v $(pwd):/workspace \
    ghcr.io/carstenartur/sandbox-cleanup:1.2.6 \
    --config /workspace/cleanup.properties \
    --mode diff \
    --source /workspace
```

## GitHub Actions

```yaml
jobs:
  cleanup-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Run cleanup check
        run: |
          docker run --rm \
            -v ${{ github.workspace }}:/workspace \
            ghcr.io/carstenartur/sandbox-cleanup:latest \
            --config /workspace/.github/cleanup-profiles/standard.properties \
            --mode check \
            --report /workspace/cleanup-report.json \
            --source /workspace
      
      - name: Upload report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: cleanup-report
          path: cleanup-report.json
```

## Building the Image

```bash
# 1. Build the product
mvn -Pproduct verify

# 2. Build the CLI distribution
cd sandbox_cleanup_cli_dist && mvn package && cd ..

# 3. Prepare Docker context
mkdir -p sandbox_cleanup_docker/dist
tar -xzf sandbox_cleanup_cli_dist/target/sandbox-cleanup-cli-*-dist.tar.gz \
    -C sandbox_cleanup_docker/dist --strip-components=1

# 4. Build the image
docker build -t sandbox-cleanup sandbox_cleanup_docker/
```

## Security

- Runs as non-root user `sandbox` (UID/GID configurable)
- No network access required during cleanup
- Mount workspace read-only for check/diff mode:
  ```bash
  docker run --rm -v $(pwd):/workspace:ro sandbox-cleanup --mode check ...
  ```

## Exit Codes

| Code | Meaning                                            |
|------|----------------------------------------------------|
| 0    | Success (no changes needed or applied)             |
| 1    | Error (config, IO, parsing)                        |
| 2    | Changes detected (check/diff mode)                 |

## Environment Variables

| Variable                     | Description                           |
|------------------------------|---------------------------------------|
| `JAVA_HOME`                  | Java installation (pre-set in image)  |
| `SANDBOX_CLEANUP_WORKSPACE`  | Custom Eclipse workspace directory    |
