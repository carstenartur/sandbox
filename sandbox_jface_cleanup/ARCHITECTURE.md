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
