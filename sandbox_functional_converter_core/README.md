# Sandbox Functional Converter Core

OSGi-free analysis logic module for the functional converter, extracted from `sandbox_functional_converter`.

## Purpose

This module contains the core analysis logic for detecting and analyzing loop-to-stream conversion patterns, without requiring Eclipse OSGi infrastructure. This enables:
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

- `sandbox_common_core` - For OSGi-free utilities
- `org.eclipse.jdt.core` (provided scope) - For AST classes only

## Usage

Used by:
- `sandbox_functional_converter` - The main OSGi plugin (should depend on this core module)
- `sandbox_functional_converter_fast_test` - Fast unit tests

## Building

```bash
mvn clean install -pl sandbox_functional_converter_core
```
