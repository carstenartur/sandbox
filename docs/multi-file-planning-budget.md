# Coordinated cleanup planning budgets

## Purpose

A coordinated cleanup may need to parse several related Java source files before it can prove that one semantic change is safe. Candidate-driven scope keeps this set small in normal projects, but pathological source graphs, unusually large working copies, or incomplete binding recovery must not turn a cleanup preview into unbounded memory use or latency.

The common multi-file cleanup layer therefore measures and guards the complete source scope before any AST batch is created.

## Default limits

| Limit | Warning | Hard abort |
|---|---:|---:|
| Distinct primary compilation units | 500 | 2,000 |
| Current source size, measured as UTF-8 bytes | 32 MiB | 128 MiB |

The warning thresholds add a nonfatal `RefactoringStatus` warning before parsing begins. The hard thresholds add a fatal status and return no plan; no AST batch, partial semantic plan, cleanup fix, state update, or preview edit is created.

Duplicate handles are measured once. Source size is taken from the current primary compilation-unit source, so unsaved working-copy content is represented. UTF-8 length is counted without allocating a second byte array.

## Configuration

The defaults can be reduced or increased for controlled environments with positive decimal system properties:

```text
org.sandbox.cleanup.planning.warningUnits
org.sandbox.cleanup.planning.hardUnits
org.sandbox.cleanup.planning.warningSourceBytes
org.sandbox.cleanup.planning.hardSourceBytes
```

Invalid, zero, or negative values fall back to repository defaults. A warning override larger than its hard limit is clamped to a safe value; configuration cannot accidentally disable a hard guard.

These are process-level safety controls, not cleanup preferences. A future UI may surface them, but callers must not silently substitute unlimited values.

## Metrics

`MultiFileCleanUpPlanResult` carries immutable `MultiFilePlanningMetrics` for the planning attempt:

- distinct compilation-unit count;
- current UTF-8 source bytes;
- AST batch parse duration;
- complete planning duration, including scope measurement;
- number of immutable semantic entries retained by the final plan.

`AbstractPlannedMultiFileCleanUp` retains these metrics only for the serialized cleanup lifecycle and clears them together with the plan during postconditions. Structured preview/reporting work in #1214 can consume the measurements without rescanning source files.

## Cancellation and deterministic abort

Cancellation is checked:

- between compilation units during pre-parse measurement;
- before and after the batch parser;
- while discovering semantic candidates or resource types;
- while validating method, constant, Rule, and resource references;
- while freezing mutable analysis state into immutable plan entries.

The JDT batch parser receives the same progress monitor. Cancellation propagates as `OperationCanceledException`; the common cleanup lifecycle removes any retained plan and metrics before rethrowing.

A hard budget failure is different from user cancellation: it returns a fatal status with the measured threshold and creates no plan. This gives UI and headless callers a deterministic, inspectable refusal instead of an incomplete migration.

## Memory ownership

The planning maps containing `CompilationUnit` and AST nodes remain method-local. Final JUnit and Int-to-Enum plans retain Java-model handles, binding keys, signatures, source ranges, generated names, and semantic edit descriptions only. AST roots are eligible for collection as soon as plan creation returns.

The retained-plan entry count is a semantic-size proxy, not a heap-size estimate. It is intended for diagnostics, regression comparisons, and future benchmarks.

## Verification

Common-layer tests cover:

- exact UTF-8 byte measurement, including supplementary and malformed surrogate input;
- duplicate compilation-unit handles;
- warning-only scopes;
- immediate hard abort by unit count and source bytes;
- cancellation before source reads;
- invalid and inverted property overrides;
- immutable metric transport through plan results.

The cleanup integration suite continues to prove apply, compile, and undo behavior for both coordinated planners. Stress regressions use synthetic large scopes and low hard limits to prove early deterministic refusal without constructing ASTs.

Related issues: #1212, #1214, #1221, #1224.
