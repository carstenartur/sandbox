# Sandbox Common Module

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Sandbox Common** module provides shared utilities, constants, and base classes used across all sandbox cleanup plugins. This module serves as the foundation for the entire sandbox ecosystem, centralizing common infrastructure to avoid duplication and ensure consistency.

## Key Features

- 🔧 **Shared Utilities** - AST manipulation, annotation handling, naming utilities
- 📋 **Cleanup Constants** - Central repository for all cleanup-related constants (`MYCleanUpConstants`)
- 🏗️ **Base Classes** - Reusable base classes and interfaces for cleanup implementations
- 🔄 **Eclipse JDT Compatibility** - Mirrors Eclipse JDT structure for easy porting
- 🗂️ **Planned Multi-file Cleanups** - Project-wide semantic planning with one local change per compilation unit

## Core Components

### MYCleanUpConstants

Central repository for all cleanup-related constants used across sandbox plugins.

**Location**: `org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants`

**Purpose**:
- Cleanup enablement keys (cleanup IDs, preference keys)
- Configuration options for each cleanup type
- Default values for cleanup settings

**Eclipse JDT Mapping**: Corresponds to Eclipse's `CleanUpConstants` class. When porting to Eclipse JDT, constants are merged into `org.eclipse.jdt.internal.corext.fix.CleanUpConstants`.

### Planned Multi-file Cleanups

The package `org.sandbox.jdt.cleanup.multifile` supports migrations that must analyse several compilation units together while retaining Eclipse's existing cleanup preview, fixpoint processing, application, and undo.

- `AbstractPlannedMultiFileCleanUp<P>` creates one immutable plan in `checkPreConditions` and emits the current file's change from `createFix`.
- `IMultiFileCleanUpScopeProvider` lets the patched JDT UI cleanup orchestrator add related source files before planning.
- `SelectedCompilationUnitPlan` and `JavaProjectCompilationUnits` provide stable Java-model-based scope handling.

See the [multi-file cleanup architecture](../docs/multi-file-cleanups.md) and the [developer cheatsheet](../docs/multi-file-cleanup-cheatsheet.md).

### Shared Utilities

The module provides common utilities used by multiple cleanup implementations:

#### AST Manipulation
- `HelperVisitor` - Base visitor for AST traversal
- `ASTProcessor` - AST processing utilities
- `ASTNavigationUtils` - Navigation helpers for AST trees

#### Annotation Utilities
- Check for annotations
- Find annotations by name
- Remove annotations
- Get annotation values

#### Naming Utilities
- Case conversions (camelCase, PascalCase, snake_case)
- Java identifier validation
- Checksum generation for unique naming

#### Type Resolution
- Type checking utilities
- Type binding resolution
- Type hierarchy navigation

#### Import Management
- Add/remove imports
- Organize imports
- Static import handling

## Package Structure

```
org.sandbox.jdt.cleanup.multifile.*      - Planned multi-file cleanup lifecycle
org.sandbox.jdt.internal.corext.fix2.*   - Core cleanup constants and utilities
org.sandbox.jdt.internal.ui.*            - Shared UI components
```

**Porting to Eclipse JDT**:
- Replace `sandbox` with `eclipse` in package names
- Constants from `MYCleanUpConstants` merge into Eclipse's `CleanUpConstants`
- Utilities distributed to appropriate Eclipse modules

## Usage

All sandbox plugins depend on `sandbox_common` for:

1. **Cleanup Constants** - Each plugin registers cleanup IDs and preferences via constants
2. **Shared Utilities** - Common code transformation logic
3. **Configuration** - Standard configuration patterns for UI preferences
4. **Multi-file Planning** - Coordinated semantic plans shared by int-to-enum and JUnit migration

### Dependency Graph

```
sandbox_common (this module)
    ↑
    ├── sandbox_common_core (lightweight Plain Maven JAR extraction)
    ├── sandbox_encoding_quickfix
    ├── sandbox_platform_helper
    ├── sandbox_functional_converter
    ├── sandbox_junit_cleanup
    ├── sandbox_jface_cleanup
    ├── sandbox_tools
    ├── sandbox_xml_cleanup
    ├── sandbox_usage_view
    └── sandbox_extra_search
```

`sandbox_common_core` is a lightweight Plain Maven JAR extraction of `sandbox_common` without JDT UI dependencies. It contains the TriggerPattern engine, pattern matching, hint file parsing, batch transformation processing, HelperVisitor utilities, and the UI-independent multi-file scope contracts. It can be used standalone outside Eclipse for CLI tools, testing, and refactoring mining.

## Design Principles

### 1. Single Source of Truth
All constants related to cleanup registration and configuration are defined once to avoid duplication.

### 2. Minimal Dependencies
This module has minimal external dependencies to avoid circular dependencies and keep the architecture clean.

### 3. Eclipse JDT Compatibility
Structure mirrors Eclipse JDT's organization to facilitate easy porting for upstream contribution.

### 4. Atomic Multi-file Changes
Project-wide analysis is completed before per-file changes are emitted. A stale required target aborts the coordinated candidate instead of leaving a partially migrated codebase.

## TriggerPattern Engine

The module includes a powerful pattern matching engine for code transformations:

### Features
- Expression and statement pattern parsing
- Placeholder matching with `$x` syntax
- AST traversal and matching
- Annotation-based hint registration (`@TriggerPattern`, `@Hint`)
- Quick Assist processor integration

### Usage Example

```java
@Hint("Simplify increment")
@TriggerPattern("$x = $x + 1")
public void simplifyIncrement(HintContext ctx) {
    // Transform to $x++
}
```

See [TRIGGERPATTERN.md](TRIGGERPATTERN.md) for detailed documentation.

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Detailed design and component descriptions
- **[Multi-file Cleanup Architecture](../docs/multi-file-cleanups.md)** - Lifecycle, scope expansion, OSGi delivery, and consumers
- **[Multi-file Cleanup Cheatsheet](../docs/multi-file-cleanup-cheatsheet.md)** - Templates, checklists, and common mistakes
- **[TODO](TODO.md)** - Pending enhancements and maintenance tasks
- **[TRIGGERPATTERN.md](TRIGGERPATTERN.md)** - Pattern matching engine documentation
- **[Testing Guide](../sandbox_common_test/TESTING.md)** - HelperVisitor API test suite

## Important Maintenance Note

⚠️ **Monitor Eclipse JDT AST Changes**: When Eclipse JDT introduces new AST node types, the helper APIs in this module (HelperVisitor, ASTProcessor, etc.) should be extended to support them. Regular review of Eclipse JDT releases ensures complete AST coverage.

See [TODO.md](TODO.md) for the monitoring checklist.

## Eclipse JDT Integration

### Current State
This module corresponds to parts of Eclipse JDT's:
- `org.eclipse.jdt.core.manipulation` - Core cleanup constants
- `org.eclipse.jdt.ui` - UI-related constants and utilities

### Contribution Path
When contributing cleanups to Eclipse JDT:
1. Constants from `MYCleanUpConstants` are merged into Eclipse's `CleanUpConstants`
2. Shared utilities may be distributed to appropriate Eclipse modules
3. Package names change from `org.sandbox` to `org.eclipse`
4. Multi-file scope expansion can become a typed JDT API after the Sandbox implementation has validated the lifecycle

## Testing

Direct tests cover the planned multi-file lifecycle; dependent plugin tests cover real coordinated transformations.

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_common_test
```

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Modules**: All sandbox cleanup plugins depend on this module. See [Main README](../README.md#projects) for the complete list.
