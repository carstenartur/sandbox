# Runtime Bouncy Castle mathematics cleanup

## Accepted scope

The requested product demo and potential upstream patches must come from unchanged, pinned Bouncy Castle production source processed by the registered cleanup. Pure constant evaluation is not an optimization deliverable: preserve the programmer's constant formula and names. Runtime-dependent cancellations may still yield constants.

## Implementation and acceptance

1. Add general int/long choice and common-mask proposals in Regelsuche, independently reverified by the existing bitvector authority. Include commuted operands, majority expressions, sign-bit boundaries and negative controls. Observe failing tests before production changes. No branch/table or numerical contract relaxation.
2. Select runtime calculations in Sandbox before search. Preserve standalone constant statements and named constant inputs. Keep the complete original trace and exact output contract. Optimize only local primitive regions inside loop bodies; do not rewrite loop control or external writes. Check compile, equivalence, idempotence and byte-exact Undo.
3. Build a clean, source-bound SDK distribution twice using fixed timestamps, compare artifacts, and qualify two fresh consumers. Do not relabel earlier distributions or claim a public release.
4. Process complete pinned production files with real bindings. Export originals, generated source, unified patches, source identities and diagnostics. Require genuine runtime changes for positive cases and unchanged results for already optimized/constant-only controls. Compare complete digest computations and known-answer vectors; samples supplement the independent proof rather than replace it.
5. Generate genuine Eclipse Clean Up previews from those same inputs and outputs, check Apply and Undo, and replace the constant-only Help demo. Keep estimates distinct from measured throughput and from constant-time review. Do not submit upstream until generated changes are reviewed and performance claims measured.

## Constraints

Keep verification, test coverage and workflow policies intact. Use Java 25, the repository Maven Wrapper and existing Tycho test ownership. Temporary diagnostic execution lives on isolated branches, not in the product workflow tree. Preserve unknown effects, exceptions, floating-point restrictions, user comments, and cryptographic control flow. An unsupported source region must remain unchanged with a diagnostic, not vanish from the report.

## Review focus

Mixed constant/runtime regions; overlapping outputs and live locals at effect boundaries; loop-local vs loop-carried values; complement masks with different selectors; current source provenance and reproducible native previews. A shorter source expression alone is not a measured machine-code improvement.
