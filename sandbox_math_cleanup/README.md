# Mathematics cleanup source adapter

For maintainers of numerical Java code and authors evaluating verified optimization,
this adapter makes a bounded calculation available to Regelsuche, checks the
emitted Java, and offers a source diff for review. You can work in Eclipse or
inspect source archives with the headless qualification tools.

Sandbox supplies Java types, bindings, evaluation order and source edits.
[Regelsuche](https://github.com/carstenartur/Regelsuche) owns mathematical
candidates, search and independent arithmetic proof.

## A source-backed example

The frozen Bouncy Castle 1.85.2 `BLS12_381Serialization` source supplies this
[recorded calculation review](SOURCE_BACKED_REWRITES.md#archive-scanner-and-review-export):

```java
// Before
BigInteger ySquared = x.multiply(x).mod(P).multiply(x).mod(P)
    .add(BigInteger.valueOf(4)).mod(P);

// Calculation review
BigInteger ySquared = x.multiply(x).multiply(x)
    .add(BigInteger.valueOf(4)).mod(P);
```

The adapter resolves the source-defined modulus through
`P -> Fp2Element.P -> BLS12_381G1.Q` and checks the required source facts.
Regelsuche verifies the arithmetic replacement; the adapter does not recognize
the project or formula by name. The [source-context regression](../sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/BouncyCastleSourceContextTest.java)
checks the exact rewrite, compilation of the resulting class, Apply/Undo and
rejection after a referenced source changes. It also checks that the available
definitions alone do not enable mathematical mode.

This example requires **explicit mathematical opt-in**. The excerpt shows the
calculation review; normal Apply retains the generated mathematical-contract
documentation, including the possible change to result reference identity.
The separate `review-only/` export is a manual review artifact. A verified
replacement and a lower estimated cost are not evidence of an actual runtime
speedup or cryptographic constant-time behavior.

## Try it and inspect the evidence

- **In Eclipse:** edit a cleanup profile under **Java → Code Style → Clean Up → Mathematics (Sandbox)**, choose the numeric types, safety profile, goal and budget, then run **Source → Clean Up** and inspect the ordinary file diff. Selection Quick Assist starts a background analysis; invoke it again after completion to obtain the proposal. See [configuration and preview](../sandbox_math_cleanup_help/html/cleanup.html#configure) and the [actual source/preview gallery](../sandbox_math_cleanup_help/html/examples.html).
- **From the command line:** use Java 25 and run the existing Maven qualification from the repository root:

  ```sh
  mvn -f qa/math-optimization/pom.xml clean verify -Pbc-corpus
  ```

  The [headless guide](../qa/math-optimization/README.md) explains the pinned inputs, strict/mathematical profiles, reports and original/generated source outputs. It also documents the separate complete-call benchmark profile and how to interpret inconclusive results.

## Semantics and current limits

The cleanup is disabled by default and initially selects BigInteger only.
Select primitive integral types to analyze those calculations; all declared and
promoted numeric kinds must be enabled. Strict Java behavior remains distinct
from the separate mathematical checkbox, which can permit changed integer
overflow behavior and BigInteger result identity. **Select All does not grant
that consent.** Review the [safety profiles](../sandbox_math_cleanup_help/html/cleanup.html#safety)
and generated contracts before accepting a change.

`JavaComputationExtractor.extractTasks` exports bounded straight-line primitive
integral tasks under `PRESERVE_JAVA`, including local versions, fresh reads and
ordered commits/stores. The scanner's [optional `--tasks` mode](../qa/math-optimization/README.md)
exposes summaries with `editable: false`. Ordinary cleanup now separately realizes
verified whole tasks by changing pure right-hand sides in place. Reads, helper
calls, intermediate assignments and stores retain their order, including on failure.
Recompilation, re-extraction and an independent proof gate each accepted change.
Static primitive helpers can be summarized from other source files while their
calls and class initialization remain in place. Source dependency edits invalidate
the patch. Configurable SymPy selection and full Nat256-addition optimization remain
outside this increment.

## Current implementation and qualification

The [2026-10-31 qualification](../docs/superpowers/plans/2026-10-31-java-mathematics-context-qualification.md)
records the exact local code revision, SDK, matched corpus inputs and integration
boundaries. The [Java context reference](JAVA_CONTEXTS.md) describes delivered
lambda/branch/predicate edits, exact instance/reference summaries, primitive map
fusion, counted-loop motion and ordered nested/object/boxed reads. Each fragment
has its own admission rules; expression-only edits cannot introduce captures and
loop-moved expressions exclude reads, calls and unboxing.

Matched Bouncy Castle 1.85.2 scans accepted **146 strict-Java regions** and
**147 mathematical-opt-in regions**, retaining all 120/129 baseline spans.
Both parsed 3,126 files with the same ten parse-error files. SWT 3.135.0
`ImageData` remained at zero accepted regions. The frozen mathematics suite
recorded 714 tests, zero failures/errors and one existing optional skip; native
core/math/test/help/feature packaging and SpotBugs passed. Native tests were
compiled, not executed in a workbench. Repository policy passed 12 tests at documentation checkpoint `13638a03`.
Final independent branch review approved draft integration without open findings.
The complete work is published in draft/reference PR #1772; its exact-head CI and
the required split before merge remain separate integration gates.
These are coverage and verification results, not measured runtime speedups.

Regelsuche owns arithmetic search and independent numerical proof. Sandbox owns
Java source effects and separately replayed scheduling evidence. The
[architecture](ARCHITECTURE.md#complete-computation-tasks-and-cas-ownership)
describes this split and the future configurable CAS boundary.

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

The runtime source adapter can expand private static helpers in the same declaring type and exact-dispatch primitive instance helpers with implicit/explicit unqualified `this`. Private/final methods or final declaring classes establish the bounded instance dispatch contract; see [the context reference](JAVA_CONTEXTS.md) for retained local receivers and primitive method references. Supported helper bodies contain integral parameters/locals (`byte`, `short`, `char`, `int`, `long`), straight-line initialized local declarations and assignments, and a final value return. With explicit mathematical opt-in, parameters, locals and the returned value can also be `BigInteger`. Nested and overloaded calls use JDT bindings and isolated value frames. No helper name, class name, formula or requested mathematical target selects applicability: the existing Regelsuche SDK searches the resulting original typed graph.

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

State/field access other than the listed JDK constants, synchronization, native or uncertain dispatch, removable helpers from other declaring classes, recursive cycles, unsupported control flow and unsupported numeric helper expressions are rejected. Source expansion is bounded to 16 nested frames, 64 statements per helper and 256 expanded statements per source region, in addition to the existing expression-depth bound. These are Java translation limits, not a catalog of mathematical problems. Deliberately authored computed constants inside helpers are protected by the same source policy; calls cannot reopen constant-folding-only demos.

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
