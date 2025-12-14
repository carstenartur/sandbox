# Functional Loop Conversion - Implementation TODO

## Current Task (December 2025)

**Objective**: Enable and validate REDUCE operation tests

**Activities**:
1. ✅ Enabled 3 additional REDUCE tests: ChainedReducer, IncrementReducer, AccumulatingMapReduce
2. 🚧 Running tests to validate REDUCE implementation
3. 🚧 Debugging and fixing any issues discovered
4. 📝 Updating documentation to reflect completed work

**Next Steps**:
- Validate that newly enabled tests pass
- Fix any edge cases discovered
- Enable remaining REDUCE tests (DOUBLEINCREMENTREDUCER, DecrementingReducer, etc.)
- Move on to AnyMatch/NoneMatch pattern implementation

---

## Overview
This document outlines the remaining work needed to complete the functional loop conversion cleanup. The goal is to convert imperative for-each loops into functional Java 8 Streams.

## Background
The implementation is based on the NetBeans mapreduce hints:
https://github.com/apache/netbeans/tree/master/java/java.hints/src/org/netbeans/modules/java/hints/jdk/mapreduce

NetBeans implementation: ~1500 lines
Current implementation: ~40% complete

## Current State

### ✅ Completed
- [x] Basic Refactorer with simple forEach conversion
- [x] ProspectiveOperation enum with all 6 operation types
- [x] First test case enabled (SIMPLECONVERT)
- [x] ProspectiveOperation lambda generation methods (setEager, createLambda, getStreamMethod, getStreamArguments, getReducingVariable)
- [x] PreconditionsChecker reducer detection (isReducer, getReducer)
- [x] ProspectiveOperation operation merging (mergeRecursivelyIntoComposableOperations)
- [x] Enhanced Refactorer with parseLoopBody for basic MAP, FILTER, FOREACH operations
- [x] Variable name tracking through pipeline (getVariableNameFromPreviousOp)
- [x] Multiple test cases enabled: SIMPLECONVERT, CHAININGMAP, ChainingFilterMapForEachConvert, SmoothLongerChaining, MergingOperations, BeautificationWorks, BeautificationWorks2, NonFilteringIfChaining (8 of 20+)
- [x] StreamPipelineBuilder class created with analyze(), buildPipeline(), and wrapPipeline() methods
- [x] StreamPipelineBuilder integrated into Refactorer with refactorWithBuilder() method
- [x] StreamPipelineBuilder fully implements parseLoopBody() with recursive nested IF processing
- [x] Variable dependency tracking through getVariableNameFromPreviousOp() in StreamPipelineBuilder
- [x] StreamPipelineBuilder.requiresStreamPrefix() determines when .stream() is needed vs direct collection methods

### 🚧 In Progress
- [x] Continue statement handling (negated filter conditions for ContinuingIfFilterSingleStatement test) - COMPLETED
- [x] REDUCE operation implementation for accumulator patterns (SimpleReducer, ChainedReducer tests) - COMPLETED
  - [x] REDUCE operations wrapped in assignment statement (variable = pipeline)
  - [x] Accumulator variable detection and tracking
  - [x] MAP to constants for counting (_item -> 1)
  - [x] Method references for Integer::sum
  - [x] ReducerType enum (INCREMENT, DECREMENT, SUM, PRODUCT, STRING_CONCAT)
  - [x] Test implementation with actual test runs
  - [ ] Fix any edge cases discovered during testing
- [ ] Enabling additional REDUCE tests (ChainedReducer, IncrementReducer, AccumulatingMapReduce)
- [ ] Operation optimization (merge consecutive filters, remove redundant operations)

### ❌ Not Started
- [ ] Advanced reducer patterns (string concatenation, custom accumulators)
- [ ] AnyMatch/NoneMatch pattern detection and conversion
- [ ] Complex side effect handling
- [ ] Remaining test cases (12+ still disabled)

## Priority Tasks

### 0. ✅ Create StreamPipelineBuilder (COMPLETED)
**Status**: StreamPipelineBuilder class is fully implemented and integrated

**Current Implementation (in StreamPipelineBuilder.java)**:
- `analyze()` - Checks preconditions and parses loop body
- `parseLoopBody()` - Analyzes loop body and extracts ProspectiveOperations
- `buildPipeline()` - Constructs the stream pipeline from operations
- `wrapPipeline()` - Wraps pipeline in appropriate statement (including assignments for REDUCE)
- `getVariableNameFromPreviousOp()` - Tracks variable names through pipeline
- `requiresStreamPrefix()` - Determines when .stream() is needed
- `detectReduceOperation()` - Detects REDUCE patterns (i++, +=, etc.)
- Full support for MAP, FILTER, FOREACH, REDUCE operations
- Recursive nested IF statement processing for filter chains
- Variable dependency tracking through the pipeline

**Integration**:
- Refactorer.refactorWithBuilder() uses StreamPipelineBuilder
- Can toggle between builder and legacy implementation via system property
- Default is to use StreamPipelineBuilder

**Next Steps**:
- [x] Continue statement handling (negated filters) - COMPLETED
- [x] Implement REDUCE operation support - IMPLEMENTED (needs testing)
- Add AnyMatch/NoneMatch pattern detection

### 1. ✅ Complete ProspectiveOperation Class (COMPLETED)
**File**: `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/ProspectiveOperation.java`

**Status**: All required methods have been implemented.

Implemented methods:
- ✅ `setEager(boolean eager)` - Sets whether this operation should be executed eagerly
- ✅ `createLambda(AST ast, String loopVarName)` - Creates a lambda expression for this operation
- ✅ `getStreamMethod()` - Returns the stream method name for this operation
- ✅ `getStreamArguments(AST ast, String loopVarName)` - Returns the arguments for the stream method call
- ✅ `getReducingVariable()` - Returns the reducing variable expression
- ✅ `mergeRecursivelyIntoComposableOperations(List<ProspectiveOperation> ops)` - Static factory method for merging operations

### 2. ✅ Enhance PreconditionsChecker (COMPLETED)
**File**: `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/PreconditionsChecker.java`

**Status**: All required methods have been implemented.

Implemented methods:
- ✅ `isReducer()` - Checks if the loop contains a reducer pattern
- ✅ `getReducer()` - Returns the statement containing the reducer pattern

### 3. ✅ Integrate StreamPipelineBuilder (COMPLETED)
**File**: `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/Refactorer.java`

**Status**: StreamPipelineBuilder is fully integrated into Refactorer.

Current implementation:
- `refactorWithBuilder()` - Main integration method using StreamPipelineBuilder
- `useStreamPipelineBuilder()` - Toggle between builder and legacy implementation
- StreamPipelineBuilder handles: simple forEach, MAP, FILTER, nested IF processing
- Variable name tracking through the pipeline
- Stream vs direct forEach decision logic

Implementation details:
- Creates StreamPipelineBuilder instance with forLoop and preconditions
- Calls analyze() to parse loop body
- Calls buildPipeline() to construct stream pipeline
- Calls wrapPipeline() to create final statement
- Replaces loop with refactored statement via ASTRewrite

Future enhancements:
- ✅ Continue statement handling (negated filters) - COMPLETED
- REDUCE operation support (see Priority Task #5 below)
- AnyMatch/NoneMatch pattern detection

### 4. 🚧 Incrementally Enable Tests (IN PROGRESS)
**File**: `sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/Java8CleanUpTest.java`

**Status**: 13 tests currently enabled in testSimpleForEachConversion method:

Enabled tests (status needs verification via test run):
1. ✅ SIMPLECONVERT - simple forEach (PASSING)
2. ✅ CHAININGMAP - map operation (PASSING)
3. ✅ ChainingFilterMapForEachConvert - filter + map (PASSING)
4. ✅ SmoothLongerChaining - map + filter + map chain (PASSING)
5. ✅ MergingOperations - operation merging (PASSING)
6. ✅ BeautificationWorks - lambda beautification (PASSING)
7. ✅ BeautificationWorks2 - more beautification (PASSING)
8. ✅ NonFilteringIfChaining - complex nested IFs (PASSING)
9. ✅ ContinuingIfFilterSingleStatement - continue as negated filter (PASSING)
10. ✅ SimpleReducer - basic reduce operation (ENABLED)
11. 🆕 ChainedReducer - filter + reduce (NEWLY ENABLED)
12. 🆕 IncrementReducer - increment pattern (NEWLY ENABLED)
13. 🆕 AccumulatingMapReduce - map + reduce (NEWLY ENABLED)

Next tests to enable (require additional implementation):
14. ⏳ DOUBLEINCREMENTREDUCER - double increment pattern
15. ⏳ DecrementingReducer - decrement pattern
16. ⏳ ChainedReducerWithMerging - complex reducer with merging
17. ⏳ StringConcat - string concatenation
18. ⏳ ChainedAnyMatch - anyMatch
19. ⏳ ChainedNoneMatch - noneMatch
20. ⏳ ...additional test cases

For each test:
1. Enable the test by adding it to `@EnumSource(value = UseFunctionalLoop.class, names = {"SIMPLECONVERT", "CHAININGMAP", ...})`
2. Run: `mvn test -pl sandbox_functional_converter_test -Dtest=Java8CleanUpTest#testSimpleForEachConversion`
3. Fix implementation issues revealed by the test
4. Repeat until test passes

**Note**: Tests 10-16 require REDUCE operation support which has been implemented but needs testing.

### 5. ✅ Implement REDUCE Operation Support (COMPLETED - TESTING IN PROGRESS)
**Files**: 
- `StreamPipelineBuilder.java` - REDUCE operation parsing implemented
- `ProspectiveOperation.java` - Enhanced REDUCE lambda generation with method references
- `PreconditionsChecker.java` - Already detects reducers (i++, +=, etc.)

**Current Status**: 
- ✅ PreconditionsChecker can detect reducers (postfix/prefix increment, compound assignments)
- ✅ ProspectiveOperation fully supports REDUCE with ReducerType enum
- ✅ StreamPipelineBuilder parses and detects REDUCE operations
- ✅ wrapPipeline() wraps REDUCE results in assignments
- ✅ Method references (Integer::sum) supported
- ✅ Implementation complete - now testing with enabled test cases

**Implementation Details**:

1. **✅ Parse REDUCE patterns in StreamPipelineBuilder.parseLoopBody()**:
   - ✅ Detect `i++`, `i--` → `.map(_item -> 1).reduce(i, Integer::sum)`
   - ✅ Detect `sum += x` → `.reduce(sum, Integer::sum)` or similar
   - ✅ Detect `count += 1` → `.map(_item -> 1).reduce(count, Integer::sum)`
   - ✅ Track accumulator variable name via `accumulatorVariable` field

2. **✅ Generate REDUCE operations in ProspectiveOperation**:
   - ✅ Create mapping lambda: `_item -> 1` for counting operations
   - ✅ Create reducer method reference: `Integer::sum` for INCREMENT/SUM
   - ✅ Create reducer lambda for other operators: `(accumulator, _item) -> accumulator + _item`
   - ✅ Handle identity value as accumulator variable reference

3. **✅ Update StreamPipelineBuilder.wrapPipeline()**:
   - ✅ REDUCE operations return a value, not void
   - ✅ Wrap in assignment: `variable = stream.reduce(...)`
   - ✅ Detect accumulator variable from the loop body
   - ✅ Create Assignment node instead of ExpressionStatement

4. **✅ Handle different reducer patterns**:
   - ✅ `i++` / `i--` → counting with map to 1, ReducerType.INCREMENT/DECREMENT
   - ✅ `sum += expr` → ReducerType.SUM with Integer::sum
   - ✅ `product *= expr` → ReducerType.PRODUCT with multiply lambda
   - ⏳ `s += string` → ReducerType.STRING_CONCAT (implemented, needs testing)

**Challenges Addressed**:
- ✅ REDUCE changes the overall structure (assignment vs expression statement) - handled by wrapPipeline
- ✅ Track which variable is the accumulator - accumulatorVariable field
- ✅ Determine the correct identity value - use accumulator variable reference
- ✅ Generate method references or appropriate lambda expressions - createAccumulatorExpression
- ⏳ Complex interaction with other operations (filter + reduce, map + reduce) - needs testing


**Estimated Effort**: 6-8 hours

**Dependencies**: Tests 10-16 are blocked until this is implemented.

## Detailed Implementation Plan

### Phase 1: Basic Operations (MAP, FILTER, FOREACH) - ✅ COMPLETED
Target tests: CHAININGMAP, ChainingFilterMapForEachConvert, ContinuingIfFilterSingleStatement

1. ✅ Implement ProspectiveOperation.createLambda() for MAP:
   - Detect variable declarations: `String s = l.toString();`
   - Generate: `l -> l.toString()` with variable name mapping
   - Handle chaining: multiple variable declarations become multiple maps

2. ✅ Implement ProspectiveOperation.createLambda() for FILTER:
   - Extract condition from if statement
   - Handle negation for continue patterns
   - Generate: `l -> (l != null)` or `l -> !(l == null)`

3. ✅ Implement ProspectiveOperation.createLambda() for FOREACH:
   - Copy loop body statements into lambda block
   - Handle single expression vs block

4. ✅ Test pipeline building with combinations

**Status**: All Phase 1 work is complete. 9 tests enabled including ContinuingIfFilterSingleStatement.

### Phase 2: Reductions (REDUCE) - ❌ NOT STARTED (MAJOR WORK REQUIRED)
Target tests: SimpleReducer, ChainedReducer, IncrementReducer, DecrementingReducer

**Status**: See Priority Task #5 above for detailed requirements.

1. ❌ Implement reducer detection in StreamPipelineBuilder:
   - Leverage existing PreconditionsChecker.isReducer() and getReducer()
   - Parse reducer patterns in parseLoopBody()
   - Track accumulator variable

2. ❌ Implement ProspectiveOperation for REDUCE:
   - Generate map to constant: `_item -> 1` for counting
   - Generate accumulator lambda: `(accumulator, _item) -> accumulator + _item`
   - Handle different operators: +, -, *, etc.
   - Use method references where possible: `Integer::sum`

3. ❌ Handle identity values:
   - 0 for addition/subtraction
   - 1 for multiplication
   - "" for string concatenation

4. ❌ Wrap result in assignment: `variable = stream.reduce(...)`
   - Update wrapPipeline() to detect REDUCE operations
   - Generate Assignment instead of ExpressionStatement

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

- ✅ ProspectiveOperation completion: 4-6 hours (COMPLETED)
- ✅ PreconditionsChecker updates: 1-2 hours (COMPLETED)
- ✅ Basic Refactorer with parseLoopBody: 4-5 hours (COMPLETED)
- ✅ Initial test enablement (8 tests): 2-3 hours (COMPLETED)
- ✅ StreamPipelineBuilder class creation: 3-4 hours (COMPLETED)
- ✅ StreamPipelineBuilder integration into Refactorer: 2-3 hours (COMPLETED)
- ✅ Continue statement handling: 2-3 hours (COMPLETED)
- ✅ REDUCE operation implementation: 4-6 hours (COMPLETED)
- 🚧 REDUCE test validation and debugging: 2-4 hours (IN PROGRESS)
- ⏳ Advanced pattern recognition (matchers, early returns): 4-6 hours
- ⏳ Remaining test fixing and iteration: 4-6 hours
- **Total Completed: ~26-35 hours**
- **Total In Progress: ~2-4 hours**
- **Total Remaining: ~8-12 hours**

## Contact

For questions, see the original NetBeans implementation or the Eclipse JDT documentation.

For help with Eclipse plugin development, see:
https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Fguide%2Fjdt_api_overview.htm
