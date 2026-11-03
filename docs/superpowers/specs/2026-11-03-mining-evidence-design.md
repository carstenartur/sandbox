# A bounded first improvement to mining quality

The user authorizes a cautious experiment while retaining Gemini free-tier
budget limits. This iteration adds no model calls. It tests stronger local
admission and provenance checks; it does not claim improved model quality or
general semantic equivalence without a later measured live comparison.

## Approaches

1. Prompt alone: cheap, but does not enforce the response contract.
2. Shorter, more specific guidance plus local response/diff checks: chosen.
3. A second model review: deferred because it adds requests/tokens and the
   existing retry counter does not yet count every HTTP attempt.

New positive responses must explicitly allow a cleanup, report confidence in
[0.80, 1], and give short original before/after spans and a file path. Missing or
contradictory decisions finish as non-candidates rather than triggering retries.
JGit checks both spans in changed lines of one hunk. JDT tokenization checks that
concrete replacement literals occur in the original after span, catching the
invented exception-message case even when the quoted hunk is correct. This is
evidence consistency, not complete AST replay or a proof of safe generalization.

Persist the selected hunk locally, preserving Java source version. New staging,
including replay from saved evaluations, must satisfy the evidence check.
Existing candidate decisions remain intact. The issue shows original evidence
separately from generated examples and describes the verifier's actual scope.

Gemini's existing 18-request default counts every attempted HTTP request,
including retries; it remains a client-lifetime limit, not a project-wide quota.
No provider/model change, additional API request, or live mining run is needed.

PR1795's reported legacy migration defect is fixed independently with
a raw schema-v1 regression. Java 25, existing Maven/JUnit/JGit/JDT dependencies,
ordinary PR against main, and the repository's review-size policy apply.
