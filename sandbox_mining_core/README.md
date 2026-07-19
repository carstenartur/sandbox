# Refactoring Mining Core (`sandbox_mining_core`)

> **Navigation**: [Main README](../README.md) | [Issue #1111](https://github.com/carstenartur/sandbox/issues/1111)

## Purpose

`sandbox_mining_core` analyzes commit history with an LLM to discover possible TriggerPattern DSL cleanups. Its output is **untrusted candidate data**, not a productive cleanup rule.

This module is intentionally separate from `sandbox_mining_cli`:

- `sandbox_mining_core` discovers possible new rules from commits.
- `sandbox_mining_cli` scans source trees with already curated rules.

A high number of scanner matches does not approve a discovered rule.

## Discovery flow

```text
Git commits
  -> local commit selection
  -> LLM evaluation
  -> deterministic DSL validation
  -> mining-candidates/*.json
  -> CandidateVerifier
  -> READY_FOR_REVIEW
  -> human approval
  -> separate promotion PR
```

`MiningCli` stages only GREEN evaluations whose DSL passes `DslValidator`. It does not write directly into the productive bundled hint directory.

## Candidate JSON

`MiningCandidate` is the authoritative record for an unreviewed proposal. Schema version 2 records:

- stable `candidateId` based on source repository, commit, and candidate ordinal;
- mutable `revision`;
- normalized rule and behavior fingerprints;
- DSL rule and target hint file;
- complete before, after, and negative examples;
- source Java version;
- structured verification diagnostics;
- auditable lifecycle transitions.

Correcting DSL or examples keeps the candidate ID and increments the revision when saved through `CandidateStore`. Multiple proposals from one commit use distinct candidate ordinals.

## Deterministic behavior verification

`CandidateVerifier` runs in process; it does not generate Java test source. It verifies:

1. required candidate fields are present;
2. DSL parsing and `DslValidator` succeed;
3. built-in guards are registered;
4. the positive Java example parses at the declared source level;
5. exactly one replacement is produced;
6. that replacement is applied using the AST match offset and length;
7. the complete transformed source equals `afterExample` after whitespace normalization;
8. the negative example parses and produces no match.

The verifier persists its version, final stage, message, match count, replacement count, and timestamp.

Run verification and generate candidate reports with:

```shell
java -cp sandbox_mining_core/target/sandbox-mining-core.jar \
  org.sandbox.mining.core.candidate.CandidateVerificationCli \
  --candidate-dir mining-candidates \
  --report-dir docs/mining-report
```

The command writes `candidates.json`, `candidates.html`, and copies individual candidate JSON files into the report directory.

## Lifecycle

```text
DISCOVERED
  -> DSL_VALID
  -> BEHAVIOR_VALID
  -> READY_FOR_REVIEW
  -> APPROVED
  -> PROMOTED
```

`REJECTED` and `SUPERSEDED` are terminal alternatives. Invalid transitions fail and valid transitions are recorded with actor, reason, and timestamp.

## Workflow safety boundary

The nightly discovery workflow may publish candidate artifacts and create review issues for `READY_FOR_REVIEW` candidates. It must not:

- auto-merge candidate JSON into `main`;
- add generated `.sandbox-hint` files;
- create review issues from raw `evaluations.json`;
- treat `known-rules.json` as approval state.

Only resumable scan state may be persisted automatically. Promotion remains a separate reviewed code change containing the rule and a behavior test.

## Legacy known-rules data

`docs/mining-report/known-rules.json` predates the candidate review boundary. It may be read as legacy duplicate context, but newly discovered evaluations are not persisted there by the workflow and its entries are not approved rules.

## Build and tests

```shell
mvn -pl sandbox_mining_core -am test
```

The normal suite covers candidate identity, revisioning, lifecycle transitions, positive transformation behavior, negative examples, malformed DSL, malformed Java, ambiguous matches, and incorrect expected output.
