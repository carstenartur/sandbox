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
✅ **Enhanced Tests**: 34 comprehensive test cases covering all patterns
✅ **Better Documentation**: Complete architecture and implementation docs
✅ **Robustness Improvements** (Option 3):
  - Variable scope validation to prevent variable leaks
  - Labeled continue detection (rejected for safety)
  - Improved side-effect statement validation
  - Better tracking of produced/consumed variables across pipeline stages

## Supported Transformations

The cleanup currently supports the following patterns:

| Pattern                                 | Transformed To                                      |
|----------------------------------------|-----------------------------------------------------|
| Simple enhanced for-loops              | `list.forEach(...)` or `list.stream().forEach(...)` |
| Mapping inside loops                   | `.stream().map(...)`                                |
| Filtering via `if` or `continue`       | `.stream().filter(...)`                             |
| Null safety checks                     | `.filter(l -> l != null).map(...)`                  |
| Reductions (sum/counter)               | `.stream().map(...).reduce(...)`                    |
| MAX/MIN reductions                     | `.reduce(init, Math::max)` or `.reduce(init, Math::min)` |
| `String` concatenation in loops        | `.reduce(..., String::concat)`                      |
| Conditional early `return true`        | `.anyMatch(...)`                                    |
| Conditional early `return false`       | `.noneMatch(...)`                                   |
| Conditional check all valid            | `.allMatch(...)`                                    |
| Method calls inside mapping/filtering  | `map(x -> method(x))`, `filter(...)`                |
| Combined `filter`, `map`, `forEach`    | Chained stream transformations                      |
| Nested conditionals                    | Multiple `.filter(...)` operations                  |
| Increment/decrement reducers           | `.map(_item -> 1).reduce(0, Integer::sum)`          |
| Compound assignment reducers           | `.map(expr).reduce(init, operator)`                 |

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

### Additional Examples

#### Null Safety with Continue
**Before:**
```java
for (Integer l : list) {
    if (l == null) {
        continue;
    }
    String s = l.toString();
    System.out.println(s);
}
```

**After:**
```java
list.stream()
    .filter(l -> !(l == null))
    .map(l -> l.toString())
    .forEachOrdered(s -> {
        System.out.println(s);
    });
```

#### AnyMatch Pattern (Early Return)
**Before:**
```java
for (Integer l : list) {
    String s = l.toString();
    Object o = foo(s);
    if (o == null)
        return true;
}
return false;
```

**After:**
```java
if (list.stream()
        .map(l -> l.toString())
        .map(s -> foo(s))
        .anyMatch(o -> (o == null))) {
    return true;
}
return false;
```

#### AllMatch Pattern (Check All Valid)
**Before:**
```java
for (String item : items) {
    if (!item.startsWith("valid")) {
        return false;
    }
}
return true;
```

**After:**
```java
if (!items.stream().allMatch(item -> item.startsWith("valid"))) {
    return false;
}
return true;
```

#### Nested Conditional Filters
**Before:**
```java
for (String item : items) {
    if (item != null) {
        if (item.length() > 5) {
            System.out.println(item);
        }
    }
}
```

**After:**
```java
items.stream()
    .filter(item -> (item != null))
    .filter(item -> (item.length() > 5))
    .forEachOrdered(item -> {
        System.out.println(item);
    });
```

#### Increment Counter
**Before:**
```java
int count = 0;
for (String s : list) {
    count += 1;
}
```

**After:**
```java
int count = list.stream()
    .map(_item -> 1)
    .reduce(0, Integer::sum);
```

#### Mapped Reduction
**Before:**
```java
int sum = 0;
for (Integer l : list) {
    sum += foo(l);
}
```

**After:**
```java
int sum = list.stream()
    .map(l -> foo(l))
    .reduce(0, Integer::sum);
```

## Not Yet Supported

The following patterns are currently **not supported** and are marked `@Disabled` in the test suite:

| Pattern Description                                 | Reason / Required Feature                          |
|-----------------------------------------------------|-----------------------------------------------------|
| `Map.put(...)` inside loop                          | Needs `Collectors.toMap(...)` support               |
| Early `break` inside loop body                      | Requires stream short-circuit modeling (`findFirst()`) |
| Labeled `continue` or `break` (`label:`)            | Not expressible via Stream API                     |
| Complex `if-else-return` branches                   | Requires flow graph and branching preservation      |
| `throw` inside loop                                 | Non-convertible – not compatible with Stream flow  |
| Multiple accumulators in one loop                   | State mutation not easily transferable              |

These patterns are intentionally **excluded from transformation** to maintain semantic correctness and safety.

## Java Version Compatibility

| API Used                      | Requires Java |
|-------------------------------|---------------|
| `Stream`, `map`, `filter`     | Java 8+       |
| `forEach`, `forEachOrdered`   | Java 8+       |
| `anyMatch`, `noneMatch`, `allMatch` | Java 8+ |
| `reduce`                      | Java 8+       |
| `Collectors.toList()`         | Java 8+       |

This cleanup is designed for **Java 8+** projects and uses only APIs available since Java 8.

## Architecture

The plugin uses a builder pattern (`StreamPipelineBuilder`) to analyze loop bodies and construct stream pipelines:

1. **Analysis Phase** (`analyze()`): Validates preconditions and parses loop body
2. **Building Phase** (`buildPipeline()`): Constructs chained stream operations
3. **Wrapping Phase** (`wrapPipeline()`): Wraps result in appropriate statement

### Robustness Features

The implementation includes several safety mechanisms to prevent incorrect transformations:

#### Variable Scope Validation
- Tracks produced and consumed variables across pipeline stages
- Prevents variable leaks outside lambda scopes
- Validates that variables are available when referenced
- Distinguishes between loop variables, mapped variables, and accumulators

#### Control Flow Safety
- Rejects loops with labeled continues (can't be safely transformed)
- Rejects loops with break statements
- Rejects loops with throw statements  
- Validates early return patterns for anyMatch/noneMatch/allMatch

#### Side Effect Detection
- Validates side-effect statements before including in pipeline
- Rejects loops that assign to external variables (except REDUCE accumulators)
- Allows safe method calls and expressions
- Conservative approach: when in doubt, don't convert

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed design documentation.

## Current Limitations

- No operation merging (consecutive filters/maps remain separate operations)
- No collect() support (only forEach, reduce, anyMatch, noneMatch, allMatch)
- No parallel streams
- No labeled break/continue (rejected for safety)
- No exception throwing in loops
- Loops with assignments to external variables are not converted (except accumulators in reduce operations)

## Testing

All 34 test patterns pass, including:
- Simple conversions
- Filter chains (including multiple continues)
- Complex chaining (with nested filters)
- Reducers (sum, product, increment, Math.max, Math.min)
- Match operations (anyMatch, noneMatch, allMatch)
- Side effects (validated for safety)
- Continue statements (unlabeled only)

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
