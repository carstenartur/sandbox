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

`JavaTailRecursionRewrite.prepare` supports standalone source and the resolved
AST of a native project. The standalone entry requires `ALLOW_FRAME_ELISION`;
the native entry requires the separate `cleanup.mathematics.tailRecursion=true`
option. Its default is false, including legacy constructors and imported profiles.
The current AST must match the complete source, including positions and comments.
Parsing, inspection, emission and replay are bounded by work and cancellation;
input/output text is limited to one million characters.

The generated loop uses separate local state variables, preserving `final`
parameters. Every next-state expression is evaluated left-to-right using the old
state and its original primitive expression width. Only after all next-state
values exist are the states assigned. The same exit and base result are retained.
For the strict Estimated Runtime profile, an independently checked counted-loop
form puts only the pure counter decrement in the updater; all other next-state
expressions still use the previous counter and previous state.

`JavaTailRecursionReplay` reparses the actual result and checks the method
signature/modifiers, local bindings, widths, condition, argument order, complete
state transfer and final result. It does not invoke the source renderer. It
rejects altered arithmetic, early writes, changed types and synchronization.

A plan stores immutable complete before/after source strings. `applyTo` and
`undo` reject changed input. The native coordinator uses these plans only in its
private working copy and supplies one ordinary Eclipse Preview/Apply/Undo.

## Native consent, phases and freshness

Eliminating frames changes stack/resource behavior, including possible
`StackOverflowError` and stack observation. Numerical agreement is not proof of
unchanged full Java execution behavior. Enable **Convert verified tail recursion
(accept changed stack behavior)** explicitly on the Mathematics page, select
the method's numeric kinds, then run whole-file **Source → Clean Up**. The method
Javadoc records the accepted frame contract even when ordinary explanations are
disabled. Select All, selection Quick Assist and save actions never supply it.

The coordinator checks up to eight candidate methods. Tail conversion precedes
ordinary mathematics. Transferred bodies are protected from subsequent unrelated
math and style conversion, including repeated Apply. Mathematics path exclusions
also exclude tail conversion. The independent, default-off Functional Converter
option may still convert eligible code elsewhere in an excluded file.

Only **Preserve Java**, mathematical mode off and **Lower estimated runtime**
permit the transferred loop to enter the existing finite-series/affine iteration
proofs. Mathematical mode with tail consent retains the verified ordinary loop.
The bundled Regelsuche SDK supplies the iteration models and proofs.

Tail inspection, reparsing, mathematical searches and optional conversion share
one work allowance and best-effort total deadline. Reservations, including failed
searches, are not refunded. Cancellation or exhausted preparation yields no
partial native change. Source, compiler, classpath, referenced definitions and
both effective profiles are checked again at Apply. Copied native edits retain
their source/profile guards and the primary buffer stays unchanged during Preview.

## Remaining boundaries

General recursive calls, mutual/non-tail recursion, floating-point state,
value-object recursive state and arbitrary control flow remain unsupported.
Only already admitted finite-series/affine forms can be accelerated; declining
that proof leaves the replayed loop. Numerical proof and lower estimated cost
do not establish a measured speedup or cryptographic constant-time behavior.

## Tests

The JUnit suites cover independent state recurrences, mixed primitive widths and
overflow, parameter/name conflicts, actual compilation/execution of generated
source, deep iteration after opt-in, stale snapshots, cancellation and adversarial
replay. Native regressions exercise both consents, real Preview/Apply/Undo,
multiple protected tail bodies, audited value reuse in the same transaction,
selection/default/save-action exclusions, changed profiles and aggregate budgets.
SDK and source qualification are recorded separately; historical test summaries
are not fresh qualification of reconstructed code.
