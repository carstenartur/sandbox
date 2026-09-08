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
- **Comment Preservation**: Comments in loop bodies are automatically preserved in the transformed code ✨

### Stream pipelines back to loops

With **Loop conversion → Enhanced for** or **Iterator while** and **From stream**
enabled, sequential `Collection.stream()` pipelines ending in `forEach` or
`forEachOrdered` can be converted back to loops. Supported pipelines include `filter`, `map`, `peek`, primitive mapping operations
(`mapToInt`, `mapToLong`, `mapToDouble`, `mapToObj`) and the boxing/widening
steps `boxed`, `asLongStream` and `asDoubleStream`:

```java
items.stream().filter(item -> !item.isEmpty())
    .map(item -> item.length()).forEach(length -> consume(length));
```

```java
for (String element : items) {
    if (!(!element.isEmpty())) {
        continue;
    }
    Integer mapped = element.length();
    consume(mapped);
}
```

The converter uses resolved Java types and fresh variable names. Expression
lambdas are inlined when their parameter is not reassigned. Complex lambda
blocks retain a typed `Predicate`, `Function` or `Consumer` local, preserving
local `return`, `finally`, scope and overload behavior. Static/unbound method
references, constructor references and references bound to `this` use the same
typed-function path. Each function is created once; each map is evaluated once
per accepted element, in pipeline order. Ordinary `Iterable.forEach` calls are
also supported.

These reverse conversions use the shared ULR (`LoopModel`, `FilterOp`, `MapOp`,
`PeekOp`, `StreamTypeConversionOp`, `ForEachTerminal`). Functional type and scope metadata live in the Core model;
original AST nodes and bindings remain in a JDT context. Both loop renderers and
the stream renderers consume this model. See [Architecture](ARCHITECTURE.md) for
the extraction/rendering contracts.

When several source formats are enabled, overlapping nested loops are converted
in separate cleanup passes. The handlers share variable-name reservations so
their generated iterator declarations cannot collide.

The conversion remains conservative: parallel or unverified stream sources,
stateful operations (`sorted`, `distinct`, `limit`, etc.),
other terminals (`collect`, `reduce`, matches, etc.), unresolved/non-denotable
types and explicitly overridden source methods remain unchanged. Arbitrary
bound method references such as `getSink()::accept` and function-valued arguments
remain unchanged because their eager evaluation and null checks need a separate
translation. Comments between pipeline calls also keep the chain unchanged;
comments in copied lambda bodies are preserved. Each inlined `peek` block has its
own scope, and a callback that modifies its parameter retains its function boundary.

As with the existing collection-to-loop conversion, this assumes the standard
collection traversal contract; it does not prove equivalence for runtime
subclasses that override stream, spliterator or traversal behavior.

### Source and type coverage

| Source | Enhanced for target | Iterator while target |
|--------|---------------------|-----------------------|
| Standard `Collection.stream()` / `Iterable.forEach()` | Supported | Supported |
| `Arrays.stream(array)` for reference, int, long or double arrays | Supported | Supported; primitive iterators avoid boxing |
| `Arrays.stream(array, from, to)` | Unchanged | Supported; the original factory preserves bounds checks and evaluation order |
| JDK Stream/IntStream/LongStream/DoubleStream `of`, `empty`, `ofNullable`, `range`, `rangeClosed`, `iterate`, `generate` where provided by the API | Unchanged | Supported as sequential, lazy sources |

Primitive and reference stages may be mixed. Explicit type witnesses, overload
selection, boxing, widening and callback scopes are preserved in the ULR and
consumed by both stream renderers and the imperative renderer. Array factories
retain their original qualification/type arguments; the adapter stores the
corresponding AST attachment. Inclusive ranges at `Integer.MAX_VALUE` and
`Long.MAX_VALUE` retain the JDK iterator protocol rather than introducing a
counter that can overflow.

Iterator while **and classic iterator for** loops can become enhanced for loops
when the iterator is referenced only by its declaration, condition and initial
`next()`. The analysis indexes resolved variable bindings once per source AST.
Uses after the loop, nested uses, lambda captures, `remove()` and extra `next()`
calls prevent conversion. Imperative targets retain `break`, `continue`, labels,
returns and mutations of surrounding state; stream targets retain their stricter
capture/control-flow checks. Element declarations preserve final/annotations,
array dimensions and resolved types. Non-denotable types remain unchanged.

`StreamCoverageTest` compares compilation and runtime results for the original
source, portable ULR output, AST stream output, native editor proposals and actual
cleanup output. It includes overloads, callback-local returns, repeated local
names, primitive extremes, NaN/signed zero, null arrays, range boundaries and
array-slice exception/evaluation order. This is regression coverage, not a proof
for arbitrary custom stream implementations or all Java programs.

### Comment Preservation ✨

**New in February 2026!** The plugin now automatically preserves comments during transformations:

```java
// Before:
for (String item : items) {
    // Skip empty items
    if (item.isEmpty()) continue;
    System.out.println(item);
}

// After:
items.stream()
    .filter(item -> {
        // Skip empty items
        return !(item.isEmpty());
    })
    .forEachOrdered(item -> {
        System.out.println(item);
    });
```

**Features:**
- ✅ Preserves line comments (`//`), block comments (`/* */`), and Javadoc (`/** */`)
- ✅ Supports leading, trailing/inline, and embedded comments ✨ **NEW: Inline comments!**
- ✅ Comments appear in generated block lambdas
- ✅ Works for filter, map, and forEach operations
- ✅ Bidirectional transformations preserve loop body comments
- ✅ Enabled by default - no configuration needed

**What's inline/trailing comments?**
```java
// Before:
for (String item : items) {
    System.out.println(item); // Print the item (inline comment!)
}

// After:
items.stream()
    .forEachOrdered(item -> {
        System.out.println(item); // Print the item (preserved!)
    });
```

**Supported:** Enhanced-for loops, bidirectional transformations  
**Coming soon:** Iterator-while and traditional for-loops

For detailed examples and technical information, see [ARCHITECTURE.md](ARCHITECTURE.md).

### Recent Improvements (December 2025 - February 2026)
✅ **Code Cleanup**: Removed ~366 lines of dead code (78% reduction in Refactorer.java)
✅ **Math.max/Math.min Support**: Full support for MAX/MIN reduction with method references
✅ **Enhanced Tests**: 34 comprehensive test cases covering all patterns
✅ **Better Documentation**: Complete architecture and implementation docs
✅ **Robustness Improvements** (Option 3):
  - Variable scope validation to prevent variable leaks
  - Labeled continue detection (rejected for safety)
  - Improved side-effect statement validation
  - Better tracking of produced/consumed variables across pipeline stages
✅ **Comment Preservation** (February 2026 - Phase 10):
  - Automatically preserves source code comments during transformations
  - Supports leading, trailing/inline, and embedded comments
  - Comments appear in generated block lambdas
  - Full support for enhanced-for loops to streams
  - Bidirectional transformations preserve loop body comments
  - See [ARCHITECTURE.md](ARCHITECTURE.md) and [FAQ.md](FAQ.md#q-was-ist-mit-inline-kommentaren--what-about-inline-comments) for details

## Editor assists, style hints and cleanup

| Entry point | Scope | How to use |
|-------------|-------|------------|
| Quick Assist | The innermost loop or pipeline at the caret | Press **Ctrl+1** in Java source and choose a conversion target. |
| Quick Fix | A reported, currently convertible loop | Enable optional hints under **Java → Loop Conversion (Sandbox)**, then use **Ctrl+1** on a hint. |
| Cleanup | Supported loops in the selected compilation units | **Source → Clean Up… → Functional Converter (Sandbox)**: enable loop conversion, choose a target and source formats. |
| Save action | Supported loops in the file being saved | Configure the Functional Converter's existing save-action profile explicitly. |

All entries use the same resolved-AST analysis and ULR transformations. Quick
Assist works independently of cleanup settings. It offers only applicable
conversions and never rewrites an adjacent loop because the caret is near it.
Proposals use JDT's native change preview and LTK undo. Previewing repeatedly
leaves the source unchanged and produces stable variable names. Stream targets
require Java 8 or later in the project settings.

Style hints are **off by default**. Choose Information or Warning and the desired
target to enable them. They describe a style choice, not a Java compilation
error. Hints update during reconciliation and Java builds; the Problems view
updates on the next build after changing this preference. JDT owns removal of
managed markers on rebuild and clean. Unsupported code gets no conversion hint.

| Target | Accepted source forms |
|--------|-----------------------|
| Stream | Enhanced for, supported iterator while/for patterns, supported classic index loops |
| Enhanced for | Supported sequential stream/forEach expressions and iterator while/for loops |
| Iterator while | Enhanced for over `Iterable`, supported sequential stream/forEach expressions |

The cleanup dialog disables source choices that do not apply to the selected
target and previews the chosen conversions. An explicit conversion target takes
precedence over legacy stream-only cleanup flags. Existing `for` and `while`
profile identifiers are accepted as aliases for `enhanced_for` and
`iterator_while`.

Enhanced-for to iterator conversion preserves the iterator's generic element
type separately from the loop variable's type. This covers unboxing and widening,
wildcards, nested generic types, `var`, raw iterables with an `Object` variable,
final/annotated variables and array-valued elements. Imports respect local type
names, and labeled `continue`/`break` still target the generated loop. Array
sources and unresolved or erroneous loops are left unchanged.

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

### Documentation
- [FAQ](FAQ.md) - Frequently Asked Questions (Deutsch/English) - **Start here for quick answers!**
- [Comment Preservation Guide](ARCHITECTURE.md) - Detailed guide on comment preservation feature
- [Before/After Examples](README.md) - Real-world transformation examples with comments
- [Architecture Documentation](ARCHITECTURE.md) - Complete design and implementation details
- [TODO and Roadmap](TODO.md) - Future enhancements and development phases

### External Resources
- [NetBeans Implementation](https://github.com/apache/netbeans/tree/master/java/java.hints/src/org/netbeans/modules/java/hints/jdk/mapreduce)
- [Eclipse JDT AST](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/package-summary.html)
- [Java 8 Streams](https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html)

## License

Eclipse Public License 2.0 (EPL-2.0)
