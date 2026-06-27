# Hint File Validation Tools

This directory contains Python scripts for validating and maintaining `.sandbox-hint` files.

## Scripts

### `scan_hints.py`

Scans all `.sandbox-hint` files in the repository and reports suspicious patterns that would cause build failures or incorrect behavior.

**Usage:**
```bash
python tools/scripts/scan_hints.py [path]
```

**Checks performed:**
- Multiple `<!id:>` directives per file (overwrites silently)
- Legacy `/*!key: value*/` per-rule directives (silently stripped)
- Unbound placeholders in replacements (build failure)
- NOOP rules (source == replacement)
- Malformed FQN-placeholder mixes
- Missing source patterns

**Exit codes:**
- 0: No issues found
- 1: Issues found

### `fix_legacy_hint_directives.py`

Automatically fixes legacy `/*!key: value*/` per-rule directives by converting them to proper `@key:` annotations.

**Usage:**
```bash
python tools/scripts/fix_legacy_hint_directives.py [path]
```

**What it does:**
- Converts `/*!id: value*/` to `@id: value`
- Converts `/*!severity: value*/` to `@severity: value`
- Converts `/*!description: value*/` to proper file-level `<!description: value>` or `@description: value` as appropriate
- Removes `/*!minJavaVersion: value*/` and `/*!tags: value*/` (not supported per-rule)

## Integration

These scripts are run as part of the build process via `HintFileResourcesValidationTest` (JUnit) and can be integrated into CI pipelines.

## Dependencies

- Python 3.6+
- No external dependencies

## Contributing

When adding new hint files or modifying existing ones, run `scan_hints.py` to ensure compliance with the DSL rules.