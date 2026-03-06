# TODO — Mining Infrastructure

## Implemented

- [x] **PR 1: Deduplicate refactoring-mining PRs** — Hash-based dedup and stale PR cleanup in `refactoring-mining.yml`
- [x] **PR 2: Epoch Rotation** — `CommitWalker.nextBatch()` now supports `endDate`, `MiningState.RepoState` tracks `currentEpoch`/`completedEpochs`, `MiningConfig` parses `epochs` from YAML, `MiningCli` performs automatic epoch rotation
- [x] **PR 3: Enable additional repos + hints** — Uncommented 3 Eclipse platform repos and added 10 more bundled hint files in `repos.yml`
- [x] **PR 4: Wire up auto-generation of hint files** — `mining-core.yml` now auto-commits `.sandbox-hint` files created by `HintFileUpdater`
- [x] **PR 5: Category-aware mining rounds** — `RepoState` now tracks `categoryHitCounts`, `exhaustedCategories`, and `focusCategory` for category-aware mining
- [x] **PR 6: DslEnhancementReporter** — New `DslEnhancementReporter` class that groups `NEEDS_DSL_EXTENSION` rules by limitation category and generates issue descriptors; `mining-core.yml` auto-creates GitHub issues per limitation category

## DSL Enhancement Issues — Implemented

### Issue 1: ✅ Bitwise operators in patterns/replacements

**Status: IMPLEMENTED**

- Added `InfixExpression` override in `PlaceholderAstMatcher` with explicit placeholder support in left/right/extended operands
- Bitwise operators (`|`, `&`, `^`, `>>`, `<<`, `>>>`) now fully work in DSL source and replacement patterns
- All operator types verified through tests: OR, AND, XOR, shift left, shift right, chained operators
- **Files modified:** `PlaceholderAstMatcher.java`

### Issue 3: ✅ Complex expression composition with arity changes (partial)

**Status: PARTIALLY IMPLEMENTED**

Implemented features:
- `$args$[N]` — indexed access to specific variadic arguments (positive and negative indices)
- `$args$[-1]` — last argument access (negative indexing from end)
- `$args$.length` — number of matched arguments (in replacement patterns)
- `argsCount($args$, N)` — guard function to check variadic arg count
- Spread syntax `$args$` continues to work as before (all args joined with `, `)

**Files modified:** `BatchTransformationProcessor.java`, `DryRunReporter.java`, `BuiltInGuards.java`

Still TODO (requires deeper design work):
- Argument reordering (e.g., `$args$[2], $args$[0], $args$[1]`)
- Argument splitting across multiple call sites
- Guard-based argument filtering

### Issue 4: ✅ Type-parameterized matching (generics)

**Status: PARTIALLY IMPLEMENTED**

Implemented features:
- `genericTypeIs($var, index, "type")` — guard function that checks generic type parameter at a given index
- Graceful degradation when binding resolution is unavailable (returns `true` conservatively)
- Supports both qualified and simple type names

**Files modified:** `BuiltInGuards.java`

Still TODO (requires deeper design work):
- Pattern-level generic type matching syntax: `new java.util.ArrayList<$T>()`
- Generic type variable binding in patterns
- Generic type constraints in pattern syntax

### Issue 2: Statement insertion / wrapping (try-with-resources)

**Status: PARTIALLY IMPLEMENTED**

Implemented features:
- `isResourceVariable($var)` — guard function that checks if a variable is AutoCloseable AND not already in try-with-resources
- Combined with existing `isAutoCloseable($var)` and `isInTryWithResourceBlock($var)` guards

**Files modified:** `BuiltInGuards.java`

Still TODO (requires deep DSL redesign):
- New rule kind `WRAP` for structural transformations (wrapping statements in try-with-resources)
- `<? code ?>` embedded Java blocks for statement-level rewrites
- Multi-statement pattern matching with resource lifecycle tracking
- This is fundamentally harder than expression-level transformations and needs dedicated design work

## Documentation Updated

- `dsl-explanation.md` (both sandbox_common_core and sandbox_mining_core copies) updated with:
  - `genericTypeIs()` guard documentation
  - `argsCount()` guard documentation
  - `isResourceVariable()` guard documentation
