# JFace Cleanup Plugin - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#jface_cleanup) | [TODO](TODO.md)

## Overview

The JFace cleanup plugin provides automated refactorings and modernizations for Eclipse JFace code. JFace is Eclipse's UI toolkit built on top of SWT (Standard Widget Toolkit).

## Purpose

- Modernize JFace API usage
- Replace deprecated JFace methods with current alternatives
- Apply JFace best practices automatically
- Simplify common JFace patterns

## Transformation Examples

### Example Transformations

The plugin focuses on JFace-specific cleanup opportunities such as:
- Simplifying viewer creation patterns
- Updating deprecated dialog APIs
- Modernizing resource management
- Applying JFace coding conventions

## Core Components

### JFaceCleanUp

**Location**: `org.sandbox.jdt.internal.corext.fix.JFaceCleanUp`

**Purpose**: Main cleanup implementation for JFace code modernization

**Key Features**:
- Identifies deprecated JFace API usage
- Applies JFace best practices
- Updates to newer JFace patterns

## Package Structure

- `org.sandbox.jdt.internal.corext.fix.*` - Core cleanup logic
- `org.sandbox.jdt.internal.ui.*` - UI components and preferences

**Eclipse JDT Correspondence**:
- Maps to `org.eclipse.jdt.internal.corext.fix.*` pattern
- Can be ported by replacing `sandbox` with `eclipse`

## Design Patterns

### AST Visitor Pattern
Identifies JFace API calls that need modernization:
```java
compilationUnit.accept(new ASTVisitor() {
    @Override
    public boolean visit(MethodInvocation node) {
        // Check for deprecated JFace API
        return true;
    }
});
```

## Eclipse JDT Integration

### Current State
Experimental JFace cleanup implementations. May benefit from community feedback before Eclipse contribution.

### Contribution Path
1. Gather feedback on useful JFace transformations
2. Implement high-value cleanups
3. Test thoroughly with real-world JFace code
4. Port to Eclipse JDT structure
5. Submit for community review

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Dependencies**: 
  - Eclipse JDT Core APIs
  - Eclipse JFace APIs
  - `sandbox_common` for cleanup constants

## Testing

### Test Module
`sandbox_jface_cleanup_test` contains test cases for JFace transformations.

## Known Limitations

1. **Limited Coverage**: Currently supports a subset of JFace APIs
2. **Version-Specific**: Some transformations may be Eclipse version-specific
3. **Manual Review Needed**: Complex JFace patterns may require manual review

## Future Enhancements

- Expand coverage to more JFace APIs
- Support for SWT cleanup as well
- Integration with Eclipse Platform cleanups
- JFace databinding modernization

## Integration Points

### Eclipse JFace API Integration

The plugin integrates with Eclipse JFace framework:

1. **IProgressMonitor API**: Works with progress monitoring infrastructure
   - `SubProgressMonitor` (deprecated) → `SubMonitor` (modern)
   - Integrates with Eclipse jobs framework
   - Respects cancellation requests

2. **JFace Dialogs and Wizards**: Cleanup applies to:
   - Dialog creation code
   - Wizard page progress tracking
   - Long-running operations in UI context

3. **SWT/JFace Integration**: Understanding of:
   - Display thread requirements
   - AsyncExec and SyncExec patterns
   - Resource disposal patterns

### Eclipse JDT AST Integration

The cleanup uses Eclipse JDT's AST (Abstract Syntax Tree) framework:

1. **AST Visitor Pattern**: Traverses compilation units to find `SubProgressMonitor` usage
   - Visits `ClassInstanceCreation` nodes
   - Identifies `new SubProgressMonitor(...)` patterns
   - Checks binding types for exact match

2. **AST Rewrite**: Transforms code while preserving formatting
   - Replaces `beginTask()` + `SubProgressMonitor` with `SubMonitor.convert()` + `split()`
   - Updates variable types (`IProgressMonitor` → `SubMonitor`)
   - Adds `SubMonitor` imports automatically

3. **Type Binding**: Resolves types accurately
   - Distinguishes `SubProgressMonitor` from other classes
   - Handles fully qualified names vs. simple names
   - Verifies inheritance hierarchy

### Cleanup Framework Integration

Registered as Eclipse JDT cleanup:

1. **Extension Point**: `org.eclipse.jdt.ui.cleanUps`
   - Cleanup ID: Defined in `MYCleanUpConstants`
   - Appears in Eclipse cleanup preferences UI
   - Can be enabled in Save Actions

2. **CompilationUnitRewriteOperation**: Uses standard cleanup API
   - Integrates with Eclipse's refactoring framework
   - Supports undo/redo
   - Batch processing across multiple files

## Algorithms and Design Decisions

### Idempotent Transformation Design

**Decision**: Ensure cleanup can run multiple times safely

**Rationale**:
- Users may run cleanup multiple times
- Save Actions trigger on every save
- Should not break already-migrated code

**Implementation**:
```
1. Check if code already uses SubMonitor → skip
2. Check if beginTask() call exists → only then convert
3. Verify SubProgressMonitor pattern → don't change unrelated code
```

**Test**: Run cleanup twice, second run changes nothing

### Variable Name Conflict Resolution

**Decision**: Generate unique variable names when conflicts exist

**Algorithm**:
```
1. Preferred name: "subMonitor"
2. If "subMonitor" exists:
   - Try "subMonitor2", "subMonitor3", etc.
   - Or use descriptor-based name if available
3. Update all references to use new name
```

**Example**:
```java
// Original - conflicts with existing subMonitor
SubMonitor subMonitor = ...; // existing code
monitor.beginTask("Task", 100);
IProgressMonitor sub = new SubProgressMonitor(monitor, 60);

// After cleanup - generates subMonitor2
SubMonitor subMonitor = ...; // existing code preserved
SubMonitor subMonitor2 = SubMonitor.convert(monitor, "Task", 100);
IProgressMonitor sub = subMonitor2.split(60);
```

**Rationale**: Avoids compilation errors while maintaining readable names

### Style Flag Preservation

**Decision**: Preserve `SubProgressMonitor` style flags in `split()` call

**Rationale**:
- `SubProgressMonitor` accepts style flags (SUPPRESS_SUBTASK_LABEL, etc.)
- `SubMonitor.split()` accepts same flags
- Semantics should be preserved

**Transformation**:
```java
// Before
new SubProgressMonitor(monitor, 60, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL)

// After
subMonitor.split(60, SubMonitor.SUPPRESS_SUBTASK_LABEL)
```

### Why Convert beginTask() → SubMonitor.convert()?

**Decision**: Replace `beginTask()` call with `SubMonitor.convert()`

**Rationale**:
- `SubMonitor.convert()` returns `SubMonitor` instance
- Original `beginTask()` returns void (less useful)
- `SubMonitor` provides better API (split, setWorkRemaining, etc.)
- Consistent pattern: one conversion point, multiple split calls

**Alternative Considered**: Keep `beginTask()`, only change `SubProgressMonitor`
- **Rejected**: Would create mixed API usage (old + new)
- **Rejected**: Misses opportunity to modernize progress tracking

### Standalone SubProgressMonitor Handling

**Decision**: Convert standalone `SubProgressMonitor` (without preceding `beginTask()`)

**Pattern**:
```java
// Before: Standalone SubProgressMonitor without beginTask()
IProgressMonitor sub = new SubProgressMonitor(monitor, 50);

// After: Chained convert().split() call
IProgressMonitor sub = SubMonitor.convert(monitor).split(50);
```

**Rationale**:
- Not all SubProgressMonitor instances are preceded by beginTask()
- These instances still need migration to SubMonitor
- Chaining convert().split() provides equivalent functionality
- No variable declaration needed for intermediate SubMonitor

**Implementation**:
- Separate tracking for standalone vs. beginTask-associated instances
- Standalone instances transformed to `SubMonitor.convert(monitor).split(work)`
- Flag mapping applies equally to standalone instances
- 3-arg constructor: `SubMonitor.convert(monitor).split(work, flags)`

### Flag Mapping Strategy

**Decision**: Map SubProgressMonitor flags to SubMonitor equivalents

**Mappings**:
- `SUPPRESS_SUBTASK_LABEL` → `SUPPRESS_SUBTASK`
- `PREPEND_MAIN_LABEL_TO_SUBTASK` → **dropped** (no SubMonitor equivalent)

**Rationale**:
- SubMonitor uses different flag naming
- SUPPRESS_SUBTASK_LABEL behavior maps to SUPPRESS_SUBTASK
- PREPEND_MAIN_LABEL_TO_SUBTASK has no equivalent; dropping is safe

**Transformation Examples**:
```java
// Before: 3-arg with SUPPRESS_SUBTASK_LABEL
new SubProgressMonitor(monitor, 50, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL)

// After: Flag mapped to SUPPRESS_SUBTASK
subMonitor.split(50, SubMonitor.SUPPRESS_SUBTASK)

// Before: 3-arg with PREPEND_MAIN_LABEL_TO_SUBTASK
new SubProgressMonitor(monitor, 50, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK)

// After: Flag dropped, becomes 1-arg split()
subMonitor.split(50)
```

**Limitations**:
- Combined flags (bitwise OR) not currently mapped
- Numeric literals passed through unchanged
- Manual review may be needed for complex flag expressions

### ImageDataProvider Migration (IMAGE_DPI)

**Purpose**: Replace `new Image(Device, ImageData)` with `new Image(Device, ImageDataProvider)` for DPI/zoom correctness.

**Motivation**: Eclipse Platform UI PR #3004 demonstrated that using `ImageDataProvider` allows drawn patterns to adapt their dimensions based on zoom level (100% vs 150%/200%). The old `Image(Device, ImageData)` constructor creates images at a fixed size regardless of DPI.

**Pattern**:
- **Before**: `new Image(device, imageData)` where imageData is a locally created `ImageData`
- **After**: `new Image(device, (ImageDataProvider) zoom -> { return new ImageData(...); })`

**Safety Preconditions**:
1. ImageData argument must be a local variable (not a parameter or field)
2. ImageData must be created in the same block with a visible initializer
3. The transformation is currently limited to simple cases where the ImageData variable is only used for the Image constructor

**Implementation**: `ImageDataProviderPlugin` extends `AbstractTool`

**Algorithm**:
```
1. Find ClassInstanceCreation: new Image(device, imageData)
2. Verify first arg is Device type, second arg is ImageData type
3. Check imageData is a SimpleName (variable reference)
4. Locate imageData variable declaration in same method/block
5. Extract initializer expression from variable declaration
6. Create lambda: (ImageDataProvider) zoom -> { return <initializer>; }
7. Replace Image constructor with new lambda-based version
8. Remove now-unused ImageData variable declaration
9. Add ImageDataProvider import
```

**Transformation Example**:
```java
// Before
ImageData imageData = new ImageData(1, 1, 1, palette);
Image image = new Image(device, imageData);

// After
Image image = new Image(device, (ImageDataProvider) zoom -> {
    return new ImageData(1, 1, 1, palette);
});
```

**Future Enhancements**:
- Scale dimensions based on zoom parameter: `(int)(width * zoom / 100.0)`
- Support ImageData from method calls or field access
- Handle more complex ImageData initialization patterns
- Add heuristics to detect when zoom-awareness is beneficial

## Cross-References

### Root README Sections

This architecture document relates to:

- [7. `sandbox_jface_cleanup`](../README.md#7-sandbox_jface_cleanup) - User-facing documentation
- [Build Instructions](../README.md#build-instructions) - How to build and test
- [Eclipse Version Configuration](../README.md#eclipse-version-configuration) - JFace APIs from Eclipse 2025-09

### Related Modules

- **sandbox_common** - Uses `MYCleanUpConstants` for cleanup IDs
- **Eclipse JFace** - Target API being modernized
- **sandbox_jface_cleanup_test** - Comprehensive test cases for transformations

## References

- [SubMonitor API Documentation](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/SubMonitor.html)
- [SubProgressMonitor (Deprecated)](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/SubProgressMonitor.html)
- [Eclipse Progress Reporting Guide](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/runtime_progress.htm)
- [JDT Cleanup Framework](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/guide/jdt_api_cleanup.htm)

## Documentation Requirements

### Feature Properties

The corresponding feature module `sandbox_jface_cleanup_feature` MUST maintain:

1. **feature.properties** - English language properties file containing:
   - `description` - Clear description of the feature's purpose and capabilities
   - `copyright` - Copyright notice with appropriate years
   - `licenseURL` - URL to the Eclipse Public License
   - `license` - Eclipse Public License text or reference

2. **feature_de.properties** - German translation of all properties

These files enable Eclipse's built-in localization mechanism and provide user-facing documentation in the Eclipse IDE. When updating feature capabilities, ensure both property files are updated accordingly.
