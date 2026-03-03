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
| `DeltaReport` | Holds comparison gaps with formatting (JSON, Markdown) |
| `GapCategory` / `GapEntry` | Gap classification with actionable suggestions |
| `HintFileUpdater` | Creates .sandbox-hint files from valid rules |
| `ErrorFeedbackCollector` | Collects LLM error patterns for feedback |
| `TypeContextEnricher` | Adds Eclipse type hierarchy to prompts |
| `CommitKeywordFilter` | Pre-filters commits by keywords |
| `NetBeansReporter` | Compiler-warning-style output format |

## Gap Categories

### Coarse-Grained (Programmatic Comparison)

| Category | Description |
|----------|-------------|
| `MISSED_RELEVANT` | Gemini missed a relevant commit |
| `WRONG_TRAFFIC_LIGHT` | Gemini assigned wrong traffic light color |
| `MISSING_DSL_RULE` | Reference produced a DSL rule where Gemini did not |
| `INVALID_DSL_RULE` | Gemini produced an invalid DSL rule |
| `CATEGORY_MISMATCH` | Disagreement on transformation category |
| `MISSING_API_CONTEXT` | Gemini lacks Eclipse API context |
| `MISSING_TYPE_CONTEXT` | Gemini lacks type hierarchy info |

### Fine-Grained (Iterative Improvement)

| Category | Action |
|----------|--------|
| `TYP_KONTEXT` | Add type information to `eclipse-api-context.md` |
| `API_VERSION` | Add Java version context to `eclipse-api-context.md` |
| `GUARD_WISSEN` | Add guard examples to `dsl-explanation.md` |
| `DSL_SYNTAX` | Add negative examples to `dsl-explanation.md` |
| `GENERALISIERUNG` | Add generalization examples to `mining-examples.md` |
| `DUPLIKAT_ERKENNUNG` | Improve existing `.sandbox-hint` descriptions |
| `KONTEXT_NUTZUNG` | Extend `PromptBuilder` context sections |

## Delta Report Output

The comparison mode now writes two files to the output directory:

- **`delta-report.json`** — Machine-readable gap data with summary and details
- **`delta-report.md`** — Human-readable Markdown with gap distribution table and actionable suggestions

## Run Documentation Template

Each comparison run should be documented by appending to this file:

```markdown
### Run N — [DATE]

**Commits analyzed:** [count]
**Gap distribution:**
- TYP_KONTEXT: X
- DSL_SYNTAX: Y
- ...

**Key findings:**
- [What was the most common gap?]
- [What specific API knowledge was missing?]
- [What DSL mistakes did Gemini repeat?]

**Improvements applied:**
- [List of files changed and what was added]

**Recommendations for next run:**
- [Focus areas for next iteration]
```

### Run 1 — 2026-03-03

**Commits analyzed:** 39 (from `eclipse-2025-sample.txt`)
**Repositories:** eclipse-platform/eclipse.platform.ui, eclipse-jdt/eclipse.jdt.ui
**Reference:** Copilot Coding Agent evaluation (no Gemini results — no API key available)

**Evaluation distribution:**
- GREEN: 2 (String.replaceAll→replace, Platform.run→SafeRunner.run)
- YELLOW: 13 (JUnit migration, URL deprecation, performance, module imports, etc.)
- RED: 14 (6 architecture migrations, 7 cleanup bug fixes, 1 deprecation handling)
- NOT_APPLICABLE: 10 (API removal, version bumps, clean code, commented-out code removal)
- Duplicates identified: 7

**Gap distribution (what Gemini would likely get wrong without improvements):**
- MISSING_API_CONTEXT: 2 (URL deprecation Java 20+, Java 21 deprecation patterns)
- DSL_SYNTAX: 1 (dsl-explanation.md was out of sync — missing `<!map>` and 4 guards)
- CATEGORY_MISMATCH: 2 (cleanup bug fixes misclassified; "clean code" commits marked relevant)
- GENERALISIERUNG: 3 (missing NOT_APPLICABLE examples for API removal, @Deprecated marking, built-in cleanups)

**Key findings:**
- **Most common gap:** GENERALISIERUNG — the mining examples lacked guidance for
  several NOT_APPLICABLE categories that Gemini would incorrectly mark as relevant
  (applying built-in cleanups, marking APIs for removal, removing entire APIs)
- **Missing API knowledge:** URL constructor deprecation (Java 20+) was not documented
  in `eclipse-api-context.md`. This affects 2 out of 39 commits. Java 21+ deprecation
  patterns (Thread methods, SecurityManager) were also missing.
- **DSL feature gap:** The `dsl-explanation.md` in `sandbox_mining_core` was missing the
  `<!map>` directive and 4 guards (`isParameter`, `isField`, `isInConstructor`,
  `classOverrides`) that exist in `sandbox_common_core`. This means Gemini would not
  know about these features when writing DSL rules.
- **Bug fix vs. refactoring:** 7 out of 14 RED commits are bug fixes to existing JDT
  cleanups (NLS markers, lambda edge cases, generic type issues). Without explicit
  guidance, Gemini might classify these as YELLOW instead of RED.

**Improvements applied:**
1. **`sandbox_mining_core/src/main/resources/dsl-explanation.md`** — Synced with
   `sandbox_common_core` version: added `<!map>` directive documentation and 4 missing
   guards (`isParameter`, `isField`, `isInConstructor`, `classOverrides`).
   *Gap: DSL_SYNTAX — Gemini would produce rules using older DSL features only*
2. **`sandbox_mining_core/src/main/resources/eclipse-api-context.md`** — Added URL
   constructor deprecation (Java 20+) section with replacement patterns and exception
   semantics. Added Java 21+ deprecation overview.
   *Gap: MISSING_API_CONTEXT — Gemini would miss URL deprecation pattern*
3. **`sandbox_mining_core/src/main/resources/mining-examples.md`** — Added 4 new examples:
   - Example 11: RED — "Clean-up Bug Fix" (NLS marker fix, commit `3702d32e`)
   - Example 12: NOT_APPLICABLE — "Perform clean code" commits
   - Example 13: NOT_APPLICABLE — "Mark deprecated API for removal"
   - Example 14: YELLOW — URL deprecation (Java 20+)
   Added 3 new "Common Mistakes" entries (#8-#10).
   *Gap: CATEGORY_MISMATCH + GENERALISIERUNG — Gemini would misclassify these commit types*
4. **`output/run-1/copilot-evaluations.json`** — Created complete Copilot reference
   evaluation for all 39 commits in proper CommitEvaluation format.

**Recommendations for next run:**
- Run with actual Gemini API key to get real LLM results for comparison
- Focus on YELLOW commits: several (URL deprecation, system properties) might become
  GREEN with DSL extensions (exception-aware guards, system property mapping)
- Consider using the existing `throwsException` guard to handle URL→URI
  replacement safely (e.g., only apply when the method already declares
  `throws MalformedURLException`)
- JUnit migration commits (3 duplicates) could benefit from per-annotation DSL rules
  even if the full migration is YELLOW
