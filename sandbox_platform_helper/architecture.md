# Platform Helper Plugin - Architecture

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
2. **Status Class Only**: Only handles `org.eclipse.core.runtime.Status` and `org.eclipse.core.runtime.MultiStatus`
3. **Simple Cases**: Complex Status creation patterns may not be transformed
4. **Plugin ID Not Preserved in Status Factory Methods**: The Eclipse Platform Status factory methods (error(), warning(), info()) only accept (message, exception) parameters and do not support plugin ID parameters

## Configuration Options

### Plugin ID Preservation

**Note**: This option is currently not applicable to Status factory method transformations because the Eclipse Platform Status.error(), Status.warning(), and Status.info() methods do not support plugin ID parameters.

The infrastructure for plugin ID preservation is in place for potential future use with other transformations or if Eclipse Platform adds plugin ID support to factory methods.

Default transformation behavior:
- **Always**: `new Status(IStatus.ERROR, "my.plugin", IStatus.OK, "msg", e)` → `Status.error("msg", e)`

The plugin ID from the original constructor call is not preserved because the factory methods don't accept it.

### MultiStatus Simplification

MultiStatus doesn't have factory methods like Status, but the cleanup can normalize the status code:
- Transforms any status code to `IStatus.OK` in MultiStatus constructors
- Example: `new MultiStatus(pluginId, 123, "msg", e)` → `new MultiStatus(pluginId, IStatus.OK, "msg", e)`
- Rationale: MultiStatus overall status is determined by child statuses, so the code should typically be `IStatus.OK`

## Future Enhancements

- Support for custom Status subclasses
- More sophisticated MultiStatus pattern detection (e.g., detecting and optimizing .add() patterns)
- Integration with Eclipse logging framework updates
- If Eclipse Platform adds plugin ID support to Status factory methods, enable plugin ID preservation

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
