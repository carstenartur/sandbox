# Sandbox Headless Cleanup Application

> **Navigation:** [Main README](../README.md) · [Installed Help](../sandbox_cleanup_application_help/html/index.html)

Runs the cleanup implementations installed in an Eclipse/Equinox distribution without opening the workbench.

## Application ID

```text
sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup
```

## Recommended first run

Use a disposable workspace and check mode before allowing writes:

```bash
eclipse -nosplash \
  -data /tmp/sandbox-cleanup-workspace \
  -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  --import-project "$PWD" \
  --mode check \
  --scope both \
  --config "$PWD/cleanup.properties" \
  --report /tmp/sandbox-cleanup-report.json \
  "$PWD"
```

`--import-project` requires an Eclipse `.project` file. Cleanup inputs must map to files inside the selected workspace after import.

## Exit-code contract

| Code | Meaning |
|---:|---|
| `0` | Help; successful apply; or check/diff found no required changes |
| `1` | Invocation, configuration, workspace/import, processing, refactoring, patch, or report error |
| `2` | Check/diff found required changes and no error occurred |

**Error `1` takes precedence over change result `2`.** A partial failure is never reported merely as “cleanup required.” Diagnostics remain on stderr under `--quiet`.

## Modes

- `--mode apply` writes cleanup changes. This is the default.
- `--mode check` applies the refactoring in the running workspace only long enough to detect changes, restores the original source bytes, and returns `2` when changes are required.
- `--mode diff` has the same restoration and exit behavior and prints a simple unified diff unless `--quiet` is active.

## Arguments

| Option | Contract |
|---|---|
| `-config`, `--config <file>` | Required Java properties file with Eclipse cleanup option keys |
| `--import-project <dir>` | Imports one checked-out Eclipse project into the selected workspace |
| `--source <path>` | Adds a source file or directory; positional paths are also accepted |
| `--scope main\|test\|both` | Selects Java paths by exact directory segments named `test` or `tests`; default `both` |
| `--patch <file>` | Writes collected diff output for changed files |
| `--report <file>` | Writes JSON execution evidence |
| `-quiet`, `--quiet` | Suppresses progress/diff output, not errors |
| `-verbose`, `--verbose` | Prints each processed file and output artifact |
| `-help`, `--help` | Prints built-in usage and exits `0` |

`--quiet` and `--verbose` are mutually exclusive.

### Scope behavior

For `main` and `test`, the public application wrapper expands directory roots into a deterministic sorted list of concrete Java files before cleanup begins. This makes a project root, `src`, `src/test`, and individual source files obey the same rule:

- `main`: no exact `test`/`tests` path segment;
- `test`: at least one exact `test`/`tests` path segment;
- `both`: caller inputs are left unchanged.

A valid scoped directory with no matching Java files is a successful zero-file run.

## Cleanup configuration

The configuration is a standard Java properties file. Keys are interpreted by the cleanup implementations installed in the running Eclipse distribution.

```properties
cleanup.format_source_code=true
cleanup.organize_imports=true
cleanup.remove_unused_imports=true
cleanup.add_missing_override_annotations=true
```

Do not assume every key is available in every target platform. Keep the target and installed Sandbox features reproducible.

## Output and evidence

A successful check run with two required changes writes progress similar to:

```text
Using configuration file: /work/cleanup.properties
Starting cleanup...
Mode: check
Cleanup done.
Files changed: 2
```

An invalid source mapping is written to stderr and produces exit `1`:

```text
Skipping file outside workspace: /work/other/Outside.java
```

The JSON report contains:

- tool/version, mode, and scope;
- start/end time and duration;
- processed and changed-file counts;
- changed paths;
- `errorCount` and collected `errors`.

If the report itself cannot be written, the process exits `1`; that failure naturally cannot be stored inside the missing report.

## CI integration

Handle `2` separately from execution errors:

```bash
set +e
eclipse -nosplash \
  -data "$RUNNER_TEMP/sandbox-cleanup-workspace" \
  -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  --import-project "$PWD" \
  --mode check \
  --config "$PWD/cleanup.properties" \
  --report "$RUNNER_TEMP/sandbox-cleanup-report.json" \
  "$PWD"
status=$?
set -e

case "$status" in
  0) echo "Cleanup check passed" ;;
  2) echo "Cleanup changes are required" >&2; exit 2 ;;
  *) echo "Cleanup execution failed" >&2; exit "$status" ;;
esac
```

Archive the JSON report and stderr when diagnosing CI failures.

## Safety and limitations

- Keep source under version control and start with `check` or `diff`.
- A file outside the workspace is an error, not a successful skip.
- Cleanup behavior depends on resolved Eclipse projects, classpaths, bindings, and installed cleanup bundles.
- The scope filter recognizes conventional directories named exactly `test` or `tests`; use explicit roots with `both` for custom source sets.
- The generated diff is review evidence, not a rename- or binary-aware replacement for Git diff.
- Patch/report write failures and source restoration failures produce exit `1`.

## Implementation structure

- `ScopeFilteringCodeCleanupApplicationWrapper` is the registered Equinox entry point and resolves scoped roots.
- `CodeCleanupApplicationWrapper` validates/imports the workspace project and maps collected errors to process exit `1`.
- `CodeCleanupApplication` parses cleanup arguments, executes refactorings, restores dry-run sources, and produces patch/JSON evidence.

## Verification

```bash
xvfb-run --auto-servernum mvn --no-transfer-progress \
  -pl sandbox_cleanup_application_test -am verify
```

Regression tests cover the public exit-code path, quiet-mode errors, project-import argument failures, and main/test/both scope expansion.

## License

Eclipse Public License 2.0, with the secondary-license terms declared in the source headers.
