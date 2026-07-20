# Int-to-Enum Refactoring Roadmap

## Current implementation

### Completed

- [x] Eclipse cleanup integration and preference UI
- [x] Helper-based cleanup architecture using `CompilationUnitRewriteOperationWithSourceRange`
- [x] Existing integer-switch prototype
- [x] Binding-based detection of private integer state constants
- [x] Detection of if/else-if chains comparing one private method parameter with a constant group
- [x] Support for equality operands in either order
- [x] Common-prefix enum naming
- [x] Validation of distinct compile-time integer values
- [x] Validation of generated Java identifiers and nested-type name conflicts
- [x] Whole-compilation-unit reference validation for constants, the state parameter, and the private method
- [x] Propagation to proven private method call sites
- [x] Removal of migrated constant fields or individual declaration fragments
- [x] Tests for positive migration and conservative rejection
- [x] Documentation of the single-file safety boundary

## Safety model

The current if/else migration intentionally accepts only candidates whose complete source-level data flow can be proven inside one compilation unit:

- constants are `private static final int` compile-time constants;
- the method is private;
- the state parameter is used only in recognised comparisons;
- all calls pass one of the recognised constants;
- constants have no other references;
- values are distinct and are not treated as aliases.

This is narrower than the eventual feature, but it is suitable for an ordinary cleanup because it does not guess about references in other files.

## High-priority next steps

### 1. Extract a reusable semantic candidate model

- [ ] Separate candidate discovery from AST rewriting
- [ ] Represent constant groups, state carriers, comparison sites, call sites, and rejection reasons explicitly
- [ ] Produce diagnostics explaining why a potential candidate was rejected
- [ ] Reuse the same model for if/else chains, switches, quick assists, and project-wide refactoring

### 2. Harden the existing switch prototype

- [ ] Apply the same visibility and whole-reference safety checks as the if/else implementation
- [ ] Propagate method call arguments before changing a parameter type
- [ ] Reject public API changes in single-file cleanup mode
- [ ] Support qualified constant references
- [ ] Add switch-expression support
- [ ] Add tests for method references, unsupported call arguments, and external-use risks

### 3. Support additional state carriers within one file

- [ ] Local variables initialized and assigned only from one constant group
- [ ] Private fields whose reads and writes are fully contained in one compilation unit
- [ ] Multiple if/else chains using the same state carrier
- [ ] Multiple private methods participating in one closed state flow
- [ ] Return values when every producer and consumer is locally provable

### 4. Detect patterns that must not become a plain enum

- [ ] Bit flags and masks: recommend `EnumSet` or retain the integer representation
- [ ] Persisted or wire-protocol values: generate an enum with an explicit numeric value and `fromValue`
- [ ] Aliased integer constants: preserve aliases or reject with an explanation
- [ ] Ordered/ranged arithmetic: reject plain enum migration
- [ ] JNI, reflection, serialization, annotations, and compile-time constant consumers

## Project-wide refactoring

### Architectural requirement

`ICleanUpFix#createChange` returns a single `CompilationUnitChange`. Therefore, a third-party ordinary cleanup cannot atomically coordinate declaration and reference changes across several compilation units.

A full migration should use one of these approaches:

1. **JDT UI multi-file cleanup API**
   - revise the proposal in `carstenartur/eclipse.jdt.ui#68` into a small, focused API change;
   - integrate candidate collection with `CleanUpRefactoring` lifecycle and preview;
   - return an LTK `CompositeChange` with unified preview and undo.

2. **Dedicated plugin refactoring**
   - add an Eclipse command and LTK `Refactoring` implementation in this project;
   - use JDT search and hierarchy APIs to collect affected compilation units;
   - expose a wizard with candidate selection, compatibility options, and preview;
   - keep the ordinary cleanup as the conservative single-file variant.

The second path is possible outside JDT UI and is likely the fastest way to validate the feature. Integration into the standard Clean Up profile requires changes in JDT UI.

### Project-wide analysis tasks

- [ ] Find all references with JDT `SearchEngine`
- [ ] Follow method declarations, overrides, implementations, and interface contracts
- [ ] Propagate types through parameters, return values, fields, locals, assignments, and calls
- [ ] Detect reflection and binary/external API compatibility risks
- [ ] Decide whether to preserve deprecated integer adapter constants
- [ ] Handle tests and generated sources
- [ ] Produce one atomic `CompositeChange`
- [ ] Add cancellation and progress reporting
- [ ] Add integration tests using at least three compilation units

## User-facing configuration

- [ ] Minimum number of states
- [ ] Preserve numeric values in the enum
- [ ] Keep deprecated integer compatibility constants
- [ ] Include package-visible APIs
- [ ] Include public APIs only with explicit confirmation
- [ ] Preserve if/else or convert to switch
- [ ] Report-only analysis mode

## Test matrix

- [x] Disabled cleanup
- [x] Basic private if/else chain
- [x] Reversed equality operands
- [x] Proven private call-site propagation
- [x] Public API rejection
- [x] Arbitrary call argument rejection
- [x] Unrelated constant-use rejection
- [ ] Multiple constant fragments in one field declaration
- [ ] Qualified references (`Type.STATUS_PENDING`)
- [ ] Parenthesized comparisons and arguments
- [ ] Duplicate values
- [ ] Existing nested enum-name conflict
- [ ] Method references
- [ ] Recursive calls
- [ ] Multiple candidate groups in one type
- [ ] Local variables and private fields
- [ ] Multi-file interface/implementation/caller scenario

## DSL status

The transformation is not currently expressible in the `.sandbox-hint` DSL. It requires binding-aware, multi-statement and eventually multi-file analysis plus new type generation. The DSL could participate only after gaining semantic candidate aggregation and coordinated change support.
