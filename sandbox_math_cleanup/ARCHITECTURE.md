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

## Immutable numeric values

`JavaValueSourceSummary` admits source-defined top-level final nongeneric value
classes whose private final components are assigned one-to-one by a pure private
constructor. Source definitions and transitive constants come from the existing
request-local `JavaSourceContext`; no class or arithmetic method name selects a
formula. A `ScalarProjector` reuses the existing typed expression decoder.
Assignments and increment/decrement are rejected throughout each admitted method
and every expanded helper before decoding. Native/synchronized methods, finalizers,
instance initializers, inheritance and constructor effects remain boundaries.

Ordinary mathematical-mode analysis can project all BigInteger components of a
straight-line whole-method result jointly. The SDK discovers the candidate;
the adapter emits a guarded path, recompiles and re-extracts that actual branch,
and independently checks its joint values before exposing Apply. It retains the
original method body as the fallback and returns a fresh object through the
original constructor. This path requires explicit mathematical opt-in: the existing
contract permits BigInteger result reference identity changes, including shared
component leaves. The enclosing method's Javadoc and review description state
this distinction; a fresh wrapper does not guarantee fresh scalar components.
Default preserve-Java profiles do not enter this path. No portable BigInteger
allocation guarantee or deployment-runtime premise is inferred.
Only same-class helpers and static values are emitted, so
foreign initialization is not skipped. Source-defined constants are conditional
facts: runtime guards check their exact current values. Inputs require nonnull
exact BigInteger receivers and at most 4096 magnitude bits. A source modulus can
supply a smaller guarded bound for the existing magnitude-aware cost model.
Larger values, nulls and subclasses execute the original body.

One value-work meter starts before preflight and shares a deadline and cancellation
token across source facts, every method, SDK search, emitted-source replay and
explanation. Each SDK verification reserves its complete allowance, including
preparation and unsuccessful attempts. This does not increase configured defaults.

The reusable summary session also composes nested immutable values, bounded to
8 fields per type,32 BigInteger leaves and depth8. Recursive shape/method/constant
cycles are rejected. Source-defined value constants may call admitted pure source
factories; their initialization arithmetic supplies conditional metadata and is
excluded from the summarized method's runtime trace. Whole-method emission remains
restricted to direct BigInteger components. The recursive summary alone grants no
permission to skip initialization, reuse object identity, assume associativity,
claim canonical residues or cancel an inverse. Consumers must establish those
obligations separately. Complete Fp12/Fp6/Fp2 multiplication and a structural rename
are replayed against freshly compiled source with24 inputs and12 scalar outputs.

## Source locals, primitive guards and redundant assignment branches

Ordinary region emission can reuse a unique earlier output declaration as a
source local. Its availability follows the original output order, independently
of the SDK's node order. A local is never referenced by a generated prelude that
executes before its declaration. Shared intermediate nodes retain their original
capture strategy when this would be unsafe. The independent Java emission
verifier declares these same source locals and proves the re-extracted outputs
and trace. Authored layout arithmetic selected from immutable constant choices
remains a source-policy boundary; it is not a numerical assumption.

Primitive remainder regions can obtain nonnegative facts from binding-checked
dominating guards on effectively final integral locals. Discovery requires the
request's source context, shares its allowance and cancellation, and has an
additional 4096-node bound. A guard can exit through an actual source-defined
static helper chain. Every helper must have no return to its caller and must end
in a proven throw or another proven throwing call; early-return lookalikes,
recursion, instance dispatch, mutable locals and guessed aliases are rejected.
The assumption records the input binding, helper binding chain and dependency
fingerprints. The original guard and calls remain in place, preserving effects,
exceptions and class initialization. Traditional switch-local initializers are
expression edits with their original primitive type and evaluation point.
Whole-file analysis first completes established regions and their retries.
Primitive guard discovery then uses only the remaining file work, including
transitive helper lookup, and retries uncovered regions only when new facts were
proved. The original region identities retain context, slice and retry metadata.
An explicit selection and direct extraction still prove these facts immediately.
Receiver and BigInteger source proofs keep their existing scheduling.

`JavaSourceNumericBranches` separately proves the RHS values of two primitive
local assignments equal through the SDK verifier. It freshly parses and
re-extracts the source to replay its `SourceNumericBranchEvidence`, which carries
the exact source hash and target binding. The adapter removes a condition only
when primitive local evaluation is pure and total. Other conditions execute once
in their original position; condition comments remain, and comments elsewhere in
the branch retain the original source. Calls, reads, division and remainder in
the RHS remain conservative boundaries. Source-control estimates explicitly add
one branch unit to the scalar estimate; they are separate from the numeric proof
and are not runtime measurements. Discovery, parsing, extraction and both
independent proof allowances debit the existing file budget.
The replay can subsume a fully contained, already verified scalar edit and select
the simpler original RHS. Its prior search work remains charged; protected
reservations and source/scheduling plans cannot be subsumed. This lets ordinary
scalar simplification and whole-branch realization cooperate without overlapping
edits or replaying a removed expression context.

The optional `SwtCurrentSourceRefinementTest` Maven/JUnit gate uses the reusable
pinned JGit fixture and verifies that the exact upstream checkout matches the
hash-pinned SWT 3.136.0 source archive. It reports ordinary whole-file results and
`ImageData.getPixel` selection results separately. Original and ordinary generated
complete classes are compiled and loaded for pixel boundaries, JPEG DCT calls,
PNG Paeth random and aliased rows, and padding conversion exceptional/identity
cases. The existing SWT 3.135.0 default QA baseline remains unchanged.

Run that explicit network corpus gate with Java 25:

```sh
./mvnw -f qa/math-optimization/pom.xml -Dtest=SwtCurrentSourceRefinementTest -Dsandbox.math.swt.current=true test
```

The gate writes a detailed receipt and an unpublished unified upstream patch to
`qa/math-optimization/target/swt-current-patch/`; JGit applies the patch to the
verified original sources and checks the complete generated files again.

## Source-proved fixed powers

`JavaFixedPowerSource` recognizes a resolved right-to-left bit loop, including
its negative-exponent prefix and final unused square. The actual source supplies
multiplication, square, identity and local bindings. Method names do not supply
semantics. Multiplication and square must allocate fresh source values, and
`JavaValueAlgebraProof` separately admits a total canonical residue domain and
asks Regelsuche to prove associativity, both identity laws and square consistency.
The synthetic law trace shares expression nodes; the original Java execution
trace remains authoritative for source effects and totality, including dead work.
A source identity must itself belong to the canonical domain. Conditional source
constants never imply completed class initialization.

`JavaFixedPowerConstant` interprets a bounded fragment of exact BigInteger source
constants, including final fields assigned by a static block with an exact
quotient/remainder check. It executes only enumerated, bounded BigInteger arithmetic
on known values; analyzed Java is never invoked. Cycle, depth, magnitude, statement,
request work and deadline bounds apply. Zero, one, negative and unavailable fixed
exponents preserve their source behavior.

`JavaFixedPowerOptimizer` admits simple local/parameter receivers and same-caller
static final exponent fields. It groups accepted calls in one caller-type envelope,
adds private static chain helpers and retains each original call verbatim as the
fallback of a conditional. Guards establish nonnull nested links, exact BigInteger
classes, bounded canonical components against the actual source modulus, the fixed
exponent and source-defined constants. Structural private-field paths are never
Java projections: every link requires a source-proved accessible getter with fresh
binding/modifier agreement. Original null, subclass, noncanonical and changed
constant behavior runs through the fallback.

Regelsuche searches and independently replays the fixed-power plan. Complete
emitted source is compiled, each generated helper is decoded independently back to
the plan through resolved bindings, and that emitted plan is independently checked.
`FixedPowerEvidence` records this source schedule with compact bindings to all
four scalar law proofs; full scalar expression DAGs remain request-local and are
not expanded into project-scan evidence JSON. Combined-source validation binds the whole generated envelope and replays
its helpers under the original request meter and deadline; dependency receipts
remain part of Apply validation. The envelope rejects overlapping later edits.
Source algebra admission also requires every summarized method and static-field
owner to belong to the existing guarded receiver/component type tree. A pure
foreign helper body does not establish that its class has initialized: such a
helper or constant rejects the fixed-power rewrite, preserving the original
initialization order and failures even when the selected plan uses only squares.
Source scalar operations, guard expressions and emitted locals contribute to the
reported estimate; these estimates are not runtime measurements. Defaults are
unchanged. Qualification of a twelve-component source algebra explicitly selects
20 million work, 20 thousand states and the existing five-second deadline.

The explicit mathematical contract is documented on each enclosing method within
the verified caller-type envelope. It permits BigInteger component-reference
identity changes while preserving a fresh wrapper and guarded original fallback.
Calls already inside conditional expressions remain unchanged; repeated cleanup
therefore cannot wrap its own retained power fallback in another schedule.
The source receipt also retains the original structural call location and exact
receiver, exponent and power-method bindings. Independent replay checks the guard,
original fallback, helper invocation and its sole use at that location; a rebound
type-envelope hash cannot authorize an altered or additional call site.
