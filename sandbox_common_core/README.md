# Sandbox Common Core

OSGi-free core utilities module that provides common AST and analysis utilities without Eclipse OSGi dependencies.

## Purpose

This module enables fast unit testing of analysis logic without requiring Tycho or Eclipse runtime. It replaces Eclipse internal utilities with standalone implementations.

## Contents

### Utility Classes

- **ASTNodeUtils** - OSGi-free replacement for `org.eclipse.jdt.internal.corext.dom.ASTNodes`
  - `getParent()` - Find ancestor nodes of specific types
  - `getFirstAncestorOrNull()` - Find first ancestor matching class
  - `hasAncestor()` - Check if ancestor of type exists
  - `getEnclosingMethod()` - Find enclosing method declaration
  - `getEnclosingType()` - Find enclosing type declaration

- **ScopeAnalyzerUtils** - OSGi-free scope analysis
  - `getUsedVariableNames()` - Find all variable references in a node
  - `getDeclaredVariableNames()` - Find all variable declarations in a node

- **ReferenceHolder** - Thread-safe map for AST traversal data
  - Simplified version without HelperVisitorProvider coupling
  - Based on ConcurrentHashMap for thread safety

- **AstProcessorBuilder** - Fluent builder for AST processing
  - Copied from sandbox_common for analysis support

## Dependencies

- `org.eclipse.jdt.core` (provided scope) - For AST classes only

## Usage

Used by `sandbox_functional_converter_core` and other analysis modules that need OSGi-free utilities.

## Building

```bash
mvn clean install -pl sandbox_common_core
```
