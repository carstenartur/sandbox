# Comparison Process: Improving Refactoring Mining with Reference Evaluations

## Overview

This document describes the iterative comparison process for improving the
Gemini-based refactoring mining pipeline by comparing its results against
a reference evaluation (e.g., from GitHub Copilot).

## Goal

Identify what information the mining pipeline (`PromptBuilder` → `GeminiClient`)
is missing, so that results improve over time. Copilot serves as reference —
it receives the same prompt but has implicit access to source code, types,
and API documentation. The question is: what does Gemini lack, and how can
we provide it explicitly?

## Architecture

```
MiningCli.run(args)
  → --comparison-mode --copilot-results copilot-eval.json
  → ExternalEvaluationImporter.importFromJson(copilotResultsPath)
  → MiningComparator.compare(miningResults, copilotResults)
  → DeltaReport with GapEntry list
  → ErrorFeedbackCollector.collect(evaluations)
  → PromptBuilder with enriched context (Eclipse API, Type Context, Examples, Error Feedback)
```

## Running a Comparison

### Step 1: Run the Standard Mining Pipeline

```bash
java -jar sandbox-mining-core.jar \
  --config .github/refactoring-mining/repos-eclipse-2025.yml \
  --sandbox-root . \
  --output output/gemini-run-1
```

### Step 2: Prepare Reference Results

Have Copilot (or another reference tool) evaluate the same commits.
Save results as a JSON array of `CommitEvaluation` objects:

```bash
# Example: copilot-results.json with same schema as evaluations.json
```

### Step 3: Run Comparison Mode

```bash
java -jar sandbox-mining-core.jar \
  --config .github/refactoring-mining/repos-eclipse-2025.yml \
  --sandbox-root . \
  --comparison-mode \
  --copilot-results copilot-results.json \
  --enrich-type-context \
  --output output/comparison-run-1
```

### Step 4: Analyze the Delta Report

The delta report groups gaps by category:

- **MISSED_RELEVANT**: Commits that Gemini missed but the reference found relevant
- **WRONG_TRAFFIC_LIGHT**: Commits where Gemini assigned a different traffic light
- **MISSING_DSL_RULE**: Reference produced a valid DSL rule where Gemini did not
- **INVALID_DSL_RULE**: Gemini produced an invalid rule where reference was valid
- **CATEGORY_MISMATCH**: Disagreement on transformation category
- **MISSING_API_CONTEXT**: Reference found patterns Gemini lacks API context for
- **MISSING_TYPE_CONTEXT**: Reference found patterns requiring type hierarchy info

### Step 5: Apply Improvements

Based on the delta report:

1. Update `eclipse-api-context.md` with missing API patterns
2. Add new examples to `mining-examples.md`
3. Extend `dsl-explanation.md` with negative examples
4. Create new `.sandbox-hint` files for valid discovered rules
5. Update `refactoring-keywords.txt` with new filter terms

### Step 6: Re-run and Iterate

Repeat from Step 1 with the improved prompt context.
Each iteration should show fewer gaps in the delta report.

## CLI Options Reference

| Option | Description |
|--------|-------------|
| `--commit-list <path>` | Process specific commits from a file |
| `--max-duration <min>` | Maximum run duration in minutes |
| `--comparison-mode` | Enable comparison against reference results |
| `--copilot-results <path>` | Path to reference evaluation JSON |
| `--enrich-type-context` | Add Eclipse type hierarchy to prompts |
| `--keyword-filter <path>` | Pre-filter commits by keywords |
| `--output-format <fmt>` | Output format: json, netbeans, or both |
| `--strict-netbeans` | Only NetBeans format on stdout |

## NetBeans Output Format

The `--output-format netbeans` option produces compiler-warning-style output:

```
repo/abc1234:1: warning: [GREEN/Collections] Replace unmodifiableList — R=8/I=6/E=3
repo/def5678:1: info: [YELLOW/Resources] Use try-with-resources — R=7/I=5/E=4
```

With `--strict-netbeans`, only this format appears on stdout (info messages
go to stderr), making the output pipe-friendly:

```bash
java -jar sandbox-mining-core.jar \
  --config repos.yml --output-format netbeans --strict-netbeans \
  | grep GREEN | wc -l
```

## Key Classes

| Class | Purpose |
|-------|---------|
| `ExternalEvaluationImporter` | Imports reference evaluations from JSON |
| `MiningComparator` | Compares mining vs reference results |
| `DeltaReport` | Holds comparison gaps with formatting |
| `GapCategory` / `GapEntry` | Gap classification |
| `HintFileUpdater` | Creates .sandbox-hint files from valid rules |
| `ErrorFeedbackCollector` | Collects LLM error patterns for feedback |
| `TypeContextEnricher` | Adds Eclipse type hierarchy to prompts |
| `CommitKeywordFilter` | Pre-filters commits by keywords |
| `NetBeansReporter` | Compiler-warning-style output format |
