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
- [x] Basic Refactorer with simple forEach conversion
- [x] ProspectiveOperation enum with all 6 operation types
- [x] ProspectiveOperation lambda generation methods (setEager, createLambda, getStreamMethod, getStreamArguments, getReducingVariable)
- [x] PreconditionsChecker reducer detection (isReducer, getReducer)
- [x] ProspectiveOperation operation merging (mergeRecursivelyIntoComposableOperations)
- [x] Seven test cases now passing:
  - SIMPLECONVERT (simple forEach)
  - CHAININGMAP (map operation)
  - ChainingFilterMapForEachConvert (filter + map + forEach)
  - SmoothLongerChaining (multiple map operations chained)
  - MergingOperations (complex if-statement with side effects)
  - BeautificationWorks (variable reassignment handling)
  - BeautificationWorks2 (unused variable elimination)
- [x] Pattern recognition for filters (if-continue → filter)
- [x] Pattern recognition for map operations (variable declarations)
- [x] Variable dependency tracking (basic implementation)
- [x] Operation merging/optimization (merging consecutive operations)

### ⏳ In Progress
- [ ] StreamPipelineBuilder class - core analysis completed via Refactorer methods
- [ ] Reducer operations (REDUCE type operations)
- [ ] Early return patterns (anyMatch/noneMatch)

### ❌ Not Yet Implemented
- [ ] Formal StreamPipelineBuilder class (functionality exists but distributed across Refactorer)
- [ ] Full reducer support (SimpleReducer, ChainedReducer, IncrementReducer, etc.)
- [ ] Match operations (ChainedAnyMatch, ChainedNoneMatch)
- [ ] Remaining test cases (13+ still disabled)

## Priority Tasks

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

### 3. ⏳ Enable Additional Tests (ONGOING - 7 PASSING, 2 ENABLED)
**File**: `sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/Java8CleanUpTest.java`

**Status**: 7 tests confirmed passing, 2 additional tests now enabled for validation

Currently passing tests:
1. ✅ SIMPLECONVERT - simple forEach
2. ✅ CHAININGMAP - map operation
3. ✅ ChainingFilterMapForEachConvert - filter + map + forEach
4. ✅ SmoothLongerChaining - multiple map operations chained
5. ✅ MergingOperations - complex if-statement with side effects
6. ✅ BeautificationWorks - variable reassignment handling
7. ✅ BeautificationWorks2 - unused variable elimination
8. ⏳ ContinuingIfFilterSingleStatement - continue as filter with nested if (ENABLED - testing)
9. ⏳ NonFilteringIfChaining - if statements with side effects (ENABLED - testing)

**Next priority tests to enable** (in suggested order):

10. ⏳ SimpleReducer - basic reduce operation
11. ⏳ ChainedReducer - filter + reduce
12. ⏳ IncrementReducer - increment operations
13. ⏳ DecrementingReducer - decrement operations
14. ⏳ ChainedAnyMatch - anyMatch pattern
15. ⏳ ChainedNoneMatch - noneMatch pattern
16. ⏳ ...continue with remaining tests

For each test:
1. Enable the test by adding it to `@EnumSource(value = UseFunctionalLoop.class, names = {...})`
2. Run: `mvn test -pl sandbox_functional_converter_test -Dtest=Java8CleanUpTest#testSimpleForEachConversion`
3. Fix implementation issues revealed by the test
4. Repeat until test passes

### 4. Consider StreamPipelineBuilder Refactoring (LOWER PRIORITY)
**File**: `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/Refactorer.java`

**Status**: Core functionality exists but is distributed across Refactorer methods

**Note**: The NetBeans implementation uses a separate StreamPipelineBuilder class (~400 lines) to encapsulate pipeline building logic. Our current implementation has this logic distributed across methods in the Refactorer class (~370 lines). This is working well for the 7 passing tests.

**Decision point**: 
- Option A: Continue with current architecture and enable more tests
- Option B: Refactor to extract StreamPipelineBuilder as a separate class

**Recommendation**: Continue with Option A (current architecture) since it's already working for 35% of tests. Consider refactoring only if complexity becomes unmanageable when adding reducer/matcher support.

## Detailed Implementation Plan

### Phase 1: MAP, FILTER, FOREACH Operations ✅ (MOSTLY COMPLETE)
Target tests: ✅ CHAININGMAP, ✅ ChainingFilterMapForEachConvert, ⏳ ContinuingIfFilterSingleStatement

**Status**: Basic functionality implemented and working

1. ✅ ProspectiveOperation.createLambda() for MAP:
   - Detect variable declarations: `String s = l.toString();`
   - Generate: `l -> l.toString()` with variable name mapping
   - Handle chaining: multiple variable declarations become multiple maps

2. ✅ ProspectiveOperation.createLambda() for FILTER:
   - Extract condition from if statement
   - Handle negation for continue patterns
   - Generate: `l -> (l != null)`

3. ✅ ProspectiveOperation.createLambda() for FOREACH:
   - Copy loop body statements into lambda block
   - Handle single expression vs block

4. ✅ Pipeline building with combinations working

**Next steps for Phase 1**:
- Enable ContinuingIfFilterSingleStatement test
- Verify nested filter handling
- Test edge cases with complex filtering

### Phase 2: Reductions (REDUCE) ⏳ (NOT STARTED)
Target tests: SimpleReducer, ChainedReducer, IncrementReducer, DecrementingReducer, DOUBLEINCREMENTREDUCER

**Status**: Reducer detection implemented in PreconditionsChecker, but transformation not yet complete

1. ⏳ Implement reducer detection in PreconditionsChecker:
   - ✅ Detect `i++`, `sum += x`, etc. (basic detection done)
   - ⏳ Track accumulator variable
   - ⏳ Identify accumulator type and operation

2. ⏳ Implement ProspectiveOperation for REDUCE:
   - Generate map to constant: `_item -> 1` for counting
   - Generate accumulator lambda: `(accumulator, _item) -> accumulator + _item`
   - Handle different operators: +, -, *, etc.
   - Use method references where possible: `Integer::sum`

3. ⏳ Handle identity values:
   - 0 for addition/subtraction
   - 1 for multiplication
   - "" for string concatenation

4. ⏳ Wrap result in assignment: `variable = stream.reduce(...)`

**Estimated effort**: 4-6 hours

### Phase 3: Early Returns (ANYMATCH, NONEMATCH) ⏳ (NOT STARTED)
Target tests: ChainedAnyMatch, ChainedNoneMatch

**Status**: Not yet implemented

1. ⏳ Detect early return patterns:
   - `if (condition) return true;` → anyMatch
   - `if (condition) return false;` → noneMatch

2. ⏳ Generate match lambdas:
   - Extract condition, possibly with preceding maps
   - Chain operations before the match

3. ⏳ Wrap in if statement:
   - `if (stream.anyMatch(...)) { return true; }`
   - `if (!stream.noneMatch(...)) { return false; }`

**Estimated effort**: 2-3 hours

### Phase 4: Complex Chains & Optimization ⏳ (PARTIALLY COMPLETE)
Target tests: ✅ SmoothLongerChaining, ✅ NonFilteringIfChaining, ✅ MergingOperations

**Status**: Basic optimization working, advanced cases may need refinement

1. ✅ Variable dependency tracking:
   - Track which variables each operation uses/produces
   - Ensure pipeline stages have access to needed variables

2. ✅ Operation merging:
   - Consecutive MAPs with same variable
   - Consecutive FILTERs (combine with &&)

3. ✅ Handle complex side effects:
   - If statements with side effects → map with if inside
   - Nested if statements

4. ⏳ Optimize lambda bodies:
   - ✅ Remove unnecessary blocks (partial)
   - ⏳ Use expression lambdas where possible
   - ⏳ Use method references where applicable

**Remaining effort**: 1-2 hours for refinements

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

- [ ] Phase 1 complete: All MAP/FILTER/FOREACH tests passing (8/20 tests)
  - [x] SIMPLECONVERT
  - [x] CHAININGMAP
  - [x] ChainingFilterMapForEachConvert
  - [x] SmoothLongerChaining
  - [x] MergingOperations
  - [x] BeautificationWorks
  - [x] BeautificationWorks2
  - [ ] ContinuingIfFilterSingleStatement
- [ ] Phase 2 complete: All REDUCE tests passing (13/20 tests)
  - [ ] SimpleReducer
  - [ ] ChainedReducer
  - [ ] IncrementReducer
  - [ ] DecrementingReducer
  - [ ] DOUBLEINCREMENTREDUCER
- [ ] Phase 3 complete: All MATCH tests passing (15/20 tests)
  - [ ] ChainedAnyMatch
  - [ ] ChainedNoneMatch
- [ ] Phase 4 complete: All 20+ test cases in `UseFunctionalLoop` enum pass
- [ ] No regressions in disabled test cases (they should not change)
- [ ] Code review passes
- [ ] CodeQL security scan passes
- [ ] JaCoCo coverage maintains or improves

## Progress Tracking

### Current Status: ~35% Complete (7 of 20+ tests passing)

**Recently Completed** (as of last merge):
- ✅ Enabled 6 additional tests beyond SIMPLECONVERT
- ✅ Implemented filter detection and transformation
- ✅ Implemented map chaining
- ✅ Implemented variable tracking and renaming
- ✅ Implemented operation merging
- ✅ Implemented side-effect handling in map operations

**Next Milestone: 40% - Enable ContinuingIfFilterSingleStatement**
- Goal: Handle continue statements as filters with nested conditions
- Estimated effort: 1-2 hours
- Files to modify: Potentially Refactorer.java for nested filter handling

**Following Milestone: 50% - Enable NonFilteringIfChaining**
- Goal: Handle if statements with side effects that don't filter
- Estimated effort: 1-2 hours
- Files to modify: Potentially ProspectiveOperation.java for side-effect maps

**Major Milestone: 60% - First Reducer Test**
- Goal: Enable SimpleReducer test
- Estimated effort: 4-6 hours
- Files to modify: Refactorer.java, ProspectiveOperation.java
- This will unlock the reducer transformation pathway

## Estimated Effort

- ✅ ProspectiveOperation completion: 4-6 hours (COMPLETED)
- ✅ PreconditionsChecker updates: 1-2 hours (COMPLETED)
- ✅ Phase 1 (MAP/FILTER/FOREACH): 6-8 hours (MOSTLY COMPLETED - 7/8 tests passing)
- ⏳ Phase 1 completion: 1-2 hours (1 test remaining)
- ⏳ Phase 2 (REDUCE operations): 6-8 hours (NOT STARTED)
- ⏳ Phase 3 (MATCH operations): 3-4 hours (NOT STARTED)
- ⏳ Phase 4 refinements: 2-3 hours (PARTIALLY COMPLETE)
- **Total Completed: ~12-16 hours**
- **Total Remaining: ~12-17 hours**
- **Overall Progress: ~50% of implementation effort complete**

## Next Immediate Steps

### Step 1: Enable ContinuingIfFilterSingleStatement Test
**Priority**: HIGH
**Estimated time**: 1-2 hours

1. Add "ContinuingIfFilterSingleStatement" to the enabled test list in Java8CleanUpTest.java
2. Run the test to identify what's missing
3. Likely needed: Handle nested filter conditions after continue-based filters
4. Implementation: May need to enhance filter combining logic in Refactorer

### Step 2: Enable NonFilteringIfChaining Test  
**Priority**: MEDIUM
**Estimated time**: 1-2 hours

1. Add "NonFilteringIfChaining" to the enabled test list
2. This test has if statements with side effects that shouldn't become filters
3. Verify that the current map-with-side-effects handling works correctly

### Step 3: Begin Reducer Implementation
**Priority**: HIGH (unlocks 5+ tests)
**Estimated time**: 6-8 hours

This is the next major feature to implement:

1. Study the expected output for SimpleReducer, ChainedReducer, etc.
2. Enhance Refactorer to detect reducer patterns
3. Implement REDUCE operation type in ProspectiveOperation
4. Generate proper reduce() method calls with:
   - Identity values
   - Accumulator functions
   - Result assignment
5. Enable and test SimpleReducer first
6. Then enable ChainedReducer (combines filter + reduce)
7. Then enable increment/decrement variants

### Step 4: Implement Match Operations
**Priority**: MEDIUM (unlocks 2 tests)
**Estimated time**: 3-4 hours

1. Detect early return patterns in loops
2. Transform `if (cond) return true;` → `stream.anyMatch(cond)`
3. Transform `if (cond) return false;` → `stream.noneMatch(cond)`
4. Wrap in appropriate if statement
5. Enable ChainedAnyMatch test
6. Enable ChainedNoneMatch test

## Contact

For questions, see the original NetBeans implementation or the Eclipse JDT documentation.

For help with Eclipse plugin development, see:
https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Fguide%2Fjdt_api_overview.htm
