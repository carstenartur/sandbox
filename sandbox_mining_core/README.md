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
  -> LLM evaluation
  -> deterministic DSL validation
  -> mining-candidates/*.json
  -> CandidateVerifier
  -> READY_FOR_REVIEW
  -> explicit human decision
  -> APPROVED or REJECTED
  -> minimal promotion PR
  -> PROMOTED after merge
```

`MiningCli` stages only GREEN evaluations whose DSL passes `DslValidator`. It does not write directly into the productive bundled hint directory.

## Candidate JSON

`MiningCandidate` is the authoritative record for an unreviewed proposal. Schema version 2 records:

- stable `candidateId` based on source repository, commit, discovery category, target hint file, and candidate ordinal;
- mutable `revision`;
- rule and behavior fingerprints;
- DSL rule and target hint file;
- complete before, after, and negative examples;
- source Java version;
- structured verification diagnostics;
- auditable lifecycle transitions.

Correcting DSL or examples keeps the candidate ID and increments the revision when saved through `CandidateStore`. Multiple proposals from one commit can use different discovery origins or candidate ordinals.

Candidate JSON and review decisions are persisted on the dedicated `mining/candidates` data branch. That branch is not merged into `main` and never contains productive rules.

## Deterministic behavior verification

`CandidateVerifier` runs in process; it does not generate Java test source. It verifies:

1. required candidate fields are present;
2. DSL parsing and `DslValidator` succeed;
3. built-in guards are composed with the existing resolver and the previous resolver is restored afterwards;
4. before, after, and negative Java examples parse at the declared source level;
5. exactly one positive replacement is produced;
6. that replacement is applied using the AST match offset and length;
7. the transformed and expected complete Java syntax trees are structurally equal, including literal values;
8. the negative example produces no match.

The verifier persists its version, final stage, message, match count, replacement count, and timestamp.

Run verification and generate candidate reports with:

```shell
java -cp sandbox_mining_core/target/sandbox-mining-core.jar \
  org.sandbox.mining.core.candidate.CandidateVerificationCli \
  --candidate-dir mining-candidates \
  --report-dir docs/mining-report \
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

`REJECTED` and `SUPERSEDED` are terminal alternatives. Invalid transitions fail and valid transitions are recorded with actor, reason, and timestamp.

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

Supported actions are `approve`, `reject`, `supersede`, and the post-merge `promote` transition.

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

`PromotedCandidateBehaviorTest` executes every indexed fixture against the proposed DSL and confirms that the DSL text is present in the selected bundled rule file. The review workflow runs these tests before opening or updating a promotion PR. A separate completion workflow records `PROMOTED` only after that PR is actually merged.

## Workflow safety boundary

The nightly discovery workflow may publish candidate artifacts and create review issues for `READY_FOR_REVIEW` candidates. It must not:

- merge candidate JSON into `main`;
- add generated `.sandbox-hint` files;
- create review issues from raw `evaluations.json`;
- treat `known-rules.json` as approval state.

Only resumable scan state may be merged automatically. Candidate state stays on `mining/candidates`, and a productive rule reaches `main` only through a tested promotion PR after an explicit approval.

## Legacy known-rules data

`docs/mining-report/known-rules.json` predates the candidate review boundary. It may be read as legacy duplicate context, but it is removed from the published approval view and its entries are not approved rules.

## Build and tests

```shell
mvn -pl sandbox_common_core,sandbox_mining_core -am test
```

The suite covers candidate identity, revisioning, lifecycle transitions, structural positive transformation behavior, negative examples, malformed DSL and Java, ambiguous matches, resolver restoration, review commands, path-safe promotion generation, and permanent promoted-candidate fixtures.
