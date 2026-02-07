# Int to Enum/Switch Refactoring Plugin

## Overview

This Eclipse plugin provides an automated cleanup that transforms integer constant-based if-else chains into type-safe enum-based switch statements.

## Implementation Status

**Note:** This plugin has a complete structural implementation following repository patterns, but the actual transformation logic is currently a placeholder. The transformation from int constants to enums is a complex refactoring that requires:

- Sophisticated AST pattern matching and analysis
- Multi-step coordinated AST rewrites
- Type propagation and scope analysis
- Careful handling of edge cases

The current implementation:
- ✅ Follows all repository patterns (helper structure, ReferenceHolder, etc.)
- ✅ Integrates with Eclipse cleanup framework
- ✅ Has proper UI and configuration
- ⚠️ Returns no transformation operations (prevents incorrect changes)
- 📋 Includes extensive documentation for future implementation

See [TODO.md](TODO.md) for detailed implementation notes and [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

## Features

- Detects integer constants used in if-else chains
- Automatically generates appropriate enum types
- Converts if-else chains to switch statements
- Updates variable types and method signatures
- Improves code maintainability and type safety

## Example Transformation

### Before:
```java
public class OrderProcessor {
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;
    
    public void processOrder(int status) {
        if (status == STATUS_PENDING) {
            System.out.println("Order pending");
        } else if (status == STATUS_APPROVED) {
            System.out.println("Order approved");
        } else if (status == STATUS_REJECTED) {
            System.out.println("Order rejected");
        }
    }
}
```

### After:
```java
public class OrderProcessor {
    public enum Status {
        PENDING, APPROVED, REJECTED
    }
    
    public void processOrder(Status status) {
        switch (status) {
            case PENDING:
                System.out.println("Order pending");
                break;
            case APPROVED:
                System.out.println("Order approved");
                break;
            case REJECTED:
                System.out.println("Order rejected");
                break;
        }
    }
}
```

## Benefits

1. **Type Safety**: Enums provide compile-time type checking
2. **Maintainability**: Clear intent and self-documenting code
3. **IDE Support**: Better autocomplete and refactoring support
4. **Extensibility**: Easy to add new values and behavior

## Usage

### Via Eclipse Cleanup

1. Open Eclipse Preferences
2. Navigate to Java → Code Style → Clean Up
3. Create or edit a cleanup profile
4. Enable "Convert int constants to enum/switch"
5. Run cleanup on your code

### As Save Action

1. Open Eclipse Preferences
2. Navigate to Java → Editor → Save Actions
3. Enable "Perform the selected actions on save"
4. Enable "Additional actions"
5. Configure to include "Convert int constants to enum/switch"

## Configuration Options

- **Minimum Constants**: Minimum number of constants required to trigger transformation (default: 2)
- **Scope**: Whether to transform public/package/private constants
- **Preserve Original**: Option to keep original constants as @Deprecated

## Requirements

- Eclipse 2025-12 or later
- Java 21 or later

## Limitations

- Only transforms constants defined in the same compilation unit
- Requires constants to be used in a clear if-else or switch pattern
- Does not transform constants used in arithmetic operations
- Conservative approach - only transforms obvious cases

## Technical Details

For architecture and implementation details, see [ARCHITECTURE.md](ARCHITECTURE.md).

For planned improvements and known issues, see [TODO.md](TODO.md).

## License

Eclipse Public License 2.0

## Contributing

This is part of the Sandbox project for Eclipse JDT experiments. Contributions following Eclipse JDT patterns are welcome.
