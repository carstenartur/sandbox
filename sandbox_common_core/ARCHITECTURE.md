# Sandbox Common Core - Architecture

> **Navigation**: [Main README](../README.md) | [TODO](TODO.md)

## Overview

The `sandbox_common_core` module provides OSGi-free core utilities for AST manipulation and visitor pattern implementation. This module can be tested with regular Maven without requiring Tycho or Eclipse runtime dependencies.

## Purpose

- Provide OSGi-free versions of common AST utilities
- Enable fast testing without Eclipse/Tycho infrastructure
- Support regular Maven builds and dependencies
- Serve as foundation for extracting other OSGi-free modules

## Key Design Principles

### 1. No OSGi Dependencies

This module uses only:
- JDT Core from Maven Central (`org.eclipse.jdt.core`)
- Standard Java libraries
- JUnit 5 for testing

**What's excluded:**
- `org.eclipse.jdt.ui` (OSGi bundle)
- `org.eclipse.jdt.internal.corext` (OSGi bundle)
- Other Eclipse platform bundles

### 2. OSGi-Free Utility Equivalents

When functionality from OSGi bundles is needed, we create OSGi-free equivalents in `org.sandbox.jdt.internal.common.util` package.

**Example:** `ASTNodeUtils` provides the same functionality as `org.eclipse.jdt.internal.corext.dom.ASTNodes` but without OSGi dependencies.

## Core Components

### HelperVisitor
**Location**: `org.sandbox.jdt.internal.common.HelperVisitor`

**Purpose**: Lambda-based AST visitor builder that simplifies AST traversal

**Key Features:**
- Generic visitor pattern with type parameters
- Lambda expressions for visitor logic
- Predicate and consumer maps for different node types
- Support for all AST node types via VisitorEnum

### ReferenceHolder
**Location**: `org.sandbox.jdt.internal.common.ReferenceHolder`

**Purpose**: Thread-safe reference holder for storing AST node references

**Key Features:**
- Extends ConcurrentHashMap for thread safety
- Integrates with HelperVisitor
- Used by cleanup visitors during traversal

### ASTProcessor
**Location**: `org.sandbox.jdt.internal.common.ASTProcessor`

**Purpose**: Provides common AST manipulation operations

### ASTNodeUtils
**Location**: `org.sandbox.jdt.internal.common.util.ASTNodeUtils`

**Purpose**: OSGi-free equivalents of JDT internal utilities

**Replaces:** `org.eclipse.jdt.internal.corext.dom.ASTNodes`

**Key Methods:**
- `usesGivenSignature()` - Check method invocation signatures
- `getParent()` - Get parent node of specific type
- `isParent()` - Check parent-child relationship
- `getContainingStatement()` - Get containing statement
- `getContainingMethod()` - Get containing method
- `getContainingType()` - Get containing type

## Package Structure

```
org.sandbox.jdt.internal.common
├── HelperVisitor.java
├── HelperVisitorProvider.java
├── ReferenceHolder.java
├── VisitorEnum.java
├── ASTProcessor.java
├── AstProcessorBuilder.java
├── LambdaASTVisitor.java
├── LibStandardNames.java
├── NodeMatcher.java
├── StatementContext.java
└── StatementDispatcher.java

org.sandbox.jdt.internal.common.util
└── ASTNodeUtils.java (OSGi-free utilities)
```

## Dependencies

### Compile Dependencies
- `org.eclipse.jdt.core:3.39.0` (from Maven Central)

### Test Dependencies
- JUnit 5 (jupiter-api, jupiter-engine, jupiter-params)

## Building and Testing

### Build Without Tycho
```bash
cd sandbox_common_core
mvn clean install
```

### Run Tests Without Xvfb
```bash
mvn test
```

**Advantages:**
- Fast compilation (no P2 repository resolution)
- Fast testing (no Eclipse runtime startup)
- Standard Maven workflow
- Can run on any CI system without Eclipse

## Migration from OSGi Version

When code uses `org.eclipse.jdt.internal.corext.dom.ASTNodes`:

**Before (OSGi-dependent):**
```java
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

if (ASTNodes.usesGivenSignature(invocation, "java.util.List", "add")) {
    // ...
}
```

**After (OSGi-free):**
```java
import org.sandbox.jdt.internal.common.util.ASTNodeUtils;

if (ASTNodeUtils.usesGivenSignature(invocation, "java.util.List", "add")) {
    // ...
}
```

## Integration with Existing Modules

The OSGi plugin modules (`sandbox_common`, `sandbox_functional_converter`, etc.) continue to work unchanged. The core modules provide:

1. **Faster iteration** - Test logic without Eclipse runtime
2. **Better separation** - Core logic separate from UI/OSGi concerns
3. **Easier porting** - Core logic can be used in non-Eclipse contexts

## Future Enhancements

- Add more utility methods as needed
- Extract more core classes from other modules
- Create functional converter core module
- Add performance benchmarks comparing Tycho vs standard Maven tests

## Related Modules

- `sandbox_common_fast_test` - Fast tests for this core module
- `sandbox_common` - OSGi plugin version (still used in Eclipse)
- `sandbox_common_test` - Tycho-based tests for OSGi plugin
