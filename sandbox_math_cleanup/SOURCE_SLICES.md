# Source-bound computation slices

This is the first implemented source-integration stage of the mathematics roadmap.
It extends the existing Regelsuche request path, not the mathematical rule inventory.
The SDK remains unchanged and Eclipse-independent.

## Supported example

```java
int left = a * b;
audit(); // unchanged, and still allowed to throw
int right = a * c;
return left + right;
```

For stable primitive integral locals/parameters, the original typed plans and
source occurrence traces can be composed into one search request. Regelsuche may
factor the two products. The source adapter can remove the dead local declarations
and replace the return expression while leaving `audit()` and its comment intact.
The mathematical identity is found and independently checked by Regelsuche; no
method name or formula in this example enables extraction. Java wraparound,
promotions and casts remain explicit. This does not imply a measured speedup.

## Three separate identities

- **Mathematical value:** the existing typed computation DAG and equality rules.
- **Source occurrence:** every original operation, including repeated/dead work,
  keeps its original `SourceEvaluationTrace.sourceId` and declared/evaluated kind.
- **Java anchor:** a source-bound syntax descriptor records its node type, range,
  binding key where available and text digest. Occurrences reference one or more
  anchors; source identity never participates in mathematical equality.

`JavaSourceRewritePlan` retains immutable data, not `ASTNode` objects. `resolve`
reconstructs the adapter-local `SourceId -> ASTNode` map only against the exact
source version and matching bindings. The occurrence mapping validates the current
adapter's explicit `operation@offset:ordinal` scheme; unknown or unmapped IDs fail
closed. IDs are not durable across arbitrary source edits. The full source SHA-256,
length and existing project/compiler/classpath/profile checks prevent stale apply.

## Safe connection and emission

`JavaComputationSlices` composes the **original** extracted plans using resolved
Java binding keys. Equal spelling is not evidence that two variables are identical.
Only same-block declarations/returns involving effectively final primitive locals
can connect. All intermediate expressions must be total local arithmetic: no
fields, arrays, method calls, division, remainder, floating values or mutation.
Gaps must be resolved call expression statements without assignments, increments,
closures or references to values produced by the group. The gap call need not be
pure: the moved arithmetic cannot observe heap state, cannot throw an arithmetic
exception, and cannot be observed by the gap. No unknown memory alias is equated.

Search, independent numerical verification and emitted-Java verification remain
unchanged. The original outputs used outside the group stay live. A source plan
uses the actual JDT `ASTRewrite` leaves, not a textual diff guess. Every gap call
and every enclosed comment is pinned. Generated temporaries/notes are explicit
insertions. The plan reconstructs exactly the already checked replacement envelope;
its grouped edits are used by the existing `Analysis.newEdit()`/JDT change path.
Original and generated whole-file Java must still parse; Apply and Undo are exact.

## Bounds and compatibility

The first fragment connects at most four parts, 64 statements and 16,000 source
characters. Discovery is capped at the lesser of 65,536 work units and one sixteenth
of the file allowance; that work is deducted before search. Expanded substituted expression trees and trace multiplicity are bounded before
record hashing or SDK preparation. Original regions are retained when discovery
is incomplete. A joint request reserves part of its work
for local retries; there is no new search budget or workflow. One difficult slice
cannot authorize unproved changes or repeated side effects.

Connection is enabled only for whole-file `PRESERVE_JAVA` analysis with mathematical
opt-in off. The default BigInteger-only selection will not exercise this integral
fragment. Selection-only analysis keeps its previous boundaries. BigInteger motion,
checked/fallback modes, general branches/loops and interprocedural memory contracts
are not implemented by this stage. Existing ordinary regions remain available.

`VerifiedRegion` adds nullable `sourcePlan`; legacy constructors and JSON lacking
that field retain their previous behavior. `Diagnostic` adds a stage with a compatible
four-argument constructor; old JSON gets the known classification or `UNKNOWN`.
Stages distinguish source, search, ambiguous search/proof, proof, emission, cost,
budget, cancellation and accepted results. They do not pretend to split an ambiguous
SDK error. Existing original/replacement text, proof, cost and explanation remain.

## Validation and next stages

Regression coverage compares contiguous and disconnected input, duplicate source
occurrences, pinned calls/comments, source-staleness and JSON compatibility, partial
selection, unsupported side effects and exact Apply/Undo. Differential tests compile
and execute complete original/generated methods, including integer edge values,
random inputs and a throwing audit call. Corpus comparisons use the same frozen
BC/SWT inputs, SDK, profiles and limits; test counts alone are not corpus gains.

Follow-up work must be driven by retained real failure cases: richer program-point
facts, bounded alias/control-flow support, matching independent proof procedures,
and better cost/readability models. General SSA/MemorySSA, SMT/e-graph evaluation,
learned performance ranking and preserving authored constant formulas during general
normalization are **not** claimed complete here.
