# Mathematics cleanup source adapter

## Current implementation and qualification

The [2026-10-27 qualification](../docs/superpowers/plans/2026-10-27-typed-computation-qualification.md)
records the exact SDK artifact, complete headless tests, matched BC corpus results
and pending native integration gates. These changes are on the feature branch;
they are not yet merged or released.

Regelsuche now proves generic high/low word reconstruction and preserves shared
computation graphs through transport and search. Sandbox supplies Java semantics
and source effects. The [architecture](ARCHITECTURE.md#complete-computation-tasks-and-cas-ownership)
describes the ownership split and the future configurable CAS boundary.

`JavaComputationExtractor.extractTasks` exports bounded straight-line primitive
integral tasks under `PRESERVE_JAVA`, including local versions, fresh reads and
ordered commits/stores. The scanner's [optional `--tasks` mode](../qa/math-optimization/README.md)
exposes summaries with `editable: false`. Whole-task inspection does not authorize
cross-store edits; ordinary cleanup retains its existing proof and source gates.
Configurable SymPy selection and full Nat256-addition optimization are not available
through this increment.

## BigInteger magnitude and modular regions

An effectively final `BigInteger` created before a region by `BigInteger(byte[])`
or `BigInteger(int, byte[])` has a magnitude bound of eight times the captured
array length. The adapter proves that length only for direct byte-array
allocations or effectively final local references to them, up to 512 bytes.
Constant dimensions use the existing binding-origin checks. Content mutation
does not change the length; reassigned references, fields, unproved dimensions,
other constructor overloads and anonymous subclasses retain receiver guards.
The allocation, contents and constructor stay in their original source position.
A magnitude bound supplies no sign or modulus-positivity premise.

In mathematical mode, a failed combined BigInteger request can retry strictly
smaller source statements within the shared file budget. Initial-validation
preparation failures debit the SDK's reported safe work bound, including earlier
trace work. Unknown unsupported outcomes reserve their allocation and any bounded
unreported preparation. Completed search-limit outcomes debit reported work only
when the SDK certifies complete accounting; partial stops reserve the allocation
or reported overshoot, whichever is greater. Regelsuche owns modular candidates and independent arithmetic proof.
The adapter still verifies emitted Java and requires an estimated runtime saving
after all receiver checks and fallback costs.

The explanation API's reported presentation work excludes its fresh verification
work. A complete explanation-call ledger remains a follow-up; the file allowance
must not be interpreted as accounting for every executed SDK operation.

## Computations across source helper calls

The runtime source adapter can expand statically resolved private static helpers in the same declaring type. Supported helper bodies contain integral parameters/locals (`byte`, `short`, `char`, `int`, `long`), straight-line initialized local declarations and assignments, and a final value return. With explicit mathematical opt-in, parameters, locals and the returned value can also be `BigInteger`. Nested and overloaded calls use JDT bindings and isolated value frames. No helper name, class name, formula or requested mathematical target selects applicability: the existing Regelsuche SDK searches the resulting original typed graph.

Every argument is translated before the callee body, including unused arguments. Dead callee operations remain in the original evaluation trace. Parameter conversions, compound-assignment narrowing and return conversions use the same decoder as ordinary source statements. Helper definitions and their comments remain unchanged when only the caller is selected. Purely unfolding a method is not itself a measured performance improvement.

BigInteger helper arguments acquire their guards and assumptions in the caller's
scope before the helper frame is entered. Parameter and local names inside the
helper only refer to these already translated values. The bound JDK constants
`BigInteger.ZERO`, `ONE`, `TWO` and `TEN` are supported inside helpers. A failed
guard executes the original caller region, including its original helper calls;
null inputs, subclass dispatch and values outside the guarded magnitude retain
that path. Accepted mathematical replacements may change result reference
identity, as documented by the existing explicit opt-in contract.

For example, a private static helper returning `value.modPow(BigInteger.TWO,
modulus)` can contribute its original modular-power graph to the caller's search.
Regelsuche can then propose a verified modular multiplication. Algebraic
cancellations can also cross a helper boundary. The runtime goal still charges
for guards and rejects proposals without a predicted benefit.

State/field access other than the listed JDK constants, synchronization, native or dynamic dispatch, other declaring classes, recursive cycles, unsupported control flow and unsupported numeric helper expressions are rejected. Source expansion is bounded to 16 nested frames, 64 statements per helper and 256 expanded statements per source region, in addition to the existing expression-depth bound. These are Java translation limits, not a catalog of mathematical problems. Deliberately authored computed constants inside helpers are protected by the same source policy; calls cannot reopen constant-folding-only demos.

Each numeric kind used by helper parameters and calculations must be selected.
A helper taking an `int` exponent therefore requires `INT` as well as
`BIG_INTEGER`. The existing restriction on dynamic `BigInteger.valueOf` primitive
precomputation remains in place.

The original Bouncy Castle constructor's general loop/method/object contracts are not inferred by this helper adapter. Its removed dedicated recognizer remains removed. Floating-point helper bodies, arbitrary loops and external library contracts require additional general semantic support.

The [historical 2026-10-11 helper qualification](../qa/math-optimization/results/2026-10-11-biginteger-helpers/README.md)
records that increment's effect: two additional regions reached analysis, while
accepted replacements remained 104 in strict mode and 114 in mathematical mode.
These are historical counts; use the current qualification above for this branch.

## Shared integral domain analysis

The mathematical opt-in delegates affine input-range analysis to the pinned
Regelsuche SDK. The Java adapter passes its cancellation token and renders the
separately verified original/candidate intervals in the existing local-range
Javadoc contract. Unsupported nonlinear, bitwise, multi-input and inexact forms
remain outside this range analysis; no caller premise is inferred from it.

The pinned SDK also admits existing algebra rules whose premises follow from
established typed facts, and bounded literal `BigInteger.shiftLeft(int)` terms in
modular search. Java kinds, promotions, source trace and overflow policy remain
explicit. Both capabilities are generic SDK features, not Java-source-name rules.

## Practical review and established source guards

The headless JSON report exposes an additive `reviewCandidates` list, keeping
advisory benefit/diff/guard assessments separate from verified edits. Complete
binding-checked guards in enclosing true branches can remove duplicate receiver
checks without changing the numerical premises or explicit opt-in boundaries.
See [practical review and repeatable BC/SWT profiles](../qa/math-optimization/PRACTICAL_REVIEW.md)
for the supported fragment, remaining limits and exact archive-run commands.
