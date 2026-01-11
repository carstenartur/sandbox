# Platform Helper Plugin - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#platform_helper) | [TODO](TODO.md)

## Overview

The platform helper plugin simplifies Eclipse Platform API usage, specifically focusing on Status object creation. It replaces verbose `new Status(...)` calls with cleaner factory methods available in Java 11+.

## Purpose

- Simplify `Status` object creation in Eclipse plugins
- Use modern Java 11+ factory methods (`Status.error()`, `Status.warning()`)
- Apply `StatusHelper` pattern for Java 8 compatibility (legacy)
- Reduce boilerplate in plugin error handling code

## Transformation Examples

### Java 11+ Transformation

**Before**:
```java
IStatus status = new Status(IStatus.ERROR, "plugin.id", "Error message");
```

**After**:
```java
IStatus status = Status.error("Error message", null);
```

### Status Creation Patterns

The plugin supports simplification of:
- `new Status(IStatus.ERROR, ...)` → `Status.error(...)`
- `new Status(IStatus.WARNING, ...)` → `Status.warning(...)`
- `new Status(IStatus.INFO, ...)` → `Status.info(...)`
- `new Status(IStatus.OK, ...)` → `Status.ok(...)`

## Core Components

### StatusCleanUp

**Location**: `org.sandbox.jdt.internal.corext.fix.StatusCleanUp`

**Purpose**: Main cleanup implementation for Status object simplification

**Key Methods**:
- `findStatusCreations()` - Identifies `new Status(...)` calls
- `rewrite()` - Transforms to factory methods
- `createFactoryMethodCall()` - Generates simplified factory call

## Version Compatibility

- **Java 11+**: Uses `Status.error()`, `Status.warning()`, etc. factory methods
- **Java 8**: Legacy approach using StatusHelper pattern (no longer actively supported)

The cleanup checks the project's Java version and only applies transformations available in that version.

## Package Structure

- `org.sandbox.jdt.internal.corext.fix.*` - Core cleanup logic
- `org.sandbox.jdt.internal.ui.*` - UI components and preferences

**Eclipse JDT Correspondence**:
- Maps to `org.eclipse.jdt.internal.corext.fix.*` in Eclipse JDT
- Can be ported by replacing `sandbox` with `eclipse` in package paths

## Design Patterns

### AST Visitor Pattern
Identifies Status constructor calls:
```java
compilationUnit.accept(new ASTVisitor() {
    @Override
    public boolean visit(ClassInstanceCreation node) {
        // Check if creating Status object
        return true;
    }
});
```

### Factory Method Pattern
Transforms constructor calls to factory methods:
```java
// Old: new Status(IStatus.ERROR, pluginId, message)
// New: Status.error(message, null)
```

## Eclipse Platform Integration

### Current State
Experimental cleanup for Eclipse Platform code. The factory methods are part of Eclipse Platform 4.12+ (2019-06).

### Benefits
- Cleaner, more readable error handling code
- Less boilerplate (no plugin ID required in factory methods)
- Follows modern Java patterns
- Aligns with Eclipse Platform best practices

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Dependencies**: 
  - Eclipse JDT Core APIs
  - Eclipse Platform APIs (for Status class)
  - `sandbox_common` for cleanup constants

## Testing

### Test Module
`sandbox_platform_helper_test` contains test cases for Status transformations:
- Factory method transformations
- Java version compatibility
- Plugin ID handling
- Exception parameter handling

## Known Limitations

1. **Java 11+ Only**: Requires Java 11 or later (aligned with current Eclipse support)
2. **Status Class Only**: Only handles `org.eclipse.core.runtime.Status`
3. **Simple Cases**: Complex Status creation patterns may not be transformed
4. **Plugin ID Removed**: Factory methods don't include plugin ID (uses default from context)

## Integration Points

### Eclipse Platform Integration

The plugin integrates with Eclipse Platform APIs:

1. **org.eclipse.core.runtime.Status**: Target class for simplification
   - Factory methods available since Eclipse Platform 4.12 (2019-06)
   - Methods: `Status.error()`, `Status.warning()`, `Status.info()`, `Status.ok()`
   - Simplified API reduces boilerplate

2. **Bundle Context**: Plugin ID resolution
   - Factory methods use implicit plugin ID from Bundle-SymbolicName
   - Eliminates need for explicit plugin ID parameter
   - Integrates with OSGi bundle framework

3. **Eclipse Logging**: Status objects integrate with logging
   - ILog.log(IStatus) accepts Status objects
   - Simplified creation improves logging code readability
   - Error reporting remains consistent

### Eclipse JDT AST Integration

Uses Eclipse JDT's AST framework for transformation:

1. **AST Visitor**: Identifies `new Status(...)` constructor calls
   - Visits `ClassInstanceCreation` nodes
   - Checks type binding for `org.eclipse.core.runtime.Status`
   - Extracts severity, message, and exception parameters

2. **AST Rewrite**: Transforms constructor to factory method
   - Replaces `new Status(...)` with `Status.error(...)`
   - Removes plugin ID parameter (no longer needed)
   - Preserves message and exception parameters
   - Updates imports if needed

3. **Type Checking**: Verifies transformation is safe
   - Checks Java version (requires 11+)
   - Verifies Status class is from org.eclipse.core.runtime
   - Ensures factory method is available

### Cleanup Framework Integration

Registered as Eclipse JDT cleanup:

1. **Extension Point**: `org.eclipse.jdt.ui.cleanUps`
   - Cleanup ID defined in `MYCleanUpConstants.PLATFORM_STATUS_CLEANUP`
   - Appears in Eclipse cleanup preferences
   - Can be enabled in Save Actions

2. **Java Version Check**: Only activates for Java 11+
   - Reads project compliance level
   - Disables cleanup for Java 8 projects
   - Aligns with Root README [Build Instructions](../README.md#build-instructions)

## Algorithms and Design Decisions

### Severity Constant Mapping

**Decision**: Map IStatus constants to factory method names

**Algorithm**:
```
IStatus.ERROR   → Status.error(message, exception)
IStatus.WARNING → Status.warning(message, exception)
IStatus.INFO    → Status.info(message, exception)
IStatus.OK      → Status.ok(message, exception)
IStatus.CANCEL  → Not transformed (no factory method)
```

**Rationale**:
- Direct 1:1 mapping simplifies implementation
- Clear, readable factory method names
- CANCEL has no factory (rarely used in creation)

### Plugin ID Elimination Strategy

**Decision**: Remove plugin ID parameter from transformation

**Rationale**:
- Factory methods infer plugin ID from calling bundle context
- Reduces parameter count (4 params → 2 params)
- Eliminates common errors (wrong plugin ID, null plugin ID)
- Aligns with modern Eclipse Platform patterns

**Trade-off**:
- **Pro**: Simpler, less error-prone code
- **Pro**: Follows Eclipse Platform best practices
- **Con**: Plugin ID not explicitly visible in code
- **Con**: Assumes correct Bundle-SymbolicName configuration

**Example**:
```java
// Before: 4 parameters, explicit plugin ID
IStatus status = new Status(IStatus.ERROR, "my.plugin.id", "Error message", exception);

// After: 2 parameters, implicit plugin ID
IStatus status = Status.error("Error message", exception);
```

### Why Only Java 11+?

**Decision**: Only apply cleanup when project uses Java 11+

**Rationale**:
- Factory methods added in Eclipse Platform 4.12 (requires Java 11)
- Sandbox targets Java 21 (see [Build Instructions](../README.md#build-instructions))
- Java 8 support dropped by Eclipse Platform
- Ensures transformed code compiles and runs

**Implementation**:
```java
if (JavaModelUtil.is11OrHigher(project)) {
    // Apply cleanup
}
```

### Exception Parameter Handling

**Decision**: Preserve exception parameter position

**Algorithm**:
```
1. Extract exception parameter (if present)
2. Pass as second parameter to factory method
3. Use null if no exception in original code
```

**Example**:
```java
// With exception
new Status(IStatus.ERROR, pluginId, "msg", ex)  → Status.error("msg", ex)

// Without exception  
new Status(IStatus.ERROR, pluginId, "msg")     → Status.error("msg", null)
```

**Rationale**: This cleanup consistently uses factory method overloads that accept an exception parameter and passes `null` when the original code had no exception, for consistency across all transformations.

## Cross-References

### Root README Sections

This architecture document relates to:

- [5. `sandbox_platform_helper`](../README.md#5-sandbox_platform_helper) - User-facing documentation
- [Build Instructions](../README.md#build-instructions) - Java 11+ requirement
- [Eclipse Version Configuration](../README.md#eclipse-version-configuration) - Platform APIs from Eclipse 2025-09

### Related Modules

- **sandbox_common** - Uses `MYCleanUpConstants` for cleanup IDs
- **Eclipse Platform Runtime** - Target API being simplified
- **sandbox_platform_helper_test** - Test cases covering transformations

## References

- [Status Class API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/Status.html)
- [Eclipse Platform Runtime Guide](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/runtime.htm)
- [OSGi Bundle Context](https://docs.osgi.org/javadoc/r6/core/org/osgi/framework/BundleContext.html)

## Future Enhancements

- Support for custom Status subclasses
- Preserve plugin ID as additional parameter if desired
- Multi-status simplification
- Integration with Eclipse logging framework updates

## Documentation Requirements

### Feature Properties

The corresponding feature module `sandbox_platform_helper_feature` MUST maintain:

1. **feature.properties** - English language properties file containing:
   - `description` - Clear description of the feature's purpose and capabilities
   - `copyright` - Copyright notice with appropriate years
   - `licenseURL` - URL to the Eclipse Public License
   - `license` - Eclipse Public License text or reference

2. **feature_de.properties** - German translation of all properties

These files enable Eclipse's built-in localization mechanism and provide user-facing documentation in the Eclipse IDE. When updating feature capabilities, ensure both property files are updated accordingly.
