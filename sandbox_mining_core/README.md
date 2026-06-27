# Mining Candidate Pipeline

`MiningCli` stages mined DSL rules as JSON candidates in `mining-candidates/` instead of writing directly to productive bundled `.sandbox-hint` files.

## What is a candidate?

A candidate is a staged mining result (`MiningCandidate`) with:

- `dslRule`
- `beforeExample`
- `afterExample`
- `negativeExample`
- `targetHintFile`
- `sourceCommit`
- `sourceRepo`
- `status`

Each candidate is persisted as `<candidateId>-candidate.json`, where `candidateId` is a deterministic SHA-256 hash over:

`repoUrl + commitHash + category + targetHintFile + dslRule`

This avoids collisions and allows multiple candidates from the same commit.

## Status model

```
DISCOVERED
→ DSL_VALID
→ TEST_GENERATED
→ TEST_PASSED
→ READY_FOR_PR
→ PROMOTED
```

Candidates can be moved to `REJECTED` from any stage.

## Generation and validation flow

1. Mining run emits `CommitEvaluation`.
2. `saveCandidates(...)` stages only `GREEN` + `VALID` evaluations.
3. `DslValidator` is executed again before staging.
4. Candidate tests can be generated via `MiningCandidateTestGenerator`.

Generated candidate tests currently validate:

- DSL parses
- `beforeExample` matches
- at least one replacement equals `afterExample` (when provided)
- `negativeExample` does not match

## Promotion constraints

- Mining does **not** mutate productive hint files.
- Promotion to productive hints must be an explicit, separate action.
- Only `READY_FOR_PR` candidates should be considered promotable.
