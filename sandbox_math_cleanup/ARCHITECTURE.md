# Mathematics Cleanup

This module participates in the explicit mathematics cleanup. Numerical proof and Java source emission are separate from the Eclipse UI. The [current qualification](../docs/superpowers/plans/2026-10-31-java-mathematics-context-qualification.md) identifies the tested revisions and boundaries of its dated evidence; consult the linked integration PR for current CI results.

## Complete computation tasks and CAS ownership

`JavaComputationExtractor.extractTasks` exports bounded,
straight-line primitive integral computations under `PRESERVE_JAVA`. It reuses
the existing binding/type/operation decoder, retains constant initializers, and
connects local assignments, array stores and continuing carry calculations.
`JavaComputationTask` contains the existing Regelsuche `JointComputationPlan`
and original `SourceEvaluationTrace`, not a second mathematical representation.
`numericRequest` gives Regelsuche the caller's explicit goal and remaining budget.

Each local assignment is a distinct named output, identified by its JDT binding
and source occurrence. Every source read is a fresh scalar input, including
repeated reads after stores through potentially aliased arrays. The separate
schedule retains `SOURCE_READ`, `LOCAL_COMMIT`, `ARRAY_STORE` and `RETURN` events
and their positions among original numeric occurrences. A whole-source SHA-256
binds offsets to the inspected snapshot. The bounded SDK transport is validated
before export; unsupported statements split tasks and produce diagnostics.

This is a value projection, not an executable memory model. The consumer must
execute each read, local commit and store in its original order. In particular,
if a store throws, a handler observes preceding local assignments, never a later
carry shift. Pure numeric equivalence cannot establish that this source schedule
may change. `JavaTaskSourceRewriter` can now realize a verified task in ordinary
cleanup by replacing only total right-hand sides at their original positions.
It keeps every declaration, call, source read and store, introduces no temporaries,
and never refers to an overwritten input. It requires a reduction in actual source
arithmetic, recompiles and re-extracts the complete task, compares its event
schedule, and independently proves the emitted value plan. Existing source
receipts and byte-exact Apply/Undo remain mandatory. Whole-task attempts receive
only the file-budget remainder after established regions, and reserve their full
allocation in four shares for search, source realization/re-extraction, independent
verification and explanation. Source-context discovery and explicit preparations
are charged within their share; reported completed-work overruns additionally
debit the file budget even when an attempt is rejected.
Indices depending on regionally changed locals, non-total arithmetic, unsupported
calls and control-flow joins retain conservative boundaries; loop bodies are
separate per-iteration tasks. The separately admitted counted-loop pass described below supplies its own scheduling evidence.

Task extraction may summarize a statically bound, straight-line primitive helper
from actual project/sourcepath source, including nonprivate and cross-file helpers.
The original call remains in place, preserving argument evaluation and class
initialization (including failures). Method declarations and parsed source units
are cached only for the current request. Source hashes are rechecked before an
edit can be applied. Mutable helper state, uncertain numerical dispatch, recursion and unsupported
effects remain boundaries; there is no summary inferred from binary attachments.

`JavaCandidateRanking` contributes actual emitted arithmetic, capture references,
receiver checks and readability footprint to the existing SDK search for memory
and BigInteger regions. Ordinary scalar regions keep the SDK's structural model.
Original source occurrences and the candidate DAG have separate cost entry points;
weighted storage improvements never count as runtime improvements. Source-specific
BigInteger bounds below the generic receiver safety ceiling enable word-size and
intermediate-growth estimates. The generic 4096-bit guard remains a safety cap,
not an assumed operand size. Ranking is metered and cached per search. Explanation
accounting includes independent verification, derivation replay and presentation,
bounded by the remaining file allowance without changing the request identity.
Array search reserves a third of its existing share for source ranking and
explanation. The overall file limit remains unchanged.

Sandbox owns JDT semantics and source effects. Regelsuche owns arithmetic
relationships, candidates and independent proofs. A configurable CAS, including
a future SymPy option, belongs behind Regelsuche's existing solver/proposal
boundaries. `SolverIr` is an obligation representation: it does not replace the
typed Java computation graph. Adapters must select supported mathematical
subgraphs losslessly, preserve widths/division/casts and declared assumptions,
share work/time/cancellation limits, and independently verify decoded proposals.
There is no CAS-specific formula catalogue or new Python process in Sandbox.

`JavaComputationTaskTest` compares the entire pinned BC 1.85.2 `Nat256.add` carry
block with a hand-built SDK plan: 25 ordinary edit regions become one inspection
task with 26 outputs, 16 reads and eight stores. Replay against the actual BC
method covers aliasing, overlapping offsets, null and short arrays. A separately
compiled try/catch example checks handler-visible intermediate local values.
This demonstrates additional context, not a measured speedup for Nat256.

The source and independently assembled Nat256 plans now both reach SDK search;
the actual int-to-long initializer conversion is retained in the manual plan's
source trace. The current result is an incomplete search relation, not a proved
improvement. `WordProjectionCleanupTest` separately requires a real high/low word
reconstruction to simplify, compile, execute identically and support exact Undo.
Failed initial-validation preparation reports a conservative work bound including
completed trace checks. The adapter debits that value before retries, including
overshoots; unused allowance remains available. Unknown legacy failures reserve
the request allowance plus bounded unreported preparation. Existing
file limits and region weights remain unchanged. Completed bounded searches debit
their reported work when the SDK certifies complete accounting; partial checker
stops still reserve the allocation or reported overshoot. Explanation verification,
replay and presentation share the remaining allowance as described above;
completed preparation is still charged when a later phase fails.

## Callable, iteration and scheduling adapters

[Java computation contexts](JAVA_CONTEXTS.md) is the current coverage reference.
`JavaExpressionContexts` keeps expression edits in place with exact primitive
types and enclosing overload/SAM checks. `JavaScalarMethodBody` admits exact source
instance/reference summaries without inferring purity from names. Pure ULR core
`NumericIteration` describes numeric sources/elements/terminals separately from
converter rendering; inspecting a stream never authorizes conversion.
`JavaNumericStreamOptimizer` composes bounded adjacent primitive map graphs by
resolved bindings and proves the per-element kernel while preserving the terminal.

`JavaLoopInvariantAnalysis` admits exact fixed-count loops and total invariant
primitive local arithmetic. `JavaLoopInvariantOptimizer` independently replays
source controls, declaration/binding/no-write facts, the temporary and cost vector.
`LoopSchedulingEvidence` records that adapter scheduling receipt separately from
the SDK proof of one evaluation; no SDK search derivation is manufactured for
motion. Conditional normal-completion cost applies when pinned array effects may
throw. Operation estimates and retained storage are not runtime measurements.

Analysis order is established primary/retry regions, deferred assignments, stores,
richer reads, source tasks/expression contexts, then streams and loops, sharing
the remaining allowance. `JavaTaskSourceRewriter.optimizeWithRemaining` refunds
only unused no-candidate search work; failed candidate realization keeps its
reservation. Expression-only contexts still cannot emit capture statements and
loop candidates still exclude calls, reads and unboxing.

## Ordered primitive array inputs

For `PRESERVE_JAVA`, including explicit mathematical opt-in, straight-line
regions can capture complete nested integral array loads and nonvolatile field
chains rooted in locals/parameters, lexical names, unqualified `this` or types.
Every complete occurrence rereads mutable links and values. Zero-argument numeric
instance calls on local/parameter receivers can be opaque inputs, including
impure/throwing calls; existing helper/SAM/known-operation summaries take priority.
Exact integral JDK wrappers are numeric only at actual resolved unboxing or an
eligible explicit primitive conversion call. Wrapper reference identity is not
numeric equality. Absolute indexed integral buffer reads retain their old model.

Indices use total primitive locals/constants, literals, casts and arithmetic.
Authored formulas retain their original spelling. Calls, mutable field indices,
mutation, nested index reads, division and remainder remain excluded. Volatile
links and effectful/computed receivers remain boundaries. See
[ordered input admission](FIELD_ARRAY_INPUTS.md) for exact composition limits.

Each source read gets a distinct scalar input and an ordered `ArrayRead` source
obligation. The source rewriter emits every read exactly once into a fresh final
local before the numerical replacement, including reads whose values become
dead. `inputNames` contains identifiers only. The SDK and the Java emitter may
schedule the captured values freely; they never schedule the memory accesses.
Repeated reads and aliases are not identified with one another.

Hoisting captures is limited to regions containing total primitive integral
operations. A statement whose read depends on a local already declared or
changed in the region starts a new region, keeping that local's preceding
evaluation in source. Potentially throwing arithmetic, array/field writes,
external local assignments, unsupported calls, and control-flow boundaries
separate regions. The original index text and the left-to-right order of reads
preserve null and bounds exception precedence. Existing loop-body extraction
remains local to each straight-line block; loop headers and writes are unchanged.

Checked and guarded-fallback profiles still treat array reads as boundaries.
Floating arrays, arbitrary reference values and helper-body memory accesses are
not admitted. Integral-wrapper array loads need actual unboxing. There are at most 64 captured reads per region. This is Java
dependency admission, with no array formula catalogue, memory optimization, or
additional mathematical search engine. `ArrayReadComputationTest` exercises the
real extraction, proof, source-edit, compilation, execution, and Undo paths.

The source adapter charges one primitive work unit for each capture store and
each capture-name reference in the actual emitted arithmetic and output values.
It also adds all N capture values to the scalar plan's peak storage, a
conservative bound that may double-count existing input lifetimes. Original
array loads and index calculations execute once on both sides and provide no
claimed savings. The reported costs and runtime/allocation goal gates use this
same assessment. Readability can accept a simpler formula with higher assessed
cost, while its runtime-improvement flag includes all capture work. These are
declared model units, not bytes, allocations, or measured timings.

An unsuccessful connected array attempt can retry the contained scalar regions
available when array reads are statement boundaries. This alternative extraction
is lazy and cached per analysis. Only strictly smaller array-free regions are
scheduled, excluding overlaps with accepted edits or pending regions; they cannot
produce another array retry. The rejected attempt's search and presentation work
stay charged, and all alternatives share the existing remaining file budget.
Capture costs and proof requirements are unchanged.

## Incoming primitive assignments

Under `PRESERVE_JAVA`, a plain or compound assignment to a resolved primitive
integral local or parameter may expose its own arithmetic even when the
declaration is outside the current block. Such an assignment is a singleton
region: preceding writes finish before it, and following reads start after it.
This also applies after a preparation retry reaches its fragment output limit.
For a compound assignment, the old local value enters the plan before decoding
the right operand. Existing Java promotion, shift and final narrowing rules are
used; the incoming value must never be confused with the value written by the
replacement. New incoming compound admission requires primitive integral types
on both sides. Field/array write targets, floating-point assignments, and checked
and guarded-fallback profiles retain their boundaries. Authored constant formulas
are still preserved, without treating a literal RHS in `x += 1` as constant-only
work. No numerical rule is implemented in this source adapter.

Whole-file analysis schedules these additional regions after established regions
and their retries, spending only the remaining file work. They do not dilute the
existing regions' weighted search shares. An explicit selection still receives
the available budget directly. The SDK and its independent proof, emission and
cost checks are unchanged.

For the runtime goal, an additional conservative source-quality check requires
fewer arithmetic operators in the replacement of an incoming assignment. Infix
operations and nonliteral negation/complement count; casts and method calls do
not. This rejects helper expansion and sharing of implicit promotions alone,
even when the SDK reports a saving. Equal-operator-count strength reductions may
therefore remain unchanged. Readability and allocation goals retain their own
existing cost policies. This check is a source-quality filter, not a proof or a
measurement of runtime performance.

`SwtAssignmentRegionTest` analyzes the complete hash-pinned SWT 3.135.0
`StyledTextRenderer`, compiles both complete versions and compares both actual
`addMerge` overloads across 96 style/range cases. Additional tests cover narrow
conversions, wraparound, observable writes before exceptions, search-budget
starvation and the fragment-retry incoming-value regression.

`IncomingCompoundAssignmentTest` covers all eleven compound operators for the
five primitive integral kinds, the old-value/field-read distinction, excluded
profiles and targets, and an actual rewrite with exceptions and exact Undo.
`FieldArrayStateVisibilityTest` compares returned values, exception types,
post-call array contents and field identities after intervening rebinding/stores,
including failures after the write and aliased inputs. These test requirements
must pass in the normal Maven authority before qualification is claimed.
