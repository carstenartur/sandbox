# Java computation contexts

The mathematics cleanup exposes bounded Java calculations to the existing
Regelsuche search and independent numerical verifier. Sandbox checks types,
bindings, execution order and source edits. Select every primitive kind used by
a calculation. New expression-only callable/branch/predicate contexts admit
primitive integral calculations under `PRESERVE_JAVA` with mathematical opt-in
off; stream fusion and loop motion use that same strict integral contract.
Established block-statement extraction keeps its existing profile admission;
block-lambda return typing now uses the nearest callable rather than the creator
method. Ordered read captures retain their existing `PRESERVE_JAVA` contract,
including mathematical opt-in.

## Coverage and composition boundaries

| Context | Delivered fragment | Boundary |
|---|---|---|
| Lambdas | Numeric block/expression lambdas, nested callables and two-argument reducers use the nearest resolved SAM return type. | Expression-only replacements retain the original expression type and introduce no statements, captures or guards. Target functional types and enclosing overloads are rechecked. |
| Branches and predicates | Conditional arms, switch yields, unbraced returns/assignment RHSs and numeric comparison operands in primitive predicates are edited in place. | Conditions, selectors, boolean short-circuit positions and control flow remain authored Java; no general CFG/phi analysis. |
| Removable source calls | Same-class private static helpers and exact private/final instance methods or final declaring classes; instance receiver is implicit or explicit unqualified `this`. | Straight-line scalar bodies only; no state reads, recursion, synchronization, native bodies or uncertain dispatch. Existing BigInteger helpers retain their separate explicit mathematical contract. |
| Retained source calls | Exact-dispatch calls on simple local/parameter receivers and source-defined static helpers; original invocation and effects remain. | Receiver/null checks, arguments and class initialization execute in place. Mutable helper state remains unsupported. |
| Primitive method references | Retained invocation of a dominated final/effectively final local initialized directly with a type-qualified static or bound exact instance reference. | Exact primitive JDK SAM/target signatures; no adaptation, alias, field, captured-callable, unbound/super/constructor or generic/varargs expansion. |
| Primitive streams | Inspect Int/LongStream numeric terminals separately from converter rendering; fuse adjacent expression-map kernels on known sequential ordered standard sources. | Same primitive width, total local arithmetic, unchanged terminal; no filter/peek/mode/unordered stages, arbitrary factory/proxy, block-map, read/call or floating fusion. |
| Counted loops | Move one complete direct initializer/RHS invariant expression per canonical fixed-count loop into a fresh final primitive local. | Exact count at least two, Block insertion/body, total integral local arithmetic and unchanged controls. No moved reads, calls, unboxing, division, recurrence or floating work. |
| Ordered source reads | Complete nested integral array loads, nonvolatile field chains, eligible zero-argument local-receiver accessors, exact integral-wrapper unboxing and indexed integral buffer reads. | Existing straight-line capture positions only; every occurrence executes once in order, including dead/repeated reads. No heap equality or purity inference. |

These are independently admitted fragments, not arbitrary combinations. In
particular, expression-only lambda/branch sites cannot acquire capture statements,
and loop-moved expressions cannot contain reads, calls or unboxing. New captures
nested in retained helper/SAM arguments are rejected (`RETAINED_CALL_READ_ARGUMENT`)
to prevent duplicate execution. See [ordered input details](FIELD_ARRAY_INPUTS.md).

The exact SAM allowlist is `IntBinaryOperator`, `LongBinaryOperator`,
`IntUnaryOperator`, `LongUnaryOperator`, `IntSupplier`, `LongSupplier`,
`IntToLongFunction` and `LongToIntFunction`. Target and SAM primitive parameter
and return types must match exactly. The reference declaration must dominate the
invocation within an enclosing block without crossing a callable/type boundary.

Stream topology admission traces the receiver to resolved JDK `IntStream` or
`LongStream` `range`, `rangeClosed`, `of`, `empty`, or primitive `Arrays.stream`.
Numeric inspection also describes unknown sources without authorizing fusion or
stream-to-loop conversion. Sum/reduce/min/max terminals, reduction identities and
optional/empty behavior retain their original source.

Loop controls cover ascending/descending exclusive-bound int `for`, canonical
`while`/`do` with an adjacent counter initializer and final body update, and
one-dimensional primitive enhanced-for arrays of proved fixed length. Counts
must not wrap. Branches, abrupt flow and nested loops exclude outer-loop motion;
an inner loop may independently qualify at its own insertion point. Inputs must
be declared before insertion and unwritten throughout the loop. Comments inside
a candidate and selections not covering the whole edit envelope exclude motion.

## Small examples

An expression callback can simplify in place while keeping its expression type:

```java
// Before
IntUnaryOperator next = x -> (x + 1) - 1;
// Eligible result
IntUnaryOperator next = x -> (int) (x);
```

A retained primitive reference can expose numerical relationships without removing
its call or store. For a supported source-defined `Helper.add(int,int)`:

```java
IntBinaryOperator f = Helper::add;
int a = f.applyAsInt(x, y);
out[0] = a;
return a - x; // May become return y; invocation and store remain.
```

A known sequential map pair can expose one per-element kernel:

```java
IntStream.range(0, count).map(x -> x + 1).map(y -> y - 1).sum();
// May become one map with the verified kernel; sum() is unchanged.
```

For a sufficiently profitable invariant `(n + 7) * (n - 7)` in an eligible
8-trip loop, the adapter can insert a final temporary before the loop and replace
one RHS with that temporary. It preserves original int overflow and the loop
header/body schedule. It is not a recurrence closed form.

## Proof, scheduling and cost

Numerical equivalence is proved by the unchanged SDK and independently checked
against emitted Java. Loop scheduling has a separate adapter receipt and replay:
exact controls/count, original input declarations and no-write facts, source
anchors, temporary type/use and unchanged loop bytes outside the substitution.
The SDK proves one numerical evaluation, not the loop schedule.

For N iterations and scalar work W, the loop model charges NW before and
W + 1 + N after, including a store and each temporary read, plus one retained
primitive value under the selected goal. The 8-trip, 3-operation example is
24 -> 12 work units. Strict operation and weighted-cost gain are required.
Retained array effects and enhanced-array iteration use `NORMAL_LOOP_COMPLETION`:
the count/saving is conditional on normal completion because null, bounds or
allocation failures can stop earlier. Wholly total bodies use `TOTAL_BODY`.
Neither receipt is a timing measurement.

Established primary regions/retries run first, then deferred assignments, stores,
richer read regions, source tasks/expression contexts, and stream/loop passes.
They share the actual remaining file allowance and cancellation. Source-task
search refunds unused work only on the no-candidate path; unsuccessful candidate
realization retains its conservative reservation. Richer reads can consume work
otherwise available to later optional passes. Fresh source/dependency receipts,
combined binding checks, full generated-source validation and exact Apply/Undo
remain mandatory.

General recurrences, arbitrary virtual numerical summaries, heap/alias analysis,
BigDecimal scale/rounding and general floating motion need additional proof
models. Configurable CAS remains upstream. See the [current qualification](../docs/superpowers/plans/2026-10-31-java-mathematics-context-qualification.md)
for exact commits, matched corpus evidence and pending integration gates.
