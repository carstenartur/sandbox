# TODO — Mining Infrastructure

## Implemented

- [x] **PR 1: Deduplicate refactoring-mining PRs** — Hash-based dedup and stale PR cleanup in `refactoring-mining.yml`
- [x] **PR 2: Epoch Rotation** — `CommitWalker.nextBatch()` now supports `endDate`, `MiningState.RepoState` tracks `currentEpoch`/`completedEpochs`, `MiningConfig` parses `epochs` from YAML, `MiningCli` performs automatic epoch rotation
- [x] **PR 3: Enable additional repos + hints** — Uncommented 3 Eclipse platform repos and added 10 more bundled hint files in `repos.yml`
- [x] **PR 4: Wire up auto-generation of hint files** — `mining-core.yml` now auto-commits `.sandbox-hint` files created by `HintFileUpdater`
- [x] **PR 5: Category-aware mining rounds** — `RepoState` now tracks `categoryHitCounts`, `exhaustedCategories`, and `focusCategory` for category-aware mining
- [x] **PR 6: DslEnhancementReporter** — New `DslEnhancementReporter` class that groups `NEEDS_DSL_EXTENSION` rules by limitation category and generates issue descriptors; `mining-core.yml` auto-creates GitHub issues per limitation category

## DSL Enhancement Issues (Require Deeper Design Work)

These issues require changes to the TriggerPattern DSL parser and matching engine in `sandbox_common_core`. They are tracked here because they need design discussions and cannot be implemented as simple code changes.

### Issue 1: 🔧 Support bitwise operators in patterns/replacements

**Current Limitation:** Bitwise operators `|`, `&`, `^`, `>>`, `<<` are not supported in DSL patterns.

**Impact:** Eclipse platform code heavily uses bitmask patterns like `StatusManager.SHOW | StatusManager.LOG` and SWT style constants like `SWT.BORDER | SWT.V_SCROLL`. Any refactoring involving these is currently RED.

**Proposed DSL extension:**
```
$mgr.handle($status, StatusManager.SHOW)
=> $mgr.handle($status, StatusManager.SHOW | StatusManager.LOG)
;;
```

**Files to modify:** `HintFileParser.java`, `PatternMatcher.java`

### Issue 2: 🔧 Support statement insertion / wrapping (try-with-resources, guard clauses)

**Current Limitation:** The DSL performs pattern matching and replacement, not arbitrary code insertion.

**Impact:** The most common Java modernization pattern is wrapping resource creation in try-with-resources. Mining keeps finding commits like `FileReader fr = new FileReader(f)` → `try (FileReader fr = new FileReader(f)) { ... }` but the DSL cannot express this.

**Proposed approach:**
- Add a new rule kind `WRAP` for structural transformations
- Or extend the `<? code ?>` embedded Java blocks to handle statement-level rewrites

**Files to modify:** `HintFileParser.java`, `TransformationRule.java`, `PatternMatcher.java`

### Issue 3: 🔧 Support complex expression composition with arity changes

**Current Limitation:** Wrapping a matched variable in a new expression has limited support. Combining it with arity changes may not work.

**Impact:** Patterns like `Arrays.asList(a, b, c)` → `List.of(a, b, c)` work, but more complex compositions like splitting arguments or reordering varargs fail.

**Proposed enhancement:**
- `$args$.length` — number of matched arguments
- `$args$[0]`, `$args$[-1]` — indexed access to specific arguments
- Spread syntax in replacement: `newMethod($args$, $extra)`

**Files to modify:** `PatternMatcher.java`, `ReplacementBuilder.java`

### Issue 4: 🔧 Support type-parameterized matching (generics)

**Current Limitation:** The DSL cannot match or constrain generic type parameters. `new ArrayList<String>()` and `new ArrayList<Integer>()` are indistinguishable in patterns.

**Impact:** Many Java modernization rules need to distinguish based on the generic type parameter.

**Proposed enhancement:**
- Add generic type matching in patterns: `new java.util.ArrayList<$T>()`
- Add generic type guards: `genericTypeIs($var, 0, "java.lang.String")`

**Files to modify:** `HintFileParser.java`, `PatternMatcher.java`, `GuardEvaluator.java`
