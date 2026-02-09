# Functional Loop Converter - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#functional_converter) | [TODO](TODO.md)

## Overview
The functional loop converter transforms imperative enhanced for-loops into functional Java 8 Stream pipelines. This document describes the architecture and implementation details of the `StreamPipelineBuilder` approach.

## V2 Parallel Implementation Strategy → Unified Implementation (February 2026)

**Status**: ✅ V2 is the complete implementation — All patterns via ULR pipeline

### Background
Issue [#450](https://github.com/carstenartur/sandbox/issues/450) introduced the Unified Loop Representation (ULR) to enable:
- AST-independent loop modeling for easier testing and maintenance
- Better separation of concerns between AST parsing and transformation logic

### Current Architecture

`LoopToFunctionalV2` uses the clean ULR pipeline for **all** patterns:

```
JDT AST → JdtLoopExtractor → LoopModel → LoopModelTransformer → ASTStreamRenderer → JDT AST
```

**`JdtLoopExtractor`** bridges JDT AST to the abstract `LoopModel`, detecting:
- `if (cond) continue;` → `FilterOp` (negated)
- `if (cond) { body }` → `FilterOp` + nested body operations
- Variable declaration `Type x = expr;` → `MapOp`
- `collection.add(expr)` → `CollectTerminal` (TO_LIST or TO_SET)
- Accumulator patterns (`+=`, `++`, `*=`) → `ReduceTerminal`
- `if (cond) return true/false;` → `MatchTerminal` (anyMatch/noneMatch)
- Simple side effects → `ForEachTerminal`

**`LoopModelTransformer`** drives the `ASTStreamRenderer` to produce JDT AST nodes.

**Direct forEach optimization**: For simple forEach without operations on COLLECTION/ITERABLE sources, generates idiomatic `collection.forEach(...)` instead of `collection.stream().forEach(...)`.

**Key Files**:
- `LoopToFunctionalV2.java` — Loop-to-functional converter (orchestrator)
- `JdtLoopExtractor.java` — JDT AST → LoopModel (pattern detection)
- `ASTStreamRenderer.java` — LoopModel → JDT AST nodes (code generation)
- `LoopModelTransformer.java` — Drives renderer through model operations

**Core Module** (`sandbox-functional-converter-core`):
- `LoopModel`, `SourceDescriptor`, `ElementDescriptor`, `LoopMetadata` — ULR data model
- `FilterOp`, `MapOp`, `CollectTerminal`, `ReduceTerminal`, `MatchTerminal` — Operations/terminals
- `LoopModelBuilder` — Fluent builder for constructing models
- `StringRenderer` — Test renderer producing Java code strings (no OSGi needed)
- `LoopModelTransformer` — Transformation engine (drives any renderer)
- All testable without Eclipse/OSGi via `mvn test`

### Phase History (Completed)

The following phases were completed during the V1/V2 parallel development:

1. **Phase 1**: Infrastructure Setup — V2 infrastructure alongside V1
2. **Phase 2/6**: ULR-Native Implementation — JdtLoopExtractor + ASTStreamRenderer
3. **Phase 5**: JDT AST Renderer — ASTStreamRenderer for JDT integration
4. **Phase 7**: Iterator Loop Support — Iterator-based loop conversion
5. **Phase 7.5**: Direct forEach Optimization — Idiomatic `collection.forEach(...)`
6. **Phase 7.6**: V1/V2 Consolidation — Removed V1 classes
7. **Phase 7.7**: Full V2 Body Analysis — JdtLoopExtractor detects all patterns natively

### Phase 6: Complete ULR Integration (COMPLETED - January 2026)
**Goal**: Remove V1 delegation and implement native ULR pipeline in LoopToFunctionalV2

**Status**: ✅ Phase 6 Complete - Full ULR pipeline operational

**Completed Deliverables**:
1. **JdtLoopExtractor** (`org.sandbox.jdt.internal.corext.fix.helper`):
   - Bridges JDT AST (`EnhancedForStatement`) to abstract ULR `LoopModel`
   - **Source type detection**: Identifies ARRAY, COLLECTION, ITERABLE via type binding analysis
     - Uses `ITypeBinding.isArray()` for arrays
     - Checks type hierarchy for `java.util.Collection` and subinterfaces (List, Set, Queue, Deque)
     - Falls back to ITERABLE for other iterable types
   - **Control flow analysis**: `LoopBodyAnalyzer` visitor detects:
     - Break statements (prevents conversion)
     - Continue statements (prevents conversion)
     - Return statements (prevents conversion)
     - Collection modifications (add/remove/clear/set calls)
   - **Integration**: Delegates to `LoopModelBuilder` for model construction
   
2. **LoopToFunctionalV2 Native Implementation**:
   - **Removed**: V1 delegation pattern (`v1Delegate` field removed)
   - **find() method**: 
     - Uses `JdtLoopExtractor` to extract `LoopModel` from AST
     - Validates convertibility via `LoopMetadata` (checks for break/continue/return)
     - Stores model in `ReferenceHolder` for later rewrite
     - Uses `HelperVisitor.callEnhancedForStatementVisitor()` with BiPredicate pattern
   - **rewrite() method**:
     - Re-extracts model (necessary due to framework architecture)
     - Creates `ASTStreamRenderer` with JDT AST and ASTRewrite
     - Uses `LoopModelTransformer<Expression>` to transform model to stream AST
     - Replaces original for-statement with stream expression
   - **Import management**:
     - Automatically adds `java.util.Arrays` for array sources
     - Automatically adds `java.util.stream.StreamSupport` for iterable sources
     - Automatically adds `java.util.stream.Collectors` for collect terminals

3. **ULR Pipeline**:
   ```
   EnhancedForStatement (JDT AST)
           ↓
      JdtLoopExtractor (extracts)
           ↓
      LoopModel (ULR - AST-independent)
           ↓
      LoopModelTransformer (transforms)
           ↓
      ASTStreamRenderer (renders)
           ↓
   Expression (JDT AST - stream pipeline)
   ```

4. **Test Suite** (`LoopToFunctionalV2Test`):
   - Simple forEach conversion from List to `items.stream().forEach()`
   - Array iteration generates `Arrays.stream(array).forEach()`
   - Loops with break/continue/return remain unchanged (negative tests)
   - Uses `MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2` constant

**Implementation Notes**:
- Collection detection uses `ITypeBinding.getErasure()` for robust type checking
- Ordered parameter set to `false` for simple forEach (produces `forEach()` not `forEachOrdered()`)
- Model re-extraction in rewrite() is necessary due to framework architecture (model not passed through operation)
- Body statements converted to strings via `toString()` for model storage (canonical representation)

**Architecture Changes**:
- V2 no longer delegates to V1 - fully independent ULR-based implementation
- `JdtLoopExtractor` serves as the AST-to-ULR bridge component
- Complete pipeline from JDT AST through abstract model back to JDT AST

**Known Limitations**:
- Model re-extraction in rewrite() creates duplicate work
- Body statement toString() may normalize formatting
- Collection modification detection doesn't verify receiver (may produce false positives)

### Phase 7: Iterator Loop Support (COMPLETED - January 2026)
**Goal**: Activate iterator-based loop conversion for modern, idiomatic Java code

**Status**: ✅ Complete - ITERATOR_LOOP activated and 20 tests enabled

**Completed Deliverables**:
1. **Activation in UseFunctionalCallFixCore**:
   - Uncommented `ITERATOR_LOOP(new IteratorLoopToFunctional())` enum entry
   - Added `IteratorLoopToFunctional` import
   - Updated comments to reflect Phase 7 activation status

2. **Cleanup Integration**:
   - Modified `UseFunctionalCallCleanUpCore.computeFixSet()` to include ITERATOR_LOOP
   - ITERATOR_LOOP runs alongside LOOP (V1) when `USEFUNCTIONALLOOP_CLEANUP` is enabled
   - Both enhanced for-loops and iterator-based loops now supported in single cleanup pass

3. **Test Activation**:
   - Enabled 14 tests in `IteratorLoopToStreamTest` (removed @Disabled annotations)
   - Enabled 6 tests in `IteratorLoopConversionTest` (removed @Disabled annotations)
   - **Total**: 20 iterator tests now active and running

4. **New Test Suites**:
   - Created `LoopBidirectionalTransformationTest` (5 tests):
     - 2 active tests validating for → Stream and Iterator → Stream
     - 3 future tests documenting desired Stream → for, for → while, while → for transformations
   - Created `AdditionalLoopPatternsTest` (9 tests):
     - 6 active negative tests (classic while, do-while, complex iterators should NOT convert)
     - 3 future tests for index-based for loops and collection access patterns

5. **Documentation Updates**:
   - Updated TODO.md with Phase 7 section
   - Updated ARCHITECTURE.md with Phase 7 documentation
   - Documented supported patterns and test coverage

**Supported Iterator Patterns** (Phase 7):
1. **while-iterator**: `Iterator<T> it = coll.iterator(); while (it.hasNext()) { T item = it.next(); ... }`
   - Converts to: `collection.stream().forEach(item -> ...)`
   - Currently limited to forEach pattern; map, filter, collect, reduce, match patterns planned for future implementation

2. **for-loop-iterator**: `for (Iterator<T> it = coll.iterator(); it.hasNext(); ) { T item = it.next(); ... }`
   - Converts to: `collection.stream().forEach(item -> ...)`
   - Same forEach-only support as while-iterator

**Implementation Architecture** (Phase 7):
- `IteratorLoopToFunctional` extends `AbstractFunctionalCall<ASTNode>`
- Uses `IteratorPatternDetector` to identify iterator patterns in AST
- Uses `IteratorLoopAnalyzer` to validate safety (no breaks, continues, etc.)
- Uses `IteratorLoopBodyParser` to extract loop body and next() variable
- Performs a direct AST rewrite of supported iterator loops into functional stream pipelines
- Marks both iterator declaration and loop statement as processed to prevent double conversion

**Test Coverage** (Phase 7):
| Test File | Tests | Status |
|-----------|-------|--------|
| `IteratorLoopToStreamTest` | 5 enabled, 9 disabled | ✅ Simple forEach functional, safety bug + advanced patterns disabled |
| `IteratorLoopConversionTest` | 6 | ✅ Enabled |
| `LoopBidirectionalTransformationTest` | 2 active, 3 future | ✅ New |
| `AdditionalLoopPatternsTest` | 6 active, 3 future | ✅ New |
| **Total Active** | **19** | **11 iterator + 8 new tests** |

**Key Decisions**:
- Iterator loops convert to same stream operations as enhanced for-loops
- No new cleanup constant needed - reuses `USEFUNCTIONALLOOP_CLEANUP`
- Both LOOP and ITERATOR_LOOP run in same cleanup pass for comprehensive conversion
- Negative tests document patterns that should NOT convert (do-while, classic while, etc.)
- Future tests document desired bidirectional transformations (Stream → for, etc.)

### Phase 7.5: Direct forEach Optimization (COMPLETED - January 2026)
**Goal**: Generate idiomatic `collection.forEach(...)` instead of `collection.stream().forEach(...)` for simple forEach patterns

**Status**: ✅ Complete - V2 now matches V1's idiomatic output for simple forEach

**Motivation**:
V2 initially generated `collection.stream().forEach(...)` for all forEach operations, while V1 optimized simple cases to use `collection.forEach(...)` directly. This phase aligns V2 with V1 to produce more idiomatic Java code for the simplest forEach patterns.

**Completed Deliverables**:
1. **Direct forEach Detection** (`LoopToFunctionalV2.canUseDirectForEach()`):
   - Checks if loop has NO intermediate operations (no filter, map, etc.)
   - Verifies terminal operation is `ForEachTerminal`
   - Confirms source is COLLECTION or ITERABLE (arrays excluded - they lack forEach method)
   - Returns `true` for simple forEach patterns eligible for direct rendering

2. **Direct forEach Rendering** (`ASTStreamRenderer.renderDirectForEach()`):
   - Generates `collection.forEach(item -> ...)` for collections/iterables
   - Falls back to `Arrays.stream(array).forEach(...)` for arrays (arrays lack forEach method)
   - Preserves original AST body to maintain binding information
   - Supports both single-statement and block lambda bodies

3. **Import Management Optimization**:
   - Direct forEach path skips stream-related imports (`StreamSupport`, etc.)
   - Only adds necessary imports based on actual code generation
   - Arrays still require `java.util.Arrays` import for `Arrays.stream()`

4. **Test Coverage**:
   - Added 3 tests in `ASTStreamRendererTest`:
     - `testRenderDirectForEach_Collection`: Validates direct forEach on collections
     - `testRenderDirectForEach_Iterable`: Validates direct forEach on iterables
     - `testRenderDirectForEach_Array_FallbackToStream`: Validates array fallback to stream
   - Updated `LoopToFunctionalV2Test.test_SimpleForEach_V2`: Now expects `items.forEach(...)` instead of `items.stream().forEach(...)`
   - Re-enabled `FeatureParityTest.parity_SimpleForEachConversion`: Validates V1/V2 parity for simple forEach

5. **Documentation**:
   - Comprehensive JavaDoc on immutability safety (direct forEach works with both mutable and immutable collections)
   - Comments explaining array exclusion from direct forEach optimization

**Implementation Details**:
```java
// Before (Phase 6):
list.stream().forEach(item -> System.out.println(item));

// After (Phase 7.5):
list.forEach(item -> System.out.println(item));

// Arrays still use stream (no forEach method):
Arrays.stream(array).forEach(item -> System.out.println(item));
```

**Immutability Considerations**:
- Direct forEach is safe for both mutable and immutable collections
- Immutable collections (List.of, Collections.unmodifiableList, etc.) support forEach
- forEach only reads elements, doesn't modify collection structure
- Side effects within lambda body are user's responsibility

**Decision Logic**:
```
if (no intermediate operations && terminal is forEach && source is COLLECTION/ITERABLE)
    → use direct forEach: collection.forEach(...)
else
    → use stream pipeline: collection.stream().filter(...).forEach(...)
```

**Success Criteria** ✅:
- V1 and V2 generate identical output for simple forEach patterns
- `FeatureParityTest.parity_SimpleForEachConversion` passes
- No unused imports added for direct forEach path
- Array handling correctly falls back to stream-based approach

### Phase 8: V1 Deprecation and V2 Full Implementation ✅ COMPLETED (February 2026)
**Goal**: Make ULR the sole implementation — no V1 fallback

**Completed**:
1. ✅ V1 (`LoopToFunctional`) removed, V2 (`LoopToFunctionalV2`) is the only implementation
2. ✅ `LOOP` enum uses `LoopToFunctionalV2`, `LOOP_V2` removed
3. ✅ V2-specific cleanup classes removed
4. ✅ `JdtLoopExtractor.analyzeAndAddOperations()` detects all patterns natively
   - Filter (if-continue, if-guard), Map, Collect, Reduce, Match, ForEach
5. ✅ `LoopToFunctionalV2.rewrite()` uses `LoopModelTransformer` + `ASTStreamRenderer` for ALL patterns
6. ✅ No dependency on V1's `Refactorer` / `PreconditionsChecker` / `StreamPipelineBuilder`
7. ✅ 19 pattern transformation tests pass in core module without OSGi

### Architecture Benefits
- **Testability**: ULR model can be tested without Eclipse runtime
- **Maintainability**: Clear separation between AST parsing and transformation
- **Portability**: ULR model can be reused in other contexts (e.g., IntelliJ plugin)
- **Safety**: Parallel implementation ensures no regressions during migration

### Related Issues
- [#450 - Unified Loop Representation](https://github.com/carstenartur/sandbox/issues/450)
- [#453 - V2 Implementation](https://github.com/carstenartur/sandbox/issues/453)

---

## Class Overview

The functional converter is organized into focused, single-responsibility classes:

### Core Pipeline Classes
| Class | Lines | Responsibility |
|-------|-------|----------------|
| `StreamPipelineBuilder` | ~640 | Main orchestrator - coordinates analysis and pipeline construction |
| `LoopBodyParser` | ~450 | Parses loop bodies into operations using handler chain |
| `PipelineAssembler` | ~280 | Assembles method invocation chains |
| `ProspectiveOperation` | ~760 | Represents individual stream operations |

### Statement Handlers (Strategy Pattern via Enum)
| Class | Lines | Responsibility |
|-------|-------|----------------|
| `StatementHandlerType` | ~580 | Enum with handler strategies for each statement type |
| `StatementHandlerContext` | ~60 | Context holding handler dependencies |
| `SideEffectChecker` | ~120 | Validates safe side-effects in streams |
| `StatementParsingContext` | ~140 | Context object for parsing state |

### Analysis & Detection
| Class | Lines | Responsibility |
|-------|-------|----------------|
| `PreconditionsChecker` | ~605 | Validates loop can be converted |
| `ReducePatternDetector` | ~583 | Detects REDUCE patterns (increment, sum, max/min) |
| `IfStatementAnalyzer` | ~362 | Analyzes IF statements for match patterns |
| `TypeResolver` | ~422 | Type resolution utilities |

### Utilities
| Class | Lines | Responsibility |
|-------|-------|----------------|
| `LambdaGenerator` | ~418 | Creates lambdas and method references |
| `ExpressionUtils` | ~332 | Expression manipulation utilities |
| `StreamConstants` | ~200 | Constants for stream method names (delegates to LibStandardNames) |

## Architecture Patterns

### Strategy Pattern for Statement Handling (Enum-Based)

The `LoopBodyParser` uses the Strategy Pattern implemented via the `StatementHandlerType` enum
to process different statement types. This approach is similar to `ReducerType` and `OperationType`,
providing a concise and type-safe way to handle different statement types:

```
LoopBodyParser
    └── StatementHandlerType (enum)
            ├── VARIABLE_DECLARATION  → MAP operations
            ├── ASSIGNMENT_MAP        → Variable transformation MAPs
            ├── IF_STATEMENT          → FILTER, match patterns
            ├── NON_TERMINAL          → Side-effect MAPs
            └── TERMINAL              → REDUCE, FOREACH
```

**Usage:**
```java
StatementHandlerType handler = StatementHandlerType.findHandler(stmt, context);
if (handler != null) {
    return handler.handle(stmt, context, ops, handlerContext);
}
```

**Benefits:**
- Each handler is an enum constant with its own `canHandle()` and `handle()` methods
- Consistent with `ReducerType` and `OperationType` enums in the codebase
- No need for separate class files per handler
- Easy to add new statement types by adding new enum constants
- Improved testability - each handler can be tested via its enum constant
- Cleaner code - no nested if-else-if chains

## Core Components

### 1. StreamPipelineBuilder
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder`
**Purpose**: Main orchestrator that coordinates analysis and pipeline construction

The `StreamPipelineBuilder` delegates parsing to `LoopBodyParser` and assembly to `PipelineAssembler`.

#### Key Methods

##### `analyze()` - Loop Analysis
Validates preconditions and parses the loop body into a sequence of `ProspectiveOperation` objects.
```java
public boolean analyze()
```
- Returns `true` if loop can be converted to streams
- Checks preconditions via `PreconditionsChecker`
- Delegates to `LoopBodyParser` to extract operations
- Initializes `PipelineAssembler` for pipeline construction
- Validates variable scoping

##### `buildPipeline()` - Pipeline Construction  
Builds the complete stream pipeline as a chained `MethodInvocation`.
```java
public MethodInvocation buildPipeline()
```
- Delegates to `PipelineAssembler.buildPipeline()`
- Returns null if analysis was not successful

##### `wrapPipeline()` - Statement Wrapping
Wraps the pipeline in an appropriate statement type.
```java
public Statement wrapPipeline(MethodInvocation pipeline)
```
- Delegates to `PipelineAssembler.wrapPipeline()`

### 2. LoopBodyParser (NEW)
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.LoopBodyParser`
**Purpose**: Parses enhanced for-loop bodies and extracts stream operations

#### Key Methods

##### `parse()` - Main Entry Point
```java
public List<ProspectiveOperation> parse(Statement body, String loopVarName)
```
- Handles `Block` statements with multiple statements
- Processes variable declarations → MAP operations
- Processes IF statements → FILTER operations (recursive for nested IFs)
- Processes continue statements → negated FILTER operations
- Detects REDUCE operations via `ReducePatternDetector`
- Detects early return patterns for ANYMATCH/NONEMATCH

### 3. PipelineAssembler (NEW)
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.PipelineAssembler`
**Purpose**: Assembles stream pipeline method invocations from operations

#### Key Methods

##### `buildPipeline()` - Build Method Chain
```java
public MethodInvocation buildPipeline()
```
- Determines if `.stream()` prefix is needed
- Chains operations in sequence (filter → map → forEach/reduce)
- Tracks variable names through pipeline

##### `wrapPipeline()` - Wrap in Statement
```java
public Statement wrapPipeline(MethodInvocation pipeline)
```
- **REDUCE operations**: Wraps in assignment (`i = stream.reduce(...)`)
- **ANYMATCH operations**: Wraps in IF with return (`if (stream.anyMatch(...)) return true;`)
- **NONEMATCH operations**: Wraps in IF with return (`if (!stream.noneMatch(...)) return false;`)
- **Other operations**: Wraps in ExpressionStatement

### 4. ProspectiveOperation
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation`
**Purpose**: Represents a single stream operation in the pipeline

Uses the standalone `OperationType` and `ReducerType` enums to define operation behavior.

#### Key Methods
- `getArguments(AST, paramName)` - Returns lambda expression/method reference for the operation
- `getReducerType()` - Returns accumulator variable for REDUCE operations
- `getProducedVariableName()` - Returns the variable name produced by MAP operations

### 5. OperationType (Standalone Enum)
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.OperationType`
**Purpose**: Types of stream operations with built-in lambda body creation

Each enum value encapsulates its own logic for creating the appropriate lambda body
for the stream operation. The enum uses a `LambdaBodyContext` record to receive
necessary context information.

```java
public enum OperationType {
    MAP(StreamConstants.MAP_METHOD) {
        @Override
        public ASTNode createLambdaBody(AST ast, LambdaBodyContext context) {
            // Handle side-effect MAP and expression MAP
        }
    },
    FOREACH(StreamConstants.FOR_EACH_ORDERED_METHOD),
    FILTER(StreamConstants.FILTER_METHOD),
    REDUCE(StreamConstants.REDUCE_METHOD),  // Has special argument handling
    ANYMATCH(StreamConstants.ANY_MATCH_METHOD),
    NONEMATCH(StreamConstants.NONE_MATCH_METHOD),
    ALLMATCH(StreamConstants.ALL_MATCH_METHOD);
    
    // Each type implements:
    public abstract ASTNode createLambdaBody(AST ast, LambdaBodyContext context);
    public String getMethodName();
    public boolean hasSpecialArgumentHandling();
    public boolean isPredicate();
    public boolean isTerminal();
    
    // Context record for lambda body creation:
    public record LambdaBodyContext(
        Expression originalExpression,
        Statement originalStatement,
        String loopVariableName
    ) { }
}
```

#### Benefits of Standalone OperationType
- **Full Encapsulation**: Each operation type contains logic to create its lambda body
- **Minimal Dependencies**: Only requires `AST` and context information
- **Open/Closed Principle**: New operation types can be added without modifying `ProspectiveOperation`
- **Helper Methods**: `isPredicate()`, `isTerminal()`, `hasSpecialArgumentHandling()` for type checking

### 5. ReducerType (Standalone Enum)
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.ReducerType`
**Purpose**: Types of reduction operations with built-in accumulator expression creation

Each enum value encapsulates its own logic for creating the appropriate accumulator expression
(method reference or lambda) for the `reduce()` operation. The enum is fully self-contained and
only requires an `AST` instance to create expressions.

```java
public enum ReducerType {
    INCREMENT {
        @Override
        public Expression createAccumulatorExpression(AST ast, 
                String accumulatorType, boolean isNullSafe) {
            return createSumExpression(ast, accumulatorType, true);
        }
    },
    DECREMENT,      // i--, --i, i -= 1
    SUM,            // sum += x
    PRODUCT,        // product *= x
    STRING_CONCAT,  // s += string
    MAX,            // max = Math.max(max, x)
    MIN,            // min = Math.min(min, x)
    CUSTOM_AGGREGATE // Custom aggregation patterns
    
    // Each type implements:
    public abstract Expression createAccumulatorExpression(
        AST ast, String accumulatorType, boolean isNullSafe);
    
    // Private helper methods for expression creation:
    // - createMethodReference(ast, typeName, methodName)
    // - createBinaryOperatorLambda(ast, operator)
    // - createCountingLambda(ast, operator)
    // - createMaxMinMethodReference(ast, accumulatorType, methodName)
    // - mapToWrapperType(type)
}
```

#### Benefits of Standalone ReducerType
- **Full Encapsulation**: Each reducer type contains all logic needed to create its expression
- **Minimal Dependencies**: Only requires `AST` - no dependency on `LambdaGenerator`
- **Open/Closed Principle**: New reducer types can be added without modifying other classes
- **Single Responsibility**: Each reducer type handles its own expression creation

### 6. StatementHandlerType (Standalone Enum)
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.StatementHandlerType`
**Purpose**: Types of statement handlers for processing loop body statements

Each enum value encapsulates its own logic for checking if it can handle a statement
and for actually processing that statement. This is the same pattern used by
`ReducerType` and `OperationType`.

```java
public enum StatementHandlerType {
    VARIABLE_DECLARATION {
        @Override
        public boolean canHandle(Statement stmt, StatementParsingContext context) {
            return stmt instanceof VariableDeclarationStatement;
        }
        
        @Override
        public ParseResult handle(Statement stmt, StatementParsingContext context,
                List<ProspectiveOperation> ops, StatementHandlerContext handlerContext) {
            // Convert variable declaration to MAP operation
        }
    },
    ASSIGNMENT_MAP,      // s = s.toString() → .map(s -> s.toString())
    IF_STATEMENT,        // if (condition) → .filter(condition) or match patterns
    NON_TERMINAL,        // Side-effect statements in middle of block
    TERMINAL;            // Last statement → FOREACH or REDUCE
    
    // Each type implements:
    public abstract boolean canHandle(Statement stmt, StatementParsingContext context);
    public abstract ParseResult handle(Statement stmt, StatementParsingContext context,
            List<ProspectiveOperation> ops, StatementHandlerContext handlerContext);
    
    // Static helper:
    public static StatementHandlerType findHandler(Statement stmt, StatementParsingContext context);
}
```

#### StatementHandlerContext
A context object holding dependencies needed by handlers:
- `LoopBodyParser parser` - For recursive parsing of nested statements
- `SideEffectChecker sideEffectChecker` - For checking safe side effects

#### Benefits of StatementHandlerType Enum
- **Consistency**: Same pattern as `ReducerType` and `OperationType` enums
- **Single File**: All handler logic in one file instead of 5+ separate class files
- **Type Safety**: Enum constants guarantee all handlers are properly defined
- **Easy Extension**: New handler types can be added as new enum constants
- **Clear Precedence**: Enum order defines handler priority
- **Testability**: Each reducer type's behavior can be tested independently

#### Key Methods
- `getSuitableMethod()` - Returns stream method name ("map", "filter", "forEach", etc.)
- `getArguments()` - Returns lambda expression/method reference for the operation
- `createLambda()` - Generates lambda expression for this operation
- `getReducingVariable()` - Returns accumulator variable for REDUCE operations

### 3. Refactorer
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.Refactorer`
**Purpose**: Main entry point for loop transformation

#### Integration Method
```java
private void refactorWithBuilder() {
    StreamPipelineBuilder builder = new StreamPipelineBuilder(forLoop, preconditions);
    
    if (!builder.analyze()) {
        return; // Cannot convert
    }
    
    MethodInvocation pipeline = builder.buildPipeline();
    if (pipeline == null) {
        return; // Failed to build
    }
    
    Statement replacement = builder.wrapPipeline(pipeline);
    if (replacement != null) {
        rewrite.replace(forLoop, replacement, null);
    }
}
```

#### System Property Toggle
Can switch between builder and legacy implementation:
```java
private boolean useStreamPipelineBuilder() {
    return !"false".equals(System.getProperty("use.stream.pipeline.builder"));
}
```

### 4. PreconditionsChecker
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.PreconditionsChecker`
**Purpose**: Validates loop is safe to refactor

#### Key Validations
- `isSafeToRefactor()` - Overall safety check
- `iteratesOverIterable()` - Confirms loop iterates over a collection
- `isReducer()` - Checks for reducer patterns
- `isAnyMatchPattern()` - Checks for early return with true
- `isNoneMatchPattern()` - Checks for early return with false

### 5. ReducePatternDetector
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.ReducePatternDetector`
**Purpose**: Detects and handles REDUCE patterns in loop statements

#### Key Responsibilities
- Detects postfix/prefix increment: `i++`, `++i`
- Detects compound assignments: `sum += x`, `product *= x`
- Detects regular assignments with infix expressions: `result = result + item`
- Detects Math.max/Math.min patterns: `max = Math.max(max, x)`
- Extracts expressions for MAP operations before REDUCE
- Handles null-safety checks for STRING_CONCAT operations

#### Pattern Detection Methods
- `detectPostfixReducePattern()` - Handles `i++`, `i--`
- `detectPrefixReducePattern()` - Handles `++i`, `--i`
- `detectCompoundAssignmentPattern()` - Handles `+=`, `-=`, `*=`
- `detectInfixReducePattern()` - Handles `result = result + item` (NEW in Jan 2026)
- `detectMathMaxMinPattern()` - Handles `max = Math.max(max, x)`

#### Expression Extraction
- `extractReduceExpression()` - Extracts RHS for MAP operations
  - For compound assignments: returns entire RHS
  - For infix expressions: returns right operand only
- `extractMathMaxMinArgument()` - Extracts non-accumulator argument from Math.max/min
- Detects Math.max/min patterns: `max = Math.max(max, value)`
- Tracks accumulator variable and type
- Adds MAP operations before REDUCE operations

#### Key Methods
- `detectReduceOperation(Statement)` - Main entry point for pattern detection
- `addMapBeforeReduce()` - Adds appropriate MAP before REDUCE based on reducer type
- `getAccumulatorVariable()` - Returns the accumulator variable name
- `getAccumulatorType()` - Returns the accumulator type (int, double, etc.)

### 6. IfStatementAnalyzer
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.IfStatementAnalyzer`
**Purpose**: Analyzes IF statements for patterns convertible to stream operations

#### Detected Patterns
- `isIfWithContinue()` - `if (condition) continue;` → negated filter
- `isIfWithBreak()` - Break statements (prevents conversion)
- `isIfWithLabeledContinue()` - Labeled continues (prevents conversion)
- `isEarlyReturnIf()` - Early return patterns for anyMatch/noneMatch/allMatch

#### Match Pattern Types
```java
enum MatchPatternType {
    ANY_MATCH,   // if (condition) return true; ... return false;
    NONE_MATCH,  // if (condition) return false; ... return true;
    ALL_MATCH,   // if (!condition) return false; ... return true;
    NONE         // No match pattern detected
}
```

### 7. LambdaGenerator
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.LambdaGenerator`
**Purpose**: Creates lambda expressions and method references for stream operations

#### Key Methods
- `createMethodReference(typeName, methodName)` - Creates `TypeName::methodName`
- `createMaxMinMethodReference(accumulatorType, methodName)` - Type-aware max/min references
- `createBinaryOperatorLambda(operator)` - Creates `(acc, item) -> acc op item`
- `createCountingLambda(operator)` - Creates `(acc, _item) -> acc + 1`
- `createAccumulatorExpression(reducerType, accumulatorType, isNullSafe)` - Main entry point

#### Type-Aware Method References
Maps accumulator types to appropriate wrapper classes:
- `int` → `Integer::sum`, `Integer::max`
- `long` → `Long::sum`, `Long::max`
- `double` → `Double::sum`, `Double::max`

### 8. TypeResolver
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.TypeResolver`
**Purpose**: Resolves variable types and annotations

#### Key Methods
- `getVariableType(node, varName)` - Gets the type of a variable
- `getTypeBinding(node, varName)` - Gets the type binding
- `hasNotNullAnnotation(node, varName)` - Checks for @NotNull/@NonNull annotations
- `findVariableDeclaration(node, varName)` - Finds variable declaration

### 9. ExpressionUtils
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.ExpressionUtils`
**Purpose**: Expression manipulation utilities

#### Key Methods
- `createNegatedExpression(ast, condition)` - Creates `!(condition)` with proper parenthesization
- `stripNegation(expr)` - Removes leading `!` from expression
- `isNegatedExpression(expr)` - Checks if expression starts with `!`
- `isIdentityMapping(expr, varName)` - Checks if expression is just the variable (no transformation)

## Conversion Patterns

### Pattern 1: Simple forEach
**Input**:
```java
for (Integer l : ls)
    System.out.println(l);
```
**Output**:
```java
ls.forEach(l -> System.out.println(l));
```
**Operations**: FOREACH

### Pattern 2: Map + forEach
**Input**:
```java
for (Integer l : ls) {
    String s = l.toString();
    System.out.println(s);
}
```
**Output**:
```java
ls.stream().map(l -> l.toString()).forEachOrdered(s -> {
    System.out.println(s);
});
```
**Operations**: MAP → FOREACH

### Pattern 3: Filter + Map + forEach
**Input**:
```java
for (Integer l : ls) {
    if (l != null) {
        String s = l.toString();
        System.out.println(s);
    }
}
```
**Output**:
```java
ls.stream().filter(l -> (l!=null)).map(l -> l.toString()).forEachOrdered(s -> {
    System.out.println(s);
});
```
**Operations**: FILTER → MAP → FOREACH

### Pattern 4: Reduce (Increment)
**Input**:
```java
Integer i = 0;
for (Integer l : ls)
    i++;
```
**Output**:
```java
Integer i = 0;
i = ls.stream().map(_item -> 1).reduce(i, Integer::sum);
```
**Operations**: MAP (to constant) → REDUCE

### Pattern 5: Reduce (Accumulate)
**Input**:
```java
int i = 0;
for (Integer l : ls) {
    i += foo(l);
}
```
**Output**:
```java
int i = 0;
i = ls.stream().map(l -> foo(l)).reduce(i, Integer::sum);
```
**Operations**: MAP (expression) → REDUCE

### Pattern 6: AnyMatch (Early Return)
**Input**:
```java
for (Integer l : ls) {
    String s = l.toString();
    Object o = foo(s);
    if (o == null)
        return true;
}
return false;
```
**Output**:
```java
if (ls.stream().map(l -> l.toString()).map(s -> foo(s)).anyMatch(o -> (o==null))) {
    return true;
}
return false;
```
**Operations**: MAP → MAP → ANYMATCH (wrapped in IF)

### Pattern 7: NoneMatch (Early Return)
**Input**:
```java
for (Integer l : ls) {
    if (condition)
        return false;
}
return true;
```
**Output**:
```java
if (!ls.stream().noneMatch(l -> condition)) {
    return false;
}
return true;
```
**Operations**: NONEMATCH (wrapped in IF with negation)

### Pattern 8: Continue → Filter
**Input**:
```java
for (Integer l : ls) {
    if (l == null) {
        continue;
    }
    String s = l.toString();
    System.out.println(s);
}
```
**Output**:
```java
ls.stream().filter(l -> !(l == null)).map(l -> l.toString()).forEachOrdered(s -> {
    System.out.println(s);
});
```
**Operations**: FILTER (negated) → MAP → FOREACH

### Pattern 9: Side-Effect Statements
**Input**:
```java
for (Integer l : ls) {
    System.out.println();  // Side effect
    System.out.println(""); // Another side effect
}
```
**Output**:
```java
ls.stream().map(_item -> {
    System.out.println();
    return _item;
}).forEachOrdered(_item -> {
    System.out.println("");
});
```
**Operations**: MAP (with side effect) → FOREACH

### Pattern 10: Max Reduction
**Input**:
```java
int max = Integer.MIN_VALUE;
for (Integer num : numbers) {
    max = Math.max(max, num);
}
```
**Output**:
```java
int max = Integer.MIN_VALUE;
max = numbers.stream().map(num -> num).reduce(max, Math::max);
```
**Operations**: MAP → REDUCE (with Math::max)

### Pattern 11: Min Reduction
**Input**:
```java
int min = Integer.MAX_VALUE;
for (Integer num : numbers) {
    min = Math.min(min, num);
}
```
**Output**:
```java
int min = Integer.MAX_VALUE;
min = numbers.stream().map(num -> num).reduce(min, Math::min);
```
**Operations**: MAP → REDUCE (with Math::min)

### Pattern 12: Max with Expression
**Input**:
```java
int maxLen = 0;
for (String str : strings) {
    maxLen = Math.max(maxLen, str.length());
}
```
**Output**:
```java
int maxLen = 0;
maxLen = strings.stream().map(str -> str.length()).reduce(maxLen, Math::max);
```
**Operations**: MAP (expression) → REDUCE (with Math::max)

### Pattern 13: Filtered Max Reduction
**Input**:
```java
int max = 0;
for (Integer num : numbers) {
    if (num % 2 == 0) {
        max = Math.max(max, num);
    }
}
```
**Output**:
```java
int max = 0;
max = numbers.stream().filter(num -> (num % 2 == 0)).map(num -> num).reduce(max, Math::max);
```
**Operations**: FILTER → MAP → REDUCE (with Math::max)

## Variable Dependency Tracking

The builder tracks variable names through the pipeline to ensure correct lambda parameters:

```java
for (Integer a : ls) {
    Integer l = new Integer(a.intValue());  // MAP: a -> l
    if (l != null) {                         // FILTER uses l
        String s = l.toString();             // MAP: l -> s
        System.out.println(s);               // FOREACH uses s
    }
}
```

Becomes:
```java
ls.stream()
  .map(a -> new Integer(a.intValue()))     // produces 'l'
  .filter(l -> (l!=null))                   // uses 'l'
  .map(l -> l.toString())                   // uses 'l', produces 's'
  .forEachOrdered(s -> {                    // uses 's'
      System.out.println(s);
  });
```

## Type-Aware Accumulator Handling

The builder detects accumulator variable types and generates appropriate literals:

```java
double len = 0.0;
for (int i : ints)
    len++;
```

Becomes:
```java
len = ints.stream().map(_item -> 1.0).reduce(len, (accumulator, _item) -> accumulator + _item);
```

Type mappings:
- `int` → `1`
- `long` → `1L`
- `float` → `1.0f`
- `double` → `1.0`

## Code Quality and Maintainability (December 2025)

### Recent Code Cleanup
As part of the December 2025 improvements, significant dead code was removed:
- **TreeUtilities.java**: Completely removed (unused utility class with no callers)
- **Refactorer.java**: Legacy implementation removed, reducing file from 417 lines to 93 lines (78% reduction)
  - Removed legacy methods: `isOneStatementBlock()`, `isReturningIf()`, `getListRepresentation()`, `isIfWithContinue()`, `refactorContinuingIf()`, `createReduceLambdaExpression()`, `createMapLambdaExpression()`, `createForEachLambdaExpression()`, and legacy `parseLoopBody()`
  - All functionality now consolidated in `StreamPipelineBuilder` class
  - Legacy implementation was never executed since `useStreamPipelineBuilder()` returns true by default

### Current Implementation Status
- **Primary implementation**: `StreamPipelineBuilder` (849 lines) - comprehensive, well-tested
- **Fallback**: Legacy implementation removed - no longer needed
- **Helper classes**: All necessary classes (`AbstractFunctionalCall`, `LoopToFunctional`, `PreconditionsChecker`, `ProspectiveOperation`) are in active use

## Limitations and Future Work

### Current Limitations
1. **No operation merging**: Consecutive filters/maps are not merged (e.g., `.filter(x -> x > 0).filter(x -> x < 100)` not merged to single filter)
2. **No collect() support**: Only forEach, reduce, anyMatch, and noneMatch terminals supported
3. **No parallel streams**: Always generates sequential streams
4. **Limited method references**: More opportunities exist for method references vs lambdas
5. **No labeled break/continue**: Loops with labeled break or continue are not converted
6. **No exception throwing**: Loops that throw exceptions are not converted

### Future Enhancements
1. **Operation optimization**: Merge consecutive filters with && logic, merge consecutive maps
2. **Extended terminals**: Support collect(), findFirst(), count(), sum(), etc.
3. **Method reference detection**: Auto-convert lambdas to method references where possible (e.g., `x -> x.toString()` to `Object::toString`)
4. **Parallel stream option**: User preference for parallel vs sequential
5. **Complex reducers**: Support for more complex accumulation patterns beyond current set
6. **Exception handling**: Support loops with controlled exception throwing patterns

## Testing

### Test Organization Strategy (January 2026)

**New Approach**: Tests are organized by **transformation pattern** rather than implementation phase. This provides better discoverability, easier maintenance, and clear documentation.

See [TEST_STRATEGY.md](../sandbox_functional_converter_test/TEST_STRATEGY.md) for comprehensive testing guidelines.

### Pattern-Based Test Structure

| Test Class | Purpose | Pattern Examples |
|------------|---------|------------------|
| `IteratorLoopToStreamTest` | Iterator-specific patterns | `while(it.hasNext())`, `for(Iterator it=...)`, collect/map/filter on iterators |
| `LoopRefactoringCollectTest` | Collection accumulation patterns | Identity collect, mapped collect, filtered collect, filter+map chains |
| `LoopRefactoringEdgeCasesTest` | Edge cases and boundaries | Empty collections, null handling, complex generics, variable shadowing |
| `FunctionalLoopNegativeTest` | Patterns that should NOT convert | break/throw/continue, external modifications, unsafe transformations |
| `FunctionalLoopSimpleConversionTest` | Basic forEach patterns | Simple iteration, method references, single statements |
| `FunctionalLoopReducerTest` | Reduce/aggregate patterns | Sum, count, min/max, custom reductions |
| `FunctionalLoopMatchPatternTest` | anyMatch/allMatch/noneMatch | Boolean aggregations, early exit patterns |

### Unit Tests (No Plugin Environment Required)

The following tests can run without an Eclipse plugin environment using JDT's `ASTParser`:

| Test Class | Tests |
|------------|-------|
| `ExpressionUtilsTest` | Expression manipulation utilities |
| `LambdaGeneratorTest` | Lambda and method reference generation |
| `SideEffectCheckerTest` | Side-effect safety validation |
| `ProspectiveOperationTest` | Stream operation representation |
| `StreamConstantsTest` | Constant values and delegation |
| `ParseResultTest` | Loop body parsing results |
| `StatementParsingContextTest` | Handler chain context object |

### Integration Tests (Plugin Environment Required)

All 26 test cases from `UseFunctionalLoop` enum are enabled:
- Simple conversions (SIMPLECONVERT, CHAININGMAP)
- Filter chains (ChainingFilterMapForEachConvert, NonFilteringIfChaining)
- Complex chains (SmoothLongerChaining, MergingOperations)
- Reducers (SimpleReducer, ChainedReducer, IncrementReducer, DecrementingReducer)
- Max/Min reducers (MaxReducer, MinReducer, MaxWithExpression, MinWithExpression)
- Complex reducers (FilteredMaxReduction, ChainedMapWithMinReduction)
- Match operations (ChainedAnyMatch, ChainedNoneMatch)
- Side effects (NoNeededVariablesMerging, SomeChainingWithNoNeededVar)
- String operations (StringConcat)

### Iterator Loop Tests (Disabled - Pending ITERATOR_LOOP Activation)

New comprehensive test suite for iterator loops (`IteratorLoopToStreamTest`):
- forEach patterns: `while(it.hasNext())` → `collection.forEach()`
- Collect patterns: Iterator loop → `stream().collect(Collectors.toList())`
- Map patterns: Iterator with transformation → `stream().map().collect()`
- Filter patterns: Conditional iteration → `stream().filter().collect()`
- Reduce patterns: Sum/count/aggregate → `stream().mapToInt().sum()`
- Negative tests: `Iterator.remove()`, multiple `next()`, break statements

**Status**: Tests created but disabled with `@Disabled` annotation. Will be activated once ITERATOR_LOOP support is implemented in `UseFunctionalCallFixCore`.

### Test Execution
```bash
# Build project
mvn clean install -DskipTests

# Run functional loop converter tests
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test \
  -Dtest=Java8CleanUpTest#testSimpleForEachConversion

# Run specific pattern test class
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test \
  -Dtest=LoopRefactoringCollectTest

# Run iterator tests (once activated)
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test \
  -Dtest=IteratorLoopToStreamTest
```

### Test Quality Standards

All new tests follow modern Java best practices:
- **Method References**: Prefer `String::toUpperCase` over `s -> s.toUpperCase()`
- **Standard Collectors**: Use `Collectors.toList()`, `Collectors.toSet()`
- **Optimal Ordering**: Filter before map for better performance
- **Null Handling**: Use `Objects::nonNull` for null checks
- **Direct forEach**: Use `collection.forEach()` not `collection.stream().forEach()`
- **Specialized Streams**: Use `IntStream` for `int[]` to avoid boxing

## Integration with Eclipse JDT

### Current State
The implementation is in the sandbox for experimentation. Package structure mirrors Eclipse JDT:
- `org.sandbox.jdt.internal.corext.fix.helper.*` → `org.eclipse.jdt.internal.corext.fix.helper.*`

### Contribution Path
To contribute to Eclipse JDT:
1. Replace `sandbox` with `eclipse` in all package names
2. Move classes to corresponding Eclipse modules:
   - `StreamPipelineBuilder.java` → `org.eclipse.jdt.core.manipulation`
   - Tests → `org.eclipse.jdt.ui.tests`
3. Update cleanup registration in plugin.xml
4. Submit to Eclipse Gerrit for review

## References

- **NetBeans Implementation**: https://github.com/apache/netbeans/tree/master/java/java.hints/src/org/netbeans/modules/java/hints/jdk/mapreduce
- **Eclipse JDT AST**: https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/package-summary.html
- **Java 8 Streams**: https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html

## Documentation Requirements

### Feature Properties

The corresponding feature module `sandbox_functional_converter_feature` MUST maintain:

1. **feature.properties** - English language properties file containing:
   - `description` - Clear description of the feature's purpose and capabilities
   - `copyright` - Copyright notice with appropriate years
   - `licenseURL` - URL to the Eclipse Public License
   - `license` - Eclipse Public License text or reference

2. **feature_de.properties** - German translation of all properties

These files enable Eclipse's built-in localization mechanism and provide user-facing documentation in the Eclipse IDE. When updating feature capabilities, ensure both property files are updated accordingly.
## Phase 9: Target Format Selection (January 2026)

### Overview
Phase 9 introduces the ability for users to select the target loop format in the cleanup preferences dialog. Previously, the cleanup always converted loops to Stream API. Now users can choose between:
- **Stream** (default): Java 8+ functional style (forEach, map, filter, reduce)
- **For-loop**: Enhanced for-loop (for-each)
- **While-loop**: Iterator-based while loop

### Architecture

#### Components

1. **LoopTargetFormat Enum**
   - Location: `org.sandbox.jdt.internal.corext.fix.helper.LoopTargetFormat`
   - Purpose: Defines available target formats
   - Values: STREAM, FOR_LOOP, WHILE_LOOP
   - Methods:
     - `getId()`: Returns string identifier ("stream", "for", "while")
     - `fromId(String)`: Parses string to enum (with default fallback)

2. **UI Integration**
   - Component: `SandboxCodeTabPage`
   - UI Element: Combo box with format selection
   - Location: Eclipse cleanup preferences → Sandbox → Java 8
   - Behavior: Enabled when "Use functional call" checkbox is checked

3. **Cleanup Logic Integration**
   - Component: `UseFunctionalCallCleanUpCore`
   - Method: `createFix(CleanUpContext)`
   - Reads preference: `MYCleanUpConstants.USEFUNCTIONALLOOP_TARGET_FORMAT`
   - Current behavior: Only processes STREAM format, skips others (returns null)

### Data Flow

```
User selects format in UI
    ↓
Preference saved to cleanup profile
    ↓
UseFunctionalCallCleanUpCore.createFix() reads preference
    ↓
LoopTargetFormat.fromId() parses string to enum
    ↓
If STREAM: Process transformation (existing behavior)
If FOR_LOOP or WHILE_LOOP: Skip (returns null) - not yet implemented
```

### Current Implementation Status

**Completed** ✅:
- Data model (LoopTargetFormat enum)
- UI integration (combo box in preferences)
- Preference persistence (initializers, constants)
- Cleanup logic reads format preference
- Test infrastructure (LoopTargetFormatTest)
- Documentation (README, TODO, ARCHITECTURE)

**Pending** ⏳:
- FOR_LOOP format transformation logic
- WHILE_LOOP format transformation logic
- Reverse transformations (Stream → for, Stream → while)
- Multiple quickfix proposal support

### Future Enhancement: Format Transformers

**Planned Design** (not yet implemented):

```java
public interface IFormatTransformer {
    Statement transform(Statement statement, CompilationUnitRewrite cuRewrite);
    boolean canTransform(Statement statement);
    LoopTargetFormat getTargetFormat();
}

// Implementations:
- StreamFormatTransformer (extracts existing stream generation logic)
- ForLoopFormatTransformer (converts to enhanced for-loop)
- WhileLoopFormatTransformer (converts to iterator while-loop)
```

### Testing

**Test Class**: `LoopTargetFormatTest`

**Test Coverage**:
1. STREAM format performs transformation (existing behavior)
2. FOR_LOOP format skips transformation (not implemented)
3. WHILE_LOOP format skips transformation (not implemented)
4. LoopTargetFormat enum parsing (fromId, getId)

All tests verify that:
- Format preference is correctly read
- STREAM format maintains existing behavior
- Unimplemented formats fail gracefully (return null instead of error)

### Integration with Existing Features

**Relationship to Phase 7 (Iterator Loop Support)**:
- Phase 7 added `IteratorLoopToFunctional` which converts iterator-based while loops **TO** streams
- Phase 9 adds the infrastructure to convert **FROM** streams to other formats
- Together they enable bidirectional transformations

**Relationship to V2 (ULR)**:
- Format transformers could leverage ULR's abstract loop model
- ULR → Format renderer pattern would enable easier format generation
- Current implementation is V1-based (direct AST manipulation)

### API Constants

```java
// In MYCleanUpConstants.java
USEFUNCTIONALLOOP_CLEANUP = "cleanup.functionalloop"
USEFUNCTIONALLOOP_TARGET_FORMAT = "cleanup.functionalloop.target_format"

// Default values
DefaultCleanUpOptionsInitializer: "stream"
SaveActionCleanUpOptionsInitializer: "stream"
```

### User Experience

**Before Phase 9**:
- Single option: "Use functional call" (checkbox)
- Always converts to Stream API

**After Phase 9**:
- Two options:
  1. "Use functional call" (checkbox)
  2. "Target format" (combo box: Stream / Classic for-loop / While-loop)
- Currently only Stream format performs conversions
- FOR_LOOP and WHILE_LOOP will be implemented in future updates

### References

- **TODO.md**: Phase 9 implementation roadmap
- **README.md**: User-facing documentation
- **LoopBidirectionalTransformationTest**: Disabled tests showing desired behavior for reverse transformations
- **LoopTargetFormatTest**: Tests for format selection infrastructure


## Phase 9: Bidirectional Loop Transformations (January 2026)

### Overview
Phase 9 extends the loop conversion system to support bidirectional transformations with a flexible GUI. Users can now:
- Select a **target format** (Stream, Enhanced-for, or Iterator-while)
- Enable/disable **source formats** individually (which loop types to convert)
- Transform loops in multiple directions, not just to Stream API

### Architecture Changes

#### New Constants (MYCleanUpConstants)

**Master Control**:
- `LOOP_CONVERSION_ENABLED` - Master switch for bidirectional conversions
- `LOOP_CONVERSION_TARGET_FORMAT` - Selected target format ("stream", "enhanced_for", "iterator_while")

**Source Format Filters**:
- `LOOP_CONVERSION_FROM_ENHANCED_FOR` - Convert FROM enhanced for-loops
- `LOOP_CONVERSION_FROM_ITERATOR_WHILE` - Convert FROM iterator while-loops
- `LOOP_CONVERSION_FROM_STREAM` - Convert FROM stream expressions
- `LOOP_CONVERSION_FROM_CLASSIC_FOR` - Convert FROM classic for-loops (experimental)

#### New Transformer Classes

All extend `AbstractFunctionalCall<ASTNode>` and implement:
- `find()` - Scan AST for transformable patterns
- `rewrite()` - Perform the transformation
- `getPreview()` - Generate before/after preview

**Transformer Implementations** (Currently stubs):

1. **StreamToEnhancedFor**
   - Pattern: `collection.forEach(x -> statement)` or `collection.stream().forEach(x -> statement)`
   - Output: `for (T x : collection) { statement; }`
   
2. **StreamToIteratorWhile**
   - Pattern: `collection.forEach(x -> statement)`
   - Output: `Iterator<T> it = c.iterator(); while (it.hasNext()) { T x = it.next(); statement; }`
   
3. **IteratorWhileToEnhancedFor**
   - Pattern: `Iterator<T> it = c.iterator(); while (it.hasNext()) { T x = it.next(); ... }`
   - Output: `for (T x : collection) { ... }`
   - Challenge: Recovering collection reference from iterator
   
4. **EnhancedForToIteratorWhile**
   - Pattern: `for (T x : collection) { ... }`
   - Output: `Iterator<T> it = c.iterator(); while (it.hasNext()) { T x = it.next(); ... }`

#### New Enum Values (UseFunctionalCallFixCore)

Added to support bidirectional transformations:
- `STREAM_TO_FOR` - Uses StreamToEnhancedFor
- `STREAM_TO_ITERATOR` - Uses StreamToIteratorWhile
- `ITERATOR_TO_FOR` - Uses IteratorWhileToEnhancedFor
- `FOR_TO_ITERATOR` - Uses EnhancedForToIteratorWhile

Existing enum values reused:
- `LOOP` - Enhanced-for → Stream (V1 implementation)
- `ITERATOR_LOOP` - Iterator-while → Stream

### UI Design

#### Cleanup Preferences Layout

```
┌─ Loop Conversion ─────────────────────────────────────────┐
│                                                            │
│ ☑ Enable bidirectional loop conversions                   │
│                                                            │
│   Target format: [Stream ▼]                               │
│                                                            │
│   Convert from:                                            │
│   ☑ Enhanced for-loops (for-each)                         │
│   ☑ Iterator while-loops                                  │
│   ☐ Stream expressions (forEach, etc.)                    │
│   ☐ Classic index-based for-loops (experimental)          │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

**UI Components** (SandboxCodeTabPage):
1. Master checkbox: Controls entire "Loop Conversion" group
2. Target format combo: Three options (Stream, Enhanced for-loop, Iterator while-loop)
3. Source format checkboxes: Four individual toggles
4. Dependencies: All child controls disabled when master checkbox unchecked

### Transformation Logic

#### computeFixSet() Algorithm

Located in `UseFunctionalCallCleanUpCore.java`:

```java
private EnumSet<UseFunctionalCallFixCore> computeFixSet() {
    EnumSet<UseFunctionalCallFixCore> fixSet = EnumSet.noneOf(...);
    
    // Legacy mode (backward compatibility)
    if (isEnabled(USEFUNCTIONALLOOP_CLEANUP)) {
        fixSet.add(LOOP);
        fixSet.add(ITERATOR_LOOP);
    }
    
    // Bidirectional mode
    if (isEnabled(LOOP_CONVERSION_ENABLED)) {
        String targetFormat = getOptions().get(LOOP_CONVERSION_TARGET_FORMAT);
        
        // Enhanced-for as source
        if (isEnabled(LOOP_CONVERSION_FROM_ENHANCED_FOR)) {
            if ("stream".equals(targetFormat)) {
                fixSet.add(LOOP); // Existing transformer
            } else if ("iterator_while".equals(targetFormat)) {
                fixSet.add(FOR_TO_ITERATOR); // New transformer
            }
        }
        
        // Iterator-while as source
        if (isEnabled(LOOP_CONVERSION_FROM_ITERATOR_WHILE)) {
            if ("stream".equals(targetFormat)) {
                fixSet.add(ITERATOR_LOOP); // Existing transformer
            } else if ("enhanced_for".equals(targetFormat)) {
                fixSet.add(ITERATOR_TO_FOR); // New transformer
            }
        }
        
        // Stream as source
        if (isEnabled(LOOP_CONVERSION_FROM_STREAM)) {
            if ("enhanced_for".equals(targetFormat)) {
                fixSet.add(STREAM_TO_FOR); // New transformer
            } else if ("iterator_while".equals(targetFormat)) {
                fixSet.add(STREAM_TO_ITERATOR); // New transformer
            }
        }
    }
    
    return fixSet;
}
```

#### Transformation Matrix

| Source Format | Target: Stream | Target: Enhanced-for | Target: Iterator-while |
|--------------|---------------|---------------------|----------------------|
| Enhanced-for | LOOP (existing) | (no-op) | FOR_TO_ITERATOR (new) |
| Iterator-while | ITERATOR_LOOP (existing) | ITERATOR_TO_FOR (new) | (no-op) |
| Stream | (no-op) | STREAM_TO_FOR (new) | STREAM_TO_ITERATOR (new) |

### Implementation Status

#### Completed (January 31, 2026)
- ✅ All constants defined
- ✅ UI components created and wired
- ✅ Default options configured
- ✅ computeFixSet() logic implemented
- ✅ Enum values added
- ✅ Stub transformer classes created
- ✅ Messages internationalized (English)

#### Pending
- ⏳ Transformer find() implementation
- ⏳ Transformer rewrite() implementation
- ⏳ Test enablement in LoopBidirectionalTransformationTest
- ⏳ German translations (feature_de.properties)
- ⏳ Stream.toList() immutability handling

### Design Decisions

#### Why Global Target + Source Filters?

**Alternative Considered**: Individual toggle for each source-target pair (9 checkboxes)
- **Rejected**: Too complex, overwhelming UI, hard to understand

**Chosen Design**: Global target + source filters
- **Advantages**: 
  - Clear mental model: "Convert these things TO this format"
  - Fewer UI elements (1 combo + 4 checkboxes vs 9 checkboxes)
  - Easier to understand for users
  - Scales well if more formats added

#### Backward Compatibility

**Legacy Mode**: `USEFUNCTIONALLOOP_CLEANUP` still works
- Enables LOOP and ITERATOR_LOOP transformers
- Uses old radio button UI (USEFUNCTIONALLOOP_FORMAT_*)
- Can coexist with new mode (both can be enabled)

**Migration Path**:
- Users can continue using old cleanup
- New cleanup offers more flexibility
- Eventually, old cleanup can be deprecated

#### Default Settings

- `LOOP_CONVERSION_ENABLED` = FALSE (opt-in)
- `LOOP_CONVERSION_FROM_ENHANCED_FOR` = TRUE (most common forward transformation)
- `LOOP_CONVERSION_FROM_ITERATOR_WHILE` = TRUE (modernization)
- `LOOP_CONVERSION_FROM_STREAM` = FALSE (inverse transformation, less common)
- Target format = "stream" (modern Java style)

### Known Limitations

1. **Stub Implementations**: All 4 new transformers are empty stubs
   - They compile but do nothing
   - Tests cannot be enabled until implemented

2. **Iterator → Enhanced-for Challenge**: 
   - Pattern: `Iterator<T> it = collection.iterator(); while (...)`
   - Problem: Need to recover `collection` reference from iterator
   - Solution: Track variable through data flow analysis

3. **Stream.toList() Immutability**:
   - `stream.toList()` creates immutable list (Java 16+)
   - If user later modifies list (.sort(), .add()), code breaks
   - Solution needed: Usage analysis or fallback to Collectors.toList()

4. **Complex Lambda Bodies**:
   - Multi-statement lambdas harder to convert back to loops
   - May need to create block statements
   - Side effect analysis required

### Testing Strategy

#### Test Files
- `LoopBidirectionalTransformationTest.java` - 3 disabled tests
  - `testStreamToFor_forEach()` - Stream → for
  - `testForToWhile_iterator()` - for → while
  - `testWhileToFor_iterator()` - while → for

#### Test Enablement Plan
1. Implement StreamToEnhancedFor
2. Enable testStreamToFor_forEach
3. Fix any issues
4. Repeat for other transformers

### Future Enhancements

1. **Classic For-Loop Support**: Index-based for loops
   - Pattern: `for (int i = 0; i < n; i++) { ... }`
   - Complex due to index variable usage analysis
   
2. **Multiple Quickfix Proposals**: 
   - Offer all valid transformations as quickfix options
   - User chooses at application time

3. **Batch Transformation**:
   - Transform all loops in file/project
   - Statistical summary of transformations

4. **Format-Aware Optimization**:
   - Different optimization strategies per format
   - Example: Stream might inline lambdas, for-loop might not

### References

- **Issues**: #453, #549
- **Test File**: `sandbox_functional_converter_test/.../LoopBidirectionalTransformationTest.java`
- **Design Spec**: `TODO.md` Phase 9 section

