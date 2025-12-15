# Functional Loop Converter - Architecture

## Overview
The functional loop converter transforms imperative enhanced for-loops into functional Java 8 Stream pipelines. This document describes the architecture and implementation details of the `StreamPipelineBuilder` approach.

## Core Components

### 1. StreamPipelineBuilder
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder`
**Lines of Code**: 849
**Purpose**: Analyzes loop bodies and constructs stream pipelines

#### Key Methods

##### `analyze()` - Loop Analysis
Validates preconditions and parses the loop body into a sequence of `ProspectiveOperation` objects.
```java
public boolean analyze()
```
- Returns `true` if loop can be converted to streams
- Checks preconditions via `PreconditionsChecker`
- Calls `parseLoopBody()` to extract operations
- Sets internal `convertible` flag

##### `buildPipeline()` - Pipeline Construction  
Builds the complete stream pipeline as a chained `MethodInvocation`.
```java
public MethodInvocation buildPipeline()
```
- Determines if `.stream()` prefix is needed via `requiresStreamPrefix()`
- Chains operations in sequence (filter → map → forEach/reduce)
- Tracks variable names through pipeline via `getVariableNameFromPreviousOp()`
- Returns null if conversion fails

##### `wrapPipeline()` - Statement Wrapping
Wraps the pipeline in an appropriate statement type.
```java
public Statement wrapPipeline(MethodInvocation pipeline)
```
- **REDUCE operations**: Wraps in assignment (`i = stream.reduce(...)`)
- **ANYMATCH operations**: Wraps in IF with return (`if (stream.anyMatch(...)) return true;`)
- **NONEMATCH operations**: Wraps in IF with return (`if (!stream.noneMatch(...)) return false;`)
- **Other operations**: Wraps in ExpressionStatement

##### `parseLoopBody()` - Recursive Analysis
Recursively analyzes loop body statements and extracts operations.
```java
private List<ProspectiveOperation> parseLoopBody(Statement body, String loopVarName)
```
- Handles `Block` statements with multiple statements
- Processes variable declarations → MAP operations
- Processes IF statements → FILTER operations (recursive for nested IFs)
- Processes continue statements → negated FILTER operations
- Processes side-effect statements → MAP with return statement
- Detects REDUCE operations via `detectReduceOperation()`
- Detects early return patterns for ANYMATCH/NONEMATCH

##### `detectReduceOperation()` - Reducer Detection
Identifies reducer patterns in statements.
```java
private ProspectiveOperation detectReduceOperation(Statement stmt)
```
- Detects postfix/prefix increment: `i++`, `++i` → `.map(_item -> 1).reduce(i, Integer::sum)`
- Detects compound assignment: `i += expr` → `.map(expr).reduce(i, Integer::sum)`
- Supports multiple operator types: `+=`, `-=`, `*=`, string concat
- Type-aware: generates appropriate literals (1.0 for double, 1L for long, etc.)
- Extracts map expressions from compound assignments via `extractReduceExpression()`

##### `getVariableNameFromPreviousOp()` - Variable Tracking
Tracks variable names through the pipeline.
```java
private String getVariableNameFromPreviousOp(List<ProspectiveOperation> operations, int currentIndex, String loopVarName)
```
- MAP operations introduce new variable names
- Subsequent operations use the new variable name
- Returns loop variable name if no prior MAP operation

##### `requiresStreamPrefix()` - Stream Decision
Determines if `.stream()` is needed or if direct collection methods suffice.
```java
private boolean requiresStreamPrefix()
```
- Returns `false` for single FOREACH operation → use `.forEach()` directly
- Returns `true` for multiple operations or non-FOREACH terminal → use `.stream()...`

### 2. ProspectiveOperation
**Location**: `org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation`
**Purpose**: Represents a single stream operation in the pipeline

#### Operation Types
```java
enum OperationType {
    MAP,        // Transform: x -> f(x)
    FILTER,     // Predicate: x -> condition
    FOREACH,    // Terminal action: x -> action(x)
    REDUCE,     // Terminal accumulation: (acc, x) -> acc op x
    ANYMATCH,   // Terminal match: x -> condition (returns true if any match)
    NONEMATCH   // Terminal match: x -> condition (returns true if none match)
}
```

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

## Limitations and Future Work

### Current Limitations
1. **No operation merging**: Consecutive filters/maps are not merged
2. **No collect() support**: Only forEach and reduce terminals
3. **No parallel streams**: Always generates sequential streams
4. **Limited method references**: Could use more method references vs lambdas

### Future Enhancements
1. **Operation optimization**: Merge consecutive filters with && logic
2. **Extended terminals**: Support collect(), findFirst(), count(), etc.
3. **Method reference detection**: Auto-convert lambdas to method references where possible
4. **Parallel stream option**: User preference for parallel vs sequential
5. **Complex reducers**: Support for more complex accumulation patterns

## Testing

### Test Coverage
All 21 test cases from `UseFunctionalLoop` enum are enabled:
- Simple conversions (SIMPLECONVERT, CHAININGMAP)
- Filter chains (ChainingFilterMapForEachConvert, NonFilteringIfChaining)
- Complex chains (SmoothLongerChaining, MergingOperations)
- Reducers (SimpleReducer, ChainedReducer, IncrementReducer, etc.)
- Match operations (ChainedAnyMatch, ChainedNoneMatch)
- Side effects (NoNeededVariablesMerging, SomeChainingWithNoNeededVar)

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
