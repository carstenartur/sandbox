# Mining evidence implementation plan

> Execution uses the existing isolated branch and bounded delegated tasks.

**Goal:** Raise discovery quality without additional LLM requests.
**Architecture:** Shared response admission; local JGit/JDT evidence gate;
persisted hunk checked again on staging; existing verifier and review lifecycle.
**Tech Stack:** Java 25, Maven/JUnit, existing JGit/JDT/Gson.
**Spec:** ../specs/2026-11-03-mining-evidence-design.md

## Global constraints and review focus

No new runtime dependency or Python invocation. Preserve old constructors and
existing candidate decisions. Do not claim semantic proof or prompt efficacy.
Tests cover contradictory/missing decisions, cross-hunk evidence, fabricated
replacement literals despite correct evidence, cached replay, and retry exhaustion.

## Tasks

- [x] Reproduce and fix PR1795's raw schema-v1 source-version invalidation finding.
- [x] RED: provider tests reject noCleanup/low or invalid confidence and preserve
  sourceVersion/evidence. GREEN: shared admission helper and both parsers.
- [x] Extend CommitEvaluation with nullable SourceEvidence(path,before,after,
  diffHunk), old constructors, withSourceEvidence, and withoutCandidate helpers.
  Provider parsers construct only the three model fields; diffHunk comes from Git.
- [x] RED: JGit source-evidence tests reject invented text, incorrect files/hunks,
  unchanged context, and correct hunk plus invented DSL literal. GREEN: implement
  SourceEvidenceVerifier using JGit patch structure and JDT scanner tokens.
- [x] RED: mining integration rejects absent evidence without extra calls and
  preserves sourceVersion through staging/recovery. GREEN: connect the gate and
  persist evidence in MiningCandidate; invalidate verification if evidence changes.
- [x] RED: transport tests count failed/retried HTTP attempts and stop at 18.
  GREEN: debit before each attempt; retain existing public compatibility names.
- [x] Refine the prompt, issue labels/evidence, and README with exact limitations.
- [x] Run common-core and mining-core clean verify, independent review, diff-size
  and whitespace checks. Publication and exact-head CI are recorded in the follow-up PR;
  PR1795 merged during implementation, so the follow-up targets the updated main.

Tests use local recorded diffs and fake transport, never live provider credentials.
The first behavioral regressions must fail before their fixes. Full verification
uses `mvn -o -pl sandbox_common_core,sandbox_mining_core -am clean verify` with the
existing cached repository. A live prompt comparison remains a separate experiment.
