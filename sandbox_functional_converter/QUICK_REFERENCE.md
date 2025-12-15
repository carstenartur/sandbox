# Functional Loop Converter - Quick Reference Guide

## For Developers

This guide provides quick reference information for working with the functional loop converter.

## Quick Start

### Running Tests
```bash
# Set Java 21
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64

# Build project
mvn clean install -DskipTests

# Run all functional loop tests
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test \
  -Dtest=Java8CleanUpTest#testSimpleForEachConversion

# Run specific test
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test \
  -Dtest=Java8CleanUpTest#testSimpleForEachConversion \
  -DforkCount=1 -DreuseForks=false
```

### Code Quality
```bash
# Run with coverage and quality checks
mvn -Pjacoco verify

# SpotBugs, CodeQL, and JaCoCo will run automatically
```

## StreamPipelineBuilder API

### Basic Usage
```java
// 1. Create builder
StreamPipelineBuilder builder = new StreamPipelineBuilder(forLoop, preconditions);

// 2. Analyze loop
if (!builder.analyze()) {
    return; // Cannot convert
}

// 3. Build pipeline
MethodInvocation pipeline = builder.buildPipeline();

// 4. Wrap in statement
Statement replacement = builder.wrapPipeline(pipeline);

// 5. Replace in AST
rewrite.replace(forLoop, replacement, null);
```

### Method Reference

| Method | Purpose | Returns |
|--------|---------|---------|
| `analyze()` | Validates and analyzes loop | `boolean` - true if convertible |
| `buildPipeline()` | Constructs stream pipeline | `MethodInvocation` - the pipeline |
| `wrapPipeline(pipeline)` | Wraps in statement | `Statement` - the replacement |

## Conversion Pattern Reference

### Pattern Detection Logic

The `parseLoopBody()` method processes statements in order:

1. **Variable Declaration** → MAP operation
   ```java
   String s = l.toString();  →  .map(l -> l.toString())
   ```

2. **IF Statement (not last)**:
   - **Continue pattern** → negated FILTER
     ```java
     if (x) continue;  →  .filter(l -> !(x))
     ```
   - **Early return true** → ANYMATCH
     ```java
     if (x) return true;  →  .anyMatch(l -> x)
     ```
   - **Early return false** → NONEMATCH
     ```java
     if (x) return false;  →  .noneMatch(l -> x)
     ```
   - **Regular filter** → FILTER + nested operations
     ```java
     if (x) { ... }  →  .filter(l -> x).<nested ops>
     ```

3. **Side-Effect Statement (not last)** → MAP with return
   ```java
   foo(l);  →  .map(l -> { foo(l); return l; })
   ```

4. **Last Statement**:
   - **REDUCE operation** → REDUCE
     ```java
     i++;  →  .map(_item -> 1).reduce(i, Integer::sum)
     i += x;  →  .map(l -> x).reduce(i, Integer::sum)
     ```
   - **Regular statement** → FOREACH
     ```java
     foo(l);  →  .forEachOrdered(l -> foo(l))
     ```

### Operation Type Reference

| Operation | Stream Method | Lambda Type | Example |
|-----------|--------------|-------------|---------|
| MAP | `map()` | `Function<T, R>` | `l -> l.toString()` |
| FILTER | `filter()` | `Predicate<T>` | `l -> (l != null)` |
| FOREACH | `forEach()` or `forEachOrdered()` | `Consumer<T>` | `l -> System.out.println(l)` |
| REDUCE | `reduce()` | `BinaryOperator<T>` | `Integer::sum` |
| ANYMATCH | `anyMatch()` | `Predicate<T>` | `l -> (l == null)` |
| NONEMATCH | `noneMatch()` | `Predicate<T>` | `l -> (l == null)` |

## Type-Aware Literal Mapping

For increment/decrement operations, the builder generates type-appropriate literals:

| Variable Type | Literal Generated | Example |
|--------------|-------------------|---------|
| `int` | `1` | `.map(_item -> 1)` |
| `long` | `1L` | `.map(_item -> 1L)` |
| `float` | `1.0f` | `.map(_item -> 1.0f)` |
| `double` | `1.0` | `.map(_item -> 1.0)` |

Implemented in `addMapBeforeReduce()` method.

## Variable Tracking

Variables flow through the pipeline and are renamed at MAP operations:

```java
for (Integer a : ls) {                    // Variable: a
    Integer l = new Integer(a);           // MAP: a → l
    if (l != null) {                      // FILTER: uses l
        String s = l.toString();          // MAP: l → s
        System.out.println(s);            // FOREACH: uses s
    }
}

// Becomes:
ls.stream()
  .map(a -> new Integer(a))               // produces l
  .filter(l -> (l!=null))                 // uses l
  .map(l -> l.toString())                 // uses l, produces s
  .forEachOrdered(s -> {                  // uses s
      System.out.println(s);
  });
```

Implemented by `getVariableNameFromPreviousOp()`.

## Common Issues and Solutions

### Issue: Test requires Xvfb
**Solution**: Always use `xvfb-run --auto-servernum` for Eclipse plugin tests on Linux.

### Issue: Java version mismatch
**Solution**: Ensure Java 21 is set:
```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

### Issue: Build fails with class version error
**Solution**: Check Java version with `java -version` and verify it's 21.

### Issue: Test not found
**Solution**: Build the entire project first with `mvn clean install -DskipTests`.

## Test Pattern Examples

### SIMPLECONVERT
Basic forEach without any transformations.

### CHAININGMAP
Single MAP operation followed by forEach.

### ChainingFilterMapForEachConvert
FILTER → MAP → FOREACH chain.

### SimpleReducer
Basic increment pattern: `i++`.

### ChainedReducer
FILTER → side-effect MAP → REDUCE.

### ChainedAnyMatch
MAP → MAP → ANYMATCH (early return true).

### NoNeededVariablesMerging
Multiple side-effect statements without variable dependencies.

## File Locations

### Source
- **StreamPipelineBuilder**: `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/StreamPipelineBuilder.java`
- **ProspectiveOperation**: `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/ProspectiveOperation.java`
- **Refactorer**: `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/Refactorer.java`
- **PreconditionsChecker**: `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/PreconditionsChecker.java`

### Tests
- **Java8CleanUpTest**: `sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/Java8CleanUpTest.java`

### Documentation
- **TODO.md**: Implementation roadmap and status
- **ARCHITECTURE.md**: Technical architecture documentation
- **IMPLEMENTATION_SUMMARY.md**: High-level implementation summary
- **QUICK_REFERENCE.md**: This file

## Adding New Test Cases

1. Add enum value to `UseFunctionalLoop`:
```java
NEW_PATTERN(
    "input code...",
    "expected output..."
)
```

2. Add to enabled tests in `@EnumSource`:
```java
@EnumSource(value = UseFunctionalLoop.class, names = {
    ...,
    "NEW_PATTERN"
})
```

3. Run test:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test \
  -Dtest=Java8CleanUpTest#testSimpleForEachConversion
```

4. Fix implementation if test fails.

## Debugging Tips

### Enable System Property
Toggle between builder and legacy:
```bash
mvn test -Duse.stream.pipeline.builder=false ...
```

### View AST
Add debug logging in `parseLoopBody()`:
```java
System.out.println("Statement: " + stmt.getClass().getSimpleName());
System.out.println("AST: " + stmt.toString());
```

### Check Operation Sequence
Add logging in `buildPipeline()`:
```java
for (ProspectiveOperation op : operations) {
    System.out.println("Op: " + op.getOperationType() + " -> " + op.getSuitableMethod());
}
```

## Contributing to Eclipse JDT

### Package Renaming
Replace `org.sandbox` with `org.eclipse` in all files:
```bash
find . -name "*.java" -exec sed -i 's/org\.sandbox/org.eclipse/g' {} \;
```

### Target Modules
- `StreamPipelineBuilder` → `org.eclipse.jdt.core.manipulation`
- Tests → `org.eclipse.jdt.ui.tests`
- Cleanup registration → `org.eclipse.jdt.ui/plugin.xml`

### Submission
Use Eclipse Gerrit: https://git.eclipse.org/r/

## References

- **Eclipse JDT Documentation**: https://help.eclipse.org/latest/
- **Stream API**: https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html
- **NetBeans Source**: https://github.com/apache/netbeans/tree/master/java/java.hints/src/org/netbeans/modules/java/hints/jdk/mapreduce

## Support

For questions or issues:
1. Check TODO.md for current status
2. Review ARCHITECTURE.md for technical details
3. Examine test cases for examples
4. Consult NetBeans implementation for reference
