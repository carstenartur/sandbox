# Sandbox Functional Converter Core

OSGi-free analysis logic module for the functional converter, extracted from `sandbox_functional_converter`.

## ⚠️ Build Status

**This module is currently not compiled during the regular Maven build.**

The module requires Eclipse JDT classes at compile time, which are not available from Maven Central. The P2 repositories used by Tycho cannot be accessed by regular JAR modules in the Maven reactor build.

### Why This Limitation Exists

Eclipse JDT artifacts are distributed through P2 repositories (Eclipse update sites), not Maven Central. While Tycho can access these P2 repositories for `eclipse-plugin` packaged modules, regular `jar` packaged modules cannot.

For now, this module serves as **reference code** showing how the analysis logic could be extracted. The actual implementation remains in the `sandbox_functional_converter` module and is fully functional.

## Purpose

This module demonstrates how analysis logic could be extracted to enable:
- Fast unit testing without Tycho
- Standalone analysis tools
- Easier debugging and development

## Contents

### Analysis Classes

- **ProspectiveOperation** - Represents a stream operation (map, filter, forEach, reduce)
- **OperationType** - Enum of operation types with lambda body generation
- **StreamPipelineBuilder** - Main builder for constructing stream pipelines from loops
- **PreconditionsChecker** - Validates if a loop is safe to convert to streams
- **LoopBodyParser** - Parses loop body statements into operations
- **ReducePatternDetector** - Detects reducer patterns (sum, product, max, min, etc.)
- **ReducerType** - Enum of reducer types
- **IfStatementAnalyzer** - Analyzes if statements for filter/match patterns
- **LambdaGenerator** - Generates lambda expressions for stream operations
- **StreamConstants** - Constants for stream method names
- **SideEffectChecker** - Checks for side effects in expressions
- **StatementParsingContext** - Context for statement parsing
- **StatementHandlerContext** - Context for statement handling
- **StatementHandlerType** - Enum of statement handler types

## Adaptations

The following Eclipse internal APIs were replaced with OSGi-free alternatives:

- `org.eclipse.jdt.internal.corext.dom.ASTNodes` → `org.sandbox.jdt.internal.common.util.ASTNodeUtils`
- `org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer` → `org.sandbox.jdt.internal.common.util.ScopeAnalyzerUtils`

## Dependencies

- `sandbox_common_core` - For OSGi-free utilities (not resolved in regular build)
- `org.eclipse.jdt.core` - For AST classes only (not resolved in regular build)

## Usage

**Note:** This module is not currently used due to build constraints. The analysis logic in `sandbox_functional_converter` is the active implementation.

## Building

```bash
# This module is skipped in regular builds
mvn clean install  # Will skip this module

# The actual functional converter plugin builds normally:
mvn clean install -pl sandbox_functional_converter
```
