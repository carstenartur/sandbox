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

The plugin transforms this code into:

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

## Implementation Design

### Package Structure

Following the standard Eclipse JDT cleanup plugin pattern:

- `org.sandbox.jdt.internal.corext.fix` - Core transformation logic
  - `IntToEnumFixCore` - Enum containing transformation operations
  - AST visitor to detect int constant patterns
  - Rewrite operations to create enum and switch

- `org.sandbox.jdt.internal.ui.fix` - UI wrapper
  - `IntToEnumCleanUp` - Wrapper class extending AbstractCleanUpCoreWrapper
  - `IntToEnumCleanUpCore` - Core cleanup implementation

- `org.sandbox.jdt.internal.ui.preferences.cleanup` - UI configuration
  - `SandboxCodeTabPage` - Configuration UI
  - `DefaultCleanUpOptionsInitializer` - Default options
  - `SaveActionCleanUpOptionsInitializer` - Save action options

### Detection Pattern

The plugin detects:
1. Multiple `public static final int` constants in the same class
2. If-else chains that compare a variable against these constants
3. Constants that are semantically related (used together in the same if-else chain)

### Transformation Steps

1. **Detect Pattern**: Find int constants used in if-else chains
2. **Analyze Scope**: Determine which constants belong together
3. **Create Enum**: Generate enum with appropriate name and values
4. **Replace Constants**: Update constant references to enum values
5. **Convert If-Else to Switch**: Transform if-else chain into switch statement
6. **Update Variable Types**: Change parameter/variable types from int to enum

### Constraints and Limitations

- Only processes constants defined in the same compilation unit
- Requires at least 2 constants in an if-else chain to trigger
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
