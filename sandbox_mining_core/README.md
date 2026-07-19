# Refactoring Mining Core (`sandbox_mining_core`)

> **Navigation**: [Main README](../README.md) | [Issue #1111](https://github.com/carstenartur/sandbox/issues/1111)

## Purpose

`sandbox_mining_core` analyzes commit history with an LLM to discover possible TriggerPattern DSL cleanups. Its output is **untrusted candidate data**, not a productive cleanup rule.

This module is intentionally separate from `sandbox_mining_cli`:

- `sandbox_mining_core` discovers possible new rules from commits.
- `sandbox_mining_cli` scans source trees with already curated rules.

A high number of scanner matches does not approve a discovered rule.

## Discovery contract

`PromptBuilder` uses a focused discovery-first contract. For each commit the model must return either exactly one local candidate or an explicit `noCleanup` decision.

The normal prompt does not request architectural analysis, plugin-replacement decisions, speculative DSL extensions, or broad numeric scoring. It requires:

- exactly one raw DSL rule;
- a target hint file and source level;
- a complete compiling before example;
- the complete compiling expected after example;
- a compiling negative example;
- confidence of at least `0.80`.

Below that threshold, or when safety depends on unavailable type, overload, data-flow, synchronization, nullability, or multi-file context, the model must return `noCleanup=true`. Full legacy rule inventories and `known-rules.json` are not copied into prompts.

## Discovery flow

```text
Git commits
  -> focused candidate-or-noCleanup request
  -> deterministic DSL validation
  -> mining-candidates/*.json
  -> legacy-schema migration if required
  -> CandidateVerifier
  -> curated and staged duplicate checks
  -> READY_FOR_REVIEW
  -> explicit human decision
  -> APPROVED or REJECTED
  -> minimal promotion PR
  -> PROMOTED after merge
```

`MiningCli` stages only relevant GREEN evaluations whose DSL passes `DslValidator`. It does not write directly into the productive bundled hint directory.

## Candidate JSON

`MiningCandidate` is the authoritative record for an unreviewed proposal. Schema version 2 records:

- stable `candidateId` based on source repository, commit, discovery category, target hint file, and candidate ordinal;
- mutable `revision`;
- normalized rule and behavior fingerprints;
- DSL rule and target hint file;
- complete before, after, and negative examples;
- source Java version;
- structured verification diagnostics;
- auditable lifecycle transitions.

Correcting the DSL, examples, or declared source level keeps the candidate ID but starts a new revision. `CandidateStore` resets that revision to `DISCOVERED`, clears stale verification and rejection state, and records a revision boundary so deterministic verification and human review must run again. Status-only updates do not change the revision. A `PROMOTED` candidate is immutable; a different transformation must use a new candidate origin.

Multiple proposals from one commit receive separate ordinals. Schema-v1 files are migrated before verification, and obsolete filenames are removed only after the canonical schema-v2 file has been written successfully.

Candidate JSON and review decisions are persisted on the dedicated `mining/candidates` data branch. That branch is not merged into `main` and never contains productive rules.

## Deterministic behavior verification

`CandidateVerifier` runs in process; it does not generate Java test source. It verifies:

1. required candidate fields are present;
2. DSL parsing and `DslValidator` succeed;
3. the candidate contains exactly one transformation rule;
4. every referenced guard function resolves through the production guard registry;
5. built-in guards are composed with the existing resolver and the previous resolver is restored afterwards;
6. before, after, and negative Java examples compile at the declared source level with binding resolution enabled;
7. exactly one positive replacement is produced;
8. that replacement is applied using the AST match offset and length;
9. the transformed and expected complete Java syntax trees are structurally equal, including literal values;
10. the negative example produces no match.

Binding resolution is part of verification so type guards are evaluated against the actual receiver type rather than the matcher’s legacy unresolved-binding fallback.

Only candidates that pass behavior verification participate in canonical duplicate selection. A malformed earlier proposal therefore cannot suppress a later valid proposal containing the same rule. Canonical rule fingerprints are derived from parsed individual rules, so file metadata and comments do not hide duplicates. Candidates duplicating either another verified candidate or a rule already present in the curated bundled library become terminal `DUPLICATE` candidates and reference the canonical source in their diagnostic.

The verifier persists its version, final stage, message, match count, replacement count, and timestamp.

Run verification and generate candidate reports with:

```shell
java -cp sandbox_mining_core/target/sandbox-mining-core.jar \
  org.sandbox.mining.core.candidate.CandidateVerificationCli \
  --candidate-dir mining-candidates \
  --report-dir docs/mining-report \
  --curated-hint-dir sandbox_common_core/src/main/resources/org/sandbox/jdt/triggerpattern/internal \
  --source-version 21
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

`REJECTED`, `DUPLICATE`, and `SUPERSEDED` are terminal alternatives. Invalid transitions fail and valid transitions are recorded with actor, reason, and timestamp. A semantic content revision is a separate audited boundary that deliberately restarts the lifecycle at `DISCOVERED`.

## Review decisions

Review decisions can be applied locally with:

```shell
java -cp sandbox_mining_core/target/sandbox-mining-core.jar \
  org.sandbox.mining.core.candidate.CandidateReviewCli \
  --candidate mining-candidates/<candidate-id>-candidate.json \
  --action approve \
  --actor <reviewer> \
  --reason "Reviewed against the source commit"
```

Supported actions are `approve`, `reject`, `supersede`, and the post-merge `promote` transition. Repeating an already-applied decision is retry-safe. An approval retry does not add another approval transition, but it does refresh and persist verification with the current verifier before promotion continues.

The **Review mining candidate** GitHub Actions workflow provides the repository workflow for this command. It loads the candidate from `mining/candidates`, records the decision there, and closes rejected review issues.

## Promotion

An approved candidate can generate a minimal promotion change set with:

```shell
java -cp sandbox_mining_core/target/sandbox-mining-core.jar \
  org.sandbox.mining.core.candidate.CandidatePromotionCli \
  --candidate mining-candidates/<candidate-id>-candidate.json \
  --repo-root . \
  --actor <reviewer>
```

The command re-verifies the approved candidate and changes only:

- the selected curated `.sandbox-hint` file;
- a permanent JSON behavior fixture;
- the promoted-fixture index.

Promotion is limited to bundled libraries that are actually loaded as active Cleanup and Quick Assist rule sets. Maintenance-only or disabled hint files cannot receive promoted candidates. The generated fixture contains the reviewed rule, complete before/after/negative examples, fingerprints, verifier version, provenance, and reviewer.

`PromotedCandidateBehaviorTest` executes every indexed fixture with binding resolution, confirms that its target remains active, and checks that both the DSL and candidate provenance marker remain in the selected bundled rule file. It also scans active bundled files in the reverse direction, so a promoted rule marker without an indexed permanent fixture fails the build.

The review workflow runs these tests before opening or updating a promotion PR. After merge, the completion workflow compares the merged fixture and rule against the still-`APPROVED` candidate before recording `PROMOTED`; a branch name or fixture file alone is not sufficient.

The Eclipse Quick Assist path is also binding-aware. Its JUnit coverage selects a proposal at the matching cursor position, applies it to an `IDocument`, verifies the complete result, and verifies that no proposal is offered outside the match.

## Workflow safety boundary

The nightly discovery workflow may publish candidate artifacts and create review issues for `READY_FOR_REVIEW` candidates. It must not:

- merge candidate JSON into `main`;
- add generated `.sandbox-hint` files;
- create review issues from raw `evaluations.json`;
- treat `known-rules.json` as approval state.

Nightly discovery, review decisions, and promotion completion share one concurrency group so updates to `mining/candidates` cannot overwrite one another. Review issue generation scans beyond candidates that already have issues and limits the number of newly created issues rather than only inspecting the first five entries. Generated issue bodies use dynamic Markdown fences so candidate text cannot break the report structure.

Only resumable scan state may be merged automatically. Candidate state stays on `mining/candidates`, and a productive rule reaches `main` only through a tested promotion PR after an explicit approval.

## Legacy known-rules data

`docs/mining-report/known-rules.json` predates the candidate review boundary. `KnownRulesStore` remains read-compatible only so historic reports can be archived. New evaluations are no longer registered there, its contents are not sent to the LLM, and it is removed from the published approval view.

## Build and tests

```shell
mvn -pl sandbox_common_core,sandbox_mining_core -am test
```

The suite covers candidate identity, revision invalidation, promoted-state immutability, collision-safe legacy migration, lifecycle transitions, compiling examples with binding-aware guards, structural positive transformation behavior, negative examples, malformed or multi-rule DSL, unresolved guards, malformed Java, ambiguous matches, curated and staged duplicate selection, resolver restoration, review retry verification, path-safe active-bundle promotion, merged-promotion integrity, permanent promoted-candidate fixtures, and Quick Assist proposal application.
