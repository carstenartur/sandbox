# Platform Helper Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Platform Helper** plugin simplifies Eclipse Platform API usage, specifically focusing on Status object creation. It replaces verbose `new Status(...)` constructor calls with cleaner factory methods available in Java 11+, or the `StatusHelper` pattern for Java 8.

## Key Features

- 🎯 **Simplify Status Creation** - Replace verbose `new Status(...)` with factory methods
- 📦 **Java Version Aware** - Uses `Status.error()` / `Status.warning()` on Java 11+
- 🧹 **Reduce Boilerplate** - No need to specify plugin ID in factory methods
- 🔌 **Eclipse Integration** - Works seamlessly with Eclipse Platform code

## Quick Start

### Enable in Eclipse

1. Open **Source** → **Clean Up...**
2. Navigate to the **Platform Helper** category
3. Enable **Simplify Status object creation**

### Example Transformations

**Basic Error Status:**
```java
// Before
IStatus status = new Status(IStatus.ERROR, "plugin.id", "Error message");

// After (Java 11+)
IStatus status = Status.error("Error message", null);
```

**Warning Status:**
```java
// Before
IStatus status = new Status(IStatus.WARNING, MyPlugin.PLUGIN_ID, "Warning");

// After (Java 11+)
IStatus status = Status.warning("Warning", null);
```

**With Exception:**
```java
// Before
IStatus status = new Status(IStatus.ERROR, "plugin.id", "Failed", exception);

// After (Java 11+)
IStatus status = Status.error("Failed", exception);
```

## Supported Factory Methods

The plugin converts to these factory methods (Java 11+):

| Old Constructor | New Factory Method |
|----------------|-------------------|
| `new Status(IStatus.ERROR, ...)` | `Status.error(...)` |
| `new Status(IStatus.WARNING, ...)` | `Status.warning(...)` |
| `new Status(IStatus.INFO, ...)` | `Status.info(...)` |
| `new Status(IStatus.OK, ...)` | `Status.ok(...)` |

## Benefits

- **Cleaner Code** - Factory methods are more concise and readable
- **Less Boilerplate** - No need to specify plugin ID (handled internally)
- **Modern Java** - Follows factory method pattern from Java 8+
- **Eclipse Best Practice** - Aligns with Eclipse Platform 4.12+ conventions

## Java Version Support

| Java Version | Transformation |
|--------------|---------------|
| **Java 8** | StatusHelper pattern (legacy, not actively maintained) |
| **Java 11+** | Factory methods (`Status.error()`, `Status.warning()`, etc.) |

> **Note**: Factory methods are available in Eclipse Platform 4.12+ (2019-06)

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Implementation details and AST visitor patterns
- **[TODO](TODO.md)** - Future enhancements and known issues
- **[Main README](../README.md#platform-status-helper-sandbox_platform_helper)** - Detailed examples

## Testing

Tests are in `sandbox_platform_helper_test`:
- Factory method transformation tests
- Java version compatibility tests
- Edge cases (null values, complex expressions)

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_platform_helper_test
```

## Contributing to Eclipse JDT

This plugin is designed for easy integration into Eclipse JDT:
1. Replace `org.sandbox` with `org.eclipse` in package names
2. Move classes to `org.eclipse.jdt.internal.corext.fix`
3. Update cleanup registration

See [Architecture](ARCHITECTURE.md) for detailed design patterns.

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Plugins**: [Encoding Quickfix](../sandbox_encoding_quickfix/), [JFace Cleanup](../sandbox_jface_cleanup/)
