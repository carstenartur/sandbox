# Functional Loop Conversion - Implementation TODO

## Overview
This document outlines the remaining work needed to complete the functional loop conversion cleanup. The goal is to convert imperative for-each loops into functional Java 8 Streams.

## Background
The implementation is based on the NetBeans mapreduce hints:
https://github.com/apache/netbeans/tree/master/java/java.hints/src/org/netbeans/modules/java/hints/jdk/mapreduce

NetBeans implementation: ~1500 lines
Current implementation: ~40% complete

## Current State

### ✅ Completed
- [x] StreamPipelineBuilder class (390 lines) - framework for complex pipelines
- [x] Basic Refactorer with simple forEach conversion
- [x] ProspectiveOperation enum with all 6 operation types
- [x] First test case enabled (SIMPLECONVERT)
- [x] Pattern recognition logic for filters, reducers, matchers

### ⏳ In Progress
- [ ] ProspectiveOperation lambda generation methods
- [ ] Integration of StreamPipelineBuilder into Refactorer
- [ ] PreconditionsChecker reducer detection

### ❌ Not Started
- [ ] Variable dependency tracking
- [ ] Operation merging/optimization
- [ ] Most test cases (19 of 20 still disabled)

## Priority Tasks

### 1. Complete ProspectiveOperation Class (HIGH PRIORITY)
**File**: `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/ProspectiveOperation.java`

Add these methods:

```java
// Core methods needed by StreamPipelineBuilder
public void setEager(boolean eager) { ... }
public LambdaExpression createLambda(AST ast, String loopVarName) { ... }
public String getStreamMethod() { ... }
public List<Expression> getStreamArguments(AST ast, String loopVarName) { ... }
public Expression getReducingVariable() { ... }

// Static factory method
public static List<ProspectiveOperation> mergeRecursivelyIntoComposableOperations(List<ProspectiveOperation> ops) { ... }
```

#### Implementation Guide:

**createLambda()**:
- MAP: `x -> { <stmt>; return x; }` or simpler form for expressions
- FILTER: `x -> (<condition>)` with optional negation
- FOREACH: `x -> { <stmt> }` 
- REDUCE: Create map to constant, then reduce with accumulator function
- ANYMATCH: `x -> (<condition>)`
- NONEMATCH: `x -> (<condition>)`

**getStreamMethod()**:
- MAP → "map"
- FILTER → "filter"
- FOREACH → "forEach" or "forEachOrdered"
- REDUCE → "reduce"
- ANYMATCH → "anyMatch"
- NONEMATCH → "noneMatch"

**getStreamArguments()**:
- MAP, FILTER, FOREACH, ANYMATCH, NONEMATCH: return list with lambda
- REDUCE: return list with [identityValue, accumulatorLambda] or just [lambda]

**mergeRecursivelyIntoComposableOperations()**:
- Merge consecutive MAP operations that transform the same variable
- Combine FILTERs with AND
- Cannot merge terminal operations (FOREACH, REDUCE, ANYMATCH, NONEMATCH)

### 2. Enhance PreconditionsChecker (MEDIUM PRIORITY)
**File**: `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/PreconditionsChecker.java`

Add these methods:

```java
public boolean isReducer() { ... }
public Statement getReducer() { ... }
```

#### Implementation Guide:
- Scan loop body for accumulator patterns:
  - `i++`, `i--`, `++i`, `--i`
  - `sum += x`, `product *= x`, `count -= 1`
  - Other compound assignments (|=, &=, etc.)
- Store the reducer statement if found
- Return true if accumulator detected

### 3. Integrate StreamPipelineBuilder (HIGH PRIORITY)
**File**: `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/Refactorer.java`

Replace current `refactor()` method:

```java
public void refactor() {
    if (!isRefactorable()) {
        return;
    }

    // Use StreamPipelineBuilder for complex analysis
    StreamPipelineBuilder builder = new StreamPipelineBuilder(forLoop, preconditions);
    
    if (!builder.analyze()) {
        return; // Can't convert
    }

    MethodInvocation pipeline = builder.buildPipeline();
    if (pipeline == null) {
        return;
    }

    Statement replacement = builder.wrapPipeline(pipeline);
    rewrite.replace(forLoop, replacement, null);
}
```

### 4. Incrementally Enable Tests (ONGOING)
**File**: `sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/Java8CleanUpTest.java`

Enable tests one at a time in this order:

1. ✅ SIMPLECONVERT - simple forEach (DONE)
2. ⏳ CHAININGMAP - map operation
3. ⏳ ChainingFilterMapForEachConvert - filter + map
4. ⏳ ContinuingIfFilterSingleStatement - continue as filter
5. ⏳ SimpleReducer - basic reduce
6. ⏳ ChainedReducer - filter + reduce
7. ⏳ ChainedAnyMatch - anyMatch
8. ⏳ ChainedNoneMatch - noneMatch
9. ⏳ ...continue with remaining 12+ tests

For each test:
1. Enable the test by adding it to `@EnumSource(value = UseFunctionalLoop.class, names = {"SIMPLECONVERT", "CHAININGMAP", ...})`
2. Run: `mvn test -pl sandbox_functional_converter_test -Dtest=Java8CleanUpTest#testSimpleForEachConversion`
3. Fix implementation issues revealed by the test
4. Repeat until test passes

## Detailed Implementation Plan

### Phase 1: Basic Operations (MAP, FILTER, FOREACH)
Target tests: CHAININGMAP, ChainingFilterMapForEachConvert, ContinuingIfFilterSingleStatement

1. Implement ProspectiveOperation.createLambda() for MAP:
   - Detect variable declarations: `String s = l.toString();`
   - Generate: `l -> l.toString()` with variable name mapping
   - Handle chaining: multiple variable declarations become multiple maps

2. Implement ProspectiveOperation.createLambda() for FILTER:
   - Extract condition from if statement
   - Handle negation for continue patterns
   - Generate: `l -> (l != null)`

3. Implement ProspectiveOperation.createLambda() for FOREACH:
   - Copy loop body statements into lambda block
   - Handle single expression vs block

4. Test pipeline building with combinations

### Phase 2: Reductions (REDUCE)
Target tests: SimpleReducer, ChainedReducer, IncrementReducer, DecrementingReducer

1. Implement reducer detection in PreconditionsChecker:
   - Detect `i++`, `sum += x`, etc.
   - Track accumulator variable

2. Implement ProspectiveOperation for REDUCE:
   - Generate map to constant: `_item -> 1` for counting
   - Generate accumulator lambda: `(accumulator, _item) -> accumulator + _item`
   - Handle different operators: +, -, *, etc.
   - Use method references where possible: `Integer::sum`

3. Handle identity values:
   - 0 for addition/subtraction
   - 1 for multiplication
   - "" for string concatenation

4. Wrap result in assignment: `variable = stream.reduce(...)`

### Phase 3: Early Returns (ANYMATCH, NONEMATCH)
Target tests: ChainedAnyMatch, ChainedNoneMatch

1. Detect early return patterns:
   - `if (condition) return true;` → anyMatch
   - `if (condition) return false;` → noneMatch

2. Generate match lambdas:
   - Extract condition, possibly with preceding maps
   - Chain operations before the match

3. Wrap in if statement:
   - `if (stream.anyMatch(...)) { return true; }`
   - `if (!stream.noneMatch(...)) { return false; }`

### Phase 4: Complex Chains & Optimization
Target tests: SmoothLongerChaining, NonFilteringIfChaining, MergingOperations

1. Implement variable dependency tracking:
   - Track which variables each operation uses/produces
   - Ensure pipeline stages have access to needed variables

2. Implement operation merging:
   - Consecutive MAPs with same variable
   - Consecutive FILTERs (combine with &&)

3. Handle complex side effects:
   - If statements with side effects → map with if inside
   - Nested if statements

4. Optimize lambda bodies:
   - Remove unnecessary blocks
   - Use expression lambdas where possible
   - Use method references where applicable

## Testing Strategy

### Local Testing
```bash
# Build the project
mvn -Pjacoco verify

# Run specific test
mvn test -pl sandbox_functional_converter_test \
  -Dtest=Java8CleanUpTest#testSimpleForEachConversion

# Run all functional converter tests
mvn test -pl sandbox_functional_converter_test -Dtest=Java8CleanUpTest
```

Note: Linux users need xvfb:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test
```

### Test-Driven Development
1. Enable one test
2. Run and observe failure
3. Implement missing functionality
4. Iterate until test passes
5. Move to next test

### Expected Test Patterns

Each test in `UseFunctionalLoop` enum has:
- `given`: Input code with for-each loop
- `expected`: Output code with stream pipeline

Compare your generated code with the expected output to understand what's needed.

## Common Pitfalls

1. **Lambda Parameter Types**: Use `VariableDeclarationFragment` for simple parameters, not `SingleVariableDeclaration`

2. **Lambda Bodies**: 
   - Single expression → expression lambda
   - Multiple statements → block lambda with explicit return
   - forEach → block without return

3. **Variable Scoping**:
   - Loop variable available to all operations
   - Intermediate variables need to be passed through pipeline
   - External variables are captured

4. **Operation Order**:
   - filter/map can be chained
   - forEach/reduce/anyMatch/noneMatch are terminal
   - Terminal operation must be last

5. **Type Safety**:
   - Ensure lambda types match stream element types
   - Handle type transformations in map operations

## Reference Materials

### NetBeans Implementation
- Refactorer.java: Overall algorithm
- ProspectiveOperation.java: Operation representation and lambda generation
- PreconditionsChecker.java: Safety analysis

### Eclipse JDT Documentation
- AST: https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/package-summary.html
- Lambda: https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/LambdaExpression.html
- MethodInvocation: https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/MethodInvocation.html

### Test Cases
See: `sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/Java8CleanUpTest.java`

## Success Criteria

- [ ] All 20+ test cases in `UseFunctionalLoop` enum pass
- [ ] No regressions in disabled test cases (they should not change)
- [ ] Code review passes
- [ ] CodeQL security scan passes
- [ ] JaCoCo coverage maintains or improves

## Estimated Effort

- ProspectiveOperation completion: 4-6 hours
- PreconditionsChecker updates: 1-2 hours
- StreamPipelineBuilder integration: 1-2 hours
- Test fixing and iteration: 3-4 hours
- **Total: 9-14 hours**

## Contact

For questions, see the original NetBeans implementation or the Eclipse JDT documentation.

For help with Eclipse plugin development, see:
https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Fguide%2Fjdt_api_overview.htm
