# Functional Loop Converter - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#functional_converter) | [TODO](TODO.md)

## Overview
The functional loop converter transforms imperative enhanced for-loops into functional Java 8 Stream pipelines. This document describes the architecture and implementation details of the `StreamPipelineBuilder` approach.

## Class Overview

The functional converter is organized into focused, single-responsibility classes:

### Core Pipeline Classes
| Class | Lines | Responsibility |
|-------|-------|----------------|
| `StreamPipelineBuilder` | ~640 | Main orchestrator - coordinates analysis and pipeline construction |
| `LoopBodyParser` | ~450 | Parses loop bodies into operations using handler chain |
| `PipelineAssembler` | ~280 | Assembles method invocation chains |
| `ProspectiveOperation` | ~760 | Represents individual stream operations |

### Statement Handlers (Strategy Pattern)
| Class | Lines | Responsibility |
|-------|-------|----------------|
| `StatementHandler` | ~70 | Interface for statement processing strategies |
| `IfStatementHandler` | ~160 | Handles IF statements (filter, match patterns) |
| `VariableDeclarationHandler` | ~140 | Handles variable declarations (MAP operations) |
| `TerminalStatementHandler` | ~90 | Handles last statements (REDUCE, FOREACH) |
| `NonTerminalStatementHandler` | ~80 | Handles non-terminal side-effect statements |
| `SideEffectChecker` | ~120 | Validates safe side-effects in streams |
| `StatementParsingContext` | ~140 | Context object for handler chain |

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

### Strategy Pattern for Statement Handling

The `LoopBodyParser` uses the Strategy Pattern to process different statement types.
This eliminates deep if-else-if chains and makes the code more maintainable:

```
LoopBodyParser
    └── handlers: List<StatementHandler>
            ├── VariableDeclarationHandler  → MAP operations
            ├── IfStatementHandler          → FILTER, match patterns
            ├── NonTerminalStatementHandler → Side-effect MAPs
            └── TerminalStatementHandler    → REDUCE, FOREACH
```

**Benefits:**
- Each handler is focused on one statement type (Single Responsibility)
- Easy to add new statement types without modifying existing code (Open/Closed)
- Improved testability - each handler can be tested in isolation
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

### Test Execution
```bash
# Build project
mvn clean install -DskipTests

# Run functional loop converter tests
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test \
  -Dtest=Java8CleanUpTest#testSimpleForEachConversion
```

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