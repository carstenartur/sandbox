# Functional Loop Converter Plugin Reference

> **Read this when**: Working on `sandbox_functional_converter`, `sandbox-functional-converter-core`, or their test modules.

## Purpose

Transforms imperative Java enhanced-for loops into functional Java 8 Stream equivalents:
- Simple forEach: `for (x : list) body` → `list.forEach(x -> body)`
- Filter: `if (cond) continue;` → `.filter(x -> !(cond))`
- Map: variable declarations → `.map()`
- Reduce: accumulators → `.reduce()` (sum, product, increment, decrement, Math.max, Math.min, String.concat)
- Match: early returns → `.anyMatch()`, `.noneMatch()`, `.allMatch()`
- Bidirectional: Enhanced-For ↔ Iterator-While conversions
- Comment preservation: all comment types preserved during transformation

## Architecture

### Two Modules

| Module | Type | Needs Xvfb? |
|--------|------|-------------|
| `sandbox_functional_converter` | Eclipse plugin | Yes |
| `sandbox-functional-converter-core` | Plain Maven JAR | **No** |

The core module contains AST-independent loop transformation logic. Test it fast:
```bash
mvn test -pl sandbox-functional-converter-core   # No xvfb needed
```

### Key Classes

- `UseFunctionalCallCleanUpCore` — Main cleanup class (extends `AbstractCleanUp`)
- `UseFunctionalCallFixCore` — Enum of all transformation types
- `AbstractFunctionalCall<T>` — Base class for transformation helpers (`find()`, `rewrite()`, `getPreview()`)  
- `FunctionalHolder` — Implements `HelperVisitorProvider`
- `SandboxCodeTabPage` — UI preferences tab

### Statement Handlers (Strategy Pattern via Enum)

Each transformation pattern is a separate helper class following the same pattern as encoding helpers.

## Target Format Selection

Three output formats (controlled via UI dropdown):

| Format | Status |
|--------|--------|
| **Stream** (default) | ✅ Fully implemented |
| **Classic for-loop** | ⏳ UI only — transformation pending |
| **While-loop** | ⏳ UI only — transformation pending |

## Test Patterns

Test enum `UseFunctionalLoop` contains all patterns with `given` and `expected` fields.

34 tested patterns including:
- SIMPLECONVERT, CHAININGMAP
- Filter chains (ChainingFilterMapForEachConvert, NonFilteringIfChaining)
- Complex chains (SmoothLongerChaining, MergingOperations)
- Reducers (SimpleReducer, ChainedReducer, IncrementReducer, DecrementingReducer)
- Max/Min reducers (MaxReducer, MinReducer, MaxWithExpression, MinWithExpression)
- Complex reducers (FilteredMaxReduction, ChainedMapWithMinReduction)
- Match operations (ChainedAnyMatch, ChainedNoneMatch)
- String operations (StringConcat)

## Comment Preservation

Comments are automatically preserved during transformations (enabled by default):
- Line comments (`//`), block comments (`/* */`), Javadoc (`/** */`)
- Leading, trailing/inline, and embedded comments
- Works for filter, map, and forEach operations
- Bidirectional transformations preserve loop body comments

## Safety Checks

The converter validates before transforming:
- Variable scope (loop variable not used after loop)
- Labeled continue detection
- Side-effect analysis
- Loop variable not modified elsewhere in body

## Running Tests

```bash
# Plugin tests (requires xvfb)
xvfb-run --auto-servernum mvn test -pl sandbox_functional_converter_test

# Core module tests (no xvfb needed — prefer this for core logic)
mvn test -pl sandbox-functional-converter-core

# Specific test
xvfb-run --auto-servernum mvn test -Dtest=Java8CleanUpTest -pl sandbox_functional_converter_test
```