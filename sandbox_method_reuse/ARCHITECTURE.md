# Method Reusability Finder - Architecture

## Overview

The Method Reusability Finder is an Eclipse JDT cleanup plugin that analyzes selected methods to identify potentially reusable code patterns across the codebase. This helps developers discover duplicate or similar code that could be refactored to improve code quality and maintainability.

## Design Goals

1. **Code Duplication Detection**: Identify similar code patterns using both token-based and AST-based analysis
2. **Intelligent Matching**: Recognize code similarity even when variable names differ
3. **Eclipse Integration**: Seamlessly integrate as a cleanup action in Eclipse JDT
4. **Performance**: Efficient analysis that scales to large codebases
5. **Portability**: Easy integration into Eclipse JDT core (following sandbox package structure)

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                 MethodReuseCleanUp                      │
│              (UI Integration Layer)                      │
│  - Wrapper for MethodReuseCleanUpCore                   │
│  - Extends AbstractCleanUpCoreWrapper                   │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ↓
┌─────────────────────────────────────────────────────────┐
│              MethodReuseCleanUpCore                      │
│                (Core Cleanup Logic)                      │
│  - createFix() - Orchestrates cleanup process           │
│  - computeFixSet() - Determines enabled fixes           │
│  - Uses EnumSet<MethodReuseCleanUpFixCore>              │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ↓
┌─────────────────────────────────────────────────────────┐
│          MethodReuseCleanUpFixCore (Enum)                │
│            (Fix Core Coordination)                       │
│  - METHOD_REUSE - General similarity detection          │
│  - INLINE_SEQUENCES - Inline code replacement           │
│  - Each value has associated plugin                     │
│  - findOperations() - Delegates to plugin               │
│  - rewrite() - Creates rewrite operations               │
└─────────────────────┬───────────────────────────────────┘
                      │
                      │ delegates to
                      ↓
        ┌──────────────────────────────────┐
        │     AbstractMethodReuse          │
        │   (Abstract Plugin Base)         │
        │  - find()                        │
        │  - rewrite()                     │
        │  - getPreview()                  │
        └──────────────┬───────────────────┘
                       │
        ┌──────────────┴──────────────────┐
        ↓                                  ↓
┌──────────────────┐            ┌──────────────────────┐
│ MethodReusePlugin│            │InlineSequencesPlugin │
│                  │            │                      │
│ - General method │            │ - Uses Inline        │
│   similarity     │            │   CodeSequence       │
│   detection      │            │   Finder             │
│   (placeholder)  │            │ - Variable mapping   │
└──────────────────┘            └──────┬───────────────┘
                                       │
                        ┌──────────────┴──────────────┐
                        ↓                             ↓
              ┌──────────────────┐        ┌──────────────────┐
              │ InlineCode       │        │ CodeSequence     │
              │ SequenceFinder   │        │ Matcher          │
              │                  │        │                  │
              │ - findInline     │        │ - matchSequence()│
              │   Sequences()    │        │ - Variable       │
              └──────────────────┘        │   Mapping        │
                                          └──────────────────┘
```

## Pattern Used: Enum-Based Fix Core

This plugin follows the **UseExplicitEncodingFixCore pattern** used elsewhere in the sandbox project:

1. **Enum as Fix Core**: `MethodReuseCleanUpFixCore` is an enum with values for each fix type
2. **Plugin Pattern**: Each enum value is associated with a plugin that extends `AbstractMethodReuse`
3. **Delegation**: The enum delegates to plugins for find/rewrite operations
4. **CleanUpCore Integration**: `MethodReuseCleanUpCore` uses `EnumSet` to manage enabled fixes

### Benefits
- **Separation of Concerns**: Each fix type has its own plugin class
- **Extensibility**: Easy to add new fix types by adding enum values
- **Consistency**: Follows established pattern in sandbox project
- **Type Safety**: Enum provides compile-time type checking

## Core Components

### 1. MethodReuseCleanUp (UI Integration)

**Location**: `org.sandbox.jdt.internal.ui.fix.MethodReuseCleanUp`

**Responsibilities**:
- UI wrapper for the core cleanup logic
- Extends `AbstractCleanUpCoreWrapper<MethodReuseCleanUpCore>`
- Handles Eclipse cleanup framework integration

**Usage**: Registered in `plugin.xml` as an Eclipse cleanup extension point

### 2. MethodReuseCleanUpCore (Core Logic)

**Location**: `org.sandbox.jdt.internal.ui.fix.MethodReuseCleanUpCore`

**Responsibilities**:
- Implements the core cleanup algorithm
- Uses `EnumSet<MethodReuseCleanUpFixCore>` to manage enabled fixes
- Coordinates fix detection and application

**Key Methods**:
- `createFix()` - Creates ICleanUpFix by delegating to enum values
- `computeFixSet()` - Determines which fixes are enabled
- `getPreview()` - Generates preview by aggregating from all enum values

### 3. MethodReuseCleanUpFixCore (Enum Fix Core)

**Location**: `org.sandbox.jdt.internal.corext.fix.MethodReuseCleanUpFixCore`

**Enum Values**:
- `METHOD_REUSE` - General method similarity detection (placeholder)
- `INLINE_SEQUENCES` - Inline code sequence replacement

**Responsibilities**:
- Coordinates plugins for each fix type
- Creates rewrite operations
- Delegates to plugins for finding and rewriting

**Key Methods**:
- `findOperations()` - Delegates to plugin's find() method
- `rewrite()` - Creates CompilationUnitRewriteOperation
- `getPreview()` - Gets preview from plugin

### 4. AbstractMethodReuse (Abstract Plugin Base)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.AbstractMethodReuse`

**Responsibilities**:
- Abstract base class for all method reuse plugins
- Defines common interface for find/rewrite/preview

**Key Methods**:
- `find()` - Find code patterns in compilation unit
- `rewrite()` - Apply transformation to AST
- `getPreview()` - Generate preview text

### 5. MethodReusePlugin (Placeholder Plugin)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.MethodReusePlugin`

**Status**: Placeholder implementation

**Responsibilities**:
- General method similarity detection
- Currently does nothing (future implementation)

### 6. InlineSequencesPlugin (Active Plugin)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.InlineSequencesPlugin`

**Status**: Structure implemented, core logic pending

**Responsibilities**:
- Find inline code sequences matching method bodies
- Replace inline sequences with method calls
- Use variable mapping for correct parameter mapping

**Uses**:
- `InlineCodeSequenceFinder` - Searches for matching sequences
- `CodeSequenceMatcher` - Matches with variable normalization
- `VariableMapping` - Tracks variable name mappings

### 7. Helper Classes (lib package)

All located in `org.sandbox.jdt.internal.corext.fix.helper.lib`:

- **InlineCodeSequenceFinder**: Searches method bodies for matching code sequences
- **CodeSequenceMatcher**: AST-based matching with variable normalization
- **VariableMapping**: Bidirectional variable name mapping
- **MethodSignatureAnalyzer**: Analyzes method signatures
- **MethodCallReplacer**: Generates method invocation code
- **CodePatternMatcher**: Pattern matching utilities
- **SideEffectAnalyzer**: Safety analysis for transformations

## Configuration

Cleanup options are defined in `MYCleanUpConstants`:
- `METHOD_REUSE_CLEANUP` - Enable/disable general method reuse detection
- `METHOD_REUSE_INLINE_SEQUENCES` - Enable/disable inline sequence detection

## Integration Points

### Eclipse Extension Points
- `org.eclipse.jdt.ui.cleanUps` - Registers the cleanup

### Dependencies
- `org.eclipse.jdt.core` - JDT core APIs
- `org.eclipse.jdt.ui` - JDT UI integration
- `org.eclipse.jdt.core.manipulation` - AST manipulation
- `sandbox_common` - Shared utilities (ReferenceHolder)

## Design Decisions

### Why Enum Pattern?
Following the `UseExplicitEncodingFixCore` pattern provides:
- **Consistency**: Same pattern as other sandbox plugins
- **Maintainability**: Clear separation between fix types
- **Extensibility**: Easy to add new fix types
- **Type Safety**: Compile-time checking

### Why Separate lib Package?
- **Reusability**: Helper classes can be used by multiple plugins
- **Testability**: Easier to unit test in isolation
- **Organization**: Clear distinction between plugins and utilities

## Future Enhancements

1. **Machine Learning**: Use ML models to improve similarity detection
2. **Refactoring Automation**: Automatically extract common code into shared methods
3. **Cross-project Analysis**: Search for similar methods across multiple projects
4. **Performance Optimization**: Cache analysis results, incremental analysis
5. **Visual Diff**: Show side-by-side comparison of similar methods

## Implementation Status

### Completed (2025-01-01)
- ✅ Enum-based architecture following UseExplicitEncodingFixCore pattern
- ✅ AbstractMethodReuse base class in helper package
- ✅ MethodReusePlugin placeholder
- ✅ InlineSequencesPlugin structure
- ✅ MethodReuseCleanUpCore using EnumSet pattern
- ✅ Test infrastructure enabled

### In Progress
- 🔄 InlineSequencesPlugin find() and rewrite() implementation
- 🔄 Integration testing

### Pending
- ⏳ MethodReusePlugin implementation
- ⏳ Performance optimization
- ⏳ Advanced features

See TODO.md for detailed pending items.
