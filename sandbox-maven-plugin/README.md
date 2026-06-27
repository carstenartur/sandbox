# Sandbox Cleanup Maven Plugin (`sandbox-maven-plugin`)

> **Navigation**: [Main README](../README.md)

## Overview

`sandbox-maven-plugin` integrates the Sandbox cleanup CLI into Maven builds. It provides goals for applying Eclipse JDT cleanups, checking for required cleanups, and generating diffs — all without requiring an Eclipse IDE installation.

The plugin resolves the `sandbox-cleanup-cli` tool distribution (from GitHub Releases or a local path) and delegates to it.

## Goals

| Goal | Phase | Description |
|------|-------|-------------|
| `sandbox:apply` | — | Apply configured cleanups to source files in-place |
| `sandbox:check` | `verify` | Check whether any cleanups are needed; fails the build if so |
| `sandbox:diff` | — | Generate a diff of pending cleanup changes without modifying files |

## Configuration

Add to your `pom.xml`:

```xml
<plugin>
  <groupId>org.sandbox</groupId>
  <artifactId>sandbox-maven-plugin</artifactId>
  <version>1.2.6-SNAPSHOT</version>
  <configuration>
    <!-- Version of sandbox-cleanup-cli to download (default: 1.2.6-SNAPSHOT) -->
    <toolVersion>1.2.6-SNAPSHOT</toolVersion>
    <!-- Optional: local path or URL to tool distribution archive -->
    <!-- <toolSource>/path/to/sandbox-cleanup-cli.zip</toolSource> -->
  </configuration>
</plugin>
```

## Usage

```bash
# Apply cleanups to source files
mvn sandbox:apply

# Check for cleanup violations (CI mode)
mvn verify -P sandbox-check

# Generate a diff of pending changes
mvn sandbox:diff
```

## Related Modules

- **[sandbox_cleanup_cli_dist](../sandbox_cleanup_cli_dist/README.md)** — CLI distribution packaging
- **[sandbox_cleanup_application](../sandbox_cleanup_application/README.md)** — Equinox-based cleanup application
- **[docs/cleanup-cli.md](../docs/cleanup-cli.md)** — CLI usage guide
