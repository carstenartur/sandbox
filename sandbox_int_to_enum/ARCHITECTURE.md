# Architecture: Int to Enum/Switch Refactoring Plugin

## Overview

This plugin provides a cleanup that automatically refactors integer constant-based if-else chains into enum-based switch statements. This transformation improves code maintainability, type safety, and readability.

## Problem Statement

Code that uses integer constants in if-else chains is less type-safe and harder to maintain:

```java
public static final int STATUS_PENDING = 0;
public static final int STATUS_APPROVED = 1;
public static final int STATUS_REJECTED = 2;

public void handleStatus(int status) {
    if (status == STATUS_PENDING) {
        // handle pending
    } else if (status == STATUS_APPROVED) {
        // handle approved
    } else if (status == STATUS_REJECTED) {
        // handle rejected
    }
}
```

## Solution

The plugin transforms integer constant-based code in two ways:

### 1. Switch with int constants → Switch with enum (Implemented)

When code already has a switch statement using int constants:

```java
public static final int STATUS_PENDING = 0;
public static final int STATUS_APPROVED = 1;
public static final int STATUS_REJECTED = 2;

public void handleStatus(int status) {
    switch (status) {
        case STATUS_PENDING:
            // handle pending
            break;
        case STATUS_APPROVED:
            // handle approved
            break;
        case STATUS_REJECTED:
            // handle rejected
            break;
    }
}
```

Is transformed into:

```java
public enum Status {
    PENDING, APPROVED, REJECTED
}

public void handleStatus(Status status) {
    switch (status) {
        case PENDING:
            // handle pending
            break;
        case APPROVED:
            // handle approved
            break;
        case REJECTED:
            // handle rejected
            break;
    }
}
```

### 2. If-else chain with int constants → Switch with enum (Planned)

When code uses if-else chains comparing against int constants:

```java
public static final int STATUS_PENDING = 0;
public static final int STATUS_APPROVED = 1;
public static final int STATUS_REJECTED = 2;

public void handleStatus(int status) {
    if (status == STATUS_PENDING) {
        // handle pending
    } else if (status == STATUS_APPROVED) {
        // handle approved
    } else if (status == STATUS_REJECTED) {
        // handle rejected
    }
}
```

Will be transformed into an enum with switch statement (not yet implemented).

## Implementation Design

### Package Structure

Following the standard Eclipse JDT cleanup plugin pattern with helper structure:

- `org.sandbox.jdt.internal.corext.fix` - Core transformation logic
  - `IntToEnumFixCore` - Enum containing transformation operations following JfaceCleanUpFixCore pattern
  - Uses helper pattern for transformation logic
  - Uses ReferenceHolder from sandbox_common for tracking patterns

- `org.sandbox.jdt.internal.corext.fix.helper` - Helper classes for transformation
  - `AbstractTool<T>` - Base class with common helper methods (addImport, getUsedVariableNames)
  - `IntToEnumHelper` - If-else chain transformation logic (placeholder)
  - `SwitchIntToEnumHelper` - Switch statement transformation logic (implemented)
  - `IntConstantHolder` - Data structure for tracking int constants and their usage

- `org.sandbox.jdt.internal.ui.fix` - UI wrapper
  - `IntToEnumCleanUp` - Wrapper class extending AbstractCleanUpCoreWrapper
  - `IntToEnumCleanUpCore` - Core cleanup implementation using CompilationUnitRewriteOperationWithSourceRange

- `org.sandbox.jdt.internal.ui.preferences.cleanup` - UI configuration
  - `SandboxCodeTabPage` - Configuration UI
  - `DefaultCleanUpOptionsInitializer` - Default options
  - `SaveActionCleanUpOptionsInitializer` - Save action options

### Detection Pattern

The plugin detects:
1. Multiple `static final int` constants in the same class with a common name prefix
2. Switch statements that use these constants as case labels (SWITCH_INT_TO_ENUM)
3. If-else chains that compare a variable against these constants (IF_ELSE_TO_SWITCH, planned)

### Transformation Steps

#### SWITCH_INT_TO_ENUM (Implemented)
1. **Detect Constants**: Find `static final int` field declarations
2. **Find Switch Statements**: Locate switch statements referencing these constants as case labels
3. **Validate**: Ensure at least 2 constants with a common prefix are used
4. **Create Enum**: Generate enum type from constant names (prefix → enum name, suffix → value)
5. **Replace Fields**: Remove old int constant field declarations
6. **Update Cases**: Replace constant references in switch cases with enum values
7. **Update Types**: Change method parameter types from int to the new enum type

#### IF_ELSE_TO_SWITCH (Planned)
1. **Detect Pattern**: Find int constants used in if-else chains using AST visitors
2. **Analyze Scope**: Determine which constants belong together
3. **Create Enum**: Generate enum type with appropriate name and values
4. **Convert If-Else to Switch**: Transform if-else chain into switch statement using enum
5. **Update Variable Types**: Change parameter/variable types from int to enum

### Helper Pattern Implementation

Following the established pattern from JFaceCleanUpFixCore:

- **IntToEnumFixCore enum** holds AbstractTool instances (IntToEnumHelper, SwitchIntToEnumHelper)
- **ReferenceHolder<Integer, IntConstantHolder>** from sandbox_common stores found patterns
- **AbstractTool.find()** discovers patterns and creates CompilationUnitRewriteOperationWithSourceRange
- **AbstractTool.rewrite()** performs the actual AST transformation
- **TightSourceRangeComputer** ensures proper source range tracking for refactoring

This pattern provides:
- Clear separation of concerns (detection vs transformation)
- Reusability of common utilities from sandbox_common
- Consistency with other cleanup plugins in the repository
- Type-safe tracking of transformation candidates

### Constraints and Limitations

- Only processes constants defined in the same compilation unit
- Requires at least 2 constants with a common prefix to trigger
- Constants must share a common underscore-delimited prefix (e.g., `STATUS_*`)
- Does not process constants used in other contexts (arithmetic, etc.)
- Conservative approach - only transforms obvious cases

## Eclipse JDT Integration

This plugin follows the standard pattern for Eclipse JDT cleanups:

1. Registered via `org.eclipse.jdt.ui.cleanUps` extension point
2. Integrates with Eclipse's cleanup framework
3. Can be enabled in cleanup preferences
4. Can be used as a save action

## Portability to Eclipse JDT

The package structure maps directly to Eclipse JDT internal packages:
- `org.sandbox.jdt.internal.*` → `org.eclipse.jdt.internal.*`

When porting to Eclipse JDT:
1. Replace package names
2. Merge constants into `CleanUpConstants`
3. Update extension point registrations
4. Add tests to JDT test suite

## Dependencies

- Eclipse JDT Core - AST manipulation
- Eclipse JDT UI - Cleanup framework integration
- Eclipse JDT Core Manipulation - Rewrite operations
- sandbox_common - Shared constants and utilities

## Future Enhancements

See TODO.md for planned improvements and known limitations.
