# Sandbox Cleanup CLI

The Sandbox Cleanup CLI is a command-line tool for running Eclipse JDT cleanup
transformations on Java source code. It supports three execution modes and
produces deterministic, CI-friendly output with optional patch files and JSON
reports.

## Architecture

```
┌─────────────────────────────────────────┐
│              CLI Contract               │
│  (Args, Exit Codes, Patch, Report)      │
├─────────────┬─────────────┬─────────────┤
│  CLI Dist   │ Maven Plugin│ Docker Image│
│  (bin/      │ (sandbox:   │ (GHCR)      │
│  sandbox-   │  check/     │             │
│  cleanup)   │  apply/diff)│             │
├─────────────┴─────────────┴─────────────┤
│         Eclipse/Equinox Engine          │
│    (CodeCleanupApplication + OSGi)      │
└─────────────────────────────────────────┘
```

**One engine, multiple thin frontends.** No logic duplication.

## CLI Reference

### Usage

```bash
sandbox-cleanup --config <file> [OPTIONS] <files or directories>
```

### Options

| Option              | Description                                          | Default |
|---------------------|------------------------------------------------------|---------|
| `--config <file>`   | Cleanup configuration properties file (required)     | —       |
| `--mode <mode>`     | Execution mode: `apply`, `check`, `diff`             | `apply` |
| `--source <path>`   | Source directory (alias for positional path)          | —       |
| `--scope <scope>`   | Scope filter: `main`, `test`, `both`                 | `both`  |
| `--patch <file>`    | Write unified diff patch to file                     | —       |
| `--report <file>`   | Write JSON report to file                            | —       |
| `-verbose`          | Print progress information                           | off     |
| `-quiet`            | Only print errors                                    | off     |
| `-help`             | Display help and exit                                | —       |

### Exit Codes

| Code | Meaning                                                        |
|------|----------------------------------------------------------------|
| 0    | Success: no changes needed (check) or applied (apply)          |
| 1    | Error: parsing, IO, config invalid, etc.                       |
| 2    | Changes detected/needed (check/diff mode)                      |

### Modes

#### `apply` (default)

Applies all cleanup transformations and writes changes to disk.

```bash
sandbox-cleanup --config cleanup.properties --mode apply --source /path/to/project
```

#### `check`

Dry-run mode. Detects changes without modifying files. Returns exit code 2 if
changes would be needed.

```bash
sandbox-cleanup --config cleanup.properties --mode check --source /path/to/project
echo $?  # 0 = clean, 2 = changes needed
```

#### `diff`

Like check, but also prints unified diff output to stdout.

```bash
sandbox-cleanup --config cleanup.properties --mode diff --source /path/to/project
```

## Configuration File

The configuration file is a Java `.properties` file where keys correspond to
Eclipse JDT cleanup option IDs:

```properties
# Standard cleanup options
cleanup.format_source_code=true
cleanup.organize_imports=true
cleanup.remove_unused_imports=true
cleanup.add_missing_override_annotations=true
cleanup.use_lambda=true

# Sandbox-specific cleanups
REMOVE_UNNECESSARY_ARRAY_CREATION=true
USE_STRINGBUILDER=true
```

Predefined profiles are available in `.github/cleanup-profiles/`:
- `minimal.properties` — Basic cleanups only
- `standard.properties` — Balanced approach
- `aggressive.properties` — Maximum transformations

## JSON Report Format

When `--report <file>` is specified, a JSON report is written:

```json
{
  "tool": "sandbox-cleanup",
  "version": "1.2.6-SNAPSHOT",
  "mode": "check",
  "scope": "both",
  "startTime": "2024-01-15T10:30:00Z",
  "endTime": "2024-01-15T10:30:45Z",
  "durationMs": 45000,
  "filesProcessed": 150,
  "filesChanged": 3,
  "changedFiles": [
    "/path/to/project/src/main/java/com/example/Foo.java",
    "/path/to/project/src/main/java/com/example/Bar.java",
    "/path/to/project/src/test/java/com/example/FooTest.java"
  ]
}
```

## Patch File

When `--patch <file>` is specified, a list of changed files is written.
This can be used in CI to identify which files were affected:

```
# Changed: /path/to/project/src/main/java/com/example/Foo.java
# Changed: /path/to/project/src/main/java/com/example/Bar.java
```

## Examples

### CI Check (fail if changes needed)

```bash
sandbox-cleanup \
    --config .github/cleanup-profiles/standard.properties \
    --mode check \
    --report cleanup-report.json \
    --source .
# Exit code 2 → changes needed → CI fails
```

### Apply and Generate Report

```bash
sandbox-cleanup \
    --config cleanup.properties \
    --mode apply \
    --report cleanup-report.json \
    --source src/
```

### Check Only Test Sources

```bash
sandbox-cleanup \
    --config cleanup.properties \
    --mode check \
    --scope test \
    --source .
```

## Distribution

The CLI tool is distributed as:

1. **CLI Distribution** (`sandbox-cleanup-cli-<version>.tar.gz` / `.zip`)
   - Contains launcher scripts, Eclipse plugins, and configuration
   - Requires Java 21+

2. **Maven Plugin** (`sandbox-maven-plugin`)
   - Goals: `sandbox:check`, `sandbox:apply`, `sandbox:diff`
   - Thin wrapper around CLI distribution

3. **Docker Image** (`ghcr.io/carstenartur/sandbox-cleanup`)
   - Self-contained with Java runtime
   - Mount workspace with `-v /path:/workspace`

See also:
- [Maven Plugin Documentation](maven-plugin.md)
- [Docker Documentation](docker.md)
