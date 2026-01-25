# Test Strategy and Guidelines - Functional Loop Converter

> **Navigation**: [Main README](../README.md) | [Plugin README](../sandbox_functional_converter/README.md) | [Architecture](../sandbox_functional_converter/ARCHITECTURE.md) | [TODO](../sandbox_functional_converter/TODO.md)

## Overview

This document describes the testing strategy and guidelines for the functional loop converter plugin. The test suite is organized by transformation patterns rather than implementation phases, making tests easier to understand, maintain, and extend.

## Test Organization Principles

### Pattern-Based Organization (Current Approach)

Tests are organized by **transformation pattern** rather than implementation phase or chronological order. This approach provides:

1. **Better Discoverability**: Developers can quickly find tests for specific patterns (collect, map, filter, etc.)
2. **Easier Maintenance**: Related tests are grouped together
3. **Clear Documentation**: Each test class documents a specific aspect of the transformation
4. **Incremental Enhancement**: New patterns can be added without restructuring existing tests

### Test Class Structure

```
sandbox_functional_converter_test/
└── src/org/sandbox/jdt/ui/tests/quickfix/
    ├── IteratorLoopToStreamTest.java         # Iterator-specific patterns
    ├── LoopRefactoringCollectTest.java       # Collect patterns (List, Set)
    ├── LoopRefactoringMapFilterTest.java     # Map/Filter combinations
    ├── LoopRefactoringEdgeCasesTest.java     # Edge cases and boundaries
    ├── FunctionalLoopNegativeTest.java       # Patterns that should NOT convert
    ├── FunctionalLoopSimpleConversionTest.java  # Basic forEach patterns
    ├── FunctionalLoopReducerTest.java        # Reduce/aggregate patterns
    ├── FunctionalLoopMatchPatternTest.java   # anyMatch/allMatch/noneMatch
    └── ... (existing test classes)
```

## Test Class Descriptions

### IteratorLoopToStreamTest

**Purpose**: Tests conversion of iterator-based loops to streams

**Patterns Covered**:
- `while (it.hasNext())` → `forEach()`
- `for (Iterator it = ...; it.hasNext(); )` → `forEach()`
- Iterator with `collect()`, `map()`, `filter()`
- Iterator with reduction operations

**Negative Cases**:
- `Iterator.remove()` - cannot convert (modifies collection)
- Multiple `next()` calls - cannot convert (consumes multiple elements)
- `break` statements - not yet supported
- External state modification - side effect detection

**Key Features**:
- All tests use idiomatic Java Stream API
- Method references preferred over lambdas where appropriate
- Proper Collectors usage for terminal operations

### LoopRefactoringCollectTest

**Purpose**: Tests loops that accumulate elements into collections

**Patterns Covered**:
- Identity collect: `for (T item : c) result.add(item);` → `c.stream().collect(Collectors.toList())`
- Mapped collect: `for (T item : c) result.add(transform(item));` → `c.stream().map(f).collect(toList())`
- Filtered collect: `for (T item : c) if (cond) result.add(item);` → `c.stream().filter(p).collect(toList())`
- Filter+Map chains: `c.stream().filter(p).map(f).collect(toList())`

**Collection Types**:
- `List` → `Collectors.toList()`
- `Set` → `Collectors.toSet()`
- Custom collections → `Collectors.toCollection()`

**Best Practices**:
- Always use `Collectors.toList()` instead of manually building lists
- Filter before map for optimal performance
- Use method references for simple transformations (`String::toUpperCase`)

### LoopRefactoringEdgeCasesTest

**Purpose**: Tests edge cases and boundary conditions

**Categories**:
1. **Empty Collections**: Ensure streams handle empty inputs correctly
2. **Single Element**: Consistent transformation regardless of size
3. **Null Handling**: Use `Objects::nonNull` for null filtering
4. **Complex Generics**: `List<List<T>>`, `List<? extends T>`, etc.
5. **Method Chaining**: `item.method1().method2()` in transformations
6. **Variable Shadowing**: Lambda parameters and outer scope variables
7. **Performance**: Direct `forEach()` vs `stream().forEach()`
8. **Primitive Arrays**: Use `IntStream` for `int[]` to avoid boxing

**Why Important**: Edge cases often reveal bugs in pattern detection and transformation logic. Each test documents what could go wrong.

### FunctionalLoopNegativeTest

**Purpose**: Tests patterns that should NOT be converted

**Unsafe Patterns**:
- `break` statements - no direct stream equivalent
- `throw` statements - changes exception handling context
- Labeled `continue` - multi-level control flow
- External variable modification (non-accumulator) - side effects
- Multiple different return values - complex transformations
- `break` AND `continue` together - complex control flow

**Testing Philosophy**: Negative tests are as important as positive tests. They ensure the refactoring doesn't break code by attempting unsafe transformations.

## Test Naming Conventions

### Test Method Names

Use descriptive names that clearly indicate what is being tested:

```java
// Pattern: test[Context]_[Transformation]_[OptionalCondition]

// Good examples:
@Test void testWhileIterator_forEach()
@Test void testIterator_collectToList()
@Test void testIterator_filterMapAndCollect()
@Test void testIterator_withRemove_notConverted()

// Use @DisplayName for even clearer descriptions:
@Test
@DisplayName("Iterator filter+map+collect: stream().filter().map().collect()")
void testIterator_filterMapAndCollect() { ... }
```

### Test Class Names

Use pattern-based names that describe the category:

```java
// Pattern: [Feature][Pattern]Test or [Feature]NegativeTest

IteratorLoopToStreamTest          // Iterator-specific transformations
LoopRefactoringCollectTest        // Collect pattern tests
LoopRefactoringEdgeCasesTest      // Edge cases and boundaries
FunctionalLoopNegativeTest        // Negative test cases
```

## Writing Good Tests

### Test Structure Template

```java
@Test
@DisplayName("Human-readable description of what pattern is tested")
void test_descriptiveName() throws CoreException {
    // 1. Setup: Create test input
    String input = """
        package test;
        // Minimal but complete example
        class E {
            void method() {
                // Pattern being tested
            }
        }
        """;

    // 2. Expected: Define expected transformation
    String expected = """
        package test;
        // Expected output after transformation
        class E {
            void method() {
                // Transformed pattern using best practices
            }
        }
        """;

    // 3. Execute: Run the cleanup
    IPackageFragment pack = context.getSourceFolder()
        .createPackageFragment("test", false, null);
    ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
    context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
    
    // 4. Assert: Verify transformation
    context.assertRefactoringResultAsExpected(
        new ICompilationUnit[] { cu }, 
        new String[] { expected }, 
        null);
}
```

### Documentation Requirements

Every test should include JavaDoc comments explaining:

1. **Pattern**: What loop pattern is being tested
2. **Expected**: What the output should be
3. **Best Practice**: Why this transformation is optimal
4. **Edge Cases**: Any special considerations (for edge case tests)
5. **Why Important**: Why this test matters (for edge cases and negative tests)

Example:

```java
/**
 * Tests iterator loop with method reference transformation.
 * 
 * <p><b>Pattern:</b> Iterator loop with simple method call transformation</p>
 * <p><b>Expected:</b> {@code collection.stream().map(ClassName::method).collect(Collectors.toList())}</p>
 * <p><b>Best Practice:</b> Use method references for simple transformations (more concise)</p>
 */
@Test
void testIterator_mapWithMethodReference() { ... }
```

### Expected Output Best Practices

All expected test outputs should follow modern Java best practices:

1. **Use Method References**: Prefer `String::toUpperCase` over `s -> s.toUpperCase()`
2. **Use Standard Collectors**: Always use `Collectors.toList()`, `Collectors.toSet()`
3. **Optimal Ordering**: Filter before map for better performance
4. **Null Handling**: Use `Objects::nonNull` for null checks
5. **Direct forEach**: Use `collection.forEach()` not `collection.stream().forEach()` for simple iteration
6. **Specialized Streams**: Use `IntStream` for `int[]` to avoid boxing

### Test Data Guidelines

1. **Minimal Examples**: Use the smallest code sample that demonstrates the pattern
2. **Realistic Code**: Examples should look like real production code
3. **Self-Contained**: Each test should be independent and not rely on external state
4. **Meaningful Names**: Use descriptive variable names even in tests

## Test Coverage Goals

### Transformation Pattern Coverage

Each transformation pattern should have tests for:

1. **Basic Case**: Simplest valid example
2. **Complex Case**: More realistic example with multiple operations
3. **Edge Cases**: Boundary conditions (empty, single element, null)
4. **Negative Cases**: When the pattern should NOT convert
5. **Type Variations**: Different source types (List, Set, Array, Iterator)

### Code Coverage Targets

- **Line Coverage**: Aim for >80% in transformation logic
- **Branch Coverage**: Aim for >75% in decision points
- **Pattern Coverage**: 100% of documented patterns should have tests

## Adding New Tests

### When to Add Tests

Add new tests when:

1. **New Pattern Discovered**: A new loop pattern needs to be supported
2. **Bug Found**: Create a test that reproduces the bug before fixing it
3. **Edge Case Identified**: Any unusual input that could cause problems
4. **Feature Enhancement**: New transformation capability added

### Where to Add Tests

Choose the appropriate test class:

| Pattern Type | Test Class |
|-------------|-----------|
| Iterator-based loops | `IteratorLoopToStreamTest` |
| Collecting to List/Set | `LoopRefactoringCollectTest` |
| Map/Filter combinations | `LoopRefactoringMapFilterTest` |
| Edge cases | `LoopRefactoringEdgeCasesTest` |
| Should NOT convert | `FunctionalLoopNegativeTest` |
| Basic forEach | `FunctionalLoopSimpleConversionTest` |
| Reduce/sum/count | `FunctionalLoopReducerTest` |

### Test Review Checklist

Before submitting new tests, verify:

- [ ] Test has clear JavaDoc documentation
- [ ] Test name and DisplayName are descriptive
- [ ] Expected output uses modern Java best practices
- [ ] Test is in the appropriate test class
- [ ] Test is independent (doesn't rely on execution order)
- [ ] Negative cases are covered where applicable
- [ ] Edge cases are considered

## Test Execution

### Running Tests Locally

```bash
# Set Java 21 (required)
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Run all functional converter tests
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test

# Run specific test class
xvfb-run --auto-servernum mvn test -Dtest=IteratorLoopToStreamTest \
  -pl sandbox_functional_converter_test

# Run specific test method
xvfb-run --auto-servernum mvn test \
  -Dtest=IteratorLoopToStreamTest#testWhileIterator_forEach \
  -pl sandbox_functional_converter_test
```

### CI Execution

Tests run automatically in GitHub Actions on:
- Every push to PR branches
- Merge to main branch
- Eclipse 2025-09 target platform

Check CI logs for failures:
```bash
# Download CI log (user will provide URL)
curl -s "<log_url>" > /tmp/ci_log.txt
grep -A 100 "expected:" /tmp/ci_log.txt
```

## Test Maintenance

### Updating Tests for New Features

When adding new transformation features:

1. **Review Existing Tests**: Identify which tests may need updates
2. **Add New Tests**: Create tests for the new pattern
3. **Update Documentation**: Update this document with new patterns
4. **Run Full Suite**: Ensure no regressions in existing tests

### Refactoring Tests

When refactoring test structure:

1. **Keep Tests Passing**: Ensure all tests pass before and after refactoring
2. **Maintain Coverage**: Don't reduce code coverage during refactoring
3. **Update Documentation**: Keep this guide in sync with test structure
4. **Review Expected Outputs**: Ensure they still follow best practices

## Future Enhancements

### Planned Test Improvements

1. **Parameterized Tests**: Use JUnit 5 `@ParameterizedTest` for pattern variations
2. **Property-Based Testing**: Generate random loop patterns and verify properties
3. **Performance Tests**: Benchmark transformation speed and memory usage
4. **Integration Tests**: Test with real Eclipse JDT projects

### Pattern Coverage Roadmap

Future patterns to add test coverage for:

- [ ] `flatMap()` patterns - nested collection flattening
- [ ] `distinct()` patterns - removing duplicates
- [ ] `sorted()` patterns - sorting elements
- [ ] `limit()` and `skip()` patterns - pagination
- [ ] `takeWhile()` and `dropWhile()` patterns - Java 9+ features
- [ ] Parallel streams - when safe to parallelize
- [ ] Custom collectors - domain-specific accumulation

## References

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Java Stream API Best Practices](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html)
- [Eclipse JDT Testing Guide](https://wiki.eclipse.org/JDT_Core_Programmer_Guide/JDT_Core_Tests)

## Change History

| Date | Change | Author |
|------|--------|--------|
| 2026-01-25 | Initial test strategy documentation | Carsten Hammer |
| 2026-01-25 | Added pattern-based test organization | Carsten Hammer |
| 2026-01-25 | Added IteratorLoopToStreamTest | Carsten Hammer |
| 2026-01-25 | Added LoopRefactoringCollectTest | Carsten Hammer |
| 2026-01-25 | Added LoopRefactoringEdgeCasesTest | Carsten Hammer |
