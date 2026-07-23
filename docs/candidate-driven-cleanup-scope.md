# Candidate-driven scope for coordinated cleanups

## Purpose

A coordinated cleanup must update every source location whose Java semantics change, but it must not parse or edit an entire project merely because one selected file contains a possible candidate.

The JUnit ExternalResource and project-wide Int-to-Enum cleanups therefore derive a binding-indexed source closure from the selected candidate before their semantic planners run.

This layer builds on the source-root policy documented in `multi-file-source-root-policy.md`. A reference can be part of the closure only when its compilation unit is editable under that policy.

## Processing stages

1. **Selected-scope candidate scan**
   - Parse only the explicitly selected compilation units.
   - Detect structural candidates.
   - Resolve the exact JDT Java elements that can change meaning.

2. **Workspace-wide reference search**
   - Search exact references through JDT Search across the workspace.
   - Include the declaring compilation units and every accurate source reference.
   - Retain deterministic Java-element-handle ordering.

3. **Policy filter and closure decision**
   - Accept only source units in the same Java project and in permitted production/test roots.
   - Reject binary, external-project, generated, derived, output, inaccurate, and otherwise non-source matches.
   - Record a closed-scope decision only when all observed references can be represented by editable source units.

4. **Semantic planning**
   - Parse only the proven closed source subset.
   - Preserve the planners' original complete-project entry points for explicit callers and compatibility tests.
   - Produce no coordinated migration when the scope decision is rejected.

## Cleanup-specific search seeds

### Int-to-Enum

For each selected structural candidate, the search seeds are:

- every candidate package-private `static final int` constant;
- every candidate package-private method whose `int` parameter would become the generated enum type.

The closure therefore includes the owner and every accurate source caller/reference user of the changed constants and method signatures.

### JUnit ExternalResource

For each selected candidate, the search seed is the concrete resource type:

- a selected class directly extending JUnit 4 `ExternalResource`; or
- the resolved declared type of a selected `@Rule` or `@ClassRule` field.

The closure therefore includes the resource declaration and every accurate source use that must move to Jupiter extension semantics.

## Fallback and refusal rules

### No candidate

No additional compilation unit is requested. Ordinary selected-file cleanup behavior remains local.

### Candidate bindings unavailable

When the selected syntax clearly indicates a candidate but JDT cannot recover all seed elements, the cleanup preserves the previous conservative fallback and requests the complete source-root-policy scope. The semantic planner still performs its normal binding validation before generating edits.

### Search result not representable as editable project source

The coordinated migration is refused when any exact closure search reports:

- a binary reference;
- a reference in another Java project;
- a generated, derived, output, or excluded source root;
- an inaccurate JDT match;
- a match without a Java-model compilation unit;
- a candidate declaration outside the permitted policy scope.

The cleanup does not silently broaden to unrelated workspace content and does not produce a partial migration.

## Host integration and lifecycle ownership

`IMultiFileCleanUpScopeProvider` is an optional integration point. A host that supports candidate-driven expansion must call `expandCleanUpScope(...)`, add the returned compilation units, and repeat until the provider reaches its fixed point.

The ordinary unpatched Eclipse `CleanUpRefactoring` does not call this provider API. Callers using that standard lifecycle must therefore supply the complete coordinated compilation-unit scope explicitly. The original complete-scope planner entry points remain supported for this path.

Scope decisions are stored only for the serialized cleanup lifecycle established by `AbstractPlannedMultiFileCleanUp`:

- a pending handle set describes the source closure requested from a supporting host;
- a verified handle set records the fixed point once those units are present;
- a rejected decision forces the coordinated planner to return an empty migration plan;
- the decision is consumed and cleared when planning begins.

## Verification

The common-layer tests cover deterministic admission and fail-closed handling for:

- accurate permitted references;
- inaccurate matches;
- binary/non-source matches;
- other-project matches;
- policy-excluded source units;
- declarations outside the allow-list.

The cleanup scope tests start from owner-only selections with real source users and verify that:

- the exact Int-to-Enum caller closure is returned;
- the exact JUnit Rule-user closure is returned;
- unrelated editable source is not admitted;
- each closure is emitted once and reaches a stable fixed point.

The lifecycle tests exercise the ordinary Eclipse path with an explicit complete scope and verify that:

- all semantically coupled sources are migrated atomically;
- unrelated selected source remains unchanged;
- apply and undo preserve the complete verified source/error baseline.

Related issues: #1212, #1214, #1221, #1224.
