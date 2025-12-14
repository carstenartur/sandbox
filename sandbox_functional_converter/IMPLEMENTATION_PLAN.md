# Implementation Plan for Functional Loop Conversion

## Current Status
- ✅ SIMPLECONVERT test passing (basic forEach)
- ❌ CHAININGMAP test disabled (needs MAP support)
- ❌ 18+ other tests disabled

## NetBeans Code Analysis

### Key Components (from /tmp/netbeans):
1. **Refactorer.java** (276 lines)
   - `getListRepresentation()`: Parses loop body into ProspectiveOperation list
   - `chainAllProspectives()`: Builds stream pipeline
   - `propagateSideEffects()`: Handles return/assignment wrapping

2. **ProspectiveOperation.java** (654 lines)
   - `createOperator()`: Factory for creating operations
   - `getSuitableMethod()`: Returns stream method name (map/filter/forEach/etc)
   - `getArguments()`: Generates lambda expressions
   - `merge()`: Combines consecutive operations
   - `beautify()`: Optimizes lambda bodies

3. **PreconditionsChecker.java** (428 lines)
   - Variable tracking
   - Safety checks

## Implementation Strategy

### Phase 1: MAP Support for CHAININGMAP (Next Steps)

**Test Case**:
```java
for (Integer l : ls) {
    String s = l.toString();
    System.out.println(s);
}
```

**Expected Output**:
```java
ls.stream().map(l -> l.toString()).forEachOrdered(s -> {
    System.out.println(s);
});
```

**Required Changes**:

1. **Refactorer.getListRepresentation()** - Parse loop body:
   ```java
   - Detect VariableDeclarationStatement → MAP operation
   - Detect other statements → FOREACH operation
   - Return List<ProspectiveOperation>
   ```

2. **ProspectiveOperation factory methods**:
   ```java
   static ProspectiveOperation createMapOperation(VariableDeclarationStatement stmt, ...)
   static ProspectiveOperation createForEachOperation(Statement stmt, ...)
   ```

3. **ProspectiveOperation.getSuitableMethod()**:
   ```java
   switch (operationType) {
       case MAP: return "map";
       case FILTER: return "filter";
       case FOREACH: return "forEachOrdered";
       case REDUCE: return "reduce";
       ...
   }
   ```

4. **ProspectiveOperation.getArguments()**:
   ```java
   - MAP: create lambda `l -> l.toString()`
   - FOREACH: create lambda `s -> { System.out.println(s); }`
   ```

5. **Refactorer.refactor()** - Build pipeline:
   ```java
   List<ProspectiveOperation> ops = getListRepresentation(forLoop.getBody(), true);
   MethodInvocation pipeline = buildStreamPipeline(ops);
   rewrite.replace(forLoop, pipeline, null);
   ```

### Phase 2: FILTER Support (if-continue pattern)

**Test Case**: ContinuingIfFilterSingleStatement
```java
for (Integer l : ls) {
    if (l == null) continue;
    System.out.println(l);
}
```

**Expected**:
```java
ls.stream().filter(l -> l != null).forEachOrdered(l -> {
    System.out.println(l);
});
```

**Changes**:
- Detect `if (cond) continue;` → FILTER with negated condition
- Add filter lambda generation

### Phase 3: REDUCE Support

**Test Case**: SimpleReducer
```java
int sum = 0;
for (Integer l : ls) {
    sum += l;
}
```

**Expected**:
```java
int sum = ls.stream().reduce(0, Integer::sum);
```

**Changes**:
- Detect compound assignments (`+=`, `-=`, etc.)
- Detect increment/decrement (`i++`, `--i`)
- Generate reduce lambda with accumulator

### Phase 4: ANYMATCH/NONEMATCH Support

**Test Case**: ChainedAnyMatch
```java
for (Integer l : ls) {
    if (l > 5) return true;
}
return false;
```

**Expected**:
```java
return ls.stream().anyMatch(l -> l > 5);
```

## Key Patterns from NetBeans

### 1. Operation Creation Pattern
```java
// NetBeans style
ProspectiveOperation.createOperator(stmt, operationType, precond, workingCopy)

// Eclipse equivalent
ProspectiveOperation.createOperation(stmt, operationType, precond, ast, loopVarName)
```

### 2. Lambda Generation Pattern
```java
// For MAP
VariableTree var = getLambdaArguments();
Tree lambdaBody = getLambdaForMap();
lambda = treeMaker.LambdaExpression(Arrays.asList(var), lambdaBody);

// Eclipse equivalent
VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
param.setName(ast.newSimpleName(loopVarName));
Expression body = extractMapBody(varDecl);
LambdaExpression lambda = ast.newLambdaExpression();
lambda.parameters().add(param);
lambda.setBody(body);
```

### 3. Pipeline Building Pattern
```java
// NetBeans
MethodInvocationTree mi = treeMaker.MethodInvocation(..., 
    treeMaker.MemberSelect(expr, "stream"), ...);
for (ProspectiveOperation op : ops) {
    mi = treeMaker.MethodInvocation(...,
        treeMaker.MemberSelect(mi, op.getSuitableMethod()),
        op.getArguments());
}

// Eclipse equivalent
MethodInvocation pipeline = ast.newMethodInvocation();
pipeline.setExpression(copySubtree(ast, forLoop.getExpression()));
pipeline.setName(ast.newSimpleName("stream"));

for (ProspectiveOperation op : ops) {
    MethodInvocation next = ast.newMethodInvocation();
    next.setExpression(pipeline);
    next.setName(ast.newSimpleName(op.getSuitableMethod()));
    next.arguments().addAll(op.getArguments(ast));
    pipeline = next;
}
```

## Testing Strategy

1. Enable one test at a time
2. Run: `xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test -Dtest=Java8CleanUpTest#testSimpleForEachConversion`
3. Fix implementation to make test pass
4. Commit and move to next test

## Estimated Complexity

- Phase 1 (MAP): 3-4 hours - **High Priority**
- Phase 2 (FILTER): 2-3 hours
- Phase 3 (REDUCE): 4-5 hours (complex)
- Phase 4 (ANYMATCH/NONEMATCH): 2-3 hours
- Total: 11-15 hours

## Next Immediate Action

Focus on Phase 1: Implement MAP support to enable CHAININGMAP test.

Start with minimal changes:
1. Add `getSuitableMethod()` to ProspectiveOperation
2. Add `getArguments()` to ProspectiveOperation  
3. Update Refactorer to detect variable declarations
4. Build simple stream pipeline
5. Test with CHAININGMAP
