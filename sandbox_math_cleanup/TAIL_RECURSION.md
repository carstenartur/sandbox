# Source-derived tail recursion and ULR state transitions

## Scope

`StateIteration` describes bounded primitive state transfers independently of JDT
and of any client library. The Java inspector derives its bindings and operations
from source, not from a class, method, package, curve or constant allowlist.

The first producer admits a static `int`/`long` method with two statements:
a `counter <= 0` exit and a directly returned self-call that decrements that
counter by one. Parameters, integral literals, arithmetic without division, and
bitwise operations form the supported pure expression fragment. The counter may
be in any parameter position. Up to eight states are supported. Calls, mutable
reads, synchronization, recovered bindings and body comments fail closed.

## Planning and independent replay

`JavaTailRecursionRewrite.prepare` is an internal source-only entry point for
self-contained compilation units. It requires `ALLOW_FRAME_ELISION`; the strict
stack policy returns no plan. Source parsing, inspection, emission and independent
replay share a work allowance and cancellation. Input/output text is bounded to
one million characters. JDT parsing uses the platform cancellation mechanism;
this entry point does not claim an independent hard parser deadline.

The generated loop uses separate local state variables, preserving `final`
parameters. Every next-state expression is evaluated left-to-right using the old
state and its original primitive expression width. Only after all next-state
values exist are the states assigned. The same exit and base result are retained.

`JavaTailRecursionReplay` reparses the actual result and checks the method
signature/modifiers, local bindings, widths, condition, argument order, complete
state transfer and final result. It does not invoke the source renderer. It
rejects altered arithmetic, early writes, changed types and synchronization.

A plan stores immutable complete before/after source strings. `applyTo` and
`undo` reject changed input; they are source-snapshot operations, not a claim of
native Eclipse preview or file-buffer lifecycle integration.

## Observable contract and remaining work

Eliminating frames changes stack/resource behavior, including possible
`StackOverflowError` and stack observation. Numerical agreement is not proof of
unchanged full Java execution behavior. No cleanup checkbox, save action, Quick
Assist or default profile is registered by this slice.

General recursive calls, mutual/non-tail recursion, floating-point state,
value-object summaries, affine/matrix extraction and mathematical replacement of
the iteration are not implemented here. They must reuse the existing Regelsuche
models and independent proofs rather than interpreting arbitrary method names.

## Tests

The JUnit suites cover independent state recurrences, mixed primitive widths and
overflow, parameter/name conflicts, actual compilation/execution of generated
source, deep iteration after opt-in, stale snapshots, cancellation and adversarial
replay. The bounded model and original inspection suite are retained.

Supplemental local evidence: 36 tests passed under Java 21; all added Java sources
also compile with Eclipse Compiler target 25. This is not a Java 25 execution or
a full project build. The authoritative Java 25 Maven/Tycho, SpotBugs and native
repository gates must pass separately before integration.
