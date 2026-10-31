# Ordered field, array and boxed inputs

Under `PRESERVE_JAVA`, including explicit mathematical opt-in, the adapter can
capture complete nested primitive integral array loads, including array fields
behind local-rooted nonvolatile field chains. Primitive fields may be lexical,
unqualified `this`, type-qualified or local-rooted chains. Values and links may
be mutable: every occurrence evaluates the whole original expression again.
Volatile at any traversed link is excluded.

Zero-argument numeric instance calls on local/parameter receivers may be opaque
ordered inputs, even if virtual, impure, synchronized or throwing. The original
call executes; no purity or numerical method summary is inferred. Existing
scalar helper, retained SAM and known-operation summaries take priority.
Static/generic/parameterized/varargs accessors, field-chain accessor receivers,
effectful receivers and relative buffer `get()` remain excluded. Absolute indexed
`get(int)` on integral NIO buffers retains its existing admission.

Actual Java unboxing of exact `Byte`, `Short`, `Character`, `Integer` and `Long`
local/field/array/accessor values is captured with its primitive result kind before
promotion. Eligible explicit wrapper conversions retain the bound original call.
Boxed reference outputs/identity, floating wrappers, arbitrary `Number` and
BigDecimal are not assigned integral semantics.

```java
final int _mathRead = holder.rows[row][column];
final int _mathRead2 = counter.next();
```

These illustrate separate complete source occurrences, not shared heap values.
Every original read/call/unboxing executes exactly once in order, including dead
or repeated text, receiver dereferences, null/bounds checks and class initialization.
Only captured numbers enter numerical search. No alias equality is inferred.

Receiver/index recursion and capture count are bounded at 64. Indices allow
primitive integral locals, compiler constants, literals, parentheses, integral
casts and total unary/arithmetic/bitwise/shift expressions. Calls, mutation,
mutable field indices, nested index reads, division and remainder are excluded.
Authored index formulas retain their source spelling.

A read depending on a region-produced local starts a new region. Earlier throwing
arithmetic, observable local commits, writes and control flow keep their boundaries.
New captures nested in retained helper/SAM arguments are rejected to prevent
executing an occurrence both as a capture and in the retained call.
Expression-only callable/branch sites cannot introduce captures; loop-moved
expressions exclude reads, calls and unboxing. Checked/fallback profiles and
helper-body memory reads remain outside this capture contract.

The independent numerical/emission proof, source receipts, output verification,
capture store/reference/storage costs, cancellation and finite budgets remain
mandatory. Established first-pass windows run before deferred assignments,
stores and richer read regions; later tasks/contexts/stream/loop work uses the
same remaining allowance. See [Java contexts](JAVA_CONTEXTS.md) and the
[current qualification](../docs/superpowers/plans/2026-10-31-java-mathematics-context-qualification.md).

## Tests and evidence

`PreservedArraySourceTest` retains the initial five regression requirements.
`FieldArrayOrderingTest` adds class-initialization ordering, repeated/dead reads,
receiver reassignment, observable writes and aliases. `JavaExtendedSourceReadTest`
adds compiled nested/object/accessor/unboxing ordering, dead calls, mutation,
null/bounds precedence and retained-call argument rejection.

`FieldArrayCorpusTest` processes complete pinned Bouncy Castle 1.85.2 HC128,
CAST5 and Keccak classes using one million work units, twenty thousand states,
the existing five-second invocation limit, Java 8, PRESERVE_JAVA and the runtime
goal. It records hashes, normalized options, admitted field-read counts,
accepted changes, independent proof evidence, diagnostics, complete compilation
and byte-exact Undo under the existing test-report artifact directory.

An admitted input is not an optimization hit. No accepted change is fabricated
when a class produces zero. These reports are not a cipher-wide execution test,
a constant-time qualification, an allocation benchmark or a measured speedup.
Inspect exact-head Maven/JUnit evidence before declaring this increment verified.
