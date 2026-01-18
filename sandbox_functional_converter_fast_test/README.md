# Sandbox Functional Converter Fast Tests

Fast unit tests for the functional converter analysis logic that would run without Tycho or Eclipse runtime.

## ⚠️ Build Status

**This module is currently not compiled or tested during the regular Maven build.**

The module requires Eclipse JDT classes and the analysis core module, which are not available in the regular Maven build due to dependency resolution issues.

### Why This Limitation Exists

Eclipse JDT artifacts are distributed through P2 repositories (Eclipse update sites), not Maven Central. The infrastructure needed to support standalone JAR modules with JDT dependencies in a Tycho-based build is not yet in place.

For now, this module serves as **reference code** demonstrating what fast tests could look like. The actual tests in `sandbox_functional_converter_test` are fully functional and run with Tycho.

## Purpose

This module demonstrates how tests could be structured for fast execution if the build infrastructure supported it:
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
# This module is skipped in regular builds
mvn test -pl sandbox_functional_converter_fast_test  # Will skip

# The actual functional converter tests work normally:
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test
```

## Dependencies

- `sandbox_functional_converter_core` - Analysis logic (not available in regular build)
- `org.eclipse.jdt.core` - For AST parsing (not available in regular build)
- `junit-jupiter` - JUnit 5 test framework

## Current Status

This module is a proof-of-concept showing how fast tests could be structured. Until the build infrastructure is enhanced to support JAR modules with JDT dependencies, the tests in `sandbox_functional_converter_test` remain the authoritative test suite.

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
