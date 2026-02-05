# JFace Cleanup Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **JFace Cleanup** plugin modernizes Eclipse JFace code by migrating deprecated `SubProgressMonitor` usage to the recommended `SubMonitor` API. This improves code maintainability and follows Eclipse Platform best practices.

## Key Features

- 🔄 **SubProgressMonitor → SubMonitor** - Automatic migration to modern progress API
- 🎯 **Style Flag Mapping** - Maps `SUPPRESS_SUBTASK_LABEL` to `SUPPRESS_SUBTASK`
- 🗑️ **Flag Dropping** - Removes `PREPEND_MAIN_LABEL_TO_SUBTASK` (no SubMonitor equivalent)
- 📦 **Variable Name Management** - Generates unique variable names to avoid conflicts
- ♻️ **Idempotent** - Running cleanup multiple times produces the same result
- 🔌 **Eclipse Integration** - Works seamlessly with Eclipse RCP/JFace code

## Quick Start

### Enable in Eclipse

1. Open **Source** → **Clean Up...**
2. Navigate to the **JFace** category
3. Enable **Migrate SubProgressMonitor to SubMonitor**

### Example Transformations

**Basic Transformation:**
```java
// Before
monitor.beginTask("Task", 100);
IProgressMonitor subMonitor = new SubProgressMonitor(monitor, 50);

// After
SubMonitor subMonitor = SubMonitor.convert(monitor, "Task", 100);
IProgressMonitor sub = subMonitor.split(50);
```

**With SUPPRESS_SUBTASK_LABEL Flag:**
```java
// Before
monitor.beginTask("Task", 100);
SubProgressMonitor sub = new SubProgressMonitor(
    monitor, 50, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL);

// After
SubMonitor subMonitor = SubMonitor.convert(monitor, "Task", 100);
IProgressMonitor sub = subMonitor.split(50, SubMonitor.SUPPRESS_SUBTASK);
```

**With PREPEND_MAIN_LABEL_TO_SUBTASK Flag (Dropped):**
```java
// Before
monitor.beginTask("Task", 100);
SubProgressMonitor sub = new SubProgressMonitor(
    monitor, 50, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK);

// After - flag is dropped as there's no equivalent in SubMonitor
SubMonitor subMonitor = SubMonitor.convert(monitor, "Task", 100);
IProgressMonitor sub = subMonitor.split(50);
```

**Unique Variable Names:**
```java
// Before
String subMonitor = "test";
monitor.beginTask("Task", 100);
IProgressMonitor sub = new SubProgressMonitor(monitor, 50);

// After
String subMonitor = "test";
SubMonitor subMonitor2 = SubMonitor.convert(monitor, "Task", 100);
IProgressMonitor sub = subMonitor2.split(50);  // Unique name generated
```

## Migration Pattern

The cleanup transforms `beginTask` + `SubProgressMonitor` to `SubMonitor.convert` + `split`:

```
monitor.beginTask(msg, work);
new SubProgressMonitor(monitor, ticks)
    ↓
SubMonitor subMonitor = SubMonitor.convert(monitor, msg, work);
subMonitor.split(ticks)
```

With `SUPPRESS_SUBTASK_LABEL` flag:

```
new SubProgressMonitor(monitor, ticks, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL)
    ↓ (in beginTask context)
subMonitor.split(ticks, SubMonitor.SUPPRESS_SUBTASK)
```

With `PREPEND_MAIN_LABEL_TO_SUBTASK` flag:

```
new SubProgressMonitor(monitor, ticks, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK)
    ↓ (flag is dropped - no equivalent in SubMonitor)
subMonitor.split(ticks)
```

## Why Migrate?

### Benefits of SubMonitor

- **More Flexible** - Better handling of progress allocation
- **Recommended API** - Eclipse Platform's preferred progress monitoring approach
- **Better Nesting** - Simplified handling of nested progress monitors
- **Modern Pattern** - Fluent API design with method chaining

### SubProgressMonitor Issues

- **Deprecated** - No longer recommended by Eclipse Platform
- **Less Flexible** - Limited options for progress subdivision
- **Verbose** - Requires more boilerplate code

## Features

### Unique Variable Name Handling

The cleanup ensures variable names don't conflict:
- Detects existing variable names in scope
- Generates unique names (e.g., `subMonitor2`, `subMonitor3`)
- Maintains code correctness during transformation

### Idempotence

Running the cleanup multiple times produces identical results:
- Already converted code is not modified again
- Transformation is deterministic and stable

### Official Eclipse Documentation

This cleanup aligns with Eclipse Platform guidelines:
- [SubMonitor JavaDoc](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/SubMonitor.html)
- [Progress Monitoring Best Practices](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/runtime_progress.htm)

## Requirements

- **Eclipse Platform 3.8+** - SubMonitor is available since Eclipse 3.8
- **Java 8+** - Compatible with Java 8 and later

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Implementation details and AST visitor patterns
- **[TODO](TODO.md)** - Future enhancements
- **[Main README](../README.md#7-sandbox_jface_cleanup)** - Detailed examples

## Testing

Comprehensive tests in `sandbox_jface_cleanup_test`:
- Basic transformation tests
- Style flag handling tests
- Variable name uniqueness tests
- Idempotence verification

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_jface_cleanup_test
```

## Limitations

- Combined flag expressions using bitwise OR (e.g., `FLAG1 | FLAG2`) or numeric flag literals are not automatically mapped and require manual review
- Custom SubProgressMonitor subclasses require manual review
- Only handles SubProgressMonitor instances with a corresponding beginTask call in the same scope
- Some rare edge cases may need manual adjustment

See [TODO.md](TODO.md) for planned improvements.

## Contributing to Eclipse JDT

This plugin is designed for easy integration into Eclipse JDT:
1. Replace `org.sandbox` with `org.eclipse` in package names
2. Move classes to `org.eclipse.jdt.internal.corext.fix`
3. Update cleanup registration

See [Architecture](ARCHITECTURE.md) for implementation patterns.

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Plugins**: [Platform Helper](../sandbox_platform_helper/), [JUnit Cleanup](../sandbox_junit_cleanup/)
