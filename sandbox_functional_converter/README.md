# Functional Loop Converter - Eclipse Cleanup Plugin

## Overview
This Eclipse cleanup plugin automatically converts imperative enhanced for-loops into functional Java 8 Stream pipelines. It helps modernize Java code by transforming traditional loop patterns into more concise and expressive stream operations.

## Features

### Supported Conversions
- **Simple forEach**: `for (Item i : list) System.out.println(i);` → `list.forEach(i -> System.out.println(i));`
- **Filter operations**: IF statements → `.filter()`
- **Map operations**: Variable declarations → `.map()`
- **Reduce operations**: Accumulators → `.reduce()`
  - Increment/decrement: `i++`, `i--`
  - Sum/product: `sum += x`, `product *= x`
  - **Math.max/Math.min**: `max = Math.max(max, x)` → `.reduce(max, Math::max)`
- **Match operations**: Early returns → `.anyMatch()`, `.noneMatch()`
- **Continue statements**: `if (condition) continue;` → `.filter(x -> !(condition))`

### Recent Improvements (December 2025)
✅ **Code Cleanup**: Removed ~366 lines of dead code (78% reduction in Refactorer.java)
✅ **Math.max/Math.min Support**: Full support for MAX/MIN reduction with method references
✅ **Enhanced Tests**: 29 comprehensive test cases covering all patterns
✅ **Better Documentation**: Complete architecture and implementation docs

## Examples

### Basic forEach
**Before:**
```java
for (Integer l : ls)
    System.out.println(l);
```

**After:**
```java
ls.forEach(l -> System.out.println(l));
```

### Filter + Map + ForEach
**Before:**
```java
for (Integer l : ls) {
    if (l != null) {
        String s = l.toString();
        System.out.println(s);
    }
}
```

**After:**
```java
ls.stream()
  .filter(l -> (l!=null))
  .map(l -> l.toString())
  .forEachOrdered(s -> {
      System.out.println(s);
  });
```

### Math.max Reduction
**Before:**
```java
int max = Integer.MIN_VALUE;
for (Integer num : numbers) {
    max = Math.max(max, num);
}
```

**After:**
```java
int max = Integer.MIN_VALUE;
max = numbers.stream().reduce(max, Math::max);
```

### Complex: Filter + Map + Math.max
**Before:**
```java
int max = 0;
for (Integer num : numbers) {
    if (num > 0) {
        int squared = num * num;
        max = Math.max(max, squared);
    }
}
```

**After:**
```java
int max = 0;
max = numbers.stream()
           .filter(num -> (num > 0))
           .map(num -> num * num)
           .reduce(max, Math::max);
```

## Architecture

The plugin uses a builder pattern (`StreamPipelineBuilder`) to analyze loop bodies and construct stream pipelines:

1. **Analysis Phase** (`analyze()`): Validates preconditions and parses loop body
2. **Building Phase** (`buildPipeline()`): Constructs chained stream operations
3. **Wrapping Phase** (`wrapPipeline()`): Wraps result in appropriate statement

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed design documentation.

## Current Limitations

- No operation merging (consecutive filters/maps)
- No collect() support (only forEach, reduce, anyMatch, noneMatch)
- No parallel streams
- No labeled break/continue
- No exception throwing in loops

## Testing

All 29 test patterns pass, including:
- Simple conversions
- Filter chains
- Complex chaining
- Reducers (sum, product, increment, Math.max, Math.min)
- Match operations (anyMatch, noneMatch)
- Side effects
- Continue statements

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test
```

## Contributing to Eclipse JDT

This codebase is designed for easy integration into Eclipse JDT. To contribute:

1. Replace `org.sandbox` with `org.eclipse` in all packages
2. Move classes to corresponding Eclipse modules:
   - `StreamPipelineBuilder.java` → `org.eclipse.jdt.core.manipulation`
   - Tests → `org.eclipse.jdt.ui.tests`
3. Update cleanup registration in plugin.xml
4. Submit to Eclipse Gerrit for review

Package structure mirrors Eclipse JDT for seamless integration.

## References

- [NetBeans Implementation](https://github.com/apache/netbeans/tree/master/java/java.hints/src/org/netbeans/modules/java/hints/jdk/mapreduce)
- [Eclipse JDT AST](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/package-summary.html)
- [Java 8 Streams](https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html)

## License

Eclipse Public License 2.0 (EPL-2.0)
