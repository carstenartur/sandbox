# Sandbox Common Module

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Sandbox Common** module provides shared utilities, constants, and base classes used across all sandbox cleanup plugins. This module serves as the foundation for the entire sandbox ecosystem, centralizing common infrastructure to avoid duplication and ensure consistency.

## Key Features

- 🔧 **Shared Utilities** - AST manipulation, annotation handling, naming utilities
- 📋 **Cleanup Constants** - Central repository for all cleanup-related constants (`MYCleanUpConstants`)
- 🏗️ **Base Classes** - Reusable base classes and interfaces for cleanup implementations
- 🔄 **Eclipse JDT Compatibility** - Mirrors Eclipse JDT structure for easy porting

## Core Components

### MYCleanUpConstants

Central repository for all cleanup-related constants used across sandbox plugins.

**Location**: `org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants`

**Purpose**:
- Cleanup enablement keys (cleanup IDs, preference keys)
- Configuration options for each cleanup type
- Default values for cleanup settings

**Eclipse JDT Mapping**: Corresponds to Eclipse's `CleanUpConstants` class. When porting to Eclipse JDT, constants are merged into `org.eclipse.jdt.internal.corext.fix.CleanUpConstants`.

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
org.sandbox.jdt.internal.corext.fix2.*  - Core cleanup constants and utilities
org.sandbox.jdt.internal.ui.*          - Shared UI components
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

### Dependency Graph

```
sandbox_common (this module)
    ↑
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

## Design Principles

### 1. Single Source of Truth
All constants related to cleanup registration and configuration are defined once to avoid duplication.

### 2. Minimal Dependencies
This module has minimal external dependencies to avoid circular dependencies and keep the architecture clean.

### 3. Eclipse JDT Compatibility
Structure mirrors Eclipse JDT's organization to facilitate easy porting for upstream contribution.

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

## Testing

Testing is primarily indirect through dependent plugins. Future improvements planned:
- Direct unit tests for utility methods
- Integration tests for constant integrity
- Validation of unique cleanup IDs

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_common_test
```

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Modules**: All sandbox cleanup plugins depend on this module. See [Main README](../README.md#projects) for the complete list.
