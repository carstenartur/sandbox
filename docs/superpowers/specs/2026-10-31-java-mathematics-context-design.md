# Java computation contexts for mathematics cleanup

The user authorized implementation of the Java-language expansion discussed on
2026-10-31. Build on merged Sandbox PR #1771 (`4f816e7`) and preserve the existing
ownership: JDT source semantics in Sandbox, mathematical search and independent
numerical proofs in Regelsuche, iteration structure in the shared ULR core.

## Intent and approach

Expose the same arithmetic despite lambda, method, branch, iteration and memory
boundaries. Reuse the typed computation graph and the existing proof/emission
pipeline. Preserve source execution and rewrite review. Prefer in-place callback
and branch edits, plus bounded compositions with explicit source contracts.
Alternative source desugaring into synthetic user-visible methods/loops was
rejected because it changes overload selection, scope, exception timing and
readability. A general whole-program optimizer would exceed the supported proof
model; admit independently checkable fragments and report remaining boundaries.

## Deliverables

1. Numeric block and expression lambdas, including multiargument reducers, use
   the nearest callable return type. Keep captures and generated work inside the
   callable. Numeric conditional/switch arms and unbraced control-flow bodies can
   be optimized in their original execution context. Preserve target typing.
2. Source summaries cover exact-dispatch instance methods. Removable calls must
   have an implicit/explicit unqualified `this` receiver; retained calls may have
   a local receiver. Reject mutable callee state, recursion, uncertain dispatch,
   synchronization, native bodies and unsupported effects.
3. Reuse ULR descriptors for numeric stream/iteration contexts. Recognize numeric
   terminals without authorizing stream-to-loop conversion. Compose adjacent
   primitive map lambdas through the existing numeric graph and independent
   verifier, preserving intermediate widths and the terminal operation.
4. Lift proven invariant total primitive computations from loop bodies while
   retaining loop headers, trip behavior and mutable state. Conservatively keep
   exceptions, reads, callback boundaries and state-dependent expressions in place.
5. Extend ordered source captures for nested integral arrays, object-qualified
   fields and numeric accessors/unboxing. Every original read/call executes once
   in its original order; identical text never establishes heap equality.
6. Qualify source effects, independent proofs, full-file compilation, target
   bindings, budgets, cancellation, Apply/Undo, stale dependencies and real corpus
   behavior. Document precise supported fragments and unresolved boundaries.

## Constraints

- Java 25, existing Maven/JUnit and Tycho builds; no separate test runner.
- Existing safety profiles, explicit mathematical opt-in and budgets stay intact.
- No algebraic formula catalogue or CAS process in Sandbox.
- Original method calls and source effects stay pinned unless their removal is
  independently justified. No inferred purity from names or `final` references.
- ULR core stays JDT-independent. Numerical proof must not depend on renderers.
- Publish ordinary PRs against main. No stacked PRs. Keep coherent implementation
  packages reviewable; document a substantive policy exception before a package
  crosses the repository's text-size limit.

General recurrence closed forms, arbitrary virtual dispatch, general heap/alias
analysis and BigDecimal scale/rounding semantics require additional upstream
numeric proof models. They must not be represented as implemented by source-only
admission. Track them explicitly if qualification establishes this boundary.

## Validation

Compile and execute original/generated Java for integer overflow, empty loops,
null receivers/arrays, bounds failures, captured mutable state, throwing arguments,
class initialization, overload-sensitive lambdas and intermediate stores. Tests
must first demonstrate missing behavior or a safety failure. Run the complete
standalone mathematics suite, ULR core suite, native compilation/SpotBugs and
repository policy, followed by matched source-corpus scans. Estimated operations
and coverage gains are not runtime measurements.
