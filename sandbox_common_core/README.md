# Sandbox Common Core

OSGi-free core utilities module that provides common AST and analysis utilities without Eclipse OSGi dependencies.

## ⚠️ Build Status

**This module is currently not compiled during the regular Maven build.** 

The module requires Eclipse JDT classes at compile time, which are not available from Maven Central. The P2 repositories used by Tycho cannot be accessed by regular JAR modules in the Maven reactor build.

### Why This Limitation Exists

Eclipse JDT artifacts are distributed through P2 repositories (Eclipse update sites), not Maven Central. While Tycho can access these P2 repositories for `eclipse-plugin` packaged modules, regular `jar` packaged modules cannot.

### Building This Module

To build this module independently, you would need to:

1. Download JDT Core JARs manually from Eclipse
2. Install them in your local Maven repository
3. Build with a custom profile

For now, this module serves as **reference code** showing how the analysis logic could be extracted for fast testing. The actual implementation remains in the `sandbox_functional_converter` module.

## Purpose

This module demonstrates how to create OSGi-free utilities that could enable fast unit testing of analysis logic without requiring Tycho or Eclipse runtime. When the build infrastructure is enhanced to support this use case, these utilities would:

- Enable fast unit testing without Tycho
- Provide standalone implementations of Eclipse internal APIs
- Support better debugging and TDD workflows

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

- `org.eclipse.jdt.core` - For AST classes only (not resolved in regular build)

## Usage

**Note:** This module is not currently used by other modules due to build constraints. It exists as reference code for future infrastructure improvements.

## Building

```bash
# This module is skipped in regular builds
mvn clean install  # Will skip this module

# To build independently (requires manual JDT setup):
# 1. Download org.eclipse.jdt.core JAR from Eclipse
# 2. Install to local Maven repo
# 3. Uncomment dependencies in pom.xml
# 4. Build: mvn clean install -pl sandbox_common_core
```
