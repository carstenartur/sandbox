# Sandbox Functional Converter Fast Tests

Fast unit tests for the functional converter analysis logic that run without Tycho or Eclipse runtime.

## Purpose

This module provides JUnit 5 tests for the functional converter's analysis logic, enabling:
- Fast test execution (seconds instead of minutes)
- No Xvfb requirement
- Standard Maven test workflow
- Easier TDD (Test-Driven Development)
- Faster CI/CD pipelines

## Test Coverage

### StreamPipelineBuilderTest

Tests for the main stream pipeline builder:

- `testSimpleForEachIsAnalyzable()` - Simple forEach conversion
- `testLoopWithBreakIsNotSafe()` - Loops with break statements
- `testLoopWithContinueIsSafe()` - Loops with continue (converts to filter)
- `testLoopWithReducerIsDetected()` - Reducer pattern detection
- `testNestedLoopIsNotSafe()` - Nested loop prevention

### TestASTHelper

Utility class for creating AST structures in tests:
- `parse(String code)` - Parse Java code into CompilationUnit
- `findFirstEnhancedFor(CompilationUnit)` - Find first enhanced for-loop

## Running Tests

```bash
# Run fast tests only (no Tycho required)
mvn test -pl sandbox_functional_converter_fast_test

# Run with full output
mvn test -pl sandbox_functional_converter_fast_test -X
```

## Dependencies

- `sandbox_functional_converter_core` - Analysis logic being tested
- `org.eclipse.jdt.core` (provided scope) - For AST parsing
- `junit-jupiter` (test scope) - JUnit 5 test framework

## Comparison with Full Tests

| Aspect | Fast Tests | Full Tests (Tycho) |
|--------|-----------|-------------------|
| Runtime | Seconds | Minutes |
| Dependencies | JDT Core only | Full Eclipse Platform |
| Environment | Any Maven | Xvfb + Eclipse runtime |
| Coverage | Analysis logic | End-to-end transformations |

## Future Extensions

Additional test classes to add:
- `PreconditionsCheckerTest` - More precondition test cases
- `ReducePatternDetectorTest` - Reducer pattern edge cases
- `LoopBodyParserTest` - Statement parsing tests
- `IfStatementAnalyzerTest` - Filter and match pattern tests
