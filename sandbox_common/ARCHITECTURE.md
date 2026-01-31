# Sandbox Common Module - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#common) | [TODO](TODO.md)

## Overview

The `sandbox_common` module provides shared utilities, constants, and base classes used across all sandbox cleanup plugins. This module serves as the foundation for the entire sandbox ecosystem.

## Purpose

- Centralize common cleanup infrastructure and utilities
- Provide shared constants for cleanup registration and configuration
- Define reusable base classes and interfaces for cleanup implementations
- Maintain consistency across all sandbox plugins

## Core Components

### MYCleanUpConstants

**Location**: `org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants`

**Purpose**: Central repository for all cleanup-related constants used across sandbox plugins

**Key Constants**:
- Cleanup enablement keys (e.g., cleanup IDs, preference keys)
- Configuration options for each cleanup type
- Default values for cleanup settings

**Eclipse JDT Correspondence**: 
- Maps to Eclipse JDT's `CleanUpConstants` class
- When porting to Eclipse JDT, constants are merged into `org.eclipse.jdt.internal.corext.fix.CleanUpConstants`

### Shared Utilities

The module provides common utilities that are used by multiple cleanup implementations:
- **AST manipulation helpers** (`HelperVisitor`, `ASTProcessor`, `ASTNavigationUtils`)
- **Annotation utilities** (`AnnotationUtils`)
  - Check for annotations, find annotations, remove annotations, get annotation values
- **Naming utilities** (`NamingUtils`)
  - Case conversions (camelCase, PascalCase, snake_case)
  - Java identifier validation
  - Checksum generation for unique naming
- **Type resolution utilities** (`TypeCheckingUtils`)
- **Import management functions**
- **Code transformation helpers**

**Important**: When implementing AST operations using these APIs, regularly check if new AST nodes have been added in Eclipse JDT UI. If new node types are introduced in JDT, the helper APIs in this module should be extended to support them. This ensures the utilities remain complete and up-to-date with the latest Eclipse JDT capabilities.

## Package Structure

- `org.sandbox.jdt.internal.corext.fix2.*` - Core cleanup constants and utilities
- `org.sandbox.jdt.internal.ui.*` - Shared UI components (if any)

**Porting to Eclipse JDT**:
- Replace `sandbox` with `eclipse` in package names
- Constants from `MYCleanUpConstants` merge into Eclipse's `CleanUpConstants`
- Utilities may be distributed to appropriate Eclipse modules

## Integration with Other Plugins

All sandbox cleanup plugins depend on `sandbox_common` for:
1. **Cleanup Constants**: Each plugin registers its cleanup IDs and preferences via constants defined here
2. **Shared Utilities**: Common code transformation logic to avoid duplication
3. **Configuration**: Standard configuration patterns for UI preferences

### Dependency Graph

```
sandbox_common (this module)
    ↑
    ├── sandbox_encoding_quickfix
    ├── sandbox_platform_helper
    ├── sandbox_functional_converter
    ├── sandbox_junit_cleanup
    ├── sandbox_jface_cleanup
    ├── sandbox_tools
    ├── sandbox_xml_cleanup
    ├── sandbox_usage_view
    └── sandbox_extra_search
```

## Design Principles

### 1. Single Source of Truth
All constants related to cleanup registration and configuration are defined once in this module to avoid duplication and inconsistencies.

### 2. Minimal Dependencies
This module should have minimal external dependencies to avoid circular dependencies and keep the plugin architecture clean.

### 3. Eclipse JDT Compatibility
The structure mirrors Eclipse JDT's organization to facilitate easy porting when cleanups are mature enough for upstream contribution.

## Integration Points

### Eclipse JDT Integration

This module integrates with Eclipse JDT at multiple levels:

1. **CleanUp Framework**: Provides constants for cleanup registration
   - `MYCleanUpConstants` defines cleanup IDs used by all plugins
   - Corresponds to Eclipse's `CleanUpConstants` class
   - Used in cleanup preferences UI and Save Actions

2. **AST Utilities**: Helper classes for AST manipulation
   - `HelperVisitor` - Visitor pattern for AST traversal
   - `ASTProcessor` - Common AST operations
   - `ASTNavigationUtils` - AST navigation helpers
   - Must be kept up-to-date with Eclipse JDT AST changes (new node types)

3. **Annotation Utilities**: `AnnotationUtils` for annotation operations
   - Check for annotation presence
   - Find annotations by type
   - Remove annotations
   - Extract annotation values
   - Supports both single and marker annotations

4. **Naming Utilities**: `NamingUtils` for identifier handling
   - Case conversions (camelCase, PascalCase, snake_case)
   - Java identifier validation
   - Checksum generation for unique naming
   - Variable name conflict resolution

5. **Type Checking**: `TypeCheckingUtils` for type resolution
   - Type binding checks
   - Inheritance hierarchy navigation
   - Fully qualified name resolution

### OSGi Bundle Integration

As a foundational module, integrates with OSGi:

1. **Bundle Exports**: Exports packages for dependent plugins
   - `org.sandbox.jdt.internal.corext.fix2` (constants)
   - Utility packages (AST, annotations, naming)
   - Makes APIs available to other sandbox plugins

2. **Bundle Dependencies**: Minimal imports
   - `org.eclipse.jdt.core` - JDT core APIs
   - `org.eclipse.jdt.ui` - JDT UI APIs
   - `org.eclipse.core.runtime` - Platform runtime
   - Avoids circular dependencies

### Cleanup Plugin Integration

All cleanup plugins depend on this module:

1. **Constant Usage**: Plugins reference `MYCleanUpConstants`
   - Example: `MYCleanUpConstants.EXPLICIT_ENCODING_CLEANUP`
   - Static imports for readability
   - Single source prevents duplicate IDs

2. **Utility Reuse**: Plugins use shared utilities
   - AST manipulation (HelperVisitor, ASTProcessor)
   - Annotation handling (AnnotationUtils)
   - Naming operations (NamingUtils)
   - Type checking (TypeCheckingUtils)

## Algorithms and Design Decisions

### Constant Naming Convention

**Decision**: Prefix all cleanup constants with descriptive names

**Convention**:
```java
public static final String [FEATURE]_CLEANUP = "cleanup.[feature]";
public static final String [FEATURE]_[OPTION] = "cleanup.[feature].[option]";
```

**Examples**:
```java
EXPLICIT_ENCODING_CLEANUP = "cleanup.explicit_encoding"
EXPLICIT_ENCODING_USE_UTF8 = "cleanup.explicit_encoding.use_utf8"
EXPLICIT_ENCODING_AGGREGATE = "cleanup.explicit_encoding.aggregate"
```

**Rationale**:
- Clear, self-documenting names
- Hierarchical structure for related options
- Easy to grep/search across codebase
- Aligns with Eclipse's naming patterns

### Why "MY" Prefix in MYCleanUpConstants?

**Decision**: Use `MYCleanUpConstants` instead of `CleanUpConstants`

**Rationale**:
- Avoids name conflict with Eclipse's `CleanUpConstants`
- Makes clear this is sandbox-specific (not Eclipse JDT)
- Easy to find/replace when porting to Eclipse (`MY` → nothing)
- Temporary name, intended to be merged into Eclipse's constants

**Porting Strategy**:
```java
// Sandbox
org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants

// Eclipse JDT (after porting)
org.eclipse.jdt.internal.corext.fix.CleanUpConstants
// Constants merged into existing file
```

### Utility Method Design Philosophy

**Decision**: Static utility methods with no state

**Rationale**:
- **Stateless**: No instance variables, thread-safe by default
- **Static**: No object creation overhead
- **Pure Functions**: Same input → same output (mostly)
- **Testable**: Easy to unit test in isolation

**Example**:
```java
// AnnotationUtils
public static boolean hasAnnotation(BodyDeclaration node, String annotationName) {
    // Pure function, thread-safe
}

// NamingUtils  
public static String toCamelCase(String input) {
    // Stateless, predictable
}
```

**Trade-off**:
- **Pro**: Simple, efficient, thread-safe
- **Con**: Less flexibility than object-oriented design
- **Con**: Can't override behavior via inheritance

**Decision**: Simplicity and thread-safety outweigh flexibility needs

### AST Visitor Design: HelperVisitor Utility

**Decision**: Provide `HelperVisitor` utility class with static helper methods

**Rationale**:
- Reduces boilerplate when searching for specific AST patterns
- Allows lambda-based visitor construction instead of full visitor classes
- Provides common patterns (e.g., "visit all method invocations of specific type")
- Simplifies cleanup code that needs to find and process specific nodes

**Usage Pattern**:
```java
// Plugin cleanup code using HelperVisitor utility
ReferenceHolder<ASTNode, Object> datah = new ReferenceHolder<>();
HelperVisitor.callMethodInvocationVisitor(
    Channels.class, 
    "newReader", 
    compilationUnit, 
    datah, 
    nodesprocessed,
    (visited, holder) -> {
        // Process found method invocation
        // Transform...
        return true;
    }
);
```

**Note**: `HelperVisitor` is NOT a base class to extend. It's a utility class providing static methods for building AST visitors using lambda expressions.

### Why Separate Utility Classes?

**Decision**: Separate utilities by concern (AST, Annotations, Naming, Types)

**Alternative Considered**: Single `Utils` class with all methods

**Chosen Approach - Separate Classes**:
- **Pro**: Clear responsibility boundaries
- **Pro**: Easier to navigate (find annotation utils in AnnotationUtils)
- **Pro**: Can extend independently
- **Pro**: Prevents single massive utility class

**Rejected Alternative - Single Utils**:
- **Con**: Would become thousands of lines
- **Con**: Hard to maintain and navigate
- **Con**: Temptation to add unrelated methods

## Cross-References

### Root README Sections

This architecture document relates to:

- [Build Instructions](../README.md#build-instructions) - sandbox_common is foundational dependency
- [What's Included](../README.md#whats-included) - Lists sandbox_common as foundational module

### Related Modules

All cleanup plugins depend on sandbox_common:
- sandbox_encoding_quickfix
- sandbox_platform_helper  
- sandbox_functional_converter
- sandbox_junit_cleanup
- sandbox_jface_cleanup
- sandbox_tools
- sandbox_xml_cleanup
- sandbox_usage_view
- sandbox_extra_search
- sandbox_method_reuse
- sandbox_triggerpattern

## Eclipse JDT Integration

### Current State
This module provides the foundation that corresponds to parts of Eclipse JDT's:
- `org.eclipse.jdt.core.manipulation` - Core cleanup constants
- `org.eclipse.jdt.ui` - UI-related constants and utilities

### Contribution Path
To contribute to Eclipse JDT:
1. Replace `org.sandbox` with `org.eclipse` in all package names
2. Merge constants from `MYCleanUpConstants` into Eclipse's `CleanUpConstants`
3. Distribute utilities to appropriate Eclipse modules
4. Update all dependent plugins to use Eclipse's constants

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Dependencies**: Minimal - primarily Eclipse JDT core and UI APIs
- **Exports**: Utilities and constants for use by dependent plugins

## Testing

### Current State
Currently, testing for common utilities is indirect through dependent plugins. Direct unit tests for utilities are planned but not yet implemented.

### Test Module Creation
A dedicated `sandbox_common_test` module should be created following the pattern of other test modules (e.g., `sandbox_platform_helper_test`). This module would:
- Provide unit tests for all utility methods
- Test constant integrity and consistency
- Validate thread-safety of `ReferenceHolder`
- Test AST navigation utilities with sample AST structures
- Ensure annotation utilities work correctly with different annotation types

### Testing Strategy
Future improvements:
- Direct unit tests for utility methods
- Integration tests for constant integrity
- Validation that all cleanup IDs are unique
- Verification that constants follow naming conventions

## Future Enhancements

- Consider extracting more shared code from individual plugins to reduce duplication
- Enhance utility classes to cover more common transformation patterns
- Improve documentation of available utilities for easier reuse
- Consider creating a dedicated test module if shared utilities grow significantly
- Keep AST utilities synchronized with Eclipse JDT AST evolution

## New Pattern Matching Utilities (January 2026)

The module now includes new utilities for more readable AST processing:

### NodeMatcher

**Location**: `org.sandbox.jdt.internal.common.NodeMatcher`

**Purpose**: Fluent type-safe wrapper for AST nodes enabling pattern matching style processing without deep instanceof chains.

**Before (nested if-instanceof)**:
```java
if (stmt instanceof VariableDeclarationStatement) {
    VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmt;
    // handle variable declaration
} else if (stmt instanceof IfStatement) {
    IfStatement ifStmt = (IfStatement) stmt;
    if (ifStmt.getElseStatement() == null) {
        // handle if without else
    }
} else if (stmt instanceof ExpressionStatement) {
    // handle expression
}
```

**After (fluent API)**:
```java
NodeMatcher.on(stmt)
    .ifVariableDeclaration(varDecl -> {
        // handle variable declaration
    })
    .ifIfStatementWithoutElse(ifStmt -> {
        // handle if without else
    })
    .ifExpressionStatement(exprStmt -> {
        // handle expression
    })
    .orElse(node -> {
        // handle other cases
    });
```

### StatementContext

**Location**: `org.sandbox.jdt.internal.common.StatementContext`

**Purpose**: Provides contextual information about a statement's position within a block, eliminating repetitive index tracking.

**Before**:
```java
List<Statement> statements = block.statements();
for (int i = 0; i < statements.size(); i++) {
    Statement stmt = statements.get(i);
    boolean isLast = (i == statements.size() - 1);
    boolean isFirst = (i == 0);
    
    if (stmt instanceof IfStatement && !isLast) {
        // handle non-last IF
    }
}
```

**After**:
```java
StatementContext.forEachInBlock(block, (stmt, ctx) -> {
    NodeMatcher.on(stmt)
        .ifIfStatementMatching(
            ifStmt -> !ctx.isLast(),
            ifStmt -> handleNonLastIf(ifStmt, ctx)
        );
});
```

**Key Methods**:
- `isFirst()`, `isLast()`, `isOnly()`, `isMiddle()` - Position queries
- `hasNext()`, `hasPrevious()` - Navigation queries
- `getNextStatement()`, `getPreviousStatement()` - Navigation
- `getRemainingStatements()` - Get statements after current
- `matcher()` - Create NodeMatcher for the statement

### StatementDispatcher

**Location**: `org.sandbox.jdt.internal.common.StatementDispatcher`

**Purpose**: Registry-based dispatcher for handling different statement types with conditions.

**Example**:
```java
StatementDispatcher<StatementContext, List<Operation>> dispatcher = 
    StatementDispatcher.<StatementContext, List<Operation>>create()
        .when(VariableDeclarationStatement.class)
            .and(ctx -> !ctx.isLast())
            .thenReturn((stmt, ctx) -> handleVarDecl(stmt, ctx))
        .when(IfStatement.class)
            .and(ctx -> ctx.isLast())
            .thenReturn((stmt, ctx) -> handleLastIf(stmt, ctx))
        .otherwise((stmt, ctx) -> handleDefault(stmt, ctx));

Optional<List<Operation>> result = dispatcher.dispatch(stmt, context);
```

### AstProcessorBuilder Convenience Methods

New convenience methods added to `AstProcessorBuilder`:

- `onIfStatementWithoutElse()` - IF statements without else branch
- `onIfStatementWithElse()` - IF statements with else branch
- `onCompoundAssignment()` - Compound assignments (+=, -=, *=, etc.)
- `onAssignmentWithOperator(operator)` - Specific assignment operator
- `onPostfixIncrementOrDecrement()` - i++, i--
- `onPrefixIncrementOrDecrement()` - ++i, --i
- `onUnlabeledContinue()` - Continue without label
- `onLabeledContinue()` - Continue with label
- `onReturnBoolean()` - Return statements with boolean literals
- `onStaticMethodInvocation(className, methodName)` - Static method calls like Math.max

**Example**:
```java
AstProcessorBuilder.with(holder)
    .onCompoundAssignment((assignment, h) -> {
        // Handle +=, -=, *=, etc.
        return true;
    })
    .onPostfixIncrementOrDecrement((postfix, h) -> {
        // Handle i++, i--
        return true;
    })
    .onStaticMethodInvocation("Math", "max", (mi, h) -> {
        // Handle Math.max() calls
        return true;
    })
    .build(node);
```

### Benefits of New Utilities

1. **Improved Readability**: Replace nested if-instanceof chains with fluent API
2. **Type Safety**: Type-safe handlers without explicit casts
3. **Reduced Boilerplate**: Common patterns extracted into reusable methods
4. **Better Maintainability**: Easier to add/modify handlers
5. **Self-Documenting**: Method names describe what they match