# Refactoring Mining CLI (`sandbox_mining_cli`)

> **Navigation**: [Main README](../README.md)

## Overview

`sandbox_mining_cli` is the standalone command-line interface for the refactoring mining tool. It clones Git repositories, scans Java source code against `.sandbox-hint` rules, and generates JSON and Markdown reports of matching patterns.

## Usage

```bash
java -jar sandbox-mining-cli.jar [options]
  --config <path>    Path to repos.yml configuration file
  --hints <dir>      Directory with .sandbox-hint files (overrides config)
  --repo <url>       Single repo to scan (ad-hoc mode, overrides config)
  --output <dir>     Output directory for reports (default: mining-results)
  --format <fmt>     Report format: markdown|json|both (default: both)
  --dry-run          Only count matches, don't generate candidate files
```

## Example

```bash
# Scan configured repositories against bundled hint files
java -jar sandbox-mining-cli.jar \
  --config .github/refactoring-mining/repos-eclipse-2025.yml \
  --output output/run-1

# Scan a single repo ad-hoc
java -jar sandbox-mining-cli.jar \
  --repo https://github.com/eclipse-jdt/eclipse.jdt.ui \
  --hints sandbox_common/sandbox-hints/ \
  --output output/adhoc
```

## Output

Reports are written to the `--output` directory:

- `report.md` — Markdown summary with matched patterns per repository
- `report.json` — Machine-readable JSON with full match details
- `candidates/` — Generated `.sandbox-hint` candidate files (unless `--dry-run`)

## Architecture

```
MiningCli (entry point)
  → MiningConfig (YAML config parsing)
  → RepoCloner (JGit-based repository cloning)
  → StandaloneAstParser (Eclipse JDT AST parser in standalone mode)
  → SourceScanner (applies .sandbox-hint rules to source files)
  → JsonReporter / MarkdownReporter (result formatting)
```

## Building

```bash
# Build the fat JAR
mvn package -pl sandbox_mining_cli
```

The resulting JAR is at `sandbox_mining_cli/target/sandbox-mining-cli-*.jar`.

## Related Modules

- **[sandbox_mining_core](../sandbox_mining_core/README.md)** — AI-assisted rule inference from commit diffs
- **[sandbox_common_core](../sandbox_common_core/README.md)** — TriggerPattern engine and HelperVisitor API
