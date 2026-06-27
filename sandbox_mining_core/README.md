# Refactoring Mining Core (`sandbox_mining_core`)

> **Navigation**: [Main README](../README.md) | [TODO](TODO.md)

## Overview

`sandbox_mining_core` is the AI-assisted commit analysis engine for inferring TriggerPattern DSL rules from Git diffs. It forms the core of the refactoring mining infrastructure by scanning Eclipse JDT commits to discover and document recurring code transformation patterns.

## Features

- **LLM-based rule inference** — integrates with Gemini and other LLM providers to analyze Git diffs and propose `.sandbox-hint` rules
- **State management** — persistent state with deferred commits, epoch rotation, and category tracking
- **Keyword filtering** — focus mining on relevant commits using configurable keyword sets
- **Comparison mode** — evaluate mining results against a reference tool (e.g., GitHub Copilot) to identify gaps
- **Automatic HintFile generation** — discovered rules are written directly as `.sandbox-hint` files
- **Category-aware mining** — tracks per-category hit counts, exhausted categories, and focus categories
- **DSL enhancement reporting** — groups unsatisfied patterns by limitation category and creates GitHub issues

## Architecture

```
MiningCli.run(args)
  → MiningConfig (YAML-based configuration)
  → CommitWalker (JGit-based Git traversal, epoch rotation)
  → LlmClient (Gemini / configurable provider)
  → PromptBuilder (enriched context: Eclipse API, type info, examples, error feedback)
  → HintFileUpdater (writes .sandbox-hint files)
  → MiningState (RepoState: currentEpoch, completedEpochs, categoryHitCounts)
  → DslEnhancementReporter (creates GitHub issues for unresolvable patterns)
```

Key packages under `org.sandbox.mining.core`:
- `action` — high-level mining actions (scan, compare, report)
- `category` — transformation category definitions and tracking
- `comparison` — comparison mode and delta reporting against reference evaluations
- `config` — YAML configuration parsing (`MiningConfig`, `RepoConfig`)
- `enrichment` — prompt enrichment (type context, API context, examples)
- `filter` — keyword-based commit filtering
- `report` — JSON/Markdown report generation

## Usage

The mining core is used by:
- **`sandbox_mining_cli`** — standalone CLI tool that wraps this module
- **GitHub Actions workflows** — `.github/workflows/mining-core.yml` runs mining automatically

### Configuration

Mining is configured via YAML files in `.github/refactoring-mining/`:

```yaml
repos:
  - url: https://github.com/eclipse-jdt/eclipse.jdt.ui
    branch: master
    keywords: [cleanup, refactor, simplify]
epochs:
  - startDate: "2024-01-01"
    endDate: "2024-06-30"
```

## Related Documentation

- [docs/COMPARISON-PROCESS.md](../docs/COMPARISON-PROCESS.md) — iterative comparison workflow
- [sandbox_mining_cli/README.md](../sandbox_mining_cli/README.md) — CLI usage
- [TODO](TODO.md) — open tasks and implementation tracking

## Mining Candidate Pipeline

`MiningCli` stages mined DSL rules as JSON candidates in `mining-candidates/` instead of writing directly to productive bundled `.sandbox-hint` files.

### What is a candidate?

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

`sourceRepo + sourceCommit + category + targetHintFile + dslRule`

This avoids collisions and allows multiple candidates from the same commit.

### Status model

```
DISCOVERED
→ DSL_VALID
→ TEST_GENERATED
→ TEST_PASSED
→ READY_FOR_PR
→ PROMOTED
```

Candidates can be moved to `REJECTED` from any stage.

### Generation and validation flow

1. Mining run emits `CommitEvaluation`.
2. `saveCandidates(...)` stages only `GREEN` + `VALID` evaluations.
3. `DslValidator` is executed again before staging.
4. Candidate tests can be generated via `MiningCandidateTestGenerator`.

Generated candidate tests currently validate:

- DSL parses
- `beforeExample` matches
- at least one replacement equals `afterExample` (when provided)
- `negativeExample` does not match

### Promotion constraints

- Mining does **not** mutate productive hint files.
- Promotion to productive hints must be an explicit, separate action.
- Only `READY_FOR_PR` candidates should be considered promotable.
