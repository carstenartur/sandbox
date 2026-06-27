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
