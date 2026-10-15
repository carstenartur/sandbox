# Source-backed BigInteger rewrites

This implements the first practical-review milestone: a verified mathematical
candidate can use a bounded chain of source-defined constants and preserve the
original Java spelling. It does not add optimizer rules or increase search limits.
The Regelsuche SDK and its independent arithmetic and emitted-Java checks remain
unchanged. Only the synthetic emitted-Java verifier imports the original JDK
`BigInteger` spelling, before running all its existing checks.

## Supported source facts

The request-local `JavaSourceContext` follows resolved Java field bindings that
are already admitted as own-class, static final BigInteger inputs. Definitions
may refer to other static final fields, JDK constants, a direct BigInteger string
constructor (with an optional literal radix), or `BigInteger.valueOf` of a literal.
Only this supported fragment is evaluated; project methods and constructors are
never executed to learn their values. A different type or an unresolved binding
is not treated as a JDK operation because it has a familiar name.

A Java static alias can observe null during class initialization. Therefore the
resolver first establishes only "this supported value, or null". A guard is removed
only when an original, completed non-null use dominates the entire calculation.
Supported observations include a direct receiver use and `compareTo` on a proved
exact, unmodified local BigInteger construction. A skipped short-circuit operand,
caught exception or supplied subclass is not such a proof. A positive modulus
and the complete magnitude/type premises must still hold. An unavailable fact
retains the original guard or leaves the calculation unchanged.

The IDE reader uses actual source compilation-unit handles, not attached source
for a binary JAR. The archive reader uses the supplied source roots and the same
binding-based evaluator. The latter currently requires the conventional top-level
qualified-name-to-`.java` layout; unusual source layouts remain conservative.
There is no general method, alias, loop or class-initialization analysis here.

The context admits at most 24 consulted units, depth 32, and one million directly
consulted source characters. Its work comes from at most an eighth of the file
allowance (capped at 32,000); unsupported or interrupted discovery yields no new
fact. JDT parsing still uses the platform parser and cancellation mechanism.
No context or AST is put into the Regelsuche graph or retained by a validation
callback after the request. Local validation callbacks retain source handles and
lookup descriptions only.

## Source freshness

Each consulted source has an identity and a SHA-256 receipt. Before returning or
creating an edit, the analysis rechecks those sources. A deletion, content change,
or a newly competing file on another configured source root invalidates the edit.
Normal project/classpath validation remains a caller responsibility as before;
this source checkpoint supplements it, rather than replacing it.

Receipts are report data, not authority to apply a saved proposal. Deserialized
source-dependent analyses have a detached, non-applicable checkpoint and require
fresh analysis. An empty historical analysis can still be read. The additional
record constructors retain source compatibility with existing in-process callers.

The Quick Assist worker also rechecks this checkpoint before reusing cached
analysis. A dependency initializer can change without a different binding
environment; in that case the old proposal is rejected and the worker recomputes
it, rather than repeatedly returning a stale cache entry.

## Preserving Java presentation

`JavaOriginalExpressions` uses the original, binding-checked factory expression
for generated BigInteger literals when suitable. It keeps compatible method-call
syntax, line breaks and arguments, only changing the operations in the verified
target. The normalized result passes the existing independent emitted-Java checks.
It does not infer an algebraic rule from the source text.

The JDT rewriter still edits exact verified ranges. For a preserved multiline
initializer, a bounded initializer-only text edit avoids reformatting its original
continuation indentation. Grouped source-slice edits and their pinned statements
are otherwise unchanged. The existing conservative comment policy remains: a
commented expression may be declined, but a comment must not silently disappear.

## Archive scanner and review export

`ProjectMathScan` materializes a shared UTF-8 source context, so a selected file
can refer to other source files in its archive. Only selected files count as
analyzed. Per-file, total-byte and file-count bounds apply to context extraction;
consulted definition receipts and the final complete source snapshot are checked
against the original archive. Additional or changed snapshot files fail the run.
Original binary/archive/profile/runtime fingerprints remain in the report.

`generated/` is the normal Apply result, including its existing mathematical
contract documentation. `review-only/` contains the same verified calculation
edits with only the separate automatic documentation edits removed. It is an
explicitly labeled **manual upstream review artifact**, not the normal Apply
result and not permission to ignore a changed numerical contract. The report
retains the omitted documentation, assumptions, proof and timing limitations.

Use the existing mathematical profile and set
`cleanup.mathematics.explanations=NONE` for a compact calculation review. This
only controls explanation blocks; ordinary Apply still retains its mathematical
contract documentation. Defaults and opt-in requirements have not changed.

For the frozen Bouncy Castle 1.85.2 serialization source, the calculated review
change is exactly:

```java
// Before
BigInteger ySquared = x.multiply(x).mod(P).multiply(x).mod(P)
    .add(BigInteger.valueOf(4)).mod(P);

// Calculation review
BigInteger ySquared = x.multiply(x).multiply(x)
    .add(BigInteger.valueOf(4)).mod(P);
```

The chain `P -> Fp2Element.P -> BLS12_381G1.Q` is ordinary source data, not a
recognized project/class/formula pattern. An original `x.compareTo(P)` and the
local BigInteger construction establish the needed non-null observation. The
full review class matches the previously hand-refined class byte-for-byte.
This is a quality improvement of an existing mathematical discovery, not a new
optimization family or a newly measured speedup.

## Qualification and remaining scope

The accompanying tests cover source resolution, guarded failures, cycles,
mutability, class-initialization null alternatives, shadowing, stale/deleted
sources, JSON transport, context lifecycle, formatting, actual pinned BC source,
compilation of the resulting full class, and real archive-scanner operation.
Existing source-slice tests still cover grouped Apply/Undo and pinned effects.

Standalone Java 25 execution and full-function differential checks qualify the
source overlay. They do not substitute for the full Maven/Tycho, SpotBugs,
Workbench or three-platform distribution gates; those remain required before
merge. The project-source reader and LTK preview/apply/Undo lifecycle also pass
the dedicated workspace test in a reconstructed headless Eclipse runtime with
freshly compiled mathematics classes. The same test is included in the ordinary
native test module; the complete Tycho build and CI remain separate gates.

The later roadmap packages are not implemented here: an SDK protocol for open
proof obligations, multiple independently verified candidates, size-calibrated
costs, additional modular/bitwise optimization families, and a redesigned detailed
explanation UI. There is no full-archive new hit total or performance benchmark
in this change.
