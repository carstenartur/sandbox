# Sandbox Cleanup CLI Distribution

This module packages the Eclipse-based cleanup application into a distributable archive
(zip/tar.gz) that can be run standalone on any machine with Java 21+.

## Contents

```
sandbox-cleanup-cli-<version>/
├── bin/
│   ├── sandbox-cleanup        # Unix/Linux/Mac launcher script
│   └── sandbox-cleanup.bat    # Windows launcher script
├── plugins/                   # Eclipse/Equinox bundles (from product build)
├── configuration/             # OSGi configuration
├── VERSION                    # Tool version file
└── README.md                  # This file
```

## Prerequisites

- **Java 21+** must be installed and available via `JAVA_HOME` or `PATH`

## Usage

```bash
# Apply cleanups
bin/sandbox-cleanup --config cleanup.properties --source /path/to/project

# Check for needed changes (exit code 2 if changes detected)
bin/sandbox-cleanup --config cleanup.properties --mode check --source /path/to/project

# Generate diff output
bin/sandbox-cleanup --config cleanup.properties --mode diff --source /path/to/project

# Generate patch file and JSON report
bin/sandbox-cleanup --config cleanup.properties --mode check \
    --patch changes.patch --report report.json --source /path/to/project
```

## Exit Codes

| Code | Meaning |
|------|---------|
| 0    | Success (no changes needed in check, or applied in apply mode) |
| 1    | Error (parsing, IO, config invalid) |
| 2    | Changes detected/needed (check/diff mode) |

## Environment Variables

- `JAVA_HOME` — Path to Java 21+ installation
- `SANDBOX_CLEANUP_WORKSPACE` — Custom Eclipse workspace directory (optional)

## Build

This distribution is assembled from the `sandbox_product` build output.
Build with Maven profiles:

```bash
mvn -Pproduct verify
```

The distribution archive will be in `sandbox_cleanup_cli_dist/target/`.
