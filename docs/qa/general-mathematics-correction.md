# Correction: general mathematics, not showcase recognizers

## Architecture contract
Sandbox processes Java: bindings, types, original evaluation traces, data dependencies and required outputs. Regelsuche processes the actual mathematical graph, searches cheaper equivalent representations and independently checks their semantics. The SDK is a general boundary, not a catalog of prepared demonstration problems.

The general core algebra catalog is now connected to the Java optimization SDK through its existing JointPlanSearch. Typed Java arithmetic is not silently converted to real-number arithmetic: division, casts, floating point, exact operations and exceptional behavior remain protected by the independent checker.

## Withdrawn work
The earlier downloadable `constructor-modular-provenance` package is withdrawn as the product implementation direction. Its DivisibilityLoopProof, ConstructorModularFacts and ConstructorModularReview source recognizers supply parts of a known solution. They are absent from this branch and must not be imported from the old combined patch. Keeping them under different names, moving them to Regelsuche or matching variable-renamed examples does not satisfy the requirement.

The historical Bouncy Castle constructor remains a desired acceptance input, not a production selector. This correction does not claim generic loop invariant inference. Unsupported control-flow or method-summary obligations must be reported instead of fabricated or inferred from comments.

## Acceptance
GeneralMathematicsRoundTripTest reads ordinary Java, invokes the real SDK through MathematicalAnalysis, compiles both versions, compares overflow-boundary and seeded inputs, and verifies exact Undo. It covers a genuinely cheaper nonconstant result across one and multiple statements, renamed/grouped forms, and the exclusion of constant-only edits. It supplies no mathematical task name and no pre-solved exponent input.

The source-bound SDK distribution, all mathematical tests and the unchanged proof/exception checks must pass before this integration is described as qualified. No screenshot, measured speedup, p2 qualification, or general control-flow support is implied by a module pass.
