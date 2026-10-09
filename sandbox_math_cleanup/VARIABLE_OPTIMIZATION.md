# Variable-dependent mathematics cleanup

Related work: Sandbox #1657, Sandbox PR #1676, Regelsuche PR #1075.

The Java adapter distinguishes observable value outputs from internal local
bindings. The original evaluation trace remains complete: removing a temporary
from the requested outputs must not remove an overflow or exception obligation.
A local referenced beyond the region, including beyond an effect boundary,
retains its declaration. Embedded declaration comments are kept conservatively.

Direct numeric return expressions participate in extraction. A return conversion
is applied after the original expression's actual Java operations: returning an
`int` multiplication as `long` does not turn that multiplication into `long`
arithmetic. Strict floating-point and BigInteger identity/dispatch restrictions
remain in force.

For example, the regression suite exercises unknown runtime `int` inputs in
`return x*a + x*b;`, as well as the same computation split over local variables.
It requires the emitted code to contain one multiplication, recompiles both
versions, compares boundary-value combinations including integer wraparound,
and checks exact undo. This is structural and correctness evidence, not a JVM
performance measurement or a claim about an unchanged upstream source file.

The plain emitter inlines input aliases and single-use expressions with explicit
grouping. Shared arithmetic retains a single temporary. The checked and guarded
execution traces are not inlined away. The independent Java emission verifier
requires exactly the requested output bindings and still checks the candidate,
the decoded operation trace, and equivalence to the original plan.

## Verification

Run with Java 25 and the repository Maven wrapper:

```sh
xvfb-run --auto-servernum ./mvnw --batch-mode --no-transfer-progress \
  -pl sandbox_target,sandbox_math_cleanup,sandbox_math_cleanup_test -am verify
```

The default mathematics module includes `MathVariableRegionsTest`, emission
mutation tests, conversion/overflow boundaries and the existing integration
regressions. The separate `swtbot` profile is required for native UI capture.

Mutation tests target the actual output bindings and expressions. They must
first accept an unmodified emission and verify that a mutation really changes
it. A missing former `_math` alias or a hard-coded obsolete `output0` identifier
is not evidence that a malicious computation was rejected.

## Still open in the parent issue

This repair does not finish #1657. BigInteger parameter fact inference and guard
cost qualification, stronger unchanged-production-corpus examples, refreshed
native Help screenshots, and complete product-level/normal PR qualification
remain separate acceptance gates. Do not replace the missing native evidence
with generated illustrations or present estimated cost reductions as measured
speedups. Cryptographic constant-time qualification remains an additional gate.
